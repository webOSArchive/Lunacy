package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * palm://com.palm.universalsearch: the search engines Just Type and the Web app's address bar
 * offer. Measured on the reference TouchPad (getUniversalSearchList, 2026-10-04): the web
 * engines below, in this order and with these enabled flags, DuckDuckGo the default. The
 * device's list also carries app searches (Maps, App Catalog), actions (New Message, New Memo)
 * and database searches; Lunacy's Just Type doesn't read them, so only the web engines are
 * answered for now.
 *
 * Each engine's icon is luna-applauncher's, which AppServer serves at the device's path.
 */
class UniversalSearch(context: Context) {
    private val prefs = context.getSharedPreferences("universalsearch", Context.MODE_PRIVATE)

    companion object {
        const val SERVICE = "com.palm.universalsearch"
        const val ICONS = "/usr/lib/luna/system/luna-applauncher/images/"
        /** id, name, URL (#{searchTerms} for the words), enabled. */
        private val ENGINES = listOf(
            listOf("duckduckgo", "DuckDuckGo", "https://lite.duckduckgo.com/lite/?q=#{searchTerms}", true),
            listOf("wikipedia", "Wikipedia", "http://en.wikipedia.org/wiki/Special:Search?search=#{searchTerms}", true),
            listOf("twitter", "Twitter", "http://search.twitter.com/search?q=#{searchTerms}", true),
            listOf("cnn", "CNN", "http://www.cnn.com/search/?query=#{searchTerms}", false),
            listOf("amazon", "Amazon", "http://www.amazon.com/s/?k=#{searchTerms}", false),
            listOf("imdb", "IMDb", "http://www.imdb.com/find?q=#{searchTerms}", false),
        )
    }

    fun register(bus: Bus) {
        bus.register(SERVICE, "getUniversalSearchList") { _, _, reply -> reply(list()) }
        bus.register(SERVICE, "setSearchPreference") { _, p, reply ->
            if (p.optString("key") != "defaultSearchEngine") return@register reply(Bus.error("Unknown search preference: ${p.optString("key")}"))
            val id = p.optString("value")
            if (ENGINES.none { it[0] == id }) return@register reply(Bus.error("No search engine $id"))
            prefs.edit().putString("defaultSearchEngine", id).apply()
            reply(Bus.ok())
        }
        // The engines a user added from a page's OpenSearch description. Lunacy has no way to
        // add one, so there are none to clear.
        bus.register(SERVICE, "clearOptionalSearchList") { _, _, reply -> reply(Bus.ok()) }
    }

    private fun list(): String {
        val engines = JSONArray()
        for ((id, name, url, enabled) in ENGINES) {
            engines.put(JSONObject().put("id", id).put("displayName", name)
                .put("iconFilePath", "${ICONS}search-icon-$id.png").put("url", url)
                .put("suggestURL", "").put("launchParam", "").put("type", "web").put("enabled", enabled))
        }
        return JSONObject().put("returnValue", true).put("UniversalSearchList", engines)
            .put("ActionList", JSONArray()).put("DBSearchItemList", JSONArray())
            .put("defaultSearchEngine", prefs.getString("defaultSearchEngine", "duckduckgo"))
            .put("subscribed", false).toString()
    }
}
