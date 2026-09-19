package org.webosarchive.lunacy.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import org.webosarchive.lunacy.card.AppWindow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A card: one app window, laid out at its maximized size and scaled down in the card view. */
@SuppressLint("ViewConstructor")
class Card(context: Context, val window: AppWindow, cornerRadius: Float) : FrameLayout(context) {
    /** Card-view transform, animated by CardLayer. */
    var cx = 0f; var cy = 0f; var scale = 1f; var lift = 0f; var fade = 1f

    init {
        addView(window, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        outlineProvider = object : ViewOutlineProvider() {
            // Small corners in card view; maximized cards are plain rectangles (docs/luna-shell-reference.md §2.4).
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, if (scale >= 0.999f) 0f else cornerRadius)
        }
        clipToOutline = true
    }
}

/**
 * The card layer: every card, in card view or maximized. Cards keep their maximized size and
 * are scaled, as Luna did, so apps never see a resize when the card view opens.
 * Geometry and timings: docs/luna-shell-reference.md.
 */
@SuppressLint("ViewConstructor")
class CardLayer(context: Context, private val luna: Luna, private val listener: Listener) : ViewGroup(context) {
    interface Listener {
        fun onMaximized(card: Card)
        fun onCardView()
        fun onThrownAway(card: Card)
    }

    /**
     * Values from the reference TouchPad (/etc/palm/luna-platform.conf, lunaAnimations.conf) and
     * LunaCE's CardWindowManager; see docs/luna-shell-reference.md §0 and §2. Lengths in TouchPad px.
     */
    object Params {
        const val ACTIVE_RATIO = 0.55f        // ActiveCardWindowRatio
        const val NON_ACTIVE_RATIO = 0.50f    // NonActiveCardWindowRatio
        const val PILL_RESERVE = 48f          // space kept for the Just Type pill
        const val ORIGIN_RATIO = 0.40f        // kWindowOriginRatio: card centre within the rest
        const val MIN_SCALE = 0.26f           // kMinimumWindowScale
        const val GAP = 30f                   // GapBetweenCardGroups
        const val CORNER = 9f                 // corner half-axis, unscaled card px
        const val SHADOW_GROW = 20f           // card-shadow-tile drawn 20 px outside the card
        const val SHADOW_DROP = 5f            // … and 5 px lower
        const val TAP_RADIUS = 25f            // TapRadiusMax
        const val AXIS_LOCK = 0.866f          // horizontal if |dx| > 0.866·|dy|
        const val MAXIMIZE_MS = 300L          // cardMaximizeDuration, OutQuart
        const val MINIMIZE_MS = 200L          // active group on minimize, OutCubic (hard-coded)
        const val SLIDE_MS = 300L             // cardSlideDuration, OutQuart
        const val DELETE_MS = 300L            // cardDeleteDuration, OutCubic
        const val EDGE = 15f                  // kGestureBorderSize
        const val TRIGGER = 15f               // kGestureTriggerDistance
        // Throw away: CardWindowManager kVelocityThreshold/kDistanceThreshold/kMinimumVelocity,
        // in Luna's flick units (about a third of px/s).
        const val THROW_VELOCITY = -1100f
        const val THROW_DISTANCE = -50f
        const val THROW_MIN_VELOCITY = -500f
    }

    val cards = ArrayList<Card>()
    var maximized: Card? = null
        private set
    /** Card-view scroll position, in cards. */
    private var position = 0f
    private var anim: ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingMin = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4

    init { clipChildren = false; setWillNotDraw(false) }

