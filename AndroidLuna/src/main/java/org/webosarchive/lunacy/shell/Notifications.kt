package org.webosarchive.lunacy.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.webosarchive.lunacy.card.AppWindow
import kotlin.math.abs

/**
 * A banner from PalmSystem.addBannerMessage, or from the system (window null), such as the
 * package manager's. Tapping it launches appId with params.
 */
class Banner(val id: Int, val window: AppWindow?, val appId: String, val text: String, val icon: Bitmap?, val params: String,
             val soundClass: String = "", val soundFile: String = "", val soundDuration: Int = 0)

/**
 * Banners, dashboards and popup alerts: Docs/luna-shell-reference.md §5. On the TouchPad banners
 * scroll inside the status bar; dashboards live in a drop-down under it.
 */
class Notifications(
    private val luna: Luna,
    private val statusBar: StatusBar,
    val menu: DashboardMenu,
    val popups: PopupLayer,
    private val onBannerTap: (Banner) -> Unit,
    /** The last dashboard went away: the shell closes the drop-down (and its tap catcher). */
    private val onNoDashboards: () -> Unit = {},
    /** A banner is starting to show: LunaSysMgr played its sound then, not when it was queued. */
    private val onBannerShown: (Banner) -> Unit = {},
) {
    companion object {
        const val SHOW_MS = 1000L        // OutCubic
        const val HIDE_MS = 1000L        // linear, opacity to 0.25
        const val HOLD_ALONE_MS = 5000L  // when no other banner is waiting
        const val HOLD_QUEUED_MS = 2000L // when others are waiting
    }

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Banner>()
    private var current: Banner? = null
    private var shownAt = 0L
    private var hold = HOLD_ALONE_MS
    private var anim: ValueAnimator? = null
    private val hideRunnable = Runnable { hideCurrent() }
    private val icons = LinkedHashMap<AppWindow, Bitmap?>()

    // ---- banners ----

    fun addBanner(b: Banner) {
        queue.addLast(b)
        if (current == null) showNext()
        else if (hold == HOLD_ALONE_MS) {
            // A banner is waiting now: cut the running 5 s hold to its 2 s remainder.
            hold = HOLD_QUEUED_MS
            main.removeCallbacks(hideRunnable)
            val left = HOLD_QUEUED_MS - (System.currentTimeMillis() - shownAt - SHOW_MS)
            main.postDelayed(hideRunnable, left.coerceAtLeast(0))
        }
    }

    fun removeBanner(window: AppWindow, id: Int) {
        queue.removeAll { it.window == window && it.id == id }
        if (current?.window == window && current?.id == id) hideCurrent()
    }

    fun clearBanners(window: AppWindow) {
        queue.removeAll { it.window == window }
        if (current?.window == window) hideCurrent()
    }

    fun tapBanner(): Boolean {
        val b = current ?: return false
        onBannerTap(b)
        hideCurrent()
        return true
    }

    private fun showNext() {
        val b = queue.removeFirstOrNull() ?: run { current = null; statusBar.banner = null; return }
        current = b
        statusBar.banner = b
        onBannerShown(b)
        hold = if (queue.isEmpty()) HOLD_ALONE_MS else HOLD_QUEUED_MS
        shownAt = System.currentTimeMillis()
        animate(0f, 1f, 1f, SHOW_MS, Easing.OutCubic) { main.postDelayed(hideRunnable, hold) }
    }

    private fun hideCurrent() {
        main.removeCallbacks(hideRunnable)
        if (current == null) return
        animate(statusBar.bannerProgress, 0f, 0.25f, HIDE_MS, Easing.Linear) { showNext() }
    }

    private fun animate(from: Float, to: Float, alphaTo: Float, ms: Long, easing: android.animation.TimeInterpolator, done: () -> Unit) {
        anim?.removeAllListeners(); anim?.cancel()
        val alphaFrom = statusBar.bannerAlpha
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms; interpolator = easing
            addUpdateListener {
                val f = it.animatedValue as Float
                statusBar.bannerProgress = from + (to - from) * f
                statusBar.bannerAlpha = alphaFrom + (alphaTo - alphaFrom) * f
                statusBar.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { done() } })
            start()
        }
    }

    // ---- dashboards ----

    val hasDashboards get() = icons.isNotEmpty()

    fun addDashboard(w: AppWindow, icon: Bitmap?) {
        icons[w] = icon
        menu.add(w)
        statusBar.addNotificationIcon(w, icon)
    }

    fun removeDashboard(w: AppWindow) {
        if (!icons.containsKey(w)) return
        icons.remove(w)
        menu.remove(w)
        statusBar.removeNotificationIcon(w)
        if (icons.isEmpty()) onNoDashboards()
    }
}

/**
 * The dashboard drop-down: LunaCE's MenuContainer frame (menu-dropdown-bg.png, borders
 * L30 T10 R30 B30; content inset L11 R11 B15) around 320-px-wide dashboard rows, 52 px each.
 * Rows swipe right to dismiss.
 */
