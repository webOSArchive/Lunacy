package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.UniversalSearch

/**
 * Just Type. On a device this is Palm's own Enyo app (com.palm.launcher, luna-applauncher) in
 * a window under the status bar; here it is drawn natively to that app's measurements and with
 * its own art, as measured on the reference TouchPad (Workbench/results/b6-tp-justtype.png,
 * jt-tp-*.png, 2026-09-22 and 2026-11-03):
 *
 * - the header (search-header-bg.png) with the 570-px field (roundedbox.png) and its clear
 *   button, 13 px from the top;
 * - once a search has run, the filter tabs, ALL / CONTACTS / CONTENT / ACTIONS: six equal
 *   tab-button.png cells across the screen, the outer two empty, 47 px high;
 * - in ALL: "Go to website" for something that reads as an address; LAUNCH, 85 × 113 app
 *   tiles with the matched start of each title underlined (Applications in Just Type's
 *   settings); a group for each app's content search with results (`universalSearch.dbsearch`:
 *   Email, Calendar Events, Bookmarks & History) and its count, Email's own results open
 *   under it when there are five or fewer; "Search <default engine>"; SEARCH USING… (the
 *   other engines and app searches that are on, four and then More…); QUICK ACTIONS (the
 *   apps' actions, four and then More…);
 * - CONTENT: the content groups alone; tapping one lists its results under its name and
 *   count. ACTIONS: launch, search and actions. An email address shows the actions alone.
 *
 * What is offered, and whether each is on, is com.palm.universalsearch's ([UniversalSearch]),
 * which Palm's Just Type settings app sets. Each choice launches its app exactly as Palm's
 * code builds the call (LaunchAndSearch.js, Actions.js, DbSearch.js).
 *
 * As on the device, the results follow the typing by half a second (MainView's debounce), and
 * the content searches by half a second more (DbSearch's own).
 *
 * Contacts aren't searched yet (codepoet, 2026-11-03): the CONTACTS tab shows no results.
 *
 * On a phone (Docs/phone.md) the TouchPad's 570 px field and 736 px groups would overrun a
 * 360 px screen, so both span the width less 12 px margins, and the tiles, still 85 × 113,
 * sit as many to a row as fit with equal gaps of at least 10 px between and around them.
 * The filter tabs drop the two empty outer cells, so the four span the width; where even
 * that is too narrow (the Fold3's outer screen, 320 px), all four labels shrink alike until
 * CONTACTS fits 8 px inside its cell (codepoet, 2026-11-04).
 *
 * Which apps match is LunaSysMgr's `applicationManager/searchApps`
 * (ApplicationManager::searchLaunchPoints): a title that starts with the text, or has a word
 * that does, sorted by title; then, for a default launch point, a keyword that starts with it
 * (three characters or more; otherwise the whole keyword) or an app menu name that does.
 */
@SuppressLint("ViewConstructor")
class JustTypePanel(context: Context, private val luna: Luna) : FrameLayout(context) {
    companion object {
        const val FADE_MS = 150L                 // universalSearchCrossFadeDuration
        const val SEARCH_DELAY_MS = 500L         // MainView's Util.debounce(…, 0.5)
        const val DB_DELAY_MS = 500L             // DbSearch's own, after that
        const val HEADER_H = 62f
        const val TABS_H = 47f                   // tab-button.png's rows
        const val TAB_PAD = 8f                   // a phone tab's label, kept this far in from the cell's edges in all
        const val SHADOW_H = 10f
        const val FIELD_W = 570f
        const val FIELD_TOP = 13f
        const val GROUP_W = 736f                 // 708 plus the 14-px borders
        const val FIRST_TOP = 33f                // the first group, below the tabs (24 + 8 + the frame's edge)
        const val DETAIL_TOP = 9f                // a content group's results, below the tabs
        const val GAP = 12f                      // between groups
        const val GAP_LABELED = 20f              // before a group with a caption
        const val GAP_AFTER_LAUNCH = 28f
        const val CAPTION_H = 28f
        const val CAPTION_OVERLAP = 3f           // the first row under a caption is this much shorter
        const val ROW_H = 74f                    // a search, action or "Search …" row
        const val DB_ROW_H = 70f                 // a content group's own row
        const val DB_HEAD_H = 71f                // the same, with its results open under it
        const val CONTENT_ROW_H = 70f            // a result under CONTENT
        const val DRAWER_ROW_H = 66f             // a result open under Email
        const val WEB_ROW_H = 58f                // "Go to website": a 32-px line
        const val TILE_W = 85f
        const val TILE_H = 113f
        const val TILE_LEFT = 40f
        const val TILE_TOP = 48f
        const val TILE_PITCH_X = 138f            // 28 + 85 + 25
        const val TILE_PITCH_Y = 138f            // 10 + 113 + 15
        const val PHONE_MARGIN = 12f
        const val PHONE_TILE_GAP = 10f
        const val LIMIT = 4                      // SEARCHITEMSLIMIT, ACTIONITEMSLIMIT
        const val DB_MIN = 2                     // DbSearch's SEARCHMINLENGTH
        const val INLINE_MAX = 5                 // Email's results open under it up to this many
        const val EMAIL = "com.palm.app.email"
        const val BROWSER = "com.palm.app.browser"
        private val DELIMITERS = " ,._-:;()\\[]{}\"/".toSet()
        // Just Type's Util: what reads as an address, and as an email address.
        private val REG_URL = Regex("^((((http|https)://))|(www\\.))[-a-zA-Z0-9@:%_+.~#?&/=]+$")
        private val URL_DOMAIN = Regex("^([0-9a-zA-Z\\-]+\\.)+[a-zA-Z]{2,6}(:[0-9]+)?(\\S*)?$")
        private val EMAIL_RE = Regex("^([A-Za-z0-9_\\-.])+@([A-Za-z0-9_\\-.@])+\\.([A-Za-z]{2,4}) *$")
        // Util.Template's syntax: #{name}.
        private val TEMPLATE = Regex("#\\{\\s*(\\w+)\\s*\\}")
        /** The tab row's cells: the four filters in cells 1 to 4, empty cells at either end on a tablet. */
        const val TAB_CELLS = 6
        private fun cellOf(f: Filter) = f.ordinal + 1
        private fun filterAt(cell: Int): Filter? = Filter.values().getOrNull(cell - 1)
    }

