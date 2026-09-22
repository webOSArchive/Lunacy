package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.RectF
import android.view.MotionEvent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.text.format.DateFormat
import android.view.View
import java.util.Date

/** The Luna status bar: title on the left; notifications, wifi, battery and clock on the right. */
@SuppressLint("ViewConstructor")
class StatusBar(context: Context, private val luna: Luna) : View(context) {
    companion object {
        const val HEIGHT = 28  // TouchPad px; statusBar/status-bar-background.png is 28 high
        /** LunaCE's custom carrier string (sysUiCarrierString), shown when no app is maximized. */
        const val CARRIER_TEXT = "Lunacy"
    }

    var title: String = CARRIER_TEXT
        set(v) { field = v; invalidate() }

    /** One icon per dashboard, newest last (reference §5.2). */
    var notificationIcons: List<Bitmap?> = emptyList()
        set(v) { field = v; invalidate() }
    /** The banner scrolling in the notification area, and its animation state. */
    var banner: Banner? = null
        set(v) { field = v; invalidate() }
    var bannerProgress = 0f
    var bannerAlpha = 1f
    /** Tap on the notification area (banner or dashboard icons). */
    var onNotificationTap: () -> Unit = {}
    /** The title (with its ▾) was tapped while an app is up: the app menu. */
    var onTitleTap: () -> Unit = {}
    private var titleRight = 0f
    /** The dashboard drop-down is open: the notification group shows its highlighted tab. */
    var menuOpen = false
        set(v) { field = v; invalidate() }

    /** status-bar-menu-dropdown-tab.png, 3-slice with 11 px caps, 11 px beyond the group each side. */
    private fun drawTab(c: Canvas, left: Float, right: Float) =
        luna.nine(c, "statusBar/status-bar-menu-dropdown-tab.png", RectF(left - luna.px(11f), 0f, right + luna.px(11f), height.toFloat()), 11, 0, 11, 0)

    /** Right edge of the notification group, where the dashboard drop-down aligns. */
    var notificationRight = 0f
        private set
    private var notificationLeft = 0f

