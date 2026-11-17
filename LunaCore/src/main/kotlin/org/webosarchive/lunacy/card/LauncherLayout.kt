package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import java.io.File
import java.util.UUID

/**
 * The launcher as its owner has arranged it, as LunaCE's launcher keeps it: the tabs (the
 * built-in ones, renamed or not, and the ones they added), each tab's icons in their order, the
 * groups they made of them, and the quick-launch bar's apps. Kept as JSON in [file], in the
 * shape AndroidLuna's launcher saves its own (LauncherStore: `tabs`, each a designator, a name
 * and its cells, a cell an app's id or a group, {"group": name, "uid": id, "apps": [ids]}), so
 * the two read alike. Luna DE's launcher is drawn from this; AndroidLuna's still keeps its own
 * copy of these rules in its Launcher view (Docs/luna-de.md).
 *
 * The rules are Launcher.setApps': an app the owner has placed keeps its tab and place; the
 * rest fall back to the default layout a fresh install starts with; anything that doesn't
 * name goes on the page its category or keywords say, alphabetically. A group left with one
 * member is that app again, and an empty one is gone.
 */
class LauncherLayout(
    private val bundled: LauncherPages,
    private val file: File,
    /** Built-in pages this platform hasn't got (Luna DE: "favorites", the tab of Android's apps). */
    private val without: Set<String> = emptySet(),
) {
    /** A launcher cell: an app, or a group of apps (LunaCE's GroupIcon). */
    sealed class Tile {
        class App(val app: AppInfo) : Tile()
        class Group(val uid: String, val name: String, val members: List<AppInfo>) : Tile()
    }

    class Tab(val designator: String, val name: String, val tiles: List<Tile>, /** One of the built-in tabs, which can be renamed but not deleted. */ val permanent: Boolean)

    private val builtIn: List<Pair<String, String>> get() = bundled.initialPages.filter { it.first !in without }

    private fun saved(): JSONObject = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())

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
        val tabs = saved().optJSONArray("tabs")
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
                page.name = t.optString("name", page.name).ifEmpty { page.name }
                val cells = t.optJSONArray("apps")
                for (j in 0 until (cells?.length() ?: 0)) tile(cells!!.opt(j))?.let { page.tiles += it }
            }
            pages.sortBy { p -> order.indexOf(p.designator).let { if (it < 0) Int.MAX_VALUE else it } }
        }
        // Then the apps Lunacy ships with, where and in the order the default layout puts them.
        for ((designator, ids) in bundled.defaultLayout) {
            val page = pages.firstOrNull { it.designator == designator } ?: continue
            for (id in ids) app(id)?.let { page.tiles += Tile.App(it) }
        }
        // Everything else by its category or keywords, alphabetically; a launch point an app
        // added, and anything unrecognised, on the first page.
        for (a in apps.filter { it.id !in placed }.sortedBy { it.title.lowercase() }) {
            val d = if (a.launchPoint != null) null else bundled.designatorFor(a)
            (pages.firstOrNull { it.designator == d } ?: pages.first()).tiles += Tile.App(a)
        }
        return pages.map { Tab(it.designator, it.name, it.tiles, it.permanent) }
    }

    /** The quick-launch bar's apps: the owner's, or the ones a fresh install has. */
    fun dock(apps: List<AppInfo>, phone: Boolean = false): List<AppInfo> {
        val byId = apps.associateBy { it.id }
        val own = saved().optJSONArray(if (phone) "dock-phone" else "dock")
        val ids = if (own != null) (0 until own.length()).map { own.optString(it) } else bundled.quickLaunch(phone)
        return ids.distinct().mapNotNull { byId[it] }
    }

    /**
     * The launcher as the owner has just left it: [tabs] in the saved shape. What it says is
     * kept as it reads (the tabs' order and names, each cell's place, the groups), but for what
     * can't be: a name is cut to [MAX_NAME] and may not be empty, a tab past [MAX_TABS] is
     * dropped, a cell that is neither an app's id nor a group goes, and a tab the owner added
     * gets a designator of its own if it came with none.
     */
    fun saveTabs(tabs: JSONArray) {
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
            val cells = JSONArray()
            val from = t.optJSONArray("apps")
            for (j in 0 until (from?.length() ?: 0)) when (val c = from!!.opt(j)) {
                is String -> cells.put(c)
                is JSONObject -> {
                    val ids = c.optJSONArray("apps") ?: JSONArray()
                    val members = JSONArray((0 until ids.length()).map { ids.optString(it) }.filter { it.isNotEmpty() })
                    cells.put(JSONObject().put("group", c.optString("group").trim().take(MAX_NAME).ifEmpty { GROUP_NAME })
                        .put("uid", c.optString("uid").ifEmpty { newGroupId() }).put("apps", members))
                }
            }
            out.put(JSONObject().put("designator", d).put("name", name).put("apps", cells))
        }
        write(saved().put("tabs", out))
    }

    /** The quick-launch bar as the owner has just left it: app ids, in order. */
    fun saveDock(ids: JSONArray, phone: Boolean = false) {
        val list = (0 until ids.length()).map { ids.optString(it) }.filter { it.isNotEmpty() }.distinct().take(MAX_DOCK)
        write(saved().put(if (phone) "dock-phone" else "dock", JSONArray(list)))
    }

    private fun write(o: JSONObject) {
        try { file.parentFile?.mkdirs(); file.writeText(o.toString()) }
        catch (e: Exception) { Log.w(Lunacy.TAG, "launcher: not saved: $e") }
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
