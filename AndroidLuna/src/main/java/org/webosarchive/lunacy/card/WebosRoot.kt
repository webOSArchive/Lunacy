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

    /** Builds root/ once per run, and lays the ROM down again after each APK update. */
    @Synchronized fun prepare() {
        if (prepared) return
        val stamp = File(root, ".lunacy-apk")
        val apk = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (!stamp.isFile || stamp.readText() != apk) {
            // Before the ROM, whose manifest this replaces: the tree the earlier scheme copied
            // wholesale on every update has nothing a script could have changed.
            if (!File(root, ROM_MANIFEST).isFile) File(root, "usr").deleteRecursively()
            syncRom()
            host.deleteRecursively()
            copyAssets("lunacy/services", host)
            installTools()
            stamp.writeText(apk)
        }
        // The folders every TouchPad has, which scripts write into without making them.
        // media/internal/.developer is developer mode's: the SDK's palm-install puts the
        // package there before installing it, and every Lunacy is in developer mode.
        for (d in listOf("media/internal", "media/internal/.developer", "tmp", "var/tmp", "var/run", "var/log", "var/palm", "var/luna/preferences", "var/luna/data", "var/preferences", "home/root", "bin", "usr/bin",
                "sbin", "usr/sbin", "usr/lib", "usr/palm/applications", "usr/palm/services", "usr/palm/public",
                "usr/palm/frameworks", "etc/palm", "etc/event.d", "etc/udev/rules.d")) File(root, d).mkdirs()
        link(installedDir.path, File(root, "media/cryptofs/apps"))
        writeBuildInfo()
        prepared = true
    }

    /**
     * `/etc/palm-build-info`, which the SDK's tools read before anything else to learn what
     * they are talking to. A device's said `PRODUCT_VERSION_STRING=webOS CE 3.1.0` (the
     * reference TouchPad); Lunacy's says it is Lunacy, and webos-sdk-redux recognises it by
     * name rather than Lunacy passing itself off as a TouchPad (codepoet, 2026-10-05).
     */
    private fun writeBuildInfo() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION") val code = info.versionCode
        val text = "PRODUCT_VERSION_STRING=Lunacy ${info.versionName}\nBUILDNAME=Lunacy\nBUILDNUMBER=$code\n"
        val f = File(root, "etc/palm-build-info")
        if (!f.isFile || f.readText() != text) { f.parentFile?.mkdirs(); f.writeText(text) }
    }

    // ---- the ROM ----

    /** Lays assets/rootfs/ into root/, keeping files changed since the last APK laid them down. */
    private fun syncRom() {
        val manifest = File(root, ROM_MANIFEST)
        val before = if (manifest.isFile) manifest.readLines().mapNotNull { l ->
            l.split(' ', limit = 2).takeIf { it.size == 2 }?.let { it[1] to it[0] }
        }.toMap() else emptyMap()
        val now = LinkedHashMap<String, String>()
        var kept = 0
        // fetch-assets lists the ROM's files: walking it with AssetManager.list() instead took
        // most of a minute on the HP tablet, because each call reads the APK's whole asset index.
        val index = try { context.assets.open("$ROM.index").bufferedReader().readLines().filter { it.isNotBlank() } }
            catch (e: IOException) { ArrayList<String>().also { l -> walkAssets(ROM) { l += it } } }
        for (rel in index) {
            val bytes = context.assets.open("$ROM/$rel").use { it.readBytes() }
            val sum = sha1(bytes)
            now[rel] = sum
            val f = File(root, rel)
            val onDisk = if (f.isFile) sha1(f.readBytes()) else null
            val shipped = before[rel]
            if (onDisk == null || onDisk == shipped || shipped == null) {
                if (onDisk != sum) { f.parentFile?.mkdirs(); f.writeBytes(bytes) }
            } else if (onDisk != sum) {
                kept++
                Log.i(AppServer.TAG, "rootfs: kept /$rel, changed since Lunacy shipped it")
            }
        }
        // Files the ROM no longer has go too, unless something changed them.
        for ((rel, sum) in before) if (rel !in now) {
            val f = File(root, rel)
            if (f.isFile && sha1(f.readBytes()) == sum) f.delete()
        }
        manifest.parentFile?.mkdirs()
        manifest.writeText(now.entries.joinToString("") { "${it.value} ${it.key}\n" })
        // The ROM's symlinks (fetch-assets' rootfs.links), made as the device had them -
        // version/1.0 -> ../submission/48 - since an APK can't carry a link. An earlier ROM
        // laid the link's target down in its place; that copy goes.
        val links = try { context.assets.open("$ROM.links").bufferedReader().readLines() } catch (e: IOException) { emptyList() }
        for (l in links) {
            val (rel, target) = l.split(' ', limit = 2).takeIf { it.size == 2 } ?: continue
            relink(relativePath(rel.substringBeforeLast('/', ""), target), File(root, rel))
        }
        Log.i(AppServer.TAG, "rootfs: ${now.size} files and ${links.size} links from the ROM, $kept kept as changed")
    }

    /** A symlink at [at] to [target], replacing whatever was there unless it is that link already. */
    private fun relink(target: String, at: File) {
        if (runCatching { Os.readlink(at.path) }.getOrNull() == target) return
        if (runCatching { Os.readlink(at.path) }.isSuccess) at.delete()
        else if (at.isDirectory) at.deleteRecursively()
        else if (at.exists()) at.delete()
        at.parentFile?.mkdirs()
        Os.symlink(target, at.path)
    }

    /** [to] as a relative path from the folder [from], both relative to the root: ("a/b/version", "a/b/submission/48") is "../submission/48". */
    private fun relativePath(from: String, to: String): String {
        val f = if (from.isEmpty()) emptyList() else from.split('/')
        val t = to.split('/')
        var common = 0
        while (common < f.size && common < t.size && f[common] == t[common]) common++
        return (List(f.size - common) { ".." } + t.drop(common)).joinToString("/")
    }

    private fun walkAssets(dir: String, rel: String = "", each: (String) -> Unit) {
        val path = if (rel.isEmpty()) dir else "$dir/$rel"
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) { if (rel.isNotEmpty()) each(rel); return }
        children.forEach { walkAssets(dir, if (rel.isEmpty()) it else "$rel/$it", each) }
    }

    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }

    private fun copyAssets(from: String, to: File) {
        val children = context.assets.list(from).orEmpty()
        if (children.isEmpty()) {
            to.parentFile?.mkdirs()
            context.assets.open(from).use { i -> to.outputStream().use { i.copyTo(it) } }
        } else children.forEach { copyAssets("$from/$it", File(to, it)) }
    }

    // ---- commands ----

    /**
     * webOS's userland. The TouchPad's `/bin` and `/usr/bin` were busybox, and package scripts
     * are written for it (Android 5 has no sed, awk, head or basename), so every busybox
     * command gets its link at its own path, as busybox installs itself. Before them go
     * Lunacy's own: `/bin/sh` is busybox's ash, as on webOS; curl and luna-send run in Node;
     * `mount` answers the remount scripts do before writing to the rootfs.
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
            val ours = setOf("bin/mount", "usr/bin/curl", "usr/bin/luna-send")
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
        list.writeText(made.joinToString("") { "$it\n" })
    }

    private fun link(target: String, at: File) {
        if (at.exists() || runCatching { Os.readlink(at.path) }.isSuccess) return
        at.parentFile?.mkdirs()
        Os.symlink(target, at.path)
    }

    // ---- processes in the webOS root ----

    /**
     * Absolute webOS paths in a shell command line or script, pointed into root/. The same
     * rule as host.js's realAll: a path under one of webOS's own top-level folders, starting a
     * word, a quoted string or an assignment. A URL's path (`file:///usr/…`) is left alone, so
     * a luna-send payload still names webOS paths.
     */
    fun mapPaths(s: String): String = PATHS.replace(s) { m -> m.groupValues[1] + root.path + m.groupValues[2] }

    /** The environment a process in the webOS root gets, calling the bus as [caller]. */
    fun environment(caller: String): Map<String, String> = mapOf(
        "PATH" to listOf("usr/sbin", "usr/bin", "sbin", "bin").joinToString(":") { "${root.path}/$it" } + ":/system/bin",
        "HOME" to File(root, "home/root").path,
        "TMPDIR" to File(root, "tmp").path,
        "LUNACY_WEBOS_ROOT" to root.path,
        "LUNACY_BUS" to lunaSend.address,
        "LUNACY_BUS_TOKEN" to lunaSend.token(caller),
    )

    companion object {
        const val ROM = "rootfs"
        const val ROM_MANIFEST = ".lunacy-rom"
        const val TOOLS_LIST = ".lunacy-tools"
        private val PATHS = Regex("(^|[\\s'\"=(;|&<>`])(/(?:usr|media|bin|sbin|var|etc|tmp|home|opt|lib)(?:/[^\\s'\";|&<>)`]*)?)", RegexOption.MULTILINE)
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

    /** Ends the socket: the accept loop stops, and a command that connects now gets nothing. */
    @Synchronized fun stop() {
        runCatching { server?.close() }; runCatching { listening?.close() }
        server = null; listening = null
        socket.delete()
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
            val line = c.inputStream.bufferedReader().readLine() ?: return close()
            val m = JSONObject(line)
            val caller = tokens[m.optString("token")]
            if (caller == null) {
                replies.offer(Bus.error("luna-send: not a caller Lunacy knows")); return close()
            }
            val url = m.optString("url")
            val payload = m.optString("payload")
            val params = runCatching { JSONObject(payload.ifEmpty { "{}" }) }.getOrDefault(JSONObject())
            // Bus.Call's own rule for a call that stays open.
            val stays = params.optBoolean("subscribe") || params.optBoolean("watch") || url.trimEnd('/').endsWith("/watch")
            main.post {
                // luna-send sends on the private bus unless told -P, as webOS's did.
                call = bus.call(caller, url, payload, privateBus = !m.optBoolean("public")) { reply ->
                    replies.offer(reply.replace("\n", " "))
                    if (!stays) close()
                }
            }
            // The command closing its end (it had its -n replies, or was killed) ends the call.
            c.inputStream.read()
            main.post { call?.cancel() }
            close()
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "luna-send: $e"); close()
        }
    }
}
