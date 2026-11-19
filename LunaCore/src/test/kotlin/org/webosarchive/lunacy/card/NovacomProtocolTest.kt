package org.webosarchive.lunacy.card

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.webosarchive.lunacy.core.TestPlatform
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * novacomd's device side as the SDK's tools see it (NovacomProtocol): the reply line, then the
 * packets a device's novacomd sent on a channel, from a command line and the client's packets.
 */
class NovacomProtocolTest {
    @Before fun platform() { TestPlatform.install() }

    /** What the device wrote: the reply line, then each packet as (type, payload). */
    private class Reply(bytes: ByteArray) {
        val line: String
        val packets = ArrayList<Pair<Int, ByteArray>>()
        init {
            val nl = bytes.indexOfFirst { it == '\n'.code.toByte() || it == 0.toByte() }
            line = String(bytes, 0, nl + 1)
            var at = nl + 1
            if (at < bytes.size && bytes[at] == 0.toByte()) at++
            while (at + 16 <= bytes.size) {
                assertEquals(0xdecafbad.toInt(), int(bytes, at))
                val size = int(bytes, at + 8)
                packets += int(bytes, at + 12) to bytes.copyOfRange(at + 16, at + 16 + size)
                at += 16 + size
            }
            assertEquals("trailing bytes", bytes.size, at)
        }
        fun data(type: Int) = packets.filter { it.first == type }.fold(ByteArray(0)) { a, p -> a + p.second }
        /** The out-of-band messages, each (message, value). */
        val oob get() = packets.filter { it.first == 2 }.map { int(it.second, 0) to int(it.second, 4) }
    }

    private fun packet(type: Int, payload: ByteArray): ByteArray {
        val h = ByteArray(16)
        put(h, 0, 0xdecafbad.toInt()); put(h, 4, 1); put(h, 8, payload.size); put(h, 12, type)
        return h + payload
    }
    private fun oob(message: Int, value: Int) = packet(2, ByteArray(20).also { put(it, 0, message); put(it, 4, value) })

    private fun serve(device: NovacomProtocol.Device, line: String, then: ByteArray = ByteArray(0)): Reply {
        val out = ByteArrayOutputStream()
        NovacomProtocol(device).serve(ByteArrayInputStream((line + "\n").toByteArray() + then), out)
        return Reply(out.toByteArray())
    }

    private class Finished(out: String, err: String, private val code: Int) : NovacomProtocol.Running {
        override val stdout: InputStream = ByteArrayInputStream(out.toByteArray())
        override val stderr: InputStream = ByteArrayInputStream(err.toByteArray())
        override val stdin: OutputStream? = null
        override fun waitFor() = code
        override fun signal(signo: Int) {}
        override fun destroy() {}
    }

    private open inner class Device : NovacomProtocol.Device {
        val runs = AtomicInteger()
        val written = ByteArrayOutputStream()
        var lastRun: Pair<String, List<String>>? = null
        override fun nduid() = "0123456789abcdef0123456789abcdef01234567"
        override fun read(path: String): InputStream =
            if (path == "/etc/palm-build-info") ByteArrayInputStream("PRODUCT_VERSION_STRING=Lunacy Luna DE\n".toByteArray())
            else throw NovacomProtocol.Refused("file open failed\n")
        override fun write(path: String): OutputStream = written
        override fun run(path: String, args: List<String>): NovacomProtocol.Running {
            runs.incrementAndGet(); lastRun = path to args
            if (path != "/bin/uname") throw NovacomProtocol.Refused("file does not exist\n")
            return Finished("Linux\n", "a warning\n", 3)
        }
    }

    @Test fun nduidAsTheKernelAnsweredIt() {
        val r = serve(Device(), "get file:///proc/nduid")
        assertEquals("ok 0\n", r.line)
        assertEquals("0123456789abcdef0123456789abcdef01234567\n", String(r.data(0)))
        assertEquals(listOf(0 to 1, 2 to 0), r.oob)
    }

    @Test fun getSendsTheFileThenItsEndAndZero() {
        val r = serve(Device(), "get file:///etc/palm-build-info")
        assertEquals("ok 0\n", r.line)
        assertEquals("PRODUCT_VERSION_STRING=Lunacy Luna DE\n", String(r.data(0)))
        assertEquals(listOf(0 to 1, 2 to 0), r.oob)
    }

    @Test fun aRefusalIsNovacomdsLineEndingInANul() {
        val out = ByteArrayOutputStream()
        NovacomProtocol(Device()).serve(ByteArrayInputStream("get file:///nowhere\n".toByteArray()), out)
        assertArrayEquals("file open failed\n".toByteArray() + 0, out.toByteArray())
    }

    @Test fun putTakesTheClientsDataUntilStdinEnds() {
        val d = Device()
        val r = serve(d, "put file:///media/internal/.developer/a.ipk", packet(0, "abc".toByteArray()) + packet(0, "def".toByteArray()) + oob(0, 0))
        assertEquals("ok 0\n", r.line)
        assertEquals("abcdef", d.written.toString())
        assertEquals(listOf(0 to 0, 2 to 0), r.oob)
    }

    @Test fun runStreamsStdoutAndStderrThenTheReturnCode() {
        val d = Device()
        val r = serve(d, "run file:///bin/uname -a")
        assertEquals("ok 0\n", r.line)
        assertEquals("Linux\n", String(r.data(0)))
        assertEquals("a warning\n", String(r.data(1)))
        // Both streams end before the return code, which comes last.
        assertEquals(2 to 3, r.oob.last())
        assertEquals(setOf(0 to 1, 0 to 2), r.oob.dropLast(1).toSet())
        // The command started once.
        assertEquals(1, d.runs.get())
        assertEquals("/bin/uname" to listOf("-a"), d.lastRun)
    }

    @Test fun argumentsAreSplitAsNovacomdSplitThem() {
        val d = Device()
        serve(d, "run file:///bin/sh -c exec\\ /usr/bin/luna-send\\ -n\\ 1\\ palm://x/y\\ '{}'")
        assertEquals("/bin/sh" to listOf("-c", "exec /usr/bin/luna-send -n 1 palm://x/y '{}'"), d.lastRun)
    }

    @Test fun anUnknownCommandIsUnrecognized() {
        val out = ByteArrayOutputStream()
        NovacomProtocol(Device()).serve(ByteArrayInputStream("connect tcp-port://8080\n".toByteArray()), out)
        assertArrayEquals("unrecognized command\n".toByteArray() + 0, out.toByteArray())
        val tty = ByteArrayOutputStream()
        NovacomProtocol(Device()).serve(ByteArrayInputStream("open tty://0\n".toByteArray()), tty)
        assertArrayEquals("unrecognized command\n".toByteArray() + 0, tty.toByteArray())
    }

    private companion object {
        fun int(b: ByteArray, at: Int) =
            (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or ((b[at + 2].toInt() and 0xff) shl 16) or ((b[at + 3].toInt() and 0xff) shl 24)
        fun put(b: ByteArray, at: Int, v: Int) { for (i in 0..3) b[at + i] = (v shr (8 * i)).toByte() }
    }
}
