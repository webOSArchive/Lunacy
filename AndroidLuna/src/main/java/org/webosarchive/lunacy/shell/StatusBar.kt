package org.webosarchive.lunacy.shell

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import java.util.Date

/**
 * The Luna status bar: title on the left; notifications, the info icons, battery and clock on
 * the right (reference §4.2). What changes is animated as LunaSysMgr animated it: the title
 * cross-fades, the title's ▾ fades with whether an app is up, and icons slide in and out.
 */
@SuppressLint("ViewConstructor")
class StatusBar(context: Context, private val luna: Luna) : View(context) {
    companion object {
        const val HEIGHT = 28  // TouchPad px; statusBar/status-bar-background.png is 28 high
        /** LunaCE's custom carrier string (sysUiCarrierString), shown when no app is maximized. */
        const val CARRIER_TEXT = "Lunacy"
        const val TITLE_CHANGE_MS = 300L   // statusBarTitleChangeDuration, linear
        const val ARROW_MS = 500L          // statusBarArrowSlideDuration, InOutQuad
        const val ITEM_SLIDE_MS = 1000L    // statusBarItemSlideDuration, InOutQuad
        const val GROUP_FADE_MS = 300L     // statusBarTabFadeDuration, linear
        const val MENU_FADE_MS = 200L      // statusBarMenuFadeDuration, linear
        /** StatusBarBattery::m_chargeLevels: the first state whose level is at least the battery's. */
        val CHARGE_LEVELS = intArrayOf(12, 20, 28, 36, 44, 52, 60, 68, 76, 84, 88, 99, 100)
        private val InOutQuad = android.animation.TimeInterpolator { if (it < 0.5f) 2 * it * it else 1 - (-2 * it + 2) * (-2 * it + 2) / 2 }
    }

    // ---- the title ----

