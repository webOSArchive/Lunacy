package org.webosarchive.lunacy.card

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.AbsoluteLayout
import org.json.JSONObject

/**
 * A PDK app's card (Docs/pdk.md). The app is a process of its own ([PdkHost]); this window
 * is what the shell holds for it: an [AppWindow] that never loads a page, with a view over
 * it that draws the frames the process sends and hands it the touches. Everything the card
 * layer does with a window - size it, scale it, thumbnail it, close it - works on it as on
 * a web app's, and the loading card goes when the first frame arrives, as it went for a web
 * app when its page had drawn.
 *
 * The app's screen keeps its shape in the card: a 1024 x 768 game on a portrait phone is
 * drawn at the card's width with black above and below, as a letterboxed card.
 */
@SuppressLint("ViewConstructor")
class PdkWindow(
    context: Context,
    appId: String,
    private val shell: WindowHost,
    private val app: AppInfo,
    runtime: PdkRuntime,
) : AppWindow(context, appId, shell), PdkHost.Listener {

    private val host = PdkHost(context, appId, app, runtime, this)
    private val frameView = FrameView(context)
    private var drawn = false
    private var frame: Bitmap? = null

    init {
        settings.javaScriptEnabled = false
        setBackgroundColor(Color.BLACK)
        @Suppress("DEPRECATION")
        addView(frameView, AbsoluteLayout.LayoutParams(AbsoluteLayout.LayoutParams.MATCH_PARENT, AbsoluteLayout.LayoutParams.MATCH_PARENT, 0, 0))
        // PDK games were written for the TouchPad's landscape screen; most set it themselves
        // through PDL_SetOrientation, which the host relays (onPdl).
        fixedOrientation = "landscape"
    }

    /** Starts the process. False if the runtime isn't in this build. */
    fun start(): Boolean = host.start()

    // ---- what the host reports ----

    override fun onMode(width: Int, height: Int, gl: Boolean) { frameView.invalidate() }

    /** The first swap says the game is drawing; the card has nothing to show for it yet. */
    override fun onGlSwap() {
        if (!drawn) {
            drawn = true
            shell.onStageReady(this)
            shell.onPageDrawn(this)
        }
    }

    override fun onFrame(frame: Bitmap) {
        this.frame = frame
        frameView.invalidate()
        if (!drawn) {
            drawn = true
            shell.onStageReady(this)
            shell.onPageDrawn(this)
        }
    }

    override fun onCaption(title: String) { }

    override fun onPdl(request: JSONObject) {
        when (request.optString("call")) {
            "orientation" -> fixedOrientation = when (request.optInt("value")) {
                // PDL_ORIENTATION_0 is the TouchPad's "button below the screen": its portrait.
                0 -> "up"; 1 -> "right"; 2 -> "down"; 3 -> "left"; else -> fixedOrientation
            }
            "vibrate" -> runCatching {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(request.optInt("duration", 50).toLong())
            }
            else -> Log.i(AppServer.TAG, "pdk [$appId] PDL $request")
        }
    }

    override fun onExit(code: Int) { shell.onWindowClosed(this) }

    // ---- what the shell asks of a window ----

    override fun relaunch(params: String, done: (Boolean) -> Unit) = done(false)

    /** The back gesture: Palm's SDL gave a PDK app the gesture as an Escape key. */
    override fun sendBack() { host.key(true, PdkHost.SDLK_ESCAPE); host.key(false, PdkHost.SDLK_ESCAPE) }

    override fun setStageActive(active: Boolean) { host.active(active) }

    override fun destroy() {
        host.stop()
        super.destroy()
    }

    /** Draws the app's frames, keeping its aspect, and sends it the fingers in its own px. */
    private inner class FrameView(context: Context) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dst = Rect()

        /** Where the app's screen sits in this view. */
        private fun place(): Rect {
            val w = host.width; val h = host.height
            if (w <= 0 || h <= 0 || width <= 0 || height <= 0) return Rect(0, 0, width, height)
            val scale = minOf(width.toFloat() / w, height.toFloat() / h)
            val dw = (w * scale).toInt(); val dh = (h * scale).toInt()
            val x = (width - dw) / 2; val y = (height - dh) / 2
            return Rect(x, y, x + dw, y + dh)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val b = frame ?: return
            dst.set(place())
            canvas.drawBitmap(b, null, dst, paint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val r = place()
            if (r.width() <= 0 || host.width <= 0) return true
            fun send(i: Int, action: Int) {
                val x = ((e.getX(i) - r.left) * host.width / r.width()).toInt()
                val y = ((e.getY(i) - r.top) * host.height / r.height()).toInt()
                host.touch(e.getPointerId(i), action, x, y)
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> send(e.actionIndex, 0)
                MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) send(i, 1)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> send(e.actionIndex, 2)
            }
            return true
        }
    }
}
