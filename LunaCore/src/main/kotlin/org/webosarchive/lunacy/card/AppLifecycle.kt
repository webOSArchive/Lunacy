package org.webosarchive.lunacy.card

import org.json.JSONObject
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Platform
import org.webosarchive.lunacy.core.Scheduler

/**
 * A window as the apps' lifecycle keeps it: AppWindow on Android (a WebView, or a PDK app's
 * window), a page's view in Electron for Luna DE.
 */
interface LifecycleWindow : RunningWindow {
    val appId: String
    /** The kind of window its page asked for; null for a name the shell doesn't know, which is shown as a card. */
    val type: WindowType?
    /** appinfo.json's requestedWindowOrientation, which every card of the app starts with. */
    var fixedOrientation: String?
    /** Starts the window's page. */
    fun load(url: String)
    /** Relaunches the app in its page (Mojo.relaunch); [done] hears whether the app handled it. */
    fun relaunch(params: String, done: (Boolean) -> Unit)
    /** Calls a lifecycle hook the way LunaSysMgr did: a function on the page's Mojo object. */
    fun callMojo(fn: String, argsJson: String = "")
    /** Closes the window as webOS did: its page's unload handlers first. */
    fun close()
    /** Gone at once, with the shell. */
    fun destroy()
}

/**
 * The apps' lifecycle, as LunaSysMgr's WebAppManager and ApplicationManager kept it: which
 * apps are running and with which windows, launching one (a web app, a PDK app, a tile that is
 * the platform's own, an added launch point) or relaunching it, a window opened or closed, an
 * app ending with its last window, the activity WebAppManager made for every app, the apps
 * started at boot, and the memory state the apps are told. One code path for Android and Luna
 * DE (Docs/luna-de.md): where a window goes on the screen is [Screen]'s (CardHost and the rest
 * of the shell on Android), and what else this needs of the platform comes through [Host].
 */
