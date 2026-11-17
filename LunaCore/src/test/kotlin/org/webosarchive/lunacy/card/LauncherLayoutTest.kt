package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.webosarchive.lunacy.core.FolderAssets
import java.io.File
import java.nio.file.Files

/**
 * The launcher as its owner arranges it (LauncherLayout), against the bundled page map: the
 * tabs, the order within them, groups and the quick-launch bar, saved and read back.
 */
class LauncherLayoutTest {
    private val assetsDir = File("../AndroidLuna/src/main/assets")
    private lateinit var registry: AppRegistry
    private lateinit var layout: LauncherLayout
    private lateinit var file: File

    private fun app(dir: File, id: String, extra: String = "") {
        File(dir, id).mkdirs()
        File(dir, "$id/appinfo.json").writeText("""{"id":"$id","title":"$id","main":"index.html"$extra}""")
    }

    @Before fun setUp() {
        val tmp = Files.createTempDirectory("launcherlayout").toFile()
        val bundled = File(tmp, "bundled").apply { mkdirs() }
        for (id in listOf("com.example.a", "com.example.b", "com.example.c", "com.example.d")) app(bundled, id)
        app(bundled, "com.example.prefs", ""","keywords":["settings"]""")
        val assets = FolderAssets(listOf("" to assetsDir, "apps/" to bundled))
        registry = AppRegistry(AppFiles(assets, File(tmp, "installed").apply { mkdirs() }))
        file = File(tmp, "launcher.json")
        layout = LauncherLayout(LauncherPages(assets), file, setOf("favorites"))
    }

    private fun apps() = registry.launchPoints
    private fun show() = layout.tabs(apps()).joinToString(" | ") { t ->
        t.designator.take(8) + ":" + t.tiles.joinToString(",") { c ->
            when (c) { is LauncherLayout.Tile.App -> c.app.id.substringAfterLast('.'); is LauncherLayout.Tile.Group -> c.name + "(" + c.members.joinToString("+") { it.id.substringAfterLast('.') } + ")" }
        }
    }
    private fun tab(designator: String, name: String, vararg cells: Any) = JSONObject().put("designator", designator).put("name", name).put("apps", JSONArray(cells.toList()))
    private fun group(name: String, vararg ids: String) = JSONObject().put("group", name).put("uid", "group_x").put("apps", JSONArray(ids.toList()))

    @Test fun aFreshLauncherHasTheBuiltInTabsWithoutAndroids() {
        val tabs = layout.tabs(apps())
        assertEquals(listOf("apps", "downloads", "prefs"), tabs.map { it.designator })
        assertTrue(tabs.all { it.permanent })
        assertEquals("apps:a,b,c,d | download: | prefs:prefs", show())
    }

    @Test fun whatTheOwnerPlacedKeepsItsTabAndPlace() {
        layout.saveTabs(JSONArray(listOf(tab("apps", "mine", "com.example.c", "com.example.a"), tab("prefs", "settings", "com.example.b"))))
        // The saved order first; the tab the save left out comes back, last; the rest where they belong.
        assertEquals("apps:c,a,d | prefs:b,prefs | download:", show())
        assertEquals("mine", layout.tabs(apps()).first().name)
    }

    @Test fun aGroupKeepsItsMembersAndDissolvesAtOne() {
        layout.saveTabs(JSONArray(listOf(tab("apps", "apps", group("Tools", "com.example.a", "com.example.b"), "com.example.c", group("Lone", "com.example.d", "no.such.app")))))
        assertEquals("apps:Tools(a+b),c,d | download: | prefs:prefs", show())
    }

    @Test fun anAppIsOnThePageOnce() {
        layout.saveTabs(JSONArray(listOf(tab("apps", "apps", "com.example.a", group("G", "com.example.a", "com.example.b", "com.example.c")))))
        assertEquals("apps:a,G(b+c),d | download: | prefs:prefs", show())
    }

    @Test fun theOwnersTabsAreKeptUpToTheLimit() {
        val tabs = JSONArray(listOf(tab("apps", "apps"), tab("usertab_1", "  one  ", "com.example.a"), tab("", "two"), tab("usertab_3", "three"), tab("usertab_4", "four"), tab("usertab_5", "five"), tab("usertab_6", "six")))
        layout.saveTabs(tabs)
        val now = layout.tabs(apps())
        assertEquals(LauncherLayout.MAX_TABS, now.size)
        assertEquals("one", now[1].name)
        assertEquals(false, now[1].permanent)
        assertTrue("a tab with no designator gets one", now[2].designator.startsWith(LauncherLayout.USER_TAB))
        // The built-in tabs are always there, whatever was saved.
        assertTrue(now.map { it.designator }.containsAll(listOf("apps", "downloads", "prefs")))
    }

    @Test fun aNameIsCutAndNeverEmpty() {
        layout.saveTabs(JSONArray(listOf(tab("apps", "x".repeat(40)), tab("prefs", "   "))))
        val now = layout.tabs(apps())
        assertEquals(24, now[0].name.length)
        assertEquals("settings", now[1].name)
    }

    @Test fun theQuickLaunchBarIsTheOwnersOnceTheyChangeIt() {
        assertTrue(layout.dock(apps()).isEmpty())   // none of Palm's own are installed here
        layout.saveDock(JSONArray(listOf("com.example.b", "com.example.b", "no.such.app", "com.example.a")))
        assertEquals(listOf("com.example.b", "com.example.a"), layout.dock(apps()).map { it.id })
        layout.saveDock(JSONArray())
        assertTrue(layout.dock(apps()).isEmpty())
    }
}
