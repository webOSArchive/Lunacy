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
 */
class ServeUrl(url: String) {
    private val uri = runCatching { java.net.URI(url) }.getOrNull()
    val host: String? = uri?.host
    /** The path, percent-decoded; null if the URL doesn't parse. */
    val path: String? = uri?.rawPath?.let(::decode)
    val encodedPath: String? = uri?.rawPath
    private val rawQuery: String? = uri?.rawQuery

    fun getQueryParameter(name: String): String? {
        val q = rawQuery ?: return null
        for (pair in q.split('&')) {
            val eq = pair.indexOf('=')
            val k = decode(if (eq < 0) pair else pair.substring(0, eq))
            if (k == name) return if (eq < 0) "" else decode(pair.substring(eq + 1))
        }
        return null
    }

    override fun toString(): String = uri?.toString().orEmpty()

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
