package org.webosarchive.lunacy.shell

import android.animation.TimeInterpolator
import kotlin.math.pow

/** Qt easing curves Luna's animation settings name, as Android interpolators. */
object Easing {
    val Linear = TimeInterpolator { it }
    val OutCubic = TimeInterpolator { 1 - (1 - it).pow(3) }   // QEasingCurve 6
    val OutQuart = TimeInterpolator { 1 - (1 - it).pow(4) }   // QEasingCurve 10
    val OutQuint = TimeInterpolator { 1 - (1 - it).pow(5) }   // QEasingCurve 14
    val InOutQuint = TimeInterpolator { if (it < 0.5f) 16 * it.pow(5) else 1 - (-2 * it + 2).pow(5) / 2 }  // QEasingCurve 15
    val InQuad = TimeInterpolator { it * it }                  // QEasingCurve 1
}
