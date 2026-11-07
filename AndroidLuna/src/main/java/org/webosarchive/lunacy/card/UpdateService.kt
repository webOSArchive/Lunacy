package org.webosarchive.lunacy.card

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * webOS's update daemon, palm://com.palm.update, for Lunacy's own releases on GitHub.
 *
 * On webOS, UpdateDaemon checked Palm's servers weekly (the persistent activity
 * weeklyUpdateCheck, interval 7d, calling TaskCheck; on the reference TouchPad) and whenever
 * System Updates opened (CheckForUpdate), and told both System Updates and SystemUI through
 * its status: Checking, then UpToDate, or Waiting with the update's version and size, then
 * Downloading and Available as it fetched and readied the update. SystemUI put up a dashboard,
 * "Update available", with the version under it, that opened System Updates.
 *
 * Here the canonical source is GitHub: the newest release of webOSArchive/Lunacy that isn't a
 * draft (every release so far is marked pre-release, so GitHub's "latest" finds none), its
 * tag a version like Lunacy's own versionName. Newer than this build, it is Waiting, with the
 * size of the release's APK that matches this build (arm32 or arm64, release or debug), and
 * the app's button (DownloadNow, labelled Learn More) opens the release page in Android's browser: Lunacy doesn't install itself
 * (codepoet, 2026-11-07). The check runs daily in the background (codepoet: daily, where webOS
 * was weekly), and a version found there is announced once, with a banner and the dashboard
 * (Lunacy's System Alerts app). Nothing else of the daemon's exists here, so its other
 * methods answer that.
 */
class UpdateService(
    private val context: Context,
    private val bus: Bus,
    /** This build's version name ("0.9.3"), and its APK on a release page: AndroidLuna-arm32-release.apk. */
    private val versionName: String,
    private val apkName: String,
    /** Announces a version found in the background (the banner and dashboard); false if it can't. */
    private val announce: (version: String) -> Boolean,
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "update check") }
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** What GetStatus and GetStatusApp answer, and send to their subscribers. */
    private var status = JSONObject().put("status", "UpToDate").put("networkAvailable", true)
    private val watchers = ArrayList<Bus.Call>()
    private var checking = false
    private val checkReplies = ArrayList<Bus.Call>()
    /** The release found, while it is newer than this build. */
    private var releasePage: String? = null

    fun register() {
        bus.register(SERVICE, "GetStatus", Bus.CallHandler(::getStatus))
        bus.register(SERVICE, "GetStatusApp", Bus.CallHandler(::getStatus))
        // Answered when the check ends, with its status: System Updates takes its state from this reply.
        bus.register(SERVICE, "CheckForUpdate", Bus.CallHandler { c -> checkReplies += c; check(background = false) })
        bus.register(SERVICE, "TaskCheck", Bus.CallHandler(::taskCheck))
        bus.register(SERVICE, "DownloadNow") { _, _, reply -> reply(downloadNow()) }
        // Dismissing or putting off an update the person downloads themselves: nothing to keep.
        bus.register(SERVICE, "DismissedUpdate") { _, _, reply -> reply(Bus.ok()) }
        bus.register(SERVICE, "InstallLater") { _, _, reply -> reply(Bus.ok()) }
        bus.register(SERVICE, "AlertDisplayed") { _, _, reply -> reply(Bus.ok()) }
        for (m in listOf("InstallNow", "CancelDownload")) bus.register(SERVICE, m) { _, _, reply ->
            reply(Bus.error("Lunacy doesn't download or install its updates: Download Now opens the release page", -1))
        }
        scheduleDaily()
        // A daily check missed while Lunacy wasn't running is made up shortly after it starts.
        if (System.currentTimeMillis() - prefs.getLong(LAST_CHECK, 0) > DAY_MS) main.postDelayed({ check(background = true) }, STARTUP_DELAY_MS)
    }

    fun close() {
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
    }

    private fun getStatus(call: Bus.Call) {
        call.reply(JSONObject(status.toString()).put("returnValue", true).toString())
        if (call.subscribe) { watchers += call; call.onCancel { main.post { watchers.remove(call) } } }
    }

    private fun setStatus(s: JSONObject) {
        status = s
        val out = JSONObject(s.toString()).put("returnValue", true).toString()
        watchers.toList().forEach { it.reply(out) }
    }

    /** The check has ended: whoever asked for it hears its status. */
    private fun answerChecks() {
        val out = JSONObject(status.toString()).put("returnValue", true).toString()
        checkReplies.toList().also { checkReplies.clear() }.forEach { it.reply(out) }
    }

    /** The daily activity, as webOS's weeklyUpdateCheck: created once, kept by the activity manager across restarts. */
    private fun scheduleDaily() {
        val activity = JSONObject()
            .put("name", "dailyUpdateCheck")
            .put("description", "Daily Update Check")
            .put("type", JSONObject().put("foreground", true).put("persist", true))
            .put("schedule", JSONObject().put("interval", "1d"))
            .put("requirements", JSONObject().put("internet", true))
            .put("callback", JSONObject().put("method", "palm://$SERVICE/TaskCheck"))
        bus.call(SERVICE, "palm://com.palm.activitymanager/create", JSONObject().put("activity", activity).put("start", true).toString(), privateBus = true) {}
    }

    private fun taskCheck(call: Bus.Call) {
        call.reply(Bus.ok())
        check(background = true)
        // An interval activity runs again once its callback completes it, with restart.
        val id = call.params.optJSONObject("\$activity")?.optInt("activityId", -1) ?: -1
        if (id >= 0) bus.call(SERVICE, "palm://com.palm.activitymanager/complete",
            JSONObject().put("activityId", id).put("restart", true).toString(), privateBus = true) {}
    }

    private fun check(background: Boolean) {
        if (checking) { if (!background) setStatus(JSONObject().put("status", "Checking").put("networkAvailable", true)); return }
        checking = true
        if (!background) setStatus(JSONObject().put("status", "Checking").put("networkAvailable", true))
        worker.execute {
            val found = runCatching { newest() }.onFailure { Log.w(AppServer.TAG, "update check: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()
            main.post {
                checking = false
                if (found == null) {
                    // The UI says "Unable to connect" only after Checking; a quiet check changes nothing.
                    if (!background || checkReplies.isNotEmpty()) setStatus(JSONObject().put("status", "NetworkFailed").put("networkAvailable", true))
                    answerChecks()
                    return@post
                }
                prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
                val (tag, release) = found
                if (newer(tag, versionName)) {
                    releasePage = release.optString("html_url")
                    val asset = release.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                        ?.firstOrNull { it.optString("name") == apkName }
                    val mb = asset?.optLong("size")?.let { Math.round(it / 1_048_576.0) } ?: 0L
                    setStatus(JSONObject().put("status", "Waiting").put("version", "Lunacy $tag").put("size", mb)
                        .put("networkAvailable", true).put("lowSpeed", false).put("releasePage", releasePage)
                        .put("releaseNotes", plainNotes(release.optString("body"))))
                    if (background && prefs.getString(ANNOUNCED, null) != tag && announce("Lunacy $tag")) {
                        prefs.edit().putString(ANNOUNCED, tag).apply()
                    }
                } else {
                    releasePage = null
                    setStatus(JSONObject().put("status", "UpToDate").put("networkAvailable", true))
                }
                answerChecks()
            }
        }
    }

    /** The newest release that isn't a draft: its tag and the release. On the worker. */
    private fun newest(): Pair<String, JSONObject> {
        val c = Http.connect(Http.Request("GET", RELEASES, listOf("Accept" to "application/vnd.github+json", "User-Agent" to "Lunacy/$versionName"),
            connectTimeoutMs = 15_000, readTimeoutMs = 30_000))
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val list = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            for (i in 0 until list.length()) {
                val r = list.getJSONObject(i)
                if (!r.optBoolean("draft")) return r.optString("tag_name") to r
            }
            throw IllegalStateException("no releases")
        } finally { c.disconnect() }
    }

    private fun downloadNow(): String {
        val page = releasePage ?: return Bus.error("No update is waiting", -1)
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); Bus.ok()
        }.getOrElse { Bus.error("Couldn't open the release page: ${it.message}", -1) }
    }

    companion object {
        const val SERVICE = "com.palm.update"
        private const val RELEASES = "https://api.github.com/repos/webOSArchive/Lunacy/releases?per_page=10"
        private const val LAST_CHECK = "lastCheck"
        private const val ANNOUNCED = "announced"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val STARTUP_DELAY_MS = 2 * 60 * 1000L

        /** Whether [tag] ("0.9.4", or "v0.9.4") is a later version than [current], number by number. */
        fun newer(tag: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").split('.', '-', ' ').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val a = parts(tag); val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** A release's Markdown as plain lines for the release notes: headings and emphasis marks dropped, list items bulleted. */
        fun plainNotes(md: String): String = md.replace("\r", "").lines().joinToString("\n") { line ->
            val t = line.trimEnd()
            when {
                t.startsWith("#") -> t.trimStart('#').trim()
                t.trimStart().startsWith("- ") || t.trimStart().startsWith("* ") -> "• " + t.trimStart().substring(2)
                else -> t
            }.replace("**", "").replace("`", "")
        }.trim()
    }
}
