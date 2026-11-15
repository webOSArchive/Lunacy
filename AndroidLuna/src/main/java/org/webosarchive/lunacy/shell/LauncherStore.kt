package org.webosarchive.lunacy.shell

import org.webosarchive.lunacy.card.androidComponent
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo

/**
 * Where the launcher's pages come from and go to: the bundled page map
 * (assets/luna/launcher-pages.json) for a fresh launcher, and the user's own tabs, kept in the
 * "launcher" preferences.
 */
class LauncherStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** The bundled pages, layout and placement rule, shared with Luna DE (LunaCore's LauncherPages). */
    private val bundled = org.webosarchive.lunacy.card.LauncherPages(org.webosarchive.lunacy.card.AndroidAssets(context.assets))

    /** The pages a launcher starts with, before the user's own tabs. */
    fun initialPages(): MutableList<Launcher.Page> =
        bundled.initialPages.map { (designator, name) -> Launcher.Page(designator, name) }.toMutableList()

    /** Where a bundled app starts: page designator to app ids ([org.webosarchive.lunacy.card.LauncherPages.defaultLayout]). */
    val defaultLayout: List<Pair<String, List<String>>> get() = bundled.defaultLayout

    /**
     * The page an app with no place of its own belongs to: LunaCE's rule
     * ([org.webosarchive.lunacy.card.LauncherPages.designatorFor]), after Lunacy's own for
     * Android's apps and added launch points. Anything unrecognised goes to the first page.
     */
    fun pageFor(app: AppInfo, pages: List<Launcher.Page>): Int {
        // Proof of concept: Android's apps start on the favorites page, named "android".
        if (app.androidComponent != null) return pages.indexOfFirst { it.designator == "favorites" }.takeIf { it >= 0 } ?: 0
        // LauncherObject::slotAppAuxiliaryIconAdd put a launch point an app adds on Favorites,
        // which is Android's tab in Lunacy; codepoet's call: it goes on the first page.
        if (app.launchPoint != null) return 0
        val designator = bundled.designatorFor(app) ?: return 0
        return pages.indexOfFirst { it.designator == designator }.takeIf { it >= 0 } ?: 0
    }

    /** The user's tabs as last saved; null before tabs could change. */
    fun savedTabs(): JSONArray? = runCatching { JSONArray(prefs.getString("tabs", null) ?: "") }.getOrNull()

    /** Before tabs could change: each page's app ids by index. */
    fun legacyPages(): JSONArray =
        runCatching { JSONArray(prefs.getString("pages", null) ?: "[" + (prefs.getString("order", null) ?: "[]") + "]") }.getOrDefault(JSONArray())

    /**
     * Each page's designator, name and apps, in order: the tabs themselves are the user's now.
     * A hidden app isn't on the page, so it is put back where the last save had it, in its tab
     * or its group, to come back there when it is shown again.
     */
    fun save(pages: List<Launcher.Page>, hidden: Set<String>) {
        val old = savedTabs()
        val oldTabs = (0 until (old?.length() ?: 0)).mapNotNull { old!!.optJSONObject(it) }.associateBy { it.optString("designator") }
        fun ids(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.opt(it) }
        prefs.edit().putString("tabs", JSONArray(pages.map { p ->
            val entries: MutableList<Any> = p.tiles.map { t ->
                when (t) {
                    is Launcher.Tile.App -> t.app.id
                    is Launcher.Tile.Group -> {
                        val members = t.members.map { it.id }.toMutableList()
                        val was = ids(oldTabs[p.designator]?.optJSONArray("apps")).filterIsInstance<JSONObject>().firstOrNull { it.optString("uid") == t.id }
                        ids(was?.optJSONArray("apps")).forEachIndexed { i, id -> if (id is String && id in hidden && id !in members) members.add(minOf(i, members.size), id) }
                        JSONObject().put("group", t.name).put("uid", t.id).put("apps", JSONArray(members))
                    }
                }
            }.toMutableList()
            ids(oldTabs[p.designator]?.optJSONArray("apps")).forEachIndexed { i, id ->
                if (id is String && id in hidden && id !in entries) entries.add(minOf(i, entries.size), id)
            }
            JSONObject().put("designator", p.designator).put("name", p.title).put("apps", JSONArray(entries))
        }).toString()).apply()
    }
}