@SuppressLint("ViewConstructor")
class DashboardMenu(context: Context, private val luna: Luna, private val onDismiss: (AppWindow) -> Unit) : FrameLayout(context) {
    companion object {
        const val CONTENT_W = 320
        const val ROW_H = 52
        const val MAX_H = 410
        const val FADE_MS = 200L           // statusBarMenuFadeDuration, linear
        const val DISMISS_MS = 200L        // dashboardDelete, linear
    }

    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    var isOpen = false
        private set

    init {
        setWillNotDraw(false)
        addView(rows, LayoutParams(luna.px(CONTENT_W), LayoutParams.WRAP_CONTENT).apply {
            leftMargin = luna.px(11); rightMargin = luna.px(11); bottomMargin = luna.px(15)
        })
        visibility = View.INVISIBLE  // dashboards keep running while the menu is closed
        alpha = 0f
    }

    fun add(w: AppWindow) {
        val row = Row(w)
        // Newest first.
        rows.addView(row, 0, LinearLayout.LayoutParams(luna.px(CONTENT_W), luna.px(ROW_H)))
        requestLayout()
    }

    fun remove(w: AppWindow) {
        for (i in 0 until rows.childCount) {
            val r = rows.getChildAt(i) as Row
            if (r.window == w) { r.removeView(w); rows.removeViewAt(i); break }
        }
        requestLayout()
    }

    fun open() {
        if (rows.childCount == 0) return
        isOpen = true
        visibility = View.VISIBLE
        animate().cancel()
        animate().alpha(1f).setDuration(FADE_MS).setInterpolator(Easing.Linear).start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        animate().cancel()
        animate().alpha(0f).setDuration(FADE_MS).setInterpolator(Easing.Linear)
            .withEndAction { if (!isOpen) visibility = View.INVISIBLE }.start()
    }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(luna.px(CONTENT_W + 22), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val max = luna.px(MAX_H)
        if (measuredHeight > max) setMeasuredDimension(measuredWidth, max)
    }

    override fun onDraw(c: Canvas) {
        luna.nine(c, "menu-dropdown-bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()), 30, 10, 30, 30)
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        // menu-divider.png between rows.
        for (i in 1 until rows.childCount) {
            val y = rows.top + rows.getChildAt(i).top.toFloat()
            luna.tile(c, "menu-divider.png", RectF(rows.left.toFloat(), y - luna.px(1f), rows.right.toFloat(), y + luna.px(1f)))
        }
    }

    /** One dashboard: its window, draggable to the right to dismiss (threshold width/4). */
    @SuppressLint("ViewConstructor")
    inner class Row(val window: AppWindow) : FrameLayout(context) {
        private var downX = 0f
        private var dragging = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        init { addView(window, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; dragging = false }
                MotionEvent.ACTION_MOVE -> if (e.rawX - downX > slop) { dragging = true; return true }
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> translationX = (e.rawX - downX).coerceAtLeast(0f)  // right only
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (translationX > width / 4f) {
                        animate().translationX(width * 1.5f).setDuration(DISMISS_MS).setInterpolator(Easing.Linear)
                            .withEndAction { onDismiss(window) }.start()
                    } else animate().translationX(0f).setDuration(DISMISS_MS).start()
                    dragging = false
                }
            }
            return true
        }
    }
}

/** Popup alerts (window: "popupalert"): 320 px wide in popup-bg.png (20 px frame), 5 px from the top right. */
@SuppressLint("ViewConstructor")
class PopupLayer(context: Context, private val luna: Luna) : FrameLayout(context) {
    companion object { const val WIDTH = 320; const val FRAME = 20; const val INSET = 5 }

    private val frames = LinkedHashMap<AppWindow, FrameLayout>()

    fun show(w: AppWindow, height: Int) {
        val frame = object : FrameLayout(context) {
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) = luna.nine(c, "popup-bg.png", RectF(0f, 0f, width.toFloat(), this.height.toFloat()), FRAME, FRAME, FRAME, FRAME)
        }
        frame.addView(w, LayoutParams(luna.px(WIDTH), luna.px(height)).apply { setMargins(luna.px(FRAME), luna.px(FRAME), luna.px(FRAME), luna.px(FRAME)) })
        addView(frame, LayoutParams(luna.px(WIDTH + 2 * FRAME), luna.px(height + 2 * FRAME)).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.TOP
            topMargin = luna.px(INSET) - luna.px(FRAME); rightMargin = luna.px(INSET) - luna.px(FRAME)
        })
        frames[w] = frame
    }

    fun remove(w: AppWindow) {
        val f = frames.remove(w) ?: return
        f.removeView(w); removeView(f)
    }

    val showing get() = frames.isNotEmpty()
    fun newest(): AppWindow? = frames.keys.lastOrNull()
}
