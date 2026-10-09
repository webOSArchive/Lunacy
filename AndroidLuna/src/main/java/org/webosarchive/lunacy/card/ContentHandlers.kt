package org.webosarchive.lunacy.card

import org.json.JSONObject
import java.io.File

/**
 * The application manager's content handlers: which app opens a file of each type.
 *
 * An installed app names its types in appinfo.json's `mimeTypes` (Adobe Reader:
 * `{"mime":"application/pdf","extension":"pdf"}`; Email: `"stream": false`), and the system's
 * own are the `resources` of /usr/palm/command-resource-handlers.json (`extn`, `mime`, `appId`,
 * `streamable`). webOS read the apps' again on every install and removal; here the table is
 * built from the app registry each time it is asked, and the registry is rescanned after every
 * install and removal, so an app's types come and go with it. Entries for an app Lunacy
 * doesn't have (the streaming music player) are left out: nothing could open them.
 *
 * Measured on the reference TouchPad, 2026-10-09, with Adobe Reader and Quickoffice installed:
 * a lookup takes the first entry for the extension or mime - an mp4 is `video/mp4-generic`,
 * the system list's first mp4, and a doc is `application/doc`, Quickoffice's first. Installed
 * apps' entries come before the system's here; the device numbered its entries (`index`) by a
 * counter of its own, and here the number is the entry's place in the table.
 */
class ContentHandlers(private val registry: AppRegistry, private val webosRoot: File) {
    class Entry(val mime: String, val extension: String, val appId: String, val streamable: Boolean, val system: Boolean, val index: Int)

    fun entries(): List<Entry> {
        val out = ArrayList<Entry>()
        fun add(mime: String, ext: String, app: String, stream: Boolean, system: Boolean) {
            if (mime.isEmpty() || app.isEmpty() || registry.get(app) == null) return
            out += Entry(mime.lowercase(), ext.lowercase(), app, stream, system, out.size)
        }
        for (app in registry.apps) {
            val list = app.appinfo.optJSONArray("mimeTypes") ?: continue
            for (i in 0 until list.length()) {
                val m = list.optJSONObject(i) ?: continue
                add(m.optString("mime"), m.optString("extension"), app.id, m.optBoolean("stream", false), false)
            }
        }
        val system = runCatching {
            JSONObject(File(webosRoot, "usr/palm/command-resource-handlers.json").readText()).optJSONArray("resources")
        }.getOrNull()
        for (i in 0 until (system?.length() ?: 0)) {
            val r = system!!.optJSONObject(i) ?: continue
            add(r.optString("mime"), r.optString("extn"), r.optString("appId"), r.optBoolean("streamable", false), true)
        }
        return out
    }

    fun byExtension(ext: String): Entry? = ext.lowercase().takeIf { it.isNotEmpty() }?.let { e -> entries().firstOrNull { it.extension == e } }
    fun byMime(mime: String): Entry? = mime.lowercase().takeIf { it.isNotEmpty() }?.let { m -> entries().firstOrNull { it.mime == m } }

    /** A URL's or path's extension, as the device read it: after the last dot of its path. */
    fun extensionOf(target: String): String {
        val path = target.substringBefore('#').substringBefore('?').substringAfterLast('/')
        return if ('.' in path) java.net.URLDecoder.decode(path.substringAfterLast('.'), "UTF-8") else ""
    }

    /** The entry for an `open` target: a file, by its extension. */
    fun forTarget(target: String): Entry? = byExtension(extensionOf(target))

    /** applicationManager/getHandlerForExtension. */
    fun handlerForExtension(p: JSONObject): String {
        val ext = p.optString("extension")
        val e = byExtension(ext) ?: return JSONObject().put("subscribed", false).put("returnValue", false)
            .put("errorCode", "No mime type mapped to extension $ext").toString()
        return answer(e)
    }

    /** applicationManager/getHandlerForUrl: by the URL's extension, a web one's too. */
    fun handlerForUrl(p: JSONObject): String {
        val url = p.optString("url")
        val e = forTarget(url) ?: return JSONObject().put("subscribed", false).put("returnValue", false)
            .put("errorCode", "No handler found for url [$url]").toString()
        return answer(e)
    }

    private fun answer(e: Entry) = JSONObject().put("subscribed", false).put("returnValue", true)
        .put("mimeType", e.mime).put("appId", e.appId).put("download", !e.streamable).toString()

    /** listAllHandlersForMime's activeHandler for [mime], or null. */
    fun activeHandler(mime: String): JSONObject? {
        val e = byMime(mime) ?: return null
        val j = JSONObject().put("mime", e.mime).put("extension", e.extension).put("appId", e.appId)
            .put("streamable", e.streamable).put("index", e.index)
        if (e.system) j.put("tag", "system-default")
        return j.put("appName", registry.get(e.appId)?.title ?: e.appId)
    }
}
