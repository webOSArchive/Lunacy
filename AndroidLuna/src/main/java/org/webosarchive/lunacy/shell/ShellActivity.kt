package org.webosarchive.lunacy.shell

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppFiles
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.jsString
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.ApplicationManager
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.Packages
import org.webosarchive.lunacy.card.SystemProperties
import org.webosarchive.lunacy.card.WindowHost
import org.webosarchive.lunacy.card.WindowType
import org.webosarchive.lunacy.card.ActivityManager
import org.webosarchive.lunacy.card.AndroidCalendar
import org.webosarchive.lunacy.card.AndroidContacts
import org.webosarchive.lunacy.card.AndroidNotifications
import org.webosarchive.lunacy.card.AppInstallService
import org.webosarchive.lunacy.card.AudioService
import org.webosarchive.lunacy.card.Configurator
import org.webosarchive.lunacy.card.ConnectionManager
import org.webosarchive.lunacy.card.ContentHandlers
import org.webosarchive.lunacy.card.CookieFlush
import org.webosarchive.lunacy.card.Db8
import org.webosarchive.lunacy.card.DeviceProfile
import org.webosarchive.lunacy.card.DeviceProfileService
import org.webosarchive.lunacy.card.DisplayService
import org.webosarchive.lunacy.card.DockMode
import org.webosarchive.lunacy.card.DownloadManager
import org.webosarchive.lunacy.card.EmulatedCard
import org.webosarchive.lunacy.card.FileIndex
import org.webosarchive.lunacy.card.FixedViewport
import org.webosarchive.lunacy.card.FormFactor
import org.webosarchive.lunacy.card.HiddenApps
import org.webosarchive.lunacy.card.HostNetwork
import org.webosarchive.lunacy.card.HostWallpaper
import org.webosarchive.lunacy.card.Http
import org.webosarchive.lunacy.card.HybridPlugins
import org.webosarchive.lunacy.card.ImageService
import org.webosarchive.lunacy.card.JsServices
import org.webosarchive.lunacy.card.KeyManager
import org.webosarchive.lunacy.card.Keys
import org.webosarchive.lunacy.card.LaunchPoint
import org.webosarchive.lunacy.card.LaunchPoints
import org.webosarchive.lunacy.card.LunacyService
import org.webosarchive.lunacy.card.ShellHooks
import org.webosarchive.lunacy.card.MediaBroadcast
import org.webosarchive.lunacy.card.MediaIndex
import org.webosarchive.lunacy.card.MediaServer
import org.webosarchive.lunacy.card.MusicLibrary
import org.webosarchive.lunacy.card.NativeServices
import org.webosarchive.lunacy.card.NetTools
import org.webosarchive.lunacy.card.NotificationIcons
import org.webosarchive.lunacy.card.Novacom
import org.webosarchive.lunacy.card.PdkRuntime
import org.webosarchive.lunacy.card.PdkWindow
import org.webosarchive.lunacy.card.Placement
import org.webosarchive.lunacy.card.Power
import org.webosarchive.lunacy.card.PowerService
import org.webosarchive.lunacy.card.Ringtones
import org.webosarchive.lunacy.card.SecurityPolicy
import org.webosarchive.lunacy.card.SharedMedia
import org.webosarchive.lunacy.card.SleepWake
import org.webosarchive.lunacy.card.SysLog
import org.webosarchive.lunacy.card.SystemService
import org.webosarchive.lunacy.card.UniversalSearch
import org.webosarchive.lunacy.card.WebosLinks
import org.webosarchive.lunacy.card.WebosRoot

/**
 * The simulated Luna shell: wallpaper, cards, status bar, "Just type", quick launch.
 * Starts as a normal full-screen activity; nothing here assumes it isn't the home launcher.
 */
class ShellActivity : Activity(), WindowHost, CardLayer.Listener {
    override lateinit var server: AppServer
    override val bus = Bus()
    private lateinit var luna: Luna
    private lateinit var registry: AppRegistry
    /** Which app opens which type of file: installed apps' mimeTypes and the system's (ContentHandlers). */
    private lateinit var handlers: ContentHandlers
    /** Launch points apps have added (applicationManager/addLaunchPoint). */
    private lateinit var addedLaunchPoints: LaunchPoints
    private lateinit var packages: Packages
    private lateinit var jsServices: JsServices
    /** The TouchPad's native system services (mail, the file cache), from the webOS root. */
    private lateinit var nativeServices: NativeServices
    /** The app that launched each app, until that launch's card is shown (showAsCard). */
    private val launchers = HashMap<String, String>()
    /** powerd's part: timers that wake the device, and locks that keep it awake. */
    private val powerLazy = lazy { Power(applicationContext) }
    private val power by powerLazy
    /** Every sign of having been asleep or frozen, answered as a webOS resume. */
    private val sleepWakeLazy = lazy { SleepWake(this, power) }
    private val sleepWake by sleepWakeLazy
    private val powerService by lazy { PowerService(this, power) }
    private val activityManagerLazy = lazy { ActivityManager(this, power) }
    private val activityManager by activityManagerLazy
    /** The shell's db8s and connection manager, closed with it ([onDestroy]). */
    private val closeOnDestroy = ArrayList<() -> Unit>()
    private lateinit var webos: WebosRoot
    private lateinit var mediaServer: MediaServer
    private lateinit var configurator: Configurator
    private lateinit var systemService: SystemService
    private lateinit var securityPolicy: SecurityPolicy
    /** Android's screen-lock setup is open for a setDevicePasscode: what to do when the person is back. */
    private var lockReturn: (() -> Unit)? = null
    private var lockLeft = false
    /** Android's credential confirmation is open for a matchDevicePasscode. */
    private var confirmReturn: ((Boolean) -> Unit)? = null
    private lateinit var audio: AudioService
    private lateinit var displayService: DisplayService
    /** The wallpaper view, reloaded when the preference changes. */
    private lateinit var wallpaperView: ImageView
    /** /media/cryptofs/apps: installed packages first, then the apps bundled in the APK. */
    private lateinit var files: AppFiles
    /**
     * The webOS device Lunacy answers as, for every app-visible surface: the layout's, so it
     * follows a foldable folded or opened (reshell). A window keeps the one it started with.
     */
    private val profile get() = DeviceProfile.forScreen(this)
    private var pdkRuntimeOf: Pair<DeviceProfile, PdkRuntime>? = null
    /**
     * The PDK runtime (Docs/pdk.md): present in the 32-bit build, absent in the 64-bit one.
     * One per profile: a native app started after the layout changed sees the new device's
     * screen, and one already running keeps its own.
     */
    private val pdkRuntime: PdkRuntime get() {
        val d = profile
        pdkRuntimeOf?.takeIf { it.first == d }?.let { return it.second }
        // A PDK app saw its device's own screen, whatever way it was held: the TouchPad's
        // 1024 x 768 landscape panel, the Pre3's 480 x 800 portrait one. The card scales it.
        val (w, h, dpi) = if (d == DeviceProfile.PRE3) Triple(480, 800, 260) else Triple(1024, 768, 132)
        return PdkRuntime(this, java.io.File(filesDir, "cryptofs/apps/usr/palm/applications"),
            w, h, dpi, d.platformVersion, d.modelNameAscii, DeviceProfile.nduid(this), webos)
            .also { pdkRuntimeOf = d to it }
    }
    private var androidAppsAt: AndroidApps? = null
    /** Proof of concept: Android's own apps in the launcher, for Lunacy as the home screen. Its icons are at the shell's scale. */
    private val androidApps: AndroidApps get() =
        androidAppsAt ?: AndroidApps(this, files, luna.px(Launcher.Params.ICON.toInt())).also { androidAppsAt = it }
    private var androidById: Map<String, AppInfo> = emptyMap()
    /** webOS apps pinned to Android's own home screen, when Lunacy isn't it. */
    private val homeScreenPins by lazy { HomeScreenPins(this) }
    /** Shortcuts other apps pinned to the home screen, among Android's tiles. */
    private val pinned by lazy { PinnedShortcuts(this, files, luna.px(Launcher.Params.ICON.toInt())) }
    /** Whether the shell is on screen (between onStart and onStop): a Home press then is Lunacy's own. */
    private var started = false
    override fun onStart() {
        super.onStart(); started = true; sleepWake.check("shell")
        coveredCard?.let { w -> if (cards.maximized?.window == w) w.setStageActive(true) }
        coveredCard = null
    }
    override fun onStop() {
        // Another Android app has come over the shell with the screen on (Android's own
        // settings, from Screen & Lock): the card on show has left the screen, as a card
        // minimized to the card view had on webOS, and hears so; it is activated again on the
        // way back, so a page that reads its state on activation finds what changed there.
        // The screen going off leaves it as it was.
        val interactive = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive
        // An Android app opened over Exhibition (from a dashboard, say) is a card arriving, and
        // a card arriving ended dock mode (SystemUiController::cardWindowAdded). The shell is
        // already out of sight, so the screen saver's end doesn't send it there again.
        if (interactive) exhibitions.cardArrivedInExhibition()
        if (interactive && ::cards.isInitialized) cards.maximized?.window?.let { it.setStageActive(false); coveredCard = it }
        // A launch that Android answers (a settings shortcut, an Android app) covers the shell
        // while the launcher is still sliding away, and a stopped window's animation doesn't
        // finish: the launcher came back on screen but closed, deaf to taps. Finish it now.
        if (::launcher.isInitialized && !launcherOpen && launcher.visibility == View.VISIBLE) {
            launcher.animate().cancel()
            launcher.translationY = launcher.height.toFloat()
            launcher.visibility = View.INVISIBLE; launcher.cancelLaunchFeedback(); exitEditMode()
        }
        started = false; super.onStop()
    }
    /** The maximized card that another Android app covered, to be activated again ([onStop]). */
    private var coveredCard: AppWindow? = null

    /** Every app the launcher could show: webOS apps, the launch points they added, then Android's and the shortcuts pinned to it. */
    private fun allLaunchPoints(): List<AppInfo> {
        val android = androidApps.list() + pinned.list()
        androidById = android.associateBy { it.id }
        return registry.launchPoints + addedTiles() + android
    }

    /** The apps the user has hidden with Software Manager: out of the launcher, the dock and Just Type. */
    private val hiddenApps by lazy {
        HiddenApps(this).also { h -> h.onChanged = { setLauncherApps(); showDock() } }
    }

    /** What the launcher shows: [allLaunchPoints] less the ones the user has hidden. */
    private fun launchPoints(): List<AppInfo> = allLaunchPoints().filter { it.id !in hiddenApps }

    /** The launcher's apps, again, and Software Manager hears it. */
    private fun setLauncherApps() {
        launcher.hidden = hiddenApps.all()
        launcher.setApps(launchPoints())
        lunacyService?.launcherAppsChanged()
    }
    private var lunacyService: LunacyService? = null
    private lateinit var applicationManager: ApplicationManager
    /** What the notifications ask of the shell. */
    private val notificationShell = object : NotificationHost.Host {
        override val luna get() = this@ShellActivity.luna
        override val registry get() = this@ShellActivity.registry
        override val server get() = this@ShellActivity.server
        override val statusBar get() = this@ShellActivity.statusBar
        override val sounds get() = this@ShellActivity.sounds
        override val blinkNotifications get() = ::systemService.isInitialized && systemService.get("BlinkNotifications") == true
        override fun launch(appId: String, params: JSONObject?) = this@ShellActivity.launch(appId, params)
        override fun closeWindow(window: AppWindow) = this@ShellActivity.closeWindow(window)
        override fun closeMenu() = this@ShellActivity.closeMenu()
        override fun toggleMenu() = this@ShellActivity.toggleMenu()
    }
    /** What Exhibition asks of the shell: its chrome, read afresh, since a reshell rebuilds it. */
    private val exhibitionHost = object : ExhibitionController.Host {
        override val luna get() = this@ShellActivity.luna
        override val registry get() = this@ShellActivity.registry
        override val dockMode get() = this@ShellActivity.dockMode
        override val statusBar get() = this@ShellActivity.statusBar
        override val cards get() = this@ShellActivity.cards
        override val menuScrim get() = this@ShellActivity.menuScrim
        override val inFront get() = this@ShellActivity.inFront
        override val launcherOpen get() = this@ShellActivity.launcherOpen
        override val notificationsMenuOpen get() = notifications.menu.isOpen
        override fun launchForDock(appId: String, params: JSONObject) = launch(appId, params, Placement.DOCK)
        override fun closeWindow(window: AppWindow) = onWindowClosed(window)
        override fun closeMenu() = this@ShellActivity.closeMenu()
        override fun closeLauncher() = this@ShellActivity.closeLauncher()
        override fun hideKeyboard() = this@ShellActivity.hideKeyboard()
        override fun releaseOrientation() = applyOrientation(null)
        override fun holdScreen() = this@ShellActivity.holdScreen()
        override fun showCardsAgain() { if (cards.maximized != null) onMaximized(cards.maximized!!) else onCardView() }
        override fun showChrome(exhibiting: Boolean) {
            fade(justType, !exhibiting && cards.maximized == null && !launcherOpen)
            showDock(!exhibiting && cards.maximized == null)
        }
    }
    /** What the application manager asks of the shell. */
    private val appManagerShell = object : ApplicationManager.Shell {
        override fun launch(appId: String, params: JSONObject?, placement: Placement, launchedBy: String?) =
            this@ShellActivity.launch(appId, params, placement, launchedBy)
        override fun install(source: String, requester: String, onProgress: (Int) -> Unit, banners: Boolean, onDone: (Packages.Result) -> Unit) =
            this@ShellActivity.install(source, requester, onProgress, banners, onDone)
        override val pdkAvailable get() = pdkRuntime.available
        override val running: Map<String, List<AppWindow>> get() = this@ShellActivity.running
        override fun closeWindow(window: AppWindow) = onWindowClosed(window)
        override fun appsChanged() = this@ShellActivity.appsChanged()
        override fun launchPointsChanged() { setLauncherApps(); showDock() }
        override fun showDock() = this@ShellActivity.showDock()
        override fun forgetIcons(key: String) = luna.forgetIcons(key)
        override fun removeInstalled(app: AppInfo, done: (String?) -> Unit) = this@ShellActivity.removeInstalled(app, done)
    }

    /** The tiles of the added launch points whose app is installed, by tile id. */
    private var addedById: Map<String, AppInfo> = emptyMap()

    private fun addedTiles(): List<AppInfo> {
        val tiles = addedLaunchPoints.all.mapNotNull { lp ->
            registry.get(lp.appId)?.let { app -> AppInfo.forLaunchPoint(app, lp) { addedLaunchPoints.open(lp.icon) } }
        }
        addedById = tiles.associateBy { it.id }
        return tiles
    }

    /** An app by id, webOS or Android, or an added launch point: what the dock and Just Type can hold. */
    private fun appById(id: String): AppInfo? = registry.get(id) ?: addedById[id] ?: androidById[id]

