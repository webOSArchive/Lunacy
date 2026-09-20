package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Exhibition mode's Time face - webOS's dock-mode clock, and one of the things people
 * remember about a TouchPad on its Touchstone.
 *
 * LunaSysMgr drew this itself, in QML, not the Clock app; the Exhibition app's own list calls
 * it "Time" and can't switch it off. So it belongs in Lunacy's shell, like the status bar and
 * the launcher, and it is drawn from the reference TouchPad's own
 * /usr/palm/sysmgr/uiComponents/DockModeTime QML rather than LunaCE's repo copy - webOS CE
 * put a plain face (SimpleClock) first and kept the three stock ones behind it, so there are
 * four, and dock mode opens on the plain one. Swipe sideways to change face; the dots follow.
 *
 * Sizes are the QML's, in TouchPad px, which is what Luna.px gives.
 */
@SuppressLint("ViewConstructor")
class ExhibitionLayer(context: Context, private val luna: Luna) : View(context) {
    /** The home button, or a tap on the status bar's Time menu, leaves exhibition mode. */
    var onExit: () -> Unit = {}

    private enum class Face { SIMPLE, ANALOG_GLASS, DIGITAL, ANALOG_MATTE }
    private val faces = Face.values()
    /** Which face is showing; fractional while a swipe is in flight. */
    private var position = 0f
    private var anim: android.animation.ValueAnimator? = null

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            // The stock faces polled every 100 ms; CE's note calls that needless on a screen
            // that sits idle for hours. A second is enough for every face, second hand included.
            ticker.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000))
        }
    }

    // SimpleClock: Prelude 178 px over a 47 px date, the column nudged up by 11.
    //
    // The QML asks for font.weight: Font.Light, but the device doesn't draw it light: the
    // family it names ("prelude") has no Light face registered under it, so Qt falls back to
    // the regular weight. Measured on the reference TouchPad's own screenshot, its glyphs
    // carry about twice the ink of a Light face at the same size. So this is the regular one
    // - what the device shows, not what the QML asks for.
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = luna.fontMedium; textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xb4, 0xb4, 0xb4); typeface = luna.font("Prelude-Medium.ttf"); textAlign = Paint.Align.CENTER
    }
    // The analog and flipper faces label themselves in Prelude at #e1e1e1.
    private val faceText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xe1, 0xe1, 0xe1); typeface = luna.font("Prelude-Medium.ttf"); textAlign = Paint.Align.CENTER
    }

    private val landscape get() = width >= height

    /**
     * The date the way the QML asks for it: Qt.DefaultLocaleLongDate, which carries the
     * weekday ("Sunday, September 20, 2026"). Android's "long" format drops it, so this is
     * the FULL one - the same thing in Java's names, and still the locale's own wording.
     */
    private fun longDate(date: Date): String =
        java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, resources.configuration.locale).format(date)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.post(tick)
    }

    override fun onDetachedFromWindow() {
        ticker.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        // Rectangle { color: "black" } under the background, which CE added because the stock
        // file relied on the image covering the window exactly and flashed white where it didn't.
        c.drawColor(Color.BLACK)
        luna.image("dockmode/time/clock_bg.png")?.let { bg ->
            // PreserveAspectCrop: cover, don't letterbox.
            val scale = maxOf(width.toFloat() / bg.width, height.toFloat() / bg.height)
            val w = bg.width * scale; val h = bg.height * scale
            c.drawBitmap(bg, null, RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), null)
        }
        // The faces sit side by side and slide with the swipe, as the QML's ListView does.
        for (i in faces.indices) {
            val dx = (i - position) * width
            if (abs(dx) >= width) continue
            c.save(); c.translate(dx, 0f)
            drawFace(c, faces[i])
            c.restore()
        }
        drawDots(c)
    }

    private fun drawFace(c: Canvas, face: Face) = when (face) {
        Face.SIMPLE -> drawSimple(c)
        Face.ANALOG_GLASS -> drawAnalog(c, glass = true)
        Face.ANALOG_MATTE -> drawAnalog(c, glass = false)
        Face.DIGITAL -> drawDigital(c)
    }

    // ---- CE's plain face, which dock mode opens on ----

    private fun drawSimple(c: Canvas) {
        val now = Date()
        val twelve = !DateFormat.is24HourFormat(context)
        timePaint.textSize = luna.px(if (landscape) 178f else 135f)
        datePaint.textSize = luna.px(if (landscape) 47f else 37f)
        val time = DateFormat.format(if (twelve) "h:mm a" else "H:mm", now).toString()
        val date = longDate(now)
        val spacing = luna.px(if (landscape) 2f else 0f)
        // A QML Column centres the whole block; its height is both lines plus the spacing.
        val timeH = -timePaint.ascent() + timePaint.descent()
        val dateH = -datePaint.ascent() + datePaint.descent()
        val top = (height - (timeH + spacing + dateH)) / 2 - luna.px(11f)
        c.drawText(time, width / 2f, top - timePaint.ascent(), timePaint)
        c.drawText(date, width / 2f, top + timeH + spacing - datePaint.ascent(), datePaint)
    }

    // ---- the three stock faces ----

    /**
     * base.png with its hands rotated about the centre: hours 30 deg an hour plus half a
     * degree a minute, minutes 6 deg a minute, seconds 6 deg a second. The glass face has no
     * second hand and carries the long date low; the matte one has the day either side.
     */
    private fun drawAnalog(c: Canvas, glass: Boolean) {
        val kind = if (glass) "glass" else "matte"
        val base = luna.image("dockmode/time/analog/$kind/base.png") ?: return
        val cx = width / 2f; val cy = height / 2f
        c.drawBitmap(base, cx - base.width / 2f, cy - base.height / 2f, null)

        val now = Calendar.getInstance()
        val hours = now.get(Calendar.HOUR_OF_DAY); val minutes = now.get(Calendar.MINUTE)
        hand(c, "dockmode/time/analog/$kind/hour.png", cx, cy, hours * 30f + minutes * 0.5f)
        hand(c, "dockmode/time/analog/$kind/minute.png", cx, cy, minutes * 6f)
        if (!glass) hand(c, "dockmode/time/analog/$kind/second.png", cx, cy, now.get(Calendar.SECOND) * 6f)

        faceText.textSize = luna.px(if (glass) 30f else 30f)
        if (glass) {
            c.drawText(longDate(now.time), cx, cy + luna.px(300f), faceText)
        } else {
            c.drawText(now.get(Calendar.DAY_OF_MONTH).toString(), cx + luna.px(108f), cy - luna.px(2f) - (faceText.ascent() + faceText.descent()) / 2, faceText)
            c.drawText(DateFormat.format("EEE", now.time).toString(), cx - luna.px(108f), cy - luna.px(2f) - (faceText.ascent() + faceText.descent()) / 2, faceText)
        }
    }

    private fun hand(c: Canvas, path: String, cx: Float, cy: Float, angle: Float) {
        val bmp = luna.image(path) ?: return
        c.save()
        c.rotate(angle, cx, cy)
        c.drawBitmap(bmp, cx - bmp.width / 2f, cy - bmp.height / 2f, null)
        c.restore()
    }

    /**
     * The flipper face: four digit tiles either side of the dots for the time, eleven smaller
     * ones for the date, each with its mask drawn over the top, and the digits in Prelude.
     */
    private fun drawDigital(c: Canvas) {
        val dir = if (landscape) "landscape" else "portrait"
        val tile = luna.image("dockmode/time/digital/$dir/flippers-time.png") ?: return
        val tileMask = luna.image("dockmode/time/digital/$dir/flippers-time-mask.png")
        val dots = luna.image("dockmode/time/digital/$dir/dots.png") ?: return
        val dateTile = luna.image("dockmode/time/digital/$dir/flippers-date.png") ?: return
        val dateMask = luna.image("dockmode/time/digital/$dir/flippers-date-mask.png")
        val cx = width / 2f; val cy = height / 2f
        val now = Calendar.getInstance()
        val twelve = !DateFormat.is24HourFormat(context)
        var hours = now.get(Calendar.HOUR_OF_DAY)
        if (twelve) hours = if (hours % 12 == 0) 12 else hours % 12
        val minutes = now.get(Calendar.MINUTE)

        // Row 1: tile, 4, tile, 22, dots, 22, tile, 4, tile - centred, 48 px above the middle.
        val gapSmall = luna.px(4f); val gapBig = luna.px(22f)
        val timeW = tile.width * 4 + gapSmall * 2 + gapBig * 2 + dots.width
        var x = cx - timeW / 2
        val timeTop = cy - luna.px(48f) - tile.height / 2f
        val digits = intArrayOf(hours / 10, hours % 10, -1, minutes / 10, minutes % 10)
        faceText.textSize = luna.px(if (landscape) 158f else 132f)
        for ((i, d) in digits.withIndex()) {
            if (d < 0) {
                c.drawBitmap(dots, x, timeTop, null); x += dots.width + gapBig
                continue
            }
            c.drawBitmap(tile, x, timeTop, null)
            c.drawText(d.toString(), x + tile.width / 2f,
                timeTop + tile.height / 2f - (faceText.ascent() + faceText.descent()) / 2 - luna.px(4f), faceText)
            tileMask?.let { c.drawBitmap(it, x, timeTop, null) }
            // "12" then a wide gap before the minutes, 4 px between a pair's two digits.
            x += tile.width + if (i == 1) gapBig else gapSmall
        }
        if (twelve) {
            val ampm = DateFormat.format("a", now.time).toString()
            val ap = Paint(faceText).apply { textSize = luna.px(if (landscape) 20f else 15f) }
            c.drawText(ampm, cx - timeW / 2 + tile.width / 2f - luna.px(42f),
                timeTop + tile.height / 2f - luna.px(95f), ap)
        }

        // Row 2: month, blank, day, blank, year - eleven tiles, 2 px apart, 136 px below.
        val month = DateFormat.format("MMM", now.time).toString().uppercase()
        val day = now.get(Calendar.DAY_OF_MONTH); val year = now.get(Calendar.YEAR)
        val cells = listOf(
            month.getOrNull(0)?.toString(), month.getOrNull(1)?.toString(), month.getOrNull(2)?.toString(), null,
            (day / 10).toString(), (day % 10).toString(), null,
            (year / 1000).toString(), (year / 100 % 10).toString(), (year / 10 % 10).toString(), (year % 10).toString())
        val gap = luna.px(2f)
        val dateW = dateTile.width * cells.size + gap * (cells.size - 1)
        var dx = cx - dateW / 2
        val dateTop = cy + luna.px(136f) - dateTile.height / 2f
        faceText.textSize = luna.px(if (landscape) 52f else 44f)
        for (cell in cells) {
            c.drawBitmap(dateTile, dx, dateTop, null)
            cell?.let {
                c.drawText(it, dx + dateTile.width / 2f,
                    dateTop + dateTile.height / 2f - (faceText.ascent() + faceText.descent()) / 2 - luna.px(4f), faceText)
            }
            dateMask?.let { c.drawBitmap(it, dx, dateTop, null) }
            dx += dateTile.width + gap
        }
    }

    /** One dot per face, 10 px apart, below the middle (CE drives them off the model). */
    private fun drawDots(c: Canvas) {
        val on = luna.image("dockmode/time/indicator/on.png") ?: return
        val off = luna.image("dockmode/time/indicator/off.png") ?: return
        val spacing = luna.px(10f)
        val total = faces.size * on.width + (faces.size - 1) * spacing
        var x = width / 2f - total / 2
        val y = height / 2f + luna.px(if (landscape) 340f else 400f) - on.height / 2f
        val current = position.roundToInt()
        for (i in faces.indices) {
            c.drawBitmap(if (i == current) on else off, x, y, null)
            x += on.width + spacing
        }
    }

    // ---- swiping between faces ----

    private var downX = 0f
    private var startPosition = 0f
    private var dragging = false
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val flingMin = android.view.ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4
    private var velocity: android.view.VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        velocity?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel(); downX = e.x; startPosition = position; dragging = false
                velocity?.recycle()
                velocity = android.view.VelocityTracker.obtain()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(e.x - downX) > slop) dragging = true
                if (dragging) {
                    position = (startPosition - (e.x - downX) / width).coerceIn(-0.4f, faces.size - 0.6f)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.computeCurrentVelocity(1000)
                val vx = velocity?.xVelocity ?: 0f
                velocity?.recycle(); velocity = null
                if (dragging) {
                    // A flick turns the page even when the finger didn't travel half the
                    // screen, as the QML ListView's SnapOneItem does.
                    val target = if (abs(vx) > flingMin) startPosition.roundToInt() + (if (vx < 0) 1 else -1)
                    else position.roundToInt()
                    settle(target.coerceIn(0, faces.size - 1))
                }
                dragging = false
            }
        }
        return true
    }

    private fun settle(target: Int) {
        anim?.cancel()
        anim = android.animation.ValueAnimator.ofFloat(position, target.toFloat()).apply {
            duration = 250; interpolator = Easing.OutCubic
            addUpdateListener { position = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Dock mode always opens on the first face, as it does on a device. */
    fun reset() {
        anim?.cancel()
        position = 0f
        invalidate()
    }
}
