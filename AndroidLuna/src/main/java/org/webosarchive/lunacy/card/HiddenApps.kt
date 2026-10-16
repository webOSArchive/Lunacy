package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONArray

/**
 * The apps the user has hidden from the launcher, with Software Manager. A hidden app is
 * still installed and still runs: another app can open it, and the bus lists it as before.
 * Only the places the user starts apps from leave it out: the launcher's pages, the dock and
 * Just Type. Android's apps can be hidden too, which is the point for the ones a device ships
 * that can't be removed.
 *
 * webOS had no such setting: an app was in the launcher unless its own appinfo said
 * `"visible": false`. This is Lunacy's, kept in the launcher's prefs beside the tabs and dock.
 */
class HiddenApps(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private var ids: Set<String> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).map { a.optString(it) }.toSet()
    }.getOrDefault(emptySet())

    /** Called on the main thread after the set changes. */
    var onChanged: () -> Unit = {}

    operator fun contains(id: String) = id in ids

    fun all(): Set<String> = ids

    fun set(id: String, hidden: Boolean) {
        val next = if (hidden) ids + id else ids - id
        if (next == ids) return
        ids = next
        prefs.edit().putString(KEY, JSONArray(next.sorted()).toString()).apply()
        onChanged()
    }

    private companion object { const val KEY = "hidden" }
}