    private val bannerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = luna.px(16f); typeface = luna.fontMedium
    }

    private var batteryLevel = 11   // battery-0 … battery-11
    /** The battery has just reached 100 %; see [Sounds.batteryFull]. */
    var onBatteryFull: () -> Unit = {}
    /** StatusBarBattery's s_playSoundWhenCharged: armed below 95 %, fired once at 100 %. */
    private var soundWhenCharged = false
    private var charging = false
    private var full = false
    private var wifiLevel = -1      // -1 off, 0..3

    /** Title: Prelude bold 14 px, 90 % letter spacing; clock: Prelude bold 15 px (reference §4.2). */
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = luna.px(14f); typeface = luna.fontBold; letterSpacing = -0.05f
    }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = luna.px(15f); typeface = luna.fontBold }

    /**
     * Background (reference §4.1): a solid fill, transparent in card view and faded in over
     * 300 ms when an app is maximized (#515558) or the launcher is open (#4F545A), with
     * status-bar-background.png tiled over it.
     */
    enum class Mode(val color: Int) { CARDS(Color.rgb(0x51, 0x55, 0x58)), APP(Color.rgb(0x51, 0x55, 0x58)), LAUNCHER(Color.rgb(0x4F, 0x54, 0x5A)) }
    private val fill = Paint()
    private var fillOpacity = 0f
    private var fillAnim: android.animation.ValueAnimator? = null
    /** Whether the title shows its ▾ (an app is maximized). */
    private var titleArrow = false

    /**
     * [color] is the maximized app's own, from setWindowProperties' statusBarColor (0xRRGGBB),
     * in place of the mode's. LunaSysMgr cross-faded from the colour the bar had to the new
     * one over statusBarColorChangeDuration, 300 ms linear, alongside the fade in or out.
     */
    fun setMode(mode: Mode, color: Int? = null) {
        titleArrow = mode == Mode.APP
        val target = if (mode == Mode.CARDS) 0f else 1f
        val fromColor = fill.color or 0xFF000000.toInt()
        // In card view the fill fades out wearing the colour it had.
        val toColor = if (mode == Mode.CARDS) fromColor else (color?.let { it or 0xFF000000.toInt() } ?: mode.color)
        // Nothing to cross-fade from while the bar is transparent.
        val startColor = if (fillOpacity == 0f) toColor else fromColor
        val startOpacity = fillOpacity
        val argb = android.animation.ArgbEvaluator()
        fillAnim?.cancel()
        fillAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300; interpolator = Easing.Linear
            addUpdateListener {
                val f = it.animatedValue as Float
                fillOpacity = startOpacity + (target - startOpacity) * f
                fill.color = argb.evaluate(f, startColor, toColor) as Int
                invalidate()
            }
            start()
        }
    }

    /**
     * A full-screen card is up: the bar slides off the top, and back when it isn't. 400 ms
     * OutCubic, as LunaSysMgr animated the positive space. [done] runs once it is back.
     */
    fun slide(hidden: Boolean, done: () -> Unit = {}) {
        val to = if (hidden) -height.toFloat() else 0f
        animate().cancel()
        if (translationY == to) { done(); return }
        animate().translationY(to).setDuration(400).setInterpolator(Easing.OutCubic).withEndAction(done).start()
    }
    val slidOut get() = translationY != 0f

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    // The bolt shows whenever power is connected, as on webOS.
                    val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                    charging = plugged
                    full = plugged && (status == BatteryManager.BATTERY_STATUS_FULL || level >= scale)
                    batteryLevel = (level * 11 / scale).coerceIn(0, 11)
                    val percent = level * 100 / scale
                    if (percent < 95) soundWhenCharged = true
                    if (soundWhenCharged && percent == 100) { soundWhenCharged = false; onBatteryFull() }
                }
                else -> updateWifi()
            }
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION); addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        })
        updateWifi()
    }

    override fun onDetachedFromWindow() { context.unregisterReceiver(receiver); super.onDetachedFromWindow() }

    @Suppress("DEPRECATION")
    private fun updateWifi() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        wifiLevel = if (ni != null && ni.isConnected) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            WifiManager.calculateSignalLevel(wm.connectionInfo.rssi, 4)
        } else -1
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), luna.px(HEIGHT))

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        fill.alpha = (fillOpacity * 255).toInt()
        c.drawRect(0f, 0f, width.toFloat(), h, fill)
        luna.tile(c, "statusBar/status-bar-background.png", RectF(0f, 0f, width.toFloat(), h))

        // Title group: 7 px padding, title (max 140 px, elided), ▾ when an app is up, separator.
        var lx = luna.px(7f)
        val t = android.text.TextUtils.ellipsize(title, android.text.TextPaint(text), luna.px(140f), android.text.TextUtils.TruncateAt.END).toString()
        c.drawText(t, lx, baseline(text, h), text)
        lx += text.measureText(t)
        if (titleArrow) lx = drawArrow(c, lx)
        else lx += luna.px(7f)
        titleRight = lx
        separator(c, lx)

        // System group, right to left: ▾, clock, battery, wifi, separator.
        var x = width - luna.px(3f)
        x = drawArrowLeft(c, x)
        // LunaCE's clock: %I:%M with the leading zero stripped and no AM/PM, or %H:%M when the
        // device is set to 24 hours (reference §4.2). It follows the same setting apps are
        // told about through PalmSystem.timeFormat.
        val clock = android.text.format.DateFormat.format(
            if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", Date()).toString()
        x -= clockPaint.measureText(clock)
        c.drawText(clock, x, baseline(clockPaint, h), clockPaint)
        x -= luna.px(5f)
        val battery = when {
            full -> "statusBar/battery-charged.png"
            charging -> "statusBar/battery-charging-$batteryLevel.png"
            else -> "statusBar/battery-$batteryLevel.png"
        }
        x = drawIcon(c, battery, x)
        if (wifiLevel >= 0) x = drawIcon(c, "statusBar/wifi-$wifiLevel.png", x - luna.px(5f))
        x -= luna.px(5f)
        x = separator(c, x - luna.px(2f)) - luna.px(3f)
        drawNotifications(c, x)
    }

    private fun baseline(p: Paint, h: Float) = h / 2 - (p.ascent() + p.descent()) / 2 - luna.px(1f)

    /** status-bar-separator.png (2×28) with its left edge at x; returns its left edge. */
    private fun separator(c: Canvas, x: Float): Float {
        val s = luna.image("statusBar/status-bar-separator.png") ?: return x
        c.drawBitmap(s, x, 0f, null)
        return x
    }

    /** A ▾ (menu-arrow.png) with 7 px either side, placed after x; returns the new x. */
    private fun drawArrow(c: Canvas, x: Float): Float {
        val a = luna.image("statusBar/menu-arrow.png") ?: return x
        c.drawBitmap(a, x + luna.px(7f), (height - a.height) / 2f, null)
        return x + luna.px(7f) + a.width + luna.px(7f)
    }

    /** A ▾ ending at x (right-aligned groups); returns its left edge. */
    private fun drawArrowLeft(c: Canvas, x: Float): Float {
        val a = luna.image("statusBar/menu-arrow.png") ?: return x
        val left = x - luna.px(7f) - a.width
        c.drawBitmap(a, left, (height - a.height) / 2f, null)
        return left - luna.px(7f)
    }

    /** Dashboard icons right to left (5 px apart, at most 26 px high), with the banner unrolling leftward from the group's right edge. */
    private fun drawNotifications(c: Canvas, right: Float) {
        notificationRight = right
        var x = right
        if (notificationIcons.isNotEmpty()) {
            // Natural size, scaled down only if taller than 26 px (StatusBarIcon), 5 px apart.
            val sizes = notificationIcons.map { icon ->
                if (icon == null) 0f to 0f else minOf(1f, luna.px(26f) / icon.height).let { k -> icon.width * k to icon.height * k }
            }
            val arrowW = (luna.image("statusBar/menu-arrow.png")?.width ?: 0) + luna.px(14f)
            val groupLeft = x - arrowW - sizes.sumOf { (it.first + luna.px(5f)).toDouble() }.toFloat()
            // The open menu's tab goes behind the group.
            if (menuOpen) drawTab(c, groupLeft - luna.px(2f), notificationRight)
            x = drawArrowLeft(c, x)
            notificationIcons.indices.reversed().forEach { i ->
                val icon = notificationIcons[i]; val (w, h) = sizes[i]
                x -= w
                if (icon != null) c.drawBitmap(icon, null, RectF(x, (height - h) / 2f, x + w, (height + h) / 2f), null)
                x -= luna.px(5f)
            }
            x = separator(c, x - luna.px(2f))
        }
        notificationLeft = x
        val b = banner ?: return
        if (bannerProgress <= 0f) return
        val margin = luna.px(5f)
        val iconS = luna.px(24f)
        // LunaCE unrolls the banner over the notification area's whole maximum width, not the
        // text's: MAX_NOTIF_ICONS × (NOTIF_ICON_WIDTH + ICON_SPACING) + arrow + 2 × ARROW_SPACING
        // (StatusBar.cpp, StatusBarNotificationArea::setMaxWidth), text left-aligned in it.
        val arrowW = (luna.image("statusBar/menu-arrow.png")?.width ?: luna.px(15))
        val w = luna.px(10 * (24 + 5).toFloat()) + arrowW + luna.px(2 * 7f)
        val textW = w - margin - (if (b.icon != null) iconS + margin else 0f) - margin
        val text = android.text.TextUtils.ellipsize(b.text, android.text.TextPaint(bannerText), textW, android.text.TextUtils.TruncateAt.END).toString()
        val left = right - bannerProgress * w
        notificationLeft = minOf(notificationLeft, left)
        c.save()
        // No backing: LunaCE's StatusBarScroll draws the icon and text straight onto the bar.
        c.clipRect(left, 0f, right, height.toFloat())
        val alpha = (bannerAlpha * 255).toInt()
        var cx = left + margin
        b.icon?.let { c.drawBitmap(it, null, RectF(cx, (height - iconS) / 2f, cx + iconS, (height + iconS) / 2f), android.graphics.Paint().apply { this.alpha = alpha }); cx += iconS + margin }
        bannerText.alpha = alpha
        c.drawText(text, cx, height / 2f - (bannerText.ascent() + bannerText.descent()) / 2, bannerText)
        c.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP && e.x >= notificationLeft - luna.px(10f) && e.x <= notificationRight + luna.px(10f)) {
            onNotificationTap()
        } else if (e.actionMasked == MotionEvent.ACTION_UP && titleArrow && e.x <= titleRight) {
            onTitleTap()
        }
        return true
    }

    private fun drawIcon(c: Canvas, path: String, right: Float): Float {
        val b = luna.image(path) ?: return right
        val left = right - b.width
        c.drawBitmap(b, left, (height - b.height) / 2f, null)
        return left
    }
}
