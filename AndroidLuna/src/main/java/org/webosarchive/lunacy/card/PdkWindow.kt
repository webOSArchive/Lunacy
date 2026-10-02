package org.webosarchive.lunacy.card

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.widget.AbsoluteLayout
import org.json.JSONObject

/**
 * A PDK app's card (Docs/pdk.md). The app is a process of its own ([PdkHost]); this window
 * is what the shell holds for it: an [AppWindow] that never loads a page, with a view over
 * it that shows the app's screen and hands it the touches. A 2D app's frames arrive as
 * bitmaps ([onFrame]); an OpenGL ES app's commands are replayed on a texture ([PdkGl]).
 * Everything the card layer does with a window - size it, scale it, thumbnail it, close
 * it - works on it as on a web app's, and the loading card goes when the first frame
 * arrives, as it went for a web app when its page had drawn.
 *
 * The app's screen keeps its shape in the card: a 1024 x 768 game on a portrait phone is
 * drawn at the card's width with black above and below, as a letterboxed card.
 *
 * Orientation (codepoet): PDK games start from landscape. A TouchPad game's buffer is
 * landscape as it is; a phone-era game drew for a 320 x 480 screen held sideways, its
 * picture turned inside its buffer, and the card turns the buffer back. PDL_SetOrientation
 * counts from "the action button below the screen" - the TouchPad's landscape, the Pre's
 * portrait - so its value says which way the buffer's bottom edge faces ([orient]). The
 * card's own orientation follows the shape of the turned picture.
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
    @Volatile private var gl: PdkGl? = null
    private var glView: TextureView? = null
    /** Batches that came before the mode reached the main thread: the first one holds the textures. */
    private val earlyBatches = ArrayList<ByteArray>()
    /** The PDL_Orientation the app asked for, if it did. */
    private var pdlOrientation: Int? = null
    /** How the buffer is shown: 0, 90, 180 or -90, clockwise. */
    private var turn = 0

    init {
        settings.javaScriptEnabled = false
        setBackgroundColor(Color.BLACK)
        @Suppress("DEPRECATION")
        addView(frameView, AbsoluteLayout.LayoutParams(AbsoluteLayout.LayoutParams.MATCH_PARENT, AbsoluteLayout.LayoutParams.MATCH_PARENT, 0, 0))
        fixedOrientation = "landscape"
    }

    /** Works out the turn from the buffer's shape and the PDL request, and the card's orientation from it. */
    private fun orient() {
        val gw = host.width; val gh = host.height
        turn = when (pdlOrientation) {
            0 -> 0; 1 -> 90; 2 -> 180; 3 -> -90
            else -> if (gh > gw) -90 else 0   // the landscape starting point
        }
        val quarter = turn == 90 || turn == -90
        val landscape = if (quarter) gh > gw else gw >= gh
        val o = if (landscape) "landscape" else "portrait"
        if (o != fixedOrientation) { fixedOrientation = o; shell.orientationRequested(this) }
        gl?.turn(turn)
        frameView.invalidate()
    }

    /** Starts the process. False if the runtime isn't in this build. */
    fun start(): Boolean = host.start()

    /** Where the app's screen sits in this window, and the turn it is shown with. */
    private fun placement(): Pair<Rect, Int> {
        val gw = host.width; val gh = host.height
        if (gw <= 0 || gh <= 0 || width <= 0 || height <= 0) return Rect(0, 0, width, height) to 0
        val rot = turn
        val quarter = rot == 90 || rot == -90
        val sw = if (quarter) gh else gw; val sh = if (quarter) gw else gh
        val scale = minOf(width.toFloat() / sw, height.toFloat() / sh)
        val dw = (sw * scale).toInt(); val dh = (sh * scale).toInt()
        val x = (width - dw) / 2; val y = (height - dh) / 2
        return Rect(x, y, x + dw, y + dh) to rot
    }

    // ---- what the host reports ----

    override fun onMode(width: Int, height: Int, gl: Boolean) {
        if (gl && this.gl == null && PdkGl.available) {
            // An OpenGL ES app: its commands are replayed on a texture the size of the window,
            // letterboxed and turned by the native side (PdkGl); the frame view stays on top
            // for the touches.
            val stream = PdkGl(appId, width, height)
            synchronized(earlyBatches) {
                for (b in earlyBatches) stream.replay(b)
                if (earlyBatches.isNotEmpty()) Log.i(AppServer.TAG, "pdk [$appId]: ${earlyBatches.size} GL batches from before the mode")
                earlyBatches.clear()
                this.gl = stream
            }
            val view = TextureView(context)
            view.isOpaque = true
            view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) { stream.attach(t) }
                override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean = false
                override fun onSurfaceTextureUpdated(t: SurfaceTexture) {}
            }
            glView = view
            @Suppress("DEPRECATION")
            addView(view, AbsoluteLayout.LayoutParams(AbsoluteLayout.LayoutParams.MATCH_PARENT, AbsoluteLayout.LayoutParams.MATCH_PARENT, 0, 0))
            frameView.bringToFront()
        }
        orient()
    }

    override fun onGl(batch: ByteArray) {
        val stream = gl
        if (stream != null) { stream.replay(batch); return }
        synchronized(earlyBatches) { if (gl == null) earlyBatches.add(batch) else gl?.replay(batch) }
    }

    /** The app's swap: the replayed frame is shown, and the first one takes the loading card away. */
    override fun onGlSwap() {
        val stream = gl ?: return
        stream.swap {
            if (!drawn) {
                drawn = true
                post { shell.onStageReady(this); shell.onPageDrawn(this) }
            }
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
            "orientation" -> { pdlOrientation = request.optInt("value", -1).takeIf { it in 0..3 }; orient() }
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
        gl?.destroy(); gl = null
        host.stop()
        super.destroy()
    }

    /** Draws a 2D app's frames in place, and sends every app the fingers in its own px. */
    private inner class FrameView(context: Context) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dst = Rect()

        override fun onDraw(canvas: Canvas) {
            if (gl != null) return  // the texture underneath is the picture
            canvas.drawColor(Color.BLACK)
            val b = frame ?: return
            val (r, rot) = placement()
            if (rot == 0) { canvas.drawBitmap(b, null, r, paint); return }
            canvas.save()
            canvas.rotate(rot.toFloat(), r.exactCenterX(), r.exactCenterY())
            if (rot == 180) dst.set(r)
            else dst.set(r.centerX() - r.height() / 2, r.centerY() - r.width() / 2, r.centerX() + r.height() / 2, r.centerY() + r.width() / 2)
            canvas.drawBitmap(b, null, dst, paint)
            canvas.restore()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val (r, rot) = placement()
            val gw = host.width; val gh = host.height
            if (r.width() <= 0 || r.height() <= 0 || gw <= 0) return true
            fun send(i: Int, action: Int) {
                val px = e.getX(i); val py = e.getY(i)
                val x: Int; val y: Int
                when (rot) {
                    -90 -> { x = ((r.bottom - py) * gw / r.height()).toInt(); y = ((px - r.left) * gh / r.width()).toInt() }
                    90 -> { x = ((py - r.top) * gw / r.height()).toInt(); y = ((r.right - px) * gh / r.width()).toInt() }
                    180 -> { x = ((r.right - px) * gw / r.width()).toInt(); y = ((r.bottom - py) * gh / r.height()).toInt() }
                    else -> { x = ((px - r.left) * gw / r.width()).toInt(); y = ((py - r.top) * gh / r.height()).toInt() }
                }
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
