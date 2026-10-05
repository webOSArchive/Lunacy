package org.webosarchive.lunacy.card

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * novacomd's device side: what the SDK's tools (`palm-install`, `palm-launch`, `palm-log`,
 * `novacom`, `novaterm`) talk to on a webOS device, so they can talk to Lunacy. See
 * Docs/architecture.md, "Developer tools".
 *
 * **Reached through adb.** The socket is `localabstract:`[SOCKET] on the Android device. The
 * host's novacomd (webos-sdk-redux 0.4 and later) finds it through the adb server and lists
 * Lunacy beside any USB TouchPad; each client connection it is handed is spliced through to
 * here unchanged. Only a peer running as root or as adb's shell user is served: whoever can
 * reach this already holds adb, which is Android's developer mode as novacom access was
 * webOS's. Every other app on the device is refused (rule 10: who is calling comes from the
 * socket, never from what is sent).
 *
 * **The protocol** is the one novacomd's commands service speaks on each channel
 * (webos-sdk-redux novacomd/src/novacom/commands_device.c): one command line, `verb
 * scheme://path args…`, tokens split on white space with `\` escaping; then `ok 0\n`, or an
 * error line ending in a NUL. After `ok`, both ways are packets: a 16-byte header (magic
 * 0xdecafbad, version 1, size, type: 0 stdout or data, 1 stderr, 2 out-of-band) and its
 * payload. Out-of-band messages are 20 bytes: end of a stream (0, fileno), a signal (1,
 * signo), the return code (2, code), a terminal size (3, rows, cols).
 *
 * - `get file://<path>` and `put file://<path>`: a file in the webOS root ([WebosRoot]).
 *   `/proc/nduid` is Lunacy's nduid, as the device's kernel answered it.
 * - `run file://<path> <args>`: a process in the webOS root, as a package's script runs: busybox's
 *   commands, webOS's paths pointed into the root ([WebosRoot.mapPaths]), and luna-send on the
 *   private bus, as root's was. The caller is [CALLER].
 * - `open tty://0` (novaterm): a login shell on a terminal, through busybox's `script`.
 * - `connect tcp-port://`: not offered; it gets novacomd's own answer for a command it hasn't.
 *
 * **Ratchet item:** commands run with Lunacy's own permissions, as root's stand-in, as package
 * scripts do.
 */
class Novacom(private val webos: WebosRoot, private val nduid: () -> String) {
    private var server: LocalServerSocket? = null

    @Synchronized fun start() {
        if (server != null) return
        val s = try { LocalServerSocket(SOCKET) } catch (e: IOException) { Log.w(AppServer.TAG, "novacom: no socket: $e"); return }
        server = s
        Thread({
            while (true) {
                val c = try { s.accept() } catch (e: IOException) { break }
                val uid = runCatching { c.peerCredentials.uid }.getOrDefault(-1)
                if (uid != ROOT_UID && uid != SHELL_UID) {
                    Log.w(AppServer.TAG, "novacom: refused a connection from uid $uid")
                    runCatching { c.close() }
                    continue
                }
                Thread({ serve(c) }, "novacom").start()
            }
        }, "novacom-accept").apply { isDaemon = true }.start()
    }