    /** Which results the filter tabs show. */
    enum class Filter(val label: String) { ALL("All"), CONTACTS("Contacts"), CONTENT("Content"), ACTIONS("Actions") }

    private val phone = luna.phone
    var onLaunch: (AppInfo) -> Unit = {}
    /** A web search's or a typed address's page, for the Web app. */
    var onSearch: (url: String) -> Unit = {}
    /** applicationManager/launch, as Just Type made it. */
    var onLaunchApp: (id: String, params: JSONObject) -> Unit = { _, _ -> }
    /** applicationManager/open of a target (mailto: for an email address). */
    var onOpenTarget: (String) -> Unit = {}
    /** A db8 call, made as Just Type (com.palm.launcher). */
    var db: (method: String, params: JSONObject, reply: (JSONObject) -> Unit) -> Unit = { _, _, _ -> }
    var appFor: (String) -> AppInfo? = { null }
    var search: UniversalSearch? = null
        set(value) { field = value; value?.let { reload() } }
    var apps: List<AppInfo> = emptyList()

    // ---- state ----
    private var term = ""                        // what the results are for (after the debounce)
    private var searched = false                 // the tabs show once a search has run
    private var view = Filter.ALL
    private var actionsOnly = false              // an email address
    private var detail: String? = null           // the content group whose results are listed
    private var moreSearch = false
    private var moreActions = false
    private var results: List<AppInfo> = emptyList()
    private val dbResults = LinkedHashMap<String, List<JSONObject>>()
    private var dbFailedFor: String? = null      // DbSearch's invalidSearchString
    private var dbGeneration = 0
    private var scroll = 0f
    /** Where a finger is down on a row (rows are rebuilt with each draw, so by place, not by object). */
    private var pressedAt: android.graphics.PointF? = null
    private var pressedTab = -1
    val showing get() = visibility == VISIBLE && alpha > 0f

    // what the service says
    private var engines: List<UniversalSearch.Item> = emptyList()
    private var actions: List<UniversalSearch.Item> = emptyList()
    private var dbItems: List<UniversalSearch.Item> = emptyList()
    private var defaultEngine = ""
    private var appSearchOn = true
    private var defaultSearchOn = true

    private val handler = Handler(Looper.getMainLooper())
    private val runSearch = Runnable { search(field.text.toString()) }
    private val runDb = Runnable { searchDb() }

