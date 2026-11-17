package org.webosarchive.lunacy.desktop

import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.JustTypeSearch
import org.webosarchive.lunacy.card.UniversalSearch
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Just Type for Luna DE's shell: what a typed text finds, and what each choice does, by
 * LunaCore's rules (JustTypeSearch) over com.palm.universalsearch's lists (UniversalSearch),
 * which every installed app's appinfo.json feeds. So a developer's `universalSearch` entries
 * (a search, an action, a content search over their db8 kind) show here as they did on a
 * device, and choosing one launches their app with the params the device gave it. The shell
 * page draws what [find] answers and sends each choice to [go]; the calls are made on the bus
 * as Palm's Just Type app (com.palm.launcher), as on Android.
 */
class DesktopJustType(private val main: DesktopScheduler, private val bus: Bus, private val registry: AppRegistry,
                      private val search: UniversalSearch, private val entry: (AppInfo) -> JSONObject) {
    /** The content results of the last search, by the item they are under: a choice names one by its place. */
    @Volatile private var lastResults: Map<String, List<JSONObject>> = emptyMap()

    private fun itemJson(i: UniversalSearch.Item) = JSONObject().put("id", i.id).put("displayName", i.displayName).put("type", i.type)

    /**
     * What [text] finds: the apps to launch, the default engine, the other searches and the
     * actions that are on, whether it reads as an address or an email address, and with
     * [content] the apps' content searches, run now (a db8 call each, waited for).
     */
    fun find(text: String, content: Boolean): JSONObject {
        val out = JSONObject().put("term", text)
        val email = JustTypeSearch.isEmail(text)
        out.put("email", email).put("goToWeb", JustTypeSearch.goesToWeb(text))
        val lists = main.call {
            Triple(search.list(UniversalSearch.SEARCH), search.list(UniversalSearch.ACTION), search.list(UniversalSearch.DBSEARCH)) to
                Triple(search.defaultEngine(), search.preference("AppSearch") != "false", search.preference("defaultSearch") != "false")
        }
        val (engines, actions, dbItems) = lists.first
        val (defaultEngine, appSearchOn, defaultSearchOn) = lists.second
        val t = text.trim().lowercase()
        val apps = if (appSearchOn) JustTypeSearch.matchApps(registry.launchPoints, text) else emptyList()
        out.put("launch", JSONArray(apps.map { a -> entry(a).put("match", JustTypeSearch.titleMatch(a.title, t)).put("matchLength", t.length) }))
        (if (defaultSearchOn) engines.firstOrNull { it.id == defaultEngine && it.type != "app" } else null)?.let { out.put("default", itemJson(it)) }
        out.put("using", JSONArray(engines.filter { it.enabled && it.id != defaultEngine }.map(::itemJson)))
        out.put("actions", JSONArray(actions.filter { it.enabled }.map(::itemJson)))
        out.put("limit", JustTypeSearch.LIMIT)
        if (content && text.length >= JustTypeSearch.DB_MIN) {
            val on = dbItems.filter { it.enabled }
            val got = java.util.concurrent.ConcurrentHashMap<String, List<JSONObject>>()
            val calls = on.mapNotNull { item -> JustTypeSearch.dbCall(item, text)?.let { item to it } }
            val done = CountDownLatch(calls.size)
            main.post {
                for ((item, call) in calls) bus.call(JustTypeSearch.CALLER, "palm://com.palm.db/${call.first}", call.second.toString()) { r ->
                    got[item.id] = JustTypeSearch.dbResults(runCatching { JSONObject(r) }.getOrDefault(JSONObject()))
                    done.countDown()
                }
            }
            if (!done.await(DB_WAIT_MS, TimeUnit.MILLISECONDS)) Log.w(Lunacy.TAG, "just type: a content search didn't answer in $DB_WAIT_MS ms")
            lastResults = got
            out.put("content", JSONArray(on.mapNotNull { item ->
                val list = got[item.id].orEmpty()
                if (list.isEmpty()) null else itemJson(item).put("inline", item.id == JustTypeSearch.EMAIL && list.size <= JustTypeSearch.INLINE_MAX)
                    .put("results", JSONArray(list.map { r -> JSONObject().put("lines", JSONArray(JustTypeSearch.displayLines(item, r))).put("kind", r.optString("_kind")) }))
            }))
        }
        return out
    }

    /**
     * A choice: "app" (launch [id]), "web" (the typed address), "default" or "using" (search
     * with engine or app [id]), "action" ([id]'s action), "result" (the [index]th content
     * result under [id]), each with the text as it was typed.
     */
    fun go(kind: String, id: String, text: String, index: Int) = main.post {
        val go: JustTypeSearch.Go? = when (kind) {
            "app" -> JustTypeSearch.Go.Launch(id, JSONObject())
            "web" -> JustTypeSearch.Go.Web(text)
            "default", "using" -> search.list(UniversalSearch.SEARCH).firstOrNull { it.id == id }?.let { JustTypeSearch.searchWith(it, text) }
            "action" -> search.list(UniversalSearch.ACTION).firstOrNull { it.id == id }?.let { JustTypeSearch.act(it, text) }
            "result" -> search.list(UniversalSearch.DBSEARCH).firstOrNull { it.id == id }?.let { item ->
                lastResults[id]?.getOrNull(index)?.let { JustTypeSearch.openResult(item, it) }
            }
            else -> null
        }
        when (go) {
            is JustTypeSearch.Go.Launch -> call("launch", JSONObject().put("id", go.appId).also { if (kind != "app") it.put("params", go.params) })
            // A web search's or a typed address's page, for the Web app.
            is JustTypeSearch.Go.Web ->
                if (registry.get(JustTypeSearch.BROWSER) != null) call("launch", JSONObject().put("id", JustTypeSearch.BROWSER).put("params", JSONObject().put("target", go.url)))
                else call("open", JSONObject().put("target", go.url))
            is JustTypeSearch.Go.Open -> call("open", JSONObject().put("target", go.target))
            null -> Log.w(Lunacy.TAG, "just type: nothing to do for $kind $id")
        }
    }

    private fun call(method: String, params: JSONObject) {
        bus.call(JustTypeSearch.CALLER, "palm://com.palm.applicationManager/$method", params.toString()) { r ->
            Log.i(Lunacy.TAG, "just type: $method ${params.optString("id", params.optString("target"))}: $r")
        }
    }

    private companion object {
        /** How long the content searches are waited for before Just Type is answered without the late ones. */
        const val DB_WAIT_MS = 3000L
    }
}
