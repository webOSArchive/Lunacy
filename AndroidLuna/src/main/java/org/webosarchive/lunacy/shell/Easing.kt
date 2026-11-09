package org.webosarchive.lunacy.shell

import android.animation.TimeInterpolator
import kotlin.math.pow

/** Qt easing curves Luna's animation settings name, as Android interpolators. */
object Easing {
    val Linear = TimeInterpolator { it }
    val OutCubic = TimeInterpolator { 1 - (1 - it).pow(3) }   // QEasingCurve 6
    val OutQuart = TimeInterpolator { 1 - (1 - it).pow(4) }   // QEasingCurve 10
    val InOutQuint = TimeInterpolator { if (it < 0.5f) 16 * it.pow(5) else 1 - (-2 * it + 2).pow(5) / 2 }  // QEasingCurve 15
    val InQuad = TimeInterpolator { it * it }                  // QEasingCurve 1
    val InCubic = TimeInterpolator { it * it * it }            // QEasingCurve 5
    val InOutQuad = TimeInterpolator { if (it < 0.5f) 2 * it * it else 1 - (-2 * it + 2).pow(2) / 2 }  // QEasingCurve 3
}

/**
 * A float animated from [from] to [to] over [ms] with [easing], handed to [set] at each frame,
 * and started: the cancel-then-animate every view wrote out for itself. [onEnd] runs when it
 * ends, after a cancel too (as ValueAnimator's onAnimationEnd does); [onFinished] only when it
 * ran to its end, not after a cancel.
 */
fun animateValue(from: Float, to: Float, ms: Long, easing: TimeInterpolator, onEnd: (() -> Unit)? = null,
                 onFinished: (() -> Unit)? = null, set: (Float) -> Unit): android.animation.ValueAnimator =
    android.animation.ValueAnimator.ofFloat(from, to).apply {
        duration = ms; interpolator = easing
        addUpdateListener { set(it.animatedValue as Float) }
        if (onEnd != null || onFinished != null) addListener(object : android.animation.AnimatorListenerAdapter() {
            var cancelled = false
            override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
            override fun onAnimationEnd(a: android.animation.Animator) {
                onEnd?.invoke()
                if (!cancelled) onFinished?.invoke()
            }
        })
        start()
    }
