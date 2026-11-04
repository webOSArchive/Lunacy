package org.webosarchive.lunacy.shell

import android.graphics.PointF
import android.view.MotionEvent
import android.view.VelocityTracker

/**
 * A finger's velocity through a gesture, in px a second: Android's VelocityTracker obtained,
 * fed and recycled the way every view that flicks did it by hand.
 */
class Flick {
    private var tracker: VelocityTracker? = null

    /** Tracking starts again: from [e], or from the next event added when it is null. */
    fun restart(e: MotionEvent? = null) {
        tracker?.recycle()
        tracker = VelocityTracker.obtain().also { if (e != null) it.addMovement(e) }
    }

    /** [e] into the gesture, starting it if none is being tracked. */
    fun add(e: MotionEvent) { (tracker ?: VelocityTracker.obtain().also { tracker = it }).addMovement(e) }

    /** [e] into the gesture, if one is being tracked. */
    fun addIfTracking(e: MotionEvent) { tracker?.addMovement(e) }

    /** The velocity now, px a second; nothing if nothing is being tracked. */
    fun velocity(): PointF {
        val t = tracker ?: return PointF(0f, 0f)
        t.computeCurrentVelocity(1000)
        return PointF(t.xVelocity, t.yVelocity)
    }

    /** The gesture is over. */
    fun drop() { tracker?.recycle(); tracker = null }
}
