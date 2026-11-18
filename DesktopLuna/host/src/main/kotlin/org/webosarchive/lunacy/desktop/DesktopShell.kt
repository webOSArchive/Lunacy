package org.webosarchive.lunacy.desktop

import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppFiles
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.AppLifecycle
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.ApplicationManager
import org.webosarchive.lunacy.card.Configurator
import org.webosarchive.lunacy.card.LaunchPoints
import org.webosarchive.lunacy.card.Packages
import org.webosarchive.lunacy.card.Placement
import org.webosarchive.lunacy.card.RunningWindow
import org.webosarchive.lunacy.card.WindowType
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Luna DE's shell, as LunaCore's services see it: what the application manager asks of a shell
 * (ApplicationManager.Shell), and what the apps' lifecycle asks of a platform (AppLifecycle's
 * Screen and Host), answered for the desktop.
 *
 * Which apps run and with which windows is LunaCore's AppLifecycle, the class Android's shell
 * runs ([lifecycle]): a running app is relaunched, not reloaded, an app ends with its last
 * window, a headless app outlives it by a moment, Email and the other keep-alive apps keep
 * their headless page, and the boot-time apps start headless. Its windows here are
 * [DesktopView]s. What goes on the screen is Electron's, which hears of it as events ([take]):
 * a window to show as a card, to keep unseen, to load, to remove or to close, a card to bring
 * forward, the launcher to draw again, a banner. Electron decides none of it.
 */
