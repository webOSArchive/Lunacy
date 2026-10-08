package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exhibition mode's launch points: which apps offer a face for it, and which the user has
 * turned on. Palm's Exhibition app (com.palm.app.exhibitionpreferences) lists them and
 * switches them; the Time face is the shell's own and isn't in the list, which is why that
 * app adds it itself and won't let you switch it off.
 *
 * An app declares itself either way webOS accepted, both seen on the reference TouchPad:
 * `"dockMode": true` (the older form, e.g. Agenda) or `"exhibitionMode": true` with an
 * optional `"exhibitionModeOptions": {"title": ...}` (the newer one, e.g. Photos).
 *
 * The reply shape is the device's, measured with luna-send.
 */
class DockMode(context: Context, private val registry: AppRegistry) {
    private val prefs = context.getSharedPreferences("dockmode", Context.MODE_PRIVATE)
    private val subscribers = LinkedHashSet<Bus.Call>()

    fun register(bus: Bus) {
        bus.register(SERVICE, "listDockModeLaunchPoints", Bus.CallHandler { call ->
            call.reply(launchPoints())
            if (call.subscribe && !call.cancelled) {
                subscribers += call
                call.onCancel { subscribers.remove(call) }
            }
        })
        bus.register(SERVICE, "addDockModeLaunchPoint") { _, p, reply -> reply(setEnabled(p, true)) }
        bus.register(SERVICE, "removeDockModeLaunchPoint") { _, p, reply -> reply(setEnabled(p, false)) }
    }

    /** Apps that offer an Exhibition face, in the shape the TouchPad's applicationManager gives. */
    private fun launchPoints(): String {
        val points = JSONArray()
        for (app in registry.apps) {
            val info = app.appinfo
            val declared = info.optBoolean("dockMode", false) || info.optBoolean("exhibitionMode", false)
            if (!declared) continue
            val title = info.optJSONObject("exhibitionModeOptions")?.optString("title").orEmpty()
                .ifEmpty { info.optString("dockModeTitle") }
                .ifEmpty { app.title }
            points.put(JSONObject()
                .put("id", app.id).put("version", app.version).put("appId", app.id)
                .put("vendor", info.optString("vendor", "")).put("vendorUrl", info.optString("vendorurl", ""))
                .put("size", 0).put("packageId", app.id)
                .put("exhibitionMode", true).put("exhibitionModeTitle", title)
                .put("removable", app.userInstalled)
                .put("launchPointId", app.id + "_default")
                .put("title", app.title).put("appmenu", info.optString("appmenu", app.title))
                .put("icon", "/media/cryptofs/apps/${Packages.APPS}/${app.dir}/${app.icon}")
                .put("enabled", isEnabled(app.id)))
        }
        return JSONObject().put("returnValue", true).put("launchPoints", points).toString()
    }

    /**
     * The set of launch points has changed - an app was installed or removed - which is the
     * one thing a device does push to everyone watching.
     */
    fun launchPointsChanged() {
        val list = launchPoints()
        subscribers.toList().forEach { it.reply(list) }
    }

    fun isEnabled(appId: String): Boolean = prefs.getBoolean(appId, false)

    /** What the status bar calls this app while it is exhibiting. */
    fun title(appId: String): String {
        val app = registry.get(appId) ?: return "Time"
        return app.appinfo.optJSONObject("exhibitionModeOptions")?.optString("title").orEmpty()
            .ifEmpty { app.appinfo.optString("dockModeTitle") }
            .ifEmpty { app.title }
    }

    /**
     * The face Exhibition opens on: the one that was showing when it was last left, if that is
     * still an enabled launch point, and otherwise the shell's own Time (null).
     *
     * That is LunaSysMgr's rule (DockModeWindowManager::setDockModeState): the clock is entry 0
     * and the default until the mode is first left, leaving it records the active app as the
     * default, and disabling the default app puts it back to the clock. Lunacy keeps it across
     * restarts of its process, which on Android come far more often than LunaSysMgr's did.
     */
    fun defaultApp(): String? =
        prefs.getString(DEFAULT, null)?.takeIf { registry.get(it) != null && isEnabled(it) }

    /** Exhibition was left showing [appId] (null for Time): it opens there next time. */
    fun setDefault(appId: String?) {
        prefs.edit().apply { if (appId == null) remove(DEFAULT) else putString(DEFAULT, appId) }.apply()
    }

    private fun setEnabled(p: JSONObject, enabled: Boolean): String {
        val appId = p.optString("appId")
        if (appId.isEmpty()) return Bus.error("appId is required")
        if (registry.get(appId) == null) return Bus.error("Application not found: $appId")
        prefs.edit().putBoolean(appId, enabled).apply()
        // No subscription update: turning a launch point on or off doesn't change the list of
        // them, and the TouchPad doesn't send one. Measured with luna-send against the
        // reference device - subscribe, then add and remove, and exactly one reply arrives.
        // Palm's Exhibition app depends on that: its handler means to clear its list and
        // instead writes the old contents straight back (`this.dockApps.splice(0, len)`
        // returns what it removed), so a second reply draws every app twice.
        return Bus.ok()
    }

    companion object {
        const val SERVICE = "com.palm.applicationManager"
        /** The default face's key, beside the per-app switches; no app id starts with a dot. */
        private const val DEFAULT = ".default"
    }
}
