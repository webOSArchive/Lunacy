package org.webosarchive.lunacy.card

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * palm://com.palm.appInstallService: HP's installer for App Catalog, which hands it a package's
 * URL and follows the install through the `status` subscription - the catalog's own progress
 * pill, "Downloading", "Installing", "Open". On a TouchPad it lives in LunaDownloadMgr; here
 * it is Lunacy's package manager, through the shell ([Installer]), so the launcher shows the
 * install as it does any other.
 *
 * Measured on the reference TouchPad with `Workbench/probe/appinstallservice-probe.sh`, called
 * as com.palm.app.enyo-findapps:
 * - private bus only: a public caller is told the service does not exist;
 * - `install` needs `id`, `version`, `ipkUrl` and `authToken`, and every string it is given must
 *   be non-empty, or `{"returnValue":false,"errorCode":-1,"reason":"Bad parameter",
 *   "subscribed":false}`; otherwise `{"returnValue":true,"subscribed":false}`;
 * - `status` answers `{"status":{"apps":[{id, details}]},"returnValue":true}` (with
 *   `"subscribed":true` for a subscription), then `{id, details}` as the install moves: the
 *   install's own parameters plus `client` (the caller), `icon`, `progress` and `state` -
 *   "icon download current", "icon download complete", "ipk download current" (progress 32,
 *   65, 100 for a 320 KB package), "ipk download complete", "installing", then "installed" or
 *   "install failed" with a `reason`; a download that fails is "download failed" with
 *   `errorCode` -5 and `reason` "Http error". A failed install stays in the list until it is
 *   cancelled.
 * - `cancel` of an unknown id: `{"returnValue":false,"errorCode":-1,"reason":"unknown app id",
 *   "subscribed":false}`; `remove` answers `{"returnValue":true,"subscribed":false}` even then;
 *   `pause` of an unknown id `{"returnValue":false,"errorCode":0,"reason":""}`.
 *
 * On the TouchPad every install now ends "install failed", reason FAILED_VERIFY: the service
 * checks signatures against HP's servers, gone since 2015. Lunacy installs packages as
 * Preware did, unsigned, so here they end "installed".
 */
class AppInstallService(private val installer: Installer, private val icons: (String) -> Boolean = ::fetchable) {
    /** What the shell does for an install: the same path as Preware's installs. Main thread. */
    interface Installer {
        /** progress is the package manager's 0..100: the download is 0..90, the unpacking the rest. */
        fun install(url: String, requester: String, progress: (Int) -> Unit, done: (Packages.Result) -> Unit)
        fun remove(appId: String, done: (String?) -> Unit)
        fun isInstalled(appId: String): Boolean
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    /** Installs the service knows about, by app id, with their details. Main thread. */
    private val apps = LinkedHashMap<String, JSONObject>()
    private val active = HashSet<String>()
    private val watchers = ArrayList<Bus.Call>()

    fun register(bus: Bus) {
        for (m in listOf("install", "installLocal", "status", "cancel", "remove", "pause", "resume")) {
            bus.register(SERVICE, m, Bus.CallHandler { c ->
                if (!c.privateBus) c.reply(Bus.error("Service does not exist: $SERVICE."))
                else when (m) {
                    "install" -> install(c, local = false)
                    "installLocal" -> install(c, local = true)
                    "status" -> status(c)
                    "cancel" -> cancel(c)
                    "remove" -> remove(c)
                    else -> pauseOrResume(c, m)
                }
            })
        }
    }

    private fun answer(ok: Boolean, reason: String? = null, code: Int = -1): String {
        val j = JSONObject().put("returnValue", ok)
        if (!ok) j.put("errorCode", code).put("reason", reason)
        return j.put("subscribed", false).toString()
    }

    private fun install(c: Bus.Call, local: Boolean) {
        val p = c.params
        val required = if (local) listOf("id", "version", "ipkUrl") else listOf("id", "version", "ipkUrl", "authToken")
        val empty = p.keys().asSequence().any { k -> (p.opt(k) as? String)?.isEmpty() == true }
        if (required.any { p.optString(it).isEmpty() } || empty) return c.reply(answer(false, "Bad parameter"))
        val id = p.getString("id")
        // "duplicate install command while current install has not completed, ignoring"
        if (id in active) return c.reply(answer(true))
        val d = JSONObject(p.toString()).apply { remove("subscribe") }
            .put("client", c.appId).put("icon", p.optString("iconUrl")).put("progress", 0)
        apps[id] = d
        active += id
        c.reply(answer(true))
        val iconUrl = p.optString("iconUrl")
        if (local || iconUrl.isEmpty()) return fetchPackage(id, d)
        post(id, d, "icon download current", 0)
        worker.execute {
            val ok = icons(iconUrl)
            main.post {
                if (!ok) { active -= id; post(id, d.put("errorCode", -5).put("reason", "Http error"), "download failed", 0) }
                else { post(id, d, "icon download complete", 100); fetchPackage(id, d) }
            }
        }
    }

