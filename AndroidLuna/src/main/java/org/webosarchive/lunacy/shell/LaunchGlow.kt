package org.webosarchive.lunacy.shell

import android.view.View

/**
 * LunaCE's launch feedback: the glow behind what was tapped to launch, until the launch is
 * done with it or 3 s run out (Launcher.Params.FEEDBACK_MS). Which item glows is [on], [none]
 * when none does; the view draws the glow itself.
 */
class LaunchGlow<T>(private val view: View, private val none: T) {
    var on: T = none
        private set
    private val clear = Runnable { on = none; view.invalidate() }

    fun show(item: T) {
        on = item
        view.removeCallbacks(clear)
        view.postDelayed(clear, Launcher.Params.FEEDBACK_MS)
        view.invalidate()
    }

    fun cancel() {
        view.removeCallbacks(clear)
        on = none
        view.invalidate()
    }
}
