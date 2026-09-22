package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import org.webosarchive.lunacy.card.AppInfo

/**
 * Just Type, the part of it in use today: the launch results and "Search DuckDuckGo". On a
 * device this is Palm's own Enyo app (com.palm.launcher, luna-applauncher) in a window under
 * the status bar; here it is drawn natively to that app's measurements and with its own art,
 * as measured on the reference TouchPad with "c" typed:
 *
 * - the header (search-header-bg.png) with the 570-px field (roundedbox.png) and its clear
 *   button, 13 px from the top;
 * - a LAUNCH group (Enyo's labeled group) of 85 × 113 app tiles, the first one highlighted
 *   (groupbox-app-focus.png), the matched start of each title underlined (underline.png);
 * - a group with "Search DuckDuckGo", which opens the device's own search URL for it.
 *
 * The filter tabs (ALL, CONTACTS, CONTENT, ACTIONS), "Search using…" and the rest of Just
 * Type are out of scope for now (codepoet, 2026-09-22).
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
        const val HEADER_H = 62f
        const val FIELD_W = 570f
        const val FIELD_TOP = 13f
        const val GROUP_W = 736f                 // 708 plus the 14-px borders
        const val GROUP_TOP = 33f                // below the header (24 + 8 + the frame's edge)
        const val GROUP_GAP = 28f
        const val TILE_W = 85f
        const val TILE_H = 113f
        const val TILE_LEFT = 40f
        const val TILE_TOP = 48f
        const val TILE_PITCH_X = 138f            // 28 + 85 + 25
        const val TILE_PITCH_Y = 138f            // 10 + 113 + 15
        const val SEARCH_H = 74f
        /** The reference TouchPad's DuckDuckGo entry (com.palm.universalsearch). */
        const val DUCKDUCKGO = "https://lite.duckduckgo.com/lite/?q="
        private val DELIMITERS = " ,._-:;()\\[]{}\"/".toSet()
    }

    var onLaunch: (AppInfo) -> Unit = {}
    var onSearch: (url: String) -> Unit = {}
    var apps: List<AppInfo> = emptyList()

    private var results: List<AppInfo> = emptyList()
    private var scroll = 0f
    val showing get() = visibility == VISIBLE && alpha > 0f

    private val field = object : EditText(context) { init { background = null } }.apply {
        setTextColor(Color.rgb(0x33, 0x33, 0x33))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, luna.px(20f))
        typeface = luna.fontMedium
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine()
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        // A plain Enter key, as the device's field has; Return launches what is highlighted:
        // the first app, else the search.
        imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        setOnEditorActionListener { _, _, _ -> enter(); true }
        setOnKeyListener { _, code, ev ->
            if (code == android.view.KeyEvent.KEYCODE_ENTER && ev.action == android.view.KeyEvent.ACTION_UP) { enter(); true }
            else code == android.view.KeyEvent.KEYCODE_ENTER
        }
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { search(s.toString()) }
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

    init {
        setWillNotDraw(false)
        visibility = INVISIBLE; alpha = 0f
        addView(field, LayoutParams(0, 0))
    }

    fun open(initial: String = "") {
        visibility = VISIBLE
        animate().cancel()
        animate().alpha(1f).setDuration(FADE_MS).setInterpolator(Easing.Linear).start()
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

    // ---- matching (ApplicationManager::searchLaunchPoints) ----

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

    private fun search(text: String) {
        val term = text.trim().lowercase()
        scroll = 0f
        results = if (term.isEmpty()) emptyList() else {
            val byTitle = apps.filter { titleMatch(it.title, term) >= 0 }.sortedBy { it.title.lowercase() }
            val byKeyword = apps.filter { it !in byTitle }.filter { app ->
                val kw = app.keywords.map { it.lowercase() }
                (if (term.length >= 3) kw.any { it.startsWith(term) } else kw.any { it == term }) ||
                    app.title.lowercase().startsWith(term)
            }.sortedBy { it.title.lowercase() }
            byTitle + byKeyword
        }
        invalidate()
    }

    private fun enter() {
        val text = field.text.toString().trim()
        if (text.isEmpty()) return
        results.firstOrNull()?.let { onLaunch(it); return }
        onSearch(DUCKDUCKGO + android.net.Uri.encode(text))
    }

    // ---- layout ----

    private fun px(v: Float) = luna.px(v)
    private fun fieldRect() = RectF((width - px(FIELD_W)) / 2, px(FIELD_TOP), (width + px(FIELD_W)) / 2, px(FIELD_TOP) + px(36f))
    private fun clearRect(): RectF { val f = fieldRect(); val s = px(20f); return RectF(f.right - px(18f) - s, f.centerY() - s / 2, f.right - px(18f), f.centerY() + s / 2) }
    private fun groupLeft() = (width - px(GROUP_W)) / 2
    private fun perRow() = maxOf(1, ((px(GROUP_W) - px(TILE_LEFT)) / px(TILE_PITCH_X)).toInt())
    private fun launchTop() = px(HEADER_H) + px(GROUP_TOP) - scroll
    private fun launchHeight(): Float {
        val rows = (results.size + perRow() - 1) / perRow()
        return px(TILE_TOP) + rows * px(TILE_H) + (rows - 1) * px(TILE_PITCH_Y - TILE_H) + px(27f)
    }
    private fun searchTop() = if (results.isEmpty()) launchTop() else launchTop() + launchHeight() + px(GROUP_GAP)
    private fun tileRect(i: Int): RectF {
        val l = groupLeft() + px(TILE_LEFT) + (i % perRow()) * px(TILE_PITCH_X)
        val t = launchTop() + px(TILE_TOP) + (i / perRow()) * px(TILE_PITCH_Y)
        return RectF(l, t, l + px(TILE_W), t + px(TILE_H))
    }
    private fun searchRect() = RectF(groupLeft(), searchTop(), groupLeft() + px(GROUP_W), searchTop() + px(SEARCH_H))

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

    override fun onDraw(c: Canvas) {
        luna.tile(c, "justtype/bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()))
        val text = field.text.toString().trim()
        c.save(); c.clipRect(0f, px(HEADER_H), width.toFloat(), height.toFloat())
        if (text.isNotEmpty()) {
            if (results.isNotEmpty()) drawLaunch(c, text.lowercase())
            drawSearch(c)
        }
        c.restore()
        // The header over what scrolls under it, and its shadow.
        luna.tile(c, "justtype/search-header-bg.png", RectF(0f, 0f, width.toFloat(), px(HEADER_H)))
        luna.tile(c, "justtype/search-header-shadow.png", RectF(0f, px(HEADER_H), width.toFloat(), px(HEADER_H) + px(10f)))
        luna.nine(c, "justtype/roundedbox.png", fieldRect(), 18, 18, 18, 17)
        if (text.isNotEmpty()) luna.image("justtype/search-input-cancel.png")?.let { c.drawBitmap(it, null, clearRect(), null) }
    }

    private fun drawLaunch(c: Canvas, term: String) {
        val top = launchTop()
        val box = RectF(groupLeft(), top, groupLeft() + px(GROUP_W), top + launchHeight())
        luna.nine(c, "justtype/group-labeled.png", box, 14, 36, 14, 14)
        c.drawText("LAUNCH", box.left + px(12f), top + px(19f), groupLabel)
        results.forEachIndexed { i, app ->
            val r = tileRect(i)
            luna.nine(c, if (i == 0) "justtype/groupbox-app-focus.png" else "justtype/groupbox-app-focus-empty.png", r, 6, 6, 6, 6)
            val half = px(32f); val cx = r.centerX(); val iy = r.top + px(37f)
            (luna.appIcon(app) ?: luna.image("default-app-icon.png"))?.let { c.drawBitmap(it, null, RectF(cx - half, iy - half, cx + half, iy + half), null) }
            drawTitle(c, app.title, term, cx, r.top + px(88f), px(75f))
        }
    }

    /** The title, at most 75 px and elided, with the matched start underlined (Util.highlightString). */
    private fun drawTitle(c: Canvas, title: String, term: String, cx: Float, baseline: Float, maxW: Float) {
        val t = android.text.TextUtils.ellipsize(title, android.text.TextPaint(label), maxW, android.text.TextUtils.TruncateAt.END).toString()
        val left = cx - label.measureText(t) / 2
        c.drawText(t, left, baseline, label)
        val at = titleMatch(t, term)
        if (at < 0) return
        val x0 = left + label.measureText(t, 0, at)
        val x1 = left + label.measureText(t, 0, minOf(t.length, at + term.length))
        luna.tile(c, "justtype/underline.png", RectF(x0, baseline + px(1f), x1, baseline + px(4f)))
    }

    private fun drawSearch(c: Canvas) {
        val r = searchRect()
        luna.nine(c, "justtype/group-unlabeled.png", r, 14, 14, 14, 14)
        val s = px(40f)
        luna.image("justtype/search-icon-duckduckgo.png")?.let { c.drawBitmap(it, null, RectF(r.left + px(15f), r.centerY() - s / 2, r.left + px(15f) + s, r.centerY() + s / 2), null) }
        c.drawText("Search DuckDuckGo", r.left + px(69f), r.centerY() - (rowText.ascent() + rowText.descent()) / 2, rowText)
    }

    // ---- touch ----

    private var downY = 0f; private var lastY = 0f; private var dragging = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.y; lastY = e.y; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && Math.abs(e.y - downY) > px(25f)) dragging = true
                if (dragging) {
                    val content = searchTop() + scroll + px(SEARCH_H) + px(GROUP_GAP)
                    scroll = (scroll - (e.y - lastY)).coerceIn(0f, maxOf(0f, content - height))
                    invalidate()
                }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> if (!dragging) tap(e.x, e.y)
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        if (clearRect().contains(x, y)) { field.setText(""); return }
        if (y < px(HEADER_H)) return
        val text = field.text.toString().trim()
        if (text.isEmpty()) return
        results.indices.firstOrNull { tileRect(it).contains(x, y) }?.let { onLaunch(results[it]); return }
        if (searchRect().contains(x, y)) onSearch(DUCKDUCKGO + android.net.Uri.encode(text))
    }
}
