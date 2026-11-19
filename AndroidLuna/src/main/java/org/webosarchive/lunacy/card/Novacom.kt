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
 * **The protocol** is LunaCore's [NovacomProtocol], shared with Luna DE. What is Android's own
 * is here: the socket and who may use it, and how a command runs:
 *
 * - `get file://<path>` and `put file://<path>`: a file in the webOS root ([WebosRoot]).
 * - `run file://<path> <args>`: a process in the webOS root, as a package's script runs: busybox's
 *   commands, webOS's paths pointed into the root ([WebosRoot.mapPaths]), and luna-send on the
 *   private bus, as root's was. The caller is [NovacomProtocol.CALLER].
 * - `open tty://0` (novaterm): a login shell on a terminal, through busybox's `script`.
 *
 * **Ratchet item:** commands run with Lunacy's own permissions, as root's stand-in, as package
 * scripts do.
 */
class Novacom(private val webos: WebosRoot, private val nduid: () -> String) {
    /**
     * The newest shell's turn at the process's one listener: the socket's name can't be bound
     * twice, and Android creates a new shell before it destroys the one going. A connection
     * goes to the shell that is up, whose [WebosRoot] has a luna-send that knows its tokens.
     */
    fun start() {
        current = this
        val s = synchronized(Novacom) {
            if (server != null) return
            try { LocalServerSocket(SOCKET) } catch (e: IOException) { Log.w(AppServer.TAG, "novacom: no socket: $e"); return }.also { server = it }
        }
        Thread({
            while (true) {
                val c = try { s.accept() } catch (e: IOException) { break }
                val uid = runCatching { c.peerCredentials.uid }.getOrDefault(-1)
                if (uid != ROOT_UID && uid != SHELL_UID) {
                    Log.w(AppServer.TAG, "novacom: refused a connection from uid $uid")
                    runCatching { c.close() }
                    continue
                }
                val up = current
                Thread({ up?.serve(c) }, "novacom").start()
            }
        }, "novacom-accept").apply { isDaemon = true }.start()
    }

    private fun serve(c: LocalSocket) {
        try {
            webos.prepare()
            NovacomProtocol(device).serve(c.inputStream, c.outputStream)
        } finally {
            // The client reads to the end of the connection after the return code. Shut it
            // down first: closing a LocalSocket alone doesn't wake the thread reading it.
            runCatching { c.shutdownOutput() }
            runCatching { c.shutdownInput() }
            runCatching { c.close() }
        }
    }

    /** Android's side of each command: files and processes in the webOS root. */
    private val device = object : NovacomProtocol.Device {
        override fun nduid() = this@Novacom.nduid()
        override fun log(line: String) { Log.i(AppServer.TAG, "novacom: $line") }

        override fun read(path: String): InputStream =
            try { FileInputStream(resolve(path)) } catch (e: IOException) { throw NovacomProtocol.Refused("file open failed\n") }

        override fun write(path: String): OutputStream =
            try { FileOutputStream(resolve(path)) } catch (e: IOException) { throw NovacomProtocol.Refused("file open failed\n") }

        override fun run(path: String, args: List<String>) = start(path, args, tty = false)
        override fun tty() = start("/usr/bin/script", listOf("-q", "-c", "/bin/sh -l", "/dev/null"), tty = true)
    }

    private fun start(path: String, args: List<String>, tty: Boolean): NovacomProtocol.Running {
        val exe = resolve(path)
        if (!exe.exists()) throw NovacomProtocol.Refused("file does not exist\n")
        if (!exe.isFile || !exe.canExecute()) throw NovacomProtocol.Refused("not an executable file\n")
        val pb = ProcessBuilder(listOf(exe.path) + args.map { webos.mapPaths(it) }).directory(webos.root)
        pb.environment().apply {
            putAll(webos.environment(NovacomProtocol.CALLER))
            put("USER", "root"); put("LOGNAME", "root"); put("SHELL", webos.mapPaths("/bin/sh"))
            if (tty) put("TERM", "linux")
        }
        val p = try { pb.start() } catch (e: IOException) { throw NovacomProtocol.Refused("problem with path\n") }
        return object : NovacomProtocol.Running {
            override val stdout: InputStream = p.inputStream
            override val stderr: InputStream = p.errorStream
            override val stdin: OutputStream = p.outputStream
            override fun waitFor() = p.waitFor()
            override fun signal(signo: Int) = signal(p, signo)
            override fun destroy() = p.destroy()
        }
    }

    /** A webOS path as a file in the webOS root. Paths outside webOS's own folders (/proc, /dev) are Android's. */
    private fun resolve(path: String): File =
        // novacomd ran from /, so `run file://bin/cat` named /bin/cat.
        File(webos.mapPaths(if (path.startsWith("/")) path else "/$path"))

    private fun signal(p: Process, signo: Int) {
        val pid = runCatching { p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(p) }.getOrNull()
        if (pid != null) android.os.Process.sendSignal(pid, signo) else p.destroy()
    }

    companion object {
        /** The abstract socket the host's novacomd asks adb for. */
        const val SOCKET = "org.webosarchive.lunacy.novacomd"
        private var server: LocalServerSocket? = null
        @Volatile private var current: Novacom? = null
        private const val ROOT_UID = 0
        private const val SHELL_UID = 2000
    }
}
