package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject

/**
 * What Just Type decides, as Palm's Just Type app did (com.palm.launcher: MainView,
 * LaunchAndSearch.js, Actions.js, DbSearch.js, Util) and as AndroidLuna's JustTypePanel has
 * it: which apps a text matches, whether it reads as an address or an email address, the db8
 * call each app's content search makes with the words, and the launch each choice makes. No
 * drawing: Luna DE's shell draws from this; AndroidLuna's panel still carries its own copy of
 * these rules (Docs/luna-de.md).
 */
object JustTypeSearch {
    /** SEARCHITEMSLIMIT, ACTIONITEMSLIMIT: four, then More…. */
    const val LIMIT = 4
    /** DbSearch's SEARCHMINLENGTH. */
    const val DB_MIN = 2
    /** Email's results open under its row up to this many. */
    const val INLINE_MAX = 5
    const val EMAIL = "com.palm.app.email"
    const val BROWSER = "com.palm.app.browser"
    /** Who Just Type is on the bus: Palm's own app. */
    const val CALLER = "com.palm.launcher"

    private val DELIMITERS = " ,._-:;()\\[]{}\"/".toSet()
    // Just Type's Util: what reads as an address, and as an email address.
    private val REG_URL = Regex("^((((http|https)://))|(www\\.))[-a-zA-Z0-9@:%_+.~#?&/=]+$")
    private val URL_DOMAIN = Regex("^([0-9a-zA-Z\\-]+\\.)+[a-zA-Z]{2,6}(:[0-9]+)?(\\S*)?$")
    private val EMAIL_RE = Regex("^([A-Za-z0-9_\\-.])+@([A-Za-z0-9_\\-.@])+\\.([A-Za-z]{2,4}) *$")
    // Util.Template's syntax: #{name}.
    private val TEMPLATE = Regex("#\\{\\s*(\\w+)\\s*\\}")

    fun isUrl(s: String) = REG_URL.matches(s) || URL_DOMAIN.matches(s)
    /** updateEmailOrWebDiv: an email address, from three characters, shows the actions alone. */
    fun isEmail(s: String) = s.length >= 3 && EMAIL_RE.matches(s)
    fun goesToWeb(term: String) = term.length >= 3 && isUrl(term) && !isEmail(term)

    /** LaunchPoint::matchesTitle: where the title starts with [term], or a word in it does; -1 if neither. */
    fun titleMatch(title: String, term: String): Int {
        val t = title.lowercase()
        if (t.startsWith(term)) return 0
        var i = t.indexOf(term, 1)
        while (i > 0) {
            if (t[i - 1] in DELIMITERS) return i
            i = t.indexOf(term, i + 1)
        }
        return -1
    }

    /**
     * LunaSysMgr's applicationManager/searchApps (ApplicationManager::searchLaunchPoints): a
     * title that starts with the text, or has a word that does, sorted by title; then a
     * keyword that starts with it (three characters or more; otherwise the whole keyword).
     */
    fun matchApps(apps: List<AppInfo>, text: String): List<AppInfo> {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        val byTitle = apps.filter { titleMatch(it.title, t) >= 0 }.sortedBy { it.title.lowercase() }
        val byKeyword = apps.filter { it !in byTitle }.filter { app ->
            val kw = app.keywords.map { it.lowercase() }
            (if (t.length >= 3) kw.any { it.startsWith(t) } else kw.any { it == t }) || app.title.lowercase().startsWith(t)
        }.sortedBy { it.title.lowercase() }
        return byTitle + byKeyword
    }

    /** encodeURIComponent. */
    fun encoded(s: String): String {
        val out = StringBuilder(s.length + 16)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            if (c < 0x80 && (c.toChar().let { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } || c.toChar() in "-_.!~*'()")) out.append(c.toChar())
            else out.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
        return out.toString()
    }

    private fun evaluate(template: String, words: String, json: Boolean): String =
        TEMPLATE.replace(template) { m ->
            when (m.groupValues[1]) {
                "searchTerms", "searchTerm" -> if (json) JSONObject.quote(words).let { it.substring(1, it.length - 1) } else words
                else -> ""
            }
        }

    /** A launchParam that is JSON text is an object; anything else is a name. */
    private fun launchObject(param: String): JSONObject? =
        param.trim().takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** What a choice does: a page for the Web app, an app launched with params, or a target opened. */
    sealed class Go {
        class Web(val url: String) : Go()
        class Launch(val appId: String, val params: JSONObject) : Go()
        class Open(val target: String) : Go()
    }

