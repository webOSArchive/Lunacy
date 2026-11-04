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
 *   there once ([SharedMedia], which the wallpapers share).
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
    private fun folder(): File? = SharedMedia.folder(ANDROID_DIR)

    /** Copies the shipped tones into Android's storage, once (SharedMedia). Off the main thread. */
    fun install() = worker.execute {
        // The flag 0.9.1 kept, carried over so tones the owner deleted stay deleted.
        val old = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (old.getBoolean(INSTALLED, false)) {
            context.getSharedPreferences("sharedmedia", Context.MODE_PRIVATE).edit().putBoolean("ringtones", true).apply()
        }
        SharedMedia.install(context, "ringtones", ASSETS, ANDROID_DIR, { it.endsWith(".mp3") }, mime = { "audio/mpeg" }) {
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Audio.AudioColumns.IS_NOTIFICATION, 1)
        }
    }

    private fun scan(paths: List<String>) = SharedMedia.scan(context, paths)

    /** A webOS path (or file:// URL) to the real file, if it is in the webOS tree. */
    private fun real(path: String): File? {
        val p = path.removePrefix("file://")
        if (!p.startsWith("/")) return null
        val f = if (p.startsWith("/media/internal")) UserFiles.resolve(webosRoot, p.removePrefix("/media/internal"))
            else File(webosRoot, p.trimStart('/'))
        return f?.takeIf { UserFiles.inside(it, webosRoot) || UserFiles.webosPath(webosRoot, it) != null }
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
