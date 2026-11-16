package org.webosarchive.lunacy.card

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Lunacy's copy of the webOS root filesystem (`files/webos`), which JS services and package
 * scripts see as `/`. It holds what the TouchPad's rootfs held for them: the service frameworks
 * and system JS services under `/usr/palm`, db8's system kinds under `/etc/palm`, busybox's
 * commands in `/bin` and `/usr/bin`, curl and luna-send, and `/media/cryptofs/apps` (a link to
 * the installed packages) and `/media/internal`. See Docs/architecture.md, "The webOS root".
 *
 * **The ROM.** What Lunacy ships under `assets/rootfs/` is laid down like a device's ROM, and
 * like a package manager's config files it is updated with care: after an APK update a file is
 * replaced only if it still holds what the last APK shipped. A file a package's script changed
 * (the webOS Community Account Manager patches the palmprofile service in place, as it does on
 * a TouchPad) is kept, and the log says so.
 */
class WebosRoot(private val context: Context, private val bus: Bus, installed: File) {
    val root = File(context.filesDir, "webos")
    /** Lunacy's own scripts for Node: the service host, curl and luna-send. Not webOS's. */
    val host = File(context.filesDir, "jshost")
    val nativeDir: String = context.applicationInfo.nativeLibraryDir
    val node = File(nativeDir, "liblunacynode.so")
    private val busybox = File(nativeDir, "libbusybox.so")
    private val installedDir = installed
    @Volatile private var prepared = false
    val lunaSend = LunaSendServer(bus, File(root, "var/run/luna-send.sock"))
    /** The root's files, which LunaCore lays down (the ROM, the folders, the build info). */
    private val rom = RomFiles(AndroidAssets(context.assets), root)

    /** Builds root/ once per run, and lays the ROM down again after each APK update. */
    @Synchronized fun prepare() {
        if (prepared) return
        val stamp = File(root, ".lunacy-apk")
        val apk = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (!stamp.isFile || stamp.readText() != apk) {
            // Before the ROM, whose manifest this replaces: the tree the earlier scheme copied
            // wholesale on every update has nothing a script could have changed.
            if (!File(root, RomFiles.ROM_MANIFEST).isFile) File(root, "usr").deleteRecursively()
            rom.syncRom()
            host.deleteRecursively()
            rom.copyAssets("lunacy/services", host)
            installTools()
            stamp.writeText(apk)
        }
        rom.makeFolders(installedDir)
        writeBuildInfo()
        prepared = true
    }

