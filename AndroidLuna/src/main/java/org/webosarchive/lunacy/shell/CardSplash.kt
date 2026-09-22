package org.webosarchive.lunacy.shell

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

/**
 * The placeholder a card shows while its app is still loading.
 *
 * webOS put a card in the switcher the moment an app was launched, before the app had drawn
 * anything: a dark card with the app's own `splashicon` in the middle, so the space is held
 * and the launch is visibly under way. codepoet's screenshot of Palm's Clock starting is one.
 *
 * Drawn as LunaSysMgr's `CardLoading` draws it, from
 * [Docs/luna-shell-reference.md](../../../../../../../../Docs/luna-shell-reference.md) §2.4:
 *
 *  - `loading-bg.png` stretched to the card;
 *  - the icon centred, at SplashIconSize (192 on a TouchPad);
 *  - `loading-glow.png` centred behind it, its opacity pulsing 0 -> 1 -> 0 over a second,
 *    the first pulse 900 ms in and a second of quiet between pulses;
 *  - and when the app is ready, the whole thing fades out over 300 ms.
 *
 * The artwork is the device's own. Note its glow is **342 x 342** where LunaCE's source says
 * 228: the shipped asset is the one to believe.
 */
class CardSplash(context: Context, private val luna: Luna, private val icon: Bitmap?) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val background = luna.image("loading-bg.png")
    private val glow = luna.image("loading-glow.png")
    private var glowAlpha = 0f
    private val dst = Rect()

    private val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = PULSE_MS + QUIET_MS
        startDelay = FIRST_PULSE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = null  // linear: the shape is in the mapping below
        addUpdateListener {
            val t = it.animatedValue as Float
            // The first half of the cycle is the pulse, up and back down; the rest is quiet.
            val u = t * (PULSE_MS + QUIET_MS) / PULSE_MS
            glowAlpha = when {
                u >= 1f -> 0f
                u <= 0.5f -> u * 2f
                else -> (1f - u) * 2f
            }
            invalidate()
        }
    }

    init { pulse.start() }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulse.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        background?.let {
            dst.set(0, 0, width, height)
            canvas.drawBitmap(it, null, dst, paint)
        } ?: canvas.drawColor(android.graphics.Color.rgb(0x2E, 0x2E, 0x2E))
        val cx = width / 2f
        val cy = height / 2f
        if (glowAlpha > 0f) glow?.let {
            paint.alpha = (glowAlpha * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(it, cx - it.width / 2f, cy - it.height / 2f, paint)
            paint.alpha = 255
        }
        icon?.let { canvas.drawBitmap(it, cx - it.width / 2f, cy - it.height / 2f, paint) }
    }

    /** The app has drawn: cross-fade away and take this view out of the card. */
    fun dismiss() {
        if (alpha < 1f) return
        animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            pulse.cancel()
            (parent as? android.view.ViewGroup)?.removeView(this)
        }.start()
    }

    private companion object {
        const val FIRST_PULSE_MS = 900L
        const val PULSE_MS = 1000L
        const val QUIET_MS = 1000L
        const val FADE_MS = 300L
    }
}
