package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import org.webosarchive.lunacy.card.AppWindow
import kotlin.math.abs

/**
 * A dashboard in a list: its window, dragged sideways past a quarter of its width to dismiss
 * it (DashboardWindowContainer::triggerItemDelete). A dashboard opened with
 * `webosDragMode: "manual"` - every Enyo dashboard - handles its own swipes: only its badge at
 * the left ([badgePx], 50 px) drags the row, and every other touch is the app's
 * (DashboardWindowContainer::mousePressEvent).
 *
 * Otherwise the row owns the touch, and the page hears of it only once it has ended as a
 * tap: a press and a release at the point it went down (DashboardWindowContainer::handleTap).
 * Given the touch from the start, the page took a swipe's cancel as a lift that never left
 * the tap radius, and Mojo opened the app mid-swipe.
 *
 * The TouchPad's drop-down ([DashboardMenu]) and the Pre3's bottom area ([NotificationArea])
 * differ in two things, kept as parameters: [rightOnly] (the drop-down's rows go right only)
 * and [onMoved] (the drop-down redraws its list as a row moves).
 */
@SuppressLint("ViewConstructor")
class DashboardRow(
    context: Context,
    val window: AppWindow,
    private val badgePx: Float,
    private val dismissMs: Long,
    private val rightOnly: Boolean,
    private val onMoved: () -> Unit,
    private val onDismiss: (AppWindow) -> Unit,
) : FrameLayout(context) {
    private var downX = 0f
    private var downY = 0f
    private var tapX = 0f
    private var tapY = 0f
    private var mine = true
    /** Past the slop sideways: the row follows the finger. */
    private var dragging = false
    /** Past the slop at all: whatever happens next, it isn't a tap. */
    private var moved = false
    /** Swiped off and going: no more touches, and dismissed once. */
    private var leaving = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val manual get() = window.attributes.optString("webosDragMode") == "manual"

    init { addView(window, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (leaving) return true
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = e.rawX; downY = e.rawY; tapX = e.x; tapY = e.y
            dragging = false; moved = false
            mine = !manual || e.x <= badgePx
        }
        return mine
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (leaving) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX; val dy = e.rawY - downY
                if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                    moved = true
                    // Sideways is the row's; up and down is the list's, which takes it.
                    if (abs(dx) > abs(dy)) { dragging = true; parent.requestDisallowInterceptTouchEvent(true) }
                }
                if (dragging) { translationX = if (rightOnly) dx.coerceAtLeast(0f) else dx; onMoved() }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (e.actionMasked == MotionEvent.ACTION_UP && !moved) { tap(e); return true }
                if (abs(translationX) > width / 4f) {
                    // Off by one and a half widths, the way it was going (removeWindow's animation).
                    leaving = true
                    animate().translationX(Math.signum(translationX) * width * 1.5f).setDuration(dismissMs).setInterpolator(Easing.Linear)
                        .setUpdateListener { onMoved() }
                        .withEndAction { onDismiss(window) }.start()
                } else animate().translationX(0f).setDuration(dismissMs).setUpdateListener { onMoved() }.start()
            }
        }
        return true
    }

    /** The tap, given to the page as a press and a release where the finger went down. */
    private fun tap(up: MotionEvent) {
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(up).apply { this.action = action; setLocation(tapX, tapY) }
                .also { window.dispatchTouchEvent(it); it.recycle() }
        }
    }
}
