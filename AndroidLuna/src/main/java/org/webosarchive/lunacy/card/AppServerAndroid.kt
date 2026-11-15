package org.webosarchive.lunacy.card

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * AppServer (in LunaCore) as Android's WebViews ask it: by Uri, answered with a
 * WebResourceResponse. [image]: the engine is loading it to draw it, which decides a JPEG's
 * EXIF orientation ([ExifOrientation]), as AppServer did before LunaCore.
 */
fun AppServer.serve(uri: Uri, image: Boolean = false): WebResourceResponse? {
    val s = serve(uri.toString()) ?: return null
    if (image && s.mimeType == "image/jpeg" && s.data != null) s.data = ExifOrientation.ignored(s.data!!)
    return s.toWebResourceResponse()
}

fun Served.toWebResourceResponse() = WebResourceResponse(mimeType, encoding, statusCode, reasonPhrase, responseHeaders, data)

/** What serving needs from Android: its folders in user storage, playlists, the thumbnailer, its own routes. */
object AndroidServerHooks : ServerHooks {
    /** An Android app's icon, for the Android Notifications app's dashboards only, while it has a notification. */
    override fun platformRoute(app: String, path: String): Served? {
        val prefix = AndroidNotifications.ICON_PATH.trimStart('/')
        if (!path.startsWith(prefix)) return null
        val bytes = if (app == AndroidNotifications.APP_ID) AndroidNotifications.dashboardIcon(path.removePrefix(prefix).removeSuffix(".svg")) else null
        return bytes?.let { Served("image/svg+xml", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(it)) }
            ?: Served("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override val pre3Dashboards: Boolean get() = FormFactor.pre3Dashboards
    override fun userFile(webosRoot: File, rel: String): File? = UserFiles.resolve(webosRoot, rel)
    override fun playlist(s: InputStream, name: String, url: String): InputStream = LocalNet.playlist(s, name, url)
    override fun extractfs(spec: String, open: (String) -> (() -> InputStream?)?): Served? =
        Extractfs.read(spec, Extractfs.Opener { open(it) })?.let {
            Served(it.mime, null, 200, "OK", mapOf("Cache-Control" to "max-age=600"), ByteArrayInputStream(it.bytes))
        }
}
