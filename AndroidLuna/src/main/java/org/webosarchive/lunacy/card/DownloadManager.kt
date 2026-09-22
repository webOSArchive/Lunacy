package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * `palm://com.palm.downloadmanager`, webOS's downloader.
 *
 * An app that wants a file off the web hands it to this service rather than fetching it
 * itself: it writes the file into the webOS tree, survives the app's card being closed, and
 * reports progress to whoever subscribed. drPodder fetches every podcast episode and every
 * feed's album art through it, and MeTube's "download first" route uses it too; without it
 * drPodder showed a broken image where the album art belongs, because the art's path is only
 * rewritten to the local copy once the download reports itself complete.
 *
 * Every reply shape here was measured on the reference TouchPad on 2026-09-22, with
 * `luna-send -i -a com.drnull.drpodder`, fetching a real file into /media/internal:
 *
 * ```
 * download ->  {"returnValue":true, "ticket":4, "url":"<target>",
 *               "target":"/media/internal/lunacyprobe/art.JPG", "subscribed":true }
 * then      ->  { "ticket":4 , "amountReceived":2195 , "e_amountReceived":"2195" ,
 *                 "amountTotal":6177 , "e_amountTotal":"6177" }
 * then      ->  { "ticket":4, … "destFile":"art.JPG", "destPath":"/media/internal/lunacyprobe/",
 *                 "mimetype":"image/jpeg", "completionStatusCode":200, "httpStatus":200,
 *                 "interrupted":false, "completed":true, "aborted":false, "target":… }
 * ```
 *
 * Note what the device does **not** send: the progress and completion messages carry no
 * `returnValue` at all, and a download that is going to fail still gets `returnValue: true`
 * and a ticket on the first reply - the failure arrives later, as a completion with
 * `completed: false`. An app that treats the first reply as the result therefore sees
 * success, which is what webOS's own did.
 *
 * The other methods, measured the same way:
 * ```
 * listPending             -> { "returnValue":true , "count":0}
 * allow1x {"value":true}  -> { "returnValue": true, "value": true, "subscribed": false }
 * downloadStatusQuery     -> { "ticket": 9999, "returnValue": false,
 *                              "errorCode": "ticket_not_found", "subscribed": false }
 * cancelDownload          -> { "ticket": 9999, "returnValue": true, "subscribed": false,
 *                              "aborted": true, "completed": false, "completionStatusCode": 12 }
 * deleteDownloadedFile    -> {"ticket":9999 , "returnValue":false ,
 *                             "errorCode":"requested download record not found" }
 * download {}             -> {"returnValue":false , "errorCode":"-1",
 *                             "errorText":"Failed to find param target in message", "subscribed":false }
 * ```
 * `cancelDownload` answers `returnValue: true` for a ticket it has never heard of, which is
 * the device's own behaviour and is kept.
 *
 * **Ratchet item.** A download runs on its own thread and keeps running while Lunacy is up;
 * webOS's kept going after the app that started it had gone, and resumed across a reboot.
 * Lunacy's records live only as long as the process, so a download interrupted by the shell
 * being killed is lost rather than resumable. `canHandlePause` is reported false, which is
 * what the reference device reported for this file, so no app should be asking to resume one.
 */
class DownloadManager(private val webosRoot: File) {
    private companion object {
        const val SERVICE = "com.palm.downloadmanager"
        /** webOS's own default, where a download with no targetDir landed. */
        const val DEFAULT_DIR = "/media/internal/downloads"
        /** The device writes to ".<name>" while it fetches and renames on completion. */
        const val TEMP_PREFIX = "."
        /** What the device reported for a cancelled download. */
        const val CANCELLED_CODE = 12
    }

