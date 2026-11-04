package org.webosarchive.lunacy.card

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * webOS media that Lunacy ships, shared with Android (codepoet, 2026-11-04): copied once into
 * one of Android's own media folders, where Android's media library lists it for Android and
 * its apps, and which [UserFiles] maps into /media/internal for webOS apps. What the owner
 * deletes stays deleted: a later Lunacy doesn't copy it back. Until Android's storage can be
 * written (Android 6 to 9 ask for it), the copy waits for the next start.
 *
 * - The ringtones (assets/luna/ringtones): Notifications/webOS, as notification sounds
 *   ([Ringtones]); /media/internal/ringtones.
 * - The TouchPad's wallpapers (assets/luna/wallpapers): Pictures/webOS Wallpapers, where
 *   Android's gallery and its wallpaper chooser find them; /media/internal/wallpapers, where
 *   Screen & Lock's picker finds them.
 */
object SharedMedia {
    const val WALLPAPERS_DIR = "Pictures/webOS Wallpapers"
    private const val PREFS = "sharedmedia"

    /** Android's folder [relative] in shared storage, made if need be; null if it can't be written. */
    fun folder(relative: String): File? {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return null
        val dir = Environment.getExternalStoragePublicDirectory(relative)
        return dir.takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() }
    }

    /** Copies assets/[assets] into [relative], once; true when done. Off the main thread. */
    fun install(context: Context, key: String, assets: String, relative: String, keep: (String) -> Boolean,
                mime: (String) -> String, media: ContentValues.() -> Unit = {}): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(key, false)) return true
        val names = context.assets.list(assets)?.filter(keep).orEmpty()
        val dir = folder(relative)
        val done = if (dir != null) copyFiles(context, assets, dir, names)
            else if (Build.VERSION.SDK_INT >= 29) insertMedia(context, assets, relative, names, mime, media) else false
        if (done) prefs.edit().putBoolean(key, true).apply()
        else Log.i(AppServer.TAG, "$key: Android's storage can't be written yet; trying again next start")
        return done
    }

    private fun copyFiles(context: Context, assets: String, dir: File, names: List<String>): Boolean = try {
        val written = ArrayList<String>()
        for (n in names) {
            val out = File(dir, n)
            if (out.exists()) continue
            context.assets.open("$assets/$n").use { i -> out.outputStream().use { i.copyTo(it) } }
            written += out.path
        }
        scan(context, written)
        true
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "shared media: copy failed: $e"); false
    }

    /** Android 10 and later, where files may not be: Android's media library writes them for Lunacy. */
    @android.annotation.TargetApi(29)
    private fun insertMedia(context: Context, assets: String, relative: String, names: List<String>,
                            mime: (String) -> String, media: ContentValues.() -> Unit): Boolean = try {
        val resolver = context.contentResolver
        names.all { n ->
            val m = mime(n)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, n)
                put(MediaStore.MediaColumns.MIME_TYPE, m)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relative/")
                media()
            }
            val collection = if (m.startsWith("image/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values)
            uri != null && resolver.openOutputStream(uri)?.use { o -> context.assets.open("$assets/$n").use { it.copyTo(o) } } != null
        }
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "shared media: media library refused: $e"); false
    }

    fun scan(context: Context, paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    /**
     * The wallpapers, shared; and the copy earlier Lunacys kept in their own /media/internal/
     * wallpapers moved out of the way, since a folder there would hide the shared one from
     * webOS apps. A file the owner added there goes into the shared folder.
     */
    fun installWallpapers(context: Context, webosRoot: File) {
        val image = { n: String -> n.endsWith(".jpg", true) || n.endsWith(".jpeg", true) || n.endsWith(".png", true) }
        val done = install(context, "wallpapers", "luna/wallpapers", WALLPAPERS_DIR, image,
            mime = { n -> if (n.endsWith(".png", true)) "image/png" else "image/jpeg" })
        val old = File(UserFiles.own(webosRoot), "wallpapers")
        val shared = folder(WALLPAPERS_DIR)
        if (!done || shared == null) {
            // Not yet (no storage permission): Lunacy's own copy, so webOS apps have them meanwhile.
            runCatching {
                old.mkdirs()
                for (n in context.assets.list("luna/wallpapers").orEmpty().filter(image)) {
                    val out = File(old, n)
                    if (!out.exists()) context.assets.open("luna/wallpapers/$n").use { i -> out.outputStream().use { i.copyTo(it) } }
                }
            }
            return
        }
        if (!old.isDirectory) return
        val moved = ArrayList<String>()
        for (f in old.listFiles().orEmpty()) {
            if (f.isDirectory) continue
            if (f.name.startsWith(".")) { f.delete(); continue }
            val there = File(shared, f.name)
            try {
                if (!there.exists()) { f.copyTo(there); moved += there.path }
                f.delete()
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "wallpapers: couldn't move ${f.name}: $e")
            }
        }
        scan(context, moved)
        if (old.listFiles().isNullOrEmpty()) old.delete()
    }
}
