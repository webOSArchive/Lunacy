package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * palm://com.palm.universalsearch: what Just Type offers besides launching apps, and the
 * settings Palm's Just Type app (com.palm.app.searchpreferences) keeps for it. Measured on the
 * reference TouchPad, 2026-11-03.
 *
 * Three lists:
 * - **UniversalSearchList** ("Search Using…"): the web engines below, then every app whose
 *   appinfo.json has `universalSearch.search` (type "app").
 * - **ActionList** ("Quick Actions"): apps with `universalSearch.action` (New Memo).
 * - **DBSearchItemList** ("Content"): apps with `universalSearch.dbsearch`, a db8 query Just
 *   Type runs with the typed words (Email, Calendar Events, Bookmarks & History).
 *
 * An app's entries are its appinfo's, with the app's id as both `id` and `url` (the device
 * lists App Catalog's search under com.palm.app.enyo-findapps, though its appinfo names
 * com.palm.app.findapps), an object launchParam as JSON text, and the app's icon. They are
 * listed in the order they were first seen, as the device's table keeps them, after the
 * engines. Whether each is on is kept here, by category and id, and outlives the app's
 * removal, as the device's table rows did.
 *
 * The device's list also had Maps (its app is gone) and Twitter (its search API is gone);
 * Lunacy leaves both out (codepoet, 2026-11-03).
 *
 * The preferences are strings, "true" or "false", under any key a caller sets: the device
 * stores whatever it is given (a key it didn't know, set to true, came back as "true").
 *
 * Each engine's icon is luna-applauncher's, which AppServer serves at the device's path.
 */
class UniversalSearch(context: Context, private val apps: () -> List<AppInfo>) {
    private val prefs = context.getSharedPreferences("universalsearch", Context.MODE_PRIVATE)
    private val listSubscribers = LinkedHashSet<Bus.Call>()
    private val prefSubscribers = LinkedHashSet<Bus.Call>()
    /** The shell's own Just Type hears every change, as the device's Just Type app subscribed. */
    var onChange: () -> Unit = {}

    companion object {
        const val SERVICE = "com.palm.universalsearch"
        const val ICONS = "/usr/lib/luna/system/luna-applauncher/images/"
        const val SEARCH = "search"
        const val ACTION = "action"
        const val DBSEARCH = "dbsearch"
        /** A web search Just Type offers: [url] has #{searchTerms} for the words; [on] is whether it is on at first. */
        private class Engine(val id: String, val name: String, val url: String, val on: Boolean)
        private val ENGINES = listOf(
            Engine("duckduckgo", "DuckDuckGo", "https://lite.duckduckgo.com/lite/?q=#{searchTerms}", true),
            Engine("wikipedia", "Wikipedia", "http://en.wikipedia.org/wiki/Special:Search?search=#{searchTerms}", true),
            Engine("cnn", "CNN", "http://www.cnn.com/search/?query=#{searchTerms}", false),
            Engine("amazon", "Amazon", "http://www.amazon.com/s/?k=#{searchTerms}", false),
            Engine("imdb", "IMDb", "http://www.imdb.com/find?q=#{searchTerms}", false),
        )
        /** The device's preferences out of the box (universalsearchprefs.db). */
        private val DEFAULT_PREFS = linkedMapOf("AppSearch" to "true", "ContactSearch" to "true", "GAL" to "false",
            "defaultSearch" to "true", "defaultSearchEngine" to "duckduckgo")
    }

    /** One entry in one of the lists, as Just Type and the settings app read it. */
    class Item(val category: String, val json: JSONObject) {
        val id: String get() = json.optString("id")
        val displayName: String get() = json.optString("displayName")
        val enabled: Boolean get() = json.optBoolean("enabled")
        val type: String get() = json.optString("type")
        val url: String get() = json.optString("url")
        val launchParam: String get() = json.optString("launchParam")
        val iconFilePath: String get() = json.optString("iconFilePath")
    }

    fun register(bus: Bus) {
        // What is installed when Lunacy first runs this is in the lists from the start, in
        // the order the apps are listed; anything installed later goes at the end.
        if (!prefs.contains("seen")) prefs.edit().putString("seen", integrated().joinToString("\n")).apply()
        bus.register(SERVICE, "getUniversalSearchList", Bus.CallHandler { c ->
            c.reply(listReply().put("subscribed", c.subscribe).toString())
            subscribe(c, listSubscribers)
        })
        bus.register(SERVICE, "getAllSearchPreference", Bus.CallHandler { c ->
            c.reply(JSONObject().put("SearchPreference", prefsJson()).put("subscribed", c.subscribe).put("returnValue", true).toString())
            subscribe(c, prefSubscribers)
        })
        bus.register(SERVICE, "setSearchPreference") { _, p, reply ->
            val key = p.optString("key")
            if (key.isEmpty() || !p.has("value")) return@register reply(Bus.error("Unable to get data"))
            prefs.edit().putString("pref:$key", p.opt("value").toString()).apply()
            reply(Bus.ok())
            prefsChanged()
            // The default engine is part of the lists' reply too.
            if (key == "defaultSearchEngine" || key == "defaultSearch") listChanged("update")
        }
        bus.register(SERVICE, "updateSearchItem") { _, p, reply ->
            val id = p.optString("id")
            val category = p.optString("category")
            if (id.isEmpty() || category !in listOf(SEARCH, ACTION, DBSEARCH)) return@register reply(Bus.error("Unable to get data"))
            val edit = prefs.edit()
            if (p.has("enabled")) edit.putBoolean(enabledKey(category, id), p.optBoolean("enabled"))
            if (p.optBoolean("setDefault", false)) edit.putString("pref:defaultSearchEngine", id)
            edit.apply()
            // The device answers true for an id it hasn't got, and changes nothing.
            reply(Bus.ok())
            listChanged("update")
            if (p.optBoolean("setDefault", false)) prefsChanged()
        }
        bus.register(SERVICE, "updateAllSearchItems") { _, p, reply ->
            val category = p.optString("category")
            if (category !in listOf(SEARCH, ACTION, DBSEARCH) || !p.has("enabled")) return@register reply(Bus.error("Unable to get data"))
            val on = p.optBoolean("enabled")
            val edit = prefs.edit()
            // The settings app turns the whole group off but the default engine's own row
            // with it; the default engine is shown by its own switch (defaultSearch).
            for (item in list(category)) if (!(category == SEARCH && item.id == defaultEngine())) edit.putBoolean(enabledKey(category, item.id), on)
            edit.apply()
            reply(Bus.ok())
            listChanged("update")
        }
        // An engine a page offered through OpenSearch, which the settings app adds when the
        // user picks it. Lunacy's Web app finds none (getOptionalSearchList is empty), so
        // nothing calls this; it is answered for real all the same.
        bus.register(SERVICE, "addSearchItem") { _, p, reply ->
            val id = p.optString("id")
            if (id.isEmpty() || p.optString("url").isEmpty()) return@register reply(Bus.error("Unable to get data"))
            val added = addedEngines()
            added.put(id, JSONObject().put("id", id).put("displayName", p.optString("displayName", id))
                .put("iconFilePath", p.optString("iconFilePath")).put("url", p.optString("url"))
                .put("suggestURL", p.optString("suggestURL")).put("launchParam", "").put("type", p.optString("type", "opensearch")))
            val edit = prefs.edit().putString("added", added.toString())
                .putBoolean(enabledKey(SEARCH, id), p.optBoolean("enabled", true))
            if (p.optBoolean("setDefault", false)) edit.putString("pref:defaultSearchEngine", id)
            edit.apply()
            reply(Bus.ok())
            listChanged("newapp")
            if (p.optBoolean("setDefault", false)) prefsChanged()
        }
        // The engines a page offered (OpenSearch descriptions the Web app found). Lunacy's Web
        // app doesn't look for them, so the list is the device's empty one.
        bus.register(SERVICE, "getOptionalSearchList", Bus.CallHandler { c ->
            c.reply(JSONObject().put("Options", JSONArray()).put("subscribed", c.subscribe).put("returnValue", true).toString())
        })
        bus.register(SERVICE, "clearOptionalSearchList") { _, _, reply -> reply(Bus.ok()) }
    }

    private fun subscribe(c: Bus.Call, set: MutableSet<Bus.Call>) {
        if (!c.subscribe || c.cancelled) return
        synchronized(set) { set += c }
        c.onCancel { synchronized(set) { set.remove(c) } }
    }

    // ---- what Just Type reads ----

    fun preference(key: String): String = prefs.getString("pref:$key", null) ?: DEFAULT_PREFS[key] ?: ""
    fun defaultEngine(): String = preference("defaultSearchEngine")

    /** The [category] list in its order, on and off alike. */
    fun list(category: String): List<Item> = when (category) {
        SEARCH -> engines() + appItems(SEARCH)
        else -> appItems(category)
    }

    /** The installed apps changed: a new app's entries join the end of the lists, a removed app's leave. */
    fun appsChanged() {
        val seen = savedSeen()
        val known = integrated()
        val added = known.filter { it !in seen }
        val removed = seen.filter { it !in known }
        if (added.isEmpty() && removed.isEmpty()) return
        prefs.edit().putString("seen", (seen.filter { it in known } + added).joinToString("\n")).apply()
        listChanged(if (added.isNotEmpty()) "newapp" else "remove")
    }

    // ---- the lists ----

    private fun enabledKey(category: String, id: String) = "on:$category:$id"
    private fun isOn(category: String, id: String, initially: Boolean) = prefs.getBoolean(enabledKey(category, id), initially)

    private fun addedEngines(): JSONObject = runCatching { JSONObject(prefs.getString("added", "{}")!!) }.getOrDefault(JSONObject())

    private fun engines(): List<Item> {
        val out = ArrayList<Item>()
        for (e in ENGINES) {
            out += Item(SEARCH, JSONObject().put("id", e.id).put("displayName", e.name)
                .put("iconFilePath", "${ICONS}search-icon-${e.id}.png").put("url", e.url)
                .put("suggestURL", "").put("launchParam", "").put("type", "web").put("enabled", isOn(SEARCH, e.id, e.on)))
        }
        val added = addedEngines()
        for (id in added.keys()) out += Item(SEARCH, JSONObject(added.getJSONObject(id).toString()).put("enabled", isOn(SEARCH, id, true)))
        return out
    }

    private fun integrated(): List<String> = apps().filter { it.appinfo.optJSONObject("universalSearch") != null }.map { it.id }
    private fun savedSeen(): List<String> = prefs.getString("seen", "").orEmpty().split('\n').filter { it.isNotEmpty() }

    /** Apps with integrations, in the order Lunacy first saw each; new ones go at the end. */
    private fun seenOrder(): List<String> {
        val seen = savedSeen()
        return seen + integrated().filter { it !in seen }
    }

    private fun appItems(category: String): List<Item> {
        val byId = apps().associateBy { it.id }
        val out = ArrayList<Item>()
        for (id in seenOrder()) {
            val app = byId[id] ?: continue
            val us = app.appinfo.optJSONObject("universalSearch") ?: continue
            val decl = us.optJSONObject(category) ?: continue
            val j = JSONObject().put("id", app.id).put("displayName", decl.optString("displayName", app.title))
                .put("iconFilePath", "/${AppServer.APPS}${app.dir}/${app.icon}")
            when (category) {
                SEARCH -> j.put("url", app.id).put("suggestURL", "").put("launchParam", param(decl)).put("type", "app")
                ACTION -> j.put("url", app.id).put("launchParam", param(decl))
                DBSEARCH -> {
                    j.put("launchParam", param(decl)).put("launchParamDbField", decl.optString("launchParamDbField"))
                    decl.opt("dbQuery")?.let { j.put("dbQuery", it) }
                    j.put("displayFields", decl.optJSONArray("displayFields") ?: JSONArray())
                    j.put("batchQuery", decl.optBoolean("batchQuery", false))
                }
            }
            j.put("enabled", isOn(category, app.id, true))
            out += Item(category, j)
        }
        return out
    }

    /** A launchParam as the device keeps it: a name as it is, an object as its JSON text. */
    /** A declaration's launchParam as text: an object or array as its JSON, anything else as itself, none as "". */
    private fun param(decl: JSONObject): String = decl.opt("launchParam")?.toString() ?: ""

    private fun prefsJson(): JSONObject {
        val out = JSONObject()
        for ((k, v) in DEFAULT_PREFS) out.put(k, v)
        for ((k, v) in prefs.all) if (k.startsWith("pref:")) out.put(k.removePrefix("pref:"), v.toString())
        return out
    }

    private fun listReply(): JSONObject {
        fun arr(category: String) = JSONArray().apply { list(category).forEach { put(it.json) } }
        return JSONObject().put("returnValue", true).put("UniversalSearchList", arr(SEARCH))
            .put("ActionList", arr(ACTION)).put("DBSearchItemList", arr(DBSEARCH))
            .put("defaultSearchEngine", defaultEngine())
    }

    // ---- telling subscribers ----

    /** The device sends the whole list again, with what happened: "update", "newapp", "remove". */
    private fun listChanged(event: String) {
        val reply = listReply().put("event", event).toString()
        synchronized(listSubscribers) { listSubscribers.toList() }.forEach { it.reply(reply) }
        onChange()
    }

    private fun prefsChanged() {
        val reply = JSONObject().put("returnValue", true).put("SearchPreference", prefsJson()).toString()
        synchronized(prefSubscribers) { prefSubscribers.toList() }.forEach { it.reply(reply) }
        onChange()
    }
}
