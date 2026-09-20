package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.webosarchive.lunacy.card.AppInfo
import kotlin.math.abs

/**
 * The quick-launch dock (reference §3.9): up to five app icons, no labels, then the launcher
 * button at the right. Holding a dock icon picks it up, for that drag only (the launcher
 * stays as it is); in the launcher's edit mode a drag picks one up too. As in LunaCE's
 * QuickLaunchBar, the picked-up icon lifts to sit 15 px above the finger and follows it; the
 * others make room for it; above the dock it turns half transparent. Let go on the dock and
 * it takes that slot, let go above the dock and it leaves the dock, as on webOS. Launcher icons dropped here join
 * the dock (ShellActivity keeps its list). The dragged icon draws outside the dock's bounds,
 * so its parent doesn't clip children.
 */
@SuppressLint("ViewConstructor")
class QuickLaunch(context: Context, private val luna: Luna, private val onLaunch: (AppInfo) -> Unit, private val onLauncher: () -> Unit) : View(context) {
    companion object {
        const val HEIGHT = 100
        const val ICON = 64
        // Icon centre below the bar top: 54, level with the launcher button, measured on the
        // reference TouchPad (the 65 in LunaCE's layout settings is not what it shows).
        const val ICON_Y = 54
        const val MAX_ITEMS = 5
        /** A picked-up icon's centre sits this far above the finger (LunaCE's MOVING_ICON_Y_OFFSET). */
        const val LIFT = 15f
    }

    var apps: List<AppInfo> = emptyList()
        set(v) { field = v.take(MAX_ITEMS); invalidate() }
    var editing = false
        set(v) { field = v; if (!v) dragging = -1; invalidate() }
    /** An icon dragged up and out: it leaves the dock. */
    var onRemoveItem: (Int) -> Unit = {}
    /** An icon dragged to another slot. */
    var onMoveItem: (Int, Int) -> Unit = { _, _ -> }

    private var pressed = -2  // -1 = launcher button
    /** The item glowing while its app launches, as in the launcher (LunaCE's launch feedback). */
    private var feedback = -1
    private val clearFeedback = Runnable { feedback = -1; invalidate() }
    private var dragging = -1
    private var dragX = 0f; private var dragY = 0f
    private var downX = 0f; private var downY = 0f

    fun cancelLaunchFeedback() { removeCallbacks(clearFeedback); feedback = -1; invalidate() }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), luna.px(HEIGHT))

    private fun buttonRect(): RectF {
        val s = luna.px(ICON.toFloat())
        val cx = width - luna.px(64f); val cy = luna.px(22f + 32f)
        return RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
    }
    private fun areaWidth() = buttonRect().centerX() - luna.px(31f)
    /** Items share the space left of the launcher button in equal slots. */
    private fun itemRect(i: Int, count: Int = apps.size): RectF {
        val slot = areaWidth() / count.coerceAtLeast(1)
        val s = luna.px(ICON.toFloat())
        val cx = slot * (i + 0.5f); val cy = luna.px(ICON_Y.toFloat())
        return RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
    }
    /** The slot under x, when the dock holds count items. */
    fun slotAt(x: Float, count: Int): Int = (x / (areaWidth() / count.coerceAtLeast(1))).toInt().coerceIn(0, maxOf(0, count - 1))

    override fun onDraw(c: Canvas) {
        luna.tile(c, "launcher3/quicklaunch-bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()))
        // While one is picked up, the others take the slots around the one it's over.
        val target = if (dragging >= 0) slotAt(dragX, apps.size) else -1
        val others = apps.indices.filter { it != dragging }
        apps.forEachIndexed { i, app ->
            if (i == dragging) return@forEachIndexed
            val r = if (dragging < 0) itemRect(i) else others.indexOf(i).let { k -> itemRect(if (k >= target) k + 1 else k) }
            if (i == feedback) luna.image("launcher3/launcher-touch-feedback.png")?.let { g ->
                val gh = luna.px(Launcher.Params.FEEDBACK) / 2
                c.drawBitmap(g, null, RectF(r.centerX() - gh, r.centerY() - gh, r.centerX() + gh, r.centerY() + gh), null)
            }
            val icon = luna.appIcon(app) ?: luna.image("default-app-icon.png")
            icon?.let { c.drawBitmap(it, null, r, null) }
        }
        luna.image("launcher3/quicklaunch-button-launcher.png")?.let { b ->
            val half = b.height / 2  // two states stacked: normal, pressed
            val src = if (pressed == -1) Rect(0, half, b.width, b.height) else Rect(0, 0, b.width, half)
            c.drawBitmap(b, src, buttonRect(), null)
        }
        // The picked-up icon, under the finger, over the dock and above it.
        if (dragging in apps.indices) {
            val half = luna.px(ICON.toFloat()) / 2; val cy = dragY - luna.px(LIFT)
            val r = RectF(dragX - half, cy - half, dragX + half, cy + half)
            val p = android.graphics.Paint().apply { alpha = if (dragY <= 0) 128 else 255 }
            (luna.appIcon(apps[dragging]) ?: luna.image("default-app-icon.png"))?.let { c.drawBitmap(it, null, r, p) }
        }
    }

    private fun hit(x: Float, y: Float): Int {
        if (buttonRect().apply { inset(-luna.px(16f), -luna.px(16f)) }.contains(x, y)) return -1
        return apps.indices.firstOrNull { itemRect(it).apply { inset(-luna.px(16f), -luna.px(16f)) }.contains(x, y) } ?: -2
    }

    /** Holding a dock icon picks it up. */
    private val hold = Runnable { if (pressed >= 0 && dragging < 0) { dragging = pressed; invalidate() } }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; dragX = e.x; downY = e.y; dragY = e.y
                pressed = hit(e.x, e.y)
                if (pressed >= 0 && !editing) postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val moved = maxOf(abs(e.x - downX), abs(e.y - downY)) > luna.px(Launcher.Params.TAP_RADIUS)
                if (editing && pressed >= 0 && dragging < 0 && moved) { dragging = pressed; removeCallbacks(hold) }
                if (dragging >= 0) { dragX = e.x; dragY = e.y; invalidate() }
                else if (moved) removeCallbacks(hold)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                val h = hit(e.x, e.y)
                when {
                    dragging >= 0 -> {
                        val from = dragging
                        dragging = -1
                        if (e.y < 0) onRemoveItem(from)  // up and out of the dock
                        else {
                            val to = slotAt(dragX, apps.size)
                            if (to != from) onMoveItem(from, to)
                        }
                    }
                    pressed == h && h == -1 -> onLauncher()
                    pressed == h && h >= 0 && !editing -> {
                        feedback = h; removeCallbacks(clearFeedback); postDelayed(clearFeedback, Launcher.Params.FEEDBACK_MS)
                        onLaunch(apps[h])
                    }
                }
                pressed = -2; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(hold); pressed = -2; dragging = -1; invalidate() }
        }
        return true
    }
}
