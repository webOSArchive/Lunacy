package org.webosarchive.lunacy.shell

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

    /**
     * The pages, their names and the keyword map, from assets/luna/launcher-pages.json (which
     * says where each value comes from). LunaCE's four built-in pages, named as the reference
     * TouchPad names them: APPS, DOWNLOADS, GAMES, SETTINGS.
     */
    private val pageMap = runCatching {
        JSONObject(context.assets.open("luna/launcher-pages.json").bufferedReader().use { it.readText() })
    }.getOrDefault(JSONObject())

    /** The pages a launcher starts with, before the user's own tabs. */
    fun initialPages(): MutableList<Launcher.Page> = pageMap.optJSONArray("pages")?.let { a ->
        (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            Launcher.Page(p.optString("designator"), p.optString("name", p.optString("designator")))
        }.toMutableList()
    }?.takeIf { it.isNotEmpty() } ?: mutableListOf(
        Launcher.Page("apps", "apps"), Launcher.Page("downloads", "downloads"), Launcher.Page("favorites", "android"), Launcher.Page("prefs", "settings"))

    /**
     * Where a bundled app starts, from `layout` in that file: webOS shipped the same thing as
     * /etc/palm/default-launcher-page-layout.json, which is what put Palm's settings apps on
     * the prefs page. Page designator to app ids, in the order they should appear.
     */
    val defaultLayout: List<Pair<String, List<String>>> =
        pageMap.optJSONArray("layout")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val items = o.optJSONArray("items")
                o.optString("title") to (0 until (items?.length() ?: 0))
                    .map { j -> items!!.optString(j).removeSuffix("_default") }
            }
        }.orEmpty()

    /** keyword (lower case) to page designator. */
    private val keywordPages: Map<String, String> = pageMap.optJSONObject("keywords")?.let { o ->
        o.keys().asSequence().associate { it.lowercase() to o.optString(it) }
    }.orEmpty()

    /**
     * The page an app with no place of its own belongs to, as LunaCE's
     * AppMonitor::pageDesignatorForWebOSApp works it out: its category first, then each of its
     * keywords. Anything unrecognised goes to the first page.
     */
    fun pageFor(app: AppInfo, pages: List<Launcher.Page>): Int {
        // Proof of concept: Android's apps start on the favorites page, named "android".
        if (app.androidComponent != null) return pages.indexOfFirst { it.designator == "favorites" }.takeIf { it >= 0 } ?: 0
        // LauncherObject::slotAppAuxiliaryIconAdd put a launch point an app adds on Favorites,
        // which is Android's tab in Lunacy; codepoet's call: it goes on the first page.
        if (app.launchPoint != null) return 0
        val designator = keywordPages[app.category.lowercase()]
            ?: app.keywords.firstNotNullOfOrNull { keywordPages[it.lowercase()] }
            // LauncherObject::pageIndexForAppByPredefinedDesignators: an app the user installed
            // goes on the downloads page (installedAppsPageIndex), as every installed app does
            // on the reference TouchPad; the rest on the first.
            ?: if (app.userInstalled) "downloads" else return 0
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
