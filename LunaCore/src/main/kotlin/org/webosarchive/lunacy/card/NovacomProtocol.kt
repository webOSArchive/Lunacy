package org.webosarchive.lunacy.card

import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * novacomd's device side, the protocol: what the SDK's tools (`palm-install`, `palm-launch`,
 * `palm-log`, `novacom`) speak on a channel, so they can talk to Lunacy (through adb, on
 * Android) and to Luna DE (through novacomd's local relay, on a desktop). One connection is one
 * channel (webos-sdk-redux novacomd/src/novacom/commands_device.c): one command line, `verb
 * scheme://path args…`, tokens split on white space with `\` escaping; then `ok 0\n`, or an
 * error line ending in a NUL. After `ok`, both ways are packets: a 16-byte header (magic
 * 0xdecafbad, version 1, size, type: 0 stdout or data, 1 stderr, 2 out-of-band) and its payload.
 * Out-of-band messages are 20 bytes: end of a stream (0, fileno), a signal (1, signo), the return
 * code (2, code), a terminal size (3, rows, cols).
 *
 * - `get file://<path>` and `put file://<path>`: a file, as the platform finds it ([Device.read],
 *   [Device.write]); `/proc/nduid` is the device's nduid, as a webOS device's kernel answered it.
 * - `run file://<path> <args>`: a process, as the platform runs it ([Device.run]).
 * - `open tty://0` (novaterm): a login shell, where the platform has one ([Device.tty]).
 * - Anything else gets novacomd's own answer for a command it hasn't.
 *
 * Which commands exist and what they may reach is the platform's: Lunacy runs them in its webOS
 * root under busybox; Luna DE runs a few of its own, inside its root, and nothing of the host's.
 */
class NovacomProtocol(private val device: Device) {
    /** What the platform does for each command. */
    interface Device {
        fun nduid(): String
        /** A file to send; throws [Refused] with novacomd's answer if it can't be read. */
        fun read(path: String): InputStream
        /** A file to receive into; throws [Refused] if it can't be written. */
        fun write(path: String): OutputStream
        /** Starts a command; throws [Refused] with novacomd's answer if it can't be started. */
        fun run(path: String, args: List<String>): Running
        /** A login shell on a terminal, or null where there is none. */
        fun tty(): Running? = null
        /** Writes a line to the log for a command (the frequent nduid probes are left out). */
        fun log(line: String) {}
    }

    /** A command that has started. */
    interface Running {
        val stdout: InputStream
        val stderr: InputStream?
        val stdin: OutputStream?
        /** Blocks until it ends; its return code. */
        fun waitFor(): Int
        fun signal(signo: Int)
        fun destroy()
    }

    /** novacomd's answer for a command it won't or can't carry out ("file does not exist"). */
    class Refused(val reply: String) : Exception(reply)

    /** One channel, start to end; the caller closes the connection after. */
    fun serve(input: InputStream, output: OutputStream) {
        val out = Packets(output)
        try {
            val line = readLine(input) ?: return
            val cmd = Command.parse(line)
            if (cmd == null) { out.reply("unrecognized command\n"); return }
            // novacomd asks every few seconds whether the device is there; only the rest is news.
            if (cmd.path != "/proc/nduid") device.log("${cmd.verb} ${cmd.scheme}://${cmd.path} ${cmd.args.joinToString(" ")}")
            when ("${cmd.verb.lowercase()} ${cmd.scheme.lowercase()}") {
                "get file" -> get(cmd.path, out)
                "put file" -> put(cmd.path, input, out)
                "run file" -> run(input, out) { device.run(cmd.path, cmd.args) }
                "open tty" -> run(input, out) { device.tty() ?: throw Refused("unrecognized command\n") }
                else -> out.reply("unrecognized command\n")
            }
        } catch (e: IOException) {
            // The client went away.
        } catch (e: Exception) {
            Log.w(Lunacy.TAG, "novacom: $e")
        }
    }

