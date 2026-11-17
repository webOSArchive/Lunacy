package org.webosarchive.lunacy.shell

import android.app.Activity
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.DeviceProfile
import org.webosarchive.lunacy.card.LaunchPoint
import org.webosarchive.lunacy.card.LunacyService
import org.webosarchive.lunacy.card.PdkRuntime
import org.webosarchive.lunacy.card.PdkWindow
import org.webosarchive.lunacy.card.WindowHost
import org.webosarchive.lunacy.core.Scheduler

/** The apps' lifecycle (LunaCore's, shared with Luna DE) over Android's windows. */
typealias AppLifecycle = org.webosarchive.lunacy.card.AppLifecycle<AppWindow>

/**
 * What LunaCore's AppLifecycle asks of a platform, answered for Android: its windows are
 * AppWindows (a WebView each, or a PDK app's PdkWindow), a card's place is [CardHost]'s, a
 * dock window's the Exhibition controller's and a dashboard's or an alert's the notification
 * host's; an Android app's tile and a shortcut to one of Android's settings screens are opened
 * by Android. What this needs of the rest of the shell comes through [Shell].
 */
class LifecyclePlatform(
    private val activity: Activity,
    /** What every window this starts talks to: the shell. */
    private val windowHost: WindowHost,
    private val bus: Bus,
    private val cardHost: CardHost,
    private val shell: Shell,
) : org.webosarchive.lunacy.card.AppLifecycle.Screen<AppWindow>, org.webosarchive.lunacy.card.AppLifecycle.Host<AppWindow> {
    interface Shell {
        val registry: AppRegistry
        /** The webOS device the next window is told it runs on. */
        val profile: DeviceProfile
        /** The PDK runtime for [profile]. */
        val pdkRuntime: PdkRuntime
        val exhibitions: ExhibitionController
        val notificationHost: NotificationHost
        /** A launch point an app added (applicationManager/addLaunchPoint), by its tile's id. */
        fun addedLaunchPoint(id: String): LaunchPoint?
        /** An Android app or a shortcut pinned to Android's home screen, by its tile's id. */
        fun androidTile(id: String): AppInfo?
        /** Opens an Android app or a pinned shortcut, which Android does. */
        fun openAndroid(app: AppInfo)
        /** A banner from the system itself; tapping it launches [appId]. */
        fun systemBanner(appId: String, message: String)
        /** Whether Lunacy is Android's home app, when an app is opened from its icon there. */
        fun lunacyIsHome(): Boolean
        fun closeMenu()
        /** The card view, with no card left to show. */
        fun onCardView()
        /** [window] has closed: whatever it held of the shell (the screen kept on) goes. */
        fun windowGone(window: AppWindow)
    }

    // ---- where a window goes (AppLifecycle.Screen) ----

    override fun launchedBy(appId: String, launchedBy: String?) = cardHost.launchedBy(appId, launchedBy)
    override fun openDirect(appId: String) = cardHost.openDirect(appId)
    override fun bringForward(appId: String) = cardHost.bringForward(appId)
    override fun showAsCard(w: AppWindow, parent: AppWindow?, placeholder: Boolean, splash: Boolean) = cardHost.showAsCard(w, parent, placeholder, splash)
    override fun cardOpened(parent: AppWindow, child: AppWindow) = cardHost.cardOpened(parent, child)
    override fun hide(w: AppWindow) = cardHost.hide(w)
    override fun isHidden(w: AppWindow) = cardHost.isHidden(w)

    override fun showDashboard(w: AppWindow) {
        val notificationHost = shell.notificationHost
        notificationHost.notifications.addDashboard(w, notificationHost.icon(w.attributes.optString("icon"), w.appId))
    }

    override fun showAlert(w: AppWindow) {
        val notificationHost = shell.notificationHost
        // A phone's list gives way to the alert in the same area: closed by the shell,
        // so the scrim over the rest goes with it, or it would eat the next tap.
        if (notificationHost.notificationsAtBottom) shell.closeMenu()
        notificationHost.notifications.popups.show(w, notificationHost.alertHeight(w))
    }

    override fun showInDock(w: AppWindow) = shell.exhibitions.addDockWindow(w)

    override fun windowClosed(w: AppWindow) {
        shell.exhibitions.windowClosed(w)
        cardHost.windowClosed(w)
        val notifications = shell.notificationHost.notifications
        notifications.removeDashboard(w)
        notifications.popups.remove(w)
    }

    override fun detach(w: AppWindow) { (w.parent as? android.view.ViewGroup)?.removeView(w) }

    override val empty get() = cardHost.cards.cards.isEmpty() && !shell.exhibitions.exhibitionOn
    override val slideMs get() = CardLayer.Params.SLIDE_MS

    // ---- the platform (AppLifecycle.Host) ----

    override val registry get() = shell.registry
    override val profile get() = shell.profile
    override fun addedLaunchPoint(id: String) = shell.addedLaunchPoint(id)

    /** An Android app or a pinned shortcut: Android opens it. */
    override fun openedOutside(id: String): Boolean {
        shell.openAndroid(shell.androidTile(id) ?: return false)
        return true
    }

    /**
     * A shortcut to one of Android's settings screens: no window, no pretending that Lunacy
     * owns the setting. Only apps Lunacy ships declare this.
     */
    override fun openedInstead(app: AppInfo): Boolean {
        if (app.androidSettings.isEmpty()) return false
        bus.call(app.id, "palm://${LunacyService.SERVICE}/android/openSettings",
            JSONObject().put("panel", app.androidSettings).toString()) { reply ->
            val r = runCatching { JSONObject(reply) }.getOrNull()
            if (r?.optBoolean("returnValue") != true) {
                shell.systemBanner(app.id, r?.optString("errorText").orEmpty().ifEmpty { "Couldn't open ${app.title}" })
            }
        }
        return true
    }

    override fun newWindow(app: AppInfo, emulated: Boolean): AppWindow = AppWindow(activity, app.id, windowHost, emulated)

    /** A PDK app: its own process, in a card of its own kind (Docs/pdk.md). */
    override fun newNativeWindow(app: AppInfo): AppWindow? {
        val pdkRuntime = shell.pdkRuntime
        if (!pdkRuntime.available) {
            shell.systemBanner(app.id, "${app.title} is a native app; it needs Lunacy's 32-bit build on a 32-bit ARM device")
            return null
        }
        return PdkWindow(activity, app.id, windowHost, app, pdkRuntime)
    }

    override fun startNative(window: AppWindow) = (window as PdkWindow).start()
    override fun systemBanner(appId: String, message: String) = shell.systemBanner(appId, message)
    override fun shellIsHome() = shell.lunacyIsHome()
    override fun onCardView() = shell.onCardView()
    override fun windowGone(window: AppWindow) = shell.windowGone(window)

    override fun memoryLow(): Boolean {
        val mi = android.app.ActivityManager.MemoryInfo()
        (activity.getSystemService(Activity.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
        return mi.lowMemory
    }
}

/**
 * Android saying memory is short (onTrimMemory), as the state LunaSysMgr's MemoryWatcher told
 * the apps: "low" or "critical". A level that is neither says nothing.
 */
fun AppLifecycle.trimMemory(level: Int) {
    memoryPressure(when (level) {
        android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "critical"
        android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "low"
        else -> return
    })
}

/** The shell's own handler as LunaCore's main thread: what the lifecycle posts goes with the shell (ShellActivity.onDestroy clears it). */
class HandlerScheduler(private val handler: Handler) : Scheduler {
    override fun isMainThread() = Looper.myLooper() == handler.looper
    override fun post(r: Runnable) { handler.post(r) }
    override fun postDelayed(r: Runnable, delayMs: Long) { handler.postDelayed(r, delayMs) }
    override fun cancel(r: Runnable) { handler.removeCallbacks(r) }
}