class DesktopShell(
    private val main: DesktopScheduler,
    private val bus: org.webosarchive.lunacy.card.Bus,
    override val registry: AppRegistry,
    private val files: AppFiles,
    private val packages: Packages,
    private val addedLaunchPoints: LaunchPoints,
    private val data: File,
) : ApplicationManager.Shell, AppLifecycle.Screen<DesktopView>, AppLifecycle.Host<DesktopView> {
    /** Every page with a native interface, by its page id. */
    val windows = ConcurrentHashMap<String, DesktopWindow>()
    /** Every window of a running app, by the id Electron knows its view by. */
    val views = ConcurrentHashMap<String, DesktopView>()
    /** The running apps and their windows. On the main thread. */
    val lifecycle = AppLifecycle(bus, this, this, main)
    private val events = LinkedBlockingQueue<JSONObject>()
    lateinit var configurator: Configurator
    lateinit var applicationManager: ApplicationManager
    var jsServices: org.webosarchive.lunacy.card.JsServices? = null
    var universalSearch: org.webosarchive.lunacy.card.UniversalSearch? = null
    var network: DesktopNetwork? = null
    /** Where every app's WebSQL databases are kept (files/websql on Android). */
    val webSqlDir get() = File(data, "websql")

    // ---- events to Electron ----

    private fun event(type: String) = JSONObject().put("type", type)

    /** What Electron is to do next, after waiting up to [ms] for the first. */
    fun take(ms: Long): List<JSONObject> {
        val first = events.poll(ms, TimeUnit.MILLISECONDS) ?: return emptyList()
        return arrayListOf(first).also { events.drainTo(it) }
    }

    override fun systemBanner(appId: String, message: String) {
        events.add(event("banner").put("id", nextBanner.getAndIncrement()).put("appId", appId).put("message", message).put("params", "").put("icon", ""))
    }

    // ---- the device ----

    /**
     * The webOS device Luna DE is being: a TouchPad (webOS CE 3.1.0, 1024 x 768) or a Pre3 (HP
     * webOS 2.2.4, 480 x 800 at a scale of 1.5, so apps lay out 320 wide). Both profiles are
     * LunaCore's, measured on the devices. A page is told its device once, as it starts, so
     * every app is closed before this changes ([setDevice]).
     */
    @Volatile var device = org.webosarchive.lunacy.card.DeviceProfile.TOUCHPAD
        private set

    /**
     * As "this screen": a TouchPad at the size Luna DE's window is, as Lunacy is on an Android
     * tablet (it fills the screen it finds, and tells apps of that screen in TouchPad px:
     * ShellActivity.deviceInfo). The long and the short side in a page's px, which Electron
     * gives as the window changes; null as a TouchPad or a Pre3 at their own sizes.
     */
    @Volatile private var fill: IntArray? = null
    val filling get() = fill != null

    /** The screen as the device names it (deviceInfo's screenWidth and screenHeight), and the scale pages are drawn at. */
    val screenWidth get() = fill?.get(0) ?: if (device == org.webosarchive.lunacy.card.DeviceProfile.PRE3) 480 else 1024
    val screenHeight get() = fill?.get(1) ?: if (device == org.webosarchive.lunacy.card.DeviceProfile.PRE3) 800 else 768
    /** Screen px per page px. As this screen the size is already in a page's px (Electron scales the pages). */
    val scale get() = if (device == org.webosarchive.lunacy.card.DeviceProfile.PRE3) 1.5 else 1.0

    /** As this screen, the window changed size: [w] by [h] in a page's px, as it is held. */
    fun setScreen(w: Int, h: Int) {
        if (fill == null || w <= 0 || h <= 0) return
        fill = intArrayOf(maxOf(w, h), minOf(w, h))
    }

    /** Luna DE becomes [name]: "touchpad", "pre3", or "screen" at [w] by [h] (a page's px, as the window is held). */
    fun setDevice(name: String, w: Int = 0, h: Int = 0) = main.call {
        // Every app ends first: a page is told its device once, as it starts.
        lifecycle.windows.forEach { lifecycle.windowClosed(it) }
        device = if (name == "pre3") org.webosarchive.lunacy.card.DeviceProfile.PRE3 else org.webosarchive.lunacy.card.DeviceProfile.TOUCHPAD
        fill = if (name == "screen" && w > 0 && h > 0) intArrayOf(maxOf(w, h), minOf(w, h)) else null
        // Held the way the device names its screen: a TouchPad wide, a Pre3 tall; this screen as its window is shaped.
        orientation = if (fill != null) (if (h > w) "up" else "right") else if (device.naturalLandscape) "right" else "up"
        Log.i(Lunacy.TAG, "device: ${device.modelNameAscii}${fill?.let { " at this screen's size, ${it[0]} x ${it[1]}" } ?: ""}")
        // As the other device starting: its boot-time apps again.
        for (id in AppLifecycle.LAUNCH_AT_BOOT) if (registry.get(id) != null && !lifecycle.isRunning(id))
            lifecycle.launch(id, JSONObject().put("launchedAtBoot", true), Placement.NO_CARD)
    }

    // ---- phone zoom ----

    /**
     * Phone Zoom, as Lunacy has it on a phone (Docs/phone.md, "Fixed viewport, per app";
     * AndroidLuna's FixedViewport): the one per-app switch the rules allow. An app with it on
     * has its page laid out so the card's shorter side is [PHONE_ZOOM_WIDTH] px and shown
     * scaled down to the card, so an app written for a TouchPad's screen fits a Pre3's whole.
     * Only as a Pre3; what the app is told doesn't change. The user's switches are kept in
     * phone-zoom.json; an app without one takes its default.
     */
    private val phoneZoomFile = File(data, "phone-zoom.json")
    private val phoneZoomSet: JSONObject = try { JSONObject(phoneZoomFile.readText()) } catch (e: Exception) { JSONObject() }

    fun phoneZoomOn(appId: String): Boolean = synchronized(phoneZoomSet) {
        if (phoneZoomSet.has(appId)) phoneZoomSet.optBoolean(appId) else appId in PHONE_ZOOM_DEFAULT_ON
    }

    /** Sets an app's switch; null puts it back on its default (as when the app is removed). */
    fun setPhoneZoom(appId: String, on: Boolean?) = synchronized(phoneZoomSet) {
        if (on == null) phoneZoomSet.remove(appId) else phoneZoomSet.put(appId, on)
        try { phoneZoomFile.writeText(phoneZoomSet.toString()) } catch (e: Exception) { Log.w(Lunacy.TAG, "phone zoom not saved: $e") }
    }

    /** For Electron, which scales the pages: the layout width, the defaults and the user's switches. */
    fun phoneZoom(): JSONObject = synchronized(phoneZoomSet) {
        JSONObject().put("width", PHONE_ZOOM_WIDTH).put("defaults", JSONArray(PHONE_ZOOM_DEFAULT_ON.toList())).put("set", JSONObject(phoneZoomSet.toString()))
    }

    /**
     * Screen px per page px for [appId]'s card, held [portrait] or not: the device's scale, or
     * less with Phone Zoom on (a Pre3 held tall: 480 / 640 = 0.75, a 640 x 1011 page).
     */
    fun pageScale(appId: String, portrait: Boolean): Double {
        if (device != org.webosarchive.lunacy.card.DeviceProfile.PRE3 || !phoneZoomOn(appId)) return scale
        val w = if (portrait) minOf(screenWidth, screenHeight) else maxOf(screenWidth, screenHeight)
        val h = (if (portrait) maxOf(screenWidth, screenHeight) else minOf(screenWidth, screenHeight)) - device.positiveSpaceTopPadding
        return minOf(scale, minOf(w, h).toDouble() / PHONE_ZOOM_WIDTH)
    }

    // ---- the screen ----

    /** How the device is held: "right" and "left" are the TouchPad's landscape, "up" and "down" its portrait (Docs/spike-1.md). */
    @Volatile var orientation = "right"
        private set
    val portrait get() = orientation == "up" || orientation == "down"

    /** The device turned (Electron's window did): every page hears it. */
    fun turn(to: String) {
        if (to !in listOf("up", "down", "left", "right") || to == orientation) return
        orientation = to
        // But a popped-out window, which is held its own way.
        main.post { windows.values.filter { it.ownOrientation == null }.forEach { it.setScreenOrientation(to) } }
    }

    /** Something Electron tells one page, by its page id: its type, its focus, the back gesture. */
    fun tell(pageId: String, what: String, value: String) {
        val w = windows[pageId] ?: run { pendingTypes[pageId] = value.takeIf { what == "type" } ?: return; return }
        when (what) {
            "type" -> w.type = value.ifEmpty { null }
            // The card's focus, and with it the app's activity's (WebAppBase::focusActivity).
            "active" -> main.post { w.setStageActive(value == "true"); w.view?.let { lifecycle.stageFocused(it, value == "true") } }
            "focus" -> main.post { w.sendFocus(value == "true") }
            "back" -> main.post { w.sendBack() }
            "keyboardShown" -> main.post { w.keyboardShown(value == "true") }
            "positiveSpace" -> main.post { w.positiveSpace(value) }
            "blur" -> main.post { w.removeInputFocus() }
            // A window of its own (popped out) turned between wide and tall as it was resized.
            "orientation" -> main.post { w.ownOrientation = value; w.setScreenOrientation(value) }
        }
    }

    /** A window's type told before its page made its first native call. */
    val pendingTypes = ConcurrentHashMap<String, String>()

    // ---- banners ----

    private val nextBanner = java.util.concurrent.atomic.AtomicInteger(1)

    /** PalmSystem.addBannerMessage from [w]: its id, as the page gets it back. */
    fun banner(w: DesktopWindow, message: String, params: String, icon: String): Int {
        val id = nextBanner.getAndIncrement()
        events.add(event("banner").put("id", id).put("page", w.pageId).put("appId", w.appId).put("message", message)
            .put("params", params).put("icon", icon))
        return id
    }
    fun removeBanner(id: Int) { events.add(event("banner-remove").put("id", id)) }

    /** Whether an app origin's URL is a file that is there (Main sets it: AppServer's answer). */
    var served: (String) -> Boolean = { false }

    /**
     * A banner's sound, or PalmSystem.playSoundNotification's: what plays is NotificationSound's
     * to say, as on Android; the shell page plays it, through the machine's own sound and at its
     * volume. A file the app names that isn't there plays the class's default, as on Android.
     * Not here: Sounds & Alerts' switches, and Android's own sound for "ringtones".
     */
    fun sound(appId: String, soundClass: String, soundFile: String, durationMs: Int) {
        if (soundClass.isEmpty() && soundFile.isEmpty()) return
        val cls = org.webosarchive.lunacy.card.NotificationSound.classOf(soundClass) ?: return
        val locale = java.util.Locale.getDefault().let { it.language + "_" + it.country.lowercase() }
        val url = if (soundFile.isEmpty()) null else
            org.webosarchive.lunacy.card.NotificationSound.candidates(appId, soundFile, locale, registry.get(appId)?.dir ?: appId)
                .firstOrNull { served(it) }
        if (soundFile.isNotEmpty() && url == null) Log.w(Lunacy.TAG, "[$appId] sound not found: $soundFile")
        events.add(event("sound").put("url", url ?: "")
            .put("name", org.webosarchive.lunacy.card.NotificationSound.defaultFile(cls))
            .put("duration", org.webosarchive.lunacy.card.NotificationSound.duration(cls, durationMs)))
    }
    fun clearBanners(w: DesktopWindow) { events.add(event("banner-clear").put("appId", w.appId)) }
    fun activate(w: DesktopWindow) { events.add(event("activate").put("page", w.pageId)) }
    fun keyboardWanted(w: DesktopWindow, show: Boolean) { events.add(event("keyboard").put("page", w.pageId).put("show", show)) }
    fun keyboardResizes(w: DesktopWindow, resize: Boolean) { events.add(event("keyboard-resizes").put("page", w.pageId).put("resize", resize)) }
    /** [w]'s page called stageReady or drew: its loading card gives way to it. */
    fun pageReady(w: DesktopWindow) { events.add(event("ready").put("page", w.pageId)) }

    // ---- the apps that are running ----

    override val running: Map<String, List<RunningWindow>> get() = lifecycle.apps

    override fun closeWindow(window: RunningWindow) {
        (window as? DesktopView)?.let { v -> main.post { lifecycle.windowClosed(v) } }
    }

    /** Every window of [appId] closed (it is being replaced or removed). On the main thread. */
    private fun closeApp(appId: String) { lifecycle.apps[appId]?.toList()?.forEach { lifecycle.windowClosed(it) } }

    /** A page has gone (a reload, or its view closing): its subscriptions end. */
    fun gone(pageId: String) {
        synchronized(pageViews) { pageViews.remove(pageId) }
        val w = windows.remove(pageId) ?: return
        w.close()
        main.post { w.view?.unbind(w); orphans.values.remove(w) }
    }

    /** The view each page is in, as Electron said as the page started ("" for a page in none it knows). */
    private val pageViews = HashMap<String, String>()

    fun pageIn(pageId: String, viewId: String) = synchronized(pageViews) {
        pageViews[pageId] = viewId
        (pageViews as Object).notifyAll()
    }

    /**
     * The view [pageId] is in, waiting a moment for Electron's word of it: the page's first
     * native call and that word leave Electron by different roads. "" if none came.
     */
    fun viewOfPage(pageId: String): String = synchronized(pageViews) {
        val until = System.currentTimeMillis() + PAGE_VIEW_WAIT_MS
        while (pageId !in pageViews) {
            val left = until - System.currentTimeMillis()
            if (left <= 0) { Log.w(Lunacy.TAG, "a page made a native call and Electron hasn't said which window it is in"); break }
            (pageViews as Object).wait(left)
        }
        pageViews[pageId].orEmpty()
    }

    /**
     * A page made its first native call from the view Electron calls [viewId]: it is that
     * window's page now. A window a page opened can reach here before Electron's word of it
     * ([opened]); its page waits for that. On the main thread.
     */
    fun bindPage(viewId: String, w: DesktopWindow) {
        views[viewId]?.bind(w) ?: run { orphans[viewId] = w }
    }
    private val orphans = HashMap<String, DesktopWindow>()

    /**
     * A page opened a window (window.open), which Electron made a view for, [viewId]: of [appId]
     * (the opener's origin, as Electron reads it), of the [kind] the page named (window.open's
     * "window" attribute), opened by the window in [parentId]. Where it goes is the lifecycle's
     * to say, as on Android (ShellActivity.onWindowOpened).
     */
    fun opened(viewId: String, parentId: String, appId: String, kind: String) {
        // The window exists from here on, for its page's first native call to find (its pid).
        val child = DesktopView(viewId, appId, WindowType.named(kind.ifEmpty { WindowType.CARD.wire }), this)
        views[viewId] = child
        main.post { openedNow(child, parentId) }
    }

    private fun openedNow(child: DesktopView, parentId: String) {
        val appId = child.appId
        orphans.remove(child.id)?.let { child.bind(it) }
        val parent = views[parentId]
        if (parent == null) Log.w(Lunacy.TAG, "$appId: a window opened by one the host doesn't know ($parentId)")
        lifecycle.windowOpened(parent ?: child, child)
    }

    /** Electron's view [viewId] has gone (its page closed itself, or was destroyed): the window has closed. */
    fun viewGone(viewId: String) = main.post {
        val v = views[viewId] ?: return@post
        if (lifecycle.apps[v.appId]?.contains(v) == true) lifecycle.windowClosed(v) else forget(v)
    }

    /** The person closed [viewId]'s card or dashboard (a flick up, the close button): ShellChrome's on Android. */
    fun closedByUser(viewId: String) = main.post { views[viewId]?.let { lifecycle.windowClosed(it) } }

    private fun forget(v: DesktopView) {
        v.place = DesktopView.Place.NONE
        views.remove(v.id, v)
    }

    private fun viewEvent(type: String, v: DesktopView) = event(type).put("view", v.id).put("appId", v.appId)
    private fun titleOf(appId: String) = registry.get(appId)?.title ?: appId

    /** [v]'s page starts loading (DesktopView.load). */
    fun load(v: DesktopView, url: String) { events.add(viewEvent("load", v).put("url", url)) }
    /** Electron closes [v]'s view (DesktopView.close). */
    fun close(v: DesktopView) { events.add(viewEvent("close", v)) }

    // ---- where a window goes (AppLifecycle.Screen) ----

    /** The app that launched each app, until that launch's card is shown: for the group its card joins (CardHost's launchers). */
    private val launchers = HashMap<String, String>()
    override fun launchedBy(appId: String, launchedBy: String?) {
        if (launchedBy != null && launchedBy != appId) launchers[appId] = launchedBy else launchers.remove(appId)
    }
    /** The shell is Luna DE's home ([shellIsHome]): nothing is opened around it. */
    override fun openDirect(appId: String) {}
    override fun bringForward(appId: String) { events.add(event("show").put("id", appId)) }

    override fun showAsCard(w: DesktopView, parent: DesktopView?, placeholder: Boolean, splash: Boolean) {
        // What the shell page needs for the group the card joins (CardHost.showAsCard): whether
        // a window of the app opened it, and which app launched it.
        val launcher = launchers.remove(w.appId)
        if (placeholder) {
            // A headless app's loading card, which its first card window takes: the shell page
            // draws it (it may have, at the tap), and the app's page runs unseen behind it.
            w.place = DesktopView.Place.PLACEHOLDER
            events.add(viewEvent("loading", w).put("title", titleOf(w.appId)).put("launcher", launcher ?: ""))
        } else {
            w.place = DesktopView.Place.CARD
            events.add(viewEvent("card", w).put("title", titleOf(w.appId)).put("fromParent", parent != null).put("launcher", launcher ?: ""))
        }
    }

    override fun cardOpened(parent: DesktopView, child: DesktopView) {
        // A headless app's first card fills the card its launch put up, and the root goes on
        // where a headless root lives (CardHost.cardOpened).
        if (parent.place == DesktopView.Place.PLACEHOLDER) parent.place = DesktopView.Place.HIDDEN
        showAsCard(child, parent)
    }

    override fun hide(w: DesktopView) {
        w.place = DesktopView.Place.HIDDEN
        events.add(viewEvent("hide", w))
    }

    override fun isHidden(w: DesktopView) = w.place == DesktopView.Place.HIDDEN

    private fun panel(w: DesktopView, place: DesktopView.Place, kind: String) {
        w.place = place
        events.add(viewEvent("panel", w).put("kind", kind))
    }
    override fun showDashboard(w: DesktopView) = panel(w, DesktopView.Place.DASHBOARD, "dashboard")
    override fun showAlert(w: DesktopView) = panel(w, DesktopView.Place.ALERT, "popupalert")
    /** Luna DE has no Exhibition: a dock-mode window runs unseen. */
    override fun showInDock(w: DesktopView) = panel(w, DesktopView.Place.DOCK, "dockmode")

    /** Its card or dashboard goes as it is taken off the screen ([detach]), which always follows. */
    override fun windowClosed(w: DesktopView) {}

    override fun detach(w: DesktopView) {
        events.add(viewEvent("remove", w))
        forget(w)
    }

    override val empty get() = views.values.none { it.place == DesktopView.Place.CARD || it.place == DesktopView.Place.PLACEHOLDER }
    /**
     * Nothing to wait for: a page here loads in a process of its own, so its load can't hold up
     * the card's slide as it does in Android's one WebView process. What the person sees is the
     * shell page's to time, and it does (a card reaches the card view before it comes up).
     */
    override val slideMs get() = 0L

    /**
     * A headless app's placeholder goes when the app is up and has opened no card, as CardHost
     * decides it on Android: a moment after it called stageReady, or after a page that never
     * does has been loaded for WindowedWebApp's timeout. On the main thread.
     */
    fun pageSaid(w: DesktopWindow, what: String) {
        val v = w.view ?: return
        if (v.place != DesktopView.Place.PLACEHOLDER) return
        when (what) {
            "stageReady" -> withdrawPlaceholder(v)
            "pageLoaded" -> main.postDelayed({ if (!w.stageReady && v.page === w) withdrawPlaceholder(v) }, AppLifecycle.SHOW_WINDOW_TIMEOUT_MS)
        }
    }

    private fun withdrawPlaceholder(v: DesktopView) = main.postDelayed({
        if (v.place == DesktopView.Place.PLACEHOLDER) {
            Log.i(Lunacy.TAG, "launch ${v.appId}: no card opened; the placeholder goes")
            v.place = DesktopView.Place.HIDDEN
            events.add(viewEvent("withdrawn", v))
        }
    }, AppLifecycle.PLACEHOLDER_GRACE_MS)

    // ---- the platform (AppLifecycle.Host) ----

    override val profile get() = device
    override fun addedLaunchPoint(id: String) =
        if (id.startsWith(AppInfo.LAUNCH_POINT_PREFIX)) addedLaunchPoints.get(id.removePrefix(AppInfo.LAUNCH_POINT_PREFIX)) else null
    /** No tile here is another system's app. */
    override fun openedOutside(id: String) = false

    override fun openedInstead(app: AppInfo): Boolean {
        // On Android the Wi-Fi tile opens Android's Wi-Fi settings. Here it is a developer's switch:
        // the connection lost, and back, so an app can be seen in both states (codepoet, 2026-11-16).
        if (app.androidSettings == "wifi") { toggleNetwork(app.id); return true }
        if (app.androidSettings.isNotEmpty()) { systemBanner(app.id, "${app.title} opens one of Android's settings; there is none on the desktop"); return true }
        return false
    }

    private val nextView = java.util.concurrent.atomic.AtomicInteger(1)

    /** An app's first window. Luna DE draws no emulated card yet: an old app gets the whole screen (Docs/luna-de.md). */
    override fun newWindow(app: AppInfo, emulated: Boolean): DesktopView =
        DesktopView("h${nextView.getAndIncrement()}", app.id, WindowType.CARD, this).also { views[it.id] = it }

    override fun newNativeWindow(app: AppInfo): DesktopView? {
        systemBanner(app.id, "${app.title} is a native app; Luna DE runs web apps")
        return null
    }
    override fun startNative(window: DesktopView) = false
    override fun shellIsHome() = true
    /** The shell page goes to its card view by itself as its last card leaves. */
    override fun onCardView() {}
    override fun windowGone(window: DesktopView) {}
    /** The desktop isn't watched for memory. */
    override fun memoryLow() = false

    // ---- launching ----

    override fun launch(appId: String, params: JSONObject?, placement: Placement, launchedBy: String?) {
        main.post { forgetStale(appId); lifecycle.launch(appId, params, placement, launchedBy) }
    }

    /**
     * A launch from the shell itself (a tap in the launcher, LUNA_DE_OPEN). One that comes as
     * Luna DE starts waits for the boot-time apps, so that it reaches Email or Calendar as a
     * relaunch of the headless app, as on a device (AppLifecycle.launchFromOutside).
     */
    fun launchFromShell(appId: String, params: JSONObject?) = main.post {
        lifecycle.launchFromOutside(booting = true) { forgetStale(appId); lifecycle.launch(appId, params) }
    }

    /**
     * A page that has stopped asking for its scripts has gone without anyone saying so (Electron
     * tells us of every view it destroys; this is for the one it didn't): its window is closed
     * here, so a launch never becomes a relaunch of a window that isn't there.
     */
    private fun forgetStale(appId: String) {
        lifecycle.apps[appId]?.toList()?.forEach { v ->
            if (v.page?.alive == false) { Log.w(Lunacy.TAG, "$appId: a page stopped answering; its window is closed"); lifecycle.windowClosed(v) }
        }
    }

    /**
     * The apps a TouchPad starts at boot (AppLifecycle.LAUNCH_AT_BOOT), headless: Luna DE starts
     * ready, so they start as it does, once the profile account is there.
     */
    private fun startBootTimeApps() = lifecycle.launchBootTimeApps()

    // ---- First Use ----

    /**
     * Luna DE has no First Use (Docs/luna-de.md, "Rule 0": a developer's tool starts ready), so
     * what Lunacy's shell does when First Use ends is done at the first start: the flag that says
     * it ran, and the profile account the accounts service makes (createNovaAccount), which
     * Calendar, Contacts and Memos keep their on-device data on. Then the configurator runs again,
     * for the activity files it holds back until both are done (ShellActivity.afterFirstUse).
     */
    fun finishFirstUse(webosRoot: File, bus: org.webosarchive.lunacy.card.Bus) {
        val prefs = File(webosRoot, "var/luna/preferences")
        val ranFirstUse = File(prefs, "ran-first-use")
        val profileMade = File(prefs, "first-use-profile-created")
        val configureAfter = !ranFirstUse.exists() || !profileMade.exists()
        runCatching { prefs.mkdirs(); ranFirstUse.createNewFile() }
        if (profileMade.exists()) { if (configureAfter) configurator.run(); startBootTimeApps(); return }
        bus.call("", "palm://com.palm.accountservices/createNovaAccount", "{\"createDefaultAccount\":true}", privateBus = true) { r ->
            Log.i(Lunacy.TAG, "firstuse-createDefaultAccount: $r")
        }
        // The service answers before the account is made, and writes the flag as it makes it.
        var waited = 0
        val check = object : Runnable {
            override fun run() {
                if (profileMade.exists()) { configurator.run(); startBootTimeApps(); return }
                waited += 500
                if (waited < 60_000) main.postDelayed(this, 500)
                else { Log.w(Lunacy.TAG, "first use: no profile account after a minute; activities held back until the next start"); startBootTimeApps() }
            }
        }
        main.postDelayed(check, 500)
    }

    // ---- the simulated network ----

    /** Whether the simulated network is up (the Wi-Fi tile, the system menu's Wi-Fi). */
    val online get() = network?.simulatedDown != true

    /** The system menu's Turn on / Turn off WiFi: the simulated network to [on], if it isn't. */
    fun setNetwork(on: Boolean) = main.post { if (on != online) toggleNetwork("") }

    private fun toggleNetwork(appId: String) {
        val net = network ?: return
        val down = !net.simulatedDown
        net.simulateDown(down)
        events.add(event("network").put("online", !down))
        systemBanner(appId, if (down) "Network connection lost (simulated)" else "Network connection restored")
        Log.i(Lunacy.TAG, "network: simulated ${if (down) "down" else "up"}")
    }

    // ---- packages ----

    override fun install(source: String, requester: String, onProgress: (Int) -> Unit, banners: Boolean, onDone: (Packages.Result) -> Unit) {
        val name = runCatching { java.net.URLDecoder.decode(source.substringAfterLast('/'), "UTF-8") }.getOrDefault(source.substringAfterLast('/'))
        if (banners) systemBanner(requester, "Installing $name")
        packages.install(source, progress = onProgress) { r ->
            val before = registry.apps.map { it.id }.toSet()
            appsChanged()
            val app = r.appIds.firstNotNullOfOrNull { registry.inDir(it) } ?: registry.apps.firstOrNull { it.id !in before && it.visible }
            when {
                !banners -> {}
                r.error != null -> systemBanner(requester, "Couldn't install $name: ${r.error}")
                app == null -> systemBanner(requester, "Installed ${r.packageId.ifEmpty { name }}")
                else -> systemBanner(app.id, "${app.title} installed")
            }
            onDone(r)
        }
    }

    /**
     * What a developer hands Luna DE to install (a drop on its window, the file chooser,
     * LUNA_DE_INSTALL): a package, installed as any package is, or an app's own folder (the one
     * holding appinfo.json), which is copied in under its id as `palm-package` and an install
     * would have put it. Dropping the folder again replaces the copy.
     */
    fun installPath(path: String) {
        val f = File(path)
        when {
            f.isFile -> install(f.path)
            f.isDirectory && File(f, "appinfo.json").isFile -> main.post { installFolder(f) }
            f.isDirectory -> systemBanner("", "${f.name} has no appinfo.json: not an app's folder")
            else -> systemBanner("", "Can't read $path")
        }
    }

    private fun installFolder(dir: File) {
        val info = runCatching { JSONObject(File(dir, "appinfo.json").readText()) }.getOrNull()
        val id = info?.optString("id").orEmpty()
        if (id.isEmpty() || !Regex("[A-Za-z0-9._-]+").matches(id) || ".." in id) { systemBanner("", "${dir.name}: its appinfo.json names no app id"); return }
        systemBanner("", "Installing ${info!!.optString("title", id)}")
        Thread({
            val error = runCatching {
                val dest = File(packages.root, "${org.webosarchive.lunacy.core.WebosPaths.APPS}/$id")
                val staging = File(packages.root, "${org.webosarchive.lunacy.core.WebosPaths.APPS}/.$id.incoming")
                staging.deleteRecursively()
                // Not what a developer's folder carries beside the app: its repository and its tools' folders.
                dir.copyRecursively(staging, overwrite = true)
                listOf(".git", "node_modules").forEach { File(staging, it).deleteRecursively() }
                dest.deleteRecursively()
                if (!staging.renameTo(dest)) throw java.io.IOException("can't move it into place")
            }.exceptionOrNull()
            main.post {
                if (error != null) { systemBanner("", "Couldn't install ${dir.name}: ${error.message}"); return@post }
                closeApp(id)
                appsChanged()
                systemBanner(id, "${registry.get(id)?.title ?: id} installed")
            }
        }, "install-folder").start()
    }

    /** The launcher's Remove, for an app that was installed (never a bundled one). */
    fun remove(appId: String) {
        val app = registry.get(appId)
        if (app == null || !app.userInstalled) { systemBanner("", "$appId isn't an installed app"); return }
        removeInstalled(app) { error -> systemBanner("", if (error == null) "${app.title} removed" else "Couldn't remove ${app.title}: $error") }
    }

    override fun removeInstalled(app: AppInfo, done: (String?) -> Unit) {
        closeApp(app.id)
        setPhoneZoom(app.id, null)
        packages.remove(app.id, app.dir) { error ->
            appsChanged()
            done(error)
        }
    }

    override fun appsChanged() {
        val before = registry.launchPoints.associateBy { it.id }
        registry.reload()
        configurator.run()
        jsServices?.reload()
        // A new app's Just Type entries join the lists, a removed app's leave.
        universalSearch?.appsChanged()
        val after = registry.launchPoints.associateBy { it.id }
        for ((id, app) in after) if (id !in before) applicationManager.launchPointChanged(app, "added")
        for ((id, app) in before) if (id !in after) applicationManager.launchPointChanged(app, "removed")
        events.add(event("apps"))
    }

    override fun launchPointsChanged() { events.add(event("apps")) }
    override fun showDock() {}
    override fun forgetIcons(key: String) { events.add(event("apps")) }
    override val pdkAvailable get() = false
    override val freeBytes get() = data.usableSpace

    companion object {
        /** How long a page's first native call waits to hear which window the page is in. */
        const val PAGE_VIEW_WAIT_MS = 2000L
        /** The page width Phone Zoom lays an app out at: Lunacy's default (FormFactor.appLayoutWidth). */
        const val PHONE_ZOOM_WIDTH = 640
        /**
         * On by default for the bundled apps that draw a TouchPad's fixed screen: the same list
         * as AndroidLuna's FixedViewport.DEFAULT_ON, to be one list when that moves into LunaCore.
         */
        val PHONE_ZOOM_DEFAULT_ON = setOf("com.palm.app.clock", "com.palm.calculator", "com.palm.app.notes", "com.palm.app.enyo-findapps")
    }
}

/**
 * Luna DE's webOS root as [org.webosarchive.lunacy.card.ScriptHost]: where JS services run, under
 * a Node of Android's major version ([node]), with Lunacy's host scripts copied beside the root.
 *
 * Package scripts are never run: Luna DE is an app environment, not an operating system
 * (Docs/luna-de.md, open question 2). There is no luna-send for a service's child processes:
 * one that shells out to it gets an error. curl is Lunacy's curl.js, as on Android.
 */
class DesktopRoot(override val root: File, override val hostScripts: File, private val repo: File,
                  private val rom: org.webosarchive.lunacy.card.RomFiles) : org.webosarchive.lunacy.card.ScriptHost {
    override val runsScripts get() = false

    /**
     * LUNA_DE_NODE if it is set, else the Node 12 DesktopLuna/fetch-node.sh fetched, else whatever
     * `node` the machine has, with a warning: Palm's services were written for node 0.4, Lunacy's
     * host runs them on Node 12, and a newer Node isn't what Android runs. (Apple Silicon's is 16:
     * Node 12 has none for it; Docs/luna-de.md, "Deltas, recorded".)
     */
    override val node: File by lazy {
        val named = System.getenv("LUNA_DE_NODE")?.takeIf { it.isNotEmpty() }?.let { File(it) }
        val fetched = File(repo, "DesktopLuna/node/bin/node").let { if (it.isFile) it else File(repo, "DesktopLuna/node/bin/node.exe").takeIf { exe -> exe.isFile } ?: it }
        when {
            named != null -> named
            fetched.isFile -> fetched
            else -> {
                val onPath = onPath("node")
                if (onPath != null) Log.w(Lunacy.TAG, "js services: no Node 12 (run DesktopLuna/fetch-node.sh); using ${onPath.path}, which isn't the version Android runs")
                onPath ?: fetched
            }
        }
    }

    /** [name] (or name.exe on Windows) on the machine's PATH. */
    private fun onPath(name: String): File? = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .flatMap { listOf(File(it, name), File(it, "$name.exe")) }.firstOrNull { it.isFile && it.canExecute() }

    /** A POSIX shell on Windows: one on the PATH, or Git for Windows' in its usual places. */
    private val windowsShell: File? by lazy {
        onPath("sh") ?: listOf(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), System.getenv("LocalAppData")?.let { "$it\\Programs" })
            .filterNotNull().flatMap { listOf(File(it, "Git\\bin\\sh.exe"), File(it, "Git\\usr\\bin\\sh.exe")) }.firstOrNull { it.isFile }
    }

    private var prepared = false

    /** The host scripts copied out, and curl in the root's /usr/bin, once a run. */
    @Synchronized override fun prepare() {
        if (prepared) return
        hostScripts.deleteRecursively()
        rom.copyAssets("lunacy/services", hostScripts)
        for (name in listOf("usr/bin/curl", "usr/bin/curl11")) {
            File(root, name).apply {
                parentFile?.mkdirs(); delete()
                writeText("#!/bin/sh\nexec \"${node.path}\" \"${File(hostScripts, "curl.js").path}\" \"\$@\"\n")
                setExecutable(true, false)
                // Windows runs no "#!" script: the same thing as a command file beside it, which
                // its shell and Node's exec find by the name "curl".
                if (WINDOWS) File(parentFile, "$name.cmd".substringAfterLast('/')).writeText("@\"${node.path}\" \"${File(hostScripts, "curl.js").path}\" %*\r\n")
            }
        }
        prepared = true
    }

    override fun startBus() {}

    /** A webOS path as the file it is under the root; anything else is left as it is. */
    override fun mapPaths(s: String): String =
        if (ROOTS.any { s == "/$it" || s.startsWith("/$it/") }) File(root, s.trimStart('/')).path else s

    override fun environment(caller: String): Map<String, String> = mapOf(
        "PATH" to listOf("usr/sbin", "usr/bin", "sbin", "bin").joinToString(File.pathSeparator) { File(root, it).path } +
            File.pathSeparator + System.getenv("PATH").orEmpty(),
        "HOME" to File(root, "home/root").path,
        "TMPDIR" to File(root, "tmp").path,
        "LUNACY_WEBOS_ROOT" to root.path,
        "LUNACY_WEBOS_DIRS" to ROOTS.joinToString("|"),
        // The root has no /bin/sh of its own here: a service's shell commands run in the machine's.
        // Windows has none of its own: Git for Windows' sh if it is installed, else nothing, and a
        // service's shell command fails as a command that couldn't start (the truth, rule 2).
        "LUNACY_SHELL" to (if (WINDOWS) windowsShell?.path ?: "" else "/bin/sh"),
        // "/" is the webOS root's top for a service, not the machine's own (host.js).
        "LUNACY_ROOT_AT_SLASH" to "1",
    )

    private companion object {
        val WINDOWS = System.getProperty("os.name").orEmpty().startsWith("Windows")
        /** The top folders whose absolute paths are webOS's (WebosRoot.ROOTS on Android). */
        val ROOTS = listOf("usr", "media", "bin", "sbin", "var", "etc", "tmp", "home", "opt", "lib")
    }

}