    private fun serve(c: LocalSocket) {
        val input = c.inputStream
        val out = Packets(c.outputStream)
        try {
            val line = readLine(input) ?: return
            val cmd = Command.parse(line)
            if (cmd == null) { out.reply("unrecognized command\n"); return }
            // novacomd asks every few seconds whether Lunacy is here; only the rest is news.
            if (cmd.path != "/proc/nduid") Log.i(AppServer.TAG, "novacom: ${cmd.verb} ${cmd.scheme}://${cmd.path} ${cmd.args.joinToString(" ")}")
            webos.prepare()
            when ("${cmd.verb.lowercase()} ${cmd.scheme.lowercase()}") {
                "get file" -> get(cmd.path, input, out)
                "put file" -> put(cmd.path, input, out)
                "run file" -> run(cmd.path, cmd.args, input, out, tty = false)
                "open tty" -> run("/usr/bin/script", listOf("-q", "-c", "/bin/sh -l", "/dev/null"), input, out, tty = true)
                else -> out.reply("unrecognized command\n")
            }
        } catch (e: IOException) {
            // The client went away.
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "novacom: $e")
        } finally {
            // The client reads to the end of the connection after the return code. Shut it
            // down first: closing a LocalSocket alone doesn't wake the thread reading it.
            runCatching { c.shutdownOutput() }
            runCatching { c.shutdownInput() }
            runCatching { c.close() }
        }
    }

    // ---- commands ----

    private fun get(path: String, input: InputStream, out: Packets) {
        val stream: InputStream = if (path == "/proc/nduid") (nduid() + "\n").byteInputStream()
            else try { FileInputStream(resolve(path)) } catch (e: IOException) { return out.reply("file open failed\n") }
        out.ok()
        stream.use { s ->
            val buf = ByteArray(CHUNK)
            while (true) { val n = s.read(buf); if (n < 0) break; out.data(STDOUT, buf, n) }
        }
        out.eof(STDOUT)
        out.returnCode(0)
    }

    private fun put(path: String, input: InputStream, out: Packets) {
        val file = try { FileOutputStream(resolve(path)) } catch (e: IOException) { return out.reply("file open failed\n") }
        out.ok()
        var code = 0
        file.use { f ->
            readPackets(input, onData = { b, n -> try { f.write(b, 0, n) } catch (e: IOException) { code = 1 } },
                onEof = { fileno -> fileno == STDIN }, onSignal = {})
        }
        out.eof(STDIN)
        out.returnCode(code)
    }

    private fun run(path: String, args: List<String>, input: InputStream, out: Packets, tty: Boolean) {
        val exe = resolve(path)
        if (!exe.exists()) return out.reply("file does not exist\n")
        if (!exe.isFile || !exe.canExecute()) return out.reply("not an executable file\n")
        val pb = ProcessBuilder(listOf(exe.path) + args.map { webos.mapPaths(it) }).directory(webos.root)
        pb.environment().apply {
            putAll(webos.environment(CALLER))
            put("USER", "root"); put("LOGNAME", "root"); put("SHELL", webos.mapPaths("/bin/sh"))
            if (tty) put("TERM", "linux")
        }
        val p = try { pb.start() } catch (e: IOException) { return out.reply("problem with path\n") }
        out.ok()
        fun pump(from: InputStream, fileno: Int) = Thread({
            val buf = ByteArray(CHUNK)
            try {
                while (true) { val n = from.read(buf); if (n < 0) break; out.data(fileno, buf, n) }
                out.eof(fileno)
            } catch (e: IOException) {}
        }, "novacom-out").apply { start() }
        val pumps = listOf(pump(p.inputStream, STDOUT), pump(p.errorStream, STDERR))
        // The client's packets: its stdin and its signals. The client closing the connection
        // (palm-log -f interrupted, say) ends the process, as the channel closing did.
        Thread({
            val stdin = p.outputStream
            try {
                readPackets(input, onData = { b, n -> try { stdin.write(b, 0, n); stdin.flush() } catch (e: IOException) {} },
                    onEof = { fileno -> if (fileno == STDIN) runCatching { stdin.close() }; false },
                    onSignal = { signo -> signal(p, signo) })
            } catch (e: Exception) {}
            p.destroy()
        }, "novacom-in").apply { isDaemon = true; start() }
        val code = p.waitFor()
        pumps.forEach { it.join() }
        runCatching { out.returnCode(code) }
    }

    /** A webOS path as a file in the webOS root. Paths outside webOS's own folders (/proc, /dev) are Android's. */
    private fun resolve(path: String): File =
        // novacomd ran from /, so `run file://bin/cat` named /bin/cat.
        File(webos.mapPaths(if (path.startsWith("/")) path else "/$path"))

    private fun signal(p: Process, signo: Int) {
        val pid = runCatching { p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(p) }.getOrNull()
        if (pid != null) android.os.Process.sendSignal(pid, signo) else p.destroy()
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
        /** The abstract socket the host's novacomd asks adb for. */
        const val SOCKET = "org.webosarchive.lunacy.novacomd"
        /** Who a novacom command's luna-send calls the bus as: root's shell, on the private bus. */
        const val CALLER = "novacomd"
        private const val ROOT_UID = 0
        private const val SHELL_UID = 2000
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
