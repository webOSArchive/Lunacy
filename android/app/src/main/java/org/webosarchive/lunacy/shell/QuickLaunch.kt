package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.webosarchive.lunacy.card.AppInfo

/**
 * The quick-launch dock (reference §3.9): up to five app icons, no labels, then the launcher
 * button at the right.
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
    }

    var apps: List<AppInfo> = emptyList()
        set(v) { field = v.take(MAX_ITEMS); invalidate() }
    private var pressed = -2  // -1 = launcher button
    /** The item glowing while its app launches, as in the launcher (LunaCE's launch feedback). */
    private var feedback = -1
    private val clearFeedback = Runnable { feedback = -1; invalidate() }

    fun cancelLaunchFeedback() { removeCallbacks(clearFeedback); feedback = -1; invalidate() }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), luna.px(HEIGHT))

    private fun buttonRect(): RectF {
        val s = luna.px(ICON.toFloat())
        val cx = width - luna.px(64f); val cy = luna.px(22f + 32f)
        return RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
    }
    /** Items share the space left of the launcher button in equal slots. */
    private fun itemRect(i: Int): RectF {
        val areaW = buttonRect().centerX() - luna.px(31f)
        val slot = areaW / apps.size.coerceAtLeast(1)
        val s = luna.px(ICON.toFloat())
        val cx = slot * (i + 0.5f); val cy = luna.px(ICON_Y.toFloat())
        return RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
    }

    override fun onDraw(c: Canvas) {
        luna.tile(c, "launcher3/quicklaunch-bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()))
        apps.forEachIndexed { i, app ->
            if (i == feedback) luna.image("launcher3/launcher-touch-feedback.png")?.let { g ->
                val r = itemRect(i); val gh = luna.px(Launcher.Params.FEEDBACK) / 2
                c.drawBitmap(g, null, RectF(r.centerX() - gh, r.centerY() - gh, r.centerX() + gh, r.centerY() + gh), null)
            }
            val icon = luna.appIcon(app) ?: luna.image("default-app-icon.png")
            icon?.let { c.drawBitmap(it, null, itemRect(i), null) }
        }
        luna.image("launcher3/quicklaunch-button-launcher.png")?.let { b ->
            val half = b.height / 2  // two states stacked: normal, pressed
            val src = if (pressed == -1) Rect(0, half, b.width, b.height) else Rect(0, 0, b.width, half)
            c.drawBitmap(b, src, buttonRect(), null)
        }
    }

    private fun hit(x: Float, y: Float): Int {
        if (buttonRect().apply { inset(-luna.px(16f), -luna.px(16f)) }.contains(x, y)) return -1
        return apps.indices.firstOrNull { itemRect(it).apply { inset(-luna.px(16f), -luna.px(16f)) }.contains(x, y) } ?: -2
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val h = hit(e.x, e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = h; invalidate() }
            MotionEvent.ACTION_UP -> {
                if (pressed == h) {
                    if (h == -1) onLauncher()
                    else if (h >= 0) {
                        feedback = h; removeCallbacks(clearFeedback); postDelayed(clearFeedback, Launcher.Params.FEEDBACK_MS)
                        onLaunch(apps[h])
                    }
                }
                pressed = -2; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -2; invalidate() }
        }
        return true
    }
}