class AppLifecycle<W : LifecycleWindow>(
    private val bus: Bus,
    private val screen: Screen<W>,
    private val host: Host<W>,
    private val scheduler: Scheduler = Platform.scheduler,
) {
    /** Where an app's windows are on the screen: the shell's cards, its dock, its dashboards and alerts. */
    interface Screen<W> {
        /** [appId]'s launch was asked for by [launchedBy] (LunaSysMgr's launchingAppId), for the group its card joins. */
        fun launchedBy(appId: String, launchedBy: String?)
        /** [appId] is opened from outside the shell's launcher: its card comes up at once. */
        fun openDirect(appId: String)
        /** Brings [appId]'s first card up. */
        fun bringForward(appId: String)
        /** [w] in a card of its own: a [placeholder] for a headless app's first card, with the app's [splash] or without. */
        fun showAsCard(w: W, parent: W? = null, placeholder: Boolean = false, splash: Boolean = true)
        /** A card window [child] that [parent] opened: a headless app's first fills the card its launch put up. */
        fun cardOpened(parent: W, child: W)
        /** [w] goes on headless: its scripts run, and it is never shown. */
        fun hide(w: W)
        fun isHidden(w: W): Boolean
        fun showDashboard(w: W)
        fun showAlert(w: W)
        /** An app's own Exhibition view: the dock's, never a card. */
        fun showInDock(w: W)
        /** [w] has closed, or is being closed: out of wherever it was shown. */
        fun windowClosed(w: W)
        /** [w] off the screen altogether, as it is closed or destroyed. */
        fun detach(w: W)
        /** Whether nothing is left to show: no card, and no Exhibition. */
        val empty: Boolean
        /** How long a new card takes to slide into the card view, which its page's load waits for. */
        val slideMs: Long
    }

    interface Host<W> {
        val registry: AppRegistry
        /** The webOS device the next window is told it runs on. */
        val profile: DeviceProfile
        /** A launch point an app added (applicationManager/addLaunchPoint), by its tile's id. */
        fun addedLaunchPoint(id: String): LaunchPoint?
        /**
         * A tile that is no webOS app, which the platform opens itself (an Android app, a shortcut
         * pinned to Android's home screen): true if [id] was one, and is opened.
         */
        fun openedOutside(id: String): Boolean
        /**
         * An app of Lunacy's own that stands for something of the platform's (one of Android's
         * settings screens; Luna DE's network switch): true if [app] was one, and that is done.
         */
        fun openedInstead(app: AppInfo): Boolean
        /** A web app's window, not yet on the screen and its page not yet started; [emulated] is the phone-sized card a TouchPad gave an old app. */
        fun newWindow(app: AppInfo, emulated: Boolean): W
        /** A native (PDK) app's window, not yet started; null where it can't run, which the host has said in a banner. */
        fun newNativeWindow(app: AppInfo): W?
        /** Starts a native app's process in [window]: false if it didn't start. */
        fun startNative(window: W): Boolean
        /** A banner from the system itself; tapping it launches [appId]. */
        fun systemBanner(appId: String, message: String)
        /** Whether the shell is where apps are opened from, so that [AppLifecycle.openDirect] is an ordinary launch (Lunacy as Android's home app). */
        fun shellIsHome(): Boolean
        /** The card view, with no card left to show. */
        fun onCardView()
        /** [window] has closed: whatever it held of the shell (the screen kept on) goes. */
        fun windowGone(window: W)
        /** Whether the platform still calls itself short of memory. */
        fun memoryLow(): Boolean
    }

    private val running = LinkedHashMap<String, MutableList<W>>()

    /** Running apps: app id to all its windows, root first. */
    val apps: Map<String, List<W>> get() = running

    /** Every running window. */
    val windows: List<W> get() = running.values.flatten()

    fun isRunning(appId: String) = running.containsKey(appId)

    /** A clock that only goes forward, in ms. */
    private fun now() = System.nanoTime() / 1_000_000

    /**
     * Starts an app, or brings it forward if it is running. [placement] is where its first
     * window goes ([Placement]); [launchedBy] is who asked, for the card this launch brings
     * (showAsCard): LunaSysMgr's launchingAppId.
     */
    fun launch(appId: String, params: JSONObject? = null, placement: Placement = Placement.CARD, launchedBy: String? = null) {
        screen.launchedBy(appId, launchedBy)
        // An added launch point launches its app with its own params, never into the dock.
        host.addedLaunchPoint(appId)?.let { lp ->
            launch(lp.appId, lp.paramsObject(), if (placement == Placement.DOCK) Placement.NO_CARD else placement); return
        }
        if (host.openedOutside(appId)) return
        val app = host.registry.get(appId) ?: run { Log.w(AppServer.TAG, "launch: no app $appId"); return }
        if (host.openedInstead(app)) return
        val windows = running[appId]
        // The dock takes web apps' windows only; a native app has no Exhibition view.
        if (placement == Placement.DOCK && !app.isWeb) { Log.w(AppServer.TAG, "exhibition: $appId is a native app"); return }
        when {
            !app.isWeb -> launchNative(app, windows)
            windows != null -> relaunch(app, windows, params, placement)
            else -> launchWeb(app, params, placement)
        }
    }

    /** A PDK app: its own process, in a card of its own kind (Docs/pdk.md); brought forward if running. */
    private fun launchNative(app: AppInfo, windows: List<W>?) {
        val appId = app.id
        if (windows != null) { screen.bringForward(appId); return }
        val w = host.newNativeWindow(app) ?: return
        running[appId] = mutableListOf(w)
        screen.showAsCard(w)
        if (!host.startNative(w)) { host.systemBanner(appId, "Couldn't start ${app.title}"); windowClosed(w) }
    }

    /**
     * Relaunch: webOS doesn't reload a running app. Unless the app handles the relaunch
     * itself, LunaSysMgr brings its first card forward.
     */
    private fun relaunch(app: AppInfo, windows: List<W>, params: JSONObject?, placement: Placement) {
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
            if (!handled && !opened && placement != Placement.DOCK) screen.bringForward(appId)
        }
    }

    /** A web app that isn't running: its root window, placed as [placement] says, and its page. */
    private fun launchWeb(app: AppInfo, params: JSONObject?, placement: Placement) {
        val appId = app.id
        // The emulated card is a tablet's answer to a phone app. A Pre3 ran the same app at
        // its own size, so on a phone - where apps are told they are on a Pre3 - there is
        // nothing to emulate.
        val emulate = placement != Placement.DOCK && host.profile == DeviceProfile.TOUCHPAD && app.emulated
        val t0 = now()
        val rootWindow = host.newWindow(app, emulate)
        rootWindow.fixedOrientation = fixedOrientationOf(app)
        running[appId] = mutableListOf(rootWindow)
        createAppActivity(rootWindow)
        val url = if (params != null) app.url + "?launchParams=" + encodeParam(params.toString()) else app.url
        // The startup card first, the page behind it: everything the launch can put off until
        // the card is on the screen waits for it (loadWhenShown). A headless app (Clock) opens
        // its card itself once its page has run, so for it the card is a placeholder with the
        // app's splash, which its first card window fills (windowOpened); an app that never
        // opens one loses the placeholder once it is up and running (the screen's to decide).
        when (placement) {
            // ProcessManager::launch: an app that wasn't running, launched with windowType
            // dockModeWindow, gets its first window as a dock window, not a card. A headless
            // app opens its dock window itself (windowOpened).
            Placement.DOCK -> {
                if (app.noWindow) screen.hide(rootWindow) else screen.showInDock(rootWindow)
                rootWindow.load(url)
            }
            Placement.NO_CARD -> {
                if (app.noWindow) screen.hide(rootWindow) else screen.showAsCard(rootWindow, splash = false)
                rootWindow.load(url)
            }
            Placement.CARD -> {
                screen.showAsCard(rootWindow, placeholder = app.noWindow)
                Log.i(AppServer.TAG, "launch $appId: window and card in ${now() - t0} ms")
                loadWhenShown(rootWindow, url, t0)
            }
        }
    }

    /** Page loads waiting for their startup card to be up; see [loadWhenShown]. */
    private val pendingLoads = HashMap<W, Runnable>()

    /**
     * Starts the page once its card has slid into the card view (or up, should it maximize
     * first). On Android the page's scripts run on the shell's thread - the WebView is
     * single-process on these Androids - so a load started any earlier froze the card's slide,
     * and the launcher with it, for as long as the app took to load. This is the point of the
     * startup card (codepoet): the work happens while it is on the screen.
     */
    private fun loadWhenShown(w: W, url: String, launchedAt: Long) {
        val load = Runnable {
            if (pendingLoads.remove(w) == null) return@Runnable
            Log.i(AppServer.TAG, "launch ${w.appId}: page load started ${now() - launchedAt} ms in")
            w.load(url)
        }
        pendingLoads[w] = load
        scheduler.postDelayed(load, screen.slideMs + 50)
    }

    /** [window]'s card is up: a page load still waiting for it starts now. */
    fun shown(window: W) {
        pendingLoads[window]?.run()
    }

    /** Opens [appId] from outside the shell's launcher (its icon on Android's home screen). With the shell as the home it is an ordinary launch. */
    fun openDirect(appId: String) {
        if (host.shellIsHome()) { launch(appId); return }
        screen.openDirect(appId)
        launch(appId)
    }

    /** appinfo.json's requestedWindowOrientation, which every card of the app starts with. */
    private fun fixedOrientationOf(app: AppInfo?): String? =
        app?.appinfo?.optString("requestedWindowOrientation")?.lowercase()
            ?.takeIf { it in ORIENTATIONS }

    /** An app's page opened another window, [child], of the kind it asked for. */
    fun windowOpened(parent: W, child: W) {
        running.getOrPut(child.appId) { mutableListOf() } += child
        child.fixedOrientation = fixedOrientationOf(host.registry.get(child.appId))
        when (child.type) {
            WindowType.DASHBOARD -> screen.showDashboard(child)
            WindowType.POPUP_ALERT -> screen.showAlert(child)
            // The headless app's first card: it fills the card its launch put up, and the root
            // goes on where a headless root lives.
            WindowType.CARD -> screen.cardOpened(parent, child)
            // An app's own Exhibition view: the dock's, never a card.
            WindowType.DOCK_MODE -> screen.showInDock(child)
            // A name the shell doesn't know.
            null -> screen.showAsCard(child, parent)
        }
    }

    /** [window] has closed, or is closed by the shell: out of wherever it was shown, then closed. */
    fun windowClosed(window: W) {
        screen.windowClosed(window)
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
    private fun createAppActivity(root: W) {
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

    fun activityId(window: W): Int = appActivities[window.appId] ?: -1

    /** WebAppBase::focusActivity and blurActivity, as the app's card gains and loses the focus. */
    fun stageFocused(window: W, focused: Boolean) {
        val id = appActivities[window.appId] ?: return
        bus.call(window.appId, "palm://com.palm.activitymanager/${if (focused) "focus" else "unfocus"}",
            JSONObject().put("activityId", id).toString(), privateBus = true) {}
    }

    /** Closes [w] and, with its last visible window gone, its app. */
    fun closeWindow(w: W) {
        pendingLoads.remove(w)
        // A window that closes while holding the screen on lets it go.
        host.windowGone(w)
        val list = running[w.appId] ?: return
        list.remove(w)
        screen.detach(w)
        // Its unload handlers first, as webOS ran them (a dashboard's onUserClose comes from there).
        w.close()
        // An app ends when its last visible window closes; its headless root goes with it, a
        // moment later: a dashboard swiped away tells the root as it unloads (Enyo's
        // onUserClose), and the root acts on it, so it must still be there to hear it. A window
        // it opens meanwhile keeps it. An app in [KEEP_ALIVE] keeps its headless root.
        val app = host.registry.get(w.appId)
        if (list.isEmpty()) endApp(w.appId, list)
        else if (app?.noWindow == true && w.appId !in KEEP_ALIVE && list.all { screen.isHidden(it) }) {
            scheduler.postDelayed({
                if (running[w.appId] === list && list.all { screen.isHidden(it) }) endApp(w.appId, list)
            }, HEADLESS_LINGER_MS)
        }
        if (screen.empty) host.onCardView()
    }

    /** An app has ended (its last window closed): what lasted for its session ends with it. */
    var onAppEnded: (String) -> Unit = {}

    private fun endApp(appId: String, list: MutableList<W>) {
        list.forEach { screen.detach(it); it.close() }
        running.remove(appId)
        endAppActivity(appId)
        onAppEnded(appId)
    }

    /**
     * Every app's pages destroyed, with the shell: on Android each is a renderer in the shell's
     * process, and Android re-creates the activity for a font-size or language change.
     */
    fun destroyAll() {
        pendingLoads.clear()
        for (w in running.values.flatten()) { screen.detach(w); w.destroy() }
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
        flushWhenSettled(0)
    }

    /** Whether what a boot starts has run: the shell's activity manager has no boot activity left. */
    var bootSettled: () -> Boolean = { true }
    /** What the boot still has to do, for the log. */
    var bootPending: () -> String = { "" }

    /**
     * The launches from outside, once the boot's own work is done: the boot-time apps
     * started, and the boot activities started, which relaunch Clock and Calendar for their own
     * ends. A launch that came in among them was taken over by them (Clock started on the
     * person's launch, asked for its preferences, and by their answer its window params were
     * an alarm update's: no card). Never longer than [BOOT_SETTLE_MAX_MS], so a boot activity
     * that never starts (one ahead of it in the queue whose app never completes it) can't keep the launch from happening.
     */
    private fun flushWhenSettled(waited: Long) {
        if (waitingForBoot.isEmpty()) return
        if (!bootSettled() && waited < BOOT_SETTLE_MAX_MS) {
            scheduler.postDelayed({ flushWhenSettled(waited + BOOT_SETTLE_STEP_MS) }, BOOT_SETTLE_STEP_MS)
            return
        }
        Log.i(AppServer.TAG, "launch from outside: the boot settled after $waited ms${if (bootSettled()) "" else " (gave up waiting on ${bootPending()})"}")
        val waiting = waitingForBoot.toList()
        waitingForBoot.clear()
        waiting.forEach { it() }
    }

    /** Launches from outside that came before the boot-time apps had started. */
    private val waitingForBoot = ArrayList<() -> Unit>()

    /**
     * A launch from outside the shell (Android's intent, a pinned icon on Android's home
     * screen), which can come as the shell starts. On a device the boot-time apps were running
     * before anyone could tap an icon, so a launch reached Email or Calendar as a relaunch of
     * the headless app. One that came first here started the app cold beside its own boot
     * start and its boot activities, and could come up with no card (0.9.0's sweep; Email's
     * card came after its placeholder had gone). So it waits for the boot-time apps when
     * they are still to come, which they are only until First Use is done and the webOS
     * root is up ([launchBootTimeApps]), and for the boot activities ([flushWhenSettled]);
     * [booting] says whether they are still to come.
     */
    fun launchFromOutside(booting: Boolean, launch: () -> Unit) {
        if (!booting || bootTimeAppsLaunched) { launch(); return }
        Log.i(AppServer.TAG, "launch from outside waits for the boot-time apps")
        waitingForBoot += launch
    }

    // ---- memory ----

    /** The memory state the apps were last told: LunaSysMgr's "normal", "low" or "critical". */
    private var memoryState = "normal"
    private var lastPressure = 0L

    /**
     * The platform saying memory is short ("low" or "critical"), passed on as LunaSysMgr's
     * MemoryWatcher did: every app's root page gets `Mojo.lowMemoryNotification({state})` when
     * the state changes, which Enyo turns into its `lowMemory` event
     * (WebAppManager::slotMemoryStateChanged). The shell still makes room itself, as before;
     * this only lets an app help.
     */
    fun memoryPressure(state: String) {
        lastPressure = now()
        setMemoryState(state)
    }

    /**
     * Android never says memory has eased, so the shell looks: "normal" once no warning has
     * come for half a minute and the system no longer calls itself low on memory.
     */
    private val memoryCheck = object : Runnable {
        override fun run() {
            if (memoryState == "normal") return
            if (now() - lastPressure >= MEMORY_CALM_MS && !host.memoryLow()) setMemoryState("normal")
            else scheduler.postDelayed(this, MEMORY_CHECK_MS)
        }
    }

    private fun setMemoryState(state: String) {
        if (state == memoryState) return
        memoryState = state
        Log.i(AppServer.TAG, "memory $state")
        // Root pages only, as LunaSysMgr sent it: a child window is the same app.
        val arg = JSONObject().put("state", state).toString()
        running.values.mapNotNull { it.firstOrNull() }.forEach { it.callMojo("lowMemoryNotification", arg) }
        scheduler.cancel(memoryCheck)
        if (state != "normal") scheduler.postDelayed(memoryCheck, MEMORY_CHECK_MS)
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
        /** The orientations a card can be fixed at (appinfo.json's requestedWindowOrientation). */
        val ORIENTATIONS = setOf("up", "down", "right", "left", "landscape", "portrait")
        const val BOOT_SETTLE_STEP_MS = 100L
        const val BOOT_SETTLE_MAX_MS = 5_000L
        const val MEMORY_CHECK_MS = 10_000L
        const val MEMORY_CALM_MS = 30_000L
        /** How long a headless app outlives its last visible window: long enough to hear that window go. */
        const val HEADLESS_LINGER_MS = 1000L

        /**
         * A launch's params as a query value, percent-encoded as android.net.Uri.encode does
         * (which is what every Lunacy build has put in an app's URL): UTF-8, everything but
         * letters, digits and _-!.~'()* escaped.
         */
        fun encodeParam(s: String): String {
            val out = StringBuilder(s.length + 16)
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xff
                if (c < 0x80 && (c.toChar().let { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } || c.toChar() in "_-!.~'()*")) out.append(c.toChar())
                else out.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
            return out.toString()
        }
    }
}
