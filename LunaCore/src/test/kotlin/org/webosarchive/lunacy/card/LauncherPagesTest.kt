package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.webosarchive.lunacy.core.FolderAssets
import java.io.File
import java.nio.file.Files

/**
 * The launcher's shared rules (LauncherPages) against the bundled files Lunacy ships: what
 * AndroidLuna's launcher and Luna DE's both place a fresh install's apps by. A change to
 * launcher-pages.json or default-dock-positions.json that moves Palm's apps shows here.
 */
class LauncherPagesTest {
    private val assetsDir = File("../AndroidLuna/src/main/assets")
    private lateinit var bundled: File
    private lateinit var installed: File
    private lateinit var pages: LauncherPages
    private lateinit var registry: AppRegistry

    private fun app(dir: File, id: String, extra: String = "") {
        File(dir, id).mkdirs()
        File(dir, "$id/appinfo.json").writeText("""{"id":"$id","title":"$id","main":"index.html"$extra}""")
    }

    @Before fun setUp() {
        val tmp = Files.createTempDirectory("launcherpages").toFile()
        bundled = File(tmp, "bundled").apply { mkdirs() }
        installed = File(tmp, "installed").apply { mkdirs() }
        app(bundled, "com.example.plain")
        app(bundled, "com.example.game", ""","category":"Games"""")
        app(bundled, "com.example.prefs", ""","keywords":["Help","settings"]""")
        app(File(installed, "usr/palm/applications"), "com.example.installed")
        val assets = FolderAssets(listOf("" to assetsDir, "apps/" to bundled))
        pages = LauncherPages(assets)
        registry = AppRegistry(AppFiles(assets, installed))
    }

    @Test fun thePagesTheReferenceTouchPadNames() {
        assertEquals(listOf("apps", "downloads", "favorites", "prefs"), pages.initialPages.map { it.first })
    }

    @Test fun palmsAppsStartWhereTheDefaultLayoutPutsThem() {
        val layout = pages.defaultLayout.toMap()
        assertEquals(listOf("com.palm.app.browser", "com.palm.app.email", "com.palm.app.calendar"), layout["apps"]!!.take(3))
        assertEquals("com.palm.app.accounts", layout["prefs"]!!.first())
    }

    @Test fun lunaCEsRule_categoryThenKeywordsThenInstalled() {
        assertEquals("favorites", pages.designatorFor(registry.get("com.example.game")!!))
        assertEquals("prefs", pages.designatorFor(registry.get("com.example.prefs")!!))
        assertEquals("downloads", pages.designatorFor(registry.get("com.example.installed")!!))
        assertNull(pages.designatorFor(registry.get("com.example.plain")!!))
    }

    @Test fun theQuickLaunchBarsDefaults() {
        assertEquals(listOf("com.palm.app.browser", "com.palm.app.email", "com.palm.app.calendar", "com.palm.app.photos", "com.palm.app.enyo-findapps"),
            pages.quickLaunch())
        assertEquals(listOf("com.palm.app.browser", "com.palm.app.email", "com.palm.app.calendar", "com.palm.app.enyo-findapps"),
            pages.quickLaunch(phone = true))
    }
}
