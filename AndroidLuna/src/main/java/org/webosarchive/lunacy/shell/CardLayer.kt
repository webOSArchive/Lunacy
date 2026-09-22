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
class Card(
    context: Context,
    val window: AppWindow,
    cornerRadius: Float,
    /**
     * The size the page is given, in device pixels, when it isn't the whole card: webOS's
     * emulated card, the phone-sized window a tablet gave an app that never said it had been
     * laid out for one (see [org.webosarchive.lunacy.card.EmulatedCard]). Null for an
     * ordinary card, which fills its own bounds.
     */
    emulatedSize: Pair<Int, Int>? = null,
    /** LunaCE's emucard-device-frame.png, drawn behind an emulated card's page. */
    private val frame: android.graphics.Bitmap? = null,
    /** The loading placeholder, over the page until the app has drawn; see [CardSplash]. */
    private val splash: CardSplash? = null,
) : FrameLayout(context) {
    /** The app counts as ready (stageReady, or loaded and out of time); see [CardLayer.ready]. */
    var ready = false
        set(v) { field = v; lift() }
    /** The page has put a frame on the screen. */
    var drawn = false
        set(v) { field = v; lift() }
    /**
     * The placeholder fades once the app is ready *and* has drawn: LunaSysMgr took its loading
     * overlay away when the window was added, so a card waiting in the card view keeps the
     * app's pulsing icon even if the page behind it has painted, and a card that is ready but
     * blank keeps it until there is something to show. Does nothing once it has gone.
     */
    private fun lift() { if (ready && drawn) splash?.dismiss() }

    /**
     * The phone an emulated card's page sits in, drawn centred on the page exactly as
     * LunaSysMgr draws it: `EmulatedCardWindow::paintBase` centres emucard-device-frame.png
     * on the card's own rect. 480 x 740 around a 320 x 452 page. Drawn onto the canvas rather
     * than put in an ImageView, which would scale it for the screen's density.
     */
    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        frame?.let {
            canvas.drawBitmap(it, (width - it.width) / 2f, (height - it.height) / 2f, null)
        }
        super.dispatchDraw(canvas)
        // Black at (1 - dimming) over an opaque card is the card's RGB times dimming, which is
        // what LunaSysMgr's corner shader did with its Active uniform.
        if (dimming < 1f) canvas.drawColor(android.graphics.Color.argb(Math.round((1f - dimming) * 255), 0, 0, 0))
    }

    /**
     * Brightness, 1 to CardDimmPercentage (0.8). LunaSysMgr dimmed a card when it stopped
     * being the active one and brightened the one that became active
     * (SystemUiController::setActiveCardWindow); a card that has never been active is not
     * dimmed. Measured on the reference TouchPad: a side card's #1d4d5c reads (23, 61, 73).
     */
    var dimming = 1f
        set(v) { if (field != v) { field = v; invalidate() } }
    private var dimAnim: ValueAnimator? = null

    /** cardDimmingDuration 300 ms, cardDimmingCurve 6 (OutCubic). */
    fun setDimmed(dim: Boolean) {
        val to = if (dim) CardLayer.Params.DIMMED else 1f
        dimAnim?.cancel()
        if (dimming == to) return
        dimAnim = ValueAnimator.ofFloat(dimming, to).apply {
            duration = CardLayer.Params.DIM_MS; interpolator = Easing.OutCubic
            addUpdateListener { dimming = it.animatedValue as Float }
            start()
        }
    }
    /** Card-view transform, animated by CardLayer. */
    var cx = 0f; var cy = 0f; var scale = 1f; var lift = 0f; var fade = 1f

    /**
     * Laid out over the status bar's space as well as below it: the app asked for full screen
     * (PalmSystem.enableFullScreenMode). LunaSysMgr grew the card's window by the bar's 28 px
     * and kept it that size in the card view too; the bar itself only goes while the card is
     * maximized, which is the shell's business. Never true for an emulated card.
     */
    var fullScreen = false
        set(v) { if (field != v) { field = v; requestLayout() } }

    init {
        if (emulatedSize == null) {
            addView(window, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            // Centred in the card, which already begins below the status bar - the reference
            // device puts the page at x 224, y 300 of its 768 x 1024 screen, which is centred
            // in what is left once the 28 px status bar is taken off.
            addView(window, LayoutParams(emulatedSize.first, emulatedSize.second, android.view.Gravity.CENTER))
            // The tablet's screen around the phone, as the device draws it.
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        // Over the page, and the same size as it, so an emulated card's placeholder sits in
        // the phone rather than across the whole card.
        splash?.let { addView(it, LayoutParams(window.layoutParams.width, window.layoutParams.height,
            (window.layoutParams as LayoutParams).gravity)) }
        outlineProvider = object : ViewOutlineProvider() {
            // Small corners in card view; maximized cards are plain rectangles (Docs/luna-shell-reference.md §2.4).
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, if (scale >= 0.999f) 0f else cornerRadius)
        }
        clipToOutline = true
    }
}

