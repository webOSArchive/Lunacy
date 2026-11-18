package org.webosarchive.lunacy.desktop

import org.json.JSONObject
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.NovacomProtocol
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * novacomd's device side for Luna DE, so the SDK's tools reach it as they reach a TouchPad:
 * `novacom -l` lists it (`<port> <nduid> local luna-de`), and `palm-install`, `palm-launch`,
 * `palm-log` and `novacom get/put/run` work against `-d luna-de`. The protocol is LunaCore's
 * [NovacomProtocol], the one Lunacy speaks on Android; only the socket and the commands differ.
 *
 * **The socket.** A Unix-domain socket of this user's, which the SDK's novacomd finds and relays
 * to, as it relays to Lunacy through adb (webos-sdk-redux novacomd/src/host/local_relay.c): on
 * Linux `$XDG_RUNTIME_DIR/luna-de/novacom-<pid>.sock` (`/run/user/<uid>`: the SDK's systemd
 * service has PrivateTmp, so novacomd doesn't see the real /tmp), on macOS (and a Linux without a
 * runtime folder) `/tmp/luna-de-<uid>/novacom-<pid>.sock`, in a folder only this user can open
 * (novacomd runs as root, and reads it); on Windows `%ProgramData%\luna-de\<user>\novacom-<pid>.sock`,
 * in a folder only that user and SYSTEM (novacomd's service) can open.
 * One per running copy of Luna DE, so two never collide. The relay hands each client connection
 * through unchanged.
 *
 * **The commands.** Nothing of the host's runs: a `run` is one of a few commands of Luna DE's
 * own, each held inside its webOS root, which is all the tools use (measured against Lunacy,
 * 2026-11-17): `luna-send` (the bus, on the private bus, as root's was), `tail` (palm-log's `-f
 * /var/log/messages`), `grep` (palm-log without -f), `cat`, `ls`, `mkdir`, `rm`, `rmdir`, `uname`, and `/bin/sh -c` holding one
 * of those (the tools wrap luna-send in `exec … 2>/dev/null`). Anything else is not offered and
 * says so. `open tty://` (novaterm) and package scripts are never offered: Luna DE is an app
 * environment, not an operating system (Docs/luna-de.md, open question 2).
 */
class DesktopNovacom(
    private val main: DesktopScheduler,
    private val bus: Bus,
    /** Luna DE's webOS root. */
    private val root: File,
    private val desktopRoot: DesktopRoot,
    private val data: File,
) {
    private var socketFile: File? = null

    /**
     * This copy of Luna DE's nduid: forty hex digits, made once and kept with its data, as a device's was fixed.
     * The one id for the SDK's tools and for an app that asks (systemProperties/Get).
     */
    val nduid: String by lazy {
        val f = File(data, "nduid")
        f.takeIf { it.isFile }?.readText()?.trim()?.takeIf { Regex("[0-9a-f]{40}").matches(it) } ?: run {
            val b = ByteArray(20).also { SecureRandom().nextBytes(it) }
            b.joinToString("") { "%02x".format(it) }.also { runCatching { f.writeText(it + "\n") } }
        }
    }

    fun start() {
        val dir = socketFolder() ?: run { Log.w(Lunacy.TAG, "novacom: no folder for the socket"); return }
        // A socket left by a copy of Luna DE that didn't end cleanly answers nothing: it goes.
        dir.listFiles { f -> f.name.endsWith(".sock") }?.forEach { f ->
            val alive = runCatching { SocketChannel.open(UnixDomainSocketAddress.of(f.toPath())).use { true } }.getOrDefault(false)
            if (!alive) f.delete()
        }
        val file = File(dir, "novacom-${ProcessHandle.current().pid()}.sock")
        file.delete()
        val server = try {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).also { it.bind(UnixDomainSocketAddress.of(file.toPath())) }
        } catch (e: Exception) { Log.w(Lunacy.TAG, "novacom: no socket at $file: $e"); return }
        socketFile = file
        file.deleteOnExit()
        Runtime.getRuntime().addShutdownHook(Thread { file.delete() })
        Log.i(Lunacy.TAG, "novacom: listening at $file (nduid $nduid)")
        Thread({
            while (true) {
                val c = try { server.accept() } catch (e: IOException) { break }
                Thread({ serve(c) }, "novacom").start()
            }
        }, "novacom-accept").apply { isDaemon = true }.start()
    }

    /**
     * Where the sockets are, which novacomd (as root, or Windows' service) looks through. On
     * Linux and macOS a folder of this user's that only this user can open (in the runtime folder
     * where there is one, else /tmp); on Windows a folder under ProgramData.
     */
    private fun socketFolder(): File? {
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        if (windows) {
            // ProgramData is open to the machine's users, so each has a folder of their own under
            // it, which only they and SYSTEM (novacomd's service) can open.
            val base = System.getenv("ProgramData") ?: "C:\\ProgramData"
            val user = System.getProperty("user.name").orEmpty().replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "user" }
            val dir = File(File(base, "luna-de"), user)
            if (!dir.isDirectory && !dir.mkdirs()) return null
            return dir.takeIf { private(it) }
        }
        val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotEmpty() }?.let { File(it) }?.takeIf { it.isDirectory }
        val dir = if (runtime != null) File(runtime, "luna-de") else {
            val uid = runCatching { Files.getAttribute(Paths.get(System.getProperty("user.home")), "unix:uid") as Int }.getOrNull() ?: return null
            File("/tmp/luna-de-$uid")
        }
        if (!dir.isDirectory && !dir.mkdirs()) return null
        // Owner only: another user on the machine can't reach a socket inside it.
        dir.setReadable(false, false); dir.setWritable(false, false); dir.setExecutable(false, false)
        dir.setReadable(true, true); dir.setWritable(true, true); dir.setExecutable(true, true)
        return dir
    }

    /**
     * On Windows: [dir]'s access list made the owner's and SYSTEM's alone, with nothing inherited
     * from ProgramData. False if it couldn't be, and then no socket is offered there.
     */
    private fun private(dir: File): Boolean = try {
        val path = dir.toPath()
        val view = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView::class.java) ?: throw IOException("no access lists")
        val lookup = path.fileSystem.userPrincipalLookupService
        val all = java.nio.file.attribute.AclEntryPermission.values().toSet()
        val inherit = setOf(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT, java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT)
        fun entry(who: java.nio.file.attribute.UserPrincipal) = java.nio.file.attribute.AclEntry.newBuilder()
            .setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(who).setPermissions(all).setFlags(inherit).build()
        view.acl = listOf(entry(view.owner), entry(lookup.lookupPrincipalByName("NT AUTHORITY\\SYSTEM")))
        true
    } catch (e: Exception) {
        Log.w(Lunacy.TAG, "novacom: can't make $dir private: $e"); false
    }

    private fun serve(c: SocketChannel) {
        try {
            // Streams of its own over the channel: Channels' streams share the channel's blocking
            // lock, so the protocol's reader, waiting on the client, would hold up every write.
            val input = object : InputStream() {
                override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }
                override fun read(b: ByteArray, off: Int, len: Int): Int = if (len == 0) 0 else c.read(java.nio.ByteBuffer.wrap(b, off, len))
            }
            val output = object : OutputStream() {
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    val buf = java.nio.ByteBuffer.wrap(b, off, len)
                    while (buf.hasRemaining()) c.write(buf)
                }
            }
            NovacomProtocol(device).serve(input, output)
        } finally {
            runCatching { c.shutdownOutput() }
            runCatching { c.close() }
        }
    }

    // ---- the device: files in the webOS root, and Luna DE's own commands ----

    /**
     * A webOS path as a file in the webOS root, or null if it would be outside it. Only webOS's
     * own folders exist here; `..` can't climb out.
     */
    private fun inRoot(path: String): File? {
        val p = if (path.startsWith("/")) path else "/$path"
        val mapped = desktopRoot.mapPaths(p)
        if (mapped == p) return null
        val f = File(mapped)
        val canonical = runCatching { f.canonicalFile }.getOrNull() ?: return null
        val top = runCatching { root.canonicalFile }.getOrNull() ?: return null
        return if (canonical.path == top.path || canonical.path.startsWith(top.path + File.separator)) f else null
    }

    private val device = object : NovacomProtocol.Device {
        override fun nduid() = this@DesktopNovacom.nduid
        override fun log(line: String) { Log.i(Lunacy.TAG, "novacom: $line") }

        override fun read(path: String): InputStream {
            val f = inRoot(path) ?: throw NovacomProtocol.Refused("file open failed\n")
            return try { FileInputStream(f) } catch (e: IOException) { throw NovacomProtocol.Refused("file open failed\n") }
        }

        override fun write(path: String): OutputStream {
            val f = inRoot(path) ?: throw NovacomProtocol.Refused("file open failed\n")
            return try { FileOutputStream(f) } catch (e: IOException) { throw NovacomProtocol.Refused("file open failed\n") }
        }

        override fun run(path: String, args: List<String>): NovacomProtocol.Running {
            val name = commandName(path) ?: throw NovacomProtocol.Refused("file does not exist\n")
            if (name == "sh") {
                // `sh -c "<one command>"`, as the tools wrap luna-send.
                val i = args.indexOf("-c")
                if (i < 0 || i + 1 >= args.size) return finished(2, err = "sh: Luna DE runs only sh -c with one command\n")
                val line = Shell.parse(args[i + 1]) ?: return finished(2, err = "sh: Luna DE runs one command, without pipes, lists or redirection but to /dev/null\n")
                val inner = commandName(line.words[0]) ?: line.words[0]
                return command(inner, line.words.drop(1), quietErrors = line.dropErrors, quietOutput = line.dropOutput)
            }
            return command(name, args)
        }
    }

    /** A command's name from its path, if it is in one of the folders commands are in. */
    private fun commandName(path: String): String? {
        val p = if (path.startsWith("/")) path else "/$path"
        val dir = p.substringBeforeLast('/')
        if (dir !in setOf("/bin", "/usr/bin", "/sbin", "/usr/sbin", "")) return null
        val name = p.substringAfterLast('/')
        return name.takeIf { it in COMMANDS }
    }

    private fun command(name: String, args: List<String>, quietErrors: Boolean = false, quietOutput: Boolean = false): NovacomProtocol.Running {
        val job = Job(quietErrors, quietOutput)
        when (name) {
            "luna-send" -> lunaSend(args, job)
            "tail" -> job.thread { tail(args, job) }
            "cat" -> job.thread { cat(args, job) }
            "grep" -> job.thread { grep(args, job) }
            "ls" -> job.thread { ls(args, job) }
            "mkdir" -> job.thread { mkdir(args, job) }
            "rm" -> job.thread { rm(args, job) }
            "rmdir" -> job.thread { rmdir(args, job) }
            "uname" -> job.thread { job.out(if (args.contains("-a")) "Linux luna-de webOS\n" else "Linux\n"); 0 }
            else -> job.thread { job.err("$name: not offered by Luna DE\n"); 127 }
        }
        return job
    }

    private fun finished(code: Int, err: String) = Job(false, false).also { j -> j.thread { j.err(err); code } }

    // ---- the commands ----

    /** luna-send [-n count] [-a appId] [-f] [-P] [-i] <url> [<json>], as Lunacy's luna-send.js: each reply on a line. */
    private fun lunaSend(args: List<String>, job: Job) {
        var count = 0; var pretty = false; var pub = false
        val rest = ArrayList<String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "-n" -> { count = args.getOrNull(++i)?.toIntOrNull() ?: 0 }
                Regex("-n\\d+").matches(a) -> count = a.drop(2).toInt()
                a == "-a" || a == "-m" || a == "-w" -> i++
                a == "-f" -> pretty = true
                a == "-P" -> pub = true
                Regex("-[iqlS]").matches(a) -> {}
                else -> rest += a
            }
            i++
        }
        if (rest.isEmpty()) { job.thread { job.err("Usage: luna-send [-n count] [-a appId] [-f] <url> <payload>\n"); 1 }; return }
        val url = rest[0]
        val payload = rest.getOrElse(1) { "{}" }
        if (runCatching { org.json.JSONTokener(payload).nextValue() }.isFailure) { job.thread { job.err("Invalid JSON: $payload\n"); 1 }; return }
        var seen = 0
        val stays = Bus.staysOpen(url, payload)
        main.post {
            if (job.done) return@post
            val call = bus.call(NovacomProtocol.CALLER, url, payload, privateBus = !pub) { reply ->
                if (job.done) return@call
                val line = if (pretty) runCatching { JSONObject(reply).toString(4) }.getOrDefault(reply) else reply.replace("\n", " ")
                job.out(line + "\n")
                // A call that isn't a subscription has had its one reply; -n stops after that many.
                if (!stays || (count > 0 && ++seen >= count)) job.finish(0)
            }
            job.onDestroy { main.post { call.cancel() } }
            if (job.done) call.cancel()
        }
    }

    /** tail [-f] [-n N | -N] <file>: its last lines (10), then, with -f, what is added, until the client goes. */
    private fun tail(args: List<String>, job: Job): Int {
        var follow = false; var lines = 10; var file: String? = null
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "-f" || a == "-F" -> follow = true
                a == "-n" -> lines = args.getOrNull(++i)?.toIntOrNull() ?: 10
                Regex("-\\d+").matches(a) -> lines = a.drop(1).toInt()
                else -> file = a
            }
            i++
        }
        val f = file?.let { inRoot(it) } ?: run { job.err("tail: can't open '${file ?: ""}'\n"); return 1 }
        if (!f.isFile) { job.err("tail: can't open '$file': No such file or directory\n"); return 1 }
        val text = runCatching { f.readText() }.getOrDefault("")
        val all = text.split('\n').let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        all.takeLast(lines).forEach { job.out(it + "\n") }
        if (!follow) return 0
        var pos = f.length()
        while (!job.done) {
            Thread.sleep(250)
            val len = f.length()
            // The log starts again empty when it rotates (SysLog): from the top, then.
            if (len < pos) pos = 0
            if (len > pos) {
                java.io.RandomAccessFile(f, "r").use { r -> r.seek(pos); val b = ByteArray((len - pos).toInt()); r.readFully(b); job.outBytes(b) }
                pos = len
            }
        }
        return 0
    }

    private fun cat(args: List<String>, job: Job): Int {
        var code = 0
        for (a in args.filter { !it.startsWith("-") || it == "-" }) {
            val f = inRoot(a)
            if (f == null || !f.isFile) { job.err("cat: can't open '$a': No such file or directory\n"); code = 1; continue }
            f.inputStream().use { s -> val b = ByteArray(16384); while (true) { val n = s.read(b); if (n < 0) break; job.outBytes(b.copyOf(n)) } }
        }
        return code
    }

    /**
     * grep [-i] [-v] [-c] [-F] [-E] <pattern> <file>…: palm-log without -f greps the system log
     * for an app's id. The pattern is a regular expression (Java's, which reads an app id or a
     * plain word as busybox's did); 0 when a line matched, 1 when none did, 2 on an error.
     */
    private fun grep(args: List<String>, job: Job): Int {
        var ignoreCase = false; var invert = false; var count = false; var fixed = false
        var pattern: String? = null
        val files = ArrayList<String>()
        for (a in args) {
            if (pattern == null && a.startsWith("-") && a.length > 1) {
                for (f in a.drop(1)) when (f) {
                    'i' -> ignoreCase = true; 'v' -> invert = true; 'c' -> count = true
                    'F' -> fixed = true; 'E' -> {}
                    else -> { job.err("grep: unrecognized option '-$f'\n"); return 2 }
                }
            } else if (pattern == null) pattern = a else files += a
        }
        if (pattern == null || files.isEmpty()) { job.err("Usage: grep [-icvFE] PATTERN FILE...\n"); return 2 }
        val re = try {
            Regex(if (fixed) Regex.escape(pattern) else pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        } catch (e: Exception) { job.err("grep: bad regex '$pattern'\n"); return 2 }
        var matched = 0; var error = false
        for (name in files) {
            val f = inRoot(name)
            if (f == null || !f.isFile) { job.err("grep: $name: No such file or directory\n"); error = true; continue }
            var n = 0
            f.bufferedReader().useLines { lines ->
                for (line in lines) if (re.containsMatchIn(line) != invert) {
                    n++
                    if (!count) job.out((if (files.size > 1) "$name:" else "") + line + "\n")
                }
            }
            if (count) job.out((if (files.size > 1) "$name:" else "") + "$n\n")
            matched += n
        }
        return if (error) 2 else if (matched > 0) 0 else 1
    }

    private fun ls(args: List<String>, job: Job): Int {
        val long = args.any { it.startsWith("-") && it.contains('l') }
        val paths = args.filter { !it.startsWith("-") }.ifEmpty { listOf("/") }
        var code = 0
        for (a in paths) {
            val f = inRoot(a)
            if (f == null || !f.exists()) { job.err("ls: $a: No such file or directory\n"); code = 1; continue }
            val list = if (f.isDirectory) f.listFiles()?.sortedBy { it.name }.orEmpty() else listOf(f)
            for (e in list) job.out(if (long) "%s %10d %s\n".format(if (e.isDirectory) "drwxr-xr-x" else "-rw-r--r--", e.length(), e.name) else e.name + "\n")
        }
        return code
    }

    private fun mkdir(args: List<String>, job: Job): Int {
        val parents = args.contains("-p")
        var code = 0
        for (a in args.filter { !it.startsWith("-") }) {
            val f = inRoot(a) ?: run { job.err("mkdir: can't create directory '$a': Permission denied\n"); code = 1; null } ?: continue
            val ok = if (parents) f.isDirectory || f.mkdirs() else f.mkdir()
            if (!ok) { job.err("mkdir: can't create directory '$a'\n"); code = 1 }
        }
        return code
    }

    private fun rm(args: List<String>, job: Job): Int {
        val force = args.any { it.startsWith("-") && it.contains('f') }
        val recursive = args.any { it.startsWith("-") && (it.contains('r') || it.contains('R')) }
        var code = 0
        for (a in args.filter { !it.startsWith("-") }) {
            val f = inRoot(a)
            if (f == null || !f.exists()) { if (!force) { job.err("rm: can't remove '$a': No such file or directory\n"); code = 1 }; continue }
            val ok = if (f.isDirectory) recursive && f.deleteRecursively() else f.delete()
            if (!ok) { job.err("rm: can't remove '$a'\n"); code = 1 }
        }
        return code
    }

    private fun rmdir(args: List<String>, job: Job): Int {
        var code = 0
        for (a in args.filter { !it.startsWith("-") }) {
            val f = inRoot(a)
            if (f == null || !f.isDirectory || !f.delete()) { job.err("rmdir: '$a': Directory not empty or not there\n"); code = 1 }
        }
        return code
    }

    // ---- a command of Luna DE's own, as NovacomProtocol runs it ----

    /** Its output as streams the protocol reads; its end, its return code. */
    private class Job(private val quietErrors: Boolean, private val quietOutput: Boolean) : NovacomProtocol.Running {
        private val outQ = LinkedBlockingQueue<ByteArray>()
        private val errQ = LinkedBlockingQueue<ByteArray>()
        private val ended = CountDownLatch(1)
        @Volatile private var code = 0
        @Volatile var done = false
            private set
        private val onDestroy = ArrayList<() -> Unit>()

        fun out(s: String) = outBytes(s.toByteArray())
        fun outBytes(b: ByteArray) { if (!done && !quietOutput) outQ.add(b) }
        fun err(s: String) { if (!done && !quietErrors) errQ.add(s.toByteArray()) }

        @Synchronized fun finish(c: Int) {
            if (done) return
            code = c; done = true
            outQ.add(END); errQ.add(END)
            ended.countDown()
        }
        @Synchronized fun onDestroy(f: () -> Unit) { if (done) f() else onDestroy += f }

        /** Runs [body] on a thread of its own; its value is the return code. */
        fun thread(body: () -> Int) = Thread({
            val c = try { body() } catch (e: InterruptedException) { 0 } catch (e: Exception) { err("${e.message}\n"); 1 }
            finish(c)
        }, "novacom-command").apply { isDaemon = true; start() }

        override val stdout: InputStream = QueueStream(outQ)
        override val stderr: InputStream = QueueStream(errQ)
        override val stdin: OutputStream? = null
        override fun waitFor(): Int { ended.await(); return code }
        override fun signal(signo: Int) = destroy()
        override fun destroy() {
            val fs = synchronized(this) { onDestroy.toList().also { onDestroy.clear() } }
            fs.forEach { runCatching { it() } }
            finish(SIGNALLED)
        }

        private class QueueStream(private val q: LinkedBlockingQueue<ByteArray>) : InputStream() {
            private var cur: ByteArray? = null
            private var at = 0
            private var eof = false
            override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (eof) return -1
                while (cur == null || at >= cur!!.size) {
                    val next = q.poll(1, TimeUnit.HOURS) ?: continue
                    if (next === END) { eof = true; return -1 }
                    cur = next; at = 0
                }
                val n = minOf(len, cur!!.size - at)
                System.arraycopy(cur!!, at, b, off, n); at += n
                return n
            }
        }

        companion object {
            private val END = ByteArray(0)
            /** A command ended by the client's signal or its going: 128 + SIGTERM, as a shell reports it. */
            const val SIGNALLED = 143
        }
    }

    /** One simple command, as `sh -c` would read it: words, quotes, an `exec` in front, and redirection to /dev/null only. */
    private class Shell(val words: List<String>, val dropErrors: Boolean, val dropOutput: Boolean) {
        companion object {
            fun parse(line: String): Shell? {
                val words = ArrayList<String>()
                val cur = StringBuilder()
                var inWord = false
                var i = 0
                while (i < line.length) {
                    val c = line[i]
                    when {
                        c == '\'' -> { val end = line.indexOf('\'', i + 1); if (end < 0) return null; cur.append(line, i + 1, end); i = end; inWord = true }
                        c == '"' -> {
                            i++
                            while (i < line.length && line[i] != '"') {
                                if (line[i] == '\\' && i + 1 < line.length && line[i + 1] in "\"\\$`") i++
                                if (line[i] == '$' || line[i] == '`') return null
                                cur.append(line[i]); i++
                            }
                            if (i >= line.length) return null
                            inWord = true
                        }
                        c == '\\' && i + 1 < line.length -> { cur.append(line[i + 1]); i++; inWord = true }
                        c.isWhitespace() -> if (inWord) { words += cur.toString(); cur.setLength(0); inWord = false }
                        c in "|;&<()$`" -> if (!(c == '&' && cur.toString() == "2>" )) return null else { cur.append(c); inWord = true }
                        else -> { cur.append(c); inWord = true }
                    }
                    i++
                }
                if (inWord) words += cur.toString()
                var dropErrors = false; var dropOutput = false
                val kept = ArrayList<String>()
                var w = 0
                while (w < words.size) {
                    val t = words[w]
                    val redirect = Regex("^([12]?)>(.*)$").find(t)
                    if (redirect != null) {
                        val target = redirect.groupValues[2].ifEmpty { words.getOrNull(++w) ?: return null }
                        val fd = redirect.groupValues[1].ifEmpty { "1" }
                        when {
                            target == "/dev/null" -> if (fd == "2") dropErrors = true else dropOutput = true
                            target == "&1" && fd == "2" -> {}
                            else -> return null
                        }
                    } else kept += t
                    w++
                }
                if (kept.firstOrNull() == "exec") kept.removeAt(0)
                return if (kept.isEmpty()) null else Shell(kept, dropErrors, dropOutput)
            }
        }
    }

    private companion object {
        /** The commands Luna DE runs itself: what the SDK's tools use. */
        val COMMANDS = setOf("sh", "luna-send", "tail", "cat", "grep", "ls", "mkdir", "rm", "rmdir", "uname")
    }
}