    // ---- commands ----

    private fun get(path: String, out: Packets) {
        val stream: InputStream = if (path == "/proc/nduid") (device.nduid() + "\n").byteInputStream()
            else try { device.read(path) } catch (e: Refused) { return out.reply(e.reply) }
        out.ok()
        stream.use { s ->
            val buf = ByteArray(CHUNK)
            while (true) { val n = s.read(buf); if (n < 0) break; out.data(STDOUT, buf, n) }
        }
        out.eof(STDOUT)
        out.returnCode(0)
    }

    private fun put(path: String, input: InputStream, out: Packets) {
        val file = try { device.write(path) } catch (e: Refused) { return out.reply(e.reply) }
        out.ok()
        var code = 0
        file.use { f ->
            readPackets(input, onData = { b, n -> try { f.write(b, 0, n) } catch (e: IOException) { code = 1 } },
                onEof = { fileno -> fileno == STDIN }, onSignal = {})
        }
        out.eof(STDIN)
        out.returnCode(code)
    }

    private fun run(input: InputStream, out: Packets, begin: () -> Running) {
        val p = try { begin() } catch (e: Refused) { return out.reply(e.reply) }
        out.ok()
        fun pump(from: InputStream, fileno: Int) = Thread({
            val buf = ByteArray(CHUNK)
            try {
                while (true) { val n = from.read(buf); if (n < 0) break; out.data(fileno, buf, n) }
                out.eof(fileno)
            } catch (e: IOException) {}
        }, "novacom-out").apply { start() }
        val pumps = listOfNotNull(pump(p.stdout, STDOUT), p.stderr?.let { pump(it, STDERR) })
        // The client's packets: its stdin and its signals. The client closing the connection
        // (palm-log -f interrupted, say) ends the process, as the channel closing did.
        Thread({
            val stdin = p.stdin
            try {
                readPackets(input, onData = { b, n -> try { stdin?.write(b, 0, n); stdin?.flush() } catch (e: IOException) {} },
                    onEof = { fileno -> if (fileno == STDIN) runCatching { stdin?.close() }; false },
                    onSignal = { signo -> p.signal(signo) })
            } catch (e: Exception) {}
            p.destroy()
        }, "novacom-in").apply { isDaemon = true; start() }
        val code = p.waitFor()
        pumps.forEach { it.join() }
        runCatching { out.returnCode(code) }
    }

    // ---- the wire ----

    /**
     * Reads the client's packets until it closes or ends the conversation. Returns true if
     * [onEof] said that end of stream was the end, false if the connection closed first.
     */
    private fun readPackets(input: InputStream, onData: (ByteArray, Int) -> Unit, onEof: (Int) -> Boolean, onSignal: (Int) -> Unit): Boolean {
        val header = ByteArray(HEADER)
        var buf = ByteArray(CHUNK)
        while (true) {
            if (!readFully(input, header, HEADER)) return false
            if (int(header, 0) != MAGIC) throw IOException("bad packet magic")
            val size = int(header, 8)
            val type = int(header, 12)
            if (size < 0 || size > MAX_PACKET) throw IOException("bad packet size $size")
            if (size > buf.size) buf = ByteArray(size)
            if (!readFully(input, buf, size)) return false
            when (type) {
                TYPE_DATA -> onData(buf, size)
                TYPE_OOB -> if (size >= 8) when (int(buf, 0)) {
                    OOB_EOF -> if (onEof(int(buf, 4))) return true
                    OOB_SIGNAL -> onSignal(int(buf, 4))
                }
            }
        }
    }

    private fun readFully(input: InputStream, b: ByteArray, n: Int): Boolean {
        var got = 0
        while (got < n) { val r = input.read(b, got, n - got); if (r < 0) return false; got += r }
        return true
    }

