package org.webosarchive.lunacy.shell

import android.app.Activity
import android.os.Handler
import android.util.Log
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.DeviceProfile
import org.webosarchive.lunacy.card.LaunchPoint
import org.webosarchive.lunacy.card.LunacyService
import org.webosarchive.lunacy.card.PdkRuntime
import org.webosarchive.lunacy.card.PdkWindow
import org.webosarchive.lunacy.card.Placement
import org.webosarchive.lunacy.card.WindowHost
import org.webosarchive.lunacy.card.WindowType

/**
 * The apps' lifecycle, as LunaSysMgr's WebAppManager and ApplicationManager kept it: which
 * apps are running and with which windows, launching one (a web app, a PDK app, an Android
 * app or settings screen, an added launch point) or relaunching it, a window opened or
 * closed, an app ending with its last window, the activity WebAppManager made for every app,
 * the apps started at boot, and the memory state the apps are told. Where a window goes on
 * the screen is [CardHost]'s; what this needs of the rest of the shell comes through [Host].
 */
class AppLifecycle(
    private val activity: Activity,
    /** What every window this starts talks to: the shell. */
    private val windowHost: WindowHost,
    private val bus: Bus,
    private val cardHost: CardHost,
    private val host: Host,
    private val handler: Handler,
) {
    interface Host {
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

    private val running = LinkedHashMap<String, MutableList<AppWindow>>()

    /** Running apps: app id to all its windows, root first. */
    val apps: Map<String, List<AppWindow>> get() = running

    /** Every running window. */
    val windows: List<AppWindow> get() = running.values.flatten()

    fun isRunning(appId: String) = running.containsKey(appId)

    /**
     * Starts an app, or brings it forward if it is running. [placement] is where its first
     * window goes ([Placement]); [launchedBy] is who asked, for the card this launch brings
     * (showAsCard): LunaSysMgr's launchingAppId.
     */
    fun launch(appId: String, params: JSONObject? = null, placement: Placement = Placement.CARD, launchedBy: String? = null) {
        cardHost.launchedBy(appId, launchedBy)
        // An added launch point launches its app with its own params, never into the dock.
        host.addedLaunchPoint(appId)?.let { lp ->
            launch(lp.appId, lp.paramsObject(), if (placement == Placement.DOCK) Placement.NO_CARD else placement); return
        }
        host.androidTile(appId)?.let { host.openAndroid(it); return }
        val app = host.registry.get(appId) ?: run { Log.w(AppServer.TAG, "launch: no app $appId"); return }
        if (app.androidSettings.isNotEmpty()) { openAndroidSettings(app); return }
        val windows = running[appId]
        // The dock takes web apps' windows only; a native app has no Exhibition view.
        if (placement == Placement.DOCK && !app.isWeb) { Log.w(AppServer.TAG, "exhibition: $appId is a native app"); return }
        when {
            !app.isWeb -> launchNative(app, windows)
            windows != null -> relaunch(app, windows, params, placement)
            else -> launchWeb(app, params, placement)
        }
    }

    /**
     * A shortcut to one of Android's settings screens: no window, no pretending that Lunacy
     * owns the setting. Only apps Lunacy ships declare this.
     */
    private fun openAndroidSettings(app: AppInfo) {
        bus.call(app.id, "palm://${LunacyService.SERVICE}/android/openSettings",
            JSONObject().put("panel", app.androidSettings).toString()) { reply ->
            val r = runCatching { JSONObject(reply) }.getOrNull()
            if (r?.optBoolean("returnValue") != true) {
                host.systemBanner(app.id, r?.optString("errorText").orEmpty().ifEmpty { "Couldn't open ${app.title}" })
            }
        }
    }

    /** A PDK app: its own process, in a card of its own kind (Docs/pdk.md); brought forward if running. */
    private fun launchNative(app: AppInfo, windows: List<AppWindow>?) {
        val appId = app.id
        if (windows != null) { cardHost.bringForward(appId); return }
        val pdkRuntime = host.pdkRuntime
        if (!pdkRuntime.available) {
            host.systemBanner(appId, "${app.title} is a native app; it needs Lunacy's 32-bit build on a 32-bit ARM device")
            return
        }
        val w = PdkWindow(activity, appId, windowHost, app, pdkRuntime)
        running[appId] = mutableListOf(w)
        cardHost.showAsCard(w)
        if (!w.start()) { host.systemBanner(appId, "Couldn't start ${app.title}"); windowClosed(w) }
    }

    /**
     * Relaunch: webOS doesn't reload a running app. Unless the app handles the relaunch
     * itself, LunaSysMgr brings its first card forward.
     */
    private fun relaunch(app: AppInfo, windows: List<AppWindow>, params: JSONObject?, placement: Placement) {
        val appId = app.id
        val before = windows.toSet()
        windows.first().relaunch(params?.toString() ?: "") { handled ->
            // A window the app opened while it handled the relaunch is its answer, even when the
            // handler doesn't say it handled it (Email's opens a compose card for a mailto: and
            // returns nothing). On a device that card arrived after LunaSysMgr had focused the
            // first card, and came up over it; here it is already on its way up, so the first
            // card mustn't be brought forward over it (it was: a compose card left behind the
            // inbox for every mailto:, 0.9.2's test round on the HP 10 G2).
            // A card (or a window of a type the shell doesn't know, which is shown as one); a
            // dashboard or an alert isn't a card, and the first card still comes forward.
            val opened = running[appId]?.any { it !in before && (it.type == WindowType.CARD || it.type == null) } == true
            Log.i(AppServer.TAG, "relaunch $appId ${if (Bus.handlesPeople(appId)) "" else params} handled=$handled${if (opened) ", opened a window" else ""}")
            // A relaunch for the dock leaves the card view alone: the app answers it with
            // a dockMode window, or the dock shows nothing of it, as on a device.
            if (!handled && !opened && placement != Placement.DOCK) cardHost.bringForward(appId)
        }
    }

    /** A web app that isn't running: its root window, placed as [placement] says, and its page. */
    private fun launchWeb(app: AppInfo, params: JSONObject?, placement: Placement) {
        val appId = app.id
        // The emulated card is a tablet's answer to a phone app. A Pre3 ran the same app at
        // its own size, so on a phone - where apps are told they are on a Pre3 - there is
        // nothing to emulate.
        val emulate = placement != Placement.DOCK && host.profile == DeviceProfile.TOUCHPAD && app.emulated
        val t0 = android.os.SystemClock.uptimeMillis()
        val rootWindow = AppWindow(activity, appId, windowHost, emulate)
        rootWindow.fixedOrientation = fixedOrientationOf(app)
        running[appId] = mutableListOf(rootWindow)
        createAppActivity(rootWindow)
        val url = if (params != null) app.url + "?launchParams=" + android.net.Uri.encode(params.toString()) else app.url
        // The startup card first, the page behind it: everything the launch can put off until
        // the card is on the screen waits for it (loadWhenShown). A headless app (Clock) opens
        // its card itself once its page has run, so for it the card is a placeholder with the
        // app's splash, which its first card window fills (onWindowOpened); an app that never
        // opens one loses the placeholder once it is up and running (withdrawPlaceholder).
        when (placement) {
            // ProcessManager::launch: an app that wasn't running, launched with windowType
            // dockModeWindow, gets its first window as a dock window, not a card. A headless
            // app opens its dock window itself (onWindowOpened).
            Placement.DOCK -> {
                if (app.noWindow) cardHost.hide(rootWindow) else host.exhibitions.addDockWindow(rootWindow)
                rootWindow.loadUrl(url)
            }
            Placement.NO_CARD -> {
                if (app.noWindow) cardHost.hide(rootWindow) else cardHost.showAsCard(rootWindow, splash = false)
                rootWindow.loadUrl(url)
            }
            Placement.CARD -> {
                cardHost.showAsCard(rootWindow, placeholder = app.noWindow)
                Log.i(AppServer.TAG, "launch $appId: window and card in ${android.os.SystemClock.uptimeMillis() - t0} ms")
                loadWhenShown(rootWindow, url, t0)
            }
        }
    }

    /** Page loads waiting for their startup card to be up; see [loadWhenShown]. */
    private val pendingLoads = HashMap<AppWindow, Runnable>()

    /**
     * Starts the page once its card has slid into the card view (or up, should it maximize
     * first). The page's scripts run on this thread - the WebView is single-process on these
     * Androids - so a load started any earlier froze the card's slide, and the launcher with
     * it, for as long as the app took to load. This is the point of the startup card
     * (codepoet): the work happens while it is on the screen.
     */
    private fun loadWhenShown(w: AppWindow, url: String, launchedAt: Long) {
        val load = Runnable {
            if (pendingLoads.remove(w) == null) return@Runnable
            Log.i(AppServer.TAG, "launch ${w.appId}: page load started ${android.os.SystemClock.uptimeMillis() - launchedAt} ms in")
            w.loadUrl(url)
        }
        pendingLoads[w] = load
        // On the activity's window rather than the card layer, which a rebuild replaces (reshell).
        activity.window.decorView.postDelayed(load, CardLayer.Params.SLIDE_MS + 50)
    }

    /** [window]'s card is up: a page load still waiting for it starts now. */
    fun shown(window: AppWindow) {
        pendingLoads[window]?.run()
    }

    /** Opens [appId] for its icon on Android's home screen. With Lunacy as the home app it is an ordinary launch. */
    fun openDirect(appId: String) {
        if (host.lunacyIsHome()) { launch(appId); return }
        cardHost.openDirect(appId)
        launch(appId)
    }

    /** appinfo.json's requestedWindowOrientation, which every card of the app starts with. */
    private fun fixedOrientationOf(app: AppInfo?): String? =
        app?.appinfo?.optString("requestedWindowOrientation")?.lowercase()
            ?.takeIf { it in ShellActivity.ORIENTATIONS }

    /** An app's page opened another window, [child], of the kind it asked for. */
    fun windowOpened(parent: AppWindow, child: AppWindow) {
        running.getOrPut(child.appId) { mutableListOf() } += child
        child.fixedOrientation = fixedOrientationOf(host.registry.get(child.appId))
        val notificationHost = host.notificationHost
        when (child.type) {
            WindowType.DASHBOARD -> notificationHost.notifications.addDashboard(child, notificationHost.icon(child.attributes.optString("icon"), child.appId))
            WindowType.POPUP_ALERT -> {
                // A phone's list gives way to the alert in the same area: closed by the shell,
                // so the scrim over the rest goes with it, or it would eat the next tap.
                if (notificationHost.notificationsAtBottom) host.closeMenu()
                notificationHost.notifications.popups.show(child, notificationHost.alertHeight(child))
            }
            // The headless app's first card: it fills the card its launch put up, and the root
            // goes on where a headless root lives.
            WindowType.CARD -> cardHost.cardOpened(parent, child)
            // An app's own Exhibition view: the dock's, never a card.
            WindowType.DOCK_MODE -> host.exhibitions.addDockWindow(child)
            // A name the shell doesn't know.
            null -> cardHost.showAsCard(child, parent)
        }
    }

    /** [window] has closed, or is closed by the shell: out of wherever it was shown, then closed. */
    fun windowClosed(window: AppWindow) {
        host.exhibitions.windowClosed(window)
        cardHost.windowClosed(window)
        val notifications = host.notificationHost.notifications
        notifications.removeDashboard(window)
        notifications.popups.remove(window)
        closeWindow(window)
    }

    /** Each running web app's own activity: its id once created, and the call that holds it. */
    private val appActivities = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val appActivityCalls = HashMap<String, Bus.Call>()

    /**
     * The app's activity, as WebAppManager made one for every app it started
     * (WebAppBase::createActivity): foreground, named for the app, its process id as the
     * description, created in the app's name and held by a subscription. Its id is
     * PalmSystem.activityId for all the app's windows, and Mojo sends it with each service
     * call as "$activity", which a JS service adopts.
     */
    private fun createAppActivity(root: AppWindow) {
        val appId = root.appId
        val params = JSONObject().put("activity", JSONObject().put("name", appId)
            .put("description", root.pid.toString()).put("type", JSONObject().put("foreground", true)))
            .put("subscribe", true).put("start", true).put("replace", true)
        appActivityCalls[appId] = bus.call(appId, "palm://com.palm.activitymanager/create", params.toString(), privateBus = true) { reply ->
            val r = runCatching { JSONObject(reply) }.getOrNull() ?: return@call
            if (r.optBoolean("returnValue") && r.has("activityId") && !r.has("event")) appActivities[appId] = r.optInt("activityId")
        }
    }

    /** The app has ended: WebAppBase::destroyActivity cancels the subscription, and the activity goes with it. */
    private fun endAppActivity(appId: String) {
        appActivities.remove(appId)
        appActivityCalls.remove(appId)?.cancel()
    }

    fun activityId(window: AppWindow): Int = appActivities[window.appId] ?: -1

    /** WebAppBase::focusActivity and blurActivity, as the app's card gains and loses the focus. */
    fun stageFocused(window: AppWindow, focused: Boolean) {
        val id = appActivities[window.appId] ?: return
        bus.call(window.appId, "palm://com.palm.activitymanager/${if (focused) "focus" else "unfocus"}",
            JSONObject().put("activityId", id).toString(), privateBus = true) {}
    }

    /** Closes [w] and, with its last visible window gone, its app. */
    fun closeWindow(w: AppWindow) {
        pendingLoads.remove(w)
        // A window that closes while holding the screen on lets it go.
        host.windowGone(w)
        val list = running[w.appId] ?: return
        list.remove(w)
        (w.parent as? android.view.ViewGroup)?.removeView(w)
        // Its unload handlers first, as webOS ran them (a dashboard's onUserClose comes from there).
        w.close()
        // An app ends when its last visible window closes; its headless root goes with it, a
        // moment later: a dashboard swiped away tells the root as it unloads (Enyo's
        // onUserClose), and the root acts on it, so it must still be there to hear it. A window
        // it opens meanwhile keeps it. An app in [KEEP_ALIVE] keeps its headless root.
        val app = host.registry.get(w.appId)
        if (list.isEmpty()) endApp(w.appId, list)
        else if (app?.noWindow == true && w.appId !in KEEP_ALIVE && list.all { cardHost.isHidden(it) }) {
            activity.window.decorView.postDelayed({
                if (running[w.appId] === list && list.all { cardHost.isHidden(it) }) endApp(w.appId, list)
            }, HEADLESS_LINGER_MS)
        }
        if (cardHost.cards.cards.isEmpty() && !host.exhibitions.exhibitionOn) host.onCardView()
    }

    private fun endApp(appId: String, list: MutableList<AppWindow>) {
        list.forEach { (it.parent as? android.view.ViewGroup)?.removeView(it); it.close() }
        running.remove(appId)
        endAppActivity(appId)
    }

    /**
     * Every app's pages destroyed, with the shell: each is a renderer in this process, and
     * Android re-creates the activity for a font-size or language change.
     */
    fun destroyAll() {
        pendingLoads.clear()
        for (w in running.values.flatten()) { (w.parent as? android.view.ViewGroup)?.removeView(w); w.destroy() }
        running.clear()
    }

    private var bootTimeAppsLaunched = false

    /**
     * ApplicationManager::launchBootTimeApps: once the launcher is up (here, once First Use
     * is done and the webOS root is ready), the apps in [LAUNCH_AT_BOOT] start headless with
     * {"launchedAtBoot": true}, which each of them answers by opening nothing. Email's headless
     * page is what tells of new mail (its DashboardManager), Messaging's of new messages.
     */
    fun launchBootTimeApps() {
        if (bootTimeAppsLaunched) return
        bootTimeAppsLaunched = true
        for (id in LAUNCH_AT_BOOT) if (host.registry.get(id) != null && running[id] == null)
            launch(id, JSONObject().put("launchedAtBoot", true), Placement.NO_CARD)
    }

    // ---- memory ----

    /** The memory state the apps were last told: LunaSysMgr's "normal", "low" or "critical". */
    private var memoryState = "normal"
    private var lastTrim = 0L

    /**
     * Android saying memory is short, passed on as LunaSysMgr's MemoryWatcher did: every app's
     * root page gets `Mojo.lowMemoryNotification({state})` when the state changes, which Enyo
     * turns into its `lowMemory` event (WebAppManager::slotMemoryStateChanged). The shell
     * still makes room itself, as before; this only lets an app help.
     */
    fun trimMemory(level: Int) {
        val state = when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "critical"
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "low"
            else -> return
        }
        lastTrim = android.os.SystemClock.uptimeMillis()
        setMemoryState(state)
    }

    /**
     * Android never says memory has eased, so the shell looks: "normal" once no warning has
     * come for half a minute and the system no longer calls itself low on memory.
     */
    private val memoryCheck = object : Runnable {
        override fun run() {
            if (memoryState == "normal") return
            val mi = android.app.ActivityManager.MemoryInfo()
            (activity.getSystemService(Activity.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
            if (android.os.SystemClock.uptimeMillis() - lastTrim >= MEMORY_CALM_MS && !mi.lowMemory) setMemoryState("normal")
            else handler.postDelayed(this, MEMORY_CHECK_MS)
        }
    }

    private fun setMemoryState(state: String) {
        if (state == memoryState) return
        memoryState = state
        Log.i(AppServer.TAG, "memory $state")
        // Root pages only, as LunaSysMgr sent it: a child window is the same app.
        val arg = JSONObject().put("state", state).toString()
        running.values.mapNotNull { it.firstOrNull() }.forEach { it.callMojo("lowMemoryNotification", arg) }
        handler.removeCallbacks(memoryCheck)
        if (state != "normal") handler.postDelayed(memoryCheck, MEMORY_CHECK_MS)
    }

    companion object {
        /**
         * The reference TouchPad's /etc/palm/luna-platform.conf, [LaunchAtBoot] and [KeepAlive]
         * (it overrides luna.conf's, which name only the phone app); used in phone mode too.
         * Measured there: closing Email's last card leaves its headless page running and
         * Clock's (not listed) ends; closing the headless page itself ends the app, and
         * nothing starts it again. Phone and Music Player aren't in Lunacy.
         */
        val LAUNCH_AT_BOOT = listOf("com.palm.app.phone", "com.palm.app.email", "com.palm.app.calendar", "com.palm.app.messaging")
        val KEEP_ALIVE = setOf("com.palm.app.email", "com.palm.app.calendar", "com.palm.app.messaging", "com.palm.app.photos", "com.palm.app.musicplayer")
        const val MEMORY_CHECK_MS = 10_000L
        const val MEMORY_CALM_MS = 30_000L
        /** How long a headless app outlives its last visible window: long enough to hear that window go. */
        const val HEADLESS_LINGER_MS = 1000L
    }
}
