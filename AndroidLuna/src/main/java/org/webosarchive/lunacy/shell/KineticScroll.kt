package org.webosarchive.lunacy.shell

import android.animation.ValueAnimator
import kotlin.math.abs

/**
 * LunaCE's KineticScroller for the launcher's pages: a flick starts at its velocity over
 * sFlickScalar and slows under a constant friction, eased OutCubic over the time it takes to
 * stop; a flick in the same direction as one still running adds to it. Past the end it may run
 * on up to 100 px, then comes back over 350 ms; let go while overscrolled without a flick, 500
 * ms OutCubic.
 *
 * The launcher's one animator ([slot]) is shared with its paging and edit-mode scrolling, so a
 * flick cancels whatever ran and the next of those cancels the flick.
 */
class KineticScroll(private val luna: Luna, private val slot: Slot, private val redraw: () -> Unit) {
    /** What scrolls: a page's offset. */
    interface Target { var scrollY: Float }
    /** The animator the launcher runs, one at a time. */
    interface Slot { var anim: ValueAnimator? }

    /** A flick's start in TouchPad px per ms, its time to stop in ms and its distance in TouchPad px. */
    class Plan(val v0: Float, val ms: Float, val distance: Float)

    /** The velocity of the flick still running, for LunaCE's "keep increasing velocity". */
    private var flickV = 0f

    /**
     * Lets the launcher's finger go with velocity [v] (px a second): a flick, or back from past
     * the end, or nothing. [max] is how far the target may scroll.
     */
    fun fling(page: Target, v: Float, max: () -> Float) {
        slot.anim?.cancel()
        val m = max()
        val start = page.scrollY
        if (abs(v) < luna.px(500f)) {
            flickV = 0f
            if (start < 0 || start > m) settle(page, Launcher.Params.OVERSCROLL_SNAP_MS, max)
            return
        }
        // flickV is only still set if the last flick was stopped by this touch, not run out.
        val p = plan(v / luna.density, flickV)
        flickV = p.v0
        val distance = p.distance * luna.density
        if (start < 0 || start > m) { overscrollFlick(page, p.v0, max); return }
        val over = luna.px(Launcher.Params.MAX_OVERSCROLL)
        val target = (start + distance).coerceIn(-over, m + over)
        val t0 = System.currentTimeMillis()
        slot.anim = ValueAnimator.ofFloat(start, target).apply {
            duration = p.ms.toLong().coerceAtLeast(1L); interpolator = Easing.OutCubic
            addUpdateListener {
                page.scrollY = it.animatedValue as Float; redraw()
                // Past the end the flick carries on under a friction that grows with how far
                // past it is (KineticScroller's FlickOverScroll), not for its full time.
                if (page.scrollY < 0 || page.scrollY > m) {
                    val elapsed = (System.currentTimeMillis() - t0).toFloat()
                    it.cancel()
                    overscrollFlick(page, p.v0 - Launcher.Params.FRICTION * Math.signum(p.v0) * elapsed, max)
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (cancelled) return
                    flickV = 0f
                    if (page.scrollY < 0 || page.scrollY > m) settle(page, Launcher.Params.OVERSCROLL_FIX_MS, max)
                }
            })
            start()
        }
    }

    /**
     * KineticScroller's FlickOverScroll state: a flick that is past the end (or starts there)
     * keeps its velocity under an opposing friction that grows every frame by the fraction of
     * the 100 px of overscroll it has used, so it dies in a few frames rather than crawling
     * for the flick's whole time, and then the 350 ms correction brings it back. Before this
     * the launcher let the eased flick run its full course against the overscroll clamp, and
     * an over-scrolled grid hung pulled out of place for most of a second (codepoet,
     * 2026-10-03). Velocities in TouchPad px per ms, as fling's.
     */
    private fun overscrollFlick(page: Target, v0: Float, max: () -> Float) {
        slot.anim?.cancel()
        val m = max()
        if (v0 == 0f) { flickV = 0f; settle(page, Launcher.Params.OVERSCROLL_FIX_MS, max); return }
        var v = v0
        var friction = Launcher.Params.FRICTION * -Math.signum(v0)
        var last = System.currentTimeMillis()
        val over = luna.px(Launcher.Params.MAX_OVERSCROLL)
        slot.anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10_000L; interpolator = Easing.Linear
            addUpdateListener {
                val now = System.currentTimeMillis(); val dt = (now - last).toFloat(); last = now
                page.scrollY += v * dt * luna.density
                v += friction * dt
                val amount = if (page.scrollY < 0) -page.scrollY else if (page.scrollY > m) page.scrollY - m else 0f
                friction += friction * (amount / over)
                redraw()
                if (Math.signum(v) != Math.signum(v0)) {
                    it.cancel(); flickV = 0f
                    settle(page, Launcher.Params.OVERSCROLL_FIX_MS, max)
                }
            }
            start()
        }
    }

    /** Back within the ends over [ms]. */
    fun settle(page: Target, ms: Long, max: () -> Float) {
        val m = max()
        slot.anim = animateValue(page.scrollY, page.scrollY.coerceIn(0f, m), ms, Easing.OutCubic) { page.scrollY = it; redraw() }
    }

    companion object {
        /**
         * A flick of [vTp] TouchPad px a second, with [carried] (TouchPad px per ms) still
         * running from the last: its start velocity (the two added when they go the same way,
         * held to 100), its time to stop under the friction and the distance it covers.
         */
        fun plan(vTp: Float, carried: Float): Plan {
            var v0 = vTp / Launcher.Params.FLICK_SCALAR
            if (carried != 0f && Math.signum(carried) == Math.signum(v0)) v0 += carried
            v0 = v0.coerceIn(-100f, 100f)
            return Plan(v0, abs(v0) / Launcher.Params.FRICTION, v0 * v0 / (2 * Launcher.Params.FRICTION) * Math.signum(v0))
        }
    }
}
