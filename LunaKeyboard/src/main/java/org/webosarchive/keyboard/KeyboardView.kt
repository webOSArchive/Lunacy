package org.webosarchive.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * webOS's tablet keyboard, drawn the way LunaCE drew it.
 *
 * There is one size, and it is the art's own. LunaCE offered four heights and scaled its
 * pictures to fit them; at anything but the tallest, the glyphs and the key bevels were
 * resampled and went soft - LunaCE even shrank its nine-tile corners to compensate
 * (`m_9tileCorner.m_trimV`). Here every picture and every letter is drawn at its own pixel
 * size, and a key only ever stretches through its flat middle, so nothing is ever resampled.
 *
 * That makes the keyboard 335 px of keys - 55 for the number row and 70 for each of the
 * other four - which is LunaCE's own `fullKeymapHeight`, the height its comment calls the
 * assets' "ideal non-scaled" size.
 *
 * The view is taller than that by [headroom], and the extra is transparent. It is where the
 * press-and-hold balloon goes, and it has to be part of this view rather than a window of its
 * own because Android clamps a touch to the frame of the window that caught it: a finger
 * dragged above the keyboard arrives with y pinned to 0, so anything drawn up there could be
 * seen but never touched. [KeyboardService.onComputeInsets] keeps the app from being pushed
 * up by it, and keeps taps in it going to the app.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val host: Host) : View(context) {

    /** What the keyboard needs from whatever is hosting it. */
    interface Host {
        fun onText(text: String)
        fun onBackspace(word: Boolean)
        fun onEnter()
        fun onTab()
        fun onHide()
        /** One step of the cursor, as an arrow key; [select] extends the selection. */
        fun onCursor(dx: Int, dy: Int, select: Boolean)
        /** The label webOS would have put on Return: "Done", "Go", "Search"… */
        fun enterLabel(): String
    }

    private val art = Art(context)
    private val handler = Handler(Looper.getMainLooper())

    // --- Sizes ------------------------------------------------------------------------------

    /**
     * A row is as tall as its plate. Straight from the art, as LunaCE did: a plate image holds
     * two states, so its own row height is half its height. The number row uses a short plate.
     */
    private fun rowHeight(row: Int): Int {
        val plate = art.image(if (row == 0) "key-gray-short.png" else "key-white.png") ?: return 70
        return plate.height / 2
    }

    private val keysHeight get() = Keymap.rows.indices.sumOf { rowHeight(it) }

    /** The background is a little taller than the keys; webOS left that gap above them. */
    private val topPadding get() = Math.max(0, (art.image("keyboard-bg.png")?.height ?: 340) - keysHeight)

    /**
     * Transparent room above the keyboard for the press-and-hold balloon. The tallest balloon
     * is two lines of accents, and it overlaps its key by [POPUP_TOP_TO_KEY], so that is the
     * furthest anything ever reaches above the keys.
     */
    val headroom get() = Math.max(0, (art.image("popup-bg-2.png")?.height ?: 150) - POPUP_TOP_TO_KEY)

    /** Where the keyboard proper starts: everything above this is the balloon's room. */
    fun keyboardTop() = height - keysHeight - topPadding

    // --- State ------------------------------------------------------------------------------

    private enum class Shift { OFF, ONCE, LOCK }
    private var shift = Shift.OFF
    private var symbols = false
    private var lastShiftTap = 0L

    private var pressed: Key? = null

    /** The press-and-hold popup: the characters offered, and which one the finger is over. */
    private var extended: String? = null
    private var extendedFrame: RectF? = null
    private var extendedIndex = 0

    private var trackball = false
    private var trackDeltaX = 0f
    private var trackDeltaY = 0f
    private var trackVX = 0f
    private var trackVY = 0f

    // --- Layout -----------------------------------------------------------------------------

    private class Zone(val key: Key, val rect: RectF, val row: Int)
    private val zones = ArrayList<Zone>()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), headroom + topPadding + keysHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        zones.clear()
        // The keys sit at the bottom, as they did on webOS, whatever room the host gave us.
        var top = (h - keysHeight).toFloat()
        for ((r, row) in Keymap.rows.withIndex()) {
            val bottom = top + rowHeight(r)
            val units = row.sumOf { Math.abs(it.weight).toDouble() }.toFloat()
            var left = 0f
            var sofar = 0f
            for (key in row) {
                sofar += Math.abs(key.weight)
                // Whole pixels: a key boundary landing mid-pixel puts a seam down its side.
                val right = Math.round(w * sofar / units).toFloat()
                zones += Zone(key, RectF(left, top, right, bottom), r)
                left = right
            }
            top = bottom
        }
    }

    // --- Drawing ----------------------------------------------------------------------------

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val prelude = art.font("Prelude-Medium.ttf")
    private val preludeBold = art.font("Prelude-Bold.ttf")

    private val activeColor = Color.rgb(20, 20, 20)
    private val disabledColor = Color.rgb(100, 100, 100)
    private val functionColor = Color.rgb(0xd2, 0xd2, 0xd2)

    override fun onDraw(c: Canvas) {
        art.image("keyboard-bg.png")?.let {
            c.drawBitmap(it, null, RectF(0f, keyboardTop().toFloat(), width.toFloat(), height.toFloat()), null)
        }
        for (z in zones) drawKey(c, z)
        drawExtended(c)
    }

    /** The plate under a key: webOS picked it by what kind of key it is. */
    private fun plateFor(z: Zone): String? = when {
        z.key.weight < 0 -> null                      // an edge zone, not a key
        z.key.main == Fn.TRACKBALL -> null            // the ball draws itself, on the background
        z.row == 0 -> "key-gray-short.png"
        z.key.main == Fn.SHIFT -> when (shift) {
            Shift.LOCK -> "key-shift-lock.png"
            Shift.ONCE -> "key-shift-on.png"
            Shift.OFF -> "key-black.png"
        }
        Fn.isFunction(z.key.main) -> "key-black.png"
        Character.isLetterOrDigit(z.key.main) || z.key.main == ' '.code -> "key-white.png"
        else -> "key-gray.png"
    }

    private fun drawKey(c: Canvas, z: Zone) {
        val down = pressed === z.key
        plateFor(z)?.let { name ->
            art.image(name)?.let { Art.plate(c, it, z.rect, down, paint) }
        }
        if (z.key.weight < 0) return
        if (z.key.main == Fn.TRACKBALL) { drawTrackball(c, z.rect); return }

        // LunaCE trims the bottom of the cap area, and nudges it down while the key is held.
        val box = RectF(z.rect.left, z.rect.top, z.rect.right, z.rect.bottom - 4)
        if (down) box.offset(0f, 2f)

        icon(z)?.let { name ->
            art.image(name)?.let { Art.centred(c, it, box.centerX(), box.centerY(), paint) }
            return
        }
        drawCap(c, z, box)
    }

    private fun icon(z: Zone): String? = when (z.key.main) {
        Fn.BACKSPACE -> "icon-delete.png"
        Fn.HIDE -> "icon-hide-keyboard.png"
        Fn.SHIFT -> when (shift) {
            Shift.LOCK -> "icon-shift-lock.png"
            Shift.ONCE -> "icon-shift-on.png"
            Shift.OFF -> "icon-shift.png"
        }
        else -> null
    }

    /** The lettering on a key, in webOS's two-character arrangements where it had them. */
    private fun drawCap(c: Canvas, z: Zone, box: RectF) {
        val key = z.key
        when (key.main) {
            Fn.TAB -> { label(c, "Tab", box, functionColor, 22f, true); return }
            Fn.SYMBOL -> { label(c, Keymap.SYMBOL_LABEL, box, functionColor, 22f, true); return }
            Fn.RETURN -> { drawEnter(c, box); return }
            ' '.code -> return
        }
        val main = key.main.toChar()
        val alt = key.altText ?: (if (key.alt != key.main) key.alt.toChar().toString() else null)
        // Row 0 shows both characters side by side; punctuation below it stacks them, unless
        // the key is too short to stack them, when webOS laid those out side by side as well.
        var across = z.row == 0 && alt != null
        var stacked = z.row > 0 && alt != null && !Character.isLetterOrDigit(key.main)
        val size = 24f
        var nudge = 1f
        if (stacked && box.height().toInt() / 3 < size - 2) { stacked = false; across = true; nudge = 2f }
        val capitalised = shift != Shift.OFF
        val face = if (capitalised) main.uppercaseChar().toString() else main.toString()

        if (!across && !stacked) {
            val t = if (symbols && alt != null) alt else face
            label(c, t, box, activeColor, if (t.length > 1) 22f else 26f, t.length > 1)
            return
        }
        // On the symbol page the alternate character is the one being typed, so it takes the
        // dark colour and the full size, and the plain character fades back.
        val mainColor = if (symbols) disabledColor else activeColor
        val altColor = if (symbols) activeColor else disabledColor
        if (across) {
            val lead = if (symbols) 5f else 4f
            val r = RectF(box.left + lead, box.top + 1, box.right - (9 - lead), box.bottom + 1)
            val half = r.width() / 2
            val mainLeft = r.left + half - nudge
            val altLeft = r.left + nudge
            label(c, face, RectF(mainLeft, r.top, mainLeft + half, r.bottom), mainColor, sized(size, mainColor), false)
            label(c, alt!!, RectF(altLeft, r.top, altLeft + half, r.bottom), altColor, sized(size, altColor), false)
        } else {
            val third = box.height() / 3
            val lower = RectF(box.left, box.bottom - third - 10, box.right, box.bottom - 10)
            val upper = RectF(box.left, box.top + 10, box.right, box.top + 10 + third)
            label(c, face, lower, mainColor, sized(size, mainColor), false)
            label(c, alt!!, upper, altColor, sized(size, altColor), false)
        }
    }

    /** webOS shrank the quieter of two characters to three quarters. */
    private fun sized(base: Float, color: Int) = if (color == activeColor) base else base * 75 / 100

    /** Return's label sits small in the bottom right corner, as it did on webOS. */
    private fun drawEnter(c: Canvas, box: RectF) {
        val r = RectF(box.left, box.top, box.left + box.width() * 85 / 100, box.top + box.height() * 80 / 100)
        text.typeface = preludeBold
        text.textSize = 22f
        text.color = functionColor
        text.textAlign = Paint.Align.RIGHT
        c.drawText(host.enterLabel(), r.right, r.bottom, text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun label(c: Canvas, s: String, box: RectF, color: Int, size: Float, bold: Boolean) {
        text.typeface = if (bold) preludeBold else prelude
        text.color = color
        var px = size
        text.textSize = px
        // webOS shrank a label that didn't fit rather than letting it run over the key.
        val room = box.width() - 16
        if (room > 0) while (text.measureText(s) > room && px > 6) { px -= 1; text.textSize = px }
        val m = text.fontMetrics
        c.drawText(s, box.centerX(), box.centerY() - (m.ascent + m.descent) / 2, text)
    }

    // --- The scroll ball --------------------------------------------------------------------

    /**
     * LunaCE's addition to Palm's keyboard: a ball in the corner that drags the insertion
     * point around. The four arrows light up in the direction it is being pushed and fade
     * back once it is let go.
     */
    private fun drawTrackball(c: Canvas, rect: RectF) {
        val ball = art.image("trackball.png") ?: return
        val arrow = art.image("menu-arrow-down.png") ?: return
        val cx = rect.centerX(); val cy = rect.centerY()
        Art.centred(c, ball, cx, cy, paint)
        val lit = floatArrayOf(trackVY, -trackVX, -trackVY, trackVX)  // down, left, up, right
        c.save()
        c.translate(Math.round(cx).toFloat(), Math.round(cy).toFloat())
        for (i in 0..3) {
            paint.alpha = (Math.min(1f, 0.2f + Math.max(0f, lit[i])) * 255).toInt()
            c.drawBitmap(arrow, -arrow.width / 2f, arrow.height / 3f, paint)
            c.rotate(90f)
        }
        c.restore()
        paint.alpha = 255
    }

    private val trackTick = object : Runnable {
        override fun run() {
            if (trackVX == 0f && trackVY == 0f) return
            trackVX = decay(trackVX); trackVY = decay(trackVY)
            invalidate()
            handler.postDelayed(this, TRACK_TICK)
        }
    }

    private fun decay(v: Float) = when {
        v > 0.1f -> v - 0.1f
        v < -0.1f -> v + 0.1f
        else -> 0f
    }

    private fun trackMove(dx: Float, dy: Float) {
        trackDeltaX += dx; trackDeltaY += dy
        val select = shift != Shift.OFF
        if (trackDeltaX >= TRACK_STEP) { trackDeltaX = 0f; trackVX = Math.min(1f, trackVX + 0.1f); host.onCursor(1, 0, select) }
        if (trackDeltaX <= -TRACK_STEP) { trackDeltaX = 0f; trackVX = Math.max(-1f, trackVX - 0.1f); host.onCursor(-1, 0, select) }
        if (trackDeltaY <= -TRACK_STEP) { trackDeltaY = 0f; trackVY = Math.max(-1f, trackVY - 0.1f); host.onCursor(0, -1, select) }
        if (trackDeltaY >= TRACK_STEP) { trackDeltaY = 0f; trackVY = Math.min(1f, trackVY + 0.1f); host.onCursor(0, 1, select) }
        invalidate()
    }

    // --- The press-and-hold popup -----------------------------------------------------------

    private fun twoLines(chars: String) = chars.length > POPUP_SINGLE_LINE_MAX
    private fun perLine(chars: String) = if (twoLines(chars)) (chars.length + 1) / 2 else chars.length
    private fun popupBg(chars: String) = if (twoLines(chars)) "popup-bg-2.png" else "popup-bg.png"

    private fun drawExtended(c: Canvas) {
        val chars = extended ?: return
        val frame = extendedFrame ?: return
        val bg = art.image(popupBg(chars)) ?: return
        // The balloon's pointer is part of the art, so only the side away from it stretches.
        Art.stretchH(c, bg, frame, POPUP_POINTER_END, POPUP_RIGHT, paint)
        val cell = art.image("popup-key.png") ?: return
        val ch = cell.height / 2f
        val line0 = perLine(chars)
        for (i in chars.indices) {
            val x = frame.left + POPUP_LEFT + (i % line0) * POPUP_KEY_WIDTH
            val y = frame.top + POPUP_LEFT + (i / line0) * ch
            val r = RectF(x, y, x + POPUP_KEY_WIDTH, y + ch)
            Art.plate(c, cell, r, i == extendedIndex, paint)
            label(c, chars[i].toString(), r, activeColor, 22f, false)
        }
    }

    private fun showExtended(z: Zone) {
        val chars = z.key.extended ?: return
        extended = chars
        extendedIndex = 0
        val h = art.image(popupBg(chars))?.height ?: 90
        val w = POPUP_LEFT + POPUP_RIGHT + perLine(chars) * POPUP_KEY_WIDTH
        val left = (z.rect.centerX() - POPUP_KEY_WIDTH / 2f - POPUP_LEFT)
            .coerceIn(0f, Math.max(0f, width - w.toFloat()))
        // Above the key, which [headroom] guarantees is still inside the view.
        val top = Math.max(0f, z.rect.top - h + POPUP_TOP_TO_KEY)
        extendedFrame = RectF(left, top, left + w, top + h)
        invalidate()
    }

    private fun hideExtended() {
        extended = null
        extendedFrame = null
    }

    private fun extendedAt(x: Float, y: Float): Int {
        val chars = extended ?: return 0
        val frame = extendedFrame ?: return 0
        // A finger still on the key, or off the side of the balloon, means the plain
        // character: the popup's first cell, which is the key's own.
        if (!frame.contains(x, y)) return 0
        val line0 = perLine(chars)
        val col = (((x - frame.left - POPUP_LEFT) / POPUP_KEY_WIDTH).toInt()).coerceIn(0, line0 - 1)
        val ch = (art.image("popup-key.png")?.height ?: 120) / 2f
        val line = (((y - frame.top - POPUP_LEFT) / ch).toInt()).coerceAtLeast(0)
        return (line * line0 + col).coerceIn(0, chars.length - 1)
    }

    // --- Touch ------------------------------------------------------------------------------

    private fun zoneAt(x: Float, y: Float): Zone? = zones.firstOrNull { it.rect.contains(x, y) }

    private val longPress = Runnable { pressedZone?.let { showExtended(it) } }
    private var pressedZone: Zone? = null

    private var repeats = 0
    private val repeat = object : Runnable {
        override fun run() {
            val z = pressedZone ?: return
            if (z.key.main != Fn.BACKSPACE) return
            repeats++
            val word = repeats * LETTER_REPEAT > WORD_DELETE_AFTER
            host.onBackspace(word)
            handler.postDelayed(this, if (word) WORD_REPEAT else LETTER_REPEAT)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { lastX = x; lastY = y }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val z = zoneAt(x, y) ?: return true
                if (z.key.main == Fn.TRACKBALL) {
                    trackball = true; trackDeltaX = 0f; trackDeltaY = 0f
                    handler.removeCallbacks(trackTick)
                    return true
                }
                pressedZone = z; pressed = z.key
                if (z.key.main == Fn.BACKSPACE) {
                    repeats = 0
                    host.onBackspace(false)
                    handler.postDelayed(repeat, FIRST_REPEAT)
                }
                if (z.key.extended != null) handler.postDelayed(longPress, HOLD_FOR_EXTENDED)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackball) { trackMove(x - lastX, y - lastY); lastX = x; lastY = y; return true }
                if (extended != null) {
                    val i = extendedAt(x, y)
                    if (i != extendedIndex) { extendedIndex = i; invalidate() }
                    return true
                }
                // Sliding off a key onto another one moves the press, as webOS allowed.
                val z = zoneAt(x, y)
                if (z !== pressedZone) {
                    handler.removeCallbacks(longPress); handler.removeCallbacks(repeat)
                    pressedZone = z; pressed = z?.key
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress); handler.removeCallbacks(repeat)
                if (trackball) {
                    trackball = false
                    handler.postDelayed(trackTick, TRACK_TICK)
                    return true
                }
                if (e.actionMasked == MotionEvent.ACTION_UP) commit()
                hideExtended()
                pressedZone = null; pressed = null
                invalidate()
            }
        }
        lastX = x; lastY = y
        return true
    }

    private var lastX = 0f
    private var lastY = 0f

    private fun commit() {
        val chars = extended
        if (chars != null) {
            host.onText(chars[extendedIndex].toString())
            if (shift == Shift.ONCE) shift = Shift.OFF
            return
        }
        val key = pressedZone?.key ?: return
        when (key.main) {
            Fn.SHIFT -> {
                val now = System.currentTimeMillis()
                shift = when {
                    now - lastShiftTap < DOUBLE_TAP -> Shift.LOCK
                    shift == Shift.OFF -> Shift.ONCE
                    else -> Shift.OFF
                }
                lastShiftTap = now
                return
            }
            Fn.SYMBOL -> { symbols = !symbols; return }
            Fn.BACKSPACE -> return          // already sent on the way down, and repeated since
            Fn.RETURN -> { host.onEnter(); return }
            Fn.TAB -> { host.onTab(); return }
            Fn.HIDE -> { host.onHide(); return }
            Fn.TRACKBALL, Fn.NONE -> return
        }
        val out = when {
            symbols && key.altText != null -> key.altText
            symbols -> key.alt.toChar().toString()
            shift != Shift.OFF -> key.main.toChar().uppercaseChar().toString()
            else -> key.main.toChar().toString()
        }
        host.onText(out)
        if (shift == Shift.ONCE) shift = Shift.OFF
    }

    /** Called when the field being edited changes, so the keyboard starts from a clean state. */
    fun reset() {
        shift = Shift.OFF; symbols = false
        hideExtended()
        pressed = null; pressedZone = null
        trackball = false; trackVX = 0f; trackVY = 0f
        handler.removeCallbacksAndMessages(null)
        invalidate()
    }

    companion object {
        // webOS's own timings (LunaCE's TabletKeyboard.cpp).
        private const val FIRST_REPEAT = 350L
        private const val LETTER_REPEAT = 120L
        private const val WORD_REPEAT = 275L
        private const val WORD_DELETE_AFTER = 1500L
        private const val DOUBLE_TAP = 500L
        private const val HOLD_FOR_EXTENDED = 400L
        private const val TRACK_TICK = 62L
        private const val TRACK_STEP = 15f

        private const val POPUP_LEFT = 11
        private const val POPUP_RIGHT = 10
        private const val POPUP_TOP_TO_KEY = 10
        private const val POPUP_POINTER_END = 62   // the balloon's pointer ends here; past it it stretches
        private const val POPUP_KEY_WIDTH = 80
        private const val POPUP_SINGLE_LINE_MAX = 5
    }
}