    private fun writeBuildInfo() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION") val code = info.versionCode
        rom.writeBuildInfo(info.versionName ?: "", code.toString())
    }

    // ---- commands ----

    /**
     * webOS's userland. The TouchPad's `/bin` and `/usr/bin` were busybox, and package scripts
     * are written for it (Android 5 has no sed, awk, head or basename), so every busybox
     * command gets its link at its own path, as busybox installs itself. Before them go
     * Lunacy's own: `/bin/sh` is busybox's ash, as on webOS; curl and luna-send run in Node;
     * `mount` answers the remount scripts do before writing to the rootfs; `ionice` runs its
     * command without the priority Android won't let an app set.
     */
    private fun installTools() {
        // Links from an earlier APK point at its native library folder, which moves on update.
        val list = File(root, TOOLS_LIST)
        if (list.isFile) list.readLines().forEach { File(root, it).delete() }
        val made = ArrayList<String>()
        fun script(rel: String, body: String) {
            File(root, rel).apply { parentFile?.mkdirs(); delete(); writeText("#!${root.path}/bin/sh\n$body"); setExecutable(true, false) }
            made += rel
        }
        fun nodeTool(js: String) = "LD_LIBRARY_PATH=$nativeDir exec ${node.path} ${File(host, js).path} \"\$@\"\n"
        if (busybox.isFile) {
            val bb = File(root, "bin/busybox")
            bb.delete(); bb.parentFile?.mkdirs(); Os.symlink(busybox.path, bb.path); made += "bin/busybox"
            val applets = try {
                ProcessBuilder(bb.path, "--list-full").redirectErrorStream(true).start().inputStream.bufferedReader().readLines()
            } catch (e: IOException) { Log.w(AppServer.TAG, "rootfs: busybox won't run: $e"); emptyList() }
            // Lunacy's own tools take these names instead.
            val ours = setOf("bin/mount", "usr/bin/curl", "usr/bin/luna-send", "bin/ionice")
            for (a in applets.map { it.trim() }.filter { it.isNotEmpty() && it !in ours }) {
                val f = File(root, a)
                if (f.exists() || runCatching { Os.readlink(f.path) }.isSuccess) f.delete()
                f.parentFile?.mkdirs()
                runCatching { Os.symlink(busybox.path, f.path); made += a }
            }
            Log.i(AppServer.TAG, "rootfs: ${applets.size} busybox commands")
        } else {
            // Without busybox (a build that didn't fetch it), sh is at least a shell.
            link("/system/bin/sh", File(root, "bin/sh")); made += "bin/sh"
        }
        script("usr/bin/curl", nodeTool("curl.js"))
        script("usr/bin/curl11", nodeTool("curl.js"))
        script("usr/bin/luna-send", nodeTool("luna-send.js"))
        // `mount -o remount,rw /` is what a script says before it writes to the rootfs, which
        // here is always writable; any other mount is one Lunacy can't do.
        script("bin/mount", "case \" \$* \" in *remount*) exit 0;; esac\necho \"mount: permission denied\" >&2\nexit 1\n")
        // `ionice -c3 cp ...` is how the photos service copies its thumbnails. Setting an I/O
        // priority (ioprio_set) is a system call Android's seccomp filter kills an app's
        // process for, so the class is dropped and the command runs as it is.
        script("bin/ionice", "while [ \$# -gt 0 ]; do case \"\$1\" in -c|-n) shift 2;; -c*|-n*|-t) shift;; " +
            "-p) exit 0;; --) shift; break;; *) break;; esac; done\n[ \$# -gt 0 ] || { echo \"none: prio 0\"; exit 0; }\nexec \"\$@\"\n")
        list.writeText(made.joinToString("") { "$it\n" })
    }

    private fun link(target: String, at: File) = rom.link(target, at)

    // ---- processes in the webOS root ----

    /**
     * Absolute webOS paths in a shell command line or script, pointed into root/. The same
     * rule as host.js's realAll: a path under one of webOS's own top-level folders, starting a
     * word, a quoted string or an assignment. A URL's path (`file:///usr/…`) is left alone, so
     * a luna-send payload still names webOS paths.
     */
    fun mapPaths(s: String): String = mapPaths(s, root.path)

    /** The environment a process in the webOS root gets, calling the bus as [caller]. */
    fun environment(caller: String): Map<String, String> = mapOf(
        "PATH" to listOf("usr/sbin", "usr/bin", "sbin", "bin").joinToString(":") { "${root.path}/$it" } + ":/system/bin",
        "HOME" to File(root, "home/root").path,
        "TMPDIR" to File(root, "tmp").path,
        "LUNACY_WEBOS_ROOT" to root.path,
        // The folders an absolute path names to be a webOS one (ROOTS): Node's shims build theirs from it.
        "LUNACY_WEBOS_DIRS" to ROOTS.joinToString("|"),
        "LUNACY_BUS" to lunaSend.address,
        "LUNACY_BUS_TOKEN" to lunaSend.token(caller),
    )

    companion object {
        const val TOOLS_LIST = ".lunacy-tools"
        /**
         * The top folders whose absolute paths are webOS's; everything else (/data, /system,
         * /proc, /dev) is Android's. One list, which the JS services' shims get too
         * (`LUNACY_WEBOS_DIRS`), so the two sides can't drift.
         */
        val ROOTS = listOf("usr", "media", "bin", "sbin", "var", "etc", "tmp", "home", "opt", "lib")
        // The folder name ends the word or is followed by a slash: /library and /usrx are not webOS's.
        private val PATHS = Regex("(^|[\\s'\"=(;|&<>`])(/(?:${ROOTS.joinToString("|")})(?=$|[/\\s'\";|&<>)`])(?:/[^\\s'\";|&<>)`]*)?)", RegexOption.MULTILINE)

        internal fun mapPaths(s: String, rootPath: String): String = PATHS.replace(s) { m -> m.groupValues[1] + rootPath + m.groupValues[2] }
    }
}

