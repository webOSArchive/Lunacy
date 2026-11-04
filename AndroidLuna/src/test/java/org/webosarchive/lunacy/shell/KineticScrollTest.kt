package org.webosarchive.lunacy.shell

import org.junit.Assert.assertEquals
import org.junit.Test

/** The launcher's flick, as LunaCE's KineticScroller works it out (KineticScroll.plan). */
class KineticScrollTest {
    private val scalar = Launcher.Params.FLICK_SCALAR
    private val friction = Launcher.Params.FRICTION

    @Test fun aFlickSlowsUnderTheFriction() {
        val p = KineticScroll.plan(2 * scalar, 0f)   // 2 TouchPad px a millisecond
        assertEquals(2f, p.v0, 0f)
        assertEquals(2f / friction, p.ms, 0.01f)
        assertEquals(4f / (2 * friction), p.distance, 0.01f)
    }

    @Test fun aFlickTheWayOneIsRunningAddsToIt() {
        assertEquals(3f, KineticScroll.plan(2 * scalar, 1f).v0, 0f)
        // The other way, the running one is dropped, not subtracted.
        assertEquals(2f, KineticScroll.plan(2 * scalar, -1f).v0, 0f)
        assertEquals(-2f, KineticScroll.plan(-2 * scalar, 1f).v0, 0f)
    }

    @Test fun aFlickIsHeldTo100AndGoesItsWay() {
        assertEquals(100f, KineticScroll.plan(1000 * scalar, 0f).v0, 0f)
        val back = KineticScroll.plan(-2 * scalar, 0f)
        assertEquals(-2f, back.v0, 0f)
        assertEquals(-4f / (2 * friction), back.distance, 0.01f)
    }
}