    private fun fetchPackage(id: String, d: JSONObject) {
        var downloaded = false
        var last = -1
        installer.install(d.getString("ipkUrl"), d.optString("client"), progress = { raw ->
            if (downloaded) return@install
            val pct = minOf(100, raw * 100 / 90)
            if (pct != last && (pct - last >= 5 || pct == 100)) { last = pct; post(id, d, "ipk download current", pct) }
            if (raw >= 90) {
                downloaded = true
                post(id, d, "ipk download complete", 100)
                post(id, d, "installing", 0)
            }
        }) { r ->
            active -= id
            when {
                r.error == null -> {
                    post(id, d, "installed", 100)
                    apps.remove(id)
                }
                !downloaded -> post(id, d.put("errorCode", -5).put("reason", "Http error"), "download failed", 0)
                else -> post(id, d.put("reason", "FAILED_IPKG_INSTALL").put("lunacyError", r.error), "install failed", 0)
            }
        }
    }

    private fun post(id: String, d: JSONObject, state: String, progress: Int) {
        d.put("state", state).put("progress", progress)
        val update = JSONObject().put("id", id).put("details", d).toString()
        watchers.toList().forEach { it.reply(update) }
    }

    private fun status(c: Bus.Call) {
        val list = JSONArray()
        for ((id, d) in apps) list.put(JSONObject().put("id", id).put("details", d))
        val j = JSONObject().put("status", JSONObject().put("apps", list)).put("returnValue", true)
        if (c.subscribe) j.put("subscribed", true)
        c.reply(j.toString())
        if (c.subscribe && !c.cancelled) { watchers += c; c.onCancel { watchers.remove(c) } }
    }

    private fun cancel(c: Bus.Call) {
        val id = c.params.optString("id")
        if (id.isEmpty()) return c.reply(answer(false, "No id specified"))
        val d = apps[id] ?: return c.reply(answer(false, "unknown app id"))
        // A download already handed to the package manager runs to its end.
        if (id in active) return c.reply(answer(false, "Cannot cancel in current state"))
        apps.remove(id)
        post(id, d, "canceled", 0)
        c.reply(answer(true))
    }

    private fun remove(c: Bus.Call) {
        val id = c.params.optString("id")
        if (id.isEmpty()) return c.reply(answer(false, "No id specified"))
        c.reply(answer(true))
        if (!installer.isInstalled(id)) return
        val d = apps[id] ?: JSONObject().put("id", id)
        post(id, d, "removing", 0)
        installer.remove(id) { error ->
            if (error == null) { apps.remove(id); post(id, d, "removed", 0) }
            else post(id, d.put("reason", error), "remove failed", 0)
        }
    }

    /** Lunacy's downloads can't be paused. An id the service doesn't know gets the device's own reply. */
    private fun pauseOrResume(c: Bus.Call, m: String) {
        val id = c.params.optString("id")
        if (id.isEmpty()) return c.reply(answer(false, "No id specified"))
        if (id !in apps) return c.reply(JSONObject().put("returnValue", false).put("errorCode", 0).put("reason", "").toString())
        c.reply(answer(false, if (m == "pause") "Pause failed" else "Resume failed"))
    }

    /**
     * palm://com.palm.appinstaller/queryInstallCapacity, App Catalog's check for room before an
     * install. Measured on the reference TouchPad (private bus): `{appId, size,
     * uncompressedSize}` in KB answers `{"returnValue":true,"result":0,"spaceNeededInKB":"…"}`,
     * `result` 3 when there isn't room, and `{"returnValue":false,"errorCode":"appinstaller_error",
     * "errorText":"missing size parameter"}` without a size. The space needed was the size plus
     * the uncompressed size (twice the size when none was given) plus a few KB for the
     * filesystem; Lunacy measures it against the free space where it keeps packages.
     */
    class AppInstaller(private val freeBytes: () -> Long) {
        fun register(bus: Bus) {
            bus.register(INSTALLER, "queryInstallCapacity", Bus.CallHandler { c ->
                if (!c.privateBus) return@CallHandler c.reply(Bus.error("Service does not exist: $INSTALLER."))
                val p = c.params
                if (!p.has("size")) return@CallHandler c.reply(JSONObject().put("returnValue", false)
                    .put("errorCode", "appinstaller_error").put("errorText", "missing size parameter").toString())
                val size = p.optLong("size")
                val unpacked = if (p.has("uncompressedSize")) p.optLong("uncompressedSize") else 2 * size
                val needed = size + unpacked + 36
                val fits = needed * 1024 < freeBytes()
                c.reply(JSONObject().put("returnValue", true).put("result", if (fits) 0 else 3)
                    .put("spaceNeededInKB", needed.toString()).toString())
            })
        }
    }

    companion object {
        const val SERVICE = "com.palm.appInstallService"
        const val INSTALLER = "com.palm.appinstaller"

        /** Whether an icon URL answers, as the device's own icon download found out. */
        fun fetchable(url: String): Boolean = try {
            val c = Http.connect(Http.Request("GET", url, readTimeoutMs = 20_000))
            try { c.responseCode in 200..299 && c.inputStream.use { it.readBytes(); true } } finally { c.disconnect() }
        } catch (e: Exception) { false }
    }
}