    /** One download, live or finished. Reached by ticket. */
    private class Record(
        val ticket: Int,
        val url: String,
        val destPath: String,
        val destFile: String,
        val target: String,
        val file: File,
    ) {
        @Volatile var amountReceived = 0L
        @Volatile var amountTotal = 0L
        @Volatile var mimetype = ""
        @Volatile var httpStatus = 0
        @Volatile var completed = false
        @Volatile var aborted = false
        @Volatile var interrupted = false
        @Volatile var finished = false
        /** Everyone watching this ticket: the caller, plus any later downloadStatusQuery. */
        val watchers = java.util.Collections.synchronizedList(ArrayList<Bus.Call>())
    }

    private val records = java.util.Collections.synchronizedMap(LinkedHashMap<Int, Record>())
    private var nextTicket = 1

    fun register(bus: Bus) {
        bus.register(SERVICE, "download", Bus.CallHandler { download(it) })
        bus.register(SERVICE, "listPending") { _, _, reply ->
            reply(JSONObject(mapOf("returnValue" to true, "count" to pending())).toString())
        }
        bus.register(SERVICE, "downloadStatusQuery", Bus.CallHandler { statusQuery(it) })
        bus.register(SERVICE, "resumeDownload", Bus.CallHandler { statusQuery(it) })
        bus.register(SERVICE, "cancelDownload") { _, p, reply -> reply(cancel(p)) }
        bus.register(SERVICE, "deleteDownloadedFile") { _, p, reply -> reply(deleteFile(p)) }
        // The 1x radio, which a WiFi tablet has no business refusing; the device answers the
        // value straight back.
        bus.register(SERVICE, "allow1x") { _, p, reply ->
            reply(JSONObject(mapOf("returnValue" to true, "value" to p.optBoolean("value", true),
                "subscribed" to false)).toString())
        }
    }

    private fun pending() = records.values.count { !it.finished }

    private fun download(call: Bus.Call) {
        val target = call.params.optString("target")
        if (target.isEmpty()) {
            return call.reply(JSONObject(mapOf("returnValue" to false, "errorCode" to "-1",
                "errorText" to "Failed to find param target in message", "subscribed" to false)).toString())
        }
        val dir = call.params.optString("targetDir").ifEmpty { DEFAULT_DIR }
        val name = call.params.optString("targetFilename").ifEmpty { target.substringAfterLast('/').substringBefore('?') }
        val destPath = if (dir.endsWith("/")) dir else "$dir/"
        val file = resolve(destPath + name)
        if (file == null) {
            return call.reply(JSONObject(mapOf("returnValue" to false, "errorCode" to "-1",
                "errorText" to "Invalid target directory", "subscribed" to false)).toString())
        }
        val ticket = synchronized(this) { nextTicket++ }
        val rec = Record(ticket, target, destPath, name, destPath + name, file)
        records[ticket] = rec
        // The device answers with the ticket at once, before a byte has moved, and reports
        // "subscribed" as whether this caller asked to be kept informed.
        call.reply(JSONObject(mapOf("returnValue" to true, "ticket" to ticket, "url" to target,
            "target" to rec.target, "subscribed" to call.subscribe)).toString())
        if (call.subscribe && !call.cancelled) watch(rec, call)
        Thread({ fetch(rec) }, "lunacy-download-$ticket").apply { isDaemon = true }.start()
    }

    /** Resolves a webOS path under /media/internal; anything else is refused, as the tree is. */
    private fun resolve(path: String): File? {
        val prefix = "/media/internal/"
        if (!path.startsWith(prefix)) return null
        return UserFiles.resolve(webosRoot, path.removePrefix(prefix))
    }

    private fun watch(rec: Record, call: Bus.Call) {
        rec.watchers += call
        call.onCancel { rec.watchers.remove(call) }
        if (rec.finished) call.reply(completion(rec))
    }

