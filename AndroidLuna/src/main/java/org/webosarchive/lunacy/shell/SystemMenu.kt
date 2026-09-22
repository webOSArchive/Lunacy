package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * The system menu, the status bar's right-hand ▾: LunaSysMgr's SystemMenu.qml, as the
 * reference TouchPad has it (/usr/palm/sysmgr/uiComponents/SystemMenu). So far the first
 * three of its rows - the date, the battery and the brightness slider; Wi-Fi, VPN,
 * Bluetooth, airplane mode, rotation lock and mute come later.
 *
 * Geometry is the QML's: 300 px wide in menu-dropdown-bg.png (borders L30 T10 R30 B30), the
 * content clipped 7 px in at the sides and 14 px up from the bottom, rows 42 px high with
 * menu-divider.png between them, headers 14 px in. It fades in and out over 200 ms.
 */
@SuppressLint("ViewConstructor")
class SystemMenu(context: Context, private val luna: Luna) : View(context) {
    companion object {
        const val WIDTH = 300
        /** SystemMenu.qml's edgeOffset: the menu's right edge is this far past the screen's. */
        const val EDGE_OFFSET = 11
        const val ROW = 42
        const val SIDE = 7
        const val BOTTOM = 14
        const val HEADER_IDENT = 14
        const val DIVIDER_OFFSET = 7
        const val FADE_MS = 200L
        // Slider.qml
        const val RAIL_EDGE = 8
        const val RAIL_BORDER = 11
        const val HANDLE_TOLERANCE = 12
        const val RAIL_STEP = 0.20f
        const val BRIGHTNESS_MARGIN = 5
        const val BRIGHTNESS_SPACING = 5
    }

    /** "Battery: 87%", StatusBar's percentage; set by the shell. */
    var batteryPercent = -1
        set(v) { field = v; invalidate() }
    /** 0…1 along the slider: LunaSysMgr's (brightness − 1) / 99. */
    var brightness = 0.5f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    /** The slider moved; [done] once the finger is up (the QML's `save`). */
    var onBrightness: (value: Float, done: Boolean) -> Unit = { _, _ -> }

