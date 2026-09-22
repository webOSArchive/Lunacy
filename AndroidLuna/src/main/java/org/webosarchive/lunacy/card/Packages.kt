package org.webosarchive.lunacy.card

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Installs .ipk packages into Lunacy's copy of /media/cryptofs/apps, where AppServer serves
 * them and AppRegistry finds them. Installs run one at a time, off the main thread; results
 * come back on it. See Docs/architecture.md, "Package manager and App Museum".
 */
class Packages(val root: File, private val cache: File) {
    class Result(val source: String, val appIds: List<String>, val error: String?)

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * source is an http(s) URL, a file:// URL or a path on the device. [progress] hears 0…100
     * on the main thread as it goes: the download, then 100 once it is unpacked.
     */
    fun install(source: String, progress: (Int) -> Unit = {}, done: (Result) -> Unit) {
        worker.execute {
            val r = try { Result(source, installNow(source) { p -> main.post { progress(p) } }, null) } catch (e: Exception) {
                Log.w(AppServer.TAG, "install $source failed", e)
                Result(source, emptyList(), e.message ?: e.javaClass.simpleName)
            }
            main.post { done(r) }
        }
    }

    /**
     * Removes an installed app with its package: the package whose packageinfo.json names it
     * ("app"), that package's services and its record; an app without one, alone. Bundled apps
     * aren't here to remove. done gets the error, or null.
     */
    fun remove(appId: String, done: (String?) -> Unit) {
        worker.execute {
            val error = try {
                val app = File(root, "$APPS/$appId")
                if (!app.isDirectory) throw IOException("$appId isn't installed")
                val pkgs = File(root, PACKAGES).listFiles().orEmpty().filter { dir ->
                    runCatching { org.json.JSONObject(File(dir, "packageinfo.json").readText()).optString("app") == appId }.getOrDefault(false)
                }
                for (pkg in pkgs) {
                    val services = runCatching { org.json.JSONObject(File(pkg, "packageinfo.json").readText()).optJSONArray("services") }.getOrNull()
                    for (i in 0 until (services?.length() ?: 0)) File(root, "$SERVICES/${services!!.getString(i)}").deleteRecursively()
                    pkg.deleteRecursively()
                }
                app.deleteRecursively()
                Log.i(AppServer.TAG, "removed $appId")
                null
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "remove $appId failed", e); e.message ?: e.javaClass.simpleName
            }
            main.post { done(error) }
        }
    }

    private fun installNow(source: String, progress: (Int) -> Unit): List<String> {
        val (ipk, temporary) = fetch(source, progress)
        val staging = File(root.parentFile, "staging").apply { deleteRecursively(); mkdirs() }
        try {
            val files = Ipk.extract(ipk, staging)
            Log.i(AppServer.TAG, "install $source: ${files.size} files")
            // Each app directory replaces any earlier version whole; other files merge in.
            val apps = File(staging, APPS).list().orEmpty().sorted()
            if (apps.isEmpty()) throw Ipk.BadPackage("package has no apps (usr/palm/applications is empty)")
            for (dir in listOf(APPS, "usr/palm/packages", "usr/palm/services")) {
                File(staging, dir).listFiles()?.forEach { f ->
                    val dest = File(root, "$dir/${f.name}")
                    dest.deleteRecursively(); dest.parentFile?.mkdirs()
                    if (!f.renameTo(dest)) throw IOException("can't move ${f.name} into place")
                }
            }
            mergeInto(staging, root)
            progress(100)
            return apps
        } finally {
            staging.deleteRecursively()
            if (temporary) ipk.delete()
        }
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
    }
}