/**
 * The card layer: every card, in card view or maximized. Cards keep their maximized size and
 * are scaled, as Luna did, so apps never see a resize when the card view opens.
 * Geometry and timings: Docs/luna-shell-reference.md.
 */
@SuppressLint("ViewConstructor")
class CardLayer(context: Context, private val luna: Luna, private val listener: Listener) : ViewGroup(context) {
    interface Listener {
        fun onMaximized(card: Card)
        fun onCardView()
        /**
         * A launching card ran out of time before its app drew, and has gone to the card view
         * to wait there; it maximizes by itself when the app is ready.
         */
        fun onPreparing(card: Card)
        fun onThrownAway(card: Card)
    }

    /**
     * Values from the reference TouchPad (/etc/palm/luna-platform.conf, lunaAnimations.conf) and
     * LunaCE's CardWindowManager; see Docs/luna-shell-reference.md §0 and §2. Lengths in TouchPad px.
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
        const val PREPARE_MS = 150L           // cardPrepareAddDuration: held off-screen for the first frame
        const val ADD_MAX_MS = 750L           // cardAddMaxDuration: then this long more before the card view
        const val EDGE = 15f                  // kGestureBorderSize
        const val TRIGGER = 15f               // kGestureTriggerDistance
        // Throw away: CardWindowManager kVelocityThreshold/kDistanceThreshold/kMinimumVelocity,
        // in Luna's flick units (about a third of px/s).
        const val THROW_VELOCITY = -1100f
        const val THROW_DISTANCE = -50f
        const val THROW_MIN_VELOCITY = -500f
        const val DIMMED = 0.8f               // CardDimmPercentage
        const val DIM_MS = 300L               // cardDimmingDuration, OutCubic
    }

    val cards = ArrayList<Card>()
    /** Plays one of LunaSysMgr's feedback sounds; see [Sounds.feedback]. */
    var feedback: (String) -> Unit = {}
    /**
     * LunaSysMgr's angry-card sounds - a stretch as the card is pulled a way down, and a bird
     * as it goes - are only for UI orientation "Down" (CardWindowManager::playAngryCardSounds).
     * That is measured against the TouchPad's panel, which is natively landscape, so it means
     * landscape upside down, never portrait (codepoet: only ever heard in landscape). The
     * shell says which orientation that is here; see ShellActivity.screenOrientation.
     */
    var upsideDown: () -> Boolean = { false }
    private var playedStretch = false
    /**
     * The status bar's height. The layer runs the whole height of the screen so that a
     * full-screen card can be laid out under the bar; every other card, and the card view's
     * geometry, start this far down.
     */
    var inset = 0
        set(v) { if (field != v) { field = v; requestLayout() } }
    /** The height a card that isn't full screen is given: what the card view is laid out in. */
    val areaHeight get() = height - inset
    /** Where a card's top edge is laid out. */
    private fun top(c: Card) = if (c.fullScreen) 0 else inset
    /** A card's own centre, which is where it sits maximized. */
    private fun naturalY(c: Card) = top(c) + (height - top(c)) / 2f
    var maximized: Card? = null
        private set
    /** The card being maximized, while its animation runs. */
    private var activating: Card? = null
    /**
     * The card that owns the screen: the maximized one, or the one on its way there. webOS
     * drew the same distinction - CardWindow::slotShowIME asks for the *active* window, not
     * the maximized one, "to handle cases where keyboard is being brought up when an app is
     * being maximized", which is exactly what an app that focuses a field as its first scene
     * is built does.
     */
    val active: Card? get() = maximized ?: activating ?: lastLaunched?.takeIf { it in launching }
    /**
     * LunaSysMgr's active card window: the card the card view is centred on, or the one on its
     * way to the screen. Changing it dims the one before; see [Card.dimming].
     */
    private var current: Card? = null
    private fun setCurrent(c: Card?) {
        if (c == current) return
        current?.takeIf { it in cards }?.setDimmed(true)
        current = c
        c?.setDimmed(false)
    }
    /** The card nearest the centre of the card view becomes the active one. */
    private fun settleCurrent() { setCurrent(cards.getOrNull(position.roundToInt().coerceIn(0, max(cards.size - 1, 0)))) }

