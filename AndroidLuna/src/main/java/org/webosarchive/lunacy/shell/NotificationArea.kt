package org.webosarchive.lunacy.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.webosarchive.lunacy.card.AppWindow
import kotlin.math.abs

/**
 * A phone's notifications, as the Pre3 had them (Docs/phone.md §7): an area along the bottom
 * of the screen that the rest of the shell gives way to. This is LunaSysMgr's
 * DashboardWindowManager where the dashboard owns the negative space
 * (SystemUiController::dashboardOwnsNegativeSpace), measured on the reference Pre3 (webOS
 * 2.2.4) at its ScaleFactor of 1.5; one shell px here is 1.5 of the Pre3's.
 *
 * - Closed, with something to show: a black bar 28 px high (PositiveSpaceBottomPadding, 42
 *   on the Pre3). The dashboards' icons are packed against its right edge, newest rightmost
 *   (BannerWindow::paint), or a banner rises into it (BannerMessageView::VerticalScroll).
 * - Open (the bar tapped): every dashboard the full width and 52 px high
 *   (sDashboardWindowHeight), the oldest at the top, newest at the bottom, below 10 Pre3 px of
 *   padding (m_DashboardTopPadding). At most 513 of the Pre3's 800 px high, measured with an
 *   app up; past that it scrolls, with dashboard-mask-top/bottom.png where there is more.
 * - An alert: the popup alert the full width, at its own height.
 *
 * The area's height is the shell's negative space. It moves over 400 ms OutCubic
 * (positiveSpaceChangeDuration and Curve), the area carried with its top edge, and the shell is
 * told through [onSpace]: as the area starts to shrink, or once it has finished growing, so a
 * page is resized once, behind the area, never every frame.
 */
