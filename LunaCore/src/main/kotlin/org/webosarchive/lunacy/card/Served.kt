package org.webosarchive.lunacy.card

import java.io.File
import java.io.InputStream

/**
 * An answer from [AppServer]: what Android's WebResourceResponse carries, in LunaCore's own
 * type, with its getters' names, so the server reads as it did. AndroidLuna turns it into a
 * WebResourceResponse (AppServerAndroid.kt); Luna DE into an HTTP response.
 */
class Served(
    val mimeType: String,
    val encoding: String?,
    val statusCode: Int,
    val reasonPhrase: String,
    val responseHeaders: Map<String, String>,
    var data: InputStream?,
)

/**
 * An app-origin URL as AppServer reads it, with android.net.Uri's answers for the parts it uses:
 * the host, the path decoded and as sent, and a query parameter decoded (a "+" stays a "+", as
 * Uri.getQueryParameter leaves it).
 *
 * Read by its delimiters alone, as android.net.Uri reads one, and never refused: a caller that
 * builds a URL from a device path sends it as it is, and java.net.URI, which this was until
 * 2026-11-17, threw on the space in "/media/internal/ringtones/Cymbells (short).mp3", so a
 * chosen ringtone was "not found" and the class's default sound played in its place.
 */
class ServeUrl(private val url: String) {
    /** The host, percent-decoded: the authority without its user info and port; null without "//". */
    val host: String?
    /** The path as sent: from the authority's end to the "?" or "#". */
    val encodedPath: String?
    /** The path, percent-decoded. */
    val path: String?
    private val rawQuery: String?

    init {
        val colon = url.indexOf(':')
        val fragment = url.indexOf('#', colon + 1).let { if (it < 0) url.length else it }
        val question = url.indexOf('?', colon + 1).let { if (it < 0 || it > fragment) -1 else it }
        var pathStart = colon + 1
        var authority: String? = null
        if (url.startsWith("//", colon + 1)) {
            var end = colon + 3
            while (end < url.length && url[end] !in "/\\?#") end++
            authority = url.substring(colon + 3, end)
            pathStart = end
        }
        host = authority?.substringAfterLast('@')?.let { a ->
            // A port is a colon after the last "]": an IPv6 host has colons of its own.
            val port = a.lastIndexOf(':')
            decode(if (port > a.lastIndexOf(']')) a.substring(0, port) else a)
        }
        val pathEnd = if (question >= 0) question else fragment
        encodedPath = if (pathStart <= pathEnd) url.substring(pathStart, pathEnd) else ""
        path = decode(encodedPath)
        rawQuery = if (question >= 0) url.substring(question + 1, fragment) else null
    }

    fun getQueryParameter(name: String): String? {
        val q = rawQuery ?: return null
        for (pair in q.split('&')) {
            val eq = pair.indexOf('=')
            val k = decode(if (eq < 0) pair else pair.substring(0, eq))
            if (k == name) return if (eq < 0) "" else decode(pair.substring(eq + 1))
        }
        return null
    }

    override fun toString(): String = url

    companion object {
        /** Percent-decoding as android.net.Uri.decode does it: UTF-8, and "+" left alone. */
        fun decode(s: String): String {
            if (s.indexOf('%') < 0) return s
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length) {
                    val b = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (b != null) { out.write(b); i += 3; continue }
                }
                val bytes = c.toString().toByteArray(Charsets.UTF_8)
                out.write(bytes, 0, bytes.size)
                i++
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }
}

/**
 * What AppServer needs from its platform beyond files: the parts of serving that are Android's
 * own (Android's folders in user storage, the local-network playlist rewrite, the notifications
 * app's icons, the phone's dashboards). Luna DE answers each in the plain way (no mapping, no
 * rewrite, no route).
 */
interface ServerHooks {
    /** A route of the platform's own, tried before the rest; null if [path] isn't one. */
    fun platformRoute(app: String, path: String): Served? = null
    /** Whether dashboards wear the Pre3's boxes (a phone's bottom area: FormFactor.pre3Dashboards). */
    val pre3Dashboards: Boolean get() = false
    /** [rel] under /media/internal, as a file (UserFiles.resolve on Android). */
    fun userFile(webosRoot: File, rel: String): File? =
        File(webosRoot, "media/internal/$rel").takeIf { org.webosarchive.lunacy.core.WebosPaths.inside(it, File(webosRoot, "media/internal")) }
    /** A media file served from user storage, as the platform rewrites playlists (LocalNet.playlist). */
    fun playlist(s: InputStream, name: String, url: String): InputStream = s
    /** webOS's thumbnailer (Extractfs on Android); null where the platform has none. */
    fun extractfs(spec: String, open: (String) -> (() -> InputStream?)?): Served? = null
}
