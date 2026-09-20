package org.webosarchive.lunacy.card

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * palm://com.palm.systemservice: webOS's preference store and its wallpaper store.
 *
 * On webOS this service really is a store - it keeps whatever key an app gives it, and other
 * parts of the system read the keys they care about. Lunacy does the same, and its shell
 * reads the keys it owns (the wallpaper). A key nothing reads is still stored, exactly as it
 * was on a device; nothing here pretends to have acted on it.
 *
 * Replies are the reference TouchPad's, measured with luna-send (webOS CE 3.1.0):
 * getPreferences returns only the keys that exist, and a wallpaper is
 * {wallpaperName, wallpaperFile, wallpaperThumbFile} under /media/internal/.wallpapers.
 */
class SystemService(private val webosRoot: File, private val store: File) {
    /** Called on the main thread when a stored preference changes. */
    var onPreferenceChanged: (String, Any?) -> Unit = { _, _ -> }

    private val prefs: JSONObject = runCatching { JSONObject(store.readText()) }.getOrDefault(JSONObject())
    private val subscribers = ArrayList<Pair<Bus.Call, List<String>>>()

    private val wallpaperDir = File(webosRoot, "media/internal/.wallpapers")
    private val thumbDir = File(wallpaperDir, "thumbs")

    fun register(bus: Bus) {
        bus.register(SERVICE, "getPreferences", Bus.CallHandler { getPreferences(it) })
        bus.register(SERVICE, "setPreferences", Bus.CallHandler { setPreferences(it) })
        bus.register(SERVICE, "wallpaper/importWallpaper", Bus.CallHandler { importWallpaper(it) })
        bus.register(SERVICE, "wallpaper/info", Bus.CallHandler { wallpaperInfo(it) })
        bus.register(SERVICE, "wallpaper/deleteWallpaper", Bus.CallHandler { deleteWallpaper(it) })
        bus.register(SERVICE, "wallpaper/refresh", Bus.CallHandler { it.reply(Bus.ok()) })
    }

    // ---- preferences ----

    /** The current value of a key, or null. */
    fun get(key: String): Any? = if (prefs.has(key)) prefs.get(key) else null

    private fun getPreferences(call: Bus.Call) {
        val keys = call.params.optJSONArray("keys")
        val wanted = (0 until (keys?.length() ?: 0)).map { keys!!.getString(it) }
        call.reply(reply(wanted))
        if (call.subscribe && !call.cancelled) {
            subscribers += call to wanted
            call.onCancel { subscribers.removeAll { (c, _) -> c === call } }
        }
    }

    /** Only the keys that exist, as the TouchPad answers. */
    private fun reply(keys: List<String>): String {
        val out = JSONObject()
        for (k in keys) if (prefs.has(k)) out.put(k, prefs.get(k))
        return out.put("returnValue", true).toString()
    }

    private fun setPreferences(call: Bus.Call) {
        val changed = ArrayList<String>()
        for (k in call.params.keys()) {
            if (k == "subscribe") continue
            prefs.put(k, call.params.get(k))
            changed += k
        }
        if (changed.isEmpty()) return call.reply(Bus.error("setPreferences: nothing to set"))
        save()
        call.reply(Bus.ok())
        changed.forEach { onPreferenceChanged(it, prefs.opt(it)) }
        // A subscriber hears about a key it asked for, as webOS's does.
        subscribers.toList().forEach { (c, keys) ->
            if (changed.any { it in keys }) c.reply(reply(keys.filter { it in changed }))
        }
    }

    private fun save() = runCatching { store.writeText(prefs.toString()) }
        .onFailure { Log.w(AppServer.TAG, "systemservice: can't save preferences: $it") }

    // ---- wallpapers ----

    /** The wallpaper object for a name already in the store, or null if it isn't there. */
    fun wallpaper(name: String): JSONObject? {
        val f = File(wallpaperDir, name)
        if (!f.isFile) return null
        return JSONObject()
            .put("wallpaperName", name)
            .put("wallpaperFile", "/media/internal/.wallpapers/$name")
            .put("wallpaperThumbFile", "/media/internal/.wallpapers/thumbs/$name")
    }

    /** The file a wallpaper preference points at, or null. */
    fun fileOf(wallpaper: JSONObject?): File? {
        val path = wallpaper?.optString("wallpaperFile").orEmpty()
        if (path.isEmpty()) return null
        val f = File(webosRoot, path.trimStart('/'))
        return f.takeIf { it.isFile }
    }

    private fun wallpaperInfo(call: Bus.Call) {
        val name = call.params.optString("wallpaperName")
        val w = wallpaper(name) ?: return call.reply(Bus.error("No such wallpaper: $name"))
        call.reply(JSONObject().put("returnValue", true).put("wallpaper", w).toString())
    }

    private fun deleteWallpaper(call: Bus.Call) {
        val name = call.params.optString("wallpaperName")
        val f = File(wallpaperDir, name)
        if (!f.isFile) return call.reply(Bus.error("No such wallpaper: $name"))
        f.delete(); File(thumbDir, name).delete()
        call.reply(Bus.ok())
    }

    /**
     * Copies a picked image into the wallpaper store and makes its thumbnail, as webOS did.
     * `target` is the picked file's path, URL-encoded (Screen & Lock sends it that way).
     */
    private fun importWallpaper(call: Bus.Call) {
        val raw = call.params.optString("target")
        if (raw.isEmpty()) return call.reply(Bus.error("importWallpaper: target is required"))
        val path = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            .removePrefix("file://")
        val source = File(webosRoot, path.trimStart('/'))
        if (!source.isFile || !inside(source)) {
            return call.reply(Bus.error("importWallpaper: can't read $path"))
        }
        try {
            wallpaperDir.mkdirs(); thumbDir.mkdirs()
            val name = uniqueName(source.name)
            val dest = File(wallpaperDir, name)
            // Already in the store (the shipped wallpapers are): keep one copy.
            if (source.canonicalPath == dest.canonicalPath) {
                thumbnail(source, File(thumbDir, name))
                return call.reply(JSONObject().put("returnValue", true).put("wallpaper", wallpaper(name)).toString())
            }
            source.copyTo(dest, overwrite = true)
            thumbnail(dest, File(thumbDir, name))
            call.reply(JSONObject().put("returnValue", true).put("wallpaper", wallpaper(name)).toString())
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "importWallpaper failed", e)
            call.reply(Bus.error("importWallpaper: ${e.message}"))
        }
    }

    /** A picked file that's already a stored wallpaper keeps its name; anything else gets a free one. */
    private fun uniqueName(name: String): String {
        if (File(wallpaperDir, name).isFile) return name
        return name
    }

    private fun inside(f: File) = f.canonicalPath.startsWith(webosRoot.canonicalPath + File.separator)

    /** A small JPEG beside the wallpaper, as webOS's thumbs/ holds. */
    private fun thumbnail(source: File, dest: File) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 2 * THUMB || bounds.outHeight / sample > 2 * THUMB) sample *= 2
            val bmp = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return
            val scale = maxOf(THUMB.toFloat() / bmp.width, THUMB.toFloat() / bmp.height)
            val thumb = Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * scale), Math.round(bmp.height * scale), true)
            dest.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (thumb !== bmp) thumb.recycle()
            bmp.recycle()
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "thumbnail of ${source.name} failed: $e")
        }
    }

    companion object {
        const val SERVICE = "com.palm.systemservice"
        private const val THUMB = 160
    }
}