    /** Changing it cross-fades the old text into the new over 300 ms while the width follows. */
    var title: String = CARRIER_TEXT
        set(v) {
            if (v == field) return
            oldTitle = field; field = v
            titleAnim?.cancel()
            titleProgress = 0f
            titleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TITLE_CHANGE_MS; interpolator = Easing.Linear
                addUpdateListener { titleProgress = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    private var oldTitle: String? = null
    private var titleProgress = 1f
    private var titleAnim: ValueAnimator? = null

    /**
     * The title group's ▾ and separator: shown while an app is up, which is what LunaSysMgr
     * called actionable (setMaximizedAppTitle), and faded in and out over 500 ms InOutQuad.
     * While it is gone the title group has no separator either - in the card view the
     * reference TouchPad draws "webOS CE" with nothing after it.
     */
    private var titleArrow = false
        set(v) {
            if (v == field) return
            field = v
            arrowAnim?.cancel()
            arrowAnim = ValueAnimator.ofFloat(arrowProgress, if (v) 1f else 0f).apply {
                duration = (ARROW_MS * Math.abs((if (v) 1f else 0f) - arrowProgress)).toLong()
                interpolator = InOutQuad
                addUpdateListener { arrowProgress = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    private var arrowProgress = 0f
    private var arrowAnim: ValueAnimator? = null

    // ---- icons ----

    /**
     * An icon that slides in and out as LunaSysMgr's StatusBarIcon did: over 1000 ms its width
     * grows in the first half (InOutQuad) while its opacity rises the whole way, and the
     * reverse to go.
     */
    private inner class SlidingIcon(var image: Bitmap?) {
        var progress = 0f
        var shown = false
        private var anim: ValueAnimator? = null
        val width get() = InOutQuad.getInterpolation(Math.min(1f, progress * 2))
        val visible get() = progress > 0f
        fun set(on: Boolean, done: () -> Unit = {}) {
            if (on == shown) return
            shown = on
            anim?.cancel()
            anim = ValueAnimator.ofFloat(progress, if (on) 1f else 0f).apply {
                duration = (ITEM_SLIDE_MS * Math.abs((if (on) 1f else 0f) - progress)).toLong()
                interpolator = Easing.Linear
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) { if (!shown) done() }
                })
                start()
            }
        }
    }

    /** Draws a sliding icon ending at [right]; returns where the next one ends, [gap] px of spacing to its left. */
    private fun drawSliding(c: Canvas, icon: SlidingIcon, right: Float, gap: Float): Float {
        val b = icon.image ?: return right
        if (!icon.visible) return right
        // StatusBarIcon: at most 26 px high (positiveSpaceTopPadding - 2), scaled to fit.
        val k = Math.min(1f, luna.px(26f) / b.height)
        val w = b.width * k * icon.width; val h = b.height * k
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (icon.progress * 255).toInt() }
        c.drawBitmap(b, Rect(0, 0, Math.round(b.width * icon.width), b.height), RectF(right - w, (height - h) / 2f, right, (height + h) / 2f), p)
        return right - w - gap * icon.width
    }

    /**
     * The dashboards' icons (reference §5.2). The first one added is drawn rightmost and later
     * ones to its left (StatusBarNotificationArea paints its list from the right).
     */
    private val notif = LinkedHashMap<Any, SlidingIcon>()
    fun addNotificationIcon(key: Any, icon: Bitmap?) {
        val first = notif.values.none { it.shown }
        notif[key] = SlidingIcon(icon).also { it.set(true) }
        if (first) fadeNotificationGroup(true)
    }
    fun removeNotificationIcon(key: Any) {
        val i = notif[key] ?: return
        i.set(false) {
            notif.remove(key)
            if (notif.isEmpty()) fadeNotificationGroup(false)
            invalidate()
        }
    }
    /** The notification group fades in with its first icon and out after its last (statusBarTabFade, 300 ms). */
    private var notifGroupOpacity = 0f
    private var notifGroupAnim: ValueAnimator? = null
    private fun fadeNotificationGroup(show: Boolean) {
        notifGroupAnim?.cancel()
        notifGroupAnim = ValueAnimator.ofFloat(notifGroupOpacity, if (show) 1f else 0f).apply {
            duration = GROUP_FADE_MS; interpolator = Easing.Linear
            addUpdateListener { notifGroupOpacity = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** The banner scrolling in the notification area, and its animation state. */
    var banner: Banner? = null
        set(v) { field = v; invalidate() }
    var bannerProgress = 0f
    var bannerAlpha = 1f
    /** Tap on the notification area (banner or dashboard icons). */
    var onNotificationTap: () -> Unit = {}
    /** The title (with its ▾) was tapped while an app is up: the app menu. */
    var onTitleTap: () -> Unit = {}
    /** The system group (clock, battery, icons, ▾) was tapped: the system menu. */
    var onSystemTap: () -> Unit = {}
    private var titleRight = 0f
    /** The dashboard drop-down is open: the notification group shows its highlighted tab. */
    var menuOpen = false
        set(v) { field = v; invalidate() }
    /** The system menu is open: the system group shows its tab. */
    var systemMenuOpen = false
        set(v) { field = v; invalidate() }

    /** status-bar-menu-dropdown-tab.png, 3-slice with 11 px caps, 11 px beyond the group each side. */
    private fun drawTab(c: Canvas, left: Float, right: Float) =
        luna.nine(c, "statusBar/status-bar-menu-dropdown-tab.png", RectF(left - luna.px(11f), 0f, right + luna.px(11f), height.toFloat()), 11, 0, 11, 0)

    /** Right edge of the notification group, where the dashboard drop-down aligns. */
    var notificationRight = 0f
        private set
    private var notificationLeft = 0f
    /** Left edge of the system group: its separator. */
    private var systemLeft = 0f

    private val bannerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = luna.px(16f); typeface = luna.fontMedium
    }

    // ---- what the device says ----

    /** 0…12, StatusBarBattery's state; 12 is full, which reuses battery-11 unless charging. */
    private var chargeState = 12
    /** The battery's own percentage, for the system menu. */
    var batteryPercent = -1
        private set
    /** The battery or the system menu's other contents changed. */
    var onSystemInfoChanged: () -> Unit = {}
    private var charging = false
    /** The battery has just reached 100 %; see [Sounds.batteryFull]. */
    var onBatteryFull: () -> Unit = {}
    /** StatusBarBattery's s_playSoundWhenCharged: armed below 95 %, fired once at 100 %. */
    private var soundWhenCharged = false

    /**
     * The info icons, right to left as StatusBarInfo paints them on a Wi-Fi tablet:
     * Bluetooth, Wi-Fi, VPN, rotation lock, mute, airplane. Each shows only while it applies.
     */
    private val bluetooth = SlidingIcon(null)
    private val wifi = SlidingIcon(null)
    private val vpn = SlidingIcon(luna.image("statusBar/vpn-status-icon.png"))
    private val rotationLock = SlidingIcon(luna.image("statusBar/icon-rotation-lock.png"))
    private val mute = SlidingIcon(luna.image("statusBar/icon-mute.png"))
    private val airplane = SlidingIcon(luna.image("statusBar/icon-airplane.png"))
    private val info get() = listOf(bluetooth, wifi, vpn, rotationLock, mute, airplane)

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
    private var fillAnim: ValueAnimator? = null

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
        fillAnim = ValueAnimator.ofFloat(0f, 1f).apply {
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
            if (i.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                // The bolt shows whenever power is connected, as on webOS.
                charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val percent = level * 100 / scale
                chargeState = CHARGE_LEVELS.indexOfFirst { percent <= it }.let { if (it < 0) 12 else it }
                if (percent < 95) soundWhenCharged = true
                if (soundWhenCharged && percent == 100) { soundWhenCharged = false; onBatteryFull() }
                if (percent != batteryPercent) { batteryPercent = percent; onSystemInfoChanged() }
            }
            updateInfo()
            invalidate()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val settingsObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { updateInfo() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION); addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION); addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION); addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        })
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, settingsObserver)
        updateInfo()
    }

    override fun onDetachedFromWindow() {
        context.unregisterReceiver(receiver)
        context.contentResolver.unregisterContentObserver(settingsObserver)
        super.onDetachedFromWindow()
    }

    /**
     * Reads everything the info icons show from Android's own state. Bluetooth needs only
     * `BLUETOOTH` on API 21; `BLUETOOTH_CONNECT` from API 31 is a ratchet item
     * (Docs/architecture.md), and a refusal is caught below.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun updateInfo() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Wi-Fi (StatusBarServicesConnector::wifiEventsCallback): on but not connected is
        // wifi-0, associating is wifi-connecting, connected is at least one bar.
        val ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        val wifiImage = when {
            !wm.isWifiEnabled -> null
            ni != null && ni.isConnected -> "wifi-" + WifiManager.calculateSignalLevel(wm.connectionInfo.rssi, 4).coerceIn(1, 3)
            ni != null && ni.detailedState in setOf(NetworkInfo.DetailedState.CONNECTING, NetworkInfo.DetailedState.AUTHENTICATING,
                NetworkInfo.DetailedState.OBTAINING_IPADDR, NetworkInfo.DetailedState.SCANNING) && ni.isConnectedOrConnecting -> "wifi-connecting"
            else -> "wifi-0"
        }
        wifiImage?.let { wifi.image = luna.image("statusBar/$it.png") }
        wifi.set(wifiImage != null)
        // Bluetooth: on, connecting or connected to a headset or speaker.
        val bt = try { BluetoothAdapter.getDefaultAdapter() } catch (e: Exception) { null }
        val btImage = try {
            if (bt == null || !bt.isEnabled) null else {
                val states = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).map { bt.getProfileConnectionState(it) }
                when {
                    BluetoothProfile.STATE_CONNECTED in states -> "bluetooth-connected"
                    BluetoothProfile.STATE_CONNECTING in states -> "bluetooth-connecting"
                    else -> "bluetooth-on"
                }
            }
        } catch (e: SecurityException) { null }
        btImage?.let { bluetooth.image = luna.image("statusBar/$it.png") }
        bluetooth.set(btImage != null)
        vpn.set(cm.getNetworkInfo(ConnectivityManager.TYPE_VPN)?.isConnected == true)
        rotationLock.set(Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0)
        // webOS's mute was the ringer switch; Android's is the ringer mode.
        mute.set((context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode != AudioManager.RINGER_MODE_NORMAL)
        airplane.set(Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0)
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), luna.px(HEIGHT))

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        fill.alpha = (fillOpacity * 255).toInt()
        c.drawRect(0f, 0f, width.toFloat(), h, fill)
        luna.tile(c, "statusBar/status-bar-background.png", RectF(0f, 0f, width.toFloat(), h))
        // The open system menu's tab, under the group (where the group began last frame).
        if (systemMenuOpen) drawTab(c, systemLeft + luna.px(2f), width.toFloat())

        titleRight = drawTitle(c, h)

        // System group, right to left: ▾, clock, battery, info icons, separator.
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
            charging && chargeState == 12 -> "statusBar/battery-charged.png"
            charging -> "statusBar/battery-charging-$chargeState.png"
            else -> "statusBar/battery-${Math.min(chargeState, 11)}.png"
        }
        x = drawIcon(c, battery, x)
        for (icon in info) x = drawSliding(c, icon, x - luna.px(5f) * icon.width, 0f)
        x -= luna.px(5f)
        systemLeft = x - luna.px(2f)
        if (!systemMenuOpen) separator(c, systemLeft)
        drawNotifications(c, systemLeft - luna.px(3f))
    }

    /** The title group; returns its right edge. */
    private fun drawTitle(c: Canvas, h: Float): Float {
        // 7 px padding, title (max 140 px, elided), ▾ when an app is up, separator.
        val lx = luna.px(7f)
        val max = luna.px(140f)
        fun elide(s: String) = android.text.TextUtils.ellipsize(s, android.text.TextPaint(text), max, android.text.TextUtils.TruncateAt.END).toString()
        val t = elide(title)
        var w = text.measureText(t)
        val old = oldTitle
        if (old != null && titleProgress < 1f) {
            // StatusBarTitle::animateTitleTransition: the old text fades out and the new one in,
            // both clipped to a width moving from the old one's to the new one's.
            val o = elide(old)
            w = text.measureText(o) * (1 - titleProgress) + w * titleProgress
            c.save(); c.clipRect(0f, 0f, lx + w, h)
            text.alpha = ((1 - titleProgress) * 255).toInt(); c.drawText(o, lx, baseline(text, h), text)
            text.alpha = (titleProgress * 255).toInt(); c.drawText(t, lx, baseline(text, h), text)
            text.alpha = 255
            c.restore()
        } else c.drawText(t, lx, baseline(text, h), text)
        var right = lx + w
        // The ▾ takes its room for as long as it is at all visible (StatusBarItemGroup::layoutLeft).
        if (arrowProgress > 0f) {
            val a = luna.image("statusBar/menu-arrow.png")
            val p = Paint().apply { alpha = (arrowProgress * 255).toInt() }
            if (a != null) {
                c.drawBitmap(a, right + luna.px(7f), (height - a.height) / 2f, p)
                right += luna.px(7f) + a.width + luna.px(7f)
            }
            luna.image("statusBar/status-bar-separator.png")?.let { c.drawBitmap(it, right, 0f, p) }
        } else right += luna.px(7f)
        return right
    }

    private fun baseline(p: Paint, h: Float) = h / 2 - (p.ascent() + p.descent()) / 2 - luna.px(1f)

    /** status-bar-separator.png (2×28) with its left edge at x; returns its left edge. */
    private fun separator(c: Canvas, x: Float, p: Paint? = null): Float {
        val s = luna.image("statusBar/status-bar-separator.png") ?: return x
        c.drawBitmap(s, x, 0f, p)
        return x
    }

    /** A ▾ ending at x (right-aligned groups); returns its left edge. */
    private fun drawArrowLeft(c: Canvas, x: Float, p: Paint? = null): Float {
        val a = luna.image("statusBar/menu-arrow.png") ?: return x
        val left = x - luna.px(7f) - a.width
        c.drawBitmap(a, left, (height - a.height) / 2f, p)
        return left - luna.px(7f)
    }

    /** Dashboard icons right to left (5 px apart, at most 26 px high), with the banner unrolling leftward from the group's right edge. */
    private fun drawNotifications(c: Canvas, right: Float) {
        notificationRight = right
        var x = right
        if (notif.isNotEmpty() || notifGroupOpacity > 0f) {
            val p = Paint().apply { alpha = (notifGroupOpacity * 255).toInt() }
            // The open menu's tab goes behind the group, in place of its separator.
            if (menuOpen) {
                val arrowW = (luna.image("statusBar/menu-arrow.png")?.width ?: 0) + luna.px(14f)
                var left = x - arrowW
                notif.values.forEach { i -> i.image?.let { b -> left -= (b.width * Math.min(1f, luna.px(26f) / b.height) + luna.px(5f)) * i.width } }
                drawTab(c, left - luna.px(2f), notificationRight)
            }
            x = drawArrowLeft(c, x, p)
            c.saveLayerAlpha(0f, 0f, x, height.toFloat(), p.alpha, Canvas.ALL_SAVE_FLAG)
            for (icon in notif.values) x = drawSliding(c, icon, x, luna.px(5f))
            c.restore()
            if (!menuOpen) separator(c, x - luna.px(2f), p)
            x -= luna.px(2f)
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
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        when {
            e.x >= notificationLeft - luna.px(10f) && e.x <= notificationRight + luna.px(10f) &&
                (notif.isNotEmpty() || banner != null) -> onNotificationTap()
            e.x >= systemLeft -> onSystemTap()
            titleArrow && e.x <= titleRight -> onTitleTap()
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
