package org.webosarchive.lunacy.card

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A log a tester can send: Device Info's "Save a Log" (system/saveLog, system/shareLog).
 *
 * Testers mostly have no adb, and release builds close Lunacy to `run-as` and DevTools, so a
 * report used to arrive with nothing to go on. This writes one text file into Android's
 * Download folder (/media/internal/downloads), where the owner can find it in any file app,
 * and hands it to Android's share sheet when asked:
 *
 * - what Lunacy runs on (Device Info's own report, without the device's serial number and
 *   webOS device id) and which of Android's permissions it holds;
 * - what Lunacy can see of Android's media folders, counts only (UserFiles.diagnose);
 * - Lunacy's own lines from Android's log (`logcat -d`: since Android 4.1 an app reads only its
 *   own), which carry the bus, the services and every app page's console;
 * - webOS's /var/log/messages, what `palm-log` reads.
 *
 * Both logs were already written to leave out what isn't Lunacy's to pass on: the bus blanks
 * passwords and tokens and gives Contacts', Calendar's and their services' calls only as a
 * length, and those apps' consoles never reach either log (Bus.personal, Bus.handlesPeople).
 * An email account's address and its messages' subjects can still appear, and the dialog says so.
 */
class LogExport(private val context: Context, private val webosRoot: File) {

    /** Writes the log; off the main thread. [report] is system/getEnvironment's answer, [permissions] permissions/status's. */
    fun save(report: String, permissions: String): String {
        val name = "lunacy-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val dir = folder() ?: return Bus.error("Neither Android's Download folder nor Lunacy's own could be written")
        val file = File(dir, name)
        return try {
            file.bufferedWriter().use { w ->
                w.write("Lunacy log, ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}\n\n")
                section(w, "Environment") { w.write(environment(report)) }
                section(w, "Android permissions") { w.write(JSONObject(permissions).apply { remove("returnValue") }.toString(2)) }
                // Written now: the index logs only when its result changes, and that line may
                // have left Android's log by the time a log is saved.
                section(w, "Media folders") { w.write(UserFiles.diagnose(context)) }
                section(w, "Android log (Lunacy's own lines)") { logcat(w) }
                section(w, "webOS log (/var/log/messages)") {
                    for (f in listOf(File(webosRoot, "var/log/messages.0"), File(webosRoot, "var/log/messages")))
                        if (f.isFile) f.bufferedReader().use { it.copyTo(w) }
                }
            }
            // Android's file apps list the Download folder from its media library.
            MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("text/plain"), null)
            Log.i(AppServer.TAG, "log saved: ${file.path} (${file.length()} bytes)")
            Bus.ok(mapOf("path" to "$WEBOS_DIR/$name", "name" to name, "bytes" to file.length(),
                "android" to (dir != own())))
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "log export failed", e)
            file.delete()
            Bus.error("The log couldn't be written: ${e.message}")
        }
    }

    /** Hands a saved log to Android's share sheet, lent through [MediaFiles]. */
    fun share(path: String): String {
        if (!SAVED.matches(path)) return Bus.error("Not a saved log: $path")
        if (UserFiles.resolve(webosRoot, path.removePrefix("/media/internal/"))?.isFile != true) return Bus.error("The log is gone: $path")
        val uri: Uri = MediaFiles.uriFor(path) ?: return Bus.error("Not a saved log: $path")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, path.substringAfterLast('/'))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            Bus.ok()
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "log share failed", e)
            Bus.error("Android couldn't share the log: ${e.message}")
        }
    }

    /** Android's Download folder when Lunacy may write there, else /media/internal/downloads in Lunacy's own tree. */
    private fun folder(): File? {
        val android = UserFiles.androidDir("downloads")
        if (android != null && android.canWrite()) return android
        return own().takeIf { it.isDirectory || it.mkdirs() }
    }

    private fun own() = File(UserFiles.own(webosRoot), "downloads")

    /** Device Info's report, less what identifies the device itself. */
    private fun environment(report: String): String {
        val j = JSONObject(report)
        j.remove("returnValue")
        j.optJSONObject("device")?.remove("serial")
        j.optJSONObject("webos")?.apply { remove("serial"); remove("nduid") }
        return j.toString(2)
    }

    private fun logcat(w: Writer) {
        val p = ProcessBuilder("logcat", "-d", "-v", "threadtime").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().use { it.copyTo(w) }
        p.waitFor()
    }

    private inline fun section(w: Writer, title: String, body: () -> Unit) {
        w.write("==== $title ====\n")
        try { body() } catch (e: Exception) { w.write("(couldn't be read: $e)") }
        w.write("\n\n")
    }

    companion object {
        private const val WEBOS_DIR = "/media/internal/downloads"
        private val SAVED = Regex("""^/media/internal/downloads/lunacy-log-\d{8}-\d{6}\.txt$""")
    }
}
