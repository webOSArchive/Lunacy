package org.webosarchive.lunacy.shell

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import org.json.JSONArray
import org.webosarchive.lunacy.card.AppInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The launcher ("launcher3"): a tab bar over pages of app icons, sliding up over the cards.
 * Geometry and timings: docs/luna-shell-reference.md §3. Lengths in TouchPad px.
 *
 * A tapped icon glows while its app launches (LunaCE's launch feedback). Holding an icon
 * enters edit mode, as LunaCE's reorder mode: icons get their frame, apps installed from
 * packages get the delete decorator, icons can be dragged to a new place, and Done (or the
 * home button) leaves it. The order is kept.
 */
@SuppressLint("ViewConstructor")
class Launcher(context: Context, private val luna: Luna, private val onLaunch: (AppInfo) -> Unit) : View(context) {
    object Params {
        const val TAB_BAR = 50f
        const val TAB_MAX_W = 150f
        const val CELL = 128f
        const val ICON = 64f
        const val ICON_DY = -11f          // icon centre above the cell centre
        const val LABEL_W = 100f
        const val LABEL_H = 40f
        const val LABEL_GAP = 2f
        const val MAX_COLUMNS = 7
        const val LEFT_MARGIN = 27f
        const val TOP_MARGIN = 20f
        const val ROW_GAP = 10f
        const val TAP_RADIUS = 25f
        const val SNAP_MS = 250L          // snap to page, InQuad
        const val FLICK_MIN_MS = 200L
        const val FLICK_MAX_MS = 1200L
        // LunaCE's IconGeometrySettings and DynamicsSettings.
        const val FEEDBACK = 90f          // launcher-touch-feedback.png, centred on the icon
        const val FEEDBACK_MS = 3000L     // IconFeedbackTimeout
        const val DELETE_DX = -50f        // delete decorator's centre from the cell centre
        const val DELETE_DY = -47f
        const val DELETE_BOX = 32f
        const val MOVE_MS = 300L          // IconReorderIconMoveAnimTime, InQuad
        const val DONE_W = 100f           // edit-button-done.png, one state
        const val DONE_H = 40f
        const val DONE_RIGHT = 12f        // doneButtonPositionAdjust
        // AppInfoDialog.qml
        const val DIALOG_EDGE = 11f
        const val DIALOG_MARGIN = 6f
        const val DIALOG_TOP = 4f
        const val DIALOG_W = 320f + 2 * DIALOG_EDGE
        const val DIALOG_BUTTON_H = 52f
    }

    class Page(val title: String, val apps: MutableList<AppInfo> = mutableListOf()) { var scrollY = 0f }

    val pages = mutableListOf(Page("apps"), Page("downloads"), Page("favorites"), Page("settings"))
    /** Current page position (fractional while dragging). */
    private var pagePos = 0f
    /** Height of the dock that sits over the launcher's bottom. */
    var dockHeight = 0f
    /** The user confirmed removing an app (the dialog's Remove). */
    var onRemove: (AppInfo) -> Unit = {}

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val tabText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(16f); typeface = luna.fontBold; textAlign = Paint.Align.CENTER }
    private val doneText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(15f); typeface = luna.fontBold; color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(14f); typeface = luna.fontBold; color = Color.WHITE }
    private val labels = HashMap<String, StaticLayout>()

    /**
     * The order is the user's: apps they've placed keep their places, and others (all of them,
     * until the first reorder) follow by title.
     */
    fun setApps(apps: List<AppInfo>) {
        val saved = runCatching { JSONArray(prefs.getString("order", "[]")) }.getOrDefault(JSONArray())
        val byId = apps.associateBy { it.id }
        val placed = (0 until saved.length()).mapNotNull { byId[saved.optString(it)] }
        pages.forEach { it.apps.clear() }
        pages[0].apps.addAll(placed + apps.filter { it !in placed }.sortedBy { it.title.lowercase() })
        if (editing && dragging != null && dragging !in pages[0].apps) dragging = null
        invalidate()
    }

    private fun saveOrder() = prefs.edit().putString("order", JSONArray(pages[0].apps.map { it.id }).toString()).apply()

    // ---- launch feedback ----

    private var feedbackId: String? = null
    private val clearFeedback = Runnable { feedbackId = null; invalidate() }

    /** The glow behind a tapped icon, until the launcher closes or LunaCE's 3 s run out. */
    fun showLaunchFeedback(app: AppInfo) {
        feedbackId = app.id
        removeCallbacks(clearFeedback)
        postDelayed(clearFeedback, Params.FEEDBACK_MS)
        invalidate()
    }

    fun cancelLaunchFeedback() { removeCallbacks(clearFeedback); feedbackId = null; invalidate() }

    // ---- geometry ----

    private fun tabBarH() = luna.px(Params.TAB_BAR)
    private fun pageTop() = tabBarH()
    private fun pageBottom() = height - dockHeight - 1
    private fun columns(): Int {
        val row = width - 2 * luna.px(Params.LEFT_MARGIN)
        return max(1, min(Params.MAX_COLUMNS, (row / luna.px(Params.CELL)).toInt()))
    }
    private fun columnPitch(): Float {
        val n = columns(); val row = width - 2 * luna.px(Params.LEFT_MARGIN)
        return if (n <= 1) luna.px(Params.CELL) else luna.px(Params.CELL) + (row - n * luna.px(Params.CELL)) / (n - 1)
    }
    private fun rowPitch() = luna.px(Params.CELL + Params.ROW_GAP)
    private fun cellCentre(i: Int): PointF {
        val n = columns()
        val x = luna.px(Params.LEFT_MARGIN) + (i % n) * columnPitch() + luna.px(Params.CELL) / 2
        val y = pageTop() + luna.px(Params.TOP_MARGIN) + (i / n) * rowPitch() + luna.px(Params.CELL) / 2
        return PointF(x, y)
    }
    /** The cell under a point in page coordinates. */
    private fun cellAt(x: Float, y: Float, count: Int): Int {
        val col = ((x - luna.px(Params.LEFT_MARGIN)) / columnPitch()).toInt().coerceIn(0, columns() - 1)
        val row = max(0, ((y - pageTop() - luna.px(Params.TOP_MARGIN)) / rowPitch()).toInt())
        return (row * columns() + col).coerceIn(0, max(0, count - 1))
    }
    private fun contentHeight(p: Page) = luna.px(Params.TOP_MARGIN) + ((p.apps.size + columns() - 1) / columns()) * rowPitch()
    private fun maxScroll(p: Page) = max(0f, contentHeight(p) - (pageBottom() - pageTop()))
    private fun tabWidth() = min(width.toFloat() / pages.size, luna.px(Params.TAB_MAX_W))
    private fun currentPage() = pages[pagePos.roundToInt().coerceIn(0, pages.size - 1)]
    private fun deleteCentre(c: PointF) = PointF(c.x + luna.px(Params.DELETE_DX), c.y + luna.px(Params.DELETE_DY))
    private fun doneRect(): RectF {
        val w = luna.px(Params.DONE_W); val h = luna.px(Params.DONE_H)
        val right = width - luna.px(Params.DONE_RIGHT); val top = (tabBarH() - h) / 2
        return RectF(right - w, top, right, top + h)
    }

    // ---- icon positions, animated while reordering ----

    /** Where icons were drawn when the order last changed; they move from there to their cells. */
    private val movedFrom = HashMap<String, PointF>()
    private var moveT = 1f
    private var moveAnim: ValueAnimator? = null

    private fun drawnCentre(app: AppInfo, i: Int): PointF {
        val target = cellCentre(i)
        val from = movedFrom[app.id] ?: return target
        return PointF(from.x + (target.x - from.x) * moveT, from.y + (target.y - from.y) * moveT)
    }

    /** Changes the order, with every icon sliding from where it is to its new cell (300 ms InQuad). */
    private fun reorder(change: () -> Unit) {
        val page = pages[0]
        val now = page.apps.mapIndexed { i, a -> a.id to drawnCentre(a, i) }.toMap()
        change()
        movedFrom.clear(); movedFrom.putAll(now)
        moveAnim?.cancel()
        moveT = 0f
        moveAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Params.MOVE_MS; interpolator = Easing.InQuad
            addUpdateListener { moveT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // ---- drawing ----

    override fun onDraw(c: Canvas) {
        luna.tile(c, "launcher3/launcher-bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()))

        // Pages, side by side.
        c.save(); c.clipRect(0f, pageTop(), width.toFloat(), pageBottom())
        pages.forEachIndexed { pi, page ->
            val dx = (pi - pagePos) * width
            if (abs(dx) >= width) return@forEachIndexed
            c.save(); c.translate(dx, -page.scrollY)
            page.apps.forEachIndexed { i, app -> if (app != dragging) drawIcon(c, app, drawnCentre(app, i)) }
            c.restore()
        }
        c.restore()
        luna.tile(c, "launcher3/tab-shadow.png", RectF(0f, pageTop(), width.toFloat(), pageTop() + luna.px(8f)))
        luna.tile(c, "launcher3/launcher-scrollfade-top.png", RectF(0f, pageTop(), width.toFloat(), pageTop() + luna.px(10f)))
        luna.tile(c, "launcher3/launcher-scrollfade-bottom.png", RectF(0f, pageBottom() - luna.px(20f), width.toFloat(), pageBottom()))

        drawTabBar(c)
        // The dragged icon, over everything, under the finger.
        dragging?.let { drawIcon(c, it, PointF(dragX - grabDx, dragY - grabDy)) }
        dialogApp?.let { drawDialog(c, it) }
    }

    /** centre is the cell centre: the icon sits above it, its label below. */
    private fun drawIcon(c: Canvas, app: AppInfo, centre: PointF) {
        val cx = centre.x; val cy = centre.y
        val half = luna.px(Params.ICON) / 2
        val iy = cy + luna.px(Params.ICON_DY)
        if (editing) {
            val fh = luna.px(Params.CELL) / 2
            luna.image("launcher3/edit-icon-bg.png")?.let { c.drawBitmap(it, null, RectF(cx - fh, cy - fh, cx + fh, cy + fh), null) }
        }
        if (feedbackId == app.id) {
            val gh = luna.px(Params.FEEDBACK) / 2
            luna.image("launcher3/launcher-touch-feedback.png")?.let { c.drawBitmap(it, null, RectF(cx - gh, iy - gh, cx + gh, iy + gh), null) }
        }
        val bmp = luna.appIcon(app) ?: luna.image("default-app-icon.png")
        bmp?.let { c.drawBitmap(it, null, RectF(cx - half, iy - half, cx + half, iy + half), null) }
        val layout = labels.getOrPut(app.id) { twoLineLabel(app.title) }
        c.save(); c.translate(cx - layout.width / 2f, iy + half + luna.px(Params.LABEL_GAP)); layout.draw(c); c.restore()
        if (editing && app.userInstalled && app != dragging) {
            val d = deleteCentre(centre)
            drawState(c, "launcher3/edit-button-delete.png", d.x, d.y, pressed = pressedDelete == app.id)
        }
    }

    /** Draws one state of a two-state image (normal above pressed), at its size, centred. */
    private fun drawState(c: Canvas, path: String, cx: Float, cy: Float, pressed: Boolean) {
        val b = luna.image(path) ?: return
        val h = b.height / 2
        val src = if (pressed) Rect(0, h, b.width, b.height) else Rect(0, 0, b.width, h)
        c.drawBitmap(b, src, RectF(cx - b.width / 2f, cy - h / 2f, cx + b.width / 2f, cy + h / 2f), null)
    }

    /** Centred, wrapped to at most two lines, the second elided with "…" (API 21 has no maxLines). */
    @Suppress("DEPRECATION")
    private fun twoLineLabel(text: String): StaticLayout {
        val w = luna.px(Params.LABEL_W).toInt()
        fun make(t: CharSequence) = StaticLayout(t, label, w, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)
        val full = make(text)
        if (full.lineCount <= 2) return full
        val second = text.substring(full.getLineStart(1))
        val elided = TextUtils.ellipsize(second, label, w.toFloat(), TextUtils.TruncateAt.END)
        return make(text.substring(0, full.getLineStart(1)) + elided)
    }

    private fun drawTabBar(c: Canvas) {
        val h = tabBarH(); val tw = tabWidth()
        luna.nine(c, "launcher3/tab-bg.png", RectF(0f, 0f, width.toFloat(), h), 4, 20, 4, 20)
        val selected = pagePos.roundToInt()
        pages.forEachIndexed { i, p ->
            val r = RectF(i * tw, 0f, (i + 1) * tw, h)
            if (i == selected) luna.nine(c, "launcher3/tab-selected-bg.png", r, 20, 20, 20, 20)
            if (i > 0 && i != selected) luna.nine(c, "launcher3/tab-divider.png", RectF(r.left - luna.px(1f), 0f, r.left + luna.px(1f), h), 0, 20, 0, 20)
            tabText.color = if (i == selected) Color.WHITE else Color.rgb(0xC8, 0xC8, 0xC8)
            c.drawText(p.title.uppercase(), r.centerX(), h / 2 - (tabText.ascent() + tabText.descent()) / 2, tabText)
        }
        if (editing) {
            val r = doneRect()
            drawState(c, "launcher3/edit-button-done.png", r.centerX(), r.centerY(), pressed = donePressed)
            c.drawText("Done", r.centerX(), r.centerY() - (doneText.ascent() + doneText.descent()) / 2, doneText)
        }
    }

    // ---- edit mode ----

    var editing = false
        private set
    private var dragging: AppInfo? = null
    private var dragX = 0f; private var dragY = 0f
    private var grabDx = 0f; private var grabDy = 0f
    private var pressedDelete: String? = null
    private var donePressed = false

    fun enterEditMode() { if (!editing) { editing = true; cancelLaunchFeedback(); invalidate() } }

    /** Done, the home button, or the launcher closing. */
    fun exitEditMode() {
        if (!editing) return
        editing = false; dragging = null; dialogApp = null; pressedDelete = null; donePressed = false
        invalidate()
    }

    private fun pickUp(app: AppInfo, x: Float, y: Float) {
        val page = pages[0]
        val c = drawnCentre(app, page.apps.indexOf(app))
        dragging = app
        dragX = x; dragY = y
        grabDx = x - c.x; grabDy = y + page.scrollY - c.y
        drag = Drag.ICON
        invalidate()
    }

    /** The dragged icon's cell follows the finger; the others make room. */
    private fun dragTo(x: Float, y: Float) {
        dragX = x; dragY = y
        val page = pages[0]
        val app = dragging ?: return
        val to = cellAt(x - grabDx, y + page.scrollY - grabDy, page.apps.size)
        val from = page.apps.indexOf(app)
        if (to != from && from >= 0) reorder { page.apps.removeAt(from); page.apps.add(to, app) }
        invalidate()
    }

    private fun drop() {
        val app = dragging ?: return
        val page = pages[0]
        // The icon settles from under the finger into its cell.
        val finger = PointF(dragX - grabDx, dragY - grabDy + page.scrollY)
        dragging = null
        reorder { }
        movedFrom[app.id] = finger
        saveOrder()
    }

    // ---- the remove dialog (LunaCE's AppInfoDialog) ----

    private var dialogApp: AppInfo? = null
    private var dialogPressed = -1  // 0 Cancel, 1 Remove
    private val dialogTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(18f); typeface = luna.fontBold; color = Color.WHITE }
    private val dialogMessage = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(14f); typeface = luna.fontBold; color = Color.WHITE }
    private val buttonText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(16f); typeface = luna.fontBold; color = Color.WHITE; textAlign = Paint.Align.CENTER }

    @Suppress("DEPRECATION")
    private fun dialogText(t: String, p: TextPaint) =
        StaticLayout(t, p, luna.px(Params.DIALOG_W - 2 * (Params.DIALOG_EDGE + Params.DIALOG_MARGIN)).toInt(), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)

    private class DialogLayout(val frame: RectF, val title: StaticLayout, val titleY: Float, val message: StaticLayout, val messageY: Float, val buttons: List<RectF>)

    private fun dialogLayout(app: AppInfo): DialogLayout {
        val edge = luna.px(Params.DIALOG_EDGE); val margin = luna.px(Params.DIALOG_MARGIN)
        val title = dialogText("Remove Application?", dialogTitle)
        val message = dialogText(app.title + " - v." + app.version, dialogMessage)
        val bh = luna.px(Params.DIALOG_BUTTON_H)
        val w = luna.px(Params.DIALOG_W)
        val h = title.height + message.height + 2 * bh + 2 * edge + 4 * margin + luna.px(Params.DIALOG_TOP)
        val left = (width - w) / 2; val top = (height - h) / 2
        val titleY = top + edge + margin + luna.px(Params.DIALOG_TOP)
        val messageY = titleY + title.height + margin
        val bx = left + edge + margin + luna.px(1f); val bw = w - 2 * (edge + margin) - luna.px(1f)
        val cancel = RectF(bx, messageY + message.height + margin, bx + bw, messageY + message.height + margin + bh)
        val remove = RectF(bx, cancel.bottom, bx + bw, cancel.bottom + bh)
        return DialogLayout(RectF(left, top, left + w, top + h), title, titleY, message, messageY, listOf(cancel, remove))
    }

    private fun drawDialog(c: Canvas, app: AppInfo) {
        luna.image("scrim.png")?.let { c.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null) }
        val d = dialogLayout(app)
        luna.nine(c, "popup-bg.png", d.frame, 35, 40, 35, 40)
        c.save(); c.translate(d.frame.centerX() - d.title.width / 2f, d.titleY); d.title.draw(c); c.restore()
        c.save(); c.translate(d.frame.centerX() - d.message.width / 2f, d.messageY); d.message.draw(c); c.restore()
        listOf("Cancel", "Remove").forEachIndexed { i, caption ->
            val r = d.buttons[i]
            luna.nine(c, if (dialogPressed == i) "pin/button-black-press.png" else "pin/button-black.png", r, 10, 10, 10, 10)
            c.drawText(caption, r.centerX(), r.centerY() - (buttonText.ascent() + buttonText.descent()) / 2, buttonText)
        }
    }

    private fun dialogTouch(e: MotionEvent) {
        val app = dialogApp ?: return
        val hit = dialogLayout(app).buttons.indexOfFirst { it.contains(e.x, e.y) }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { dialogPressed = hit; invalidate() }
            MotionEvent.ACTION_UP -> {
                dialogPressed = -1
                if (hit >= 0) { dialogApp = null; if (hit == 1) onRemove(app) }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { dialogPressed = -1; invalidate() }
        }
    }

    // ---- touch ----

    private enum class Drag { NONE, UNDECIDED, PAGE, SCROLL, ICON }
    private var drag = Drag.NONE
    private var downX = 0f; private var downY = 0f; private var downPage = 0f; private var downScroll = 0f
    private var downApp: AppInfo? = null
    private var velocity: VelocityTracker? = null
    private var anim: ValueAnimator? = null
    /** Holding an icon enters edit mode and picks the icon up. */
    private val longPress = Runnable {
        val app = downApp ?: return@Runnable
        if (drag != Drag.UNDECIDED) return@Runnable
        enterEditMode()
        pickUp(app, downX, downY)
    }

    private fun appAt(x: Float, y: Float): AppInfo? {
        if (y < pageTop() || y > pageBottom() || abs(pagePos - pagePos.roundToInt()) > 0.01f) return null
        val page = currentPage()
        val half = luna.px(Params.CELL) / 2
        return page.apps.withIndex().firstOrNull { (i, _) -> val c = cellCentre(i); abs(x - c.x) < half && abs(y + page.scrollY - c.y) < half }?.value
    }

    private fun deleteAt(x: Float, y: Float): AppInfo? {
        if (!editing) return null
        val page = pages[0]
        val r = luna.px(Params.DELETE_BOX) / 2 + luna.px(6f)
        return page.apps.withIndex().firstOrNull { (i, a) ->
            if (!a.userInstalled) return@firstOrNull false
            val d = deleteCentre(cellCentre(i)); abs(x - d.x) < r && abs(y + page.scrollY - d.y) < r
        }?.value
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (dialogApp != null) { dialogTouch(e); return true }
        if (velocity == null) velocity = VelocityTracker.obtain()
        velocity!!.addMovement(e)
        val page = currentPage()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel(); drag = Drag.UNDECIDED
                downX = e.x; downY = e.y; downPage = pagePos; downScroll = page.scrollY
                pressedDelete = deleteAt(e.x, e.y)?.id
                donePressed = editing && doneRect().contains(e.x, e.y)
                downApp = if (pressedDelete == null && !donePressed) appAt(e.x, e.y) else null
                if (downApp != null && !editing) postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (drag == Drag.UNDECIDED && max(abs(dx), abs(dy)) > luna.px(Params.TAP_RADIUS)) {
                    removeCallbacks(longPress)
                    pressedDelete = null; donePressed = false
                    val app = downApp
                    // In edit mode an icon moves with the finger; otherwise the first axis past
                    // the tap radius wins (reference §3.7).
                    if (editing && app != null) pickUp(app, downX, downY)
                    else drag = if (abs(dx) >= abs(dy)) Drag.PAGE else Drag.SCROLL
                }
                when (drag) {
                    Drag.ICON -> dragTo(e.x, e.y)
                    Drag.PAGE -> { pagePos = (downPage - dx / width).coerceIn(-0.2f, pages.size - 0.8f); invalidate() }
                    Drag.SCROLL -> {
                        var y = downScroll - dy
                        val m = maxScroll(page)
                        if (y < 0) y /= 2 else if (y > m) y = m + (y - m) / 2  // half rate while overscrolled
                        page.scrollY = y; invalidate()
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                velocity!!.computeCurrentVelocity(1000)
                val vx = velocity!!.xVelocity; val vy = velocity!!.yVelocity
                velocity!!.recycle(); velocity = null
                when (drag) {
                    Drag.ICON -> drop()
                    Drag.PAGE -> {
                        val flick = abs(vx) > luna.px(500f)
                        if (flick) {
                            val target = (downPage.roundToInt() + if (vx < 0) 1 else -1).coerceIn(0, pages.size - 1)
                            val ms = (abs(target - pagePos) * width / (abs(vx) / 1000f)).toLong().coerceIn(Params.FLICK_MIN_MS, Params.FLICK_MAX_MS)
                            animatePage(target, ms, Easing.OutCubic)
                        } else animatePage(pagePos.roundToInt().coerceIn(0, pages.size - 1), Params.SNAP_MS, Easing.InQuad)
                    }
                    Drag.SCROLL -> fling(page, -vy)
                    Drag.UNDECIDED -> if (e.actionMasked == MotionEvent.ACTION_UP) tap(e.x, e.y)
                    Drag.NONE -> {}
                }
                pressedDelete = null; donePressed = false
                drag = Drag.NONE
                invalidate()
            }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        if (editing) {
            if (doneRect().contains(x, y)) { exitEditMode(); return }
            deleteAt(x, y)?.let { dialogApp = it; return }
        }
        if (y < tabBarH()) {
            val i = (x / tabWidth()).toInt()
            if (i in pages.indices) animatePage(i, Params.SNAP_MS, Easing.InQuad)
            return
        }
        if (editing) return  // icons don't launch while being arranged
        appAt(x, y)?.let { showLaunchFeedback(it); onLaunch(it) }
    }

    private fun animatePage(target: Int, ms: Long, easing: android.animation.TimeInterpolator) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(pagePos, target.toFloat()).apply {
            duration = ms; interpolator = easing
            addUpdateListener { pagePos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Kinetic vertical scroll, then snap back inside the page (500 ms OutCubic). */
    private fun fling(page: Page, v: Float) {
        anim?.cancel()
        val m = maxScroll(page)
        val start = page.scrollY
        val target = (start + v * 0.35f).coerceIn(0f, m)
        val ms = if (start < 0 || start > m) 500L else (min(1200f, abs(target - start) / max(abs(v), 1f) * 2500f)).toLong().coerceAtLeast(200L)
        anim = ValueAnimator.ofFloat(start, target).apply {
            duration = ms; interpolator = Easing.OutCubic
            addUpdateListener { page.scrollY = it.animatedValue as Float; invalidate() }
            start()
        }
    }
}
