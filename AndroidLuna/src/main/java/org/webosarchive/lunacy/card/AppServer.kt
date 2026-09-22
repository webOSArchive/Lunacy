package org.webosarchive.lunacy.card

import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.json.JSONObject

/**
 * Serves every request to an app origin: the app's own files at their webOS path, the
 * frameworks, and Lunacy's injected scripts. See Docs/architecture.md, "Card host".
 */
class AppServer(
    private val assets: AssetManager,
    private val files: AppFiles,
    private val webosRoot: java.io.File,
    /** Where the per-app lists of framework art live; see [preload]. */
    private val artCacheDir: java.io.File,
) {
    companion object {
        const val TAG = "Lunacy"
        /** Apps check location.hostname for this (the TouchPad reports ".media.cryptofs.apps..."). */
        const val HOST_SUFFIX = ".media.cryptofs.apps"
        /** Installed packages' tree, served from AppFiles (installed first, then bundled). */
        const val CRYPTOFS = "media/cryptofs/apps/"
        const val APPS = CRYPTOFS + Packages.APPS + "/"
        const val MEDIA_INTERNAL = "media/internal/"

        fun appUrl(id: String, main: String = "index.html") = "https://$id$HOST_SUFFIX/$APPS$id/$main"

        /** The app id an origin belongs to, or null for any other host. */
        fun appIdOf(url: String?): String? {
            val host = Uri.parse(url ?: return null).host ?: return null
            return if (host.endsWith(HOST_SUFFIX)) host.removeSuffix(HOST_SUFFIX) else null
        }

        private val FRAMEWORK = Regex("(?:^|.*/)usr/palm/frameworks/enyo/[^/]+/(.*)")
        /** Mojo has no version folder: apps load /usr/palm/frameworks/mojo/mojo.js directly. */
        private val MOJO = Regex("(?:^|.*/)usr/palm/frameworks/mojo/(.*)")
        /** mojocommon sits beside Mojo; the submission's resources and images link into it. */
        private val MOJO_COMMON = Regex("(?:^|.*/)usr/palm/frameworks/mojocommon/(.*)")
        /**
         * The rest of /usr/palm/frameworks, at their own paths: mojo2, prototype, mojo.core,
         * the libraries MojoLoader hands out, and mojoloader.js itself. Enyo, Mojo and
         * mojocommon are matched first and come from their own trees.
         */
        private val FRAMEWORKS = Regex("(?:^|.*/)usr/palm/frameworks/(.*)")
        /**
         * A page that loads Mojo, either version. webOS's browser had the framework compiled
         * in, and mojo.js looks for it as a global; Chromium hasn't, so the builtins that
         * carry it are served in front of the app's own tag. The submission comes from the
         * tag itself, as mojo.js reads it: for Mojo 1, x-mojo-version 1 is submission 506;
         * Mojo 2's own loader defaults to 205 and takes only x-mojo-submission.
         */
        private val MOJO_TAG = Regex(
            """<script[^>]*src="[^"]*?/usr/palm/frameworks/(mojo|mojo2)/mojo\.js[^"]*"[^>]*>\s*</script>""",
            RegexOption.IGNORE_CASE)
        private val MOJO_VERSION = Regex("""x-mojo-(version|submission)="([^"]*)"""", RegexOption.IGNORE_CASE)
        private val MOJO_SUBMISSIONS = mapOf("1" to "506", "2" to "344")
        /** Framework art an app has asked for: what to preload next time, and how much (see preload). */
        private val IMAGE = Regex("""\.(png|jpg|jpeg|gif)$""", RegexOption.IGNORE_CASE)
        private const val MAX_ART = 120
        /** An app id is a file name here, so it has to be one: webOS ids are dotted names. */
        private val APP_ID = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")
        private const val ART_FLUSH_MS = 2000L
        /**
         * What MojoLoader looks for on the window: a builtin library is `palm<name>Version<v>`
         * with the dots turned to underscores. A Mojo 2 page gets them all in front of its own
         * tag, because MojoLoader asks for mojo.core before the framework itself loads.
         */
        private val MOJO2_LIBRARIES = listOf(
            "palmunderscoreVersion1_0", "palmfoundationsVersion1_0",
            "palmglobalizationVersion1_0", "palmmojo_coreVersion1_0")
        /**
         * webOS's own system UI, which apps reach at its absolute path: enyo.FilePicker
         * loads /usr/lib/luna/system/luna-systemui/app/FilePicker/filepicker.html in an
         * iframe. Lunacy serves its own pages there, so the control works in every app.
         */
        const val SYSTEM_UI = "usr/lib/luna/system/luna-systemui/app/"

        /**
         * webOS's thumbnailer, which was a FUSE filesystem rather than a service: a read of
         * `/var/luna/data/extractfs<source path>:<x>:<y>:<width>:<height>:<mode>` returned
         * the source image scaled to fit that box. An app builds the path with
         * `encodeURIComponent`, so the source's own slashes arrive as %2F: the request keeps
         * them escaped, which is why this route reads the *encoded* path and decodes it once
         * itself rather than taking the decoded one.
         */
        const val EXTRACTFS = "var/luna/data/extractfs"
        /**
         * A scaled copy of an image in the webOS tree: `?__lunacy_thumb=160` gives a JPEG
         * whose short side is about 160 px. A picker or gallery would otherwise decode
         * full-size photos, which 1 GB of RAM can't hold. Lunacy's own, hence the prefix.
         */
        const val THUMB_PARAM = "__lunacy_thumb"
        /**
         * `?__lunacy_res=1`: this request is an app *reading a file*, not the browser loading
         * a page. `palmGetResource` in the bridge adds it, because on a device that call read
         * the file off the disk and no browser was involved - so the file must come back as
         * it is, without Lunacy's own scripts in front of it. Mojo reads every widget
         * template and every scene this way, and a template with Lunacy's boot scripts at the
         * front is not the template the framework wrote.
         */
        const val RESOURCE_PARAM = "__lunacy_res"
    }

    fun serve(uri: Uri): WebResourceResponse? {
        val host = uri.host ?: return null
        if (!host.endsWith(HOST_SUFFIX)) return null  // real network
        val path = uri.path.orEmpty().trimStart('/')
        // The thumbnailer's paths carry the source's slashes as %2F, and a decoded path would
        // lose the difference between those and the ones in the route itself.
        val encodedPath = uri.encodedPath.orEmpty().trimStart('/')
        val app = host.removeSuffix(HOST_SUFFIX)
        val resource = runCatching { uri.getQueryParameter(RESOURCE_PARAM) != null }.getOrDefault(false)
        if (path.startsWith("__lunacy/fonts/")) return asset("luna/fonts/" + path.removePrefix("__lunacy/fonts/"), path)
        if (path.startsWith("__lunacy/")) return asset("lunacy/" + path.removePrefix("__lunacy/"), path)
        // The TouchPad answers this one with 200 and an empty body; Enyo's Tellurium hooks
        // read it while starting, and a 404 makes them throw where a device doesn't.
        if (path == "usr/palm/frameworks/tellurium/tellurium_config.json") {
            return WebResourceResponse("application/json", "utf-8", 200, "OK",
                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(ByteArray(0)))
        }
        val fw = FRAMEWORK.matchEntire(path)
        val mojo = MOJO.matchEntire(path)
        val mojoCommon = MOJO_COMMON.matchEntire(path)
        val frameworks = FRAMEWORKS.matchEntire(path)
        val thumb = runCatching { uri.getQueryParameter(THUMB_PARAM)?.toInt() }.getOrNull()
        val resp = when {
            fw != null -> asset("fw/enyo/1.0/" + fw.groupValues[1], path, resource)
            mojoCommon != null -> asset("fw/mojocommon/" + mojoCommon.groupValues[1], path, resource)
            mojo != null -> asset("fw/mojo/" + mojo.groupValues[1], path, resource)
            frameworks != null -> asset("fw/frameworks/" + frameworks.groupValues[1], path, resource)
            path.startsWith(SYSTEM_UI) -> asset("luna-systemui/" + path.removePrefix(SYSTEM_UI), path, resource)
            encodedPath.startsWith(EXTRACTFS) ->
                extractfs(Uri.decode(encodedPath.removePrefix(EXTRACTFS)))
            path.startsWith(CRYPTOFS) -> files.open(path.removePrefix(CRYPTOFS))?.let { respond(it, path, resource, app) }
            // webOS's user storage, shared by apps and services (JS services write files here).
            path.startsWith(MEDIA_INTERNAL) -> {
                val rel = path.removePrefix(MEDIA_INTERNAL)
                if (thumb != null) thumbnail(rel, thumb) else internal(rel)?.let { respond(it, path, resource, app) }
            }
            else -> null
        }
        if (resp != null) noteArt(app, path) else Log.w(TAG, "404 $uri")
        return resp ?: WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(),
            ByteArrayInputStream(ByteArray(0)))
    }

    /** webOS's user storage, Lunacy's own and the Android folders mapped into it (UserFiles). */
    private fun internal(rel: String): InputStream? =
        UserFiles.resolve(webosRoot, rel)?.takeIf { it.isFile }?.inputStream()

    /**
     * webOS's thumbnailer: `/var/luna/data/extractfs/<source>:<x>:<y>:<w>:<h>:<mode>`.
     *
     * Not a service and not a file an app wrote - a FUSE filesystem, mounted on the device at
     * `/var/luna/data/extractfs`, that answered a read with the named image scaled down. Any
     * app that shows artwork at a fixed size uses it rather than letting the page scale a
     * full-size picture: drPodder's feed and episode lists ask for every podcast's cover at
     * `:0:0:56:56:3`, and without it the lists are full of broken images.
     *
     * Measured on the reference TouchPad on 2026-09-22, by reading the synthetic files
     * straight off the mount and looking at what came back:
     *
     * | asked for | source | came back |
     * |---|---|---|
     * | `:0:0:56:56:3` | 480 x 480 | 56 x 56 |
     * | `:0:0:56:56:3` | 700 x 875 | **45 x 56** |
     * | `:0:0:100:50:3` | 700 x 875 | **40 x 50** |
     *
     * So the box is a bound, not a shape: the image is scaled to fit inside it with its
     * aspect ratio kept, and never cropped or stretched. Modes 0 and 3 returned the same
     * bytes for the same source, and the first two numbers were 0 in everything seen, so
     * neither is acted on here; if an app turns up that varies them, measure again.
     *
     * The device returned an uncompressed BMP. This returns a PNG, which no page can tell
     * apart from an img's point of view and which is a tenth the size.
     */
    private fun extractfs(spec: String): WebResourceResponse? {
        // <source path>:<x>:<y>:<width>:<height>:<mode>, so the last five fields are the box.
        val parts = spec.trimStart('/').split(':')
        if (parts.size < 6) return null
        val source = parts.dropLast(5).joinToString(":")
        val box = parts.takeLast(5).map { it.toIntOrNull() ?: return null }
        val w = box[2]
        val h = box[3]
        if (w !in 1..4096 || h !in 1..4096) return null
        val open = resolveWebos(source) ?: return null
        return try {
            // inJustDecodeBounds makes decodeStream return null and fill in the options,
            // so what says whether the source is there is the stream, not the bitmap.
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (open() ?: return null).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
            val bmp = open()?.use {
                android.graphics.BitmapFactory.decodeStream(it, null,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            // Fit inside the box, as the device does, and never scale up: a source smaller
            // than the box came back at its own size there.
            val scale = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height, 1f)
            val out = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                bmp, Math.max(1, Math.round(bmp.width * scale)), Math.max(1, Math.round(bmp.height * scale)), true)
            else bmp
            val bytes = java.io.ByteArrayOutputStream()
            out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
            if (out !== bmp) out.recycle()
            bmp.recycle()
            WebResourceResponse("image/png", null, 200, "OK", mapOf("Cache-Control" to "max-age=600"),
                ByteArrayInputStream(bytes.toByteArray()))
        } catch (e: Exception) {
            Log.w(TAG, "extractfs $spec: $e")
            null
        }
    }

    /**
     * An absolute webOS path, as the thumbnailer is given one: user storage or an app's own
     * files. Returns a way to open it twice - the bounds are read before the pixels - because
     * a bundled app's files are in the APK and have no path on disk.
     */
    private fun resolveWebos(path: String): (() -> InputStream?)? {
        val p = path.trimStart('/')
        if (p.startsWith(MEDIA_INTERNAL)) {
            val f = UserFiles.resolve(webosRoot, p.removePrefix(MEDIA_INTERNAL))?.takeIf { it.isFile } ?: return null
            return { runCatching { f.inputStream() as InputStream }.getOrNull() }
        }
        if (p.startsWith(CRYPTOFS)) {
            val rel = p.removePrefix(CRYPTOFS)
            return { files.open(rel) }
        }
        return null
    }

    /** A JPEG copy of an image under /media/internal, scaled so its short side is about `size`. */
    private fun thumbnail(rel: String, size: Int): WebResourceResponse? {
        val f = UserFiles.resolve(webosRoot, rel)?.takeIf { it.isFile } ?: return null
        val px = size.coerceIn(16, 1024)
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
            val bmp = android.graphics.BitmapFactory.decodeFile(f.path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val scale = px.toFloat() / minOf(bmp.width, bmp.height)
            val out = if (scale < 1f)
                android.graphics.Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * scale), Math.round(bmp.height * scale), true)
            else bmp
            val bytes = java.io.ByteArrayOutputStream()
            out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bytes)
            if (out !== bmp) out.recycle()
            bmp.recycle()
            WebResourceResponse("image/jpeg", null, 200, "OK", mapOf("Cache-Control" to "max-age=600"),
                ByteArrayInputStream(bytes.toByteArray()))
        } catch (e: Exception) {
            Log.w(TAG, "thumbnail $rel: $e")
            null
        }
    }

    private fun asset(assetPath: String, name: String, resource: Boolean = false): WebResourceResponse? {
        val stream: InputStream = try { assets.open(assetPath) } catch (e: IOException) { return null }
        return respond(stream, name, resource)
    }

    private fun respond(stream: InputStream, name: String, resource: Boolean = false,
                        app: String? = null): WebResourceResponse {
        val mime = mimeOf(name)
        val body = when (mime) {
            "text/html" -> html(stream, resource, app)
            "text/css" -> CssTransforms.apply(stream)
            else -> stream
        }
        return WebResourceResponse(mime, "utf-8", 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), body)
    }

    /**
     * Every piece of HTML Lunacy serves gets the parser fix ([HtmlTransforms.selfClosingTags]),
     * because that is how the device's own parser read it, whether the file is a page or a
     * template an app reads. Lunacy's own scripts go only into a page: a file read through
     * `palmGetResource` ([RESOURCE_PARAM]) comes back as it is on disk.
     */
    private fun html(s: InputStream, resource: Boolean, app: String? = null): InputStream {
        val text = HtmlTransforms.selfClosingTags(s.bufferedReader().readText())
        return ByteArrayInputStream((if (resource) text else injectInto(text, app)).toByteArray())
    }

    /** Global serve-time transform: Lunacy's scripts run first in every page. */
    private fun injectInto(source: String, app: String? = null): String {
        var html = source
        val tag = "<link rel=\"stylesheet\" href=\"/__lunacy/fonts.css\">" +
            "<script src=\"/__lunacy/compat.js\"></script><script src=\"/__lunacy/bridge.js\"></script>" +
            "<script src=\"/__lunacy/net.js\"></script>" + preload(app)
        val m = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        html = if (m != null) html.substring(0, m.range.last + 1) + tag + html.substring(m.range.last + 1) else tag + html
        return mojoBuiltins(html)
    }

    // ---- the framework's art, asked for before the page needs it ----

    /**
     * A device kept its frameworks in the browser and its art on local storage. Here every
     * piece of that art is a request, and a framework only asks for a piece once a widget
     * that uses it has been laid out - so the layout arrives before its chrome, and a dialog's
     * own frame (Onyx's popup.png, asked for only when the dialog opens) lands half a second
     * after the dialog. That is what "the widgets popping in" is.
     *
     * Serving faster doesn't help: `serve` answers the median request in under a millisecond
     * and the wait is the page's, not the file's. Asking earlier does. Measured on the HP 10
     * G2 with the App Museum, three runs each:
     *
     *                                   art in hand   DOMContentLoaded   load
     *   as it was                          3.16 s          2.25 s        3.09 s
     *   all of Onyx's art (61 images)      0.90 s          2.84 s        3.43 s
     *   the 11 pieces the app uses         0.43 s          2.26 s        2.83 s
     *
     * Preloading a framework's whole theme pays for fifty images nobody asked for, and the
     * page appears half a second later for it. Preloading what the app actually used costs
     * nothing and has the art in hand before the framework has finished starting.
     *
     * So Lunacy keeps no list of its own: it remembers what each app asked the *frameworks*
     * for last time ([noteArt]) and asks for that again at the top of the page. Nothing here
     * knows anything about any app - it is a cache, filled by the same rule for every one of
     * them, and an app that has never run gets no preload and behaves as it did before.
     */
    private fun preload(app: String?): String {
        val art = artOf(app ?: return "")
        if (art.isEmpty()) return ""
        val list = art.joinToString(",") { JSONObject.quote(it) }
        return "<script>(function(){var a=[$list];" +
            "for(var i=0;i<a.length;i++){(new Image()).src=a[i];}})();</script>"
    }

    /** What this app asked the frameworks for, in memory and on disk. */
    private val art = HashMap<String, LinkedHashSet<String>>()
    private val artFlush = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "lunacy-art").apply { isDaemon = true }
    }
    private var artFlushPending: java.util.concurrent.ScheduledFuture<*>? = null

    private fun artOf(app: String): Set<String> = synchronized(art) {
        if (!APP_ID.matches(app)) return emptySet()
        art.getOrPut(app) {
            val f = java.io.File(artCacheDir, app)
            LinkedHashSet(runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList()))
        }.toSet()
    }

    /**
     * One piece of framework art this app has now asked for. Only the frameworks Lunacy
     * serves: an app's own images are its own business and change with its content, while
     * the widget art is the same every time the app runs.
     */
    private fun noteArt(app: String, path: String) {
        if (!APP_ID.matches(app) || !path.contains("usr/palm/frameworks/") || !IMAGE.containsMatchIn(path)) return
        val url = "/$path"
        synchronized(art) {
            val set = art.getOrPut(app) {
                val f = java.io.File(artCacheDir, app)
                LinkedHashSet(runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList()))
            }
            if (set.size >= MAX_ART || !set.add(url)) return
            artFlushPending?.cancel(false)
            artFlushPending = artFlush.schedule({ flushArt() }, ART_FLUSH_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    private fun flushArt() {
        val snapshot = synchronized(art) { art.mapValues { it.value.toList() } }
        runCatching { artCacheDir.mkdirs() }
        for ((app, urls) in snapshot) {
            runCatching { java.io.File(artCacheDir, app).writeText(urls.joinToString("\n")) }
        }
    }

    /**
     * Another global transform: a page that loads Mojo gets the builtins in front of it.
     *
     * On a device the framework was part of the browser - mojo.js calls a global
     * palmInitFramework<submission> that WebKit provided, and falls back to fetching
     * javascripts/loader.js, which webOS doesn't ship. Chromium has no such global, so the
     * files that carry it (Prototype, and the framework itself) are loaded first and the
     * app's own tag then finds what it expects. See "Mojo" in Docs/architecture.md.
     */
    private fun mojoBuiltins(html: String): String {
        val tag = MOJO_TAG.find(html) ?: return html
        val two = tag.groupValues[1].equals("mojo2", ignoreCase = true)
        val attr = MOJO_VERSION.find(tag.value)?.groupValues
        // Mojo 2's loader has no version map and ignores x-mojo-version: it defaults to
        // submission 205 and takes only x-mojo-submission, and the builtin it then looks for
        // is palmInitFramework2 + that number. Mojo 1 maps x-mojo-version through its own table.
        val submission = if (two)
            "2" + (attr?.takeIf { it[1].equals("submission", true) }?.get(2)).orEmpty().ifEmpty { "205" }
        else {
            val version = attr?.get(2).orEmpty()
            MOJO_SUBMISSIONS[version] ?: version.ifEmpty { "506" }
        }
        // Mojo 2 takes Prototype from its own framework, through the app's own script tag;
        // only Mojo 1 needs the builtin copy. It does need MojoLoader's libraries first,
        // because the framework asks for mojo.core while it is still loading.
        val libraries = if (two) MOJO2_LIBRARIES else listOf("InstallPrototypeBuiltIn")
        val boot = libraries.joinToString("") {
            "<script src=\"/usr/palm/frameworks/mojo/builtins/$it.js\"></script>"
        } + "<script src=\"/usr/palm/frameworks/mojo/builtins/palmInitFramework$submission.js\"></script>" +
            "<script src=\"/__lunacy/mojo-boot.js\"></script>"
        return html.substring(0, tag.range.first) + boot + html.substring(tag.range.first)
    }

    private fun mimeOf(p: String) = when (p.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"; "js" -> "application/javascript"; "css" -> "text/css"
        "json" -> "application/json"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"; "svg" -> "image/svg+xml"; "ico" -> "image/x-icon"
        "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ttf" -> "font/ttf"; "woff" -> "font/woff"
        "m3u8" -> "application/vnd.apple.mpegurl"; "mp4", "m4a" -> "audio/mp4"; "aac" -> "audio/aac"; "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}

/**
 * Global serve-time HTML transforms. One mechanical rule applied to every app's HTML; no app
 * is named.
 */
object HtmlTransforms {
    /** Elements HTML closes on its own, where `<x />` already means what it looks like. */
    private val VOID = setOf(
        "area", "base", "basefont", "bgsound", "br", "col", "command", "embed", "frame", "hr",
        "img", "input", "isindex", "keygen", "link", "meta", "param", "source", "track", "wbr")
    /** Parsed through rather than tokenized: their content is not markup. */
    private val RAW_TEXT = setOf("script", "style", "textarea", "title")

    /**
     * `<script src="x.js" />` closes the element, as it did on the device.
     *
     * webOS's WebKit is old enough to keep the pre-HTML5 tokenizer, which honoured a trailing
     * slash on *any* tag. Chromium follows the standard instead: only void elements close
     * that way, so `<script src="x.js" />` opens a script that swallows the rest of the file
     * as its (ignored) text content. An app written that way - drPodder's index.html is, and
     * it is XHTML throughout - then loses every tag after the first such script: its
     * stylesheet, its other scripts and its whole body.
     *
     * Measured on the reference TouchPad, not assumed (Docs/mojo.md, "Self-closing tags"):
     * a probe page in drPodder's shape reported `selfClosed=true`, the marker element present
     * and its stylesheet applied, with `document.xmlVersion` null - so the device was parsing
     * HTML, and simply closed the tag.
     *
     * Applies to every piece of HTML Lunacy serves, pages and templates alike, because the
     * device's parser read both. Script and style content is skipped: a string like
     * `"<div/>"` in an app's JavaScript is not markup.
     */
    fun selfClosingTags(html: String): String {
        if (!html.contains("/>")) return html
        val out = StringBuilder(html.length + 64)
        var i = 0
        while (true) {
            val lt = html.indexOf('<', i)
            if (lt < 0) { out.append(html, i, html.length); break }
            out.append(html, i, lt)
            if (html.startsWith("<!--", lt)) {
                val end = html.indexOf("-->", lt + 4)
                val stop = if (end < 0) html.length else end + 3
                out.append(html, lt, stop); i = stop; continue
            }
            var j = lt + 1
            while (j < html.length && (html[j].isLetterOrDigit() || html[j] == '-')) j++
            val name = html.substring(lt + 1, j).lowercase()
            if (name.isEmpty()) { out.append('<'); i = lt + 1; continue }
            // To the tag's own '>', ignoring one inside a quoted attribute value.
            var k = j
            var quote = ' '
            while (k < html.length) {
                val c = html[k]
                if (quote != ' ') { if (c == quote) quote = ' ' }
                else if (c == '"' || c == '\'') quote = c
                else if (c == '>') break
                k++
            }
            if (k >= html.length) { out.append(html, lt, html.length); i = html.length; continue }
            var slash = k - 1
            while (slash > j && html[slash].isWhitespace()) slash--
            val closes = slash >= j && html[slash] == '/'
            if (closes && name !in VOID) {
                out.append(html, lt, slash).append("></").append(name).append('>')
                i = k + 1
            } else {
                out.append(html, lt, k + 1)
                i = k + 1
            }
            // A raw-text element the tag left open: its content is text, so copy it through.
            if (name in RAW_TEXT && !closes) {
                val close = Regex("</$name\\b", RegexOption.IGNORE_CASE).find(html, i)
                val stop = close?.range?.first ?: html.length
                out.append(html, i, stop); i = stop
            }
        }
        return out.toString()
    }
}

/**
 * Global serve-time CSS transforms. Each is a mechanical rule applied to every app's CSS;
 * none names an app.
 */
object CssTransforms {
    private val rule = Regex("\\{[^{}]*\\}")
    private val borderImage = Regex("-webkit-border-image\\s*:(?!\\s*none)", RegexOption.IGNORE_CASE)
    private val mouseTarget = Regex("-webkit-palm-mouse-target\\s*:\\s*ignore", RegexOption.IGNORE_CASE)

    fun apply(s: InputStream): InputStream =
        ByteArrayInputStream(mouseTargetIgnore(borderImageNeedsStyle(s.bufferedReader().readText())).toByteArray())

    /**
     * `-webkit-palm-mouse-target: ignore` is webOS's own property for "this element is not
     * what a touch here means": the touch goes to whatever is behind it. Chromium has never
     * heard of it, so such an element swallows the touch instead, and whatever was meant to
     * receive it never hears anything.
     *
     * Mojo's own stylesheets use it nineteen times, and it is load-bearing. A page header
     * draws its back icon absolutely positioned at the top left and lays the title over the
     * whole header on top of it (`global-lists.css`: `.palm-page-header .icon` is
     * `position: absolute`, `.palm-page-header .title` covers it and is marked ignore), so in
     * Chromium every tap on the back button hit the title. In drPodder that meant the back
     * button silently ran the title's own handler - it showed and hid the playhead instead of
     * leaving the scene. The same applies to Mojo's menus and lists, and apps use the
     * property themselves (drPodder three times).
     *
     * `pointer-events: none` is the same idea in a property Chromium has, down to a child
     * being able to opt back in (`pointer-events: auto` for `-webkit-palm-mouse-target:
     * accept`). Only `ignore` is ever used - the whole of Mojo, mojocommon and the apps
     * looked at use no other value - so only `ignore` is translated, and anything else is
     * left alone to be noticed rather than guessed at.
     */
    fun mouseTargetIgnore(css: String): String =
        mouseTarget.replace(css) { m -> m.value + ";pointer-events:none" }

    /**
     * 2011 WebKit drew -webkit-border-image whatever the border-style; newer Chromium
     * computes border-width to 0 when border-style is none, so the image vanishes.
     * Appended to the rule: Enyo's own CSS sets border-image next to `border-style: none`.
     */
    fun borderImageNeedsStyle(css: String): String = rule.replace(css) { m ->
        if (borderImage.containsMatchIn(m.value)) m.value.dropLast(1) + ";border-style:solid;border-color:transparent}" else m.value
    }
}
