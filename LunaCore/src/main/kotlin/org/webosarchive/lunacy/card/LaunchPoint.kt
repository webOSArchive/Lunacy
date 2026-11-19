package org.webosarchive.lunacy.card

import org.json.JSONObject

/**
 * A dynamic launch point: a second launcher icon for an app, which launches it with params of
 * its own. The Web app's Add to Launcher makes one per page, and webOS Archive's PWA Installer
 * one per site. LunaCE's `LaunchPoint`, for the launch points `addLaunchPoint` makes (an app's
 * default launch point is the app itself, [AppInfo]).
 */
class LaunchPoint(
    /** The app it launches. */
    val appId: String,
    /** LunaCE's id for it: a random number from 1 to 1000000, and the name of its file. */
    val launchPointId: String,
    val title: String,
    val appmenu: String,
    /** A webOS path, absolute, with any `file://` taken off (getAbsolutePath). */
    val icon: String,
    /** The params as the caller gave them, as JSON text; null if they weren't JSON. */
    val params: String?,
    val removable: Boolean,
) {
    /** The launch's params, if they are an object - what a web app is launched with. */
    fun paramsObject(): JSONObject? = params?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** LunaCE's LaunchPoint::toJSON, as written to the file and posted to launchPointChanges. */
    fun toJson(app: AppInfo?): JSONObject {
        val j = JSONObject().put("id", appId)
        if (app != null) {
            val e = app.listEntry()
            j.put("version", app.version).put("appId", app.id).put("vendor", e.optString("vendor"))
                .put("vendorUrl", e.optString("vendorUrl"))
        }
        j.put("size", 0).put("packageId", appId).put("removable", removable)
            .put("launchPointId", launchPointId).put("title", title).put("appmenu", appmenu).put("icon", icon)
        params?.let { p -> runCatching { org.json.JSONTokener(p).nextValue() }.getOrNull()?.let { j.put("params", it) } }
        return j
    }
}