    /**
     * Android's packages coming, going or changing (an update, a component enabled or
     * disabled): the launcher, dock and Just Type follow, with the package's icons and
     * labels drawn again.
     */
    private var watchingPackages = false
    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            // An update removes the old package and adds the new; wait for the second.
            if (intent.action == android.content.Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(android.content.Intent.EXTRA_REPLACING, false)) return
            if (pkg == packageName) return
            androidApps.forget(pkg)
            AndroidNotifications.forget(pkg)
            androidById.keys.filter { it.startsWith("${AndroidApps.PREFIX}$pkg/") }.forEach { luna.forgetIcons(it) }
            setLauncherApps()
            showDock()
        }
    }
    /** A shortcut was pinned or unpinned, or its app changed it: the tiles follow, with new icons. */
    private fun shortcutsChanged() {
        androidById.keys.filter { it.startsWith(PinnedShortcuts.PREFIX) }.forEach { luna.forgetIcons(it) }
        setLauncherApps()
        showDock()
    }

    /** From Android 8 an app can change or withdraw the shortcuts it pinned; LauncherApps says so. */
    private var shortcutWatch: Any? = null
    @android.annotation.TargetApi(26)
    private fun watchShortcuts() {
        val cb = object : android.content.pm.LauncherApps.Callback() {
            override fun onPackageRemoved(p: String, u: android.os.UserHandle) {}
            override fun onPackageAdded(p: String, u: android.os.UserHandle) {}
            override fun onPackageChanged(p: String, u: android.os.UserHandle) {}
            override fun onPackagesAvailable(p: Array<out String>, u: android.os.UserHandle, r: Boolean) {}
            override fun onPackagesUnavailable(p: Array<out String>, u: android.os.UserHandle, r: Boolean) {}
            override fun onShortcutsChanged(p: String, s: MutableList<android.content.pm.ShortcutInfo>, u: android.os.UserHandle) {
                pinned.forget(p); shortcutsChanged()
            }
        }
        runCatching { (getSystemService(LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps).registerCallback(cb) }.onSuccess { shortcutWatch = cb }
    }

    private lateinit var statusBar: StatusBar
    private lateinit var cards: CardLayer
    private lateinit var justType: JustType
    private lateinit var justTypePanel: JustTypePanel
    /** com.palm.universalsearch: what Just Type offers besides apps, and its settings. */
    private lateinit var universalSearch: UniversalSearch
    private lateinit var quickLaunch: QuickLaunch
    private lateinit var launcher: Launcher
    private lateinit var tabDialog: TabDialog
    private lateinit var groupOverlay: GroupOverlay
    private var launcherOpen = false
    /** Banners, dashboards and alerts (NotificationHost). */
    private val notificationHost = NotificationHost(this, notificationShell)
    private val notifications: Notifications get() = notificationHost.notifications
    private val notificationsAtBottom: Boolean get() = notificationHost.notificationsAtBottom
    private val area: NotificationArea? get() = notificationHost.area
    private lateinit var systemMenu: SystemMenu
    private lateinit var systemControls: SystemControls
    private lateinit var dockMode: DockMode
    /** Exhibition and its dock (ExhibitionController). */
    private val exhibitions = ExhibitionController(this, exhibitionHost)
    /** Whether the shell is the activity in front, which decides where a screen saver's Exhibition goes back to. */
    private var inFront = false
    /** Catches taps outside the dashboard drop-down, which close it. */
    private lateinit var menuScrim: View

    /** Headless root windows of noWindow apps: attached so their scripts run, never shown. */
    private lateinit var hidden: FrameLayout
    /** A debuggable build (the debug build type), which keeps the adb development extras. */
    private val devBuild by lazy { applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    /** Running apps: app id to all its windows, root first. */
    private val running = LinkedHashMap<String, MutableList<AppWindow>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One shell per process, as one LunaSysMgr per device. Android makes a second one
        // beside the first when the shell is started outside its task: from another launcher's
        // icon while Lunacy is the home app, or a home intent from adb (a home task and a
        // standard one, which singleTask keeps apart). Two shells were two systems, each running
        // every activity on its own bus and db8, so syncs ran twice. The newcomer hands its
        // intent to the shell that is up, with its cards, and goes before building anything.
        live?.get()?.takeIf { it !== this && !it.isDestroyed && !it.isFinishing }?.let { up ->
            handedOff = true
            up.takeOver(intent)
            finish()
            return
        }
        live = java.lang.ref.WeakReference(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // chrome://inspect is a debug build's only: a release one leaves its cards closed to it.
        WebView.setWebContentsDebuggingEnabled(devBuild)
        Bus.strict = devBuild
        FormFactor.shown = FormFactor.decide(this)
        luna = Luna(this, FormFactor.shown == FormFactor.Kind.PHONE)
        val installed = java.io.File(filesDir, "cryptofs/apps")
        // The webOS root filesystem services and package scripts see as /. Its ROM is laid down
        // (again after an update) off the main thread, below; a service's first call and a
        // package script wait for it (WebosRoot.prepare is synchronized).
        webos = WebosRoot(this, bus, installed).apply { lunaSend.start() }
        // Its /etc/resolv.conf and CA bundle, from Android's network and trust store.
        HostNetwork(this, webos.root).start()
        // What webOS's syslogd kept, and novacomd's device side: the SDK's tools over adb.
        SysLog.start(webos.root)
        Novacom(webos) { DeviceProfile.nduid(this) }.start()
        files = AppFiles(assets, installed, webos.root, java.io.File(applicationInfo.sourceDir))
        jsServices = JsServices(bus, files.root, webos)
        nativeServices = NativeServices(bus, webos) { pdkRuntime }
        server = AppServer(assets, files, jsServices.root, java.io.File(filesDir, "framework-art"))
        mediaServer = MediaServer(jsServices.root)
        registry = AppRegistry(files)
        handlers = ContentHandlers(registry, webos.root)
        addedLaunchPoints = LaunchPoints(webos.root, files)
        Http.init(assets)
        packages = Packages(files.root, java.io.File(cacheDir, "packages"), webos)
        // Native services already in the webOS root are on the bus from the start, before the
        // configurator sends the file cache its types, as ls-hubd knew every service file at
        // boot and started one on its first call; the root's update below registers what it adds.
        nativeServices.reload()
        registerServices()
        jsServices.reload()
        Thread({
            val t = System.currentTimeMillis()
            webos.prepare()
            files.warm()
            Log.i(AppServer.TAG, "webOS root ready in ${System.currentTimeMillis() - t} ms")
            removeOutdatedPackages()
            // What the ROM brought: its services and their db8 kinds, and any system apps.
            // Activities run once the root's services are on the bus, as webOS's waited for boot.
            runOnUiThread { jsServices.reload(); nativeServices.reload(); appsChanged(); activityManager.enable(); powerService.enable(); afterFirstUse(); androidNotificationsChanged(); FontWarmer(this, server).warmWhenIdle() }
        }, "webos-root").start()

        sounds.preload()
        registerReceiver(packageReceiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        watchingPackages = true
        buildShell()
        // The splash was the window's background (Theme.Lunacy.Splash), which is what Android
        // painted while this was all being set up. The shell covers it now, so let the logo go
        // rather than hold the bitmap behind an opaque wallpaper for the rest of the session.
        shellRoot?.post { window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)) }
        reportedOrientation = screenOrientation()
        (getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
            .registerDisplayListener(displayListener, android.os.Handler(android.os.Looper.getMainLooper()))
        seedMediaInternal()
        onCardView()
        goImmersive()

        // First Use, as a webOS device put it in front of a new owner: once, on the first
        // start, it asks for Android's permissions (LunacyService). After it has run, the
        // shell asks for storage itself if it is still missing, as it did before First Use.
        if (!LunacyService.firstUseDone(this)) {
            cards.post { launch(LunacyService.FIRST_USE_APP) }
        } else {
            askForStorage()
        }

        handleIntent(intent)
    }

    /** The shell's own root view, under the wallpaper: what [buildShell] lays out and [reshell] replaces. */
    private var shellRoot: FrameLayout? = null

    /**
     * The shell's views, at [luna]'s scale and in its layout: wallpaper, cards, launcher, pill,
     * dock, notifications, menus, status bar. Built once at startup, and again by [reshell]
     * when the layout changes; everything else - the services, the bus, the apps' windows -
     * is the activity's own and outlives it.
     */
    private fun buildShell() {
        // The dock draws an icon being dragged out of it above its own bounds.
        val root = FrameLayout(this).apply { clipChildren = false }
        wallpaperView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        showWallpaper()
        val screen = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen.addView(wallpaperView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        screen.addView(root, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        hidden = FrameLayout(this)
        root.addView(hidden, FrameLayout.LayoutParams(1, 1))

        buildCards(root)
        buildLauncher(root)
        buildJustType(root)
        buildQuickLaunch(root)
        buildBarAndMenus(screen, root)

        fitCutout(screen, root)
        setContentView(screen)
        screen.requestApplyInsets()
        watchKeyboard(root)
        statusBar.onTitleTap = { if (exhibitions.exhibitionOn) exhibitions.toggleExhibitionMenu() else toggleAppMenu() }
        shellRoot = root
    }

    /** The card layer, the whole screen, so that a full-screen card can be laid out under the status bar. */
    private fun buildCards(root: FrameLayout) {
        // The whole screen, so that a full-screen card can be laid out under the status bar;
        // every other card starts below it (CardLayer.inset).
        cards = CardLayer(this, luna, this).apply { inset = luna.px(StatusBar.HEIGHT); feedback = { sounds.feedback(it) }
            // A phone's card view is the Pre3's: its own card ratios (Docs/pre3.md).
            phone = luna.phone
            // The TouchPad's landscape turned end for end, which Lunacy calls "left".
            upsideDown = { screenOrientation() == "left" } }
        root.addView(cards, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** The launcher, with its groups' panel and its tab dialog. */
    private fun buildLauncher(root: FrameLayout) {
        // Overlays, bottom to top as in LunaCE's OverlayWindowManager: launcher, pill, dock.
        // The icon glows first; the launch follows a frame later, so the glow is seen.
        launcher = Launcher(this, luna) { app -> launcher.postDelayed({ launch(app.id); closeLauncher() }, LAUNCH_DELAY_MS) }
        PinnedShortcuts.onChanged = ::shortcutsChanged
        NotificationIcons.onChanged = ::notificationIconsChanged
        AndroidNotifications.onChanged = ::androidNotificationsChanged
        if (android.os.Build.VERSION.SDK_INT >= 26) watchShortcuts()
        launcher.onRemove = { app -> remove(app) }
        launcher.appTitle = { id -> registry.get(id)?.title }
        setLauncherApps()
        launcher.dockHeight = luna.px(QuickLaunch.barHeight(luna.phone)).toFloat()
        launcher.visibility = View.INVISIBLE
        root.addView(launcher, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        // [LunaCE] Launcher groups: the panel a group opens into.
        groupOverlay = GroupOverlay(this, luna)
        root.addView(groupOverlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        launcher.onOpenGroup = { g, from -> groupOverlay.show(g, from) }
        groupOverlay.onLaunch = { app -> launcher.postDelayed({ launch(app.id); closeLauncher() }, LAUNCH_DELAY_MS) }
        // [LunaCE] The group's glow stays while one of its members launches, and goes when
        // the panel is simply dismissed.
        groupOverlay.onDismissed = { g, launched -> if (launched) launcher.showLaunchFeedback(g.id) else launcher.cancelLaunchFeedback() }
        groupOverlay.onPopOut = { g, app -> launcher.popOut(g, app) }
        groupOverlay.onRenamed = { g -> launcher.groupChanged(g) }
        // [LunaCE] Renaming, adding and deleting launcher tabs.
        tabDialog = TabDialog(this, luna)
        root.addView(tabDialog, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        launcher.onRenameTab = { i, name, deletable ->
            tabDialog.show("Rename Tab", name, deletable, commitOnTapAway = true) { newName, delete ->
                if (newName != null) launcher.renameTab(i, newName)
                if (delete) launcher.deleteTab(i)
            }
        }
        launcher.onAddTab = {
            tabDialog.show("New Tab", "New Tab", deletable = false, commitOnTapAway = false) { newName, _ ->
                if (newName != null) launcher.addTab(newName)
            }
        }
    }

    /** The Just Type pill and its panel. */
    private fun buildJustType(root: FrameLayout) {
        justType = JustType(this, luna)
        // The pill takes text now, so something else has to hold focus first, or the shell
        // opens with a cursor blinking in it.
        root.isFocusableInTouchMode = true
        // The pill is 3/4 of the short side: a TouchPad's 768 px on a tablet, whatever the
        // screen (the shell keeps the TouchPad's proportions there); on a phone, the screen's
        // own, as the Pre3's shell laid it out on its 480 px (Docs/pre3.md).
        val shortSide = if (luna.phone) {
            realMetrics().let { minOf(it.widthPixels, it.heightPixels) }
        } else (luna.density * 768).toInt()
        root.addView(justType, FrameLayout.LayoutParams((shortSide * JustType.WIDTH_OF_SHORT_SIDE).toInt(), luna.px(JustType.HEIGHT)).apply {
            topMargin = luna.px(StatusBar.HEIGHT + JustType.TOP_GAP); gravity = android.view.Gravity.CENTER_HORIZONTAL
        })

        // Just Type, under the status bar, over the cards, launcher, pill and dock.
        justTypePanel = JustTypePanel(this, luna)
        root.addView(justTypePanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        justType.setOnClickListener { openJustType() }
        justTypePanel.onLaunch = { app -> closeJustType(); launch(app.id) }
        // Just Type's other choices go over the bus as its own calls did, as com.palm.launcher.
        justTypePanel.onLaunchApp = { id, params ->
            closeJustType()
            bus.call(JUST_TYPE, "palm://com.palm.applicationManager/launch", JSONObject().put("id", id).put("params", params).toString()) { r ->
                Log.i(AppServer.TAG, "just type: launch $id: $r")
            }
        }
        justTypePanel.onOpenTarget = { target ->
            closeJustType()
            bus.call(JUST_TYPE, "palm://com.palm.applicationManager/open", JSONObject().put("target", target).toString()) { r ->
                Log.i(AppServer.TAG, "just type: open: $r")
            }
        }
        justTypePanel.db = { method, params, reply ->
            bus.call(JUST_TYPE, "palm://com.palm.db/$method", params.toString()) { r ->
                reply(runCatching { JSONObject(r) }.getOrDefault(JSONObject().put("returnValue", false)))
            }
        }
        justTypePanel.appFor = { id -> registry.get(id) }
        if (::universalSearch.isInitialized) justTypePanel.search = universalSearch
        justTypePanel.onSearch = { url ->
            closeJustType()
            if (registry.get(ApplicationManager.BROWSER) != null) launch(ApplicationManager.BROWSER, JSONObject().put("target", url))
            else WebosLinks.intentFor("", null, url)?.let { WebosLinks.open(this, it) }
        }
    }

    /** The quick-launch dock along the bottom. */
    private fun buildQuickLaunch(root: FrameLayout) {
        quickLaunch = QuickLaunch(this, luna, onLaunch = { app -> quickLaunch.postDelayed({ launch(app.id) }, LAUNCH_DELAY_MS) }, onLauncher = { toggleLauncher() })
        // By position in the dock as drawn, which leaves out apps since removed.
        quickLaunch.onRemoveItem = { i -> setDock(shownDock().toMutableList().apply { removeAt(i) }) }
        quickLaunch.onMoveItem = { from, to -> setDock(shownDock().toMutableList().apply { add(to, removeAt(from)) }) }
        launcher.onDropOnDock = { app, x -> dropOnDock(app, x) }
        launcher.onEditModeChanged = { on -> quickLaunch.editing = on }
        showDock()
        root.addView(quickLaunch, FrameLayout.LayoutParams(MATCH_PARENT, luna.px(QuickLaunch.barHeight(luna.phone))).apply { gravity = android.view.Gravity.BOTTOM })
    }

    /**
     * Everything over the cards and overlays, bottom to top: Exhibition's dock, the alerts,
     * the menus' scrim, the notifications, the system menu, the status bar, Exhibition's menu.
     */
    private fun buildBarAndMenus(screen: FrameLayout, root: FrameLayout) {
        // Notifications layer (reference §1.1): popup alerts, then the dashboard drop-down, under the status bar.
        statusBar = StatusBar(this, luna)
        statusBar.onBatteryFull = { sounds.batteryFull() }
        val popups = PopupLayer(this, luna)
        root.addView(exhibitions.buildDock(), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        root.addView(popups, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        menuScrim = View(this).apply {
            visibility = View.GONE
            setOnClickListener { closeMenu(); exhibitions.closeExhibitionMenu() }
        }
        root.addView(menuScrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        notificationHost.build(screen, root, popups)
        // The system menu, right-aligned under the bar, its edge 11 px past the screen's.
        systemControls = SystemControls(this, ::askPermissions, ::openAndroidPanel) { key, value -> displayService.write(key, value) }
        systemMenu = SystemMenu(this, luna, systemControls)
        systemControls.onChange = { systemMenu.refresh() }
        // WiFiElement: a network the menu turned on or joined came up; the menu goes a second later.
        systemControls.onConnected = { closeSystemMenuIn(1000) }
        systemMenu.onCloseRequest = { closeSystemMenuIn(it) }
        systemControls.start()
        root.addView(systemMenu, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = luna.px(StatusBar.HEIGHT); gravity = android.view.Gravity.TOP or android.view.Gravity.END
        })
        systemMenu.translationX = luna.px(SystemMenu.EDGE_OFFSET).toFloat()
        // SystemMenu::slotMenuBrightnessChanged: 0…1 along the slider is 1…100 %.
        systemMenu.onBrightness = { v, done -> setMenuBrightness(Math.round(v * 99) + 1, done) }
        statusBar.onSystemTap = { toggleSystemMenu() }
        statusBar.onSystemInfoChanged = { systemMenu.batteryPercent = statusBar.batteryPercent }
        root.addView(statusBar, FrameLayout.LayoutParams(MATCH_PARENT, luna.px(StatusBar.HEIGHT)))

        root.addView(exhibitions.buildMenu(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = luna.px(StatusBar.HEIGHT); gravity = android.view.Gravity.TOP or android.view.Gravity.START
        })
    }

    override fun onDestroy() {
        if (handedOff) { super.onDestroy(); return }
        if (::systemControls.isInitialized) systemControls.stop()
        if (live?.get() === this) live = null
        (getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
            .unregisterDisplayListener(displayListener)
        exhibitions.stop()
        if (watchingPackages) { unregisterReceiver(packageReceiver); watchingPackages = false }
        if (PinnedShortcuts.onChanged == ::shortcutsChanged) PinnedShortcuts.onChanged = null
        if (NotificationIcons.onChanged == ::notificationIconsChanged) NotificationIcons.onChanged = null
        if (AndroidNotifications.onChanged == ::androidNotificationsChanged) AndroidNotifications.onChanged = null
        shortcutWatch?.let { if (android.os.Build.VERSION.SDK_INT >= 26) (getSystemService(LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps).unregisterCallback(it as android.content.pm.LauncherApps.Callback) }
        if (watchingSystem) { unregisterReceiver(systemReceiver); watchingSystem = false }
        // Everything that would outlive the activity: the apps' pages (each a renderer in this
        // process), the services' Node processes, the luna-send socket and the players. Android
        // re-creates the activity for a font-size or language change, and without this the old
        // one's cards ran on, unreachable, beside the new one's.
        pendingLoads.clear()
        for (w in running.values.flatten()) { (w.parent as? android.view.ViewGroup)?.removeView(w); w.destroy() }
        running.clear()
        if (::jsServices.isInitialized) jsServices.stopAll()
        if (::nativeServices.isInitialized) nativeServices.stopAll()
        if (::webos.isInitialized) webos.lunaSend.stop()
        // And the system under them: the bus, so nothing pending in this shell reaches a
        // service again, then the activity manager's schedules, powerd's timers, the sleep
        // watcher and db8. Left running, a re-created shell's predecessors each ran every
        // activity again on their own bus (four shells' webCal syncs added each event four
        // times, Galaxy Z Fold3).
        bus.close()
        if (activityManagerLazy.isInitialized()) activityManager.stop()
        if (sleepWakeLazy.isInitialized()) sleepWake.stop()
        if (powerLazy.isInitialized()) power.stop()
        // A shell that has gone runs nothing it had pending.
        shellHandler.removeCallbacksAndMessages(null)
        closeOnDestroy.forEach { it() }
        if (::audio.isInitialized) audio.close()
        sounds.release()
        super.onDestroy()
    }

    /**
     * What the system tells subscribers about: the time, the zone or the date changing
     * (systemservice's time/getSystemTime), and the screen going on or off
     * (display/control/status). Registered once the services exist (registerServices).
     */
    private var watchingSystem = false
    private val systemReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            when (intent.action) {
                android.content.Intent.ACTION_TIME_CHANGED, android.content.Intent.ACTION_TIMEZONE_CHANGED,
                android.content.Intent.ACTION_DATE_CHANGED -> systemService.timeChanged()
                android.content.Intent.ACTION_SCREEN_ON, android.content.Intent.ACTION_SCREEN_OFF -> {
                    displayService.screenChanged()
                    // The owner is looking: the light has done its job.
                    if (intent.action == android.content.Intent.ACTION_SCREEN_ON) notificationHost.clearLight()
                }
            }
        }
    }

    /** Lunacy is single-task: later launch requests (e.g. from adb or, later, Android intents) arrive here. */
    /** Set when this shell found another already up and handed it its intent ([onCreate]). */
    private var handedOff = false

    /** A second shell's intent: this shell comes to the front and takes it as its own. */
    private fun takeOver(intent: android.content.Intent) {
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).moveTaskToFront(taskId, 0)
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // As the home screen: Home while the shell is up is Lunacy's own Home button. From
        // an Android app it only brings the shell back, as it was.
        if (intent.action == android.content.Intent.ACTION_MAIN && intent.hasCategory(android.content.Intent.CATEGORY_HOME) && started) homePressed()
        handleIntent(intent)
    }

    /**
     * Android's screen saver asking for Exhibition (ExhibitionDream), and extras for
     * development over adb: launch <appid> [params <json>], install <url or path>, layout
     * auto|phone|tablet (the FormFactor setting; the shell is rebuilt, reshell), launcher (opens it).
     * The shell is exported to every app on the device, so the ones that install a package
     * or run a command - install, sh, rawsh - are a debug build's only ([devBuild]).
     */
    private fun handleIntent(intent: android.content.Intent) {
        if (devBuild) intent.takeString("install")?.let { install(it) }
        intent.getStringExtra("layout")?.let { v ->
            if (FormFactor.setSetting(this, v)) { intent.removeExtra("layout"); if (::cards.isInitialized) reshell() }
            else Log.w(AppServer.TAG, "layout: $v is not auto, phone or tablet")
        }
        if (intent.hasExtra("launcher")) { intent.removeExtra("launcher"); if (started && !launcherOpen) openLauncher() }
        // `--es sh '<command>'`: runs it in the webOS root from this process, with its output
        // in the log, for seeing what a package's script sees under the app's own sandbox.
        if (devBuild) intent.getStringExtra("sh")?.let { cmd -> intent.removeExtra("sh"); packages.shell(cmd) }
        // `--es rawsh '<command>'`: the same through Android's own shell with no environment
        // of Lunacy's, to tell the sandbox's doing from the runner's.
        if (devBuild) intent.getStringExtra("rawsh")?.let { cmd ->
            intent.removeExtra("rawsh")
            Thread {
                try {
                    val p = ProcessBuilder("/system/bin/sh", "-c", cmd).redirectErrorStream(true).start()
                    p.inputStream.bufferedReader().forEachLine { Log.i(AppServer.TAG, "rawsh: $it") }
                    Log.i(AppServer.TAG, "rawsh: exit ${p.waitFor()}")
                } catch (e: Exception) { Log.w(AppServer.TAG, "rawsh: $e") }
            }.start()
        }
        // Say so and launch without them rather than throwing: the activity is singleTask, so
        // a bad `params` would otherwise be replayed on every restart and the shell could
        // never start again. Quoting one of these on a command line is easy to get wrong.
        // A webOS app's icon on Android's home screen. Taken once: the activity is singleTask
        // and a re-created one would otherwise open it again.
        if (intent.action == HomeScreenPins.ACTION_OPEN_APP) {
            intent.getStringExtra(HomeScreenPins.EXTRA_APP)?.let { openDirect(it) }
            intent.action = null
        }
        intent.takeString("launch")?.let { id ->
            val raw = intent.takeString("params")
            val params = raw?.let {
                runCatching { JSONObject(it) }
                    .onFailure { e -> Log.w(AppServer.TAG, "launch $id: params is not JSON (${raw.length} characters)") }
                    .getOrNull()
            }
            launch(id, params)
        }
        if (intent.getBooleanExtra(EXTRA_EXHIBITION, false)) { intent.removeExtra(EXTRA_EXHIBITION); exhibitions.startDreamExhibition() }
        // Android 5's am has no send-trim-memory; this stands in for it (Docs/dev-workflow.md).
        if (intent.hasExtra("trimMemory")) { val level = intent.getIntExtra("trimMemory", 0); intent.removeExtra("trimMemory"); onTrimMemory(level) }
    }

    /**
     * An extra read once: the activity is singleTask, and a re-created one is handed the same
     * intent, which would otherwise install, launch or open Exhibition again.
     */
    private fun android.content.Intent.takeString(key: String): String? = getStringExtra(key)?.also { removeExtra(key) }

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
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val state = when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL, TRIM_MEMORY_COMPLETE -> "critical"
            TRIM_MEMORY_RUNNING_LOW, TRIM_MEMORY_MODERATE -> "low"
            else -> return
        }
        lastTrim = android.os.SystemClock.uptimeMillis()
        setMemoryState(state)
    }

    /**
     * Android never says memory has eased, so the shell looks: "normal" once no warning has
     * come for half a minute and the system no longer calls itself low on memory.
     */
    /**
     * Delayed shell actions. Not a view's queue: reshell replaces the shell's views, and what
     * was posted to one that has gone never runs (memory stayed "low", brightness held).
     */
    private val shellHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val memoryCheck = object : Runnable {
        override fun run() {
            if (memoryState == "normal") return
            val mi = android.app.ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
            if (android.os.SystemClock.uptimeMillis() - lastTrim >= MEMORY_CALM_MS && !mi.lowMemory) setMemoryState("normal")
            else shellHandler.postDelayed(this, MEMORY_CHECK_MS)
        }
    }

    private fun setMemoryState(state: String) {
        if (state == memoryState) return
        memoryState = state
        Log.i(AppServer.TAG, "memory $state")
        // Root pages only, as LunaSysMgr sent it: a child window is the same app.
        val arg = JSONObject().put("state", state).toString()
        running.values.mapNotNull { it.firstOrNull() }.forEach { it.callMojo("lowMemoryNotification", arg) }
        shellHandler.removeCallbacks(memoryCheck)
        if (state != "normal") shellHandler.postDelayed(memoryCheck, MEMORY_CHECK_MS)
    }

    override fun onResume() {
        super.onResume(); inFront = true
        if (lockLeft) { lockLeft = false; val back = lockReturn; lockReturn = null; back?.invoke() }
        if (::securityPolicy.isInitialized) securityPolicy.recheck()
    }

    override fun onPause() {
        inFront = false; if (lockReturn != null) lockLeft = true
        CookieFlush.now(); super.onPause()
    }

    @Deprecated("Lunacy's minSdk 21 has no ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONFIRM_LOCK) { val back = confirmReturn; confirmReturn = null; back?.invoke(resultCode == RESULT_OK) }
    }

    /** Android's side of webOS's device lock (SecurityPolicy): Android's own lock screen is the lock. */
    private val lockHost = object : SecurityPolicy.Host {
        private val keyguard get() = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        override fun isSecure() = keyguard.isKeyguardSecure
        override fun chooseLock(done: () -> Unit): Boolean {
            // The screen-lock chooser itself, or failing that the security settings it is in.
            val ok = listOf(android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD, android.provider.Settings.ACTION_SECURITY_SETTINGS)
                .any { action -> runCatching { startActivity(android.content.Intent(action)) }.isSuccess }
            if (ok) { lockReturn = done; lockLeft = false }
            return ok
        }
        override fun confirmLock(done: (Boolean) -> Unit): Boolean {
            val intent = keyguard.createConfirmDeviceCredentialIntent(null, null) ?: return false
            val ok = runCatching { startActivityForResult(intent, CONFIRM_LOCK) }.isSuccess
            if (ok) confirmReturn = done
            return ok
        }
    }

    // ---- apps ----

    /**
     * Starts an app, or brings it forward if it is running. [placement] is where its first
     * window goes ([Placement]); [launchedBy] is who asked, for the card this launch brings
     * (showAsCard): LunaSysMgr's launchingAppId.
     */
    fun launch(appId: String, params: JSONObject? = null, placement: Placement = Placement.CARD, launchedBy: String? = null) {
        if (launchedBy != null && launchedBy != appId) launchers[appId] = launchedBy else launchers.remove(appId)
        // An added launch point launches its app with its own params, never into the dock.
        addedById[appId]?.launchPoint?.let { lp ->
            launch(lp.appId, lp.paramsObject(), if (placement == Placement.DOCK) Placement.NO_CARD else placement); return
        }
        androidById[appId]?.let { launchAndroid(it); return }
        val app = registry.get(appId) ?: run { Log.w(AppServer.TAG, "launch: no app $appId"); return }
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

    /** An Android app or a pinned shortcut, which Android opens. */
    private fun launchAndroid(a: AppInfo) {
        val opened = if (a.androidShortcut != null) pinned.launch(a) else androidApps.launch(a)
        if (!opened) systemBanner("", "Couldn't open ${a.title}")
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
                systemBanner(app.id, r?.optString("errorText").orEmpty().ifEmpty { "Couldn't open ${app.title}" })
            }
        }
    }

    /** A PDK app: its own process, in a card of its own kind (Docs/pdk.md); brought forward if running. */
    private fun launchNative(app: AppInfo, windows: List<AppWindow>?) {
        val appId = app.id
        if (windows != null) { cards.cards.firstOrNull { it.window.appId == appId }?.let { cards.maximize(it, maximizeMs(appId)) }; return }
        if (!pdkRuntime.available) {
            systemBanner(appId, "${app.title} is a native app; it needs Lunacy's 32-bit build on a 32-bit ARM device")
            return
        }
        val w = PdkWindow(this, appId, this, app, pdkRuntime)
        running[appId] = mutableListOf(w)
        showAsCard(w)
        if (!w.start()) { systemBanner(appId, "Couldn't start ${app.title}"); onWindowClosed(w) }
    }

    /**
     * Relaunch: webOS doesn't reload a running app. Unless the app handles the relaunch
     * itself, LunaSysMgr brings its first card forward.
     */
    private fun relaunch(app: AppInfo, windows: List<AppWindow>, params: JSONObject?, placement: Placement) {
        val appId = app.id
        windows.first().relaunch(params?.toString() ?: "") { handled ->
            Log.i(AppServer.TAG, "relaunch $appId ${if (Bus.handlesPeople(appId)) "" else params} handled=$handled")
            // A relaunch for the dock leaves the card view alone: the app answers it with
            // a dockMode window, or the dock shows nothing of it, as on a device.
            if (!handled && placement != Placement.DOCK) cards.cards.firstOrNull { it.window.appId == appId }?.let { cards.maximize(it, maximizeMs(appId)) }
        }
    }

    /** A web app that isn't running: its root window, placed as [placement] says, and its page. */
    private fun launchWeb(app: AppInfo, params: JSONObject?, placement: Placement) {
        val appId = app.id
        // The emulated card is a tablet's answer to a phone app. A Pre3 ran the same app at
        // its own size, so on a phone - where apps are told they are on a Pre3 - there is
        // nothing to emulate.
        val emulate = placement != Placement.DOCK && profile == DeviceProfile.TOUCHPAD && app.emulated
        val t0 = android.os.SystemClock.uptimeMillis()
        val rootWindow = AppWindow(this, appId, this, emulate)
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
                if (app.noWindow) hidden.addView(rootWindow, FrameLayout.LayoutParams(1, 1)) else exhibitions.addDockWindow(rootWindow)
                rootWindow.loadUrl(url)
            }
            Placement.NO_CARD -> {
                if (app.noWindow) hidden.addView(rootWindow, FrameLayout.LayoutParams(1, 1)) else showAsCard(rootWindow, splash = false)
                rootWindow.loadUrl(url)
            }
            Placement.CARD -> {
                showAsCard(rootWindow, placeholder = app.noWindow)
                Log.i(AppServer.TAG, "launch $appId: window and card in ${android.os.SystemClock.uptimeMillis() - t0} ms")
                loadWhenShown(rootWindow, url, t0)
            }
        }
    }

    /** The card waiting for a headless app's first card window, if it is [window]'s. */
    private fun placeholderOf(window: AppWindow): Card? = cards.cards.firstOrNull { it.awaitingWindow && it.window == window }

    /** The app is up and running with no card of its own: the placeholder goes, and the root goes on headless. */
    private fun withdrawPlaceholder(window: AppWindow) {
        // On the activity's window rather than the card layer, which a rebuild replaces (reshell).
        this.window.decorView.postDelayed({
            val card = placeholderOf(window) ?: return@postDelayed
            Log.i(AppServer.TAG, "launch ${window.appId}: no card opened; the placeholder goes")
            card.awaitingWindow = false
            (window.parent as? android.view.ViewGroup)?.removeView(window)
            hidden.addView(window, FrameLayout.LayoutParams(1, 1))
            cards.remove(card)
            if (cards.maximized == null) { statusBar.slide(false); applyOrientation(null) }
            if (cards.cards.isEmpty()) onCardView()
        }, PLACEHOLDER_GRACE_MS)
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
        window.decorView.postDelayed(load, CardLayer.Params.SLIDE_MS + 50)
    }

    /** Card view, while a launching card waits in it: the dock and the pill stay away, as on a device. */
    override fun onPreparing(card: Card) {
        if (launcherOpen) closeLauncher()
        onCardView()
        fade(justType, false)
        showDock(false)
    }

    private fun showAsCard(w: AppWindow, parent: AppWindow? = null, placeholder: Boolean = false, splash: Boolean = true) {
        // A card arriving ends Exhibition (SystemUiController::cardWindowAdded): an app opened
        // from a dashboard, or one that opens a card of its own.
        exhibitions.cardArrivedInExhibition()
        val card = newCard(w, splash)
        card.fullScreen = w.fullScreen && !w.emulated
        card.awaitingWindow = placeholder
        // A card an app opens while its own card is up joins that card's group, and so does
        // one an app opens for another app that launched it while that app's card was up
        // (CardWindowManager::prepareAddWindowSibling: the active card is focused and the
        // window's launching app is its app). Closing it then comes back to that card.
        val launchedBy = launchers.remove(w.appId)
        val up = cards.maximized?.takeIf { (parent != null && it.window.appId == w.appId) || (launchedBy != null && it.window.appId == launchedBy) }
        cards.add(card, up)
        if (w.appId == directApp) cards.openMaximized(card) else cards.openLaunching(card)
    }

    /**
     * The app opened from its icon on Android's home screen (HomeScreenPins), while its card is
     * the one up: it opened straight away, and leaving it goes back to Android rather than to
     * Lunacy's card view, so Lunacy's launcher never shows. Null otherwise.
     */
    private var directApp: String? = null
    /** Whether [directApp]'s card has been maximized: only then does going to the card view mean leaving. */
    private var directShown = false

    /** A running app's card comes up at once for its icon on Android's home screen, from the card view otherwise. */
    private fun maximizeMs(appId: String) = if (appId == directApp) 0L else CardLayer.Params.MAXIMIZE_MS

    /** Opens [appId] for its icon on Android's home screen. With Lunacy as the home app it is an ordinary launch. */
    private fun openDirect(appId: String) {
        if (homeScreenPins.lunacyIsHome()) { launch(appId); return }
        directApp = appId
        directShown = false
        launch(appId)
    }

    /** A card for [w], drawn at the shell's scale. */
    private fun newCard(w: AppWindow, splash: Boolean): Card {
        // An app that never said it was laid out for a tablet gets the phone-sized card a
        // TouchPad gave it, rather than being stretched across the whole one.
        val emu = if (!w.emulated) null else Pair(
            luna.px(EmulatedCard.WIDTH.toFloat()).toInt(),
            luna.px((EmulatedCard.HEIGHT - DeviceProfile.TOUCHPAD.positiveSpaceTopPadding).toFloat()).toInt())
        val card = Card(this, w, luna.px(CardLayer.Params.CORNER), emu,
            if (emu == null) null else luna.image("emucard-device-frame.png"),
            // webOS held the card's space with the app's own icon until it had drawn.
            if (splash) registry.get(w.appId)?.let { CardSplash(this, luna, luna.splashIcon(it)) } else null,
            if (emu == null) null else luna, registry.get(w.appId)?.title ?: w.appId)
        // The emulated card's own chrome: its title is the app menu, its strip the back gesture,
        // its button the keyboard (EmulatedCardWindow).
        card.onChrome = { action ->
            when (action) {
                Card.ChromeAction.APP_MENU -> toggleAppMenu()
                Card.ChromeAction.BACK -> w.sendBack()
                Card.ChromeAction.KEYBOARD -> { w.requestFocus(); imm.showSoftInput(w, android.view.inputmethod.InputMethodManager.SHOW_FORCED) }
            }
        }
        return card
    }

    /** appinfo.json's requestedWindowOrientation, which every card of the app starts with. */
    private fun fixedOrientationOf(app: AppInfo?): String? =
        app?.appinfo?.optString("requestedWindowOrientation")?.lowercase()
            ?.takeIf { it in ORIENTATIONS }

    override fun onWindowOpened(parent: AppWindow, child: AppWindow) {
        running.getOrPut(child.appId) { mutableListOf() } += child
        child.fixedOrientation = fixedOrientationOf(registry.get(child.appId))
        when (child.type) {
            WindowType.DASHBOARD -> notifications.addDashboard(child, notificationHost.icon(child.attributes.optString("icon"), child.appId))
            WindowType.POPUP_ALERT -> {
                // A phone's list gives way to the alert in the same area: closed by the shell,
                // so the scrim over the rest goes with it, or it would eat the next tap.
                if (notificationsAtBottom) closeMenu()
                notifications.popups.show(child, notificationHost.alertHeight(child))
            }
            // The headless app's first card: it fills the card its launch put up, and the root
            // goes on where a headless root lives.
            WindowType.CARD -> {
                val card = placeholderOf(parent)
                if (card == null) showAsCard(child, parent)
                else {
                    val root = card.attach(child)
                    hidden.addView(root, FrameLayout.LayoutParams(1, 1))
                    card.fullScreen = child.fullScreen && !child.emulated
                    if (cards.maximized == card) onMaximized(card)
                }
            }
            // An app's own Exhibition view: the dock's, never a card.
            WindowType.DOCK_MODE -> exhibitions.addDockWindow(child)
            // A name the shell doesn't know.
            null -> showAsCard(child, parent)
        }
    }

    override fun onWindowClosed(window: AppWindow) {
        exhibitions.windowClosed(window)
        cards.cards.firstOrNull { it.window == window }?.let { cards.remove(it) }
        // A full-screen card that closes itself while maximized takes the bar's absence with it,
        // and one that had fixed the screen's orientation lets it go.
        if (cards.maximized == null) { statusBar.slide(false); applyOrientation(null) }
        notifications.removeDashboard(window)
        notifications.popups.remove(window)
        closeWindow(window)
    }

    private val sounds by lazy {
        Sounds(this, server) { id -> registry.get(id)?.dir ?: id }.apply {
            // Sounds & Alerts' switches, read as each sound plays.
            allMuted = { ::systemService.isInitialized && systemService.get("muteSound") == true }
            feedbackMuted = {
                (::systemService.isInitialized && systemService.get("systemSounds") == false) ||
                    (::audio.isInitialized && audio.muted)
            }
        }
    }

    override fun paste(window: AppWindow) {
        val target = cards.maximized?.window ?: return
        val clip = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        target.evaluateJavascript("window.__lunacyPaste&&__lunacyPaste(${jsString(text)})", null)
    }

    override fun playSound(window: AppWindow, soundClass: String, soundFile: String, duration: Int) =
        sounds.play(window.appId, soundClass, soundFile, duration)

    /**
     * Installs a package, as Preware did for the App Museum: banners report progress, and
     * tapping the "installed" banner launches the app.
     */
    private fun install(source: String, requester: String = "", onProgress: (Int) -> Unit = {},
                        banners: Boolean = true, onDone: (Packages.Result) -> Unit = {}) {
        val name = android.net.Uri.decode(source.substringAfterLast('/'))
        if (banners) systemBanner(requester, "Installing $name")
        // LunaSysMgr showed the package on the launcher while it installed, with its progress.
        launcher.startInstall(source, name.removeSuffix(".ipk").substringBefore('_'))
        packages.install(source, progress = { p -> launcher.installProgress(source, p); onProgress(p) },
            // Named after the file at first, as the package is all there is; its app's own
            // title once the package is on the device and its appinfo.json can be read.
            title = { t -> launcher.installTitle(source, t) }) { r ->
            launcher.endInstall(source)
            val before = registry.apps.map { it.id }.toSet()
            appsChanged()
            jsServices.reload()
            // A package's own apps, or else one its script put in /usr/palm/applications.
            val app = r.appIds.firstNotNullOfOrNull { registry.inDir(it) }
                ?: registry.apps.firstOrNull { it.id !in before && it.visible }
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
     * What a webOS device did at each boot once First Use had run. First Use left
     * /var/luna/preferences/ran-first-use, which the configurator reads (Configurator's
     * activities), and the upstart job firstuse-createDefaultAccount, finding no
     * first-use-profile-created beside it, asked the palmprofile service for the profile
     * account sign-in would have made: a local one, "webOS User" in webOS CE's service, which
     * Calendar keeps its on-device calendar on. The service makes the flag, and makes no
     * account when one exists (a sign-in during First Use). Called once the webOS root's
     * services are up, and when First Use finishes, where the device would reboot.
     */
    private fun afterFirstUse() {
        if (!LunacyService.firstUseDone(this) || !::webos.isInitialized) return
        launchBootTimeApps()
        val prefs = java.io.File(webos.root, "var/luna/preferences")
        runCatching { prefs.mkdirs(); java.io.File(prefs, "ran-first-use").createNewFile() }
        if (java.io.File(prefs, "first-use-profile-created").exists()) return
        bus.call("", "palm://com.palm.accountservices/createNovaAccount", "{\"createDefaultAccount\":true}", privateBus = true) { r ->
            Log.i(AppServer.TAG, "firstuse-createDefaultAccount: $r")
        }
    }

    private var bootTimeAppsLaunched = false

    /**
     * ApplicationManager::launchBootTimeApps: once the launcher is up (here, once First Use
     * is done and the webOS root is ready), the apps in [LAUNCH_AT_BOOT] start headless with
     * {"launchedAtBoot": true}, which each of them answers by opening nothing. Email's headless
     * page is what tells of new mail (its DashboardManager), Messaging's of new messages.
     */
    private fun launchBootTimeApps() {
        if (bootTimeAppsLaunched) return
        bootTimeAppsLaunched = true
        for (id in LAUNCH_AT_BOOT) if (registry.get(id) != null && running[id] == null)
            launch(id, JSONObject().put("launchedAtBoot", true), Placement.NO_CARD)
    }

    /** The installed apps changed (an install, a removal, a script's rescan): the launcher hears it. */
    private fun appsChanged() {
        val start = System.currentTimeMillis()
        val before = registry.launchPoints.associateBy { it.id }
        registry.reload()
        configurator.run()
        securityPolicy.load()
        Log.i(AppServer.TAG, "apps rescanned in ${System.currentTimeMillis() - start} ms")
        setLauncherApps()
        dockMode.launchPointsChanged()
        val after = registry.launchPoints.associateBy { it.id }
        for ((id, app) in after) if (id !in before) applicationManager.launchPointChanged(app, "added")
        for ((id, app) in before) if (id !in after) applicationManager.launchPointChanged(app, "removed")
        accountTemplatesChanged()
        if (::universalSearch.isInitialized) universalSearch.appsChanged()
    }

    /** The account templates installed packages carry, as last seen: see [accountTemplatesChanged]. */
    private var accountTemplates: String? = null

    /**
     * The accounts service reads the account templates (/usr/palm/public/accounts, and
     * /media/cryptofs/apps/usr/palm/accounts for installed packages) when it starts, and again
     * only when told an app was installed or removed (its appsChanged). webOS told it; without
     * that, a Synergy connector installed while it ran (webCal Sync, on the Pixel Tablet) wasn't
     * in Accounts' list until Lunacy restarted. It is told when the installed templates differ
     * from the last look, so a start that installs nothing doesn't wake it.
     */
    private fun accountTemplatesChanged() {
        val dir = java.io.File(filesDir, "cryptofs/apps/usr/palm/accounts")
        val now = dir.walkTopDown().filter { it.isFile }.map { "${it.path}:${it.length()}:${it.lastModified()}" }.sorted().joinToString("\n")
        val was = accountTemplates
        accountTemplates = now
        if (was == null || was == now) return
        bus.call("com.palm.appinstaller", "palm://com.palm.service.accounts/appsChanged", "{}", privateBus = true) { r ->
            Log.i(AppServer.TAG, "accounts: templates changed, appsChanged: $r")
        }
    }

    override fun addBanner(window: AppWindow, message: String, params: String, icon: String, soundClass: String, soundFile: String, duration: Int) =
        notificationHost.addBanner(window, message, params, icon, soundClass, soundFile, duration)
    override fun copiedToClipboard(window: AppWindow) = notificationHost.copiedToClipboard(window)
    override fun removeBanner(window: AppWindow, id: Int) = notificationHost.removeBanner(window, id)
    override fun clearBanners(window: AppWindow) = notificationHost.clearBanners(window)
    /** A banner from the system itself, e.g. the package manager. Tapping it launches appId. */
    private fun systemBanner(appId: String, message: String, params: String = "") = notificationHost.systemBanner(appId, message, params)
    private fun notificationIconsChanged() = notificationHost.iconsChanged()

    private fun toggleMenu() = if (notifications.menu.isOpen) closeMenu() else openMenu()

    /** Opening one status-bar menu closes the other (StatusBar::slotMenuGroupActivated). */
    private fun toggleSystemMenu() {
        if (systemMenu.isOpen) { closeMenu(); return }
        closeMenu()
        // SystemMenu::slotPositiveSpaceChangeFinished: as tall as the room under the bar, plus 10.
        shellRoot?.let { if (it.height > 0) systemMenu.maxHeightPx = it.height - luna.px(StatusBar.HEIGHT) + luna.px(10) }
        systemMenu.batteryPercent = statusBar.batteryPercent
        systemMenu.brightness = (displayService.brightnessPercent() - 1) / 99f
        menuScrim.visibility = View.VISIBLE
        statusBar.systemMenuOpen = true
        systemMenu.open()
    }

    /** The drop-down's right edge lines up with the notification group's right edge. */
    private fun openMenu() {
        if (!notifications.hasDashboards || notifications.popups.showing) return
        statusBar.systemMenuOpen = false; systemMenu.close()
        // A phone's open over the bottom; a tap above them closes them (the soft dismiss).
        if (notificationsAtBottom) { menuScrim.visibility = View.VISIBLE; notifications.menu.open(); return }
        val menu = notifications.menu as DashboardMenu
        menu.measure(0, 0)
        (menu.layoutParams as FrameLayout.LayoutParams).leftMargin =
            // 15 px past the group's right edge, measured against the reference TouchPad.
            (statusBar.notificationRight + luna.px(15f) - menu.measuredWidth).toInt().coerceAtLeast(0)
        menu.requestLayout()
        menuScrim.visibility = View.VISIBLE
        statusBar.menuOpen = true
        menu.open()
    }

    /**
     * Android's notifications changed (AndroidNotifications): the Android Notifications app
     * hears it, or, not running, is started to show them, headless, as a service starts an app
     * on webOS. It ends again once its last dashboard goes. Not before the webOS root is up and
     * First Use has run.
     */
    private fun androidNotificationsChanged() {
        val service = lunacyService ?: return
        service.notificationsChanged()
        if (service.notificationsWatched || running.containsKey(ANDROID_NOTIFICATIONS_APP)) return
        if (!AndroidNotifications.any()) return
        if (!::webos.isInitialized || !LunacyService.firstUseDone(this)) return
        if (registry.get(ANDROID_NOTIFICATIONS_APP) == null) return
        launch(ANDROID_NOTIFICATIONS_APP, null, Placement.NO_CARD)
    }

    /**
     * The system menu's slider, which on the TouchPad set the backlight at once. Android's
     * setting alone can't: with adaptive brightness on, Android ramps a lower value in at its
     * slow rate, about 0.02 a second (measured on the Fold3, the Pixel Tablet too by codepoet),
     * so the screen took most of a minute to follow a drag down. Android's own slider avoids
     * that with a call only the system may make. A window's brightness override is ramped at
     * the fast rate, so the shell's window holds the slider's value while it moves, the setting
     * takes the same value (Android's slider agrees, and it lasts outside Lunacy), and the
     * override goes a moment after the finger lifts, when adaptive brightness's target is that
     * value already.
     */
    private val releaseBrightness = Runnable {
        window.attributes = window.attributes.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }
    private fun setMenuBrightness(percent: Int, done: Boolean) {
        displayService.setBrightnessPercent(percent)
        shellHandler.removeCallbacks(releaseBrightness)
        window.attributes = window.attributes.apply { screenBrightness = displayService.overrideFromPercent(percent) }
        if (done) shellHandler.postDelayed(releaseBrightness, BRIGHTNESS_HOLD_MS)
    }

    /** SystemMenu.qml's closeMenuTimer. A menu closed and opened again meanwhile stays. */
    private var systemMenuCloser: Runnable? = null
    private fun closeSystemMenuIn(delayMs: Long) {
        systemMenuCloser?.let { shellHandler.removeCallbacks(it) }
        if (!systemMenu.isOpen) return
        val r = Runnable { systemMenuCloser = null; if (systemMenu.isOpen) closeMenu() }
        systemMenuCloser = r
        shellHandler.postDelayed(r, delayMs)
    }

    /** One of Android's settings screens, by LunacyService's panel name: where the system menu hands over. */
    private fun openAndroidPanel(panel: String) {
        val action = LunacyService.PANELS[panel] ?: return
        try {
            startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "systemMenu: can't open Android's $panel settings", e)
            systemBanner("", "Couldn't open Android's settings")
        }
    }

    private fun closeMenu() {
        systemMenuCloser?.let { shellHandler.removeCallbacks(it) }; systemMenuCloser = null
        menuScrim.visibility = View.GONE
        statusBar.menuOpen = false
        notifications.menu.close()
        statusBar.systemMenuOpen = false
        systemMenu.close()
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

    override fun activityId(window: AppWindow): Int = appActivities[window.appId] ?: -1

    /** Called on the bridge's thread, the first time the page embeds a plugin. */
    override fun hybridPlugins(window: AppWindow): HybridPlugins? {
        val app = registry.get(window.appId) ?: return null
        return HybridPlugins(window, app, pdkRuntime) { webos.prepare() }
    }

    /** WebAppBase::focusActivity and blurActivity, as the app's card gains and loses the focus. */
    override fun stageFocused(window: AppWindow, focused: Boolean) {
        val id = appActivities[window.appId] ?: return
        bus.call(window.appId, "palm://com.palm.activitymanager/${if (focused) "focus" else "unfocus"}",
            JSONObject().put("activityId", id).toString(), privateBus = true) {}
    }

    private fun closeWindow(w: AppWindow) {
        pendingLoads.remove(w)
        // A window that closes while holding the screen on lets it go.
        if (screenAwake.remove(w)) holdScreen()
        val list = running[w.appId] ?: return
        list.remove(w)
        (w.parent as? android.view.ViewGroup)?.removeView(w)
        // Its unload handlers first, as webOS ran them (a dashboard's onUserClose comes from there).
        w.close()
        // An app ends when its last visible window closes; its headless root goes with it, a
        // moment later: a dashboard swiped away tells the root as it unloads (Enyo's
        // onUserClose), and the root acts on it, so it must still be there to hear it. A window
        // it opens meanwhile keeps it. An app in [KEEP_ALIVE] keeps its headless root.
        val app = registry.get(w.appId)
        if (list.isEmpty()) endApp(w.appId, list)
        else if (app?.noWindow == true && w.appId !in KEEP_ALIVE && list.none { it.parent != hidden }) {
            window.decorView.postDelayed({
                if (running[w.appId] === list && list.none { it.parent != hidden }) endApp(w.appId, list)
            }, HEADLESS_LINGER_MS)
        }
        if (cards.cards.isEmpty() && !exhibitions.exhibitionOn) onCardView()
    }

    private fun endApp(appId: String, list: MutableList<AppWindow>) {
        list.forEach { (it.parent as? android.view.ViewGroup)?.removeView(it); it.close() }
        running.remove(appId)
        endAppActivity(appId)
    }

    /**
     * /media/internal maps Android's shared folders (UserFiles), which Android 5 granted at
     * install. From Android 6 the user grants them while the app runs, so the shell asks once
     * at startup; refused, those folders are simply not there, as UserFiles already handles.
     */
    private fun askForStorage() {
        if (android.os.Build.VERSION.SDK_INT < 23) return
        val wanted = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val missing = wanted.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), STORAGE_REQUEST)
    }

    /** Runtime permission requests made on the bus (LunacyService), each answered to its caller when Android has. */
    private val permissionCallbacks = mutableMapOf<Int, (Boolean, Boolean) -> Unit>()
    private var nextPermissionCode = FIRST_PERMISSION_REQUEST

    /**
     * Asks Android for [permissions] and calls [done] with whether every one was granted and,
     * if not, whether Android would still show the question next time: from Android 11 a
     * refusal given twice is final, and a later ask is refused with no dialog. Before Android 6
     * they were granted at install.
     */
    private fun askPermissions(permissions: List<String>, done: (granted: Boolean, askAgain: Boolean) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < 23) { done(true, false); return }
        val code = nextPermissionCode++
        permissionCallbacks[code] = done
        requestPermissions(permissions.toTypedArray(), code)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
        val askAgain = android.os.Build.VERSION.SDK_INT >= 23 && permissions.any { shouldShowRequestPermissionRationale(it) }
        permissionCallbacks.remove(requestCode)?.invoke(granted, askAgain)
    }

    /**
     * When a card's app counts as ready, as WindowedWebApp decided it: when the page calls
     * stageReady, or - a page that never does - 3 s after it finished loading
     * (kShowWindowTimeoutMs). Having drawn doesn't count: measured with the slow probe, the
     * reference TouchPad kept a page that had painted but not called stageReady off the screen
     * until it did. Here the card waits in the card view (CardLayer.openLaunching); being ready
     * maximizes it, and lets its placeholder go once the page has drawn as well (onPageDrawn).
     */
    override fun onPageLoaded(window: AppWindow) {
        shellHandler.postDelayed({
            if (window.stageReady) return@postDelayed
            if (placeholderOf(window) != null) withdrawPlaceholder(window)
            else cardOf(window)?.let { cards.ready(it) }
        }, SHOW_WINDOW_TIMEOUT_MS)
    }

    /** The framework says the app is ready: a launching card maximizes, and its placeholder goes once the page has drawn. */
    override fun onStageReady(window: AppWindow) {
        if (placeholderOf(window) != null) withdrawPlaceholder(window)
        else cardOf(window)?.let { cards.ready(it) }
    }

    /** The app has drawn: the loading placeholder on its card can go. */
    override fun onPageDrawn(window: AppWindow) {
        cardOf(window)?.drawn = true
    }

    /** The card whose page is [window]; a placeholder's headless root is nobody's page. */
    private fun cardOf(window: AppWindow): Card? = cards.cards.firstOrNull { it.window == window && !it.awaitingWindow }

    override fun mediaBase() = mediaServer.base()

    /**
     * PalmSystem.enableFullScreenMode. The card is laid out over the bar's space at once, as
     * LunaSysMgr resized the window at once, and the bar slides away if the card is up. Going
     * back, the bar slides in first and the card shrinks under it when it has arrived, so
     * nothing shows through the gap. LunaSysMgr left an emulated card's bar alone
     * (SystemUiController::applyWindowProperties), and so does this.
     */
    override fun orientationRequested(window: AppWindow) {
        if (cards.maximized?.window == window) applyOrientation(window)
    }

    /**
     * A card that fixes its orientation turns the whole screen while it is maximized - status
     * bar, card and all - which is what the reference TouchPad does (its luna.conf has
     * displayUiRotates, and LunaSysMgr then rotated the UI rather than the card). webOS's
     * window "up" is portrait on both the TouchPad and a phone. The screen is let go again in
     * card view or when another card comes up; Android then follows the sensor at once, where
     * a TouchPad waited for the next time it was turned.
     */
    private fun applyOrientation(window: AppWindow?) {
        val o = window?.takeUnless { it.emulated }?.fixedOrientation
        val want = ORIENTATIONS[o] ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (requestedOrientation != want) requestedOrientation = want
    }

    override fun fullScreen(window: AppWindow) {
        if (window.emulated) return
        val card = cards.cards.firstOrNull { it.window == window } ?: return
        when {
            window.fullScreen -> { card.fullScreen = true; if (cards.maximized == card) statusBar.slide(true) }
            cards.maximized == card -> statusBar.slide(false) { if (!window.fullScreen) card.fullScreen = false }
            else -> card.fullScreen = false
        }
    }

    override fun activate(window: AppWindow) {
        cards.cards.firstOrNull { it.window == window }?.let { if (cards.maximized != it) cards.maximize(it) }
    }

    // ---- keyboard ----

    private val imm by lazy { getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager }
    /** The keyboard's height in Android px, 0 when hidden. */
    private var keyboardHeight = 0

    /**
     * Windows holding the screen awake. webOS let an app ask for this per window, and a video
     * player asks for it while it plays; Android's equivalent is a flag on the activity, so
     * the flag is on while any window still wants it.
     */
    private val screenAwake = java.util.Collections.newSetFromMap(java.util.WeakHashMap<AppWindow, Boolean>())

    override fun blockScreenTimeout(window: AppWindow, block: Boolean) {
        if (block) screenAwake.add(window) else screenAwake.remove(window)
        holdScreen()
    }

    /** The screen stays on while exhibiting (the point of a dock) or while a window asks. */
    private fun holdScreen() {
        if (exhibitions.exhibitionOn || screenAwake.isNotEmpty()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** A window that asked for the keyboard before its card had finished opening. */
    private var keyboardWanted: AppWindow? = null

    /**
     * Only the active card's window gets the keyboard, as on webOS - and "active" means the
     * card on its way to maximized as well as the one already there. An app that focuses a
     * field while its first scene is built (webOS SimpleChat's compose box) asks for the
     * keyboard during the open animation; LunaSysMgr made the same allowance, in so many
     * words (CardWindow::slotShowIME). The request is repeated once the card has settled,
     * because the view can't take focus while it is still being animated into place.
     */
    override fun keyboard(window: AppWindow, show: Boolean) {
        if (show) {
            if (cards.active?.window != window) return
            keyboardWanted = if (cards.maximized?.window == window) null else window
            window.requestFocus()
            imm.showSoftInput(window, 0)
        } else {
            keyboardWanted = null
            if (keyboardHeight > 0 && cards.maximized?.window == window) {
                imm.hideSoftInputFromWindow(window.windowToken, 0)
            }
        }
    }

    /**
     * Follows the keyboard. In full screen Android doesn't resize the window for it, so the
     * visible frame gives its height. As LunaSysMgr did: Mojo.keyboardShown(true), then the
     * card shrinks to the space above the keyboard (or, for a window that asked not to be
     * resized, Mojo.positiveSpaceChanged reports that space); on hiding, the card grows back
     * and Mojo.keyboardShown(false) follows, and the page's input focus goes with the
     * keyboard, which is how webOS put it away (AppWindow.removeInputFocus).
     */
    private var keyboardWatch: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
    private fun watchKeyboard(root: View) = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        val r = android.graphics.Rect()
        root.getWindowVisibleDisplayFrame(r)
        val h = (root.rootView.height - r.bottom).let { if (it > root.rootView.height / 6) it else 0 }
        if (h == keyboardHeight) return@OnGlobalLayoutListener
        keyboardHeight = h
        val w = cards.maximized?.window
        val lp = cards.layoutParams as FrameLayout.LayoutParams
        // A phone's notification area steps aside for the keyboard and comes back after it.
        area?.suspended = h > 0
        if (h > 0) {
            w?.callMojo("keyboardShown", "true")
            if (w == null || w.keyboardResizes) { lp.bottomMargin = h; cards.layoutParams = lp }
            // In the page's own px, which on a phone may be scaled (AppWindow.pageScale).
            else w.callMojo("positiveSpaceChanged", "${Math.round(cards.width / w.pageScale)},${Math.round((cards.areaHeight - h) / w.pageScale)}")
        } else {
            val resized = lp.bottomMargin != 0
            lp.bottomMargin = 0; cards.layoutParams = lp
            if (w != null && !resized) w.callMojo("positiveSpaceChanged", "${Math.round(cards.width / w.pageScale)},${Math.round(cards.areaHeight / w.pageScale)}")
            w?.let { win -> cards.post { win.callMojo("keyboardShown", "false") } }
            w?.removeInputFocus()
            goImmersive()  // Android shows its bars with the keyboard and leaves them up
        }
    }.also { keyboardWatch = it; root.viewTreeObserver.addOnGlobalLayoutListener(it) }

    private fun hideKeyboard() {
        if (keyboardHeight > 0) imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    override val pixelScale get() = luna.density
    // A phone's: a tablet's card is a TouchPad's size already, and its landscape card, shorter
    // than the 640 px a phone's shorter side is laid out at, would otherwise be shrunk.
    override fun fixedViewportWidth(appId: String) =
        if (FormFactor.isPhone(this) && FixedViewport.isOn(this, appId))
            FormFactor.appLayoutWidth(this) else 0

    /**
     * The device an app sees. Which one is DeviceProfile's decision - a TouchPad on a
     * tablet-sized screen, a Pre3 on a phone - and everything else an app can look at (the
     * user agent, the system properties, X-Palm-Carrier) comes from the same place, so they
     * can never disagree.
     *
     * Like a TouchPad's, this doesn't change when the screen turns: the device reports
     * 1024 x 768 in both orientations. So the screen's long side is the width here too, and a
     * page that read deviceInfo at load never goes stale. Rotation reaches apps through
     * PalmSystem.screenOrientation and the window's own resize, as it did on webOS. The sizes
     * are this screen's real ones: a TouchPad reported its own screen, and an app that lays
     * out from them should use the room it actually has.
     */
    override fun deviceInfo(emulated: Boolean): String {
        // In TouchPad px, the unit apps lay out in.
        val dm = realMetrics().apply {
            density = luna.density
        }
        val long = (maxOf(dm.widthPixels, dm.heightPixels) / dm.density).toInt()
        val short = (minOf(dm.widthPixels, dm.heightPixels) / dm.density).toInt()
        val d = profile
        // Which side the device calls its width: a TouchPad names its screen 1024 x 768, a
        // Pre3 names its own 480 x 800. Measured on both (Docs/pre3.md).
        var w = if (d.naturalLandscape) long else short
        var h = if (d.naturalLandscape) short else long
        var minCardHeight = d.minimumCardHeight
        var rows = d.touchableRows
        // An app that never said it was laid out for a tablet is told about the phone-sized
        // card it is running in, not about the screen. Measured on the reference TouchPad
        // (EmulatedCard): 320 x 480, a 452-high card and 8 touchable rows. Everything else -
        // the model, the version, the serial - is the device's own, as it is there.
        if (emulated) {
            w = EmulatedCard.WIDTH
            h = EmulatedCard.HEIGHT
            minCardHeight = EmulatedCard.MINIMUM_CARD_HEIGHT
            rows = EmulatedCard.TOUCHABLE_ROWS
        }
        return JSONObject(mapOf(
            "modelName" to d.modelName, "modelNameAscii" to d.modelNameAscii,
            "platformVersion" to d.platformVersion, "platformVersionMajor" to d.platformVersionMajor,
            "platformVersionMinor" to d.platformVersionMinor, "platformVersionDot" to d.platformVersionDot,
            "carrierName" to d.carrierName,
            "serialNumber" to DeviceProfile.serial(this, d),
            "screenWidth" to w, "screenHeight" to h,
            "minimumCardWidth" to w, "minimumCardHeight" to minCardHeight,
            "maximumCardWidth" to w, "maximumCardHeight" to h - d.positiveSpaceTopPadding,
            "touchableRows" to rows,
            "keyboardAvailable" to d.keyboardAvailable, "keyboardSlider" to d.keyboardSlider,
            "keyboardType" to d.keyboardType,
            "wifiAvailable" to true, "bluetoothAvailable" to d.bluetoothAvailable,
            // The TouchPad has this member between bluetoothAvailable and coreNaviButton; the
            // Pre3 hasn't got it at all, and a page that enumerates deviceInfo would see it.
            *(if (d.reportsCarrierAvailable) arrayOf("carrierAvailable" to d.carrierAvailable) else emptyArray()),
            "coreNaviButton" to d.coreNaviButton, "swappableBattery" to d.swappableBattery,
            // True on both reference devices, and Lunacy has dock mode: the Exhibition app
            // puts the shell into it, and Android's screen saver starts it.
            "dockModeEnabled" to true,
        )).toString()
    }

    /**
     * systemservice's locale, region and timeFormat, from Android's settings, and the device's
     * x_palm_carrier (SystemService.hostPreference). The carrier code is the device's own, as
     * X-Palm-Carrier is: the reference TouchPad answers "c090-01" for both. Help builds its
     * content URL from it (help.webosarchive.org/en-us/c090-01/index.json).
     */
    private fun hostPreference(key: String): Any? {
        val locale = resources.configuration.locale
        val country = locale.country.lowercase().ifEmpty { "us" }
        val region = JSONObject().put("countryCode", country)
            .put("countryName", java.util.Locale("", country.uppercase()).getDisplayCountry(java.util.Locale.ENGLISH))
        return when (key) {
            "locale" -> JSONObject().put("languageCode", locale.language.lowercase().ifEmpty { "en" })
                .put("countryCode", country).put("phoneRegion", region)
            "region" -> region
            "timeFormat" -> if (android.text.format.DateFormat.is24HourFormat(this)) "HH24" else "HH12"
            "x_palm_carrier" -> profile.carrierCode
            // A device always has a wallpaper preference: its factory default until one is
            // picked, in this shape with no thumbnail (/etc/palm/defaultPreferences.txt on the
            // reference TouchPad). Lunacy's default is the one it shows, which it ships into
            // /media/internal/wallpapers. Email's compose window draws it behind the message.
            "wallpaper" -> JSONObject().put("wallpaperName", Luna.DEFAULT_WALLPAPER)
                .put("wallpaperFile", "/media/internal/wallpapers/${Luna.DEFAULT_WALLPAPER}").put("wallpaperThumbFile", "")
            else -> null
        }
    }

    /**
     * PalmSystem's locale fields and clock format. webOS reported what the device was set to,
     * so these follow Android's settings: an app that formats a date or a time gets the same
     * answer as the shell's own clock.
     */
    override fun localeInfo(): String {
        val locale = resources.configuration.locale
        val language = locale.language.lowercase().ifEmpty { "en" }
        val country = locale.country.lowercase().ifEmpty { "us" }
        return JSONObject()
            .put("locale", language + "_" + country)
            .put("localeRegion", country)
            .put("phoneRegion", country)
            .put("timeFormat", if (android.text.format.DateFormat.is24HourFormat(this)) "HH24" else "HH12")
            .toString()
    }

    /**
     * webOS's screen orientation. The reference TouchPad reports "right" in landscape and
     * "up" in portrait (Docs/spike-1.md): its panel is portrait, as this tablet's is, so
     * Android's display rotation maps straight onto webOS's strings.
     */
    override fun screenOrientation(): String {
        @Suppress("DEPRECATION")
        return when (windowManager.defaultDisplay.rotation) {
            android.view.Surface.ROTATION_90 -> "left"
            android.view.Surface.ROTATION_180 -> "down"
            android.view.Surface.ROTATION_270 -> "right"
            else -> "up"
        }
    }

    /**
     * webOS's window orientation, which is the screen's turned by where this device's home
     * button would be. LunaSysMgr kept the two apart and so does Lunacy: an app reads
     * `PalmSystem.screenOrientation` for the screen and `PalmSystem.windowOrientation` for
     * the window, and a Mojo app branches on the second. See [DeviceProfile.windowOrientationFor].
     */
    override fun windowOrientation(emulated: Boolean): String =
        // An emulated card is drawn upright whichever way the tablet is held, and the
        // reference device reports it as "up" where a full card in the same orientation
        // reports "right".
        if (emulated) "up" else profile.windowOrientationFor(screenOrientation())

    /**
     * The display in webOS pixels, the way round it is now. `getRealMetrics` follows the
     * rotation, so this turns with the screen the way a device's `screen.width` did: the
     * reference TouchPad, in portrait, reported 768 x 1024 where its `deviceInfo` says
     * 1024 x 768 whichever way up it is.
     */
    override fun screenSize(emulated: Boolean): String {
        if (emulated) {
            return JSONObject()
                .put("width", EmulatedCard.WIDTH)
                .put("height", EmulatedCard.HEIGHT)
                .toString()
        }
        val dm = realMetrics()
        return JSONObject()
            .put("width", Math.round(dm.widthPixels / luna.density))
            .put("height", Math.round(dm.heightPixels / luna.density))
            .toString()
    }

    /** The orientation the windows have been told about. */
    private var reportedOrientation = ""

    /**
     * The screen turned. A configuration change isn't enough on its own: turning the tablet
     * end for end changes the rotation without changing the orientation or the size, so
     * Android doesn't report a configuration change at all. The display listener sees every
     * rotation, and both paths end here.
     */
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) = updateOrientation()
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }

    /**
     * Every window learns the new orientation before its own resize event arrives, which is
     * when Enyo reads PalmSystem.screenOrientation.
     */
    private fun updateOrientation() {
        val o = screenOrientation()
        if (o == reportedOrientation) return
        reportedOrientation = o
        running.values.flatten().forEach { it.setScreenOrientation(o) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientation()
        goImmersive()
        // A foldable folded or opened: the screen may now call for the other layout. Once the
        // display has its new size (the configuration can arrive first).
        window.decorView.post { if (shownKind() != FormFactor.decide(this)) reshell() else notificationHost.placeNotifications() }
    }

    /**
     * The shell rebuilt in the layout the screen now calls for - a foldable folded onto its
     * narrow outer screen, or opened out - or that the owner has just chosen (Screen & Lock,
     * `--es layout`). The phone or tablet decision and the shell's scale were made once, at
     * startup, and a folded phone kept a tablet's launcher squeezed onto its outer screen.
     *
     * Only the shell's own views are rebuilt. The apps run on through it: every card keeps its
     * page, its place in its group and its state (maximized, in the card view, still loading),
     * and its frame is drawn again at the new scale; dashboards, alerts and banners move to the
     * new bar. What was open over the cards - the launcher, Just Type, a menu, Exhibition - was
     * the old layout's and closes, as a press of home would. A page is told its new scale (its
     * viewport written again); what a window was told about the device when it opened, a Pre3
     * or a TouchPad, it keeps, and the next window opened is told the new one.
     */
    /**
     * The layout this shell is drawn in. Its own rather than FormFactor.shown: Android can run
     * a second shell activity in the process (Lunacy as the home app, beside one started
     * another way), and the newer one's decision mustn't stop this one rebuilding.
     */
    private fun shownKind() = if (luna.phone) FormFactor.Kind.PHONE else FormFactor.Kind.TABLET

    private fun reshell() {
        val kind = FormFactor.decide(this)
        if (kind == shownKind()) return
        val old = shellRoot ?: return
        Log.i(AppServer.TAG, "layout: ${shownKind()} -> $kind; the shell is rebuilt, its cards kept")
        if (exhibitions.exhibitionOn) exhibitions.setExhibition(false)
        hideKeyboard()
        keyboardWatch?.let { old.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        keyboardWatch = null
        keyboardHeight = 0
        launcherOpen = false
        exhibitions.forgetShown()

        // What the old views hold, before they go.
        val handover = cards.handOver()
        val dashboards = notifications.dashboards
        val alerts = notifications.popups.windows
        val banners = notifications.handOverBanners()
        val headless = (0 until hidden.childCount).map { hidden.getChildAt(it) }
        // Every page out of the old views but the cards' own, which come out as their new cards take them.
        for (w in running.values.flatten()) if (w.parent !is Card) (w.parent as? android.view.ViewGroup)?.removeView(w)

        FormFactor.shown = kind
        luna = Luna(this, kind == FormFactor.Kind.PHONE)
        androidAppsAt = null
        buildShell()

        cards.takeOver(handover) { c ->
            val splashing = c.splashing
            newCard(c.release(), splashing).also { n ->
                n.fullScreen = c.fullScreen; n.awaitingWindow = c.awaitingWindow
                n.drawn = c.drawn; n.ready = c.ready
            }
        }
        headless.forEach { hidden.addView(it, FrameLayout.LayoutParams(1, 1)) }
        exhibitions.takeWindowsBack()
        notificationHost.giveNotifications(dashboards, alerts, banners)
        // Once the pages are laid out in their new cards: the viewport is written from the card's size.
        cards.post { running.values.flatten().forEach { it.rescale() } }
        // Pages loaded under the other layout fetch the images served by layout again.
        running.values.flatten().forEach { it.evaluateJavascript(AppServer.REFETCH_LAYOUT_IMAGES, null) }
        val maximized = cards.maximized
        if (maximized != null) onMaximized(maximized) else onCardView()
        goImmersive()
    }

    /**
     * The wallpaper the system service holds, else the one Lunacy ships. The reference
     * TouchPad's own preference names a file in /media/internal/.wallpapers; Lunacy's is the
     * same shape, so Screen & Lock's "Change Wallpaper" sets this one.
     */
    private fun showWallpaper() {
        val chosen = systemService.fileOf(systemService.get("wallpaper") as? JSONObject)
        val dm = realMetrics()
        val bmp = chosen?.let { f -> luna.decodeFull(f, maxOf(dm.widthPixels, dm.heightPixels)) } ?: luna.wallpaper()
        if (bmp != null) {
            // LunaSysMgr filled the screen only with an image that could fill it one way up or
            // the other; a smaller one was drawn at its own size, centred on the screen, over
            // the scene's Qt::darkGray (WindowServerLuna::generateWallpaperImages; measured on
            // the reference TouchPad with a 600 x 400 image). Its own size is in TouchPad px.
            // "The screen" is at most a TouchPad's 1024 x 768: a screen larger than that would
            // otherwise leave webOS's own 1024 x 1024 wallpapers, made to fill a TouchPad,
            // floating in grey.
            val long = minOf(maxOf(dm.widthPixels, dm.heightPixels) / luna.density, 1024f)
            val short = minOf(minOf(dm.widthPixels, dm.heightPixels) / luna.density, 768f)
            val fills = (bmp.width >= long && bmp.height >= short) || (bmp.width >= short && bmp.height >= long)
            if (fills) {
                wallpaperView.scaleType = ImageView.ScaleType.CENTER_CROP
                wallpaperView.setImageBitmap(bmp)
                wallpaperView.background = null
            } else {
                wallpaperView.scaleType = ImageView.ScaleType.CENTER
                wallpaperView.setImageBitmap(if (luna.density == 1f) bmp else android.graphics.Bitmap.createScaledBitmap(
                    bmp, luna.px(bmp.width), luna.px(bmp.height), true))
                wallpaperView.setBackgroundColor(Color.rgb(0x80, 0x80, 0x80))
            }
        } else {
            wallpaperView.scaleType = ImageView.ScaleType.CENTER_CROP
            wallpaperView.setImageDrawable(null)
            wallpaperView.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 24, 64), Color.rgb(20, 90, 200)))
        }
    }

    /** Reads the wallpaper [showWallpaper] shows: the owner's pick, else the one Lunacy ships. */
    private fun wallpaperSource(): () -> java.io.InputStream {
        val chosen = systemService.fileOf(systemService.get("wallpaper") as? JSONObject)
        return { chosen?.inputStream() ?: assets.open("luna/wallpapers/${Luna.DEFAULT_WALLPAPER}") }
    }

    /**
     * A stored preference the shell owns. webOS worked the same way: the service keeps the
     * key, and whoever owns the thing it names acts on it. Keys nothing here owns are kept
     * and nothing more, as they were on a device.
     */
    private fun onPreferenceChanged(key: String, value: Any?) {
        when (key) {
            "wallpaper" -> {
                showWallpaper()
                // Android's own wallpaper follows, if the owner asked (Screen & Lock).
                if (HostWallpaper.isOn(this)) {
                    val open = wallpaperSource()
                    Thread { HostWallpaper.follow(applicationContext, open) }.start()
                }
            }
            // webOS's Auto Dim: the display owner acts on it, and here that is Android's.
            "enableALS" -> displayService.setAutomaticBrightness(value == true)
        }
    }

    /**
     * What a device shipped with: the TouchPad's wallpapers, in Android's pictures where Android
     * and its apps find them as well as Screen & Lock's picker (SharedMedia). Off the main thread.
     */
    private fun seedMediaInternal() = Thread {
        try {
            SharedMedia.installWallpapers(this, jsServices.root)
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "couldn't share the wallpapers", e)
        }
    }.start()

    /** The whole display in real pixels, the way round it is now, bars and cutouts included. */
    @Suppress("DEPRECATION")
    private fun realMetrics() = android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }

    /** What the shell knows about the screen: real pixels, and the TouchPad px apps lay out in. */
    private fun displayInfo(): JSONObject {
        val dm = realMetrics()
        return JSONObject()
            .put("width", dm.widthPixels).put("height", dm.heightPixels)
            .put("dpi", dm.densityDpi).put("androidDensity", dm.density)
            .put("scale", luna.density).put("orientation", screenOrientation())
            .put("cardWidth", Math.round(cards.width / luna.density))
            .put("cardHeight", Math.round(cards.areaHeight / luna.density))
            .put("pixelGrid", pixelGrid(cards.width, cards.areaHeight))
    }

    /**
     * Whether a card's CSS pixels land on whole device pixels, and what they come out as
     * when they don't.
     *
     * Chromium lays a page out in density-independent pixels: it divides the view by the
     * display's density, rounds to whole dip, and multiplies back. Where that doesn't return
     * the number it started from, the page is drawn through a scale a hair off 1, and a
     * border-image's nine pieces stop landing on whole pixels - which is where the seams in
     * Mojo's and Enyo's frames come from ("border-image seams" in Docs/fix-log.md). It is a
     * rounding coincidence rather than a property of the screen: on the reference tablet
     * 1280 px comes back as 1281 while 800 px comes back as 800, so the same device is off
     * in landscape and exact in portrait.
     *
     * Reported so that a screen Lunacy has not run on before says which it is, in Device
     * Info, rather than leaving it to be noticed in the artwork. "1:1" when they land, and
     * the size the page comes out as when they don't.
     */
    private fun pixelGrid(w: Int, h: Int): String {
        val dm = realMetrics()
        fun css(px: Int) = if (px <= 0 || dm.density <= 0f) px else Math.round(Math.round(px / dm.density) * dm.density)
        val cw = css(w); val ch = css(h)
        return if (cw == w && ch == h) "1:1" else "$cw \u00d7 $ch"
    }

    private fun registerServices() {
        SystemProperties(this).register(bus)
        // webOS's preference and wallpaper store, and the two display settings Android lets
        // Lunacy really set. See Docs/architecture.md, "Settings".
        displayService = DisplayService(this)
        displayService.onDockMode = { on -> runOnUiThread { exhibitions.setExhibition(on) } }
        displayService.inDockMode = { exhibitions.exhibitionOn }
        displayService.register(bus)
        dockMode = DockMode(this, registry).also { it.register(bus) }
        systemService = SystemService(jsServices.root, java.io.File(filesDir, "systemservice.json"))
        systemService.onPreferenceChanged = { key, value -> onPreferenceChanged(key, value) }
        systemService.hostPreference = { key -> hostPreference(key) }
        systemService.register(bus)
        // webOS's ringtones, in Android's notification sounds and webOS's pickers alike (Ringtones).
        Ringtones(this, jsServices.root).also { it.register(bus); it.install() }
        registerReceiver(systemReceiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_CHANGED); addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_SCREEN_ON); addAction(android.content.Intent.ACTION_SCREEN_OFF)
        })
        watchingSystem = true
        audio = AudioService(this, startMuted = systemService.get("systemSounds") == false,
            playFeedback = { sounds.feedback(it) },
            // Music holds media/lockVolumeKeys while it is in front: the keys work its volume.
            lockVolumeKeys = { on -> volumeControlStream = if (on) android.media.AudioManager.STREAM_MUSIC else android.media.AudioManager.USE_DEFAULT_STREAM_TYPE })
        audio.register(bus)
        // Lunacy's own service, on its own name: the environment it really runs in, and
        // Android's settings screens for the settings Android owns.
        lunacyService = LunacyService(this, registry, jsServices, jsServices.root, object : ShellHooks {
            override fun display() = displayInfo()
            override fun requestPermissions(permissions: List<String>, answer: (granted: Boolean, askAgain: Boolean) -> Unit) =
                askPermissions(permissions, answer)
            override fun startForResult(intent: android.content.Intent) = runCatching { startActivityForResult(intent, HOME_ROLE_REQUEST) }.isSuccess
            override fun onLayoutChanged() = reshell()
            override fun wallpaper() = wallpaperSource()
            override fun onFirstUseDone() = afterFirstUse()
            override val hidden get() = hiddenApps
            override fun launcherApps() = allLaunchPoints()
            override fun appIcon(app: AppInfo) = luna.appIcon(app)
            override fun pinStatus() = homeScreenPins.status()
            override fun pinToHome(app: AppInfo, icon: android.graphics.Bitmap?) = homeScreenPins.pin(app, icon)
        }).also { it.register(bus) }
        val connections = ConnectionManager(this) { sleepWake.blocked }.also { it.register(bus) }
        closeOnDestroy += connections::stop
        sleepWake.onBlockedChanged = connections::changed
        sleepWake.onBlocked = activityManager::networkBlocked
        // Secrets, where the accounts service keeps each account's credentials.
        KeyManager(java.io.File(filesDir, "keymanager.json")).register(bus)
        DeviceProfileService(this) { profile }.register(bus)
        NetTools(this).register(bus)
        // webOS's downloader, which apps hand every file fetch to: drPodder's episodes and
        // album art, MeTube's "download first". It writes into the webOS tree.
        DownloadManager(jsServices.root).register(bus)
        ImageService(jsServices.root).register(bus)
        val db8 = Db8("com.palm.db", java.io.File(filesDir, "db8.sqlite"),
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0).also { it.register(bus) }
        db8.registerPickerRelay(bus)
        val tempdb = Db8("com.palm.tempdb", null).also { it.register(bus) }
        closeOnDestroy += db8::close; closeOnDestroy += tempdb::close
        // Documents', photos' and videos' indexes, answered from the folders themselves (FileIndex, MediaIndex).
        FileIndex(jsServices.root).attach(db8)
        // The music library, from Android's media library (MusicLibrary): its songs go in with the ringtones.
        val musicLibrary = MusicLibrary(this, jsServices.root).also { it.attach(db8) }
        MediaIndex(jsServices.root).also { it.music = musicLibrary::songs }.attach(db8, bus)
        // After db8: it reads its persisted activities back as it registers.
        power.register()
        activityManager.register(bus)
        powerService.register(bus)
        sleepWake.start()
        configurator = Configurator(files, db8, tempdb, webos.root, bus)
        configurator.run()
        // Android's contacts and calendars, once the owner adds that account (AndroidContacts, AndroidCalendar).
        AndroidContacts(this, jsServices.root, db8).also { it.attach(bus); closeOnDestroy += it::close }
        AndroidCalendar(this, db8).also { it.attach(bus); closeOnDestroy += it::close }
        // Exchange's security policies and the device lock, once the kinds are there (SecurityPolicy).
        securityPolicy = SecurityPolicy(bus, lockHost, java.io.File(filesDir, "securitypolicy.json"))
        securityPolicy.register()
        securityPolicy.load()
        applicationManager = ApplicationManager(this, registry, handlers, addedLaunchPoints, files, packages, jsServices, webos, appManagerShell)
            .also { it.register(bus) }
        val keys = Keys(this).also { it.register(bus) }
        // What Music plays is Android's media session too; its controls come back as webOS's media keys (NowPlaying).
        MediaBroadcast(this).register(bus)
        NowPlaying.onKey = { k -> runOnUiThread { keys.mediaKey(k) } }
        universalSearch = UniversalSearch(this) { registry.apps }.also {
            it.register(bus)
            it.onChange = { runOnUiThread { if (::justTypePanel.isInitialized) justTypePanel.reload() } }
            if (::justTypePanel.isInitialized) justTypePanel.search = it
        }
        // browserserver's own calls, which the Web app makes for its Preferences.
        bus.register("com.palm.browserServer", "clearCache") { _, _, reply ->
            android.webkit.WebView(this).apply { clearCache(true); destroy() }
            reply(Bus.ok())
        }
        bus.register("com.palm.browserServer", "clearCookies") { _, _, reply ->
            // webOS's browser kept its own cookies; Android's WebView has one jar for the whole
            // of Lunacy, so clearing the browser's would sign every app out as well.
            reply(Bus.error("Lunacy's browser shares its cookies with every app, so they can't be cleared on their own"))
        }
    }

    // ---- shell state ----

    /**
     * Removes an installed app (the launcher's Remove): its windows close, then its package
     * goes, and the launcher, services and db8 kinds are reloaded.
     */
    /**
     * An upgrade of Lunacy can bring a newer copy of an app that was also installed as a
     * package. The older package goes and the bundled copy takes over ([AppFiles.outdatedInstalled]);
     * the app keeps its data. Waits for the removals, so the first app list is already right.
     */
    private fun removeOutdatedPackages() {
        val outdated = files.outdatedInstalled()
        if (outdated.isEmpty()) return
        val done = java.util.concurrent.CountDownLatch(outdated.size)
        for (id in outdated) {
            Log.i(AppServer.TAG, "bundled $id is newer than the installed package: removing it")
            packages.remove(id) { done.countDown() }
        }
        done.await(30, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun remove(app: AppInfo) {
        // A pinned shortcut is unpinned; the app that pinned it stays.
        if (app.androidShortcut != null) { pinned.remove(app); return }
        if (app.androidComponent != null) { androidApps.uninstall(app); return }
        app.launchPoint?.let { lp -> applicationManager.removeLaunchPoint(lp.launchPointId); return }
        removeInstalled(app) { error ->
            systemBanner("", if (error == null) "${app.title} removed" else "Couldn't remove ${app.title}: $error")
        }
    }

    /**
     * An installed app's package removed, by the launcher or an installer on the bus: its
     * windows close and its viewport setting goes first, then the package, and the launcher,
     * services, db8 kinds and dock are brought up to date before [done] hears how it went.
     */
    private fun removeInstalled(app: AppInfo, done: (String?) -> Unit) {
        running[app.id]?.toList()?.forEach { w -> onWindowClosed(w) }
        FixedViewport.forget(this, app.id)
        packages.remove(app.id, app.dir) { error ->
            appsChanged()
            jsServices.reload()
            showDock()
            done(error)
        }
    }

    // ---- the dock ----

    private val dockPrefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    /**
     * The dock's apps, as the user arranged them; until they do, Lunacy's
     * assets/luna/default-dock-positions.json, in the shape of the device's
     * /usr/palm/default-dock-positions.json (whose "quicklauncher" LunaSysMgr read for a fresh
     * dock): Web, Email, Calendar, Photos & Videos and App Catalog (codepoet, 2026-10-08; the
     * TouchPad's had Messaging where Lunacy has App Catalog). A phone's dock, four apps, has a
     * list of its own, "quicklauncher-phone": Web, Email, Calendar and App Catalog (codepoet,
     * 2026-11-04).
     */
    private fun dock(): List<String> {
        val a = dockPrefs.getString("dock", null)?.let { runCatching { org.json.JSONArray(it) }.getOrNull() }
            ?: runCatching {
                val o = org.json.JSONObject(assets.open("luna/default-dock-positions.json").bufferedReader().use { it.readText() })
                (if (luna.phone) o.optJSONArray("quicklauncher-phone") else null) ?: o.getJSONArray("quicklauncher")
            }.getOrDefault(org.json.JSONArray())
        return (0 until a.length()).map { a.optString(it).removeSuffix("_default") }.take(quickLaunch.maxItems)
    }

    /**
     * The dock as arranged, from [shownDock]'s ids. A hidden app keeps its slot, so it comes
     * back where it was when it is shown again, while there is room for it.
     */
    private fun setDock(ids: List<String>) {
        val all = ids.distinct().toMutableList()
        dock().forEachIndexed { i, id -> if (id in hiddenApps && id !in all && all.size < quickLaunch.maxItems) all.add(minOf(i, all.size), id) }
        dockPrefs.edit().putString("dock", org.json.JSONArray(all.take(quickLaunch.maxItems)).toString()).apply()
        showDock()
    }

    /** The dock's ids that are still apps and aren't hidden, in the order they are drawn. */
    private fun shownDock() = dock().filter { appById(it) != null && it !in hiddenApps }

    private fun showDock() { quickLaunch.apps = shownDock().mapNotNull { appById(it) } }

    /**
     * A launcher icon dropped on the dock. It joins at the slot under it; one already on the
     * dock moves there. On a full dock it takes the place of the icon it lands on (LunaCE
     * refused a sixth icon; swapping is Lunacy's).
     */
    private fun dropOnDock(app: AppInfo, x: Float) {
        val ids = shownDock().toMutableList()
        val present = ids.indexOf(app.id)
        when {
            present >= 0 -> { ids.removeAt(present); ids.add(quickLaunch.slotAt(x, ids.size + 1), app.id) }
            ids.size < quickLaunch.maxItems -> ids.add(quickLaunch.slotAt(x, ids.size + 1), app.id)
            else -> ids[quickLaunch.slotAt(x, ids.size)] = app.id
        }
        setDock(ids)
    }

    private fun exitEditMode() {
        launcher.exitEditMode()
        quickLaunch.editing = false
    }

    override fun onMaximized(card: Card) {
        // A running app brought forward ends Exhibition too (setCardWindowMaximized); leaving
        // it comes back here with the card.
        if (exhibitions.cardArrivedInExhibition()) return
        if (card.window.appId == directApp) directShown = true
        pendingLoads[card.window]?.run()
        quickLaunch.cancelLaunchFeedback()
        if (justTypePanel.showing) justTypePanel.close()
        // A keyboard asked for while this card was still opening (see keyboard()).
        if (keyboardWanted == card.window) {
            keyboardWanted = null
            card.window.requestFocus()
            imm.showSoftInput(card.window, 0)
        }
        // An emulated card has its title on its own status bar; the main one shows the carrier
        // string (SystemUiController::updateStatusBarTitle passes no title for it).
        statusBar.title = if (card.window.emulated) StatusBar.CARRIER_TEXT else registry.get(card.window.appId)?.title ?: card.window.appId
        // The app's own colour, if it set one: LunaSysMgr read it as the card was maximized
        // and not again until the next time (measured on the reference TouchPad).
        statusBar.setMode(StatusBar.Mode.APP, card.window.statusBarColor)
        statusBar.slide(card.fullScreen)
        applyOrientation(card.window)
        if (launcherOpen) closeLauncher()
        fade(justType, false); showDock(false)
        // Not a headless app's root behind its launching card: a TouchPad never activates that
        // page, and Enyo's app menu goes to the first active window it finds, which would be it.
        if (!card.awaitingWindow) card.window.setStageActive(true)
        cards.cards.filter { it != card }.forEach { it.window.setStageActive(false) }
        // The active card's page holds the focus, as webOS's did. Without it document.hasFocus()
        // is false, and Chromium then sets activeElement without dispatching focus or focusin -
        // so an app that focuses a field itself is invisible to the bridge and gets no keyboard.
        card.window.requestFocus()
    }


    /**
     * The title's ▾: LunaSysMgr relaunched the maximized card's app with
     * {"palm-command":"open-app-menu"}, and Enyo and Mojo open their app menu on that.
     */
    private fun toggleAppMenu() {
        val card = cards.maximized ?: return
        launch(card.window.appId, JSONObject().put("palm-command", "open-app-menu"))
    }

    override fun onCardView() {
        // Leaving an app opened from Android's home screen goes back there (HomeScreenPins).
        if (directApp != null && directShown) {
            directApp = null; directShown = false
            moveTaskToBack(true)
        }
        hideKeyboard()
        statusBar.title = StatusBar.CARRIER_TEXT
        statusBar.setMode(if (launcherOpen) StatusBar.Mode.LAUNCHER else StatusBar.Mode.CARDS)
        statusBar.slide(false)
        applyOrientation(null)
        if (!launcherOpen) fade(justType, true)
        showDock(true)
        cards.cards.forEach { it.window.setStageActive(false) }
    }

    /** SystemUiController::enterOrExitCardReorder: the dock fades while a card is being reordered. */
    override fun onReorder(active: Boolean) = showDock(!active)

    /**
     * Lunacy's "Edge swipe for home" (Screen & Lock): a swipe in from a side edge does what
     * Android's Back does, the TouchPad's Home. For Android's three-button navigation, which
     * has no edge gesture; with gesture navigation Android takes those edges itself. The
     * setting is the toggle's own key, sysUiEnableNextPrevGestures, on by default as on the
     * reference TouchPad. A delta: the TouchPad's side edges did nothing (luna-deltas.md B15).
     */
    override fun onSideSwipe() {
        if (systemService.get("sysUiEnableNextPrevGestures") != false) homePressed()
    }

    override fun onThrownAway(card: Card) {
        card.window.evaluateJavascript("try{window.close()}catch(e){}", null)
        closeWindow(card.window)
    }

    /**
     * Android's back button is the TouchPad's home button (webOS tablets had no back button).
     * Same order as LunaCE's SystemUiController, Key_CoreNavi_Home: close what's open on top
     * (dashboard, alert, menu, launcher, search), else minimize a maximized card, else
     * toggle the launcher.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = homePressed()

    private fun homePressed() {
        if (exhibitions.menuOpen) { exhibitions.closeExhibitionMenu(); return }
        if (exhibitions.exhibitionOn) { exhibitions.setExhibition(false); return }
        if (notifications.menu.isOpen || systemMenu.isOpen) { closeMenu(); return }
        notifications.popups.newest()?.let { onWindowClosed(it); return }
        if (justTypePanel.showing) { closeJustType(); return }
        if (tabDialog.showing) { tabDialog.dismiss(); return }
        if (groupOverlay.showing) { groupOverlay.close(); return }
        if (launcher.editing || quickLaunch.editing) { exitEditMode(); return }
        if (launcherOpen) { closeLauncher(); return }
        val max = cards.maximized
        if (max != null) { cards.showCardView(); return }
        toggleLauncher()
    }

    // ---- Just Type ----

    /**
     * Just Type opens over the card view or the launcher, cross-fading in (150 ms); the pill
     * and the dock go (reference §3.1), and the status bar says "Just Type" with its ▾, as
     * SystemUiController::updateStatusBarTitle has it.
     */
    private fun openJustType(initial: String = "") {
        if (cards.maximized != null) return
        if (launcherOpen) closeLauncher()
        justTypePanel.apps = (registry.launchPoints + addedById.values + androidById.values).filter { it.id !in hiddenApps }
        justTypePanel.open(initial)
        fade(justType, false); showDock(false)
        statusBar.setMode(StatusBar.Mode.APP)
        statusBar.title = "Just Type"
    }

    private fun closeJustType() {
        if (!justTypePanel.showing) return
        justTypePanel.close()
        if (cards.maximized == null) onCardView()
    }

    // ---- overlays (reference §3.1, §3.2) ----

    private fun toggleLauncher() = if (launcherOpen) closeLauncher() else openLauncher()

    /** Slides up from below the screen; the cards are hidden once it is fully open. */
    private fun openLauncher() {
        if (cards.maximized != null) return
        launcherOpen = true
        // SystemUiController::setLauncherShown, and the launcher's own title in the bar
        // (updateStatusBarTitle: "Launcher", with no ▾).
        sounds.feedback("LauncherOpenApp")
        statusBar.title = LAUNCHER_TITLE
        launcher.visibility = View.VISIBLE
        statusBar.setMode(StatusBar.Mode.LAUNCHER)
        launcher.translationY = launcher.height.toFloat()
        launcher.animate().translationY(0f).setDuration(LAUNCHER_MS).setInterpolator(Easing.InOutQuint)
            .withEndAction { if (launcherOpen) cards.visibility = View.INVISIBLE }.start()
        fade(justType, false)
    }

    private fun closeLauncher() {
        if (!launcherOpen) return
        launcherOpen = false
        sounds.feedback("LauncherCloseApp")
        cards.visibility = View.VISIBLE
        launcher.animate().translationY(launcher.height.toFloat()).setDuration(LAUNCHER_MS).setInterpolator(Easing.InOutQuint)
            .withEndAction { if (!launcherOpen) { launcher.visibility = View.INVISIBLE; launcher.cancelLaunchFeedback(); exitEditMode() } }.start()
        tabDialog.dismiss()
        groupOverlay.close()
        if (cards.maximized == null) { fade(justType, true); statusBar.setMode(StatusBar.Mode.CARDS); statusBar.title = StatusBar.CARRIER_TEXT }
    }

    private fun fade(v: View, show: Boolean) {
        v.animate().cancel()
        if (show) v.visibility = View.VISIBLE
        v.animate().alpha(if (show) 1f else 0f).setDuration(FADE_MS).setInterpolator(Easing.OutCubic)
            .withEndAction { if (!show) v.visibility = View.INVISIBLE }.start()
    }

    /** The dock slides by its own height and fades (quickLaunchDuration, quickLaunchFadeDuration). */
    private fun showDock(show: Boolean) {
        val h = quickLaunch.height.toFloat().takeIf { it > 0 } ?: luna.px(QuickLaunch.barHeight(luna.phone)).toFloat()
        quickLaunch.animate().cancel()
        if (show) quickLaunch.visibility = View.VISIBLE
        quickLaunch.animate().translationY(if (show) 0f else h).setDuration(DOCK_MS).setInterpolator(Easing.OutCubic).start()
        quickLaunch.animate().alpha(if (show) 1f else 0f).setDuration(FADE_MS).setInterpolator(Easing.OutCubic)
            .withEndAction { if (!show) quickLaunch.visibility = View.INVISIBLE }.start()
    }

    /**
     * A display cutout (a phone's camera): the window takes the whole screen, cutout and all,
     * where Android would leave the cutout's band black. At the top, the shell runs up to the
     * screen's edge as on a device without one: the status bar lays its items out around the
     * camera, and what the camera covers below the bar is lost behind it. A cutout at the
     * side or bottom (the phone turned) stays a black band beside the shell, so a card's
     * edge never goes under it. Luna-deltas A20.
     */
    private fun fitCutout(screen: FrameLayout, root: FrameLayout) {
        if (android.os.Build.VERSION.SDK_INT < 28) return
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        screen.setOnApplyWindowInsetsListener { _, insets ->
            val cut = insets.displayCutout
            val l = cut?.safeInsetLeft ?: 0; val r = cut?.safeInsetRight ?: 0; val b = cut?.safeInsetBottom ?: 0
            if (root.paddingLeft != l || root.paddingRight != r || root.paddingBottom != b) {
                root.setPadding(l, 0, r, b)
                wallpaperView.layoutParams = (wallpaperView.layoutParams as FrameLayout.LayoutParams).apply { setMargins(l, 0, r, b) }
            }
            // The top corners. A screen that reports none but has a cutout in it (the Kyocera)
            // is taken to have rounded ones all the same, a tenth of its short side in radius.
            fun corner(pos: Int): Float = if (android.os.Build.VERSION.SDK_INT >= 31) insets.getRoundedCorner(pos)?.radius?.toFloat() ?: 0f else 0f
            var tl = corner(android.view.RoundedCorner.POSITION_TOP_LEFT)
            var tr = corner(android.view.RoundedCorner.POSITION_TOP_RIGHT)
            if (cut != null && tl == 0f && tr == 0f) {
                val dm = realMetrics()
                tl = minOf(dm.widthPixels, dm.heightPixels) * ASSUMED_CORNER; tr = tl
            }
            // A side the shell is kept off (the cutout's, turned) is clear of its corner.
            statusBar.corners = (if (l > 0) 0f else tl) to (if (r > 0) 0f else tr)
            // The camera's span across the bar, in the bar's x, a 3 px margin either side.
            val bar = luna.px(StatusBar.HEIGHT)
            statusBar.cutout = cut?.boundingRects?.filter { it.top < bar && !it.isEmpty }
                ?.takeIf { it.isNotEmpty() }
                ?.let { rs -> (rs.minOf { it.left } - l - luna.px(3f))..(rs.maxOf { it.right } - l + luna.px(3f)) }
            insets
        }
    }

    /** Full screen: hide Android's bars; a swipe from the edge shows them for a moment. */
    private fun goImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    companion object {
        /** How long the slider's override outlasts the finger: the fast ramp (0.7 a second) crossing the whole range, and some. */
        const val BRIGHTNESS_HOLD_MS = 3000L
        /**
         * The reference TouchPad's /etc/palm/luna-platform.conf, [LaunchAtBoot] and [KeepAlive]
         * (it overrides luna.conf's, which name only the phone app); used in phone mode too.
         * Measured there: closing Email's last card leaves its headless page running and
         * Clock's (not listed) ends; closing the headless page itself ends the app, and
         * nothing starts it again. Phone and Music Player aren't in Lunacy.
         */
        val LAUNCH_AT_BOOT = listOf("com.palm.app.phone", "com.palm.app.email", "com.palm.app.calendar", "com.palm.app.messaging")
        val KEEP_ALIVE = setOf("com.palm.app.email", "com.palm.app.calendar", "com.palm.app.messaging", "com.palm.app.photos", "com.palm.app.musicplayer")

        /** The shell that is up, if any: a second one hands off to it ([onCreate]). */
        private var live: java.lang.ref.WeakReference<ShellActivity>? = null
        /** onActivityResult code for Android's credential confirmation (matchDevicePasscode). */
        /** Request codes: the storage permissions asked at start, the system's home-role dialog, the lock screen's confirmation, and the bus's permission requests from FIRST_PERMISSION_REQUEST up. */
        private const val STORAGE_REQUEST = 1
        private const val HOME_ROLE_REQUEST = 200
        /** The orientations a card can be fixed at, and the Android orientation each holds the screen in. */
        private val ORIENTATIONS = mapOf(
            "up" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            "down" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            "right" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            "left" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            "landscape" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            "portrait" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        )
        private const val CONFIRM_LOCK = 201
        private const val FIRST_PERMISSION_REQUEST = 1000
        /** The launcher's launch point title, com.palm.launcher's on the reference TouchPad. */
        const val LAUNCHER_TITLE = "Launcher"
        /** An unreported rounded corner's radius on a screen with a cutout, as a part of its short side (fitCutout). */
        const val ASSUMED_CORNER = 0.1f
        const val LAUNCHER_MS = 350L   // reference TouchPad lunaAnimations.conf: launcherDuration 350, curve 15
        const val DOCK_MS = 350L       // quickLaunchDuration
        const val FADE_MS = 200L       // quickLaunchFadeDuration
        /** Long enough for the launch glow to be drawn before the card work starts. */
        const val LAUNCH_DELAY_MS = 60L
        /** A change of face in the dock: AnimationSettings' dockFadeDockAnimationDuration. */
        /** Android's screen saver asking for Exhibition; see ExhibitionDream. */
        const val EXTRA_EXHIBITION = "exhibition"
        const val MEMORY_CHECK_MS = 10_000L
        /** WindowedWebApp's kShowWindowTimeoutMs: how long a loaded page gets to call stageReady. */
        const val SHOW_WINDOW_TIMEOUT_MS = 3000L
        /** How long a headless app outlives its last visible window: long enough to hear that window go. */
        const val HEADLESS_LINGER_MS = 1000L
        /** Lunacy's app that shows Android's notifications as dashboards. */
        const val ANDROID_NOTIFICATIONS_APP = "org.webosarchive.lunacy.notifications"
        /** How long after a headless app is up its placeholder card waits for it to open one. */
        const val PLACEHOLDER_GRACE_MS = 1000L
        const val MEMORY_CALM_MS = 30_000L
        /** Just Type's app id on a device (luna-applauncher): what its bus calls come from, and what db8 grants read to. */
        const val JUST_TYPE = "com.palm.launcher"
    }

    /**
     * Just type: a keyboard's printable key in the card view or the launcher opens Just Type
     * with that character, as on a TouchPad with a keyboard paired (codepoet, 2026-10-02).
     * Not while a card is up, a text field (a tab's name, a group's) has the focus, or a
     * modifier is held.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && !justTypePanel.showing && cards.maximized == null &&
            !exhibitions.exhibitionOn && !tabDialog.showing && currentFocus !is android.widget.EditText &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
            val ch = event.unicodeChar
            if (ch != 0 && (ch and android.view.KeyCharacterMap.COMBINING_ACCENT) == 0 && !Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                openJustType(String(Character.toChars(ch)))
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** A hardware keyboard's Escape is webOS's back gesture, delivered to the maximized card. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
            cards.maximized?.window?.sendBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
