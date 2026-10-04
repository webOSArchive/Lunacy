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
    val webosRoot: java.io.File,
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
        const val IPKGS = "usr/palm/ipkgs/"

        /** An app's page: its origin is its id, and its path is in its folder ([AppInfo.dir]). */
        fun appUrl(id: String, main: String = "index.html", dir: String = id) = "https://$id$HOST_SUFFIX/$APPS$dir/$main"

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
        /** Just Type's search engine icons, which com.palm.universalsearch names (UniversalSearch). */
        const val SEARCH_ICONS = "usr/lib/luna/system/luna-applauncher/images/"
        /** The Web app's bookmark thumbnails and icons, which its WebView writes (BrowserViews). */
        const val BROWSER_DATA = "var/luna/data/browser/"

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
        /** The longest a page waits for its fonts before its own scripts run anyway. */
        const val FONT_WAIT_MS = 1000L
        /** The page the shell loads at startup to have the default faces decoded (shell/FontWarmer). */
        const val WARM_URL = "https://lunacy-fonts.media.cryptofs.apps/__lunacy/warm.html"
        const val WARM_DONE = "warm"
        private val WARM_PAGE = "<!doctype html><html><head><link rel=\"stylesheet\" href=\"/__lunacy/fonts.css\">" +
            "<script>window.onload=function(){var w=[];document.fonts.forEach(function(f){" +
            "if(f.family.replace(/[\"']/g,\"\")===\"Prelude\"&&f.style===\"normal\")w.push(f);});" +
            "var n=w.length;function one(){if(--n<=0)document.title=\"$WARM_DONE\";}" +
            "if(!n)one();for(var i=0;i<w.length;i++)w[i].load().then(one,one);};</script></head>" +
            "<body></body></html>"
        private fun html(s: String) = WebResourceResponse("text/html", "utf-8", 200, "OK", emptyMap(),
            ByteArrayInputStream(s.toByteArray()))
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
        if (path == "__lunacy/fonts-loaded") return fontsLoaded(uri)
        if (path == "__lunacy/warm.html") return html(WARM_PAGE)
        if (path == "__lunacy/fonts-ready.js") return fontsReady(uri)
        if (path.startsWith("__lunacy/fonts/")) return font(path.removePrefix("__lunacy/fonts/"))
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
            mojo != null -> if (mojo.groupValues[1] == "mojo.js" && !resource) mojoJs("fw/mojo/mojo.js") else asset("fw/mojo/" + mojo.groupValues[1], path, resource)
            frameworks != null -> if (frameworks.groupValues[1] == "mojo2/mojo.js" && !resource) mojoJs("fw/frameworks/mojo2/mojo.js") else asset("fw/frameworks/" + frameworks.groupValues[1], path, resource)
            path.startsWith(SYSTEM_UI) -> asset("luna-systemui/" + path.removePrefix(SYSTEM_UI), path, resource)
            path.startsWith(SEARCH_ICONS) -> asset("luna-applauncher/images/" + path.removePrefix(SEARCH_ICONS), path, resource)
            path.startsWith(BROWSER_DATA) -> java.io.File(webosRoot, path).takeIf { it.isFile && it.canonicalPath.startsWith(java.io.File(webosRoot, BROWSER_DATA).canonicalPath + java.io.File.separator) }
                ?.let { respond(it.inputStream(), path, resource, app) }
            // The webOS root's list of the packages its ROM ships (WebosRoot), which App Catalog
            // reads as the apps it can revert to their shipped version.
            path.startsWith(IPKGS) -> java.io.File(webosRoot, path).takeIf { it.isFile && it.canonicalPath.startsWith(java.io.File(webosRoot, IPKGS).canonicalPath + java.io.File.separator) }
                ?.let { respond(it.inputStream(), path, resource, app) }
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
        val path = if (assetPath.startsWith("fw/")) "fw/" + followLinks(assetPath.removePrefix("fw/")) else assetPath
        val stream: InputStream = try { assets.open(path) } catch (e: IOException) { return null }
        return respond(stream, name, resource)
    }

    /**
     * The framework trees' symlinks, as fetch-assets.sh records them in fw.links: a link's
     * path to the file it points at, both under assets/fw. The device's frameworks are full
     * of them - version/1.0 is a link to submission/N, and Mojo's images and templates link
     * into mojocommon file by file - and an APK can't hold a link, so the server follows them.
     */
    private val links: Map<String, String> by lazy {
        val lines = try { assets.open("fw.links").bufferedReader().readLines() } catch (e: IOException) { emptyList() }
        lines.mapNotNull { l -> l.split(' ', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    }

    /** [path] under fw/ with every link on it followed, the longest prefix first, a few times over for a link inside a linked folder. */
    private fun followLinks(path: String): String {
        var p = path
        repeat(8) {
            var end = p.length
            while (end > 0) {
                val target = links[p.substring(0, end)]
                if (target != null) { p = target + p.substring(end); break }
                end = p.lastIndexOf('/', end - 1)
            }
            if (end <= 0) return p
        }
        return p
    }

    private fun respond(stream: InputStream, name: String, resource: Boolean = false,
                        app: String? = null): WebResourceResponse {
        val mime = mimeOf(name)
        val body = when (mime) {
            "text/html" -> html(stream, resource, app)
            "text/css" -> CssTransforms.apply(stream)
            "application/javascript" -> if (resource) stream else JsTransforms.apply(stream)
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
        return ByteArrayInputStream((if (resource) text else injectInto(JsTransforms.sloppyMode(text), app)).toByteArray())
    }

    /** Global serve-time transform: Lunacy's scripts run first in every page. */
    private fun injectInto(source: String, app: String? = null): String {
        var html = source
        val token = fontToken()
        val tag = "<link rel=\"stylesheet\" href=\"/__lunacy/fonts.css\">" + fontLoad(token) +
            "<script src=\"/__lunacy/compat.js\"></script><script src=\"/__lunacy/bridge.js\"></script>" +
            "<script src=\"/__lunacy/net.js\"></script><script src=\"/__lunacy/websql.js\"></script>" + preload(app) +
            "<script src=\"/__lunacy/fonts-ready.js?t=$token\"></script>"
        val m = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        html = if (m != null) html.substring(0, m.range.last + 1) + tag + html.substring(m.range.last + 1) else tag + html
        return mojoBuiltins(html)
    }

    // ---- the fonts, in hand before the page's own scripts run ----

    /**
     * On a device Prelude was installed, so a page's first script measured text in it. Here it
     * is a web font, and Chromium makes a document's web fonts ready only after the script
     * that is running ends - even a font it already has, from a data: URL or its memory cache
     * (measured with the font probe, Docs/fix-log.md). So text an app measures while it starts
     * (Enyo renders in the body's script) came out in the fallback face.
     *
     * So every page waits for its fonts while the splash card is still up, as the device
     * never had to. The page asks for the faces at once ([fontLoad]); when they are ready it
     * says so ([fontsLoaded]); and a script placed ahead of the app's own is held back until
     * then ([fontsReady]), with a limit so a page can never hang on it. The parser can't pass
     * that script, so nothing of the app's runs before the fonts are in.
     *
     * The wait is short because the fonts are: every page's fonts.css names them at one origin
     * (tools/gen-fonts-css.py), so Chromium decodes each once and every card shares it, and the
     * shell has the default faces decoded while it is idle at startup (shell/FontWarmer).
     * Measured on the HP 10 G2: the first app after Lunacy starts waits about 40 ms, later
     * ones 3-5 ms, and text in the page's first script measures as Prelude.
     */
    // A page that never asks is forgotten once there are too many to be current: the oldest
    // goes, not - as before - every page still loading.
    private val fontWaits: MutableMap<String, java.util.concurrent.CountDownLatch> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, java.util.concurrent.CountDownLatch>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, java.util.concurrent.CountDownLatch>?) = size > 64
        })
    private val fontTokens = java.util.concurrent.atomic.AtomicLong()

    private fun fontToken(): String {
        val t = fontTokens.incrementAndGet().toString(36) + java.lang.Long.toString(System.nanoTime() and 0xffffff, 36)
        fontWaits[t] = java.util.concurrent.CountDownLatch(1)
        return t
    }

    /**
     * The page asks for the default faces, Prelude and its bold, and reports when they are
     * ready. Each face is loaded through its own FontFace: FontFaceSet.load() settles only
     * after the page next lays out, which can't happen while the parser waits on
     * fonts-ready.js, so it would only ever answer once the wait had timed out.
     */
    private fun fontLoad(token: String) = "<script>(function(){var t=\"$token\";" +
        "function done(){var x=new XMLHttpRequest();x.open(\"GET\",\"/__lunacy/fonts-loaded?t=\"+t,true);x.send();}" +
        "try{var s=document.fonts,w=[];if(!s||!s.forEach){done();return;}" +
        "s.forEach(function(f){if(f.family.replace(/[\"']/g,\"\")===\"Prelude\"&&f.style===\"normal\")w.push(f);});" +
        "var n=w.length;if(!n){done();return;}" +
        "function one(){if(--n===0)done();}" +
        "for(var i=0;i<w.length;i++)w[i].load().then(one,one);" +
        "}catch(e){done();}})();</script>"

    /** A font file, from the one origin every page's fonts.css names, cached for good. */
    private fun font(name: String): WebResourceResponse? {
        val stream = try { assets.open("luna/fonts/$name") } catch (e: IOException) { return null }
        return WebResourceResponse("font/ttf", null, 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "max-age=31536000, immutable"), stream)
    }

    private fun fontsLoaded(uri: Uri): WebResourceResponse {
        uri.getQueryParameter("t")?.let { fontWaits[it]?.countDown() }
        return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private fun fontsReady(uri: Uri): WebResourceResponse {
        val t = uri.getQueryParameter("t").orEmpty()
        val start = System.nanoTime()
        // The page's word arrives on another thread while this one waits, so the wait stays
        // listed until it is over.
        val ready = fontWaits[t]?.await(FONT_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        fontWaits.remove(t)
        val ms = (System.nanoTime() - start) / 1_000_000
        Log.i(TAG, "fonts ${if (ready == true) "ready" else "not ready"} after $ms ms (${uri.host})")
        return WebResourceResponse("application/javascript", "utf-8", 200, "OK", emptyMap(),
            ByteArrayInputStream("/* Lunacy: this page's fonts are in. */".toByteArray()))
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
     *
     * This sees only a tag in the page's HTML. A tag a script writes (an Ares app's ares.js
     * writes it with document.write) is handled by [mojoJs]: mojo.js itself goes out with a
     * prelude that loads the same builtins when they aren't there yet.
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

    /**
     * mojo.js itself (either version's), with lunacy/mojo-prelude.js in front: for a page that
     * loads Mojo from a script rather than from its HTML, where [mojoBuiltins] can't see the
     * tag, the prelude fetches the builtins the transform would have written and evaluates
     * them before mojo.js's own code runs. A page that had the tag in its HTML already has
     * them, and the prelude does nothing. Not for a `palmGetResource` read, which gets the
     * file as it is.
     */
    private fun mojoJs(assetPath: String): WebResourceResponse? {
        val js = try { assets.open("fw/" + followLinks(assetPath.removePrefix("fw/"))).bufferedReader().readText() } catch (e: IOException) { return null }
        val prelude = try { assets.open("lunacy/mojo-prelude.js").bufferedReader().readText() } catch (e: IOException) { "" }
        return WebResourceResponse("application/javascript", "utf-8", 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream((prelude + "\n" + JsTransforms.sloppyMode(js)).toByteArray()))
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
 * Global serve-time JavaScript transforms, applied to every script a page loads and to a page's
 * inline scripts. A file an app reads through `palmGetResource` comes back as it is.
 */
object JsTransforms {
    /**
     * A `"use strict"` directive: at the start of a file, a script element or a function body,
     * after any comments. Only there is the string a directive; elsewhere it is left alone.
     */
    private val directive = Regex(
        "((?:^|[{};>])(?:\\s|/\\*[\\s\\S]*?\\*/|//[^\\n]*\\n)*)([\"'])use strict\\2")

    fun apply(s: InputStream): InputStream =
        ByteArrayInputStream(sloppyMode(s.bufferedReader().readText()).toByteArray())

    /**
     * `"use strict"` does nothing, as on the device: the directive becomes a string of the
     * same length that means nothing, so line and column numbers are unchanged.
     *
     * webOS 3's WebKit (534.6) predates strict mode and ignores the directive. Measured on the
     * reference TouchPad with `Workbench/probe/org.webosarchive.lunacy.jsprobe`: in a strict
     * function `this` is the global object, assigning an undeclared variable creates it,
     * `arguments.callee` and a caller's `.caller` answer, and `with`, octal literals and
     * duplicate parameters are all accepted. Chromium enforces every one of those. Apps were
     * only ever run on the device, so a strict file that breaks a rule worked there and throws
     * here - the revived App Catalog's own patch files are strict, and Enyo's `warn()` reads
     * `arguments.callee.caller.nom`, which is null when the caller is strict, so the catalog's
     * first warning threw and its Featured page never loaded.
     */
    fun sloppyMode(js: String): String {
        if (!js.contains("use strict")) return js
        return directive.replace(js) { m -> m.groupValues[1] + m.groupValues[2] + "not strict" + m.groupValues[2] }
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
    /** Selector and body, so a rule can be rewritten with its selector in hand. */
    private val selectorRule = Regex("([^{}]+)\\{([^{}]*)\\}")

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
     * `pointer-events: none` is the same idea in a property Chromium has, with one
     * difference that matters: **it inherits, and webOS's did not.** `-webkit-palm-mouse-target`
     * is a flag on the element itself, so an element that takes no touches still has children
     * that do - which is the whole point of the way Mojo uses it on `.palm-menu`, whose
     * buttons are what a person taps. Translating it to `pointer-events: none` alone made the
     * entire view menu untouchable, and drPodder's episode list lost its back button. So each
     * rule also gets a companion putting its children back: `sel > * { pointer-events: auto }`,
     * which - because the property inherits - restores the whole subtree and leaves only the
     * element itself transparent.
     *
     * Only `ignore` is ever used - the whole of Mojo, mojocommon and the apps looked at use no
     * other value - so only `ignore` is translated, and anything else is left alone to be
     * noticed rather than guessed at.
     */
    fun mouseTargetIgnore(css: String): String = selectorRule.replace(css) { m ->
        val body = m.groupValues[2]
        if (!mouseTarget.containsMatchIn(body)) m.value else {
            val selector = m.groupValues[1]
            val children = selector.split(',').joinToString(",") { it.trim() + " > *" }
            m.groupValues[1] + "{" + body.trimEnd().trimEnd(';') + ";pointer-events:none}" +
                children + "{pointer-events:auto}"
        }
    }

    /**
     * 2011 WebKit drew -webkit-border-image whatever the border-style; newer Chromium
     * computes border-width to 0 when border-style is none, so the image vanishes.
     * Appended to the rule: Enyo's own CSS sets border-image next to `border-style: none`.
     */
    fun borderImageNeedsStyle(css: String): String = rule.replace(css) { m ->
        if (borderImage.containsMatchIn(m.value)) m.value.dropLast(1) + ";border-style:solid;border-color:transparent}" else m.value
    }
}