    // ---- layout ----

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (c in cards) c.layout(0, 0, r - l, b - t)
        applyTransforms()
    }

    override fun onMeasure(w: Int, h: Int) {
        val ws = MeasureSpec.getSize(w); val hs = MeasureSpec.getSize(h)
        for (c in cards) c.measure(MeasureSpec.makeMeasureSpec(ws, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(hs, MeasureSpec.EXACTLY))
        setMeasuredDimension(ws, hs)
    }

    /** Rotation or resize: put every card where the current state says it belongs. */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        anim?.cancel(); anim = null
        drag = Drag.NONE
        val max = maximized
        if (max != null) {
            cards.forEachIndexed { i, c -> val (x, y, s) = cardViewTarget(i); c.cx = x; c.cy = y; c.scale = s; c.lift = 0f; c.fade = 1f }
            max.cx = w / 2f; max.cy = h / 2f; max.scale = 1f
        } else settleCardView()
    }

    private fun reserve() = luna.px(Params.PILL_RESERVE)
    private fun activeScale() = max(Params.MIN_SCALE, (height - reserve()) * Params.ACTIVE_RATIO / height)
    private fun nonActiveScale() = max(Params.MIN_SCALE, (height - reserve()) * Params.NON_ACTIVE_RATIO / height)
    /** Distance between the centres of two side cards. */
    private fun pitch() = width * nonActiveScale() + luna.px(Params.GAP)

    /**
     * Where each card sits in the card view for the current scroll position: the active card
     * at activeScale, the others at nonActiveScale with GAP between their edges.
     */
    private fun cardViewTarget(i: Int): Triple<Float, Float, Float> {
        val d = i - position
        val t = min(abs(d), 1f)
        val a = activeScale(); val n = nonActiveScale()
        val s = a + (n - a) * t
        val x = width / 2f + d * pitch() + Math.signum(d) * t * width * (a - n) / 2
        val y = reserve() + (height - reserve()) * Params.ORIGIN_RATIO
        return Triple(x, y, s)
    }

    private fun applyTransforms() {
        for (c in cards) {
            c.pivotX = width / 2f; c.pivotY = height / 2f
            c.scaleX = c.scale; c.scaleY = c.scale
            c.translationX = c.cx - width / 2f
            c.translationY = c.cy - height / 2f + c.lift
            c.alpha = c.fade
            c.invalidateOutline()
        }
        invalidate()
    }

    private fun settleCardView() {
        cards.forEachIndexed { i, c -> val (x, y, s) = cardViewTarget(i); c.cx = x; c.cy = y; c.scale = s; c.lift = 0f; c.fade = 1f }
        applyTransforms()
    }

    /** Luna's card shadow, drawn behind each card at its transformed bounds. */
    override fun dispatchDraw(c: Canvas) {
        val shadow = luna.image("card-shadow-tile.png")
        if (shadow != null) for (card in cards) {
            if (card.visibility != View.VISIBLE || card.scale >= 0.999f) continue
            // card-shadow-tile.png as a nine-patch (43 px insets), 20 px outside the card and
            // 5 px down, in the card's own scaled coordinates (reference §2.4).
            val s = card.scale; val g = luna.px(Params.SHADOW_GROW); val drop = luna.px(Params.SHADOW_DROP)
            val halfW = (width / 2f + g) * s; val halfH = (height / 2f + g) * s
            val cy = card.cy + card.lift + drop * s
            // 43 of 87 px in the original: keep at least a 1-px stretchable middle after scaling,
            // or the edge slices vanish and only the corners draw.
            val inset = (shadow.width - 1) / 2
            val p = android.graphics.Paint().apply { alpha = (card.fade * 255).toInt(); isFilterBitmap = true }
            val d = inset * s
            Luna.drawNineSlice(c, shadow, RectF(card.cx - halfW, cy - halfH, card.cx + halfW, cy + halfH), inset, inset, inset, inset, p, d, d, d, d)
        }
        super.dispatchDraw(c)
    }

    // ---- state changes ----

    fun add(card: Card) {
        cards += card
        addView(card)
        card.visibility = View.VISIBLE
    }

    fun remove(card: Card) {
        val i = cards.indexOf(card)
        if (i < 0) return
        cards.removeAt(i); removeView(card)
        if (maximized == card) maximized = null
        position = min(position, max(cards.size - 1f, 0f))
    }

    /** Animates from wherever the cards are now to their targets. */
    private fun animateTo(ms: Long, target: (Int) -> Triple<Float, Float, Float>, easing: android.animation.TimeInterpolator = Easing.OutQuart, done: () -> Unit = {}) {
        anim?.cancel()
        val from = cards.map { floatArrayOf(it.cx, it.cy, it.scale, it.lift) }
        val to = cards.indices.map { target(it) }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms; interpolator = easing
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                cards.forEachIndexed { i, c ->
                    if (i >= from.size) return@forEachIndexed
                    c.cx = from[i][0] + (to[i].first - from[i][0]) * f
                    c.cy = from[i][1] + (to[i].second - from[i][1]) * f
                    c.scale = from[i][2] + (to[i].third - from[i][2]) * f
                    c.lift = from[i][3] * (1 - f)
                }
                applyTransforms()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { anim = null; done() }
            })
            start()
        }
    }

    fun maximize(card: Card) {
        val i = cards.indexOf(card)
        if (i < 0) return
        position = i.toFloat()
        cards.forEach { it.visibility = View.VISIBLE }
        card.bringToFront()
        animateTo(Params.MAXIMIZE_MS, { j -> if (j == i) Triple(width / 2f, height / 2f, 1f) else cardViewTarget(j) }, Easing.OutQuart) {
            maximized = card
            cards.forEach { if (it != card) it.visibility = View.INVISIBLE }
            listener.onMaximized(card)
        }
    }

    fun showCardView() {
        val was = maximized
        maximized = null
        cards.forEach { it.visibility = View.VISIBLE }
        if (was != null) position = cards.indexOf(was).toFloat()
        animateTo(Params.MINIMIZE_MS, ::cardViewTarget, Easing.OutCubic)
        listener.onCardView()
    }

    /** Puts a newly added card straight into the maximized state, as a launch does. */
    fun openMaximized(card: Card) {
        if (width == 0) { post { openMaximized(card) }; return }
        settleCardView()
        // A new card starts full size just below the screen and maximizes upward (reference §2.5).
        card.cx = width / 2f; card.cy = height * 1.5f; card.scale = 1f
        applyTransforms()
        maximize(card)
    }

    private fun throwAway(card: Card, velocity: Float) {
        val start = card.lift
        val end = -(height.toFloat())
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Params.DELETE_MS; interpolator = Easing.OutCubic
            addUpdateListener { a -> val f = a.animatedValue as Float; card.lift = start + (end - start) * f; applyTransforms() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    anim = null
                    remove(card)
                    listener.onThrownAway(card)
                    animateTo(Params.SLIDE_MS, ::cardViewTarget)
                }
            })
            start()
        }
    }

    // ---- touch ----

    private enum class Drag { NONE, UNDECIDED, SCROLL, THROW, MINIMIZE }
    private var drag = Drag.NONE
    private var downX = 0f; private var downY = 0f; private var downPos = 0f
    private var dragCard: Card? = null
    private var velocity: VelocityTracker? = null

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        val max = maximized
        if (max == null) return true  // card view: the shell owns every touch
        // Maximized: only a swipe up from the bottom edge is ours; everything else goes to the app.
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drag = if (e.y > height - luna.px(Params.EDGE)) Drag.UNDECIDED else Drag.NONE
                downX = e.x; downY = e.y
            }
            MotionEvent.ACTION_MOVE -> if (drag == Drag.UNDECIDED && downY - e.y >= luna.px(Params.TRIGGER) && downY - e.y > abs(e.x - downX)) {
                drag = Drag.MINIMIZE; dragCard = max
                cards.forEach { it.visibility = View.VISIBLE }
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (velocity == null) velocity = VelocityTracker.obtain()
        velocity!!.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel(); anim = null
                drag = Drag.UNDECIDED; downX = e.x; downY = e.y; downPos = position
                dragCard = cardAt(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (drag == Drag.UNDECIDED && Math.hypot(dx.toDouble(), dy.toDouble()) > luna.px(Params.TAP_RADIUS)) {
                    drag = if (abs(dx) > Params.AXIS_LOCK * abs(dy)) Drag.SCROLL
                    else if (dragCard != null) Drag.THROW else Drag.NONE
                }
                when (drag) {
                    Drag.SCROLL -> {
                        position = (downPos - dx / pitch()).coerceIn(-0.3f, cards.size - 0.7f)
                        settleCardView()
                    }
                    Drag.THROW -> { dragCard?.lift = dy; applyTransforms() }
                    Drag.MINIMIZE -> minimizeFollow(dy)
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity!!.computeCurrentVelocity(1000)
                val vx = velocity!!.xVelocity; val vy = velocity!!.yVelocity
                velocity!!.recycle(); velocity = null
                val card = dragCard
                when (drag) {
                    Drag.SCROLL -> {
                        var target = position.roundToInt()
                        if (abs(vx) > flingMin) target = if (vx < 0) downPos.roundToInt() + 1 else downPos.roundToInt() - 1
                        scrollTo(target.coerceIn(0, max(cards.size - 1, 0)))
                    }
                    Drag.THROW -> if (card != null) {
                        // Luna's rule: far enough, fast enough, and faster the shorter the drag.
                        val dist = card.lift / luna.density
                        val v = vy / luna.density / 3f
                        val flung = dist < Params.THROW_DISTANCE && v < Params.THROW_MIN_VELOCITY &&
                            v < (Params.THROW_VELOCITY * Params.THROW_DISTANCE) / dist
                        val centreAboveTop = card.cy + card.lift < 0
                        if (flung || centreAboveTop) throwAway(card, vy)
                        else animateTo(Params.SLIDE_MS, ::cardViewTarget)
                    }
                    Drag.MINIMIZE -> if (card != null) {
                        if (vy < -flingMin || (downY - e.y) > height * 0.15f) showCardView() else maximize(card)
                    }
                    Drag.UNDECIDED -> if (e.actionMasked == MotionEvent.ACTION_UP && card != null) {
                        val i = cards.indexOf(card)
                        if (i == position.roundToInt()) maximize(card) else scrollTo(i)
                    }
                    Drag.NONE -> {}
                }
                drag = Drag.NONE; dragCard = null
            }
        }
        return true
    }

    /** Fluid minimize: the maximized card shrinks toward its card-view slot as the finger rises. */
    private fun minimizeFollow(dy: Float) {
        val card = dragCard ?: return
        val i = cards.indexOf(card)
        val f = (-dy / (height * 0.5f)).coerceIn(0f, 1f)
        val (x, y, s) = cardViewTarget(i)
        card.cx = width / 2f + (x - width / 2f) * f
        card.cy = height / 2f + (y - height / 2f) * f
        card.scale = 1f + (s - 1f) * f
        applyTransforms()
    }

    private fun scrollTo(i: Int) {
        val start = position
        anim?.cancel()
        anim = ValueAnimator.ofFloat(start, i.toFloat()).apply {
            duration = Params.SLIDE_MS; interpolator = Easing.OutQuart
            addUpdateListener { a -> position = a.animatedValue as Float; settleCardView() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { anim = null } })
            start()
        }
    }

    private fun cardAt(x: Float, y: Float): Card? = cards.lastOrNull { c ->
        val w = width * c.scale; val h = height * c.scale
        x >= c.cx - w / 2 && x <= c.cx + w / 2 && y >= c.cy - h / 2 && y <= c.cy + h / 2
    }
}