/**
 * `luna-send` for processes in the webOS root: package scripts and JS services' child
 * processes. The command (host/luna-send.js) connects to this socket and sends one call; the
 * replies come back one line each, and the connection closes when the call is over: after one
 * reply, unless it is a subscription, or when the command has read the replies it asked for
 * (`-n`).
 *
 * **Who is calling** comes from the token in the process's environment, which Lunacy issued
 * to that script or service (rule 10), never from the command line: `-a` is accepted and
 * ignored. The socket is a file in Lunacy's private storage, so no other Android app can
 * reach it. (Not an abstract socket: Node 12's libuv can't name one.)
 */
class LunaSendServer(private val bus: Bus, private val socket: File) {
    /** The writer thread's stop sign (compared by identity). */
    private val END = String()
    private val random = SecureRandom()
    val address: String get() = socket.path
    private val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val callers = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val main = Handler(Looper.getMainLooper())
    private var server: LocalServerSocket? = null
    private var listening: LocalSocket? = null

    private fun hex(n: Int) = ByteArray(n).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** The token that makes a luna-send call come from caller. */
    fun token(caller: String): String = callers.getOrPut(caller) { hex(16).also { tokens[it] = caller } }

    @Synchronized fun start() {
        if (server != null) return
        val s = try {
            socket.parentFile?.mkdirs(); socket.delete()
            // Kept: the listening server shares its descriptor.
            val bound = LocalSocket().also { listening = it }.apply { bind(android.net.LocalSocketAddress(socket.path, android.net.LocalSocketAddress.Namespace.FILESYSTEM)) }
            LocalServerSocket(bound.fileDescriptor)
        } catch (e: IOException) { Log.w(AppServer.TAG, "luna-send: no socket: $e"); return }
        server = s
        Thread({
            while (true) {
                val c = try { s.accept() } catch (e: IOException) { break }
                Thread({ serve(c) }, "luna-send").start()
            }
        }, "luna-send-accept").apply { isDaemon = true }.start()
    }

    /**
     * Ends the socket: the accept loop stops, and a command that connects now gets nothing.
     * The file stays. Android creates a new shell before it destroys the one going, so by now
     * that path can be the new shell's socket; [start] clears one nothing is listening on.
     */
    @Synchronized fun stop() {
        runCatching { server?.close() }; runCatching { listening?.close() }
        server = null; listening = null
    }

    private fun serve(c: LocalSocket) {
        val out = c.outputStream
        // Replies are written by a thread of the connection's own, never by the bus's (the main
        // thread, for most services): a reply longer than the socket's buffer - a db8 find of
        // a few hundred objects - would otherwise hold the shell until the command read it.
        val replies = java.util.concurrent.LinkedBlockingQueue<String>()
        Thread({
            while (true) {
                val r = replies.take()
                if (r === END) break
                try { out.write((r + "\n").toByteArray()); out.flush() } catch (e: IOException) { break }
            }
            runCatching { c.close() }
        }, "luna-send-write").apply { isDaemon = true }.start()
        var call: Bus.Call? = null
        fun close() { replies.offer(END) }
        try {
            // One reader for the connection: the request line, then nothing until it closes.
            val input = c.inputStream.bufferedReader()
            val line = input.readLine() ?: return close()
            val m = JSONObject(line)
            val caller = tokens[m.optString("token")]
            if (caller == null) {
                replies.offer(Bus.error("luna-send: not a caller Lunacy knows")); return close()
            }
            val url = m.optString("url")
            val payload = m.optString("payload")
            val stays = Bus.staysOpen(url, payload)
            main.post {
                // luna-send sends on the private bus unless told -P, as webOS's did.
                call = bus.call(caller, url, payload, privateBus = !m.optBoolean("public")) { reply ->
                    replies.offer(reply.replace("\n", " "))
                    if (!stays) close()
                }
            }
            // The command closing its end (it had its -n replies, or was killed) ends the call;
            // anything else it sends meanwhile is read and ignored, not taken for the close.
            while (input.read() >= 0) { }
            main.post { call?.cancel() }
            close()
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "luna-send: $e"); close()
        }
    }
}
