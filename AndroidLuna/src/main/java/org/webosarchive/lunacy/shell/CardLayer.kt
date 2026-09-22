package org.webosarchive.lunacy.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
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
    /** An emulated card's chrome: the art, and the app's title for its own status bar. */
    private val luna: Luna? = null,
    private val title: String = "",
) : FrameLayout(context) {
    private val emulated = emulatedSize != null

    /** What a tap on an emulated card's chrome asks for. */
    enum class ChromeAction { APP_MENU, BACK, KEYBOARD }
    var onChrome: (ChromeAction) -> Unit = {}

    /**
     * An emulated card's chrome is up - its own status bar, the gesture strip, the keyboard
     * button, the phone around it - while the card is maximized, and not in the card view
     * (EmulatedCardWindow::setMaximized). The backdrop is #0F0F0F behind the phone, black in
     * the card view (both measured on the reference TouchPad).
     */
    var chromeShown = false
        set(v) {
            if (field == v) return
            field = v
            if (emulated) setBackgroundColor(if (v) android.graphics.Color.rgb(0x0F, 0x0F, 0x0F) else android.graphics.Color.BLACK)
            pressed = null
            invalidate()
        }

    /**
     * In the card view LunaSysMgr drew an emulated card's page larger than the phone, by
     * (2 − the card's scale) - about 1.5 at the card view's size - so it can be read in the
     * little card; maximized it is its own size.
     */
    fun setPageScale(k: Float) {
        if (window.scaleX == k) return
        window.scaleX = k; window.scaleY = k
        splash?.let { it.scaleX = k; it.scaleY = k }
        invalidate()
    }
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
        if (chromeShown) frame?.let {
            canvas.drawBitmap(it, (width - it.width) / 2f, (height - it.height) / 2f, null)
        }
        super.dispatchDraw(canvas)
        if (emulated) drawChrome(canvas)
        // Black at (1 - dimming) over an opaque card is the card's RGB times dimming, which is
        // what LunaSysMgr's corner shader did with its Active uniform.
        if (dimming < 1f) canvas.drawColor(android.graphics.Color.argb(Math.round((1f - dimming) * 255), 0, 0, 0))
    }

    // ---- an emulated card's chrome (EmulatedCardWindow) ----

    /** The page as drawn, its scale included. */
    private fun pageRect(): RectF {
        val k = window.scaleX
        val cx = (window.left + window.right) / 2f; val cy = (window.top + window.bottom) / 2f
        return RectF(cx - window.width * k / 2, cy - window.height * k / 2, cx + window.width * k / 2, cy + window.height * k / 2)
    }
    private val chromeFullScreen get() = window.fullScreen
    private fun titleRect(): RectF? {
        val l = luna ?: return null
        val p = pageRect()
        val w = l.px(13f) + titlePaint.measureText(title) + l.px(20f) + l.px(2f)
        val top = p.top - l.px(28f) + l.px(1f)
        return RectF(p.left, top, p.left + w, top + l.px(26f))
    }
    private fun stripRect(): RectF? { val l = luna ?: return null; val p = pageRect(); return RectF(p.left, p.bottom, p.right, p.bottom + l.px(66f)) }
    private fun keyboardRect(): RectF? {
        val l = luna ?: return null
        val right = width - l.px(17f); val bottom = height - l.px(18f)
        return RectF(right - l.px(52f), bottom - l.px(44f), right, bottom)
    }
    private val titlePaint by lazy {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = luna?.px(14f) ?: 14f; luna?.let { typeface = it.fontBold }; letterSpacing = -0.05f
        }
    }
    private var pressed: ChromeAction? = null

    private fun drawChrome(c: Canvas) {
        val l = luna ?: return
        val p = pageRect()
        // drawRoundedCorners: wm-corner-*.png over the page's corners, scaled with it (none in
        // full screen).
        if (!chromeFullScreen) {
            val k = window.scaleX; val s = l.px(24f) * k
            l.image("wm-corner-top-left.png")?.let { c.drawBitmap(it, null, RectF(p.left, p.top, p.left + s, p.top + s), null) }
            l.image("wm-corner-top-right.png")?.let { c.drawBitmap(it, null, RectF(p.right - s, p.top, p.right, p.top + s), null) }
            l.image("wm-corner-bottom-left.png")?.let { c.drawBitmap(it, null, RectF(p.left, p.bottom - s, p.left + s, p.bottom), null) }
            l.image("wm-corner-bottom-right.png")?.let { c.drawBitmap(it, null, RectF(p.right - s, p.bottom - s, p.right, p.bottom), null) }
        }
        if (!chromeShown) return
        // Its own status bar (StatusBar::TypeEmulatedCard), just above the page: the app's
        // title in appname-background.png (13 px left cap, 20 px right with the ▾ in it).
        if (!chromeFullScreen) titleRect()?.let { r ->
            l.nine(c, "statusBar/appname-background.png", r, 13, 0, 20, 0)
            c.drawText(title, r.left + l.px(13f - 4f), r.centerY() - (titlePaint.ascent() + titlePaint.descent()) / 2 - l.px(1f), titlePaint)
        }
        // The virtual core navi, 66 px below the page: the light bar, bright while pressed.
        stripRect()?.let { r ->
            fun bar(kind: String, paint: android.graphics.Paint?) {
                val left = l.image("corenavi/light-bar-$kind-left.png"); val right = l.image("corenavi/light-bar-$kind-right.png")
                val centre = l.image("corenavi/light-bar-$kind-center.png")
                val h = (left?.height ?: 0).toFloat(); val top = r.centerY() - h / 2
                left?.let { c.drawBitmap(it, r.left, top, paint) }
                right?.let { c.drawBitmap(it, r.right - it.width, top, paint) }
                centre?.let { c.drawBitmap(it, null, RectF(r.left + (left?.width ?: 0), top, r.right - (right?.width ?: 0), top + h), paint) }
            }
            bar("dark", null)
            if (pressed == ChromeAction.BACK) bar("bright", null)
        }
        // The keyboard button, 17 px in and 18 px up from the card's corner: a two-state
        // sprite, 52 × 44 at rows 0 and 44.
        keyboardRect()?.let { r ->
            l.sprite(c, "emucard-kb-up_icon.png", r.centerX(), r.centerY(), 0, if (pressed == ChromeAction.KEYBOARD) 44 else 0, 52, 44)
        }
    }

    private fun chromeAt(x: Float, y: Float): ChromeAction? {
        if (!emulated || !chromeShown) return null
        val slop = luna?.px(10f) ?: 10f
        return when {
            keyboardRect()?.let { RectF(it).apply { inset(-slop, -slop) }.contains(x, y) } == true -> ChromeAction.KEYBOARD
            stripRect()?.contains(x, y) == true -> ChromeAction.BACK
            !chromeFullScreen && titleRect()?.contains(x, y) == true -> ChromeAction.APP_MENU
            else -> null
        }
    }

    private var downOn: ChromeAction? = null
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downOn = chromeAt(e.x, e.y); pressed = downOn; if (downOn != null) invalidate() }
        return downOn != null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val on = downOn ?: return false
        val here = chromeAt(e.x, e.y) == on
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> { val p = if (here) on else null; if (p != pressed) { pressed = p; invalidate() } }
            MotionEvent.ACTION_UP -> { pressed = null; invalidate(); downOn = null; if (here) onChrome(on) }
            MotionEvent.ACTION_CANCEL -> { pressed = null; downOn = null; invalidate() }
        }
        return true
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
    /**
     * Card-view transform, animated by CardLayer, as LunaSysMgr kept it: [gx] is the card's
     * group's x (from the centre of the screen) and [rx] the card's own x within the group,
     * because the two slide on different timings. [rot] is degrees; [lift] is a drag off
     * its place up or down.
     */
    var gx = 0f; var rx = 0f; var cy = 0f; var scale = 1f; var rot = 0f; var lift = 0f; var fade = 1f
    /** The group this card is in (LunaCE's CardGroup); see [CardLayer]. */
    internal var group: CardLayer.Group? = null

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
 *
 * Cards live in groups, LunaSysMgr's CardGroup: a card an app opens while its own card is up
 * joins that card's group, and anything else starts a group of its own just right of the
 * active one. The active group is fanned; the others are collapsed into a 10 px stagger at
 * the smaller scale. Geometry and timings: Docs/luna-shell-reference.md §2.
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
        /** A card is picked up to be reordered, or put down: the dock fades out meanwhile. */
        fun onReorder(active: Boolean)
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
        const val GROUP_X_DISTANCE = 0.35f    // CardGroupingXDistanceFactor
        const val GROUP_ROT = 90f             // CardGroupRotFactor
        const val SIDE_STAGGER = 10f          // a collapsed group's cards, each this far right of the last
        const val CORNER = 9f                 // corner half-axis, unscaled card px
        const val SHADOW_GROW = 20f           // card-shadow-tile drawn 20 px outside the card
        const val SHADOW_DROP = 5f            // … and 5 px lower
        const val TAP_RADIUS = 25f            // TapRadiusMax
        const val AXIS_LOCK = 0.866f          // horizontal if |dx| > 0.866·|dy|
        const val MAXIMIZE_MS = 300L          // cardMaximizeDuration, OutQuart
        const val FAN_MS = 200L               // the active group's cards on every slide, OutCubic (hard-coded)
        const val SLIDE_MS = 300L             // cardSlideDuration, OutQuart
        const val DELETE_MS = 300L            // cardDeleteDuration, OutCubic
        const val TRACK_GROUP_MS = 50L        // cardTrackGroupDuration, linear: groups under the finger
        const val TRACK_MS = 300L             // cardTrack: not in the device's conf, so the code's 300 ms linear
        const val SHUFFLE_MS = 350L           // cardShuffleReorderDuration, OutCubic
        const val GROUP_REORDER_MS = 500L     // cardGroupReorderDuration, OutCubic
        const val HOLD_MS = 700L              // Qt's QTapAndHoldGesture timeout; LunaSysMgr keeps it
        const val REORDER_OPACITY = 0.8f      // a card being reordered
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

    /** LunaCE's CardGroup: cards back (left) to front (right), and the one that is active. */
    class Group {
        val cards = ArrayList<Card>()
        var active: Card? = null
        /** Extents either side of the group's x, from its last layout, for spacing the groups. */
        var left = 0f; var right = 0f
    }

    private val groups = ArrayList<Group>()
    /** Every card, group by group, back to front. */
    val cards: List<Card> get() = groups.flatMap { it.cards }
    private var activeGroup: Group? = null

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
    private fun cardHeight(c: Card) = height - top(c)
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
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingMin = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4

    init { clipChildren = false; setWillNotDraw(false); isChildrenDrawingOrderEnabled = true }

    // ---- layout ----

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (c in cards) c.layout(0, top(c), r - l, b - t)
        // A maximized card that has just gone to or from full screen has a new centre.
        maximized?.let { if (anim == null && drag == Drag.NONE) { it.gx = 0f; it.rx = 0f; it.cy = naturalY(it) } }
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
        endReorder(animate = false)
        drag = Drag.NONE
        val max = maximized
        place(if (max != null) maximizedLayout(max) else layout(Arrange.STACK))
    }

    private fun reserve() = luna.px(Params.PILL_RESERVE)
    private fun activeScale() = max(Params.MIN_SCALE, (areaHeight - reserve()) * Params.ACTIVE_RATIO / areaHeight)
    private fun nonActiveScale() = max(Params.MIN_SCALE, (areaHeight - reserve()) * Params.NON_ACTIVE_RATIO / areaHeight)
    /** CardWindowManager's kWindowOrigin: where every group's centre sits in the card view. */
    private fun originY() = inset + reserve() + (areaHeight - reserve()) * Params.ORIGIN_RATIO

    /** Where a card should be: its group's x, its own x in the group, centre y, scale, degrees. */
    private class Pose(val gx: Float, val rx: Float, val cy: Float, val scale: Float, val rot: Float)

    /** Half a transformed card's width, rotation included, as LunaCE's mapRect measures it. */
    private fun halfExtent(c: Card, scale: Float, rot: Float): Float {
        val a = Math.toRadians(rot.toDouble())
        return scale * (width / 2f * Math.cos(a).toFloat() + cardHeight(c) / 2f * abs(Math.sin(a).toFloat()))
    }

    /**
     * CardGroup::calculateOpenedPositions for the Stack arranger (the card view), relative to
     * the group, with LunaCE's always-centred position p. A group [xOffset] from the centre
     * collapses towards a 10 px stagger at the non-active scale as it goes. Sets the group's
     * extents, as LunaCE did.
     */
    private fun fan(g: Group, xOffset: Float): List<Pose> {
        val n = g.cards.size; val p = (n - 1) / 2f
        val cur = activeScale(); val non = nonActiveScale(); val aw = width * cur
        val poses = (0 until n).map { i ->
            var x = ((i - p) / 3f) * aw * Params.GROUP_X_DISTANCE
            var y = if (x > 0) x / 15f else 0f
            var s = cur
            var r = (x / luna.density) / (cur * Params.GROUP_ROT)
            if (xOffset != 0f) {
                val amt = max(1f, aw - abs(xOffset)) / aw
                x = x * amt + (1 - amt) * luna.px(Params.SIDE_STAGGER) * i
                y *= amt; s = non + (cur - non) * amt; r *= amt
            }
            Pose(0f, x, originY() + y, s, r)
        }
        if (n > 0) {
            g.left = -(poses.first().rx - halfExtent(g.cards.first(), poses.first().scale, poses.first().rot))
            g.right = poses.last().rx + halfExtent(g.cards.last(), poses.last().scale, poses.last().rot)
        }
        return poses
    }

    /** The Linear arranger, used while a card is maximized: full size, side by side. */
    private fun linear(g: Group): List<Pose> {
        val n = g.cards.size
        val a = g.cards.indexOf(g.active).coerceAtLeast(0)
        val poses = (0 until n).map { i ->
            Pose(0f, (i - a) * (width + if (n > 1) luna.px(Params.GAP) else 0f), naturalY(g.cards[i]), 1f, 0f)
        }
        if (n > 0) { g.left = -(poses.first().rx - width / 2f); g.right = poses.last().rx + width / 2f }
        return poses
    }

    private enum class Arrange { STACK, LINEAR }

    /**
     * Every card's place, CardWindowManager::slideAllGroups: the active group at [activeX]
     * and the others either side of it, GAP between their extents. [tracking] is
     * slideAllGroupsTo, the groups under a finger, where the active group collapses too.
     */
    private fun layout(arr: Arrange, activeX: Float = 0f, tracking: Boolean = false): HashMap<Card, Pose> {
        val out = HashMap<Card, Pose>()
        val ag = activeGroup ?: return out
        val gap = luna.px(Params.GAP)
        fun poses(g: Group, offset: Float) = if (arr == Arrange.STACK) fan(g, offset) else linear(g)
        fun put(g: Group, gx: Float, ps: List<Pose>) = g.cards.forEachIndexed { i, c -> out[c] = Pose(gx, ps[i].rx, ps[i].cy, ps[i].scale, ps[i].rot) }
        put(ag, activeX, poses(ag, if (tracking) activeX else 0f))
        val ai = groups.indexOf(ag)
        // A side group's extents depend on how far it has collapsed, which depends on where it
        // lands: estimate it collapsed, then place it once more with that.
        var edge = activeX - ag.left - gap
        for (i in ai - 1 downTo 0) {
            val g = groups[i]
            poses(g, -Float.MAX_VALUE / 4)
            val ps = poses(g, edge - g.right)
            val x = edge - g.right
            put(g, x, ps)
            edge = x - gap - g.left
        }
        edge = activeX + ag.right + gap
        for (i in ai + 1 until groups.size) {
            val g = groups[i]
            poses(g, Float.MAX_VALUE / 4)
            val ps = poses(g, edge + g.left)
            val x = edge + g.left
            put(g, x, ps)
            edge = x + gap + g.right
        }
        return out
    }

    /**
     * CardWindowManager::maximizeActiveWindow: the groups laid out Linear, the card itself
     * full size in the middle, and the rest of its group off either side at the active scale.
     */
    private fun maximizedLayout(card: Card): HashMap<Card, Pose> {
        val g = card.group ?: return HashMap()
        val out = layout(Arrange.LINEAR)
        val a = g.cards.indexOf(card)
        g.cards.forEachIndexed { i, c ->
            out[c] = when {
                c == card -> Pose(0f, 0f, naturalY(card), 1f, 0f)
                i < a -> Pose(0f, -width.toFloat(), naturalY(card), activeScale(), 0f)
                else -> Pose(0f, width.toFloat(), naturalY(card), activeScale(), 0f)
            }
        }
        return out
    }

    private fun place(poses: Map<Card, Pose>) {
        for ((c, p) in poses) { c.gx = p.gx; c.rx = p.rx; c.cy = p.cy; c.scale = p.scale; c.rot = p.rot; c.lift = 0f }
        applyTransforms()
    }

    private fun cx(c: Card) = width / 2f + c.gx + c.rx

    private fun applyTransforms() {
        for (c in cards) {
            c.pivotX = c.width / 2f; c.pivotY = c.height / 2f
            c.scaleX = c.scale; c.scaleY = c.scale
            c.rotation = c.rot
            // Chromium 37's WebView draws nothing, and asks to draw again every frame, when it
            // is itself turned by a negative angle, and nothing at all under a view's alpha;
            // drawn into a layer that is then turned or faded, it is fine. Only a card that is
            // turned or see-through has one.
            val layer = if (abs(c.rot) > 0.01f || c.fade < 1f) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
            if (c.layerType != layer) c.setLayerType(layer, null)
            c.translationX = cx(c) - (c.left + c.width / 2f)
            c.translationY = c.cy - (c.top + c.height / 2f) + c.lift
            c.alpha = c.fade
            if (c.window.emulated) c.setPageScale(if (c.chromeShown) 1f else max(1f, 2f - c.scale))
            c.invalidateOutline()
        }
        invalidate()
    }

    // ---- stacking ----

    /** The card drawn over everything else: the one being maximized, reordered or launched. */
    private var topCard: Card? = null
    private var drawOrder = IntArray(0)

    /**
     * Within a group later cards are drawn over earlier ones, and the active group over the
     * others (CardGroup::raiseCards, CardWindowManager's re-raise after every animation).
     */
    private fun restack() {
        val order = ArrayList<Card>()
        groups.filter { it != activeGroup }.forEach { order += it.cards }
        activeGroup?.let { order += it.cards }
        topCard?.takeIf { it in order }?.let { order.remove(it); order += it }
        drawOrder = order.map { indexOfChild(it) }.filter { it >= 0 }.toIntArray()
        invalidate()
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int =
        if (drawOrder.size == childCount) drawOrder[i] else i

    /** Luna's card shadow, drawn under each card as the card itself is drawn (reference §2.4). */
    override fun drawChild(c: Canvas, child: View, drawingTime: Long): Boolean {
        val card = child as? Card
        val shadow = luna.image("card-shadow-tile.png")
        if (card != null && shadow != null && card.visibility == View.VISIBLE && card.scale < 0.999f) {
            // card-shadow-tile.png as a nine-patch (43 px insets), 20 px outside the card and
            // 5 px down, in the card's own scaled and turned coordinates.
            val s = card.scale; val g = luna.px(Params.SHADOW_GROW); val drop = luna.px(Params.SHADOW_DROP)
            val halfW = (card.width / 2f + g) * s; val halfH = (card.height / 2f + g) * s
            val x = cx(card); val y = card.cy + card.lift
            // 43 of 87 px in the original: keep at least a 1-px stretchable middle after scaling,
            // or the edge slices vanish and only the corners draw.
            val inset = (shadow.width - 1) / 2
            val p = android.graphics.Paint().apply { alpha = (card.fade * 255).toInt(); isFilterBitmap = true }
            val d = inset * s
            c.save()
            c.rotate(card.rot, x, y)
            Luna.drawNineSlice(c, shadow, RectF(x - halfW, y + drop * s - halfH, x + halfW, y + drop * s + halfH), inset, inset, inset, inset, p, d, d, d, d)
            c.restore()
        }
        return super.drawChild(c, child, drawingTime)
    }

    // ---- the active card ----

    /**
     * LunaSysMgr's active card window: the active group's active card. Changing it dims the
     * one before; see [Card.dimming].
     */
    private var current: Card? = null
    private fun setCurrent(c: Card?) {
        if (c == current) return
        current?.takeIf { it.group != null }?.setDimmed(true)
        current = c
        c?.setDimmed(false)
    }

    private fun setActiveGroup(g: Group?) {
        activeGroup = g
        setCurrent(g?.active)
        restack()
    }

    /** CardWindowManager::groupClosestToCenterHorizontally. */
    private fun closestGroup(): Group? = groups.minByOrNull { g -> g.cards.firstOrNull()?.let { abs(it.gx) } ?: Float.MAX_VALUE }

    // ---- animation ----

    /** One card's move: its group's x on one timing, its own place in the group on another. */
    private class Move(val card: Card, val to: Pose, val groupMs: Long, val groupEase: TimeInterpolator,
                       val cardMs: Long, val cardEase: TimeInterpolator)

    private var anim: ValueAnimator? = null

    private fun run(moves: List<Move>, done: () -> Unit = {}) {
        anim?.cancel(); anim = null
        val total = moves.maxOfOrNull { max(it.groupMs, it.cardMs) } ?: 0L
        if (total <= 0L) { moves.forEach { m -> place(mapOf(m.card to m.to)) }; done(); return }
        val from = moves.map { m -> m.card.let { floatArrayOf(it.gx, it.rx, it.cy, it.scale, it.rot, it.lift) } }
        var cancelled = false
        anim = ValueAnimator.ofFloat(0f, total.toFloat()).apply {
            duration = total; interpolator = Easing.Linear
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                moves.forEachIndexed { k, m ->
                    val f = from[k]; val c = m.card; val to = m.to
                    val fg = m.groupEase.getInterpolation(if (m.groupMs <= 0) 1f else min(1f, t / m.groupMs))
                    val fc = m.cardEase.getInterpolation(if (m.cardMs <= 0) 1f else min(1f, t / m.cardMs))
                    c.gx = f[0] + (to.gx - f[0]) * fg
                    c.rx = f[1] + (to.rx - f[1]) * fc
                    c.cy = f[2] + (to.cy - f[2]) * fc
                    c.scale = f[3] + (to.scale - f[3]) * fc
                    c.rot = f[4] + (to.rot - f[4]) * fc
                    c.lift = f[5] * (1 - fc)
                }
                applyTransforms()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(a: Animator) { cancelled = true }
                override fun onAnimationEnd(a: Animator) {
                    if (anim === a) anim = null
                    if (!cancelled) { restack(); done() }
                }
            })
            start()
        }
    }

    /**
     * slideAllGroups: every group to its place over cardSlideDuration, and the active group's
     * own cards over a quicker, hard-coded 200 ms OutCubic.
     */
    private fun slide(except: Card? = null, done: () -> Unit = {}) {
        val ag = activeGroup
        run(layout(Arrange.STACK).filterKeys { it != except }.map { (c, p) ->
            if (c.group == ag) Move(c, p, Params.SLIDE_MS, Easing.OutQuart, Params.FAN_MS, Easing.OutCubic)
            else Move(c, p, Params.SLIDE_MS, Easing.OutQuart, Params.SLIDE_MS, Easing.OutQuart)
        }, done)
    }

    // ---- state changes ----

    /**
     * A new card. One that [sibling]'s app opens while [sibling] is up joins its group, at the
     * front; anything else starts a group of its own just right of the active group
     * (CardWindowManager::prepareAddWindowSibling). Either way it becomes the active card.
     */
    fun add(card: Card, sibling: Card? = null) {
        val g = sibling?.group ?: Group().also { groups.add(activeGroup?.let { groups.indexOf(it) + 1 } ?: groups.size, it) }
        g.cards += card
        card.group = g
        g.active = card
        addView(card)
        card.visibility = View.VISIBLE
        setActiveGroup(g)
    }

    fun remove(card: Card) {
        val g = card.group ?: return
        val i = g.cards.indexOf(card)
        val activeIndex = g.cards.indexOf(g.active)
        g.cards.removeAt(i); card.group = null
        // CardGroup::removeFromGroup: the card behind takes over as the group's active one.
        if (g.cards.isEmpty()) g.active = null
        else if (i == activeIndex) g.active = if (i > 0) g.cards[i - 1] else g.cards[0]
        if (g.cards.isEmpty()) groups.remove(g)
        removeView(card)
        launching.remove(card)
        if (reorderCard == card) endReorder(animate = false)
        if (topCard == card) topCard = null
        if (current == card) current = null
        val wasUp = maximized == card || activating == card
        if (maximized == card) maximized = null
        if (activating == card) { activating = null; anim?.cancel(); anim = null }
        if (dragCard == card) { drag = Drag.NONE; dragCard = null }
        // CardWindowManager::removeCardFromGroup: the group nearest the middle is the active one,
        // and everything slides to its place. A card that goes while it is up takes the shell
        // back to the card view (removeCardFromGroupMaximized).
        setActiveGroup(if (g.cards.isNotEmpty() && g == activeGroup) g else closestGroup())
        if (wasUp) { if (cards.isNotEmpty()) showCardView() }
        else if (maximized == null && activating == null) slide()
    }

    fun maximize(card: Card) {
        val g = card.group ?: return
        maximized?.takeIf { it != card }?.chromeShown = false
        launching.remove(card)
        g.active = card
        setActiveGroup(g)
        cards.forEach { it.visibility = View.VISIBLE }
        activating = card
        topCard = card
        restack()
        run(maximizedLayout(card).map { (c, p) -> Move(c, p, Params.MAXIMIZE_MS, Easing.OutQuart, Params.MAXIMIZE_MS, Easing.OutQuart) }) {
            activating = null
            maximized = card
            cards.forEach { if (it != card) it.visibility = View.INVISIBLE }
            card.chromeShown = true
            listener.onMaximized(card)
        }
    }

    fun showCardView() {
        maximized?.chromeShown = false
        maximized = null
        activating = null
        topCard = null
        cards.forEach { it.visibility = View.VISIBLE }
        restack()
        slide()
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
        // setActiveCardOffScreen: full size, its top at the bottom of the screen.
        card.gx = 0f; card.rx = 0f; card.cy = height + cardHeight(card) / 2f; card.scale = 1f; card.rot = 0f; card.lift = 0f
        topCard = card
        restack()
        applyTransforms()
        // The others make room while it waits (slideAllGroups without the new card).
        if (maximized == null && activating == null) slide(except = card)
        if (card.ready) { maximize(card); return }
        launching += card
        lastLaunched = card
        postDelayed({
            if (card in launching && card.group != null && activating == null && drag == Drag.NONE) timedOut(card)
        }, Params.PREPARE_MS + Params.ADD_MAX_MS)
    }

    private fun timedOut(card: Card) {
        maximized = null
        card.group?.let { it.active = card; setActiveGroup(it) }
        cards.forEach { it.visibility = View.VISIBLE }
        topCard = null
        restack()
        run(layout(Arrange.STACK).map { (c, p) -> Move(c, p, Params.SLIDE_MS, Easing.OutQuart, Params.SLIDE_MS, Easing.OutQuart) })
        listener.onPreparing(card)
    }

    /**
     * The card's app is ready. A launching card maximizes now, unless something else has
     * been maximized since or the card view is being handled.
     */
    fun ready(card: Card) {
        card.ready = true
        if (!launching.remove(card) || card.group == null) return
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
        anim?.cancel(); anim = null
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Params.DELETE_MS; interpolator = Easing.OutCubic
            addUpdateListener { a -> val f = a.animatedValue as Float; card.lift = start + (end - start) * f; applyTransforms() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (anim === a) anim = null
                    if (card.group == null) return
                    remove(card)
                    listener.onThrownAway(card)
                }
            })
            start()
        }
    }

    // ---- reorder (reference §2.7) ----

    private var reorderCard: Card? = null
    /** -1, 0, 1: the finger is left of, over or right of the active card's slot. */
    private var zone = 0

    /** CardWindowManager::getReorderZone: either side of the middle W·activeScale. */
    private fun zoneAt(x: Float): Int {
        val half = width * activeScale() / 2f
        val rel = x - width / 2f
        return if (rel < -half) -1 else if (rel > half) 1 else 0
    }

    /** CardWindowManager::enterReorder: the held card goes 80 % opaque and leaves its group's layout. */
    private fun startReorder(c: Card, x: Float) {
        drag = Drag.REORDER
        reorderCard = c
        c.fade = Params.REORDER_OPACITY
        topCard = c
        restack()
        zone = zoneAt(x)
        listener.onReorder(true)
        applyTransforms()
    }

    private fun reorderMove(dx: Float, dy: Float, x: Float) {
        val c = reorderCard ?: return
        c.rx += dx; c.cy += dy; c.scale = activeScale(); c.rot = 0f
        applyTransforms()
        val z = zoneAt(x)
        if (z == zone && z == 0) moveReorderCentre()
        else if (z != zone) { zone = z; if (z == 1) moveReorderRight() else if (z == -1) moveReorderLeft() }
    }

    /** The others make room: the active group to the middle, everyone to their places (arrangeWindowsAfterReorderChange). */
    private fun arrangeAfterReorder(ms: Long) {
        val ag = activeGroup
        run(layout(Arrange.STACK).filterKeys { it != reorderCard }.map { (c, p) ->
            if (c.group == ag) Move(c, p, Params.SHUFFLE_MS, Easing.OutCubic, ms, Easing.OutCubic)
            else Move(c, p, ms, Easing.OutCubic, ms, Easing.OutCubic)
        }) {
            // ReorderState::animationsFinished: a finger still held to one side keeps going.
            if (drag == Drag.REORDER) { if (zone == 1) moveReorderRight() else if (zone == -1) moveReorderLeft() }
        }
    }

    /** CardGroup::moveActiveCard: past a sibling's centre, take its place. */
    private fun moveReorderCentre() {
        val c = reorderCard ?: return
        val g = c.group ?: return
        val a = g.cards.indexOf(c)
        val x = cx(c)
        val to = (0 until a).firstOrNull { x < cx(g.cards[it]) }
            ?: (g.cards.size - 1 downTo a + 1).firstOrNull { x > cx(g.cards[it]) } ?: return
        g.cards.removeAt(a); g.cards.add(to, c)
        setActiveGroup(g)
        arrangeAfterReorder(Params.SHUFFLE_MS)
    }

    /**
     * moveReorderSlotRight: one place right in the group; past its front, out of it. A card
     * that was alone joins the next group at the back; one leaving a group starts a new one.
     */
    private fun moveReorderRight() {
        val c = reorderCard ?: return
        val g = c.group ?: return
        val a = g.cards.indexOf(c)
        if (a < g.cards.size - 1) {
            java.util.Collections.swap(g.cards, a, a + 1)
            setActiveGroup(g)
            arrangeAfterReorder(Params.SHUFFLE_MS)
        } else if (g != groups.last() || g.cards.size > 1) {
            val gi = groups.indexOf(g)
            detach(c, g)
            val ng = if (g.cards.isEmpty()) { groups.removeAt(gi); groups[gi] } else Group().also { groups.add(gi + 1, it) }
            ng.cards.add(0, c); c.group = ng; ng.active = c
            setActiveGroup(ng)
            arrangeAfterReorder(Params.GROUP_REORDER_MS)
        }
    }

    /** moveReorderSlotLeft, the mirror: a card that was alone joins the previous group at the front. */
    private fun moveReorderLeft() {
        val c = reorderCard ?: return
        val g = c.group ?: return
        val a = g.cards.indexOf(c)
        if (a > 0) {
            java.util.Collections.swap(g.cards, a, a - 1)
            setActiveGroup(g)
            arrangeAfterReorder(Params.SHUFFLE_MS)
        } else if (g != groups.first() || g.cards.size > 1) {
            val gi = groups.indexOf(g)
            detach(c, g)
            val ng = if (g.cards.isEmpty()) { groups.removeAt(gi); groups[max(0, gi - 1)] } else Group().also { groups.add(gi, it) }
            ng.cards.add(c); c.group = ng; ng.active = c
            setActiveGroup(ng)
            arrangeAfterReorder(Params.GROUP_REORDER_MS)
        }
    }

    private fun detach(c: Card, g: Group) {
        val i = g.cards.indexOf(c)
        g.cards.removeAt(i)
        g.active = if (g.cards.isEmpty()) null else g.cards[max(0, i - 1)]
    }

    /** handleMouseReleaseReorder: opaque again, back in its group, and everything to its place. */
    private fun endReorder(animate: Boolean = true) {
        val c = reorderCard ?: return
        reorderCard = null
        c.fade = 1f
        topCard = null
        if (drag == Drag.REORDER) drag = Drag.NONE
        listener.onReorder(false)
        restack()
        if (animate) slide()
    }

    // ---- touch ----

    private enum class Drag { NONE, UNDECIDED, SCROLL, THROW, MINIMIZE, REORDER }
    private var drag = Drag.NONE
    private var downX = 0f; private var downY = 0f
    private var lastX = 0f; private var lastY = 0f
    /** The active group's x as the finger took it (m_activeGroupPivot). */
    private var pivot = 0f
    private var dragCard: Card? = null
    private var velocity: VelocityTracker? = null
    /** A touch that arrived while a card was maximizing, which LunaSysMgr had no state for. */
    private var ignoring = false

    /**
     * Tap-and-hold (handleTapAndHoldGestureMinimized): on a card of the active group, pick it
     * up to reorder; on the empty space either side, go to the previous or next group.
     */
    private val hold = Runnable {
        if (drag != Drag.UNDECIDED) return@Runnable
        val c = dragCard
        if (c != null) startReorder(c, lastX)
        else {
            drag = Drag.NONE
            val ag = activeGroup ?: return@Runnable
            val i = groups.indexOf(ag) + if (downX < width / 2f) -1 else 1
            groups.getOrNull(i)?.let { setActiveGroup(it) }
            slide()
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        val max = maximized
        if (max == null) return true  // card view: the shell owns every touch
        // Maximized: only a swipe up from the bottom edge is ours; everything else goes to the app.
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drag = if (e.y > height - luna.px(Params.EDGE)) Drag.UNDECIDED else Drag.NONE
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                ignoring = false
                // The gesture dead zone (SystemUiController, sysUiEnableGestureDeadzone, on by
                // default and with the edge gestures on, as the reference TouchPad has them): a
                // touch that starts in the 15 px band at the bottom, or at the left or right
                // below the status bar, never reaches the app, so a swipe up can't press a
                // toolbar button on its way.
                val edge = luna.px(Params.EDGE)
                if (e.y >= height - 1 - edge || (e.y > inset && (e.x <= edge || e.x >= width - 1 - edge))) {
                    dragCard = max
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (drag == Drag.UNDECIDED && downY - e.y >= luna.px(Params.TRIGGER) && downY - e.y > abs(e.x - downX)) {
                drag = Drag.MINIMIZE; dragCard = max
                cards.forEach { it.visibility = View.VISIBLE }
                max.chromeShown = false
                return true
            }
        }
        return false
    }

    /** The topmost card under a point, of [g] only when given (CardGroup::setActiveCard, testHit). */
    private fun cardAt(x: Float, y: Float, g: Group? = null): Card? {
        val order = drawOrder.map { getChildAt(it) }.filterIsInstance<Card>().ifEmpty { cards }
        return order.lastOrNull { c ->
            if (g != null && c.group != g) return@lastOrNull false
            val w = c.width * c.scale; val h = c.height * c.scale; val cx = cx(c); val cy = c.cy + c.lift
            x >= cx - w / 2 && x <= cx + w / 2 && y >= cy - h / 2 && y <= cy + h / 2
        }
    }

    /** CardGroup::withinColumn. */
    private fun withinColumn(g: Group, x: Float): Boolean {
        val gx = width / 2f + (g.cards.firstOrNull()?.gx ?: 0f)
        return x >= gx - g.left && x <= gx + g.right
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (maximized != null && drag != Drag.MINIMIZE) return deadZoneTouch(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) ignoring = activating != null
        if (ignoring) return true
        if (velocity == null) velocity = VelocityTracker.obtain()
        velocity!!.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel(); anim = null
                drag = Drag.UNDECIDED; downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                playedStretch = false
                // handleMousePressMinimized: only the active group's cards can be taken hold of.
                dragCard = activeGroup?.let { g -> cardAt(e.x, e.y, g)?.also { g.active = it } }
                pivot = activeGroup?.cards?.firstOrNull()?.gx ?: 0f
                removeCallbacks(hold); postDelayed(hold, Params.HOLD_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (drag == Drag.UNDECIDED && Math.hypot(dx.toDouble(), dy.toDouble()) > luna.px(Params.TAP_RADIUS)) {
                    removeCallbacks(hold)
                    drag = if (abs(dx) > Params.AXIS_LOCK * abs(dy)) Drag.SCROLL
                    else if (dragCard != null) Drag.THROW else Drag.NONE
                }
                when (drag) {
                    Drag.SCROLL -> {
                        // slideAllGroupsTo: the groups follow the finger, collapsing as they leave the middle.
                        pivot += e.x - lastX
                        run(layout(Arrange.STACK, pivot, tracking = true).map { (c, p) ->
                            Move(c, p, Params.TRACK_GROUP_MS, Easing.Linear, Params.TRACK_MS, Easing.Linear)
                        })
                    }
                    Drag.THROW -> {
                        dragCard?.lift = dy; applyTransforms()
                        // kAngryCardThreshold: 30 % of half the card area's height.
                        if (!playedStretch && dy > areaHeight / 2f * 0.30f && upsideDown()) { playedStretch = true; feedback("carddrag") }
                    }
                    Drag.MINIMIZE -> minimizeFollow(dy)
                    Drag.REORDER -> reorderMove(e.x - lastX, e.y - lastY, e.x)
                    else -> {}
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                velocity!!.computeCurrentVelocity(1000)
                val vx = velocity!!.xVelocity; val vy = velocity!!.yVelocity
                velocity!!.recycle(); velocity = null
                val card = dragCard
                when (drag) {
                    Drag.SCROLL -> {
                        // The group nearest the middle becomes active; a flick goes one further.
                        var g = closestGroup()
                        if (abs(vx) > flingMin && g != null) {
                            val i = (groups.indexOf(g) + if (vx < 0) 1 else -1).coerceIn(0, groups.size - 1)
                            g = groups[i]
                        }
                        setActiveGroup(g)
                        slide()
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
                        else slide()
                    }
                    Drag.MINIMIZE -> if (card != null) {
                        if (vy < -flingMin || (downY - e.y) > height * 0.15f) showCardView() else maximize(card)
                    }
                    Drag.REORDER -> endReorder()
                    Drag.UNDECIDED -> if (e.actionMasked == MotionEvent.ACTION_UP) {
                        val ag = activeGroup
                        if (card != null) maximize(card)
                        else if (ag != null && !withinColumn(ag, e.x)) {
                            // A tap on another group makes it the active one.
                            cardAt(e.x, e.y)?.group?.let { setActiveGroup(it); slide() }
                        }
                    }
                    Drag.NONE -> {}
                }
                drag = Drag.NONE; dragCard = null
            }
        }
        return true
    }

    /**
     * A touch that began in the dead zone of a maximized card: the app never sees it. One from
     * the bottom band is still the swipe up.
     */
    private fun deadZoneTouch(e: MotionEvent): Boolean {
        val max = maximized ?: return true
        if (e.actionMasked == MotionEvent.ACTION_MOVE && drag == Drag.UNDECIDED &&
            downY - e.y >= luna.px(Params.TRIGGER) && downY - e.y > abs(e.x - downX)) {
            drag = Drag.MINIMIZE; dragCard = max
            cards.forEach { it.visibility = View.VISIBLE }
            max.chromeShown = false
            if (velocity == null) velocity = VelocityTracker.obtain()
            velocity!!.addMovement(e)
        }
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) { drag = Drag.NONE; dragCard = null }
        return true
    }

    /** Fluid minimize: the maximized card shrinks toward its card-view slot as the finger rises. */
    private fun minimizeFollow(dy: Float) {
        val card = dragCard ?: return
        val to = layout(Arrange.STACK)[card] ?: return
        val f = (-dy / (height * 0.5f)).coerceIn(0f, 1f)
        card.gx = to.gx * f; card.rx = to.rx * f
        card.cy = naturalY(card) + (to.cy - naturalY(card)) * f
        card.scale = 1f + (to.scale - 1f) * f
        card.rot = to.rot * f
        applyTransforms()
    }
}
