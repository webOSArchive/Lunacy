package org.webosarchive.lunacy.shell

import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller

/**
 * The system menu, the status bar's right-hand ▾: LunaSysMgr's SystemMenu.qml as the
 * reference TouchPad has it (/usr/palm/sysmgr/uiComponents/SystemMenu). Top to bottom: the
 * date, the battery, the brightness slider, the Wi-Fi and Bluetooth drawers, then Airplane
 * Mode, Rotation Lock and Mute. VPN's drawer is left out, as webOS left it out on a device
 * without VPN: Android keeps its VPNs from apps (Docs/luna-deltas.md B3).
 *
 * Geometry is the QML's: 300 px wide in menu-dropdown-bg.png (borders L30 T10 R30 B30), the
 * content clipped 7 px in at the sides and 14 px up from the bottom, rows 42 px high with
 * menu-divider.png between them, headers 14 px in and a drawer's rows 16 more. A drawer opens
 * over 350 ms OutCubic. The content flicks when it is taller than the menu may be (410 px, or
 * the room under the bar), with the scroll-fade masks and arrows at whichever end has more.
 * The menu fades in and out over 200 ms. What each row does is [SystemControls]'.
 */
@SuppressLint("ViewConstructor")
class SystemMenu(context: Context, private val luna: Luna, private val controls: SystemControls) : View(context) {
    companion object {
        const val WIDTH = 300
        const val MAX_HEIGHT = 410
        /** SystemMenu.qml's edgeOffset: the menu's right edge is this far past the screen's. */
        const val EDGE_OFFSET = 11
        const val ROW = 42
        const val SIDE = 7
        const val BOTTOM = 14
        const val HEADER_IDENT = 14
        const val SUB_IDENT = 16
        const val DIVIDER_OFFSET = 7
        const val FADE_MS = 200L
        // the toggles' icons (AirplaneModeElement.qml and its siblings)
        const val ICON_SPACING = 4
        const val RIGHT_MARGIN = 8
        // Drawer.qml
        const val DRAWER_MS = 350L
        const val DRAWER_CHANGE_MS = 200L
        const val MASK_MS = 70L
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
    /** SystemMenu.qml's closeMenuTimer: the shell closes the menu after [delayMs]. */
    var onCloseRequest: (delayMs: Long) -> Unit = {}
    /** The most the menu may be tall, px: the room under the bar (slotPositiveSpaceChangeFinished). */
    var maxHeightPx = 0
        set(v) { field = v; requestLayout() }

    var isOpen = false
        private set

    private val textPaint = paint(18f, Color.WHITE)
    private val greyPaint = paint(18f, Color.rgb(0xAA, 0xAA, 0xAA))
    private val entryPaint = paint(16f, Color.WHITE)
    private val statusPaint = paint(10f, Color.rgb(0xAA, 0xAA, 0xAA))
    private val headerStatePaint = paint(13f, Color.rgb(0xAA, 0xAA, 0xAA))
    private fun paint(size: Float, colour: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour; textSize = luna.px(size); typeface = luna.fontMedium
    }

    init { visibility = INVISIBLE; alpha = 0f }

    override fun onMeasure(w: Int, h: Int) {
        val cap = if (maxHeightPx > 0) Math.min(maxHeightPx, luna.px(MAX_HEIGHT)) else luna.px(MAX_HEIGHT)
        setMeasuredDimension(luna.px(WIDTH), cap)
    }

    fun open() {
        isOpen = true
        visibility = VISIBLE
        frozen = false
        snapshotToggles()
        scrollY0 = 0f; scroller.forceFinished(true)
        animate().cancel()
        animate().alpha(1f).setDuration(FADE_MS).setInterpolator(Easing.Linear).start()
        invalidate()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        pressed = null
        animate().cancel()
        animate().alpha(0f).setDuration(FADE_MS).setInterpolator(Easing.Linear)
            .withEndAction {
                if (isOpen) return@withEndAction
                visibility = INVISIBLE
                // SystemMenu.qml: drawers close, and a toggle's new text shows, once it's gone.
                drawers.values.forEach { it.snap(false) }
                controls.drawerWifiOpen = false; controls.stopScanning()
                frozen = false; snapshotToggles()
            }.start()
    }

    /** Something [controls] shows changed. */
    fun refresh() {
        if (!frozen) snapshotToggles()
        invalidate()
    }

    // ---- the toggles' text, held while the menu is up after a tap (delayUpdate) ----

    private var frozen = false
    private var shownRotationLocked = false
    private var shownMuted = false
    private fun snapshotToggles() { shownRotationLocked = controls.rotationLocked; shownMuted = controls.muted }

    // ---- the rows ----

    private sealed class Row {
        object Date : Row()
        object Battery : Row()
        object Brightness : Row()
        object Divider : Row()
        data class Header(val drawer: String) : Row()
        data class Action(val key: String, val text: String, val icon: String?, val last: Boolean = false, val sub: Boolean = false, val enabled: Boolean = true) : Row()
        data class Net(val n: SystemControls.Network) : Row()
        data class Dev(val d: SystemControls.Device) : Row()
    }

    private fun rowHeight(r: Row) = if (r is Row.Divider) dividerH() else luna.px(ROW.toFloat())
    private fun selectable(r: Row) = when (r) {
        is Row.Header, is Row.Net, is Row.Dev -> true
        is Row.Action -> r.enabled
        else -> false
    }

    /** One block of the column: plain rows, or a drawer (its header, then its clipped body). */
    private class Block(val rows: List<Row>, val drawer: Drawer? = null, val body: List<Row> = emptyList())

    private fun blocks(): List<Block> {
        val out = ArrayList<Block>()
        out += Block(listOf(Row.Date, Row.Divider, Row.Battery, Row.Divider, Row.Brightness, Row.Divider))
        if (controls.wifiAvailable) {
            val body = ArrayList<Row>()
            body += Row.Divider
            body += Row.Action("wifiOnOff", if (controls.wifiOn) "Turn off WiFi" else "Turn on WiFi", null, sub = true)
            body += Row.Divider
            controls.networks.forEach { body += Row.Net(it); body += Row.Divider }
            body += Row.Action("wifiPrefs", "Wi-Fi Preferences", null, sub = true)
            out += Block(listOf(Row.Header("wifi")), drawer("wifi"), body)
            out += Block(listOf(Row.Divider))
        }
        if (controls.bluetoothAvailable) {
            val body = ArrayList<Row>()
            body += Row.Divider
            body += Row.Action("btOnOff", when {
                controls.bluetoothTurningOn -> "Turning on Bluetooth..."
                controls.bluetoothOn -> "Turn off Bluetooth"
                else -> "Turn on Bluetooth"
            }, null, sub = true)
            body += Row.Divider
            controls.devices.forEach { body += Row.Dev(it); body += Row.Divider }
            body += Row.Action("btPrefs", "Bluetooth Preferences", null, sub = true)
            out += Block(listOf(Row.Header("bluetooth")), drawer("bluetooth"), body)
            out += Block(listOf(Row.Divider))
        }
        val air = controls.airplaneOn
        out += Block(listOf(
            Row.Action("airplane", if (air) "Turn off Airplane Mode" else "Turn on Airplane Mode",
                if (air) "statusBar/icon-airplane-off.png" else "statusBar/icon-airplane.png"),
            Row.Divider,
            Row.Action("rotation", if (shownRotationLocked) "Turn off Rotation Lock" else "Turn on Rotation Lock",
                if (shownRotationLocked) "statusBar/icon-rotation-lock-off.png" else "statusBar/icon-rotation-lock.png"),
            Row.Divider,
            Row.Action("mute", if (shownMuted) "Unmute Sound" else "Mute Sound",
                if (shownMuted) "statusBar/icon-mute-off.png" else "statusBar/icon-mute.png", last = true),
        ))
        return out
    }

    /** A row where it lands: y in the content, and the clip it is drawn and touched within. */
    private class Placed(val row: Row, val y: Float, val h: Float, val clipTop: Float, val clipBottom: Float)

    private var placedCache: List<Placed> = emptyList()
    private var contentH = 0f

    private fun place(): List<Placed> {
        val out = ArrayList<Placed>()
        var y = 0f
        for (b in blocks()) {
            for (r in b.rows) { val h = rowHeight(r); out += Placed(r, y, h, y, y + h); y += h }
            val d = b.drawer ?: continue
            var full = 0f
            b.body.forEach { full += rowHeight(it) }
            d.resize(full)
            val shown = d.height()
            var by = y
            for (r in b.body) { val h = rowHeight(r); out += Placed(r, by, h, y, y + shown); by += h }
            y += shown
        }
        contentH = y
        placedCache = out
        return out
    }

    // ---- drawers (Drawer.qml) ----

    private val drawers = HashMap<String, Drawer>()
    private fun drawer(name: String) = drawers.getOrPut(name) { Drawer() }

    /** A drawer's body height: 0 closed, its rows' height open, animated between. */
    private inner class Drawer {
        var open = false
        var full = 0f
        private var from = 0f
        private var to = 0f
        private var start = 0L
        private var duration = 0L
        private var interp: TimeInterpolator = Easing.Linear

        fun height(): Float {
            val target = if (open) full else 0f
            if (duration == 0L) return target
            val t = (SystemClock.uptimeMillis() - start).toFloat() / duration
            if (t >= 1f) { duration = 0L; return target }
            postInvalidateOnAnimation()
            return from + (to - from) * interp.getInterpolation(t)
        }

        fun toggle(o: Boolean) {
            if (o == open) return
            val now = height()
            open = o
            from = now; to = if (o) full else 0f; start = SystemClock.uptimeMillis(); duration = DRAWER_MS; interp = Easing.OutCubic
            invalidate()
        }

        /** Its rows changed while open: Behavior on height, 200 ms from where it was. */
        fun resize(newFull: Float) {
            if (newFull == full) return
            if (open && duration == 0L && full > 0f) {
                from = full; to = newFull; start = SystemClock.uptimeMillis(); duration = DRAWER_CHANGE_MS; interp = Easing.Linear
            } else if (open && duration != 0L) to = newFull
            full = newFull
        }

        fun snap(o: Boolean) { open = o; duration = 0L }
    }

    // ---- drawing ----

    private val side get() = luna.px(SIDE.toFloat())
    private val contentW get() = width - 2 * side
    private fun dividerH() = (luna.image("menu-divider.png")?.height ?: luna.px(2)).toFloat()
    private val viewportH get() = Math.min(height - luna.px(BOTTOM.toFloat()), contentH)
    private val maxScroll get() = Math.max(0f, contentH - (height - luna.px(BOTTOM.toFloat())))

    private var scrollY0 = 0f

    override fun onDraw(c: Canvas) {
        if (scroller.computeScrollOffset()) { scrollY0 = scroller.currY.toFloat(); postInvalidateOnAnimation() }
        val placed = place()
        scrollY0 = scrollY0.coerceIn(0f, maxScroll)
        val bgH = Math.min(height.toFloat(), contentH + luna.px(BOTTOM.toFloat()))
        luna.nine(c, "menu-dropdown-bg.png", RectF(0f, 0f, width.toFloat(), bgH), 30, 10, 30, 30)
        c.save()
        c.clipRect(side, 0f, side + contentW, viewportH)
        c.translate(side, -scrollY0)
        for (p in placed) {
            if (p.clipBottom <= p.clipTop || p.y >= p.clipBottom || p.y + p.h <= p.clipTop) continue
            if (p.y + p.h < scrollY0 || p.y > scrollY0 + viewportH) continue
            c.save()
            c.clipRect(0f, p.clipTop, contentW, p.clipBottom)
            drawRow(c, p)
            c.restore()
        }
        c.restore()
        drawMasks(c)
        if (spinnerOn()) postInvalidateOnAnimation()
    }

    private fun drawRow(c: Canvas, p: Placed) {
        val r = p.row
        val cy = p.y + p.h / 2
        if (r is Row.Divider) { divider(c, p.y); return }
        val highlighted = (pressed === r && selectable(r)) || (r is Row.Net && r.n.connStatus == "connecting")
        if (highlighted) highlight(c, p.y, r is Row.Action && r.last)
        val ident = luna.px(HEADER_IDENT.toFloat())
        val subIdent = ident + luna.px(SUB_IDENT.toFloat())
        when (r) {
            Row.Date -> text(c, java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(java.util.Date()), ident, cy, greyPaint)
            Row.Battery -> text(c, "Battery: " + if (batteryPercent in 0..100) "$batteryPercent%" else "Not Available", ident, cy, greyPaint)
            Row.Brightness -> drawBrightness(c, p.y)
            is Row.Header -> drawHeader(c, r, cy)
            is Row.Action -> {
                text(c, r.text, if (r.sub) subIdent else ident, cy, if (r.enabled) textPaint else greyPaint)
                r.icon?.let { icon(c, it, contentW - luna.px((ICON_SPACING + RIGHT_MARGIN).toFloat()), cy) }
            }
            is Row.Net -> drawNetwork(c, r.n, subIdent, cy)
            is Row.Dev -> drawDevice(c, r.d, subIdent, cy)
            Row.Divider -> {}
        }
    }

    /** An image with its right edge at [right], centred on [cy]. Returns its left edge. */
    private fun icon(c: Canvas, path: String, right: Float, cy: Float): Float {
        val b = luna.image(path) ?: return right
        c.drawBitmap(b, right - b.width, cy - b.height / 2f, null)
        return right - b.width
    }

    private fun text(c: Canvas, s: String, x: Float, cy: Float, p: Paint) =
        c.drawText(s, x, cy - (p.ascent() + p.descent()) / 2, p)

    /** MenuDivider.qml: menu-divider.png across the content less 7 px, centred. */
    private fun divider(c: Canvas, y: Float) {
        val w = contentW - luna.px(DIVIDER_OFFSET.toFloat())
        val x = (contentW - w) / 2
        luna.tile(c, "menu-divider.png", RectF(x, y, x + w, y + dividerH()))
    }

    /** MenuListEntry.qml's highlight: the row less 8 px, centred, borders 19. */
    private fun highlight(c: Canvas, y: Float, last: Boolean) {
        val inset = luna.px(4f)
        luna.nine(c, if (last) "menu-selection-gradient-last.png" else "menu-selection-gradient-default.png",
            RectF(inset, y, contentW - inset, y + luna.px(ROW.toFloat())), 19, 0, 19, 0)
    }

    /** WiFiElement / BluetoothElement's header: the title, its spinner, the state on the right. */
    private fun drawHeader(c: Canvas, r: Row.Header, cy: Float) {
        val ident = luna.px(HEADER_IDENT.toFloat())
        val wifi = r.drawer == "wifi"
        val title = if (wifi) "Wi-Fi" else "Bluetooth"
        text(c, title, ident, cy, textPaint)
        val titleW = textPaint.measureText(title)
        if (wifi && spinnerOn()) spinner(c, titleW + luna.px(18f), cy)
        val state = (if (wifi) controls.wifiHeaderText() else controls.bluetoothHeaderText()).uppercase()
        val room = contentW - titleW - luna.px(60f)
        val shown = TextUtils.ellipsize(state, headerStatePaint, room, TextUtils.TruncateAt.END).toString()
        val right = contentW - luna.px(14f)
        text(c, shown, right - headerStatePaint.measureText(shown), cy, headerStatePaint)
    }

    private fun spinnerOn() = isOpen && drawers["wifi"]?.open == true && controls.wifiOn && controls.scanning

    /** AnimatedSpinner: spinner.png turned in 60 steps a second, its top 17 px above the row's middle. */
    private val spinMatrix = Matrix()
    private fun spinner(c: Canvas, x: Float, cy: Float) {
        val b = luna.image("spinner.png") ?: return
        val frame = (SystemClock.uptimeMillis() % 1000) * 60 / 1000
        val top = cy - luna.px(17f)
        spinMatrix.reset()
        spinMatrix.postRotate(frame * 6f, b.width / 2f, b.width / 2f)
        spinMatrix.postTranslate(x, top)
        c.drawBitmap(b, spinMatrix, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** WifiEntry.qml: the name, a status under it, and from the right the bars, a lock, a check. */
    private fun drawNetwork(c: Canvas, n: SystemControls.Network, x: Float, cy: Float) {
        val sp = luna.px(ICON_SPACING.toFloat())
        val bars = luna.image("statusBar/wifi-${n.bars}.png")
        val lock = luna.image("statusBar/system-menu-lock.png")
        val check = luna.image("statusBar/system-menu-popup-item-checkmark.png")
        val sigX = icon(c, "statusBar/wifi-${n.bars}.png", contentW - sp - luna.px(3f), cy)
        val lockX = sigX - sp - (lock?.width ?: 0)
        if (n.secured) icon(c, "statusBar/system-menu-lock.png", sigX - sp, cy)
        if (n.connected) icon(c, "statusBar/system-menu-popup-item-checkmark.png", lockX - sp, cy)
        val nameW = (contentW - x) - (bars?.width ?: 0) - (check?.width ?: 0) - (lock?.width ?: 0) - luna.px(3f) - 3 * sp - luna.px(5f)
        entry(c, n.ssid, statusText(n.connStatus), x, cy, nameW)
    }

    private fun statusText(s: String) = when (s) {
        "connecting", "associated", "associating" -> "Connecting..."
        "ipFailed" -> "IP configuration failed"
        "associationFailed" -> "Association failed"
        else -> ""
    }

    /** BluetoothEntry.qml: the name, and a check at the right when it is connected. */
    private fun drawDevice(c: Canvas, d: SystemControls.Device, x: Float, cy: Float) {
        val check = luna.image("statusBar/system-menu-popup-item-checkmark.png")
        if (d.connected) icon(c, "statusBar/system-menu-popup-item-checkmark.png", contentW - luna.px((ICON_SPACING + RIGHT_MARGIN).toFloat()), cy)
        val nameW = (contentW - x) - (check?.width ?: 0) - luna.px((RIGHT_MARGIN + ICON_SPACING + 5).toFloat())
        entry(c, d.name, "", x, cy, nameW)
    }

    /** The 16 px name centred on the row, a 10 px upper-case status a pixel under its baseline. */
    private fun entry(c: Canvas, name: String, status: String, x: Float, cy: Float, w: Float) {
        val shown = TextUtils.ellipsize(name, entryPaint, Math.max(0f, w), TextUtils.TruncateAt.END).toString()
        val baseline = cy - (entryPaint.ascent() + entryPaint.descent()) / 2
        c.drawText(shown, x, baseline, entryPaint)
        if (status.isNotEmpty()) c.drawText(status.uppercase(), x, baseline + luna.px(1f) - statusPaint.ascent(), statusPaint)
    }

    // ---- the scroll masks (SystemMenu.qml maskTop / maskBottom) ----

    private var topMask = 0f
    private var bottomMask = 0f
    private var maskTime = 0L

    private fun drawMasks(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val step = if (maskTime == 0L) 1f else (now - maskTime).toFloat() / MASK_MS
        maskTime = now
        val wantTop = if (scrollY0 > 0.5f) 1f else 0f
        val wantBottom = if (scrollY0 < maxScroll - 0.5f) 1f else 0f
        topMask = approach(topMask, wantTop, step)
        bottomMask = approach(bottomMask, wantBottom, step)
        if (topMask != wantTop || bottomMask != wantBottom) postInvalidateOnAnimation() else maskTime = 0L
        val w = width - luna.px(22f)
        val x = (width - w) / 2f
        if (topMask > 0f) {
            val p = Paint().apply { alpha = (255 * topMask).toInt() }
            val img = luna.image("menu-dropdown-scrollfade-top.png")
            luna.nine(c, "menu-dropdown-scrollfade-top.png", RectF(x, 0f, x + w, (img?.height ?: 0).toFloat()), 20, 0, 20, 0, p)
            luna.image("menu-arrow-up.png")?.let { c.drawBitmap(it, (width - it.width) / 2f, 0f, p) }
        }
        if (bottomMask > 0f) {
            val p = Paint().apply { alpha = (255 * bottomMask).toInt() }
            val img = luna.image("menu-dropdown-scrollfade-bottom.png")
            val y = viewportH - luna.px(29f)
            luna.nine(c, "menu-dropdown-scrollfade-bottom.png", RectF(x, y, x + w, y + (img?.height ?: 0)), 20, 0, 20, 0, p)
            luna.image("menu-arrow-down.png")?.let { c.drawBitmap(it, (width - it.width) / 2f, y + luna.px(10f), p) }
        }
    }

    private fun approach(v: Float, to: Float, step: Float) =
        if (v < to) Math.min(to, v + step) else Math.max(to, v - step)

    // ---- the brightness row (BrightnessElement.qml, Slider.qml) ----

    private data class SliderGeometry(val x: Float, val w: Float, val cy: Float)

    /** In view coordinates, where the brightness row is now. */
    private fun slider(): SliderGeometry? {
        val p = placedCache.firstOrNull { it.row === Row.Brightness } ?: return null
        val g = sliderIn(p.y)
        return SliderGeometry(g.x + side, g.w, g.cy - scrollY0)
    }

    private fun sliderIn(top: Float): SliderGeometry {
        val rowCy = top + luna.px(ROW.toFloat()) / 2
        val contentX = luna.px(4f)
        val contentWidth = contentW - luna.px(8f)
        val less = luna.image("statusBar/brightness-less.png")?.width ?: luna.px(24)
        val more = luna.image("statusBar/brightness-more.png")?.width ?: luna.px(24)
        val w = contentWidth - (less + more + 2 * luna.px(BRIGHTNESS_MARGIN) + 2 * luna.px(BRIGHTNESS_SPACING))
        return SliderGeometry(contentX + contentWidth / 2 - w / 2, w, rowCy)
    }

    private fun drawBrightness(c: Canvas, top: Float) {
        val cy = top + luna.px(ROW.toFloat()) / 2
        val contentX = luna.px(4f)
        val contentWidth = contentW - luna.px(8f)
        luna.image("statusBar/brightness-less.png")?.let { c.drawBitmap(it, contentX + luna.px(BRIGHTNESS_MARGIN), cy - it.height / 2f, null) }
        luna.image("statusBar/brightness-more.png")?.let { c.drawBitmap(it, contentX + contentWidth - it.width - luna.px(BRIGHTNESS_MARGIN), cy - it.height / 2f, null) }
        val s = sliderIn(top)
        val track = luna.image("statusBar/slider-track.png")
        val th = (track?.height ?: luna.px(24)).toFloat()
        luna.nine(c, "statusBar/slider-track.png", RectF(s.x, s.cy - th / 2, s.x + s.w, s.cy + th / 2), RAIL_BORDER, 0, RAIL_BORDER, 0)
        val handle = luna.image("statusBar/slider-handle.png")
        val hw = (handle?.width ?: luna.px(30)).toFloat()
        val progressW = Math.max((s.w - hw / 2) * brightness + hw / 2, 2f * luna.px(RAIL_BORDER))
        luna.nine(c, "statusBar/slider-track-progress.png", RectF(s.x, s.cy - th / 2, s.x + progressW, s.cy + th / 2), RAIL_BORDER, 0, RAIL_BORDER, 0)
        handle?.let { c.drawBitmap(it, handleX(s, hw), s.cy - it.height / 2f, null) }
    }

    private fun handleX(s: SliderGeometry, hw: Float) =
        s.x + luna.px(RAIL_EDGE) + (s.w - 2 * luna.px(RAIL_EDGE)) * brightness - hw / 2

    private fun valueAt(x: Float, s: SliderGeometry) =
        ((x - s.x - luna.px(RAIL_EDGE)) / (s.w - 2 * luna.px(RAIL_EDGE))).coerceIn(0f, 1f)

    // ---- touch: rows (MenuListEntry), the slider, and the Flickable ----

    private var pressed: Row? = null
    private var pressedPlaced: Placed? = null
    private var onHandle = false
    private var onBar = false
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var flicking = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null

    private fun hit(x: Float, y: Float): Placed? {
        val cy = y + scrollY0
        val cx = x - side
        if (cx < 0 || cx > contentW || y > viewportH) return null
        return placedCache.firstOrNull { cy >= it.y && cy < it.y + it.h && cy >= it.clipTop && cy < it.clipBottom }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = slider()
        val hw = (luna.image("statusBar/slider-handle.png")?.width ?: luna.px(30)).toFloat()
        val tol = luna.px(HANDLE_TOLERANCE.toFloat())
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Below the menu's background the touch is the scrim's: it closes the menu.
                if (e.y > Math.min(height.toFloat(), contentH + luna.px(BOTTOM.toFloat()))) return false
                scroller.forceFinished(true)
                velocity?.recycle(); velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                onHandle = false; onBar = false; flicking = false; downX = e.x; downY = e.y; lastY = e.y
                val p = hit(e.x, e.y)
                pressedPlaced = p
                pressed = p?.row?.takeIf { selectable(it) }
                if (p?.row === Row.Brightness && s != null) {
                    val hx = handleX(s, hw)
                    if (e.x > hx - tol && e.x < hx + hw + tol) onHandle = true
                    else if (e.x >= s.x - 2 * tol && e.x <= s.x + s.w + 2 * tol) onBar = true
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                if (onHandle && s != null) { brightness = valueAt(e.x, s); onBrightness(brightness, false); return true }
                // A finger that wanders off the bar isn't a tap on it any more.
                if (onBar && Math.abs(e.x - downX) > luna.px(20f)) onBar = false
                if (!flicking && maxScroll > 0 && Math.abs(e.y - downY) > slop) { flicking = true; onBar = false }
                if (flicking) {
                    pressed = null
                    scrollY0 = (scrollY0 - (e.y - lastY)).coerceIn(0f, maxScroll)
                    invalidate()
                } else if (pressed != null) {
                    // onExited: off the row, it is no longer pressed.
                    val p = pressedPlaced
                    val cy = e.y + scrollY0
                    if (p == null || cy < p.y || cy >= p.y + p.h || e.x < side || e.x > side + contentW) { pressed = null; invalidate() }
                }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                if (onHandle && s != null) { if (e.x != downX) brightness = valueAt(e.x, s); onBrightness(brightness, true) }
                else if (onBar && s != null) {
                    // A tap on the rail moves the handle a fifth of the way towards it.
                    brightness += if (e.x < handleX(s, hw)) -RAIL_STEP else RAIL_STEP
                    onBrightness(brightness, true)
                } else if (flicking) {
                    velocity?.computeCurrentVelocity(1000)
                    val vy = velocity?.yVelocity ?: 0f
                    scroller.fling(0, scrollY0.toInt(), 0, -vy.toInt(), 0, 0, 0, maxScroll.toInt())
                    postInvalidateOnAnimation()
                } else pressed?.let { act(it) }
                onHandle = false; onBar = false; flicking = false; pressed = null
                velocity?.recycle(); velocity = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                onHandle = false; onBar = false; flicking = false; pressed = null
                velocity?.recycle(); velocity = null
                invalidate()
            }
        }
        return true
    }

    /** A row tapped: SystemMenu.qml's handlers and their close timers. */
    private fun act(r: Row) {
        when (r) {
            is Row.Header -> {
                val d = drawer(r.drawer)
                d.toggle(!d.open)
                if (r.drawer == "wifi") {
                    controls.drawerWifiOpen = d.open
                    if (d.open) controls.scan() else controls.stopScanning()
                } else if (d.open) controls.loadDevices()
            }
            is Row.Action -> when (r.key) {
                "wifiOnOff" -> {
                    val on = controls.wifiOn
                    controls.setWifi(!on)
                    // Turned off, the menu goes; turned on, it waits to close on the connection.
                    if (on) onCloseRequest(300)
                }
                "wifiPrefs" -> { controls.openWifiPrefs(); onCloseRequest(300) }
                "btOnOff" -> {
                    if (controls.bluetoothOn && !controls.bluetoothTurningOn) onCloseRequest(300)
                    controls.setBluetooth(!controls.bluetoothOn)
                }
                "btPrefs" -> { controls.openBluetoothPrefs(); onCloseRequest(300) }
                "airplane" -> { controls.openAirplane(); onCloseRequest(250) }
                "rotation" -> { frozen = true; controls.toggleRotationLock(); onCloseRequest(250) }
                "mute" -> { frozen = true; controls.toggleMute(); onCloseRequest(250) }
            }
            is Row.Net -> if (controls.join(r.n)) onCloseRequest(300)
            is Row.Dev -> { controls.openBluetoothPrefs(); onCloseRequest(350) }
            else -> {}
        }
        invalidate()
    }
}
