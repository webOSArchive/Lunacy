package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.webosarchive.lunacy.core.FolderAssets
import org.webosarchive.lunacy.core.KeyedStore
import org.webosarchive.lunacy.core.TestPlatform
import java.io.File
import java.nio.file.Files

/**
 * Just Type's rules (JustTypeSearch) and what apps offer it (UniversalSearch), with apps that
 * declare each kind of integration in their appinfo.json: what a developer's app gets.
 */
class JustTypeSearchTest {
    private class Store : KeyedStore {
        val map = LinkedHashMap<String, String>()
        override fun all(): Map<String, String> = map
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private lateinit var registry: AppRegistry
    private lateinit var search: UniversalSearch

    private fun app(dir: File, id: String, title: String, extra: String = "") {
        File(dir, id).mkdirs()
        File(dir, "$id/appinfo.json").writeText("""{"id":"$id","title":"$title","main":"index.html"$extra}""")
    }

    @Before fun setUp() {
        TestPlatform.install()
        val tmp = Files.createTempDirectory("justtype").toFile()
        val bundled = File(tmp, "bundled").apply { mkdirs() }
        app(bundled, "com.example.notes", "Field Notes", ""","keywords":["memo","jotter"],"universalSearch":{
            "action":{"displayName":"New Note","url":"com.example.notes","launchParam":"newNote"},
            "search":{"displayName":"Field Notes","url":"com.example.notes","launchParam":{"find":"#{searchTerms}","from":"justtype"}},
            "dbsearch":{"displayName":"Notes","url":"com.example.notes","launchParam":"open","launchParamDbField":"_id",
                "displayFields":["title",{"name":"when","type":"timestamp"}],
                "dbQuery":{"from":"com.example.notes:1","where":[{"prop":"text","op":"?","val":"","collate":"primary"}],"limit":20}}}""")
        app(bundled, "com.example.shop", "Corner Shop", ""","universalSearch":{"search":{"displayName":"Corner Shop","launchParam":"q"}}""")
        app(bundled, "com.example.plain", "Plain")
        registry = AppRegistry(AppFiles(FolderAssets(listOf("apps/" to bundled)), File(tmp, "installed").apply { mkdirs() }))
        search = UniversalSearch(Store()) { registry.apps }
        search.register(Bus())
    }

    private fun item(category: String, id: String) = search.list(category).first { it.id == id }

    @Test fun appsMatchByTitleWordsThenKeywords() {
        val apps = registry.apps
        assertEquals(listOf("Field Notes"), JustTypeSearch.matchApps(apps, "fie").map { it.title })
        assertEquals("a word in the title", listOf("Field Notes"), JustTypeSearch.matchApps(apps, "no").map { it.title })
        assertEquals("a keyword, from three characters", listOf("Field Notes"), JustTypeSearch.matchApps(apps, "jot").map { it.title })
        assertTrue("two characters must be the whole keyword", JustTypeSearch.matchApps(apps, "jo").isEmpty())
        assertEquals(listOf("Corner Shop", "Plain"), JustTypeSearch.matchApps(apps, "  ").map { it.title } + JustTypeSearch.matchApps(apps, "c").map { it.title } + JustTypeSearch.matchApps(apps, "pl").map { it.title })
    }

    @Test fun addressesAndEmailAddressesAreTold() {
        assertTrue(JustTypeSearch.goesToWeb("www.example.com"))
        assertTrue(JustTypeSearch.goesToWeb("example.org/page"))
        assertFalse(JustTypeSearch.goesToWeb("example"))
        assertTrue(JustTypeSearch.isEmail("a@example.com"))
        assertFalse("an email address isn't an address to go to", JustTypeSearch.goesToWeb("a@example.com"))
    }

    @Test fun anAppsDeclarationsAreInTheListsAfterTheEngines() {
        val using = search.list(UniversalSearch.SEARCH)
        assertEquals(listOf("duckduckgo", "wikipedia", "cnn", "amazon", "imdb", "com.example.notes", "com.example.shop"), using.map { it.id })
        assertEquals("app", using.last().type)
        assertEquals(listOf("New Note"), search.list(UniversalSearch.ACTION).map { it.displayName })
        assertEquals(listOf("Notes"), search.list(UniversalSearch.DBSEARCH).map { it.displayName })
        assertTrue(using.first { it.id == "com.example.shop" }.enabled)
    }

    @Test fun aSearchLaunchesItsAppWithTheWordsEncoded() {
        val shop = JustTypeSearch.searchWith(item(UniversalSearch.SEARCH, "com.example.shop"), "red & blue") as JustTypeSearch.Go.Launch
        assertEquals("com.example.shop", shop.appId)
        assertEquals("""{"q":"red%20%26%20blue"}""", shop.params.toString())
        // A launchParam that is an object: the words go where #{searchTerms} is.
        val notes = JustTypeSearch.searchWith(item(UniversalSearch.SEARCH, "com.example.notes"), "to do") as JustTypeSearch.Go.Launch
        assertEquals("to%20do", notes.params.getString("find"))
        assertEquals("justtype", notes.params.getString("from"))
        val web = JustTypeSearch.searchWith(item(UniversalSearch.SEARCH, "wikipedia"), "palm pre") as JustTypeSearch.Go.Web
        assertEquals("http://en.wikipedia.org/wiki/Special:Search?search=palm%20pre", web.url)
    }

    @Test fun anActionGetsTheWordsAsTyped() {
        val go = JustTypeSearch.act(item(UniversalSearch.ACTION, "com.example.notes"), "buy \"milk\" & eggs") as JustTypeSearch.Go.Launch
        assertEquals("buy \"milk\" & eggs", go.params.getString("newNote"))
    }

    @Test fun aContentSearchPutsTheWordsInItsQueryAndOpensAResult() {
        val notes = item(UniversalSearch.DBSEARCH, "com.example.notes")
        val (method, params) = JustTypeSearch.dbCall(notes, "milk")!!
        assertEquals("search", method)
        assertEquals("milk", params.getJSONObject("query").getJSONArray("where").getJSONObject(0).getString("val"))
        assertEquals(20, params.getJSONObject("query").getInt("limit"))
        val reply = JSONObject().put("returnValue", true).put("results", JSONArray().put(JSONObject().put("_id", "++abc").put("title", "Milk run").put("when", 0)))
        val result = JustTypeSearch.dbResults(reply).single()
        val lines = JustTypeSearch.displayLines(notes, result)
        assertEquals("Milk run", lines[0])
        assertTrue("a timestamp is shown as a date", lines[1].contains("19"))
        val go = JustTypeSearch.openResult(notes, result) as JustTypeSearch.Go.Launch
        assertEquals("""{"open":"++abc"}""", go.params.toString())
        assertNull(JustTypeSearch.openResult(notes, JSONObject()))
        assertTrue(JustTypeSearch.dbResults(JSONObject().put("returnValue", false)).isEmpty())
    }

    @Test fun whatIsTurnedOffStaysInTheListOff() {
        val bus = Bus()
        val s = UniversalSearch(Store()) { registry.apps }
        s.register(bus)
        bus.call("com.palm.app.searchpreferences", "palm://com.palm.universalsearch/updateSearchItem", """{"id":"com.example.shop","category":"search","enabled":false}""") {}
        TestPlatform.scheduler.idle()
        assertFalse(s.list(UniversalSearch.SEARCH).first { it.id == "com.example.shop" }.enabled)
    }
}
