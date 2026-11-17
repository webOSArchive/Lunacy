package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.core.KeyedStore
import java.util.UUID

/**
 * The launcher as its owner has arranged it, as LunaCE's launcher keeps it: the tabs (the
 * built-in ones, renamed or not, and the ones they added), each tab's icons in their order, the
 * groups they made of them, and the quick-launch bar's apps. Kept in [store] as JSON text: `tabs`,
 * each a designator, a name and its cells, a cell an app's id or a group, {"group": name, "uid":
 * id, "apps": [ids]}; and `dock`. AndroidLuna keeps it in its "launcher" preferences, as it always
 * has (and reads a launcher saved before tabs could change, `pages` or `order`); Luna DE in a
 * file. Both launchers are drawn from this; what each platform has of its own (Android's apps and
 * tab, the device's own phone and camera) comes in through the hooks.
 *
 * The rules are Launcher.setApps': an app the owner has placed keeps its tab and place; the
 * rest fall back to the default layout a fresh install starts with; anything that doesn't
 * name goes on the page its category or keywords say, alphabetically. A group left with one
 * member is that app again, and an empty one is gone.
 */
class LauncherLayout(
    private val bundled: LauncherPages,
    private val store: KeyedStore,
    /** Built-in pages this platform hasn't got (Luna DE: "favorites", the tab of Android's apps). */
    private val without: Set<String> = emptySet(),
    /** An id in the default layout that stands for one of the platform's own apps ("android:phone"): that app's id, or null. */
    private val alias: (id: String, apps: List<AppInfo>) -> String? = { _, _ -> null },
    /** The platform's own place for an app, before LunaCE's rule (Android's apps on their tab): a page designator, or null. */
    private val platformPage: (AppInfo) -> String? = { null },
    /** A saved tab's name as the platform shows it (an old default name given way to a new one). */
    private val rename: (designator: String, name: String) -> String = { _, n -> n },
) {
    /** A launcher cell: an app, or a group of apps (LunaCE's GroupIcon). */
    sealed class Tile {
        class App(val app: AppInfo) : Tile()
        class Group(val uid: String, val name: String, val members: List<AppInfo>) : Tile()
    }

    class Tab(val designator: String, val name: String, val tiles: List<Tile>, /** One of the built-in tabs, which can be renamed but not deleted. */ val permanent: Boolean)

    private val builtIn: List<Pair<String, String>> get() = bundled.initialPages.filter { it.first !in without }

    private fun savedArray(key: String): JSONArray? = store.all()[key]?.let { runCatching { JSONArray(it) }.getOrNull() }

    /** The tabs with their cells, for the apps there are now. */
    fun tabs(apps: List<AppInfo>): List<Tab> {
        class Page(val designator: String, var name: String, val permanent: Boolean, val tiles: MutableList<Tile> = ArrayList())
        val pages = builtIn.map { (d, n) -> Page(d, n, true) }.toMutableList()
        val byId = apps.associateBy { it.id }
        val placed = HashSet<String>()
        fun app(id: String) = byId[id]?.takeIf { placed.add(it.id) }
        fun tile(entry: Any?): Tile? = when (entry) {
            is String -> app(entry)?.let { Tile.App(it) }
            is JSONObject -> {
                val ids = entry.optJSONArray("apps")
                val members = (0 until (ids?.length() ?: 0)).mapNotNull { app(ids!!.optString(it)) }
                // A group left with one member is that app again (it dissolves, as LunaCE's does).
                when (members.size) {
                    0 -> null
                    1 -> Tile.App(members[0])
                    else -> Tile.Group(entry.optString("uid").ifEmpty { newGroupId() }, entry.optString("group", GROUP_NAME), members)
                }
            }
            else -> null
        }
        val tabs = savedArray("tabs")
        if (tabs != null) {
            // The owner's own tabs: the built-in ones by designator, keeping their names, and
            // any they added, in their order.
            val order = ArrayList<String>()
            for (i in 0 until tabs.length()) {
                val t = tabs.optJSONObject(i) ?: continue
                val d = t.optString("designator")
                if (d.isEmpty() || d in without || d in order) continue
                val page = pages.firstOrNull { it.designator == d }
                    ?: if (pages.size < MAX_TABS) Page(d, t.optString("name"), false).also { pages += it } else continue
                order += d
                page.name = rename(d, t.optString("name", page.name).ifEmpty { page.name })
                val cells = t.optJSONArray("apps")
                for (j in 0 until (cells?.length() ?: 0)) tile(cells!!.opt(j))?.let { page.tiles += it }
            }
            pages.sortBy { p -> order.indexOf(p.designator).let { if (it < 0) Int.MAX_VALUE else it } }
        } else {
            // A launcher saved before tabs could change: each page's apps by index.
            val legacy = savedArray("pages") ?: store.all()["order"]?.let { runCatching { JSONArray("[$it]") }.getOrNull() }
            for (pi in 0 until minOf(legacy?.length() ?: 0, pages.size)) {
                val ids = legacy!!.optJSONArray(pi) ?: continue
                for (i in 0 until ids.length()) app(ids.optString(i))?.let { pages[pi].tiles += Tile.App(it) }
            }
        }
        // Then the apps Lunacy ships with, where and in the order the default layout puts them.
        for ((designator, ids) in bundled.defaultLayout) {
            val page = pages.firstOrNull { it.designator == designator } ?: continue
            for (id in ids) app(alias(id, apps) ?: id)?.let { page.tiles += Tile.App(it) }
        }
        // Everything else by its category or keywords, alphabetically; a launch point an app
        // added, and anything unrecognised, on the first page.
        for (a in apps.filter { it.id !in placed }.sortedBy { it.title.lowercase() }) {
            val d = platformPage(a) ?: if (a.launchPoint != null) null else bundled.designatorFor(a)
            (pages.firstOrNull { it.designator == d } ?: pages.first()).tiles += Tile.App(a)
        }
        return pages.map { Tab(it.designator, it.name, it.tiles, it.permanent) }
    }

    /** The quick-launch bar's apps: the owner's, or the ones a fresh install has. */
    fun dock(apps: List<AppInfo>, phone: Boolean = false): List<AppInfo> {
        val byId = apps.associateBy { it.id }
        val own = savedArray(if (phone) "dock-phone" else "dock")
        val ids = if (own != null) (0 until own.length()).map { own.optString(it) } else bundled.quickLaunch(phone)
        return ids.distinct().mapNotNull { byId[it] }
    }

    /**
     * The launcher as the owner has just left it: [tabs] in the saved shape. What it says is
     * kept as it reads (the tabs' order and names, each cell's place, the groups), but for what
     * can't be: a name is cut to [MAX_NAME] and may not be empty, a tab past [MAX_TABS] is
     * dropped, a cell that is neither an app's id nor a group goes, and a tab the owner added
     * gets a designator of its own if it came with none. An app the platform hides ([hidden]) isn't
     * on the page, so it is put back where the last save had it, in its tab or its group, to come
     * back there when it is shown again (LauncherStore.save).
     */
    fun saveTabs(tabs: JSONArray, hidden: Set<String> = emptySet()) {
        val old = savedArray("tabs")
        val oldTabs = (0 until (old?.length() ?: 0)).mapNotNull { old!!.optJSONObject(it) }.associateBy { it.optString("designator") }
        fun ids(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.opt(it) }
        val out = JSONArray()
        val seen = HashSet<String>()
        val builtInNames = builtIn.toMap()
        for (i in 0 until tabs.length()) {
            val t = tabs.optJSONObject(i) ?: continue
            var d = t.optString("designator")
            if (d !in builtInNames) {
                if (out.length() >= MAX_TABS) continue
                if (!d.startsWith(USER_TAB) || d in seen) d = USER_TAB + UUID.randomUUID()
            }
            if (!seen.add(d)) continue
            val name = t.optString("name").trim().take(MAX_NAME).ifEmpty { builtInNames[d] ?: "tab" }
            val entries = ArrayList<Any>()
            val from = t.optJSONArray("apps")
            for (j in 0 until (from?.length() ?: 0)) when (val c = from!!.opt(j)) {
                is String -> entries += c
                is JSONObject -> {
                    val uid = c.optString("uid").ifEmpty { newGroupId() }
                    val members = ids(c.optJSONArray("apps")).filterIsInstance<String>().filter { it.isNotEmpty() }.toMutableList()
                    val was = ids(oldTabs[t.optString("designator")]?.optJSONArray("apps")).filterIsInstance<JSONObject>().firstOrNull { it.optString("uid") == uid }
                    ids(was?.optJSONArray("apps")).forEachIndexed { i, id -> if (id is String && id in hidden && id !in members) members.add(minOf(i, members.size), id) }
                    entries += JSONObject().put("group", c.optString("group").trim().take(MAX_NAME).ifEmpty { GROUP_NAME })
                        .put("uid", uid).put("apps", JSONArray(members))
                }
            }
            ids(oldTabs[t.optString("designator")]?.optJSONArray("apps")).forEachIndexed { i, id ->
                if (id is String && id in hidden && id !in entries) entries.add(minOf(i, entries.size), id)
            }
            out.put(JSONObject().put("designator", d).put("name", name).put("apps", JSONArray(entries)))
        }
        store.put("tabs", out.toString())
    }

    /** The quick-launch bar as the owner has just left it: app ids, in order. */
    fun saveDock(ids: JSONArray, phone: Boolean = false) {
        val list = (0 until ids.length()).map { ids.optString(it) }.filter { it.isNotEmpty() }.distinct().take(MAX_DOCK)
        store.put(if (phone) "dock-phone" else "dock", JSONArray(list).toString())
    }

    companion object {
        /** LauncherObject's limit: the built-in tabs and the owner's together. */
        const val MAX_TABS = 6
        /** A tab's or a group's name (renamedialog.cpp, groupoverlay.cpp). */
        const val MAX_NAME = 24
        /** What a new group is called until it is renamed (LunaCE's). */
        const val GROUP_NAME = "Group"
        /** The designator LauncherObject::createUserTab gives a tab the owner adds. */
        const val USER_TAB = "usertab_"
        /** The quick-launch bar holds five (QuickLaunch.MAX_ITEMS). */
        const val MAX_DOCK = 5
        fun newGroupId() = "group_" + UUID.randomUUID()

        /** A tab's cells in the saved shape, as the shell is given them and gives them back. */
        fun cells(tiles: List<Tile>, entry: (AppInfo) -> Any): JSONArray = JSONArray(tiles.map { t ->
            when (t) {
                is Tile.App -> entry(t.app)
                is Tile.Group -> JSONObject().put("group", t.name).put("uid", t.uid).put("apps", JSONArray(t.members.map(entry)))
            }
        })
    }
}
