package org.webosarchive.lunacy.card

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView

/**
 * A TV remote's D-pad and OK in a card (experimental, branch android-tv). webOS had no remote:
 * the page's compat layer (assets/lunacy/remote.js) keeps a focus over the page's controls,
 * shown in their own pressed state, and moves it for the D-pad. OK is a finger's tap on the
 * focused control: a real touch, sent where the page says the control is, so it reaches
 * input.js and the frameworks as any tap does. Holding OK holds the finger down.
 *
 * Only a remote's keys: a keyboard's arrows and Enter stay a keyboard's, as on webOS.
 */
class RemoteKeys(private val page: WebView) {
    /** Where the finger is down, in the view's pixels, while OK is held. */
    private var down: FloatArray? = null
    private var downTime = 0L
    /** The page hasn't said where to tap yet. */
    private var asking = false
    /** OK came up while the page was still being asked. */
    private var upEarly = false

    /** Handles [e] if it is a remote's; false leaves it to the page. */
    fun handle(e: KeyEvent): Boolean {
        if (!fromRemote(e)) return false
        val dir = when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"; KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"; KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            else -> null
        }
        if (dir != null) {
            if (e.action == KeyEvent.ACTION_DOWN) page.evaluateJavascript("window.__lunacyRemote&&__lunacyRemote.move('$dir')", null)
            return true
        }
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) press()
            KeyEvent.ACTION_UP -> release()
        }
        return true
    }

    private fun press() {
        if (asking || down != null) return
        asking = true; upEarly = false
        page.evaluateJavascript("window.__lunacyRemote?__lunacyRemote.point():null") { v ->
            asking = false
            val p = v?.trim('"')?.split(',')?.mapNotNull { it.toFloatOrNull() }
            if (p == null || p.size != 2) { upEarly = false; return@evaluateJavascript }
            val at = floatArrayOf(p[0] * page.width, p[1] * page.height)
            downTime = SystemClock.uptimeMillis()
            down = at
            touch(MotionEvent.ACTION_DOWN, at)
            if (upEarly) release()
        }
    }

    private fun release() {
        val at = down
        if (at == null) { if (asking) upEarly = true; return }
        down = null; upEarly = false
        touch(MotionEvent.ACTION_UP, at)
        page.evaluateJavascript("window.__lunacyRemote&&__lunacyRemote.tapped()", null)
    }

    private fun touch(action: Int, at: FloatArray) {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at[0]; y = at[1]; pressure = 1f; size = 1f })
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        page.dispatchTouchEvent(e)
        e.recycle()
    }

    companion object {
        private val KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)

        /**
         * A remote's (or a game pad's) D-pad and OK, not a keyboard's arrows and Enter: a real
         * device that isn't a full keyboard, or a key sent as a D-pad's alone (`adb shell input
         * dpad keyevent`, which comes from the virtual keyboard).
         */
        fun fromRemote(e: KeyEvent): Boolean {
            if (e.keyCode !in KEYS) return false
            val d = e.device
            if (d != null && !d.isVirtual) return d.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
            return e.isFromSource(InputDevice.SOURCE_DPAD) && !e.isFromSource(InputDevice.SOURCE_KEYBOARD)
        }
    }
}