    var isOpen = false
        private set

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xAA, 0xAA, 0xAA); textSize = luna.px(18f); typeface = luna.fontMedium
    }

    init { visibility = INVISIBLE; alpha = 0f }

    private fun contentHeight() = 3 * luna.px(ROW) + 2 * (luna.image("menu-divider.png")?.height ?: luna.px(2))

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(luna.px(WIDTH), contentHeight() + luna.px(BOTTOM))

    fun open() {
        isOpen = true
        visibility = VISIBLE
        animate().cancel()
        animate().alpha(1f).setDuration(FADE_MS).setInterpolator(Easing.Linear).start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        animate().cancel()
        animate().alpha(0f).setDuration(FADE_MS).setInterpolator(Easing.Linear)
            .withEndAction { if (!isOpen) visibility = INVISIBLE }.start()
    }

    // ---- drawing ----

    private val side get() = luna.px(SIDE.toFloat())
    private val contentW get() = width - 2 * side
    private fun rowTop(i: Int): Float {
        val divider = (luna.image("menu-divider.png")?.height ?: luna.px(2)).toFloat()
        return i * (luna.px(ROW.toFloat()) + divider)
    }

    override fun onDraw(c: Canvas) {
        luna.nine(c, "menu-dropdown-bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()), 30, 10, 30, 30)
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(java.util.Date())
        drawText(c, date, 0)
        divider(c, rowTop(1) - dividerH())
        drawText(c, "Battery: " + if (batteryPercent in 0..100) "$batteryPercent%" else "Not Available", 1)
        divider(c, rowTop(2) - dividerH())
        drawBrightness(c, rowTop(2))
    }

    private fun dividerH() = (luna.image("menu-divider.png")?.height ?: luna.px(2)).toFloat()

    /** MenuDivider.qml: menu-divider.png across the content less 7 px, centred. */
    private fun divider(c: Canvas, y: Float) {
        val w = contentW - luna.px(DIVIDER_OFFSET.toFloat())
        val x = side + (contentW - w) / 2
        luna.tile(c, "menu-divider.png", RectF(x, y, x + w, y + dividerH()))
    }

    /** A header row: Prelude 18 px #AAA, 14 px in, centred in its 42 px. */
    private fun drawText(c: Canvas, s: String, row: Int) {
        val top = rowTop(row)
        val y = top + luna.px(ROW.toFloat()) / 2 - (textPaint.ascent() + textPaint.descent()) / 2
        c.drawText(s, side + luna.px(HEADER_IDENT.toFloat()), y, textPaint)
    }

    /** BrightnessElement.qml: less and more icons at the ends, the slider between them. */
    private data class SliderGeometry(val x: Float, val w: Float, val cy: Float)
    private fun slider(): SliderGeometry {
        val rowCy = rowTop(2) + luna.px(ROW.toFloat()) / 2
        val contentX = side + luna.px(4f)
        val contentWidth = contentW - luna.px(8f)
        val less = luna.image("statusBar/brightness-less.png")?.width ?: luna.px(24)
        val more = luna.image("statusBar/brightness-more.png")?.width ?: luna.px(24)
        val w = contentWidth - (less + more + 2 * luna.px(BRIGHTNESS_MARGIN) + 2 * luna.px(BRIGHTNESS_SPACING))
        return SliderGeometry(contentX + contentWidth / 2 - w / 2, w, rowCy)
    }

    private fun drawBrightness(c: Canvas, top: Float) {
        val cy = top + luna.px(ROW.toFloat()) / 2
        val contentX = side + luna.px(4f)
        val contentWidth = contentW - luna.px(8f)
        luna.image("statusBar/brightness-less.png")?.let { c.drawBitmap(it, contentX + luna.px(BRIGHTNESS_MARGIN), cy - it.height / 2f, null) }
        luna.image("statusBar/brightness-more.png")?.let { c.drawBitmap(it, contentX + contentWidth - it.width - luna.px(BRIGHTNESS_MARGIN), cy - it.height / 2f, null) }
        val s = slider()
        val track = luna.image("statusBar/slider-track.png")
        val th = (track?.height ?: luna.px(24)).toFloat()
        luna.nine(c, "statusBar/slider-track.png", RectF(s.x, s.cy - th / 2, s.x + s.w, s.cy + th / 2), RAIL_BORDER, 0, RAIL_BORDER, 0)
        val handle = luna.image("statusBar/slider-handle.png")
        val hw = (handle?.width ?: luna.px(30)).toFloat()
        val progressW = Math.max((s.w - hw / 2) * brightness + hw / 2, 2f * luna.px(RAIL_BORDER))
        luna.nine(c, "statusBar/slider-track-progress.png", RectF(s.x, s.cy - th / 2, s.x + progressW, s.cy + th / 2), RAIL_BORDER, 0, RAIL_BORDER, 0)
        handle?.let { c.drawBitmap(it, handleX(s, hw) , s.cy - it.height / 2f, null) }
    }

    private fun handleX(s: SliderGeometry, hw: Float) =
        s.x + luna.px(RAIL_EDGE) + (s.w - 2 * luna.px(RAIL_EDGE)) * brightness - hw / 2

    private fun valueAt(x: Float, s: SliderGeometry) =
        ((x - s.x - luna.px(RAIL_EDGE)) / (s.w - 2 * luna.px(RAIL_EDGE))).coerceIn(0f, 1f)

    // ---- the slider (Slider.qml) ----

    private var onHandle = false
    private var onBar = false
    private var downX = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = slider()
        val hw = (luna.image("statusBar/slider-handle.png")?.width ?: luna.px(30)).toFloat()
        val tol = luna.px(HANDLE_TOLERANCE.toFloat())
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onHandle = false; onBar = false; downX = e.x
                if (Math.abs(e.y - s.cy) <= luna.px(ROW.toFloat()) / 2) {
                    val hx = handleX(s, hw)
                    if (e.x > hx - tol && e.x < hx + hw + tol) onHandle = true
                    else if (e.x >= s.x - 2 * tol && e.x <= s.x + s.w + 2 * tol) onBar = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (onHandle) { brightness = valueAt(e.x, s); onBrightness(brightness, false) }
                // A finger that wanders off the bar isn't a tap on it any more.
                else if (onBar && Math.abs(e.x - downX) > luna.px(20f)) onBar = false
            }
            MotionEvent.ACTION_UP -> {
                if (onHandle) { if (e.x != downX) brightness = valueAt(e.x, s); onBrightness(brightness, true) }
                else if (onBar) {
                    // A tap on the rail moves the handle a fifth of the way towards it.
                    brightness += if (e.x < handleX(s, hw)) -RAIL_STEP else RAIL_STEP
                    onBrightness(brightness, true)
                }
                onHandle = false; onBar = false
            }
            MotionEvent.ACTION_CANCEL -> { onHandle = false; onBar = false }
        }
        return true
    }
}
