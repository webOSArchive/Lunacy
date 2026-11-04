package org.webosarchive.lunacy.shell

import android.app.Activity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.FormFactor
import org.webosarchive.lunacy.card.NotificationIcons

/**
 * The shell's notifications: banners, dashboards and popup alerts, in the status bar and its
 * drop-down on a tablet (the TouchPad's) or in the area along the bottom on a phone held
 * upright (the Pre3's), and handed between the two as the phone turns. Also the icons they
 * draw and the notification light. What it needs of the rest of the shell comes through [Host].
 */
class NotificationHost(private val activity: Activity, private val host: Host) {
    interface Host {
        val luna: Luna
        val registry: AppRegistry
        val server: AppServer
        val statusBar: StatusBar
        val sounds: Sounds
        /** Screen & Lock's Blink Notifications. */
        val blinkNotifications: Boolean
        /** A banner tapped: its app launched with its params. */
        fun launch(appId: String, params: JSONObject?)
        fun closeWindow(window: AppWindow)
        fun closeMenu()
        /** The bar's or the area's tap: the notification drop-down opened or closed. */
        fun toggleMenu()
    }

    lateinit var notifications: Notifications
        private set
    /** A phone's notifications, along the bottom of the screen (the Pre3's); null on a tablet. */
    var area: NotificationArea? = null
        private set
    /** The TouchPad's hosts, from the bar: a tablet's, and a phone's in landscape. */
    private lateinit var barMenu: DashboardMenu
    private lateinit var barPopups: PopupLayer
    /** Whether the notifications are in [area] now: a phone held upright. */
    var notificationsAtBottom = false
        private set
    private val bannerIds = java.util.concurrent.atomic.AtomicInteger(1)
    /**
     * Builds the notifications' views with the shell's: the tablet's drop-down in [root], a
     * phone's area along the bottom of [screen], which [root] gives way to, and [popups], the
     * bar's alert layer.
     */
    fun build(screen: FrameLayout, root: FrameLayout, popups: PopupLayer) {
        val menu = DashboardMenu(activity, host.luna) { w -> notifications.removeDashboard(w); host.closeWindow(w) }
        root.addView(menu, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = host.luna.px(StatusBar.HEIGHT); gravity = android.view.Gravity.TOP or android.view.Gravity.START
        })
        // A phone's are the Pre3's, in an area along the bottom that the shell's root gives way
        // to (NotificationArea, Docs/phone.md §7); a tablet's are the TouchPad's, from the bar.
        area = if (!host.luna.phone) null else NotificationArea(activity, host.luna) { w -> notifications.removeDashboard(w); host.closeWindow(w) }.also { a ->
            screen.addView(a, FrameLayout.LayoutParams(MATCH_PARENT, 0).apply { gravity = android.view.Gravity.BOTTOM })
            a.onSpace = { px ->
                (root.layoutParams as FrameLayout.LayoutParams).let { lp -> if (lp.bottomMargin != px) { lp.bottomMargin = px; root.layoutParams = lp } }
            }
            a.onNotificationTap = { if (!notifications.tapBanner()) host.toggleMenu() }
            a.edgeTarget = root
            host.statusBar.onSlide = { hidden -> a.fullScreen = hidden }
        }
        barMenu = menu; barPopups = popups
        notificationsAtBottom = wantNotificationsAtBottom()
        FormFactor.pre3Dashboards = notificationsAtBottom
        notifications = newNotifications()
        host.statusBar.onNotificationTap = { if (!notifications.tapBanner()) host.toggleMenu() }
    }

    fun clearLight() = notificationLight.clear()

    /**
     * An icon from an app's origin (a dashboard's `icon`, a banner's), else the app's mini
     * icon, as LunaSysMgr fell back for both (DashboardWindow::icon, BannerMessageHandler::generateIcon).
     */
    fun icon(url: String?, appId: String): android.graphics.Bitmap? {
        val colour = NotificationIcons.colour(activity)
        if (url != null && url.startsWith("data:")) {
            // How the Android Notifications app gives an Android app's icon: a launcher icon,
            // so sized, and grey or not, as the mini icon made from one (Luna.miniIcon). A
            // webOS icon's art fills about nine tenths of its square and Android's all of it,
            // so it is drawn that much smaller in the middle, or it would fill the bar edge to edge.
            runCatching {
                android.util.Base64.decode(url.substringAfter(','), android.util.Base64.DEFAULT).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
            }.getOrNull()?.let {
                val side = host.luna.px(28)
                val sized = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
                val inset = side * (1f - ANDROID_ICON_ART) / 2f
                android.graphics.Canvas(sized).drawBitmap(it, null, android.graphics.RectF(inset, inset, side - inset, side - inset),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                return if (colour) sized else Luna.grey(sized)
            }
        } else if (!url.isNullOrEmpty()) host.server.serve(android.net.Uri.parse(url))?.takeIf { it.statusCode == 200 }?.let { host.luna.decode(it.data)?.let { b -> return b } }
        return host.registry.get(appId)?.let { host.luna.miniIcon(it, colour) }
    }

    fun addBanner(window: AppWindow, message: String, params: String, icon: String, soundClass: String, soundFile: String, duration: Int): Int {
        val id = bannerIds.getAndIncrement()
        activity.runOnUiThread { notifications.addBanner(Banner(id, window, window.appId, message, icon(icon, window.appId), params, soundClass, soundFile, duration)) }
        return id
    }

    /** The last "Selection Copied" banner, which the next one replaces (WebAppManager::copiedToClipboard). */
    private var copiedBanner: Pair<AppWindow, Int>? = null

    fun copiedToClipboard(window: AppWindow) {
        copiedBanner?.let { (w, id) -> notifications.removeBanner(w, id) }
        val id = bannerIds.getAndIncrement()
        notifications.addBanner(Banner(id, window, window.appId, "Selection Copied", icon(null, window.appId), "{ }"))
        copiedBanner = window to id
    }

    /** A banner from the system itself, e.g. the package manager. Tapping it launches appId. */
    fun systemBanner(appId: String, message: String, params: String = "") =
        notifications.addBanner(Banner(bannerIds.getAndIncrement(), null, appId, message, icon(null, appId), params))

    fun removeBanner(window: AppWindow, id: Int) = notifications.removeBanner(window, id)
    fun clearBanners(window: AppWindow) = notifications.clearBanners(window)


    /**
     * A phone held upright has the Pre3's notifications along the bottom; turned on its side
     * it is short, so they go to the bar as on a tablet (a delta: the Pre3 kept them at the
     * bottom; codepoet, 2026-10-16; Docs/luna-deltas.md B12).
     */
    private fun wantNotificationsAtBottom() =
        area != null && activity.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE

    private fun newNotifications(): Notifications {
        val a = area?.takeIf { notificationsAtBottom }
        return Notifications(host.luna, a ?: host.statusBar, a ?: barMenu, a ?: barPopups, onBannerTap = { b ->
            host.closeMenu()
            host.launch(b.appId, try { JSONObject(b.params) } catch (e: Exception) { null })
        }, onNoDashboards = { host.closeMenu(); notificationLight.clear() }, onBannerShown = { b ->
            host.sounds.play(b.appId, b.soundClass, b.soundFile, b.soundDuration)
        }, onArrived = { notificationLight.arrived() })
    }

    /** Screen & Lock's Blink Notifications: the device's notification light, where it has one. */
    private val notificationLight by lazy {
        NotificationLight(activity, enabled = { host.blinkNotifications },
            screenOn = { (activity.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isInteractive })
    }

    /** The owner chose colour or grey icons: the dashboards showing are drawn again. */
    fun iconsChanged() {
        notifications.dashboards.forEach { notifications.setIcon(it, icon(it.attributes.optString("icon"), it.appId)) }
    }

    /** A popup alert's height, as its window asked, or [DEFAULT_ALERT_HEIGHT]. */
    fun alertHeight(w: AppWindow) = w.attributes.optInt("height", DEFAULT_ALERT_HEIGHT)

    /** Dashboards, alerts and banners taken from the notifications a reshell or a turn replaced, given to the new ones. */
    fun giveNotifications(dashboards: List<AppWindow>, alerts: List<AppWindow>, banners: List<Banner>) {
        dashboards.forEach { notifications.addDashboard(it, icon(it.attributes.optString("icon"), it.appId)) }
        alerts.forEach { notifications.popups.show(it, alertHeight(it)) }
        notifications.takeBanners(banners)
    }

    /** The phone turned: its dashboards, alerts and banners move between the area and the bar. */
    fun placeNotifications() {
        val bottom = wantNotificationsAtBottom()
        if (bottom == notificationsAtBottom) return
        host.closeMenu()
        val old = notifications
        val dashboards = old.dashboards
        val alerts = old.popups.windows
        val banners = old.handOverBanners()
        dashboards.forEach { old.removeDashboard(it) }
        alerts.forEach { old.popups.remove(it) }
        (if (notificationsAtBottom) area!! as BannerSurface else host.statusBar).let { it.banner = null; it.redrawBanner() }
        notificationsAtBottom = bottom
        FormFactor.pre3Dashboards = bottom
        notifications = newNotifications()
        giveNotifications(dashboards, alerts, banners)
        host.statusBar.post { (dashboards + alerts).forEach { it.rescale() } }
        // Their boxes are the other kind now (FormFactor.pre3Dashboards).
        dashboards.forEach { it.evaluateJavascript(AppServer.REFETCH_LAYOUT_IMAGES, null) }
    }

    private companion object {
        /** A popup alert's height when its window gives none, in TouchPad px. */
        const val DEFAULT_ALERT_HEIGHT = 200
        /** How much of a webOS launcher icon's square its art fills (Lunacy's bundled apps' icons: 0.88 to 0.97): an Android icon in the bar is drawn this size. */
        const val ANDROID_ICON_ART = 0.9f
    }
}