@SuppressLint("ViewConstructor")
class NotificationArea(context: Context, private val luna: Luna, private val onDismiss: (AppWindow) -> Unit)
    : FrameLayout(context), BannerSurface, DashboardHost, AlertHost {
    companion object {
        const val BAR_H = 28f              // PositiveSpaceBottomPadding: 42 Pre3 px
        const val ROW_H = 52               // DashboardWindowContainer::sDashboardWindowHeight
        const val TOP_PADDING = 10f / 1.5f // m_DashboardTopPadding: 10 Pre3 px
        const val MASK_CORRECTION = 10f / 1.5f // m_BottomMaskHeightCorrection
        /** The open area's most, of the screen's height: 513 of the Pre3's 800 px, measured over Calculator. */
        const val MAX_RATIO = 513f / 800f
        const val SPACE_MS = 400L          // positiveSpaceChangeDuration, OutCubic
        const val SNAP_MS = 200L           // dashboardSnapDuration, OutCubic
        const val DISMISS_MS = 200L        // dashboardDeleteDuration, linear
        const val BADGE_W = 50f            // sDashboardBadgeWidth
        const val TEXT_MARGIN = 5f         // BannerMessageHandler kTextMarginX
        const val BANNER_ICON = 24f
        const val BANNER_TEXT = 16f        // kFontHeight
    }

    private val barPx = Math.round(luna.px(BAR_H))

    /** How much of the screen's bottom the shell gives the area, in px. */
    var onSpace: (Int) -> Unit = {}
    /** The closed bar was tapped: a banner on show is followed, else the dashboards open. */
    var onNotificationTap: () -> Unit = {}

    /**
     * A full-screen card is up: the area takes no space and shows nothing but a banner, over
     * the bottom of the page (measured on the Pre3: the banner's bar, black, over a full-screen
     * card, which keeps its size).
     */
    var fullScreen = false
        set(v) { if (v != field) { field = v; relayout() } }

    /** The keyboard is up: Android's keyboard takes the bottom of the screen, and the area steps aside. */
    var suspended = false
        set(v) { if (v != field) { field = v; relayout(immediate = v) } }

    // ---- the closed bar: icons and banners ----

    private val icons = LinkedHashMap<Any, Bitmap?>()
    override var banner: Banner? = null
        set(v) { field = v; relayout(); bar.invalidate() }
    override var bannerProgress = 0f
    override var bannerAlpha = 1f
    override fun invalidate() { super.invalidate(); bar.invalidate() }

    override fun addNotificationIcon(key: Any, icon: Bitmap?) { icons[key] = icon; relayout(); bar.invalidate() }
    override fun removeNotificationIcon(key: Any) { icons.remove(key); relayout(); bar.invalidate() }

    private val bannerText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = luna.px(BANNER_TEXT); typeface = luna.fontMedium }

    private val bar = object : View(context) {
        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            c.drawColor(Color.BLACK)
            val b = banner
            if (b != null && bannerProgress > 0f) {
                // BannerMessageHandler::paintBanner, VerticalScroll: up from below the bar as it
                // comes in, and back down as it goes; icon and text 5 px apart, vertically centred.
                c.save(); c.clipRect(0f, 0f, width.toFloat(), h)
                c.translate(0f, (1f - bannerProgress) * h)
                val alpha = (bannerAlpha * 255).toInt()
                var x = luna.px(TEXT_MARGIN)
                b.icon?.let {
                    val s = luna.px(BANNER_ICON)
                    c.drawBitmap(it, null, RectF(x, (h - s) / 2f, x + s, (h + s) / 2f), Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
                    x += s + luna.px(TEXT_MARGIN)
                }
                val text = android.text.TextUtils.ellipsize(b.text, android.text.TextPaint(bannerText), width - x - luna.px(TEXT_MARGIN), android.text.TextUtils.TruncateAt.END).toString()
                bannerText.alpha = alpha
                c.drawText(text, x, h / 2f - (bannerText.ascent() + bannerText.descent()) / 2, bannerText)
                c.restore()
                return
            }
            if (fullScreen) return
            // The dashboards' icons from the right edge leftward, newest first, each at most
            // the bar's height and no gap between them.
            var right = width.toFloat()
            for (icon in icons.values.reversed()) {
                val i = icon ?: continue
                val ih = minOf(h, i.height.toFloat()); val iw = i.width * ih / i.height
                c.drawBitmap(i, null, RectF(right - iw, (h - ih) / 2f, right, (h + ih) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
                right -= iw
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP) onNotificationTap()
            return true
        }
    }

    // ---- the dashboards ----

    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Math.round(luna.px(TOP_PADDING)), 0, 0) }

    /**
     * The open list. A drag scrolls it, and on letting go it snaps to a whole row counted from
     * the bottom (DashboardWindowContainer::restoreNonDeletedItems); there is no fling.
     */
    private val scroller = object : android.widget.ScrollView(context) {
        override fun fling(velocityY: Int) {}
        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) { super.onScrollChanged(l, t, oldl, oldt); this@NotificationArea.invalidate() }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val r = super.onTouchEvent(e)
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) snap()
            return r
        }
        fun snap() {
            val bottom = rows.height - height
            if (bottom <= 0) return
            val row = luna.px(ROW_H)
            val target = (bottom - Math.round((bottom - scrollY).toFloat() / row) * row).coerceIn(0, bottom)
            ValueAnimator.ofInt(scrollY, target).apply {
                duration = SNAP_MS; interpolator = Easing.OutCubic
                addUpdateListener { scrollTo(0, it.animatedValue as Int) }
                start()
            }
        }
    }.apply { isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER }

    override var isOpen = false
        private set

    // ---- alerts ----

    private val alerts = LinkedHashMap<AppWindow, Int>()
    private val alertFrame = FrameLayout(context)

    init {
        setBackgroundColor(Color.BLACK)
        setWillNotDraw(false)
        scroller.addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        addView(alertFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, barPx))
        scroller.visibility = View.INVISIBLE
        alertFrame.visibility = View.INVISIBLE
    }

    override fun add(w: AppWindow) {
        rows.addView(Row(w), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, luna.px(ROW_H)))
        // DashboardWindowContainer::addWindow: focused if the area is open, not if it isn't.
        w.sendFocus(isOpen)
        relayout()
        if (isOpen) scrollToNewest()
    }

    /** A dashboard or an alert going: both hosts' remove, as one area is both. */
    override fun remove(w: AppWindow) {
        for (i in 0 until rows.childCount) {
            val r = rows.getChildAt(i) as Row
            if (r.window == w) { r.removeView(w); rows.removeViewAt(i); break }
        }
        if (alerts.remove(w) != null) alertFrame.removeView(w)
        relayout()
    }

    override fun open() {
        if (rows.childCount == 0 || alerts.isNotEmpty()) return
        isOpen = true
        for (i in 0 until rows.childCount) (rows.getChildAt(i) as Row).window.sendFocus(true)
        relayout()
        scrollToNewest()
    }

    override fun close() {
        if (!isOpen) return
        isOpen = false
        for (i in 0 until rows.childCount) (rows.getChildAt(i) as Row).window.sendFocus(false)
        relayout()
    }

    /** DWMStateOpen: setScrollBottom(0), the newest dashboard at the bottom of the view. */
    private fun scrollToNewest() = scroller.post { scroller.scrollTo(0, (rows.height - scroller.height).coerceAtLeast(0)) }

    override val showing get() = alerts.isNotEmpty()
    override val windows: List<AppWindow> get() = alerts.keys.toList()
    override fun newest(): AppWindow? = alerts.keys.lastOrNull()

    override fun show(w: AppWindow, height: Int) {
        if (isOpen) close()
        alerts[w] = height
        alertFrame.addView(w, LayoutParams(LayoutParams.MATCH_PARENT, luna.px(height)))
        relayout()
    }

    // ---- the negative space ----

    /** The screen's height sets the open list's most: a turn or a fold is laid out again. */
    private val screenWatch = OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        if (bottom - top != oldBottom - oldTop) post { relayout() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); (parent as? View)?.addOnLayoutChangeListener(screenWatch) }
    override fun onDetachedFromWindow() { (parent as? View)?.removeOnLayoutChangeListener(screenWatch); super.onDetachedFromWindow() }

    private val maxOpen get() = Math.round((parent as? View)?.height?.let { it * MAX_RATIO } ?: 0f)
    private val contentHeight get() = Math.round(luna.px(TOP_PADDING)) + rows.childCount * luna.px(ROW_H)

    /** What the area shows, by DashboardWindowManager's states: alert, open, or closed with content or without. */
    private fun target(): Int = when {
        suspended -> 0
        alerts.isNotEmpty() -> luna.px(alerts.values.last())
        fullScreen -> if (banner != null) barPx else 0
        isOpen && rows.childCount > 0 -> minOf(contentHeight, maxOpen)
        banner != null || icons.isNotEmpty() -> barPx
        else -> 0
    }

    /** The space the shell gives up for [h]: none over a full-screen card, where a banner is laid over the page. */
    private fun spaceFor(h: Int) = if (fullScreen && alerts.isEmpty()) 0 else h

    private var goal = 0
    private var shown = 0f
    private var anim: ValueAnimator? = null

    private fun relayout(immediate: Boolean = false) {
        // What is on top: the alert, the open list, or the bar. DashboardWindowManager raises
        // one over the others as the state changes, so a closing list is gone at once and the
        // bar rides down in its place.
        val alert = alerts.isNotEmpty() && !suspended
        val open = !alert && isOpen && !suspended
        alerts.keys.forEachIndexed { i, w -> w.visibility = if (i == alerts.size - 1) View.VISIBLE else View.INVISIBLE }
        alertFrame.visibility = if (alert) View.VISIBLE else View.INVISIBLE
        scroller.visibility = if (open) View.VISIBLE else View.INVISIBLE
        bar.visibility = if (!alert && !open) View.VISIBLE else View.INVISIBLE
        val t = target()
        // The list keeps its open height while it is hidden, so its pages are laid out and drawn
        // before it opens, as the drop-down's are on a tablet.
        val listH = minOf(contentHeight, maxOpen)
        (scroller.layoutParams as LayoutParams).let { if (it.height != listH) { it.height = listH; scroller.layoutParams = it } }
        setBackgroundColor(if (fullScreen && !alert) Color.TRANSPARENT else Color.BLACK)
        if (t == goal) { if (anim == null) onSpace(spaceFor(t)); return }
        goal = t
        anim?.removeAllListeners(); anim?.cancel(); anim = null
        val from = shown
        if (t < from) onSpace(spaceFor(t))  // shrinking: the shell grows into the space first, behind the area
        if (immediate || !isLaidOut) { place(t.toFloat(), t); onSpace(spaceFor(t)); return }
        val h = maxOf(from, t.toFloat()).toInt()
        anim = ValueAnimator.ofFloat(from, t.toFloat()).apply {
            duration = SPACE_MS; interpolator = Easing.OutCubic
            addUpdateListener { place(it.animatedValue as Float, h) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    anim = null
                    place(t.toFloat(), t)
                    if (t >= from) onSpace(spaceFor(t))  // grown: the shell gives up the space now the area covers it
                }
            })
            start()
        }
    }

    /** Shows [visible] px of the area, laid out [h] high: its top edge carries everything in it. */
    private fun place(visible: Float, h: Int) {
        shown = visible
        val lp = layoutParams as? LayoutParams
        if (lp != null && lp.height != h) { lp.height = h; layoutParams = lp }
        translationY = h - visible
        visibility = if (visible > 0f) View.VISIBLE else View.INVISIBLE
    }

    // ---- the bottom edge ----

    /**
     * What the area pushed up from the bottom of the screen (the shell's root). On the Pre3 the
     * swipe up came from the gesture area below the screen, so the area never stood in its way;
     * here the screen's bottom edge is the gesture area too. A swipe up that starts on the bar,
     * or in the bottom [CardLayer.Params.EDGE] px of the open list or an alert, is handed to this
     * view as one that started at its own bottom edge, where the card layer takes it (a maximized
     * card goes to the card view; the open list's scrim closes it first, as Home does). A tap on
     * the bar stays the bar's, and the list still scrolls.
     */
    var edgeTarget: View? = null
    private var edgeDownX = 0f
    private var edgeDownY = 0f
    /** 0: not an edge touch; 1: an edge touch, not yet a swipe; 2: a swipe, handed over. */
    private var edgeState = 0

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            edgeDownX = e.rawX; edgeDownY = e.rawY
            val screen = parent as? View
            val at = IntArray(2).also { screen?.getLocationOnScreen(it) }
            val band = if (bar.visibility == View.VISIBLE) barPx.toFloat() else luna.px(CardLayer.Params.EDGE)
            edgeState = if (edgeTarget != null && screen != null && e.rawY >= at[1] + screen.height - band) 1 else 0
        }
        if (edgeState == 1 && e.actionMasked == MotionEvent.ACTION_MOVE) {
            val up = edgeDownY - e.rawY
            if (up >= luna.px(CardLayer.Params.TRIGGER) && up > abs(e.rawX - edgeDownX)) {
                edgeState = 2
                // The bar, a row or an alert had the touch so far: it ends for them.
                MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }.also { super.dispatchTouchEvent(it); it.recycle() }
                handOver(e, MotionEvent.ACTION_DOWN, edgeDownX, edgeDownY)
            }
        }
        if (edgeState == 2) {
            handOver(e, e.actionMasked, e.rawX, e.rawY)
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) edgeState = 0
            return true
        }
        return super.dispatchTouchEvent(e)
    }

    /** The swipe as [edgeTarget] would have had it: from its bottom edge, by as far as the finger has gone. */
    private fun handOver(e: MotionEvent, action: Int, rawX: Float, rawY: Float) {
        val t = edgeTarget ?: return
        val at = IntArray(2).also { t.getLocationOnScreen(it) }
        MotionEvent.obtain(e.downTime, e.eventTime, action, rawX - at[0], t.height - 1 + (rawY - edgeDownY), 0)
            .also { t.dispatchTouchEvent(it); it.recycle() }
    }

    // ---- the scroll masks ----

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        if (scroller.visibility != View.VISIBLE) return
        // DashboardWindowContainer::paint: the top mask while there are rows above, the bottom
        // one 10 Pre3 px up from the bottom while there are rows below; tiled across.
        if (scroller.canScrollVertically(-1)) luna.pre3Image("dashboard-mask-top.png")?.let { m ->
            c.drawBitmap(m, null, RectF(0f, 0f, width.toFloat(), m.height.toFloat()), null)
        }
        if (scroller.canScrollVertically(1)) luna.pre3Image("dashboard-mask-bottom.png")?.let { m ->
            val y = scroller.height - luna.px(MASK_CORRECTION)
            c.drawBitmap(m, null, RectF(0f, y, width.toFloat(), y + m.height), null)
        }
    }

    /**
     * One dashboard, the full width. Dragged sideways, either way, it follows the finger, and
     * past a quarter of its width it goes on off that side by one and a half widths
     * (DashboardWindowContainer::triggerItemDelete). A dashboard opened with
     * `webosDragMode: "manual"` handles its own swipes past its 50 px badge
     * (DashboardWindowContainer::mousePressEvent).
     */
    @SuppressLint("ViewConstructor")
    inner class Row(val window: AppWindow) : FrameLayout(context) {
        private var downX = 0f
        private var downY = 0f
        private var mine = true
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val manual get() = window.attributes.optString("webosDragMode") == "manual"

        init { addView(window, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; mine = !manual || e.x <= luna.px(BADGE_W) }
                MotionEvent.ACTION_MOVE -> if (mine && abs(e.rawX - downX) > slop && abs(e.rawX - downX) > abs(e.rawY - downY)) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> translationX = e.rawX - downX
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (abs(translationX) > width / 4f) {
                        animate().translationX(Math.signum(translationX) * width * 1.5f).setDuration(DISMISS_MS).setInterpolator(Easing.Linear)
                            .withEndAction { onDismiss(window) }.start()
                    } else animate().translationX(0f).setDuration(DISMISS_MS).start()
                }
            }
            return true
        }
    }
}