    /** LaunchAndSearch.handleLaunchAndSearchSelected: a web search's page, or the app with the words (encoded) under its launchParam. */
    fun searchWith(item: UniversalSearch.Item, term: String): Go? {
        val words = encoded(term)
        if (item.type == "web" || item.type == "opensearch") return Go.Web(evaluate(item.url, words, false))
        val params = if (launchObject(item.launchParam) != null)
            runCatching { JSONObject(evaluate(item.launchParam, words, true)) }.getOrNull() ?: return null
        else JSONObject().put(item.launchParam, words)
        return Go.Launch(item.url, params)
    }

    /** Actions.handleActionItemSelected: the words as typed, not encoded; Email with an address typed is a mailto:. */
    fun act(item: UniversalSearch.Item, term: String): Go? {
        if (item.url == EMAIL && isEmail(term)) return Go.Open("mailto:$term")
        val params = if (launchObject(item.launchParam) != null)
            runCatching { JSONObject(evaluate(item.launchParam, term, true)) }.getOrNull() ?: return null
        else JSONObject().put(item.launchParam, term)
        return Go.Launch(item.url, params)
    }

    /** DbSearch.handleContentItemTap: the app, with the result's field under its launchParam. */
    fun openResult(item: UniversalSearch.Item, result: JSONObject): Go? {
        val value = fieldOf(result, item.json.optString("launchParamDbField")) ?: return null
        return Go.Launch(item.id, JSONObject().put(item.launchParam, value))
    }

    /** A field of a db8 result by its dotted name ("from.name"). */
    fun fieldOf(o: JSONObject, name: String): Any? {
        var cur: Any? = o
        for (part in name.split('.')) cur = (cur as? JSONObject)?.opt(part) ?: return null
        return cur
    }

    /**
     * DbSearch.startSearchInDb: the db8 call for one content search with the words put in, as
     * its method ("search" or "batch") and params; null where the app declared no query.
     */
    fun dbCall(item: UniversalSearch.Item, text: String): Pair<String, JSONObject>? {
        val j = item.json
        val batch = j.optBoolean("batchQuery", false)
        val q = j.opt("dbQuery") ?: return null
        if (batch && q is JSONArray) {
            val ops = JSONArray(q.toString())
            // "It supports one field in the Where clause per query in the batch."
            for (i in 0 until ops.length()) ops.optJSONObject(i)?.optJSONObject("params")?.optJSONObject("query")
                ?.optJSONArray("where")?.optJSONObject(0)?.put("val", text)
            return "batch" to JSONObject().put("operations", ops)
        }
        if (q is JSONObject) {
            val query = JSONObject(q.toString())
            query.optJSONArray("where")?.let { w ->
                for (i in 0 until w.length()) w.optJSONObject(i)?.let { c -> if (c.optString("val").isBlank() && c.opt("val") !is Boolean) c.put("val", text) }
            }
            return "search" to JSONObject().put("query", query)
        }
        return null
    }

    /** A db8 reply's results: a search's own, or every response's of a batch. */
    fun dbResults(r: JSONObject): List<JSONObject> {
        val list = ArrayList<JSONObject>()
        if (!r.optBoolean("returnValue", false)) return list
        r.optJSONArray("responses")?.let { rs ->
            for (i in 0 until rs.length()) rs.optJSONObject(i)?.optJSONArray("results")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let(list::add) }
        } ?: r.optJSONArray("results")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let(list::add) }
        return list
    }

    /**
     * ContentItem: up to two of a result's display fields, as text; a field declared a
     * timestamp as its date and time (DateFmt, date long, time short, twelve-hour).
     */
    fun displayLines(item: UniversalSearch.Item, result: JSONObject): List<String> {
        val fields = item.json.optJSONArray("displayFields") ?: JSONArray()
        val lines = ArrayList<String>()
        for (i in 0 until minOf(2, fields.length())) {
            val f = fields.opt(i)
            val name = (f as? JSONObject)?.optString("name") ?: f.toString()
            var v = fieldOf(result, name)?.toString().orEmpty()
            if ((f as? JSONObject)?.optString("type") == "timestamp") v.toLongOrNull()?.let { ms ->
                v = java.text.SimpleDateFormat("MMMM d, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
            }
            lines += v
        }
        return lines
    }
}
