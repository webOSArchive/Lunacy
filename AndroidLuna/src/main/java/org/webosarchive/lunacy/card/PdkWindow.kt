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
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
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
    /** Where a movie's path is looked up (libSDL_cinema): the webOS root, and the installed apps' folder. */
    private val runtimeWebosRoot: java.io.File? = runtime.webos?.root
    private val appsRoot: java.io.File = runtime.appsRoot

    private val host = PdkHost(context, appId, app, runtime, this)
    private val frameView = FrameView(context)
    private var drawn = false
    private var frame: Bitmap? = null
    /**
     * An OpenGL ES app: its commands are replayed into a framebuffer of its own size and shown
     * on a texture over the card, letterboxed and turned by the native side (PdkGl); the frame
     * view stays on top for the touches. The view is built on the main thread once the stream starts.
     */
    private val glStream = GlStream(
        make = { w, h, version ->
            if (!PdkGl.available) null
            else PdkGl(appId, w, h, version, host::glAnswer) {
                post { if (!drawn) { drawn = true; shell.onStageReady(this); shell.onPageDrawn(this) } }
            }
        },
        startOn = { start -> if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) start() else post(start) },
        onStarted = { stream, waiting -> showGl(stream, waiting) },
    )
    private val gl get() = glStream.stream
    private var glView: TextureView? = null
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
        // The app's screen is the size it set; the keyboard comes up over it rather than
        // shrinking the card, as on the TouchPad (codepoet, 2026-10-15).
        keyboardResizes = false
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
        fitGl()
        frameView.invalidate()
    }

    /**
     * The GL picture's view covers only the letterboxed picture, not the whole card. When the
     * card changes shape - the screen turning for it on a tablet held the other way - a
     * TextureView stretches its last frame to its new size until the next one comes, and an
     * app loading draws none for seconds: over the whole card, Temple Run 2's splash stayed
     * squashed into the card's old shape (HP 10 G2, 2026-10-16). Sized to the picture, the
     * stretch keeps its shape.
     */
    private fun fitGl() {
        val v = glView ?: return
        val r = placement().first
        @Suppress("DEPRECATION")
        val lp = v.layoutParams as? AbsoluteLayout.LayoutParams ?: return
        if (lp.x != r.left || lp.y != r.top || lp.width != r.width() || lp.height != r.height()) {
            lp.x = r.left; lp.y = r.top; lp.width = r.width(); lp.height = r.height()
            v.layoutParams = lp
        }
        // Placed here as well: called from onSizeChanged, inside the card's layout, the view
        // was already measured at the old size, and the second pass the new params ask for
        // never came on the Galaxy Z Fold3 - the picture kept its portrait size in the
        // landscape place, in a corner (2026-10-16).
        if (v.left != r.left || v.top != r.top || v.width != r.width() || v.height != r.height()) {
            v.measure(View.MeasureSpec.makeMeasureSpec(r.width(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(r.height(), View.MeasureSpec.EXACTLY))
            v.layout(r.left, r.top, r.right, r.bottom)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fitGl()
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
        if (gl) glStream.onMode(width, height)
        orient()
    }

    private fun showGl(stream: PdkGl, waiting: Int) {
        if (waiting > 0) Log.i(AppServer.TAG, "pdk [$appId]: $waiting GLES batches from before the stream")
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
        fitGl()
        orient()
    }

    override fun onGl(batch: ByteArray, version: Int) = glStream.onBatch(batch, version)

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
            "screenTimeout" -> { timeoutAllowed = request.optBoolean("on", false); holdScreen() }
            "cinema" -> cinema(request)
            "keyboard" -> {
                keyboardOn = request.optBoolean("on")
                if (keyboardOn) imm.restartInput(this)
                shell.keyboard(this, keyboardOn)
            }
            else -> Log.i(AppServer.TAG, "pdk [$appId] PDL $request")
        }
    }

    // ---- the keyboard (PDL_SetKeyboardState), its keys as SDL's ----

    /** Whether the app has asked for the keyboard. */
    private var keyboardOn = false
    private val imm get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

    override fun onCheckIsTextEditor() = keyboardOn

    /**
     * What the keyboard types goes to the app as key presses, each with its unicode, as webOS's
     * keyboard reached an SDL app. The field is a password-style one so a keyboard commits
     * each character as it is typed; text a keyboard still composes is typed and retyped.
     */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!keyboardOn) return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            private var composing = 0
            private fun replaceComposing(text: CharSequence?) {
                repeat(composing) { press(SDLK_BACKSPACE, 8) }
                text?.forEach { type(it) }
            }
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean { replaceComposing(text); composing = 0; return true }
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                replaceComposing(text); composing = text?.length ?: 0; return true
            }
            override fun finishComposingText(): Boolean { composing = 0; return true }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { press(SDLK_BACKSPACE, 8) }
                repeat(afterLength) { press(SDLK_DELETE, 127) }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean { sdlKey(event); return true }
            override fun performEditorAction(actionCode: Int): Boolean { press(SDLK_RETURN, 13); return true }
        }
    }

    /** A hardware keyboard's keys too, while the card has focus; the back key stays the shell's. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (event.keyCode != KeyEvent.KEYCODE_BACK && sdlKey(event)) true else super.dispatchKeyEvent(event)

    /** An Android key as SDL's: false when it has no SDL key. */
    private fun sdlKey(e: KeyEvent): Boolean {
        val down = when (e.action) { KeyEvent.ACTION_DOWN -> true; KeyEvent.ACTION_UP -> false; else -> return false }
        val sym = when (e.keyCode) {
            KeyEvent.KEYCODE_DEL -> SDLK_BACKSPACE; KeyEvent.KEYCODE_FORWARD_DEL -> SDLK_DELETE
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> SDLK_RETURN; KeyEvent.KEYCODE_TAB -> 9
            KeyEvent.KEYCODE_ESCAPE -> PdkHost.SDLK_ESCAPE; KeyEvent.KEYCODE_SPACE -> 32
            KeyEvent.KEYCODE_DPAD_UP -> 273; KeyEvent.KEYCODE_DPAD_DOWN -> 274
            KeyEvent.KEYCODE_DPAD_RIGHT -> 275; KeyEvent.KEYCODE_DPAD_LEFT -> 276
            else -> -1
        }
        val c = e.unicodeChar
        if (sym >= 0) { host.key(down, sym, if (sym < 128) sym else 0); return true }
        if (c == 0 || c and android.view.KeyCharacterMap.COMBINING_ACCENT != 0) return false
        host.key(down, symOf(c.toChar()), c)
        return true
    }

    private fun type(c: Char) = if (c == '\n') press(SDLK_RETURN, 13) else press(symOf(c), c.code)

    private fun press(sym: Int, unicode: Int) { host.key(true, sym, unicode); host.key(false, sym, 0) }

    /** SDL 1.2's keysym for a character: its ASCII code, lower case for a letter; 0 beyond ASCII (the unicode carries it). */
    private fun symOf(c: Char) = if (c.code < 128) c.lowercaseChar().code else 0

    private companion object {
        const val SDLK_BACKSPACE = 8; const val SDLK_RETURN = 13; const val SDLK_DELETE = 127
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

    /**
     * The screen stays on while a PDK app has focus, as on the TouchPad (codepoet, 2026-10-15),
     * unless the app has let it time out (PDL_ScreenTimeoutEnable(true)).
     */
    private var focused = false
    private var timeoutAllowed = false
    private fun holdScreen() = shell.blockScreenTimeout(this, focused && !timeoutAllowed)

    override fun setStageActive(active: Boolean) {
        focused = active; holdScreen()
        movie?.setActive(active)
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
     * Readings as webOS gave them (Workbench/probe/accelprobe on the reference TouchPad and
     * turnprobe on the Pre3; Docs/pdk.md): 1 g = 32768, in the device's own hardware frame,
     * which is portrait on both - the TouchPad's landscape screen doesn't turn it. With the
     * TouchPad held landscape, home button on the right, axis 0 was -1 g upright and axis 1
     * +0.5 g with the right edge lowered (2026-10-16); on the Pre3 axis 0 is positive with the
     * right edge down. So against the screen, with g the way gravity pulls (x right, y up, z
     * out of the screen): the TouchPad's axes are (gy, gx, gz), the Pre3's (gx, -gy, gz).
     * Android's readings are the other way from gravity, in the card's frame.
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
                // Gravity in the card's frame is (-sx, -sy, -z). Sent as Android's axes were, every
                // tilt game steered backwards, and sideways on the tablet profile.
                val k = 32768f / android.hardware.SensorManager.GRAVITY_EARTH
                val gx = -sx * k; val gy = -sy * k; val gz = -z * k
                if (landscapeDevice) host.accel(gy.toInt(), gx.toInt(), gz.toInt())
                else host.accel(gx.toInt(), (-gy).toInt(), gz.toInt())
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
        movie?.end(null)
        stopAccel()
        shell.blockScreenTimeout(this, false)
        glStream.destroy()
        host.stop()
        super.destroy()
    }

    // ---- movies: Palm's libSDL_cinema (LunaRuntimes/pdk/libcinema), played by Android ----

    /** The movie the app loaded (CIN_LoadCIN), not yet played. */
    private var movieFile: java.io.File? = null
    private var movie: Movie? = null

    /**
     * libSDL_cinema's calls. On the TouchPad the media player drew the movie over the app at the
     * display's size and paused it while the app was in the background; the end of the movie, or
     * an error, became SDL_CINEMAEVENT in the app's queue, and CIN_Stop ended it with no event
     * (its disassembly, 2026-11-07). Here Android's MediaPlayer plays it over the card, on black,
     * letterboxed and turned as the card turns a game's screen of the other shape ([orient]);
     * touches still reach the game, which may skip its movie on a tap.
     */
    private fun cinema(r: JSONObject) {
        when (r.optString("op")) {
            "load" -> { movie?.end(null); movieFile = cinemaFile(r.optString("url")) }
            "play" -> {
                val m = movie
                if (m != null) { m.resume(); return }
                val f = movieFile?.takeIf { it.isFile }
                if (f == null) { Log.w(AppServer.TAG, "pdk [$appId] cinema: no movie to play (${movieFile?.path})"); host.cinemaEnded(true); return }
                movie = Movie(f)
            }
            "pause" -> movie?.pause()
            "stop" -> movie?.end(null)
        }
    }

    /** A movie's URL, file:// or a path (one without a leading slash in the app's own folder), as a real file. */
    private fun cinemaFile(url: String): java.io.File? {
        val path = url.removePrefix("file://").takeIf { it.isNotEmpty() } ?: return null
        val root = runtimeWebosRoot ?: return null
        return when {
            path.startsWith("/media/internal/") -> UserFiles.resolve(root, path.removePrefix("/media/internal/"))
            path.startsWith("/") -> java.io.File(root, path.trimStart('/'))
            else -> java.io.File(java.io.File(appsRoot, app.dir), path)
        }
    }

    /** One movie playing over the card. Main thread. */
    private inner class Movie(file: java.io.File) {
        private val box = android.widget.FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        private val texture = TextureView(context)
        private val player = android.media.MediaPlayer()
        private val input = java.io.FileInputStream(file)
        private var prepared = false
        private var playing = true
        private var stageActive = focused
        private var ended = false

        init {
            box.addView(texture, android.widget.FrameLayout.LayoutParams(0, 0, android.view.Gravity.CENTER))
            @Suppress("DEPRECATION")
            addView(box, AbsoluteLayout.LayoutParams(AbsoluteLayout.LayoutParams.MATCH_PARENT, AbsoluteLayout.LayoutParams.MATCH_PARENT, 0, 0))
            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                    runCatching { player.setSurface(android.view.Surface(st)) }
                }
                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
            }
            box.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fit() }
            try {
                // A descriptor, not the path: the media server can't open a file in Lunacy's own storage.
                player.setDataSource(input.fd)
                player.setOnPreparedListener { prepared = true; fit(); if (playing && stageActive) player.start() }
                player.setOnCompletionListener { end(false) }
                player.setOnErrorListener { _, what, extra -> Log.w(AppServer.TAG, "pdk [$appId] cinema: error $what/$extra"); end(true); true }
                player.prepareAsync()
                Log.i(AppServer.TAG, "pdk [$appId] cinema: playing ${file.name}")
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "pdk [$appId] cinema: ${file.name}: ${e.javaClass.simpleName}")
                post { end(true) }
            }
        }

        /** Fits the picture in the card, letterboxed, turned when its shape is the other one from the device's (as [orient] turns a game). */
        private fun fit() {
            if (!prepared) return
            val vw = player.videoWidth; val vh = player.videoHeight
            val cw = box.width; val ch = box.height
            if (vw <= 0 || vh <= 0 || cw <= 0 || ch <= 0) return
            val turned = (vw >= vh) != landscapeDevice
            val (sw, sh) = if (turned) vh to vw else vw to vh
            val scale = minOf(cw.toFloat() / sw, ch.toFloat() / sh)
            val w = Math.round(vw * scale); val h = Math.round(vh * scale)
            texture.rotation = if (!turned) 0f else if (landscapeDevice) -90f else 90f
            val lp = texture.layoutParams as android.widget.FrameLayout.LayoutParams
            if (lp.width != w || lp.height != h) { lp.width = w; lp.height = h; texture.layoutParams = lp }
        }

        fun pause() { playing = false; if (prepared) runCatching { player.pause() } }
        fun resume() { playing = true; if (prepared && stageActive) runCatching { player.start() } }

        /** The app in front or behind: the movie waits behind, as Palm's PausedCallback had it. */
        fun setActive(active: Boolean) {
            stageActive = active
            if (!prepared) return
            if (!active) runCatching { player.pause() } else if (playing) runCatching { player.start() }
        }

        /** Ends the movie; [error] null is CIN_Stop's (or the card closing), with no event. */
        fun end(error: Boolean?) {
            if (ended) return
            ended = true
            runCatching { player.stop() }
            player.release()
            runCatching { input.close() }
            removeView(box)
            if (movie === this) movie = null
            if (error != null) host.cinemaEnded(error)
        }
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