    private fun readLine(input: InputStream): String? {
        val b = java.io.ByteArrayOutputStream()
        while (b.size() < MAX_LINE) {
            val c = input.read()
            if (c < 0) return null
            if (c == '\n'.code) return b.toString("UTF-8")
            b.write(c)
        }
        return null
    }

    /**
     * What the device writes. Synchronized: stdout's and stderr's pumps share it. Each packet
     * goes out in one write, header and payload together, as novacomd queued them: the novacom
     * client reads a packet's payload straight after its header without waiting, and takes a
     * payload that hasn't arrived yet for the connection closing.
     */
    private class Packets(private val out: OutputStream) {
        @Synchronized fun reply(line: String) { out.write(line.toByteArray() + 0); out.flush() }
        @Synchronized fun ok() { out.write(OK); out.flush() }
        fun data(fileno: Int, b: ByteArray, n: Int) = packet(if (fileno == STDERR) TYPE_ERR else TYPE_DATA, b, n)
        fun eof(fileno: Int) = oob(OOB_EOF, fileno)
        fun returnCode(code: Int) = oob(OOB_RETURN, code)
        private fun oob(message: Int, value: Int) {
            val m = ByteArray(OOB_SIZE); put(m, 0, message); put(m, 4, value)
            packet(TYPE_OOB, m, OOB_SIZE)
        }
        @Synchronized private fun packet(type: Int, b: ByteArray, n: Int) {
            val p = ByteArray(HEADER + n)
            put(p, 0, MAGIC); put(p, 4, 1); put(p, 8, n); put(p, 12, type)
            System.arraycopy(b, 0, p, HEADER, n)
            out.write(p); out.flush()
        }
    }

    /** A command line, split as novacomd's tokenizer splits it (commands.c). */
    class Command(val verb: String, val scheme: String, val path: String, val args: List<String>) {
        companion object {
            fun parse(line: String): Command? {
                val tokens = ArrayList<String>()
                val cur = StringBuilder()
                var inToken = false
                var escaped = false
                for (ch in line.trim()) {
                    when {
                        escaped -> { cur.append(ch); escaped = false }
                        ch == '\\' -> { escaped = true; inToken = true }
                        ch.isWhitespace() -> { if (inToken) { tokens += cur.toString(); cur.setLength(0); inToken = false } }
                        else -> { cur.append(ch); inToken = true }
                    }
                }
                if (inToken) tokens += cur.toString()
                if (tokens.isEmpty()) return null
                // Verb arguments (novacom's `run -t`, say) come before the url, and are ignored.
                val at = tokens.indexOfFirst { it.contains("://") }.takeIf { it > 0 } ?: return null
                val url = tokens[at]
                return Command(tokens[0], url.substringBefore("://"), url.substringAfter("://"), tokens.drop(at + 1))
            }
        }
    }

    companion object {
        /** Who a novacom command's luna-send calls the bus as: root's shell, on the private bus. */
        const val CALLER = "novacomd"
        private val OK = "ok 0\n".toByteArray()
        private const val MAGIC = 0xdecafbad.toInt()
        private const val HEADER = 16
        private const val OOB_SIZE = 20
        private const val TYPE_DATA = 0
        private const val TYPE_ERR = 1
        private const val TYPE_OOB = 2
        private const val OOB_EOF = 0
        private const val OOB_SIGNAL = 1
        private const val OOB_RETURN = 2
        private const val STDIN = 0
        private const val STDOUT = 1
        private const val STDERR = 2
        private const val CHUNK = 16 * 1024
        private const val MAX_PACKET = 1024 * 1024
        private const val MAX_LINE = 64 * 1024

        private fun int(b: ByteArray, at: Int) =
            (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or ((b[at + 2].toInt() and 0xff) shl 16) or ((b[at + 3].toInt() and 0xff) shl 24)
        private fun put(b: ByteArray, at: Int, v: Int) { for (i in 0..3) b[at + i] = (v shr (8 * i)).toByte() }
    }
}