    /** Card-view scroll position, in cards. */
    private var position = 0f
    private var anim: ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingMin = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4

    init { clipChildren = false; setWillNotDraw(false) }

    // ---- layout ----

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (c in cards) c.layout(0, top(c), r - l, b - t)
        // A maximized card that has just gone to or from full screen has a new centre.
        maximized?.let { if (anim == null && drag == Drag.NONE) { it.cx = (r - l) / 2f; it.cy = naturalY(it) } }
        applyTransforms()
    }

    override fun onMeasure(w: Int, h: Int) {
        val ws = MeasureSpec.getSize(w); val hs = MeasureSpec.getSize(h)
        for (c in cards) c.measure(MeasureSpec.makeMeasureSpec(ws, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(hs - top(c), MeasureSpec.EXACTLY))
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
            max.cx = w / 2f; max.cy = naturalY(max); max.scale = 1f
        } else settleCardView()
    }

    private fun reserve() = luna.px(Params.PILL_RESERVE)
    private fun activeScale() = max(Params.MIN_SCALE, (areaHeight - reserve()) * Params.ACTIVE_RATIO / areaHeight)
    private fun nonActiveScale() = max(Params.MIN_SCALE, (areaHeight - reserve()) * Params.NON_ACTIVE_RATIO / areaHeight)
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
        val y = inset + reserve() + (areaHeight - reserve()) * Params.ORIGIN_RATIO
        return Triple(x, y, s)
    }

    private fun applyTransforms() {
        for (c in cards) {
            c.pivotX = c.width / 2f; c.pivotY = c.height / 2f
            c.scaleX = c.scale; c.scaleY = c.scale
            c.translationX = c.cx - (c.left + c.width / 2f)
            c.translationY = c.cy - (c.top + c.height / 2f) + c.lift
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
            val halfW = (card.width / 2f + g) * s; val halfH = (card.height / 2f + g) * s
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
        launching.remove(card)
        if (maximized == card) maximized = null
        if (activating == card) activating = null
        position = min(position, max(cards.size - 1f, 0f))
        if (current == card) { current = null; settleCurrent() }
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
        launching.remove(card)
        position = i.toFloat()
        setCurrent(card)
        cards.forEach { it.visibility = View.VISIBLE }
        card.bringToFront()
        activating = card
        animateTo(Params.MAXIMIZE_MS, { j -> if (j == i) Triple(width / 2f, naturalY(card), 1f) else cardViewTarget(j) }, Easing.OutQuart) {
            activating = null
            maximized = card
            cards.forEach { if (it != card) it.visibility = View.INVISIBLE }
            listener.onMaximized(card)
        }
    }

    fun showCardView() {
        val was = maximized
        maximized = null
        activating = null
        cards.forEach { it.visibility = View.VISIBLE }
        if (was != null) position = cards.indexOf(was).toFloat()
        animateTo(Params.MINIMIZE_MS, ::cardViewTarget, Easing.OutCubic)
        listener.onCardView()
    }

    /** Launching cards whose apps haven't drawn yet: held off-screen, or waiting in the card view. */
    private val launching = HashSet<Card>()
    /** The newest of them: the card on its way to owning the screen, for [active]. */
    private var lastLaunched: Card? = null

    /**
     * A launch, as LunaSysMgr ran one (reference §2.5, measured on the reference TouchPad with
     * Workbench/probe's slowprobe). The new card waits full size just below the screen for its
     * app to be ready - cardPrepareAddDuration and then cardAddMaxDuration, 900 ms in all - and
     * maximizes upward the moment it is. An app that takes longer gets its card slid up into
     * the card view instead, where it pulses with the app's icon until the app is ready, and
     * then maximizes by itself. [ready] is called by the shell; see [ready].
     */
    fun openLaunching(card: Card) {
        if (width == 0) { post { openLaunching(card) }; return }
        settleCardView()
        card.cx = width / 2f; card.cy = height * 1.5f; card.scale = 1f
        applyTransforms()
        setCurrent(card)
        if (card.ready) { maximize(card); return }
        launching += card
        lastLaunched = card
        postDelayed({
            if (card in launching && card in cards && activating == null && drag == Drag.NONE) timedOut(card)
        }, Params.PREPARE_MS + Params.ADD_MAX_MS)
    }

    private fun timedOut(card: Card) {
        maximized = null
        position = cards.indexOf(card).toFloat()
        cards.forEach { it.visibility = View.VISIBLE }
        card.bringToFront()
        animateTo(Params.SLIDE_MS, ::cardViewTarget, Easing.OutQuart)
        listener.onPreparing(card)
    }

    /**
     * The card's app is ready. A launching card maximizes now, unless something else has
     * been maximized since or the card view is being handled.
     */
    fun ready(card: Card) {
        card.ready = true
        if (!launching.remove(card) || card !in cards) return
        if (maximized != null && maximized != card) return
        if (drag != Drag.NONE) return
        maximize(card)
    }

    /**
     * Closes a card by sending it off the top of the card area, whichever way it was thrown:
     * LunaSysMgr's closeWindow animates the card until its bottom edge is at the top, over
     * cardDeleteDuration - the angry card pulled off the bottom included.
     */
    private fun throwAway(card: Card, angry: Boolean = false) {
        // lunaSystemSoundAppClose, "appclose", as the card goes.
        feedback(if (angry && upsideDown()) "birdappclose" else "appclose")
        val start = card.lift
        val end = inset - card.height * card.scale / 2f - card.cy
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Params.DELETE_MS; interpolator = Easing.OutCubic
            addUpdateListener { a -> val f = a.animatedValue as Float; card.lift = start + (end - start) * f; applyTransforms() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    anim = null
                    remove(card)
                    listener.onThrownAway(card)
                    settleCurrent()
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
                playedStretch = false
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
                    Drag.THROW -> {
                        dragCard?.lift = dy; applyTransforms()
                        // kAngryCardThreshold: 30 % of half the card area's height.
                        if (!playedStretch && dy > areaHeight / 2f * 0.30f && upsideDown()) { playedStretch = true; feedback("carddrag") }
                    }
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
                        val centreAboveTop = card.cy + card.lift < inset
                        // The angry card: let go with its centre below the bottom of the screen.
                        val centreBelowBottom = card.cy + card.lift > height
                        if (flung || centreAboveTop) throwAway(card)
                        else if (centreBelowBottom) throwAway(card, angry = true)
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
        card.cy = naturalY(card) + (y - naturalY(card)) * f
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
            // The slide's target is the new active card from the start, as switchToNextGroup has it.
            setCurrent(cards.getOrNull(i))
            start()
        }
    }

    private fun cardAt(x: Float, y: Float): Card? = cards.lastOrNull { c ->
        val w = c.width * c.scale; val h = c.height * c.scale
        x >= c.cx - w / 2 && x <= c.cx + w / 2 && y >= c.cy - h / 2 && y <= c.cy + h / 2
    }
}
