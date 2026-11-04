package org.webosarchive.lunacy.card

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * webOS's ringtones, shared with Android (codepoet, 2026-11-04).
 *
 * A TouchPad kept its ringtones in /media/internal/ringtones, and the file picker listed them
 * from the media index (MediaIndex's audio records, `isRingtone`). Here that folder is
 * Android's Notifications/webOS ([UserFiles]): a sound file there is in Android's own
 * notification-sound list, for Android's settings and every Android app, and in webOS's
 * pickers, for any webOS app that chooses its own sound (Messaging's "Ringtone").
 *
 * - **The shipped tones.** The TouchPad's ten short ringtones (assets/luna/ringtones) are copied
 *   there once. A tone the user deletes stays deleted; a later Lunacy doesn't copy it back.
 *   Until Android's storage can be written (Android 6-9 ask for it), the copy waits for the
 *   next start.
 * - **palm://com.palm.systemservice/ringtone/addRingtone** {filePath} copies a local file into
 *   the folder, and **deleteRingtone** {filePath} removes one from it and nowhere else, as
 *   luna-sysservice's RingtonePrefsHandler did, with its error texts. Android's media library
 *   is told either way, so its list follows.
 *
 * Which ringtone plays for the system's own sounds stays Android's setting (Sounds).
 */
class Ringtones(private val context: Context, private val webosRoot: File) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun register(bus: Bus) {
        bus.register(SystemService.SERVICE, "ringtone/addRingtone", Bus.CallHandler { call -> off(call) { add(call.params) } })
        bus.register(SystemService.SERVICE, "ringtone/deleteRingtone", Bus.CallHandler { call -> off(call) { delete(call.params) } })
    }

    private fun off(call: Bus.Call, work: () -> String) = worker.execute {
        val reply = try { work() } catch (e: Exception) { Log.w(AppServer.TAG, "ringtone: $e"); fail("Unable to add ringtone.") }
        main.post { call.reply(reply) }
    }

    /** Android's Notifications/webOS, made if it isn't there; null if Android's storage can't be written. */
    private fun folder(): File? {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return null
        val dir = Environment.getExternalStoragePublicDirectory(ANDROID_DIR)
        return dir.takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() }
    }

    /** Copies the shipped tones into Android's storage, once. Off the main thread. */
    fun install() = worker.execute {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(INSTALLED, false)) return@execute
        val names = context.assets.list(ASSETS)?.filter { it.endsWith(".mp3") }.orEmpty()
        val dir = folder()
        val done = if (dir != null) copyFiles(dir, names) else if (Build.VERSION.SDK_INT >= 29) insertMedia(names) else false
        if (done) prefs.edit().putBoolean(INSTALLED, true).apply()
        else Log.i(AppServer.TAG, "ringtones: Android's storage can't be written yet; trying again next start")
    }

    private fun copyFiles(dir: File, names: List<String>): Boolean = try {
        val written = ArrayList<String>()
        for (n in names) {
            val out = File(dir, n)
            if (out.exists()) continue
            context.assets.open("$ASSETS/$n").use { i -> out.outputStream().use { i.copyTo(it) } }
            written += out.path
        }
        scan(written)
        true
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "ringtones: copy failed: $e"); false
    }

    /** Android 10 and later, where files may not be: Android's media library writes them for Lunacy. */
    @android.annotation.TargetApi(29)
    private fun insertMedia(names: List<String>): Boolean = try {
        val resolver = context.contentResolver
        names.all { n ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, n)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$ANDROID_DIR/")
                put(MediaStore.Audio.AudioColumns.IS_NOTIFICATION, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            uri != null && resolver.openOutputStream(uri)?.use { o -> context.assets.open("$ASSETS/$n").use { it.copyTo(o) } } != null
        }
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "ringtones: media library refused: $e"); false
    }

    private fun scan(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    /** A webOS path (or file:// URL) to the real file, if it is in the webOS tree. */
    private fun real(path: String): File? {
        val p = path.removePrefix("file://")
        if (!p.startsWith("/")) return null
        val f = if (p.startsWith("/media/internal")) UserFiles.resolve(webosRoot, p.removePrefix("/media/internal"))
            else File(webosRoot, p.trimStart('/'))
        return f?.takeIf { it.canonicalPath.startsWith(webosRoot.canonicalPath + File.separator) || UserFiles.webosPath(webosRoot, it) != null }
    }

    private fun add(p: JSONObject): String {
        val path = p.optString("filePath")
        if (path.isEmpty()) return fail("source file missing")
        if (path.contains("://") && !path.startsWith("file://")) {
            return fail("input file specification doesn't support non-local files (use file:///path/file or /path/file format")
        }
        val source = real(path)?.takeIf { it.isFile } ?: return fail("source file doesn't exist")
        if (source.name.isEmpty()) return fail("source file name missing.")
        val dir = folder() ?: return fail("Unable to add ringtone.")
        val dest = File(dir, source.name)
        if (source.canonicalPath != dest.canonicalPath) source.copyTo(dest, overwrite = true)
        scan(listOf(dest.path))
        return Bus.ok()
    }

    private fun delete(p: JSONObject): String {
        val path = p.optString("filePath")
        if (path.isEmpty()) return fail("filePath is missing")
        val f = real(path)?.takeIf { it.isFile } ?: return fail("file doesn't exist")
        // Only from the ringtones folder, as the TouchPad allowed.
        if (path.removePrefix("file://").substringBeforeLast('/') != WEBOS_DIR) return fail("Unable to delete.")
        if (!f.delete()) return fail("Unable to delete ringtone.")
        scan(listOf(f.path))
        return Bus.ok()
    }

    private fun fail(text: String) = JSONObject().put("returnValue", false).put("errorText", text).toString()

    companion object {
        /** Android's folder for them: in its notification sounds, under a name of their own. */
        const val ANDROID_DIR = "Notifications/webOS"
        const val WEBOS_DIR = "/media/internal/ringtones"
        private const val ASSETS = "luna/ringtones"
        private const val PREFS = "ringtones"
        private const val INSTALLED = "shippedTonesCopied"
    }
}
