package org.webosarchive.lunacy.card

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder

/**
 * Plain-http images and media for pages on an engine that won't fetch them itself.
 *
 * webOS pages ran from file://, and an app could put any http:// URL in an image or a player -
 * a Plex client shows its server's thumbnails at http://192.168.x.x:32400. Lunacy's pages are on
 * https app origins, and the WebView was told to allow mixed content, which old engines do. A
 * current WebView (149, Pixel Tablet, measured 2026-10-08) still blocks a plain-http image to a
 * private address ("Mixed Content ... has been blocked") and leaves a request to the loopback
 * interface pending for good, with no permission request to the app to answer - Chromium's
 * Local Network Access. A public http image still loads.
 *
 * On such an engine compat.js points the page's http:// images and media at [PATH] on its own
 * origin, and this fetches them with Lunacy's own HTTP (the network shim's, with the page's
 * cookies and the device's user agent) and streams the answer back, byte ranges and all, as
 * players seek. The loopback MediaServer is out of reach there too, so /media/internal media
 * stays on the app origin as well. See Docs/architecture.md, "Local network".
 */
object LocalNet {
    const val PATH = "/__lunacy/net"

    /**
     * Whether the engine holds back an https page's plain-http requests to the local network.
     * Measured only at Chromium 149 (yes). The cut-off of 141 is an assumption, not a
     * measurement: about where Chrome brought in Local Network Access checks, and well after
     * Chromium began playing HLS itself, which a same-origin URL needs. Below it nothing
     * changes: old engines keep loading the URLs directly, and HLS keeps the loopback server,
     * since there Chromium hands it to Android's MediaPlayer.
     */
    @Volatile var blocked = false
        private set

    private var checked = false

    /** Reads the engine's version, once; on the main thread, before the first page loads. */
    fun init(context: Context) {
        if (checked) return
        checked = true
        Engine.init(context)
        blocked = Engine.major >= 141
    }

    /** Request headers a player or image load sends that matter to the server. */
    private val PASS = setOf("range", "accept", "accept-language", "if-range")

    /** [req] if it is for [PATH] on an app origin: fetched, else null. Off the main thread. */
    fun serve(req: WebResourceRequest, userAgent: String): WebResourceResponse? {
        val uri = req.url
        if (uri.host?.endsWith(AppServer.HOST_SUFFIX) != true || uri.path != PATH) return null
        val target = runCatching { uri.getQueryParameter("u") }.getOrNull()
        if (target == null || !target.startsWith("http://", ignoreCase = true)) return status(400, "Bad Request")
        val headers = mutableListOf("User-Agent" to userAgent, "Accept-Encoding" to "identity")
        for ((k, v) in req.requestHeaders) if (k.lowercase() in PASS) headers += k to v
        return try {
            // The TouchPad gave up connecting after about 9 s, as the network shim does.
            val c = Http.connect(Http.Request(if (req.method == "HEAD") "HEAD" else "GET", target, headers, connectTimeoutMs = 9_000))
            val code = c.responseCode
            // A WebResourceResponse can't carry a 3xx; Http.connect has followed redirects.
            if (code in 300..399) return status(502, "Bad Gateway")
            val type = c.contentType
            if (code == 200 && isPlaylist(type, c.url.path)) {
                val text = c.inputStream.use { it.bufferedReader().readText() }
                return WebResourceResponse("application/vnd.apple.mpegurl", "utf-8", 200, "OK", emptyMap(),
                    ByteArrayInputStream(rewrite(text, c.url.toString()).toByteArray()))
            }
            val out = LinkedHashMap<String, String>()
            for (h in listOf("Content-Range", "Accept-Ranges", "Cache-Control", "Last-Modified", "ETag"))
                c.getHeaderField(h)?.let { out[h] = it }
            val raw = (if (code >= 400) c.errorStream else c.inputStream) ?: ByteArrayInputStream(ByteArray(0))
            val length = c.getHeaderField("Content-Length")?.trim()?.toLongOrNull()
            val range = c.getHeaderField("Content-Range")?.let { RANGE.find(it) }
            val body = when {
                code == 206 && range != null -> {
                    val first = range.groupValues[1].toLong()
                    val total = range.groupValues[3].toLongOrNull() ?: (range.groupValues[2].toLong() + 1)
                    Whole(raw, first, total - first)
                }
                length != null -> Whole(raw, 0, length)
                else -> raw
            }
            WebResourceResponse(type?.substringBefore(';')?.trim()?.ifEmpty { null } ?: "application/octet-stream",
                type?.let { Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) },
                code, c.responseMessage?.ifEmpty { null } ?: "OK", out, body)
        } catch (e: IOException) {
            status(502, "Bad Gateway")
        }
    }

    /**
     * An HLS playlist served from /media/internal ([name] there, at [url]): on an engine that
     * blocks, its http:// URIs are pointed at [PATH] too, since a player fetches them itself
     * (Plex's helper service writes its transcoder's playlist there, segments on the server).
     */
    fun playlist(s: InputStream, name: String, url: String): InputStream =
        if (!blocked || !isPlaylist(null, name)) s
        else ByteArrayInputStream(rewrite(s.use { it.bufferedReader().readText() }, url).toByteArray())

    private fun isPlaylist(type: String?, path: String?) =
        type?.contains("mpegurl", ignoreCase = true) == true || path?.endsWith(".m3u8", ignoreCase = true) == true

    /** Each URI in an HLS playlist (its lines and URI="..." attributes), resolved against [base]; http ones via [PATH]. */
    private fun rewrite(text: String, base: String): String = text.lines().joinToString("\n") { line ->
        when {
            line.isBlank() -> line
            line.startsWith("#") -> URI_ATTR.replace(line) { m -> "URI=\"" + via(m.groupValues[1], base) + "\"" }
            else -> via(line.trim(), base)
        }
    }

    private val URI_ATTR = Regex("URI=\"([^\"]*)\"")

    private fun via(u: String, base: String): String {
        val abs = runCatching { URL(URL(base), u).toString() }.getOrNull() ?: return u
        return if (abs.startsWith("http://", ignoreCase = true)) "$PATH?u=" + URLEncoder.encode(abs, "UTF-8") else u
    }

    private val RANGE = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)

    /**
     * The body as the WebView expects it: the whole resource, of which the server sent [rest]
     * bytes from [first] on. The WebView applies a request's Range to the stream itself (its
     * InputStreamReader): it checks the range against available(), then skips to the first
     * byte. The server has already answered that range, so the bytes before [first] are skipped
     * without being there. Otherwise every seek, and the player's look at the end of the file,
     * failed with net::ERR_FAILED; and a network stream's available() of 0 also gave the answer
     * a second Content-Length of 0 (WebView 153, 2026-10-09) - Apollo's Pandora tracks never
     * played. A resource past 2 GB reports 2 GB.
     */
    private class Whole(private val s: InputStream, private var first: Long, private var rest: Long) : InputStream() {
        override fun available() = (first + rest).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        override fun skip(n: Long): Long {
            if (first == 0L) return super.skip(n)
            return minOf(n, first).also { first -= it }
        }
        override fun read(): Int = s.read().also { if (it >= 0) rest-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int = s.read(b, off, len).also { if (it > 0) rest -= it }
        override fun close() = s.close()
    }

    private fun status(code: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
