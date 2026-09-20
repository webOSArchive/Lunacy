package org.webosarchive.lunacy.card

import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Serves every request to an app origin: the app's own files at their webOS path, the
 * frameworks, and Lunacy's injected scripts. See docs/architecture.md, "Card host".
 */
class AppServer(private val assets: AssetManager, private val files: AppFiles, private val webosRoot: java.io.File) {
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
        /**
         * webOS's own system UI, which apps reach at its absolute path: enyo.FilePicker
         * loads /usr/lib/luna/system/luna-systemui/app/FilePicker/filepicker.html in an
         * iframe. Lunacy serves its own pages there, so the control works in every app.
         */
        const val SYSTEM_UI = "usr/lib/luna/system/luna-systemui/app/"
        /**
         * A scaled copy of an image in the webOS tree: `?__lunacy_thumb=160` gives a JPEG
         * whose short side is about 160 px. A picker or gallery would otherwise decode
         * full-size photos, which 1 GB of RAM can't hold. Lunacy's own, hence the prefix.
         */
        const val THUMB_PARAM = "__lunacy_thumb"
    }

    fun serve(uri: Uri): WebResourceResponse? {
        val host = uri.host ?: return null
        if (!host.endsWith(HOST_SUFFIX)) return null  // real network
        val path = uri.path.orEmpty().trimStart('/')
        if (path.startsWith("__lunacy/fonts/")) return asset("luna/fonts/" + path.removePrefix("__lunacy/fonts/"), path)
        if (path.startsWith("__lunacy/")) return asset("lunacy/" + path.removePrefix("__lunacy/"), path)
        // The TouchPad answers this one with 200 and an empty body; Enyo's Tellurium hooks
        // read it while starting, and a 404 makes them throw where a device doesn't.
        if (path == "usr/palm/frameworks/tellurium/tellurium_config.json") {
            return WebResourceResponse("application/json", "utf-8", 200, "OK",
                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(ByteArray(0)))
        }
        val fw = FRAMEWORK.matchEntire(path)
        val thumb = runCatching { uri.getQueryParameter(THUMB_PARAM)?.toInt() }.getOrNull()
        val resp = when {
            fw != null -> asset("fw/enyo/1.0/" + fw.groupValues[1], path)
            path.startsWith(SYSTEM_UI) -> asset("luna-systemui/" + path.removePrefix(SYSTEM_UI), path)
            path.startsWith(CRYPTOFS) -> files.open(path.removePrefix(CRYPTOFS))?.let { respond(it, path) }
            // webOS's user storage, shared by apps and services (JS services write files here).
            path.startsWith(MEDIA_INTERNAL) -> {
                val rel = path.removePrefix(MEDIA_INTERNAL)
                if (thumb != null) thumbnail(rel, thumb) else internal(rel)?.let { respond(it, path) }
            }
            else -> null
        }
        if (resp == null) Log.w(TAG, "404 $uri")
        return resp ?: WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(),
            ByteArrayInputStream(ByteArray(0)))
    }

    /** webOS's user storage, Lunacy's own and the Android folders mapped into it (UserFiles). */
    private fun internal(rel: String): InputStream? =
        UserFiles.resolve(webosRoot, rel)?.takeIf { it.isFile }?.inputStream()

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

    private fun asset(assetPath: String, name: String): WebResourceResponse? {
        val stream: InputStream = try { assets.open(assetPath) } catch (e: IOException) { return null }
        return respond(stream, name)
    }

    private fun respond(stream: InputStream, name: String): WebResourceResponse {
        val mime = mimeOf(name)
        val body = when (mime) {
            "text/html" -> injectInto(stream)
            "text/css" -> CssTransforms.apply(stream)
            else -> stream
        }
        return WebResourceResponse(mime, "utf-8", 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), body)
    }

    /** Global serve-time transform: Lunacy's scripts run first in every page. */
    private fun injectInto(s: InputStream): InputStream {
        val html = s.bufferedReader().readText()
        val tag = "<link rel=\"stylesheet\" href=\"/__lunacy/fonts.css\">" +
            "<script src=\"/__lunacy/compat.js\"></script><script src=\"/__lunacy/bridge.js\"></script>" +
            "<script src=\"/__lunacy/net.js\"></script>"
        val m = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        val out = if (m != null) html.substring(0, m.range.last + 1) + tag + html.substring(m.range.last + 1) else tag + html
        return ByteArrayInputStream(out.toByteArray())
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
 * Global serve-time CSS transforms. Each is a mechanical rule applied to every app's CSS;
 * none names an app.
 */
object CssTransforms {
    private val rule = Regex("\\{[^{}]*\\}")
    private val borderImage = Regex("-webkit-border-image\\s*:(?!\\s*none)", RegexOption.IGNORE_CASE)

    fun apply(s: InputStream): InputStream =
        ByteArrayInputStream(borderImageNeedsStyle(s.bufferedReader().readText()).toByteArray())

    /**
     * 2011 WebKit drew -webkit-border-image whatever the border-style; newer Chromium
     * computes border-width to 0 when border-style is none, so the image vanishes.
     * Appended to the rule: Enyo's own CSS sets border-image next to `border-style: none`.
     */
    fun borderImageNeedsStyle(css: String): String = rule.replace(css) { m ->
        if (borderImage.containsMatchIn(m.value)) m.value.dropLast(1) + ";border-style:solid;border-color:transparent}" else m.value
    }
}
