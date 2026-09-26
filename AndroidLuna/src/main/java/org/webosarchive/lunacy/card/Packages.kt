package org.webosarchive.lunacy.card

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Installs .ipk packages into Lunacy's copy of /media/cryptofs/apps, where AppServer serves
 * them and AppRegistry finds them, as Preware did: through ipkg, whose `preinst` and
 * `postinst` scripts run around the unpacking and whose records go to
 * /media/cryptofs/apps/usr/lib/ipkg/info. Installs run one at a time, off the main thread;
 * results come back on it. See Docs/architecture.md, "Package manager and App Catalog".
 */
class Packages(val root: File, private val cache: File, private val webos: WebosRoot) {
    /** packageId is the control file's Package; appIds the folders of the apps it unpacked. */
    class Result(val source: String, val appIds: List<String>, val error: String?, val packageId: String = "")

    /** Where ipkg -o /media/cryptofs/apps kept each package's control file, scripts and file list. */
    private val info = File(root, INFO)

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * source is an http(s) URL, a file:// URL or a path on the device. [progress] hears 0…100
     * on the main thread as it goes: the download, then 100 once it is unpacked.
     */
    fun install(source: String, progress: (Int) -> Unit = {}, done: (Result) -> Unit) {
        worker.execute {
            val r = try { installNow(source) { p -> main.post { progress(p) } } } catch (e: Exception) {
                Log.w(AppServer.TAG, "install $source failed", e)
                Result(source, emptyList(), e.message ?: e.javaClass.simpleName)
            }
            main.post { done(r) }
        }
    }