    private val field = object : EditText(context) { init { background = null } }.apply {
        setTextColor(Color.rgb(0x33, 0x33, 0x33))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, luna.px(20f))
        typeface = luna.fontMedium
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine()
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        // A plain Enter key, as the device's field has; Return does what is highlighted.
        imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        setOnEditorActionListener { _, _, _ -> enter(); true }
        setOnKeyListener { _, code, ev ->
            if (code == android.view.KeyEvent.KEYCODE_ENTER && ev.action == android.view.KeyEvent.ACTION_UP) { enter(); true }
            else code == android.view.KeyEvent.KEYCODE_ENTER
        }
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { typed(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = luna.px(14f); typeface = luna.fontBold }
    private val groupLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = luna.px(14f); typeface = luna.fontBold
        setShadowLayer(0.1f, 0f, luna.px(1f), Color.rgb(0x64, 0x64, 0x54))
    }
    private val rowText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = luna.px(18f); typeface = luna.fontMedium }
    private val subText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xB8, 0xE2, 0xFF); textSize = luna.px(16f); typeface = luna.fontMedium }
    private val countText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = luna.px(18f); typeface = luna.fontMedium; textAlign = Paint.Align.CENTER }
    private val tabText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = luna.px(15f); typeface = luna.fontBold; textAlign = Paint.Align.CENTER
        setShadowLayer(luna.px(1f), luna.px(1f), luna.px(1f), Color.BLACK)
    }
    private val emptyText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(128, 255, 255, 255); textSize = luna.px(20f); typeface = luna.fontMedium; textAlign = Paint.Align.CENTER }
    private val divDark = Paint().apply { color = Color.rgb(0x1d, 0x20, 0x23) }
    private val divLight = Paint().apply { color = Color.rgb(0x60, 0x64, 0x68) }
    private val held = Paint().apply { color = Color.rgb(0x21, 0x23, 0x25) }
    /** Icons are scaled from their TouchPad size to the screen's; filtered, as WebKit scaled them, or they come out jagged. */
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        visibility = INVISIBLE; alpha = 0f
        addView(field, LayoutParams(0, 0))
    }

    fun open(initial: String = "") {
        visibility = VISIBLE
        animate().cancel()
        animate().alpha(1f).setDuration(FADE_MS).setInterpolator(Easing.Linear).start()
        reload()
        field.setText(initial); field.setSelection(field.text.length)
        field.requestFocus()
        post { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, 0) }
    }

    fun close() {
        if (visibility != VISIBLE) return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
        field.clearFocus()
        animate().cancel()
        animate().alpha(0f).setDuration(FADE_MS).setInterpolator(Easing.Linear)
            .withEndAction { visibility = INVISIBLE; field.setText("") }.start()
    }

    /** The service's lists and settings changed (or Just Type is opening): read them again. */
    fun reload() {
        val s = search ?: return
        engines = s.list(UniversalSearch.SEARCH)
        actions = s.list(UniversalSearch.ACTION)
        dbItems = s.list(UniversalSearch.DBSEARCH)
        defaultEngine = s.defaultEngine()
        appSearchOn = s.preference("AppSearch") != "false"
        defaultSearchOn = s.preference("defaultSearch") != "false"
        dbResults.keys.retainAll(dbItems.filter { it.enabled }.map { it.id }.toSet())
        invalidate()
    }

    // ---- typing and searching ----

    private fun typed(text: String) {
        handler.removeCallbacks(runSearch)
        if (text.isEmpty()) {
            // JustType.clearSearchText: back to the start, tabs and all.
            handler.removeCallbacks(runDb)
            term = ""; searched = false; view = Filter.ALL; actionsOnly = false; detail = null
            moreSearch = false; moreActions = false; results = emptyList(); dbResults.clear(); dbFailedFor = null
            scroll = 0f; invalidate(); return
        }
        // updateEmailOrWebDiv runs on every keystroke from three characters.
        if (text.length >= 3) {
            if (EMAIL_RE.matches(text)) { actionsOnly = true; view = Filter.ACTIONS; detail = null }
            else if (actionsOnly) { actionsOnly = false; view = Filter.ALL }
        }
        handler.postDelayed(runSearch, SEARCH_DELAY_MS)
        invalidate()
    }

    private fun search(text: String) {
        if (text.isEmpty()) return
        val firstSearch = !searched
        searched = true
        term = text
        moreSearch = false; moreActions = false
        if (firstSearch) scroll = 0f
        val t = text.trim().lowercase()
        results = if (t.isEmpty() || !appSearchOn) emptyList() else {
            val byTitle = apps.filter { titleMatch(it.title, t) >= 0 }.sortedBy { it.title.lowercase() }
            val byKeyword = apps.filter { it !in byTitle }.filter { app ->
                val kw = app.keywords.map { it.lowercase() }
                (if (t.length >= 3) kw.any { it.startsWith(t) } else kw.any { it == t }) ||
                    app.title.lowercase().startsWith(t)
            }.sortedBy { it.title.lowercase() }
            byTitle + byKeyword
        }
        handler.removeCallbacks(runDb)
        val dbFailed = dbFailedFor
        if (text.length < DB_MIN || (dbFailed != null && text.startsWith(dbFailed))) {
            dbResults.clear(); dbGeneration++
            if (detail != null) { detail = null; view = Filter.CONTENT }
        } else {
            dbFailedFor = null
            handler.postDelayed(runDb, DB_DELAY_MS)
        }
        invalidate()
    }

    /** DbSearch.startSearchInDb: each content search that is on, with the words put in. */
    private fun searchDb() {
        val text = term
        val on = dbItems.filter { it.enabled }
        val gen = ++dbGeneration
        if (on.isEmpty()) return
        val got = LinkedHashMap<String, List<JSONObject>>()
        var left = on.size
        for (item in on) {
            val j = item.json
            val batch = j.optBoolean("batchQuery", false)
            val q = j.opt("dbQuery")
            if (q == null) { left--; continue }
            val params: JSONObject
            if (batch && q is JSONArray) {
                val ops = JSONArray(q.toString())
                // "It supports one field in the Where clause per query in the batch."
                for (i in 0 until ops.length()) ops.optJSONObject(i)?.optJSONObject("params")?.optJSONObject("query")
                    ?.optJSONArray("where")?.optJSONObject(0)?.put("val", text)
                params = JSONObject().put("operations", ops)
            } else if (q is JSONObject) {
                val query = JSONObject(q.toString())
                query.optJSONArray("where")?.let { w ->
                    for (i in 0 until w.length()) w.optJSONObject(i)?.let { c -> if (c.optString("val").isBlank() && c.opt("val") !is Boolean) c.put("val", text) }
                }
                params = JSONObject().put("query", query)
            } else { left--; continue }
            db(if (batch) "batch" else "search", params) { r ->
                post {
                    if (gen != dbGeneration) return@post
                    val list = ArrayList<JSONObject>()
                    if (r.optBoolean("returnValue", false)) {
                        r.optJSONArray("responses")?.let { rs ->
                            for (i in 0 until rs.length()) rs.optJSONObject(i)?.optJSONArray("results")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let(list::add) }
                        } ?: r.optJSONArray("results")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let(list::add) }
                    }
                    got[item.id] = list
                    if (--left <= 0) dbDone(on, got)
                }
            }
        }
        if (left <= 0) dbDone(on, got)
    }

    private fun dbDone(on: List<UniversalSearch.Item>, got: Map<String, List<JSONObject>>) {
        dbResults.clear()
        for (item in on) got[item.id]?.let { dbResults[item.id] = it }
        if (dbResults.values.all { it.isEmpty() }) dbFailedFor = term
        // The results being looked at went away: back to the content groups.
        detail?.let { if (dbResults[it].isNullOrEmpty()) { detail = null; view = Filter.CONTENT } }
        invalidate()
    }

    /** LaunchPoint::matchesTitle: the title starts with it, or a word in it does. */
    private fun titleMatch(title: String, term: String): Int {
        val t = title.lowercase()
        if (t.startsWith(term)) return 0
        var i = t.indexOf(term, 1)
        while (i > 0) {
            if (t[i - 1] in DELIMITERS) return i
            i = t.indexOf(term, i + 1)
        }
        return -1
    }

    private fun isUrl(s: String) = REG_URL.matches(s) || URL_DOMAIN.matches(s)
    private fun showGoToWeb() = term.length >= 3 && isUrl(term) && !actionsOnly

    // ---- launching, as Palm's code builds each call ----

    private fun encoded(s: String) = android.net.Uri.encode(s, "-_.!~*'()")   // encodeURIComponent

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

    /** LaunchAndSearch.handleLaunchAndSearchSelected. */
    private fun searchWith(item: UniversalSearch.Item) {
        val words = encoded(term)
        if (item.type == "web" || item.type == "opensearch") {
            onSearch(evaluate(item.url, words, false))
            return
        }
        val params = if (launchObject(item.launchParam) != null)
            runCatching { JSONObject(evaluate(item.launchParam, words, true)) }.getOrNull() ?: return
        else JSONObject().put(item.launchParam, words)
        onLaunchApp(item.url, params)
    }

    /** Actions.handleActionItemSelected: the words as typed, not encoded. */
    private fun act(item: UniversalSearch.Item) {
        if (item.url == EMAIL && actionsOnly) { onOpenTarget("mailto:$term"); return }
        val params = if (launchObject(item.launchParam) != null)
            runCatching { JSONObject(evaluate(item.launchParam, term, true)) }.getOrNull() ?: return
        else JSONObject().put(item.launchParam, term)
        onLaunchApp(item.url, params)
    }

    /** DbSearch.handleContentItemTap: the app, with the result's field under its launchParam. */
    private fun openResult(item: UniversalSearch.Item, result: JSONObject) {
        val value = fieldOf(result, item.json.optString("launchParamDbField")) ?: return
        onLaunchApp(item.id, JSONObject().put(item.launchParam, value))
    }

    private fun defaultItem(): UniversalSearch.Item? =
        if (!defaultSearchOn) null else engines.firstOrNull { it.id == defaultEngine && it.type != "app" }

    private fun enter() {
        handler.removeCallbacks(runSearch)
        val text = field.text.toString()
        if (text.isEmpty()) return
        if (text != term || !searched) search(text)
        if (showGoToWeb()) { onSearch(term); return }
        if (view != Filter.CONTENT && view != Filter.CONTACTS) results.firstOrNull()?.let { onLaunch(it); return }
        defaultItem()?.let { searchWith(it) }
    }

    // ---- layout: groups of rows, top to bottom ----

    private class Row(val h: Float, val draw: (Canvas, RectF) -> Unit, val tap: (() -> Unit)?) {
        var rect = RectF()
    }
    private class Group(val caption: String?, val rows: List<Row>, val launch: Boolean = false, val focused: Boolean = false) {
        var rect = RectF()
    }

    private fun groups(): List<Group> {
        if (!searched) return emptyList()
        val out = ArrayList<Group>()
        detail?.let { id ->
            val item = dbItems.firstOrNull { it.id == id } ?: return out
            val list = dbResults[id].orEmpty()
            out += Group("${item.displayName} (${list.size})", list.map { r -> resultRow(item, r, CONTENT_ROW_H, 32f, 52f, true) })
            return out
        }
        if (view == Filter.CONTACTS) return out
        if (actionsOnly) { actionGroup()?.let { out += it }; return out }
        val all = view == Filter.ALL
        val goToWeb = all && showGoToWeb()
        if (goToWeb) out += Group(null, listOf(Row(px(WEB_ROW_H), { c, r -> drawWebRow(c, r) }, { onSearch(term) })), focused = true)
        if ((all || view == Filter.ACTIONS) && results.isNotEmpty()) out += Group("Launch", emptyList(), launch = true)
        if (all || view == Filter.CONTENT) out += contentGroups()
        if (all || view == Filter.ACTIONS) {
            defaultItem()?.let { d ->
                out += Group(null, listOf(Row(px(ROW_H), { c, r -> drawItemRow(c, r, iconFor(d), "Search ${d.displayName}") }, { searchWith(d) })),
                    focused = !goToWeb && results.isEmpty())
            }
            val others = engines.filter { it.enabled && it.id != defaultEngine }
            if (others.isNotEmpty()) out += Group("Search Using...", limited(others, moreSearch, { moreSearch = true }) { e ->
                Row(px(ROW_H), { c, r -> drawItemRow(c, r, iconFor(e), e.displayName) }, { searchWith(e) })
            })
            actionGroup()?.let { out += it }
        }
        return out
    }

    private fun actionGroup(): Group? {
        val on = actions.filter { it.enabled }
        if (on.isEmpty()) return null
        return Group("Quick Actions", limited(on, moreActions, { moreActions = true }) { a ->
            Row(px(ROW_H), { c, r -> drawItemRow(c, r, iconFor(a), a.displayName) }, { act(a) })
        })
    }

    /** Four, then More… when there is more than one left (populateLaunchAndSearchList). */
    private fun limited(items: List<UniversalSearch.Item>, all: Boolean, more: () -> Unit, row: (UniversalSearch.Item) -> Row): List<Row> {
        if (all || items.size <= LIMIT + 1) return items.map(row)
        return items.take(LIMIT).map(row) + Row(px(ROW_H), { c, r -> drawItemRow(c, r, luna.image("justtype/search-icon-new.png"), "More...") }, { more(); invalidate() })
    }

    private fun contentGroups(): List<Group> {
        val out = ArrayList<Group>()
        for (item in dbItems) {
            if (!item.enabled) continue
            val list = dbResults[item.id] ?: continue
            if (list.isEmpty()) continue
            val inline = item.id == EMAIL && list.size <= INLINE_MAX
            val head = Row(px(if (inline) DB_HEAD_H else DB_ROW_H), { c, r -> drawDbHead(c, r, item, list.size) }, { showDetail(item.id) })
            out += Group(null, listOf(head) + if (inline) list.map { resultRow(item, it, DRAWER_ROW_H, 30f, 50f, false) } else emptyList())
        }
        return out
    }

    private fun showDetail(id: String) { detail = id; view = Filter.CONTENT; scroll = 0f; invalidate() }

    private fun resultRow(item: UniversalSearch.Item, r: JSONObject, h: Float, l1: Float, l2: Float, kindIcon: Boolean) =
        Row(px(h), { c, rect -> drawResult(c, rect, item, r, l1, l2, kindIcon) }, { openResult(item, r) })

    private fun px(v: Float) = luna.px(v)
    private fun fieldW() = if (phone) width - 2 * px(PHONE_MARGIN) else px(FIELD_W)
    private fun groupW() = if (phone) width - 2 * px(PHONE_MARGIN) else px(GROUP_W)
    private fun fieldRect() = RectF((width - fieldW()) / 2, px(FIELD_TOP), (width + fieldW()) / 2, px(FIELD_TOP) + px(36f))
    private fun clearRect(): RectF { val f = fieldRect(); val s = px(20f); return RectF(f.right - px(18f) - s, f.centerY() - s / 2, f.right - px(18f), f.centerY() + s / 2) }
    private fun groupLeft() = (width - groupW()) / 2
    private fun topOfList() = px(HEADER_H) + (if (searched) px(TABS_H) else 0f)
    private fun phoneColumns(): Pair<Int, Float> {
        val inner = groupW() - 2 * px(14f)
        val n = maxOf(1, ((inner - px(PHONE_TILE_GAP)) / (px(TILE_W) + px(PHONE_TILE_GAP))).toInt())
        return n to (inner - n * px(TILE_W)) / (n + 1)
    }
    private fun perRow() = if (phone) phoneColumns().first else maxOf(1, ((px(GROUP_W) - px(TILE_LEFT)) / px(TILE_PITCH_X)).toInt())
    private fun tileLeft() = if (phone) px(14f) + phoneColumns().second else px(TILE_LEFT)
    private fun tilePitchX() = if (phone) px(TILE_W) + phoneColumns().second else px(TILE_PITCH_X)
    private fun launchHeight(): Float {
        val rows = (results.size + perRow() - 1) / perRow()
        return px(TILE_TOP) + rows * px(TILE_H) + (rows - 1) * px(TILE_PITCH_Y - TILE_H) + px(27f)
    }

    /** Places every group and row at the current scroll; returns the bottom of the last one. */
    private fun place(gs: List<Group>): Float {
        var y = topOfList() + px(if (detail != null) DETAIL_TOP else FIRST_TOP) - scroll
        var prev: Group? = null
        for (g in gs) {
            if (prev != null) y += px(if (prev.launch) GAP_AFTER_LAUNCH else if (g.caption != null) GAP_LABELED else GAP)
            val top = y
            if (g.launch) {
                y += launchHeight()
            } else {
                if (g.caption != null) y += px(CAPTION_H)
                g.rows.forEachIndexed { i, row ->
                    val h = if (i == 0 && g.caption != null) row.h - px(CAPTION_OVERLAP) else row.h
                    row.rect = RectF(groupLeft(), y, groupLeft() + groupW(), y + h)
                    y += h
                }
            }
            g.rect = RectF(groupLeft(), top, groupLeft() + groupW(), y)
            prev = g
        }
        return y + scroll
    }

    private fun tileRect(g: Group, i: Int): RectF {
        val l = groupLeft() + tileLeft() + (i % perRow()) * tilePitchX()
        val t = g.rect.top + px(TILE_TOP) + (i / perRow()) * px(TILE_PITCH_Y)
        return RectF(l, t, l + px(TILE_W), t + px(TILE_H))
    }

    /** Six cells on a tablet, the outer two empty; on a phone the four tabs alone span the width. */
    private fun tabRect(i: Int): RectF {
        if (phone) {
            if (filterAt(i) == null) return RectF()
            val w = width / Filter.values().size.toFloat()
            return RectF((i - 1) * w, px(HEADER_H), i * w, px(HEADER_H) + px(TABS_H))
        }
        val w = width / TAB_CELLS.toFloat(); return RectF(i * w, px(HEADER_H), (i + 1) * w, px(HEADER_H) + px(TABS_H))
    }

    /** The TouchPad's 15 px, or on a phone too narrow for it, the size at which the widest label fits its cell. */
    private fun tabTextSize(): Float {
        val full = px(15f)
        if (!phone) return full
        tabText.textSize = full
        val widest = Filter.values().maxOf { tabText.measureText(it.label.uppercase()) }
        val room = width / 4f - px(TAB_PAD)
        return if (widest <= room) full else full * room / widest
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val f = fieldRect()
        field.layout((f.left + px(14f)).toInt(), f.top.toInt(), (clearRect().left - px(6f)).toInt(), f.bottom.toInt())
    }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, h)
        val f = fieldRect()
        field.measure(MeasureSpec.makeMeasureSpec((clearRect().left - px(6f) - f.left - px(14f)).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(f.height().toInt(), MeasureSpec.EXACTLY))
    }

    // ---- drawing ----

    private var laidOut: List<Group> = emptyList()

    override fun onDraw(c: Canvas) {
        luna.tile(c, "justtype/bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()))
        val gs = groups()
        laidOut = gs
        place(gs)
        c.save(); c.clipRect(0f, topOfList(), width.toFloat(), height.toFloat())
        val text = term.trim().lowercase()
        for (g in gs) drawGroup(c, g, text)
        if (searched && gs.isEmpty() && (view == Filter.CONTENT || view == Filter.CONTACTS)) drawNoResults(c)
        c.restore()
        // The header and the tabs over what scrolls under them, and their shadow.
        luna.tile(c, "justtype/search-header-bg.png", RectF(0f, 0f, width.toFloat(), px(HEADER_H)))
        if (searched) drawTabs(c)
        luna.tile(c, "justtype/search-header-shadow.png", RectF(0f, topOfList(), width.toFloat(), topOfList() + px(SHADOW_H)))
        luna.nine(c, "justtype/roundedbox.png", fieldRect(), 18, 18, 18, 17)
        if (field.text.isNotEmpty()) luna.image("justtype/search-input-cancel.png")?.let { c.drawBitmap(it, null, clearRect(), iconPaint) }
    }

    private fun drawGroup(c: Canvas, g: Group, term: String) {
        if (g.rect.bottom < topOfList() || g.rect.top > height) return
        if (g.launch) { drawLaunch(c, g, term); return }
        if (g.caption != null) {
            luna.nine(c, "justtype/group-labeled.png", g.rect, 14, 36, 14, 14)
            c.drawText(g.caption.uppercase(), g.rect.left + px(12f), g.rect.top + px(19f), groupLabel)
        } else luna.nine(c, "justtype/group-unlabeled.png", g.rect, 14, 14, 14, 14)
        g.rows.forEachIndexed { i, row ->
            val r = row.rect
            if (pressedAt?.let { row.tap != null && r.contains(it.x, it.y) } == true) c.drawRect(r.left + px(1f), r.top, r.right - px(1f), r.bottom, held)
            // .enyo-item's borders: #606468 above each row, #1d2023 below, between rows.
            if (i > 0) {
                c.drawRect(r.left + px(1f), r.top - px(1f), r.right - px(1f), r.top, divDark)
                c.drawRect(r.left + px(1f), r.top, r.right - px(1f), r.top + px(1f), divLight)
            }
            row.draw(c, r)
        }
        if (g.focused) luna.nine(c, "justtype/groupbox-focus.png", g.rect, 6, 6, 6, 6)
    }

    private fun drawLaunch(c: Canvas, g: Group, term: String) {
        val box = g.rect
        luna.nine(c, "justtype/group-labeled.png", box, 14, 36, 14, 14)
        c.drawText("LAUNCH", box.left + px(12f), box.top + px(19f), groupLabel)
        results.forEachIndexed { i, app ->
            val r = tileRect(g, i)
            luna.nine(c, if (i == 0) "justtype/groupbox-app-focus.png" else "justtype/groupbox-app-focus-empty.png", r, 6, 6, 6, 6)
            val half = px(32f); val cx = r.centerX(); val iy = r.top + px(37f)
            (luna.appIcon(app) ?: luna.image("default-app-icon.png"))?.let { c.drawBitmap(it, null, RectF(cx - half, iy - half, cx + half, iy + half), iconPaint) }
            drawTitle(c, app.title, term, cx, r.top + px(88f), px(75f))
        }
    }

    /** The title, at most 75 px and elided, with the matched start underlined (Util.highlightString). */
    private fun drawTitle(c: Canvas, title: String, term: String, cx: Float, baseline: Float, maxW: Float) {
        val t = TextUtils.ellipsize(title, TextPaint(label), maxW, TextUtils.TruncateAt.END).toString()
        val left = cx - label.measureText(t) / 2
        c.drawText(t, left, baseline, label)
        val at = titleMatch(t, term)
        if (at < 0) return
        val x0 = left + label.measureText(t, 0, at)
        val x1 = left + label.measureText(t, 0, minOf(t.length, at + term.length))
        luna.tile(c, "justtype/underline.png", RectF(x0, baseline + px(1f), x1, baseline + px(4f)))
    }

    private fun centreBaseline(r: RectF, p: Paint) = r.centerY() - (p.ascent() + p.descent()) / 2

    /** A 48-px list icon, 10 px in, and the label at 69 px, as LaunchAndSearchItem and ActionItem lay out. */
    private fun drawItemRow(c: Canvas, r: RectF, icon: Bitmap?, text: String) {
        val s = px(48f); val l = r.left + px(10f)
        icon?.let { c.drawBitmap(it, null, RectF(l, r.centerY() - s / 2, l + s, r.centerY() + s / 2), iconPaint) }
        val t = TextUtils.ellipsize(text, TextPaint(rowText), px(600f).coerceAtMost(r.right - r.left - px(81f)), TextUtils.TruncateAt.END).toString()
        c.drawText(t, r.left + px(69f), centreBaseline(r, rowText), rowText)
    }

    private fun drawWebRow(c: Canvas, r: RectF) {
        val s = px(32f); val l = r.left + px(12f)
        luna.image("justtype/goto-icon-32.png")?.let { c.drawBitmap(it, null, RectF(l, r.centerY() - s / 2, l + s, r.centerY() + s / 2), iconPaint) }
        c.drawText("Go to website", l + s + px(10f), centreBaseline(r, rowText), rowText)
    }

    /** DbSearchItem: its icon, its name, and the count in filter-field-pill.png. */
    private fun drawDbHead(c: Canvas, r: RectF, item: UniversalSearch.Item, count: Int) {
        val s = px(48f); val l = r.left + px(10f)
        val box = RectF(l, r.centerY() - s / 2, l + s, r.centerY() + s / 2)
        val fixed = when (item.id) { EMAIL, "com.palm.app.enyo-email" -> "justtype/icon-email.png"; BROWSER -> "justtype/go-url.png"; else -> null }
        if (fixed != null) luna.image(fixed)?.let { c.drawBitmap(it, null, box, iconPaint) }
        else {
            // A background image, not scaled: the app's 64-px icon, its middle 48 px showing.
            val icon = appFor(item.id)?.let { luna.appIcon(it) } ?: luna.image("justtype/calendar.png")
            icon?.let { b ->
                val w = px(64f); c.save(); c.clipRect(box)
                c.drawBitmap(b, null, RectF(box.centerX() - w / 2, box.centerY() - w / 2, box.centerX() + w / 2, box.centerY() + w / 2), iconPaint)
                c.restore()
            }
        }
        c.drawText(item.displayName, r.left + px(69f), centreBaseline(r, rowText), rowText)
        val n = count.toString()
        val w = maxOf(px(25f), countText.measureText(n) + px(22f))
        val pill = RectF(r.right - px(11f) - w, r.centerY() - px(13f), r.right - px(11f), r.centerY() + px(13f))
        luna.nine(c, "justtype/filter-field-pill.png", pill, 11, 11, 11, 11)
        c.drawText(n, pill.centerX(), centreBaseline(pill, countText), countText)
    }

    /** ContentItem: up to two of the item's display fields, the typed words underlined. */
    private fun drawResult(c: Canvas, r: RectF, item: UniversalSearch.Item, result: JSONObject, l1: Float, l2: Float, kindIcon: Boolean) {
        val fields = item.json.optJSONArray("displayFields") ?: JSONArray()
        val lines = ArrayList<String>()
        for (i in 0 until minOf(2, fields.length())) {
            val f = fields.opt(i)
            val name = (f as? JSONObject)?.optString("name") ?: f.toString()
            var v = fieldOf(result, name)?.toString().orEmpty()
            if ((f as? JSONObject)?.optString("type") == "timestamp") v.toLongOrNull()?.let { ms ->
                // DateFmt({date:"long", time:"short", twelveHourFormat:true}).
                v = java.text.SimpleDateFormat("MMMM d, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
            }
            lines += v
        }
        // The first row under a caption is shorter by the caption's overlap.
        val top = if (r.height() < px(CONTENT_ROW_H) - 1 && kindIcon) r.top - px(CAPTION_OVERLAP) else r.top
        val maxW = px(500f)
        val words = term.trim()
        lines.getOrNull(0)?.let { drawHighlighted(c, it, r.left + px(12f), top + px(l1), maxW, rowText, words) }
        lines.getOrNull(1)?.let { drawHighlighted(c, it, r.left + px(12f), top + px(l2), maxW, subText, words) }
        if (kindIcon) {
            val icon = when (result.optString("_kind")) {
                "com.palm.browserbookmarks:1" -> "justtype/bookmark-icon-48.png"
                "com.palm.browserhistory:1" -> "justtype/history-icon-48.png"
                else -> null
            }
            icon?.let { luna.image(it) }?.let { b ->
                val s = px(48f); val rr = r.right - px(12f)
                c.drawBitmap(b, null, RectF(rr - s, r.centerY() - s / 2, rr, r.centerY() + s / 2), iconPaint)
            }
        }
    }

    /** Util.highlightString: every word that starts with the typed text, underlined. */
    private fun drawHighlighted(c: Canvas, text: String, x: Float, baseline: Float, maxW: Float, p: Paint, words: String) {
        val t = TextUtils.ellipsize(text, TextPaint(p), maxW, TextUtils.TruncateAt.END).toString()
        c.drawText(t, x, baseline, p)
        if (words.isEmpty()) return
        val re = runCatching { Regex("\\b(" + Regex.escape(words) + ")", RegexOption.IGNORE_CASE) }.getOrNull() ?: return
        for (m in re.findAll(t)) {
            val x0 = x + p.measureText(t, 0, m.range.first)
            val x1 = x + p.measureText(t, 0, m.range.last + 1)
            luna.tile(c, "justtype/underline.png", RectF(x0, baseline + px(1f), x1, baseline + px(4f)))
        }
    }

    /** A field of a db8 result by its dotted name ("from.name"). */
    private fun fieldOf(o: JSONObject, name: String): Any? {
        var cur: Any? = o
        for (part in name.split('.')) cur = (cur as? JSONObject)?.opt(part) ?: return null
        return cur
    }

    private fun iconFor(item: UniversalSearch.Item): Bitmap? = when {
        item.type == "web" -> luna.image("justtype/search-icon-${item.id}.png") ?: luna.image("justtype/search-icon-generic.png")
        item.type == "opensearch" -> luna.image("justtype/search-icon-generic.png")
        else -> appFor(item.id)?.let { luna.appIcon(it) } ?: luna.image("justtype/search-icon-generic.png")
    }

    private fun drawTabs(c: Canvas) {
        val selected = cellOf(if (actionsOnly) Filter.ACTIONS else view)
        tabText.textSize = tabTextSize()
        for (i in 0 until TAB_CELLS) {
            if (phone && filterAt(i) == null) continue
            val r = tabRect(i)
            // tab-button.png: the plain cell in rows 0-46, pressed (blue) in 49-95, chosen in 99-145.
            val rows = when (i) { pressedTab -> 49; selected -> 99; else -> 0 }
            drawTabCell(c, r, rows)
            if (filterAt(i) != null) {
                tabText.color = if (i == selected || i == pressedTab) Color.WHITE else Color.rgb(0xc8, 0xc8, 0xc8)
                c.drawText(filterAt(i)!!.label.uppercase(), r.centerX(), r.top + px(31f), tabText)
            }
        }
    }

    /** One row band of tab-button.png, its 34-px ends kept and its middle stretched. */
    private fun drawTabCell(c: Canvas, r: RectF, top: Int) {
        val b = luna.image("justtype/tab-button.png") ?: return
        val k = b.width / 80f
        val y0 = (top * k).toInt(); val y1 = ((top + TABS_H) * k).toInt().coerceAtMost(b.height)
        val cap = (34 * k).toInt(); val capW = px(34f)
        c.drawBitmap(b, Rect(0, y0, cap, y1), RectF(r.left, r.top, r.left + capW, r.bottom), iconPaint)
        c.drawBitmap(b, Rect(cap, y0, b.width - cap, y1), RectF(r.left + capW, r.top, r.right - capW, r.bottom), iconPaint)
        c.drawBitmap(b, Rect(b.width - cap, y0, b.width, y1), RectF(r.right - capW, r.top, r.right, r.bottom), iconPaint)
    }

    /** "No Search Results Found" under empty-search.png, as DbSearch and ContactSearch show it. */
    private fun drawNoResults(c: Canvas) {
        val s = px(176f)
        val top = topOfList() + px(24f) + px(100f)
        luna.image("justtype/empty-search.png")?.let { c.drawBitmap(it, null, RectF(width / 2f - s / 2, top, width / 2f + s / 2, top + s), iconPaint) }
        c.drawText("No Search Results Found", width / 2f, top + s + px(100f) - px(80f) - emptyText.ascent(), emptyText)
    }

    // ---- touch ----

    private var downX = 0f; private var downY = 0f; private var lastY = 0f; private var dragging = false

    private fun rowAt(x: Float, y: Float): Row? {
        if (y < topOfList()) return null
        for (g in laidOut) for (row in g.rows) if (row.rect.contains(x, y)) return row
        return null
    }

    private fun tabAt(x: Float, y: Float): Int =
        if (!searched || y < px(HEADER_H) || y >= topOfList()) -1 else (0 until TAB_CELLS).firstOrNull { tabRect(it).contains(x, y) }?.takeIf { filterAt(it) != null } ?: -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastY = e.y; dragging = false
                pressedTab = tabAt(e.x, e.y)
                pressedAt = if (pressedTab < 0 && rowAt(e.x, e.y)?.tap != null) android.graphics.PointF(e.x, e.y) else null
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && Math.abs(e.y - downY) > px(25f)) {
                    dragging = true; pressedAt = null; pressedTab = -1
                }
                if (dragging) {
                    val content = place(laidOut) + px(GAP_LABELED)
                    scroll = (scroll - (e.y - lastY)).coerceIn(0f, maxOf(0f, content - height))
                    invalidate()
                }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                val tab = pressedTab
                pressedAt = null; pressedTab = -1
                if (!dragging) {
                    if (tab >= 0 && tabAt(e.x, e.y) == tab) chooseTab(tab) else tap(e.x, e.y)
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedAt = null; pressedTab = -1; invalidate() }
        }
        return true
    }

    /** JustType.tabClickHandler → MainView.showView. */
    private fun chooseTab(tab: Int) {
        actionsOnly = false
        detail = null
        moreSearch = false; moreActions = false
        view = filterAt(tab) ?: Filter.ALL
        scroll = 0f
    }

    private fun tap(x: Float, y: Float) {
        if (clearRect().contains(x, y)) { field.setText(""); return }
        if (y < topOfList()) return
        for (g in laidOut) {
            if (g.launch) {
                results.indices.firstOrNull { tileRect(g, it).contains(x, y) }?.let { onLaunch(results[it]); return }
                continue
            }
            for (row in g.rows) if (row.rect.contains(x, y)) { row.tap?.invoke(); return }
        }
    }
}