    private fun fetch(rec: Record) {
        val temp = File(rec.file.parentFile, TEMP_PREFIX + rec.file.name)
        try {
            rec.file.parentFile?.mkdirs()
            val c = Http.connect(Http.Request("GET", rec.url))
            rec.httpStatus = c.responseCode
            rec.mimetype = (c.contentType ?: "").substringBefore(';').trim()
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val total = c.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L
            rec.amountTotal = total
            c.inputStream.use { input ->
                temp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var lastReport = 0L
                    while (true) {
                        if (rec.aborted) throw IOException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        rec.amountReceived += n
                        // The device reports as it goes rather than per byte; a quarter of a
                        // second is often enough for a progress bar and cheap for a small file.
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 250) { lastReport = now; report(rec) }
                    }
                }
            }
            // A total the server didn't declare is what actually arrived, as the device's
            // completion reported amountTotal equal to amountReceived.
            if (rec.amountTotal == 0L) rec.amountTotal = rec.amountReceived
            if (rec.file.exists()) rec.file.delete()
            if (!temp.renameTo(rec.file)) throw IOException("could not put ${rec.file} in place")
            rec.completed = true
        } catch (e: Exception) {
            temp.delete()
            rec.interrupted = !rec.aborted
            if (rec.httpStatus == 0) rec.httpStatus = 0
            Log.w(AppServer.TAG, "download ${rec.ticket} ${rec.url}: $e")
        }
        rec.finished = true
        val done = completion(rec)
        rec.watchers.toList().forEach { it.reply(done) }
    }

    private fun report(rec: Record) {
        val msg = JSONObject(mapOf(
            "ticket" to rec.ticket,
            "amountReceived" to rec.amountReceived, "e_amountReceived" to rec.amountReceived.toString(),
            "amountTotal" to rec.amountTotal, "e_amountTotal" to rec.amountTotal.toString(),
        )).toString()
        rec.watchers.toList().forEach { it.reply(msg) }
    }

    /** The record the device sends when a download ends, member for member. */
    private fun completion(rec: Record): String = JSONObject(mapOf(
        "ticket" to rec.ticket,
        "url" to rec.url,
        "sourceUrl" to rec.url,
        "deviceId" to "",
        "authToken" to "",
        "destTempPrefix" to TEMP_PREFIX,
        "destFile" to rec.destFile,
        "destPath" to rec.destPath,
        "mimetype" to rec.mimetype,
        "amountReceived" to rec.amountReceived, "e_amountReceived" to rec.amountReceived.toString(),
        "amountTotal" to rec.amountTotal, "e_amountTotal" to rec.amountTotal.toString(),
        "initialOffset" to 0, "e_initialOffsetBytes" to "0",
        "e_rangeLow" to "0", "e_rangeHigh" to "0",
        "canHandlePause" to false,
        "cookieHeader" to "",
        "completionStatusCode" to if (rec.aborted) CANCELLED_CODE else rec.httpStatus,
        "httpStatus" to rec.httpStatus,
        "interrupted" to rec.interrupted,
        "completed" to rec.completed,
        "aborted" to rec.aborted,
        "target" to rec.target,
    )).toString()

    private fun statusQuery(call: Bus.Call) {
        val ticket = call.params.optInt("ticket", -1)
        val rec = records[ticket]
        if (rec == null) {
            return call.reply(JSONObject(mapOf("ticket" to ticket, "returnValue" to false,
                "errorCode" to "ticket_not_found", "subscribed" to false)).toString())
        }
        if (rec.finished) call.reply(completion(rec)) else report(rec)
        if (call.subscribe && !call.cancelled && !rec.finished) watch(rec, call)
    }

    private fun cancel(p: JSONObject): String {
        val ticket = p.optInt("ticket", -1)
        records[ticket]?.let { it.aborted = true }
        // True even for a ticket that was never issued: the reference device answers the same.
        return JSONObject(mapOf("ticket" to ticket, "returnValue" to true, "subscribed" to false,
            "aborted" to true, "completed" to false, "completionStatusCode" to CANCELLED_CODE)).toString()
    }

    private fun deleteFile(p: JSONObject): String {
        val ticket = p.optInt("ticket", -1)
        val rec = records[ticket]
            ?: return JSONObject(mapOf("ticket" to ticket, "returnValue" to false,
                "errorCode" to "requested download record not found")).toString()
        rec.file.delete()
        records.remove(ticket)
        return JSONObject(mapOf("ticket" to ticket, "returnValue" to true)).toString()
    }
}