    /**
     * Removes an installed app with its package: the package whose packageinfo.json names it
     * ("app"), that package's services and its record; an app without one, alone. Bundled apps
     * aren't here to remove. dir is the app's folder ([AppInfo.dir]). done gets the error, or null.
     */
    fun remove(appId: String, dir: String = appId, done: (String?) -> Unit) {
        worker.execute {
            val error = try {
                val app = File(root, "$APPS/$dir")
                if (!app.isDirectory) throw IOException("$appId isn't installed")
                val pkgs = File(root, PACKAGES).listFiles().orEmpty().filter { dir ->
                    runCatching { org.json.JSONObject(File(dir, "packageinfo.json").readText()).optString("app") == appId }.getOrDefault(false)
                }
                // ipkg's own record names the package; an app installed before Lunacy kept one
                // has only its packageinfo.json, whose id is the package's.
                val ids = pkgs.mapNotNull { runCatching { org.json.JSONObject(File(it, "packageinfo.json").readText()).optString("id") }.getOrNull() }
                    .ifEmpty { listOf(appId) }
                for (id in ids) script(id, "prerm", "remove")
                for (pkg in pkgs) {
                    val services = runCatching { org.json.JSONObject(File(pkg, "packageinfo.json").readText()).optJSONArray("services") }.getOrNull()
                    for (i in 0 until (services?.length() ?: 0)) File(root, "$SERVICES/${services!!.getString(i)}").deleteRecursively()
                    pkg.deleteRecursively()
                }
                app.deleteRecursively()
                for (id in ids) { removeListed(id); script(id, "postrm", "remove"); forget(id) }
                Log.i(AppServer.TAG, "removed $appId")
                null
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "remove $appId failed", e); e.message ?: e.javaClass.simpleName
            }
            main.post { done(error) }
        }
    }

    private fun installNow(source: String, progress: (Int) -> Unit): Result {
        val (ipk, temporary) = fetch(source, progress)
        val staging = File(root.parentFile, "staging").apply { deleteRecursively(); mkdirs() }
        try {
            val control = Ipk.control(ipk)
            val fields = control["control"]?.toString(Charsets.UTF_8).orEmpty()
            val pkg = Regex("(?m)^Package:\\s*(\\S+)").find(fields)?.groupValues?.get(1)
                ?: source.substringAfterLast('/').substringBefore('_')
            val old = File(info, "$pkg.control").takeIf { it.isFile }?.readText()
                ?.let { Regex("(?m)^Version:\\s*(\\S+)").find(it)?.groupValues?.get(1) ?: "" }
            val files = Ipk.extract(ipk, staging)
            Log.i(AppServer.TAG, "install $source: package $pkg, ${files.size} files")
            // ipkg ran preinst before unpacking, and stopped if it failed.
            control["preinst"]?.let { body ->
                val (code, out) = run(pkg, "preinst", body, if (old == null) listOf("install") else listOf("upgrade", old))
                if (code != 0) throw IOException("its preinst script failed ($code)${lastLine(out)}")
            }
            // Each app directory replaces any earlier version whole; other files merge in.
            val apps = File(staging, APPS).list().orEmpty().sorted()
            for (dir in listOf(APPS, "usr/palm/packages", "usr/palm/services")) {
                File(staging, dir).listFiles()?.forEach { f ->
                    val dest = File(root, "$dir/${f.name}")
                    dest.deleteRecursively(); dest.parentFile?.mkdirs()
                    if (!f.renameTo(dest)) throw IOException("can't move ${f.name} into place")
                }
            }
            mergeInto(staging, root)
            // ipkg's record: the control file, the scripts and the list of files.
            info.mkdirs()
            for (name in listOf("control", "preinst", "postinst", "prerm", "postrm", "conffiles")) {
                val f = File(info, "$pkg.$name")
                val body = control[name]
                if (body == null) f.delete() else { f.writeBytes(body); if (name != "control" && name != "conffiles") f.setExecutable(true, false) }
            }
            File(info, "$pkg.list").writeText(files.joinToString("") { "/media/cryptofs/apps/$it\n" })
            // Preware ran postinst as root once the files were in place. When it failed, the
            // install was reverted and reported as failed (ipkgservice's do_install).
            control["postinst"]?.let { body ->
                val (code, out) = run(pkg, "postinst", body, listOf("configure"))
                if (code != 0) {
                    script(pkg, "prerm", "remove")
                    for (a in apps) File(root, "$APPS/$a").deleteRecursively()
                    removeListed(pkg); script(pkg, "postrm", "remove"); forget(pkg)
                    throw IOException("its postinst script failed ($code)${lastLine(out)}")
                }
            }
            progress(100)
            return Result(source, apps, null, pkg)
        } finally {
            staging.deleteRecursively()
            if (temporary) ipk.delete()
        }
    }

    private fun lastLine(out: List<String>) = out.lastOrNull { it.isNotBlank() }?.let { ": $it" } ?: ""

    /** Runs an installed package's script from its ipkg record, if it has that one. */
    private fun script(pkg: String, name: String, vararg args: String) {
        val f = File(info, "$pkg.$name")
        if (f.isFile) run(pkg, name, f.readBytes(), args.toList())
    }

    /** Deletes the files ipkg listed for the package, and the folders that leaves empty. */
    private fun removeListed(pkg: String) {
        val list = File(info, "$pkg.list").takeIf { it.isFile } ?: return
        val dirs = HashSet<File>()
        for (line in list.readLines()) {
            val rel = line.trim().removePrefix("/media/cryptofs/apps/")
            if (rel.isEmpty() || rel.split('/').any { it == ".." }) continue
            val f = File(root, rel)
            if (f.delete()) generateSequence(f.parentFile) { it.parentFile }.takeWhile { it != root }.forEach { dirs += it }
        }
        dirs.sortedByDescending { it.path.length }.forEach { if (it.list()?.isEmpty() == true) it.delete() }
    }

    private fun forget(pkg: String) {
        for (name in listOf("control", "list", "preinst", "postinst", "prerm", "postrm", "conffiles")) File(info, "$pkg.$name").delete()
    }

    /**
     * Runs one of a package's scripts as ipkgservice did - `IPKG_OFFLINE_ROOT=/media/cryptofs/apps
     * /bin/sh <script>` - in Lunacy's webOS root: busybox's sh, webOS's paths pointed into the
     * root ([WebosRoot.mapPaths]), and luna-send calling the bus as the package. Returns the exit
     * code and what it printed, which the log gets line by line.
     */
    private fun run(pkg: String, name: String, body: ByteArray, args: List<String>): Pair<Int, List<String>> {
        webos.prepare()
        webos.lunaSend.start()
        val r = webos.root
        val file = File(r, "tmp/ipkg-$pkg.$name").apply { parentFile?.mkdirs(); writeText(webos.mapPaths(body.toString(Charsets.UTF_8))) }
        val sh = File(r, "bin/sh").takeIf { it.exists() }?.path ?: "/system/bin/sh"
        val out = ArrayList<String>()
        val code = try {
            val pb = ProcessBuilder(listOf(sh, file.path) + args).directory(r).redirectErrorStream(true)
            pb.environment().apply {
                putAll(webos.environment(pkg))
                put("IPKG_OFFLINE_ROOT", File(r, "media/cryptofs/apps").path)
                put("PKG_ROOT", File(r, "media/cryptofs/apps").path)
            }
            val p = pb.start()
            p.outputStream.close()
            val reader = Thread({
                p.inputStream.bufferedReader().forEachLine { line ->
                    Log.i(AppServer.TAG, "script [$pkg $name] $line")
                    synchronized(out) { out += line }
                }
            }, "ipkg-script").apply { start() }
            val deadline = System.currentTimeMillis() + SCRIPT_TIMEOUT_MS
            var exit: Int? = null
            while (exit == null) {
                exit = try { p.exitValue() } catch (e: IllegalThreadStateException) { null }
                if (exit == null) {
                    if (System.currentTimeMillis() > deadline) { p.destroy(); exit = 124; synchronized(out) { out += "timed out" } }
                    else Thread.sleep(50)
                }
            }
            reader.join(2000)
            exit
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "script [$pkg $name] can't run: $e"); synchronized(out) { out += e.toString() }; 127
        } finally { file.delete() }
        Log.i(AppServer.TAG, "script [$pkg $name] exit $code")
        return code to synchronized(out) { out.toList() }
    }

    private fun mergeInto(from: File, to: File) {
        from.listFiles()?.forEach { f ->
            val dest = File(to, f.name)
            if (f.isDirectory) { dest.mkdirs(); mergeInto(f, dest) } else { dest.delete(); f.renameTo(dest) }
        }
    }

    /** The package as a local file, and whether it is a download to delete afterwards. */
    private fun fetch(source: String, progress: (Int) -> Unit): Pair<File, Boolean> {
        val scheme = source.substringBefore("://", "").lowercase()
        if (scheme == "") return File(source) to false
        if (scheme == "file") return File(java.net.URI(source).path) to false
        if (scheme != "http" && scheme != "https") throw IOException("can't fetch $scheme:// packages")
        val c = Http.connect(Http.Request("GET", source, readTimeoutMs = 60_000))
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} for ${c.url}")
            cache.mkdirs()
            val out = File.createTempFile("pkg", ".ipk", cache)
            // The download is the first nine tenths of the progress; unpacking is the rest.
            val total = c.contentLength.toLong()
            var got = 0L; var last = -1
            c.inputStream.use { i -> out.outputStream().use { o ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    o.write(buf, 0, n); got += n
                    if (total > 0) { val p = (got * 90 / total).toInt(); if (p != last) { last = p; progress(p) } }
                }
            } }
            return out to true
        } finally { c.disconnect() }
    }

    companion object {
        const val APPS = "usr/palm/applications"
        const val PACKAGES = "usr/palm/packages"
        const val SERVICES = "usr/palm/services"
        const val INFO = "usr/lib/ipkg/info"
        /** ipkgservice put no limit on a script; this one is only against a hang. */
        const val SCRIPT_TIMEOUT_MS = 5 * 60_000L
    }
}
