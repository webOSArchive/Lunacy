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
 * Orientation (codepoet: PDK games start from landscape and turn themselves): the screen is
 * held the device's way up while the card is up, and a buffer of the other shape is turned
 * a quarter ([orient]): counter-clockwise on a TouchPad, clockwise on a Pre3, each measured
 * on the device (Docs/pdk.md). The app is told its device's own screen, 1024 x 768 for the
 * TouchPad, 480 x 800 for the Pre3.
 */
@SuppressLint("ViewConstructor")
class PdkWindow(
    context: Context,
    appId: String,
    private val shell: WindowHost,
    private val app: AppInfo,
    runtime: PdkRuntime,
) : AppWindow(context, appId, shell), PdkHost.Listener {

    /** The device's screen is landscape (the TouchPad's 1024 x 768) or portrait (the Pre3's). */
    private val landscapeDevice = runtime.screenWidth >= runtime.screenHeight

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
        fixedOrientation = if (landscapeDevice) "right" else "up"
        // A PDK app had the whole screen on webOS, no status bar (codepoet); the shell slides
        // the bar away while the card is maximized, as for PalmSystem.enableFullScreenMode.
        fullScreen = true
    }

    /**
     * As the reference TouchPad shows a PDK app (measured 2026-10-02 with a probe holding
     * 1024 x 768, 768 x 1024 and 320 x 480 buffers): the screen stays the device's own way up -
     * landscape on a TouchPad - and a buffer of the other shape is turned a quarter
     * counter-clockwise and scaled to fit, letterboxed. An app that wants to be held another
     * way draws itself turned. PDL_SetOrientation only tells the system which way the app is
     * drawing, for its banners (the PDK's PDL.h); it moves nothing, so it is only noted. The
     * Pre3 profile's portrait screen is assumed to turn the same way (no Pre3 to measure).
     */
    private fun orient() {
        val gw = host.width; val gh = host.height
        if (gw <= 0 || gh <= 0) return
        // The TouchPad turns an other-shape buffer counter-clockwise, the Pre3 clockwise:
        // its top edge lands on the screen's right (measured 2026-10-03 with an 800 x 480
        // buffer on a Pre3; the TouchPad's on 2026-10-02).
        turn = if ((gw >= gh) == landscapeDevice) 0 else if (landscapeDevice) -90 else 90
        val o = if (landscapeDevice) "right" else "up"
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
        shell.fullScreen(this)   // the card exists by now
        if (gl) { glMode = width to height; startGl() }
        orient()
    }

    /** The GL mode, once the app has set one; with the version of the first batch, the stream can start. */
    private var glMode: Pair<Int, Int>? = null
    @Volatile private var glVersion = 0

    /**
     * An OpenGL ES app: its commands are replayed into a framebuffer of its own size and shown
     * on a texture over the card, letterboxed and turned by the native side (PdkGl); the frame
     * view stays on top for the touches. Starts when both the mode (main thread) and the first
     * batch (which says GLES 1.1 or 2; reader thread) have come, in whichever order.
     */
    private fun startGl() {
        val (width, height) = glMode ?: return
        val version = glVersion
        if (gl != null || version == 0 || !PdkGl.available) return
        val stream = PdkGl(appId, width, height, version, host::glAnswer) {
            post { if (!drawn) { drawn = true; shell.onStageReady(this); shell.onPageDrawn(this) } }
        }
        synchronized(earlyBatches) {
            for (b in earlyBatches) stream.replay(b)
            if (earlyBatches.isNotEmpty()) Log.i(AppServer.TAG, "pdk [$appId]: ${earlyBatches.size} GLES $version batches from before the stream")
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
        orient()
    }

    override fun onGl(batch: ByteArray, version: Int) {
        val stream = gl
        if (stream != null) { stream.replay(batch); return }
        synchronized(earlyBatches) {
            val g = gl
            if (g != null) { g.replay(batch); return }
            earlyBatches.add(batch)
            if (glVersion == 0) { glVersion = version; post { startGl() } }
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

    /**
     * What the app was last told, null before its card was first focused. SDL starts an app
     * active, so a card still waiting in the card view for its first frame has nothing to
     * tell: told it was deactivated, Fieldrunners paused before drawing that frame, and the
     * card waited for it for ever (on the Nexus 5, HP 10 G2 and Kyocera, as the timing fell).
     */
    private var toldActive: Boolean? = null

    override fun setStageActive(active: Boolean) {
        if (active) startAccel() else stopAccel()
        if (toldActive == null && !active) return
        if (toldActive == active) return
        toldActive = active
        host.active(active)
    }

    // ---- the accelerometer, as webOS's SDL joystick 0 (sdl-lunacy/SDL_lunacyjoystick.c) ----

    private val sensors get() = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
    private var accelListener: android.hardware.SensorEventListener? = null

    /**
     * Readings in the device's frame - the screen as the card holds it, x right, y up - and
     * webOS's units, measured on the TouchPad: 1 g = 32768, y positive toward the top of the
     * screen, z into it (the opposite of Android's z).
     */
    private fun startAccel() {
        if (accelListener != null) return
        val sm = sensors ?: return
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
                val (sx, sy) = when (rotation) {
                    android.view.Surface.ROTATION_90 -> -y to x
                    android.view.Surface.ROTATION_180 -> -x to -y
                    android.view.Surface.ROTATION_270 -> y to -x
                    else -> x to y
                }
                val k = 32768f / android.hardware.SensorManager.GRAVITY_EARTH
                host.accel((sx * k).toInt(), (sy * k).toInt(), (-z * k).toInt())
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor, a: Int) {}
        }
        accelListener = l
        sm.registerListener(l, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopAccel() {
        accelListener?.let { sensors?.unregisterListener(it) }
        accelListener = null
    }

    override fun destroy() {
        stopAccel()
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
