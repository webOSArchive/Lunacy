package org.webosarchive.lunacy.shell

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.WebView
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppFiles
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.BuildVariant
import org.webosarchive.lunacy.card.LocationService
import org.webosarchive.lunacy.card.PhotoCache
import org.webosarchive.lunacy.card.UpdateService
import org.webosarchive.lunacy.card.jsString
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.ApplicationManager
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.Packages
import org.webosarchive.lunacy.card.SystemProperties
import org.webosarchive.lunacy.card.WindowHost
import org.webosarchive.lunacy.card.ActivityManager
import org.webosarchive.lunacy.card.AndroidCalendar
import org.webosarchive.lunacy.card.AndroidContacts
import org.webosarchive.lunacy.card.AndroidNotifications
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
import org.webosarchive.lunacy.card.PageHooks
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
import org.webosarchive.lunacy.card.WebosRoot

/**
 * The simulated Luna shell: wallpaper, cards, status bar, "Just type", quick launch.
 * Starts as a normal full-screen activity; nothing here assumes it isn't the home launcher.
 */
class ShellActivity : Activity(), WindowHost {
    override lateinit var server: AppServer
    override val bus = Bus()
    /** The shell's face: launcher, Just Type, dock, status bar and menus, wallpaper (ShellChrome). */
    private lateinit var chrome: ShellChrome
    private val luna: Luna get() = chrome.luna
    private lateinit var registry: AppRegistry
    /** Which app opens which type of file: installed apps' mimeTypes and the system's (ContentHandlers). */
    private lateinit var handlers: ContentHandlers
    /** Launch points apps have added (applicationManager/addLaunchPoint). */
    private lateinit var addedLaunchPoints: LaunchPoints
    private lateinit var packages: Packages
    private lateinit var jsServices: JsServices
    /** The TouchPad's native system services (mail, the file cache), from the webOS root. */
    private lateinit var nativeServices: NativeServices
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
    /** Whether the shell is on screen (between onStart and onStop). */
    private var started = false
    /**
     * Whether the shell has been out of sight since it was last in front (onStop to onResume).
     * Android brings it back from another app with onStart before onNewIntent, so [started]
     * can't tell that Home from a Home press with the shell up: this can.
     */
    private var covered = false
    /** Keeps a stand-alone Lunacy with mail running in the background; made with db8. */
    private var backgroundWork: BackgroundWork? = null
    override fun onStart() {
        super.onStart(); started = true; sleepWake.check("shell")
        backgroundWork?.check()
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
        if (interactive && cardHost.built) cards.maximized?.window?.let { it.setStageActive(false); coveredCard = it }
        if (::chrome.isInitialized) chrome.covered()
        started = false; covered = true; super.onStop()
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
    private fun setLauncherApps() = chrome.setLauncherApps()
    private fun showDock() = chrome.showDock()
    private var lunacyService: LunacyService? = null
    private lateinit var applicationManager: ApplicationManager
    /** What the notifications ask of the shell. */
    private val notificationShell = object : NotificationHost.Host {
        override val luna get() = this@ShellActivity.luna
        override val registry get() = this@ShellActivity.registry
        override val server get() = this@ShellActivity.server
        override val statusBar get() = chrome.statusBar
        override val sounds get() = this@ShellActivity.sounds
        override val blinkNotifications get() = ::systemService.isInitialized && systemService.get("BlinkNotifications") == true
        override fun launch(appId: String, params: JSONObject?) = lifecycle.launch(appId, params)
        override fun closeWindow(window: AppWindow) = lifecycle.closeWindow(window)
        override fun closeMenu() = chrome.closeMenu()
        override fun toggleMenu() = chrome.toggleMenu()
    }
    /** What Exhibition asks of the shell: its chrome, read afresh, since a reshell rebuilds it. */
    private val exhibitionHost = object : ExhibitionController.Host {
        override val luna get() = this@ShellActivity.luna
        override val registry get() = this@ShellActivity.registry
        override val dockMode get() = this@ShellActivity.dockMode
        override val statusBar get() = chrome.statusBar
        override val cards get() = this@ShellActivity.cards
        override val menuScrim get() = chrome.menuScrim
        override val inFront get() = this@ShellActivity.inFront
        override val launcherOpen get() = chrome.launcherOpen
        override val notificationsMenuOpen get() = notifications.menu.isOpen
        override fun launchForDock(appId: String, params: JSONObject) = launch(appId, params, Placement.DOCK)
        override fun closeWindow(window: AppWindow) = lifecycle.windowClosed(window)
        override fun closeMenu() = chrome.closeMenu()
        override fun closeLauncher() = chrome.closeLauncher()
        override fun hideKeyboard() = chrome.hideKeyboard()
        override fun releaseOrientation() = chrome.applyOrientation(null)
        override fun holdScreen() = this@ShellActivity.holdScreen()
        override fun showCardsAgain() { if (cards.maximized != null) chrome.onMaximized(cards.maximized!!) else chrome.onCardView() }
        override fun showChrome(exhibiting: Boolean) = chrome.showChrome(exhibiting)
    }
    /** What the application manager asks of the shell. */
    private val appManagerShell = object : ApplicationManager.Shell {
        override fun launch(appId: String, params: JSONObject?, placement: Placement, launchedBy: String?) =
            lifecycle.launch(appId, params, placement, launchedBy)
        override fun install(source: String, requester: String, onProgress: (Int) -> Unit, banners: Boolean, onDone: (Packages.Result) -> Unit) =
            this@ShellActivity.install(source, requester, onProgress, banners, onDone)
        override val pdkAvailable get() = pdkRuntime.available
        override val running: Map<String, List<AppWindow>> get() = lifecycle.apps
        override fun closeWindow(window: AppWindow) = lifecycle.windowClosed(window)
        override fun appsChanged() = this@ShellActivity.appsChanged()
        override fun launchPointsChanged() { setLauncherApps(); showDock() }
        override fun showDock() = chrome.showDock()
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

    /** What the card layer's owner asks of the shell. */
    private val cardShell = object : CardHost.Host {
        override val luna get() = this@ShellActivity.luna
        override val registry get() = this@ShellActivity.registry
        override val sounds get() = this@ShellActivity.sounds
        override val listener get() = chrome
        override fun screenOrientation() = this@ShellActivity.screenOrientation()
        override fun cardArriving() { exhibitions.cardArrivedInExhibition() }
        override fun noCardUp() = chrome.noCardUp()
        override fun toggleAppMenu() = chrome.toggleAppMenu()
        override fun showKeyboard(window: AppWindow) = chrome.showKeyboardForced(window)
    }
    /** The cards and the headless windows (CardHost). After [shellHandler], which it posts to. */
    private val cardHost: CardHost by lazy { CardHost(this, cardShell, shellHandler) }
    private val cards: CardLayer get() = cardHost.cards

    /** com.palm.universalsearch: what Just Type offers besides apps, and its settings. */
    private lateinit var universalSearch: UniversalSearch
    /** Banners, dashboards and alerts (NotificationHost). */
    private val notificationHost: NotificationHost = NotificationHost(this, notificationShell)
    private val notifications: Notifications get() = notificationHost.notifications
    private val notificationsAtBottom: Boolean get() = notificationHost.notificationsAtBottom
    private val area: NotificationArea? get() = notificationHost.area
    private lateinit var dockMode: DockMode
    /** Exhibition and its dock (ExhibitionController). */
    private val exhibitions: ExhibitionController = ExhibitionController(this, exhibitionHost)
    /** Whether the shell is the activity in front, which decides where a screen saver's Exhibition goes back to. */
    private var inFront = false

    /** A debuggable build (the debug build type), which keeps the adb development extras. */
    private val devBuild by lazy { applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    /** What the shell's face asks of the rest of the shell. */
    private val chromeShell = object : ShellChrome.Host {
        override val registry get() = this@ShellActivity.registry
        override val sounds get() = this@ShellActivity.sounds
        override val systemService get() = this@ShellActivity.systemService
        override val displayService get() = this@ShellActivity.displayService
        override val universalSearch: UniversalSearch? get() = if (this@ShellActivity::universalSearch.isInitialized) this@ShellActivity.universalSearch else null
        override val hiddenApps get() = this@ShellActivity.hiddenApps
        override fun launchPoints() = this@ShellActivity.launchPoints()
        override fun justTypeApps() = (registry.launchPoints + addedById.values + androidById.values).filter { it.id !in hiddenApps }
        override fun appById(id: String) = this@ShellActivity.appById(id)
        override fun remove(app: AppInfo) = this@ShellActivity.remove(app)
        override fun launcherAppsChanged() { lunacyService?.launcherAppsChanged() }
        override fun askPermissions(permissions: List<String>, done: (granted: Boolean, askAgain: Boolean) -> Unit) =
            this@ShellActivity.askPermissions(permissions, done)
        override fun systemBanner(appId: String, message: String) = this@ShellActivity.systemBanner(appId, message)
        override fun screenOrientation() = this@ShellActivity.screenOrientation()
        // Android's apps' icons are drawn at the shell's scale.
        override fun scaleChanged() { androidAppsAt = null }
    }
    /** What the apps' lifecycle asks of the shell. */
    private val lifecycleShell = object : AppLifecycle.Host {
        override val registry get() = this@ShellActivity.registry
        override val profile get() = this@ShellActivity.profile
        override val pdkRuntime get() = this@ShellActivity.pdkRuntime
        override val exhibitions get() = this@ShellActivity.exhibitions
        override val notificationHost get() = this@ShellActivity.notificationHost
        override fun addedLaunchPoint(id: String) = addedById[id]?.launchPoint
        override fun androidTile(id: String) = androidById[id]
        override fun openAndroid(app: AppInfo) = launchAndroid(app)
        override fun systemBanner(appId: String, message: String) = this@ShellActivity.systemBanner(appId, message)
        override fun lunacyIsHome() = homeScreenPins.lunacyIsHome()
        override fun closeMenu() = chrome.closeMenu()
        override fun onCardView() = chrome.onCardView()
        override fun windowGone(window: AppWindow) { if (screenAwake.remove(window)) holdScreen() }
    }
    /** The running apps and their windows (AppLifecycle). After [shellHandler], which it posts to. */
    private val lifecycle: AppLifecycle by lazy { AppLifecycle(this, this, bus, cardHost, lifecycleShell, shellHandler) }
    private fun launch(appId: String, params: JSONObject? = null, placement: Placement = Placement.CARD, launchedBy: String? = null) =
        lifecycle.launch(appId, params, placement, launchedBy)

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
        chrome = ShellChrome(this, chromeShell, bus, cardHost, lifecycle, exhibitions, notificationHost, shellHandler)
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
            // Not for a shell that has gone meanwhile: Android 17 relaunches an app it has just
            // updated beside the owner's own launch, finishing the first shell under it, and that
            // one's root was ready after its onDestroy had stopped everything. It started the
            // services and activities again on its closed bus, and launched the boot apps.
            runOnUiThread {
                if (isDestroyed || live?.get() !== this) { Log.i(AppServer.TAG, "webOS root ready for a shell that has gone"); return@runOnUiThread }
                jsServices.reload(); nativeServices.reload(); appsChanged(); activityManager.enable(); powerService.enable(); afterFirstUse(); androidNotificationsChanged(); FontWarmer(this, server).warmWhenIdle()
            }
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
        PinnedShortcuts.onChanged = ::shortcutsChanged
        NotificationIcons.onChanged = ::notificationIconsChanged
        AndroidNotifications.onChanged = ::androidNotificationsChanged
        if (android.os.Build.VERSION.SDK_INT >= 26) watchShortcuts()
        chrome.buildShell()
        // The splash was the window's background (Theme.Lunacy.Splash), which is what Android
        // painted while this was all being set up. The shell covers it now, so let the logo go
        // rather than hold the bitmap behind an opaque wallpaper for the rest of the session.
        cards.post { window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)) }
        reportedOrientation = screenOrientation()
        (getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
            .registerDisplayListener(displayListener, android.os.Handler(android.os.Looper.getMainLooper()))
        seedMediaInternal()
        chrome.onCardView()
        chrome.goImmersive()

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

    override fun onDestroy() {
        if (handedOff) { super.onDestroy(); return }
        if (::chrome.isInitialized) chrome.stop()
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
        lifecycle.destroyAll()
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
        // an Android app it only brings the shell back, as it was: the launcher still open if
        // the app was opened from it, a card still up if one was (webOS's Home came back to
        // what was there; codepoet, 2026-11-08). onResume, which clears [covered], comes after.
        if (intent.action == android.content.Intent.ACTION_MAIN && intent.hasCategory(android.content.Intent.CATEGORY_HOME) && started && !covered) chrome.homePressed()
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
            if (FormFactor.setSetting(this, v)) { intent.removeExtra("layout"); if (cardHost.built) chrome.reshell() }
            else Log.w(AppServer.TAG, "layout: $v is not auto, phone or tablet")
        }
        if (intent.hasExtra("launcher")) { intent.removeExtra("launcher"); if (started && !chrome.launcherOpen) chrome.openLauncher() }
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
            intent.getStringExtra(HomeScreenPins.EXTRA_APP)?.let { lifecycle.openDirect(it) }
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        lifecycle.trimMemory(level)
    }

    /**
     * Delayed shell actions. Not a view's queue: reshell replaces the shell's views, and what
     * was posted to one that has gone never runs (memory stayed "low", brightness held).
     */
    private val shellHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onResume() {
        super.onResume(); inFront = true; covered = false
        // Also here: Home pressed with the shell up comes back through onNewIntent and onResume,
        // and is how a shell just made the home app hears it is one.
        backgroundWork?.check()
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

    /** An Android app or a pinned shortcut, which Android opens. */
    private fun launchAndroid(a: AppInfo) {
        val opened = if (a.androidShortcut != null) pinned.launch(a) else androidApps.launch(a)
        if (!opened) systemBanner("", "Couldn't open ${a.title}")
    }

    override fun onWindowOpened(parent: AppWindow, child: AppWindow) = lifecycle.windowOpened(parent, child)
    override fun onWindowClosed(window: AppWindow) = lifecycle.windowClosed(window)

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
        target.evaluateJavascript(PageHooks.call(PageHooks.PASTE, jsString(text)), null)
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
        chrome.launcher.startInstall(source, name.removeSuffix(".ipk").substringBefore('_'))
        packages.install(source, progress = { p -> chrome.launcher.installProgress(source, p); onProgress(p) },
            // Named after the file at first, as the package is all there is; its app's own
            // title once the package is on the device and its appinfo.json can be read.
            title = { t -> chrome.launcher.installTitle(source, t) }) { r ->
            chrome.launcher.endInstall(source)
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
        lifecycle.launchBootTimeApps()
        // An install whose First Use came before its Running in the Background page: that
        // page by itself, once (codepoet, 2026-11-06: always ask).
        if (!LunacyService.backgroundAsked(this) && !LunacyService.backgroundAllowed(this)) {
            LunacyService.setBackgroundAsked(this)
            launch(LunacyService.FIRST_USE_APP, JSONObject().put("only", "background"))
        } else if (android.os.Build.VERSION.SDK_INT >= 23 && !LunacyService.locationAsked(this) &&
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Likewise its Wi-Fi Networks page, which explains the location question before the
            // System menu would put it up unexplained (codepoet, 2026-11-07). The page asks, and
            // that marks it asked; one page per start, so after the background page if both.
            launch(LunacyService.FIRST_USE_APP, JSONObject().put("only", "location"))
        }
        val prefs = java.io.File(webos.root, "var/luna/preferences")
        runCatching { prefs.mkdirs(); java.io.File(prefs, "ran-first-use").createNewFile() }
        if (java.io.File(prefs, "first-use-profile-created").exists()) return
        bus.call("", "palm://com.palm.accountservices/createNovaAccount", "{\"createDefaultAccount\":true}", privateBus = true) { r ->
            Log.i(AppServer.TAG, "firstuse-createDefaultAccount: $r")
        }
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

    /**
     * Android's notifications changed (AndroidNotifications): the Android Notifications app
     * hears it, or, not running, is started to show them, headless, as a service starts an app
     * on webOS. It ends again once its last dashboard goes. Not before the webOS root is up and
     * First Use has run.
     */
    private fun androidNotificationsChanged() {
        val service = lunacyService ?: return
        service.notificationsChanged()
        if (service.notificationsWatched || lifecycle.isRunning(ANDROID_NOTIFICATIONS_APP)) return
        if (!AndroidNotifications.any()) return
        if (!::webos.isInitialized || !LunacyService.firstUseDone(this)) return
        if (registry.get(ANDROID_NOTIFICATIONS_APP) == null) return
        launch(ANDROID_NOTIFICATIONS_APP, null, Placement.NO_CARD)
    }

    /**
     * An account's sync state was written (com.palm.account.syncstate:1, by a transport or the
     * accounts service): if one would put up a dashboard (a first sync, a removal, a login
     * that failed) and Sync Status isn't running, it is started, headless, to show it, as
     * SystemUI did on webOS. It ends again once its last dashboard goes.
     */
    private fun syncStateChanged(tempdb: Db8) {
        if (lifecycle.isRunning(SYNC_STATUS_APP)) return
        tempdb.count(SYNC_STATE_KIND, { o ->
            o.isNull("collectionId") && when (o.optString("syncState")) {
                "INITIAL_SYNC", "DELETE" -> true
                "ERROR" -> o.optString("errorCode") == "401_UNAUTHORIZED"
                else -> false
            }
        }) { n ->
            if (n == 0 || lifecycle.isRunning(SYNC_STATUS_APP)) return@count
            if (!::webos.isInitialized || !LunacyService.firstUseDone(this)) return@count
            if (registry.get(SYNC_STATUS_APP) == null) return@count
            launch(SYNC_STATUS_APP, null, Placement.NO_CARD)
        }
    }

    override fun activityId(window: AppWindow): Int = lifecycle.activityId(window)

    /** Called on the bridge's thread, the first time the page embeds a plugin. */
    override fun hybridPlugins(window: AppWindow): HybridPlugins? {
        val app = registry.get(window.appId) ?: return null
        return HybridPlugins(window, app, pdkRuntime) { webos.prepare() }
    }

    override fun stageFocused(window: AppWindow, focused: Boolean) = lifecycle.stageFocused(window, focused)

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

    override fun onPageLoaded(window: AppWindow) = cardHost.pageLoaded(window)
    override fun onStageReady(window: AppWindow) = cardHost.stageReady(window)
    override fun onPageDrawn(window: AppWindow) = cardHost.pageDrawn(window)

    override fun mediaBase() = mediaServer.base()

    override fun orientationRequested(window: AppWindow) {
        if (cards.maximized?.window == window) chrome.applyOrientation(window)
    }

    override fun fullScreen(window: AppWindow) = cardHost.fullScreen(window, chrome.statusBar)

    override fun activate(window: AppWindow) = cardHost.activate(window)

    // ---- keyboard and screen ----

    override fun keyboard(window: AppWindow, show: Boolean) = chrome.keyboard(window, show)

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
        lifecycle.windows.forEach { it.setScreenOrientation(o) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientation()
        chrome.goImmersive()
        // A foldable folded or opened: the screen may now call for the other layout. Once the
        // display has its new size (the configuration can arrive first).
        window.decorView.post { chrome.screenChanged() }
    }

    /**
     * A stored preference the shell owns. webOS worked the same way: the service keeps the
     * key, and whoever owns the thing it names acts on it. Keys nothing here owns are kept
     * and nothing more, as they were on a device.
     */
    private fun onPreferenceChanged(key: String, value: Any?) {
        when (key) {
            "wallpaper" -> {
                chrome.showWallpaper()
                // Android's own wallpaper follows, if the owner asked (Screen & Lock).
                if (HostWallpaper.isOn(this)) {
                    val open = chrome.wallpaperSource()
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
            override fun onLayoutChanged() = chrome.reshell()
            override fun wallpaper() = chrome.wallpaperSource()
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
        // OAuth sign-in for mail accounts, and the tokens the mail services' logins use (Docs/oauth.md).
        val own = lunacyService!!
        org.webosarchive.lunacy.card.OAuth(this, java.io.File(filesDir, "oauth/accounts.json"), own::ownApp,
            { id -> registry.get(id)?.userInstalled == false },
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0).register(bus)
        DeviceProfileService(this) { profile }.register(bus)
        NetTools(this).register(bus)
        // webOS's location service, over Android's: each webOS app and site is asked first, as on
        // webOS, by SystemUI's own Location alert (LocationService, Lunacy's System Alerts app).
        val location = LocationService(this, { id -> registry.get(id)?.title }, { type, id, name ->
            if (registry.get(LocationService.ALERTS_APP) == null) false
            else { launch(LocationService.ALERTS_APP, JSONObject().put("alert", "LocationAlert").put("type", type).put("appId", id).put("appName", name), Placement.NO_CARD); true }
        }, ::askPermissions).also { it.register(bus); closeOnDestroy += it::close }
        lifecycle.onAppEnded = { id -> location.appEnded(id) }
        // webOS's downloader, which apps hand every file fetch to: drPodder's episodes and
        // album art, MeTube's "download first". It writes into the webOS tree.
        DownloadManager(jsServices.root).register(bus)
        ImageService(jsServices.root).register(bus)
        val db8 = Db8("com.palm.db", java.io.File(filesDir, "db8.sqlite"),
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0).also { it.register(bus) }
        db8.registerPickerRelay(bus)
        val tempdb = Db8("com.palm.tempdb", null).also { it.register(bus) }
        closeOnDestroy += db8::close; closeOnDestroy += tempdb::close
        tempdb.onWrites(SYNC_STATE_KIND) { syncStateChanged(tempdb) }
        // Not the home app, with mail: a foreground service keeps the system from being frozen (BackgroundWork).
        backgroundWork = BackgroundWork(this, db8) { homeScreenPins.lunacyIsHome() }.also { it.start(); closeOnDestroy += it::stop }
        // Documents', photos' and videos' indexes, answered from the folders themselves (FileIndex, MediaIndex).
        FileIndex(jsServices.root).attach(db8)
        // The music library, from Android's media library (MusicLibrary): its songs go in with the ringtones.
        val musicLibrary = MusicLibrary(this, jsServices.root).also { it.attach(db8) }
        MediaIndex(jsServices.root).also { it.music = musicLibrary::songs }.attach(db8, bus)
        // Photos' full-view copies at the screen's size (PhotoCache; LunaRuntimes/photos-service 0001).
        PhotoCache.measure(this)
        PhotoCache.refresh(this, bus, jsServices.root)
        // After db8: it reads its persisted activities back as it registers.
        power.register()
        activityManager.register(bus)
        // webOS's update daemon, for Lunacy's releases on GitHub: System Updates asks it, and a
        // daily check announces a new release once (UpdateService, System Alerts). After the
        // activity manager, which keeps its daily activity.
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        UpdateService(this, bus, version, BuildVariant.apkName(this)) { found ->
            if (registry.get(LocationService.ALERTS_APP) == null || !LunacyService.firstUseDone(this)) false
            else { launch(LocationService.ALERTS_APP, JSONObject().put("alert", "UpdateAvailable").put("version", found), Placement.NO_CARD); true }
        }.also { it.register(); closeOnDestroy += it::close }
        powerService.register(bus)
        sleepWake.start()
        configurator = Configurator(files, db8, tempdb, webos.root, bus)
        configurator.run()
        // Sync Status shows accounts syncing in SystemUI's place, and reads the accounts as it did.
        db8.configure(emptyList(), listOf(org.json.JSONArray().put(org.json.JSONObject().put("type", "db.kind").put("object", "com.palm.account:1")
            .put("caller", SYNC_STATUS_APP).put("operations", org.json.JSONObject().put("read", "allow")))))
        // Android's contacts and calendars, once the owner adds that account (AndroidContacts, AndroidCalendar).
        val androidContacts = AndroidContacts(this, jsServices.root, db8).also { it.attach(bus); closeOnDestroy += it::close }
        AndroidCalendar(this, db8, androidContacts).also { it.attach(bus); closeOnDestroy += it::close }
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
            it.onChange = { runOnUiThread { chrome.searchChanged() } }
            chrome.searchReady(it)
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
        lifecycle.apps[app.id]?.toList()?.forEach { w -> lifecycle.windowClosed(w) }
        FixedViewport.forget(this, app.id)
        packages.remove(app.id, app.dir) { error ->
            appsChanged()
            jsServices.reload()
            showDock()
            done(error)
        }
    }

    /**
     * Android's back button is the TouchPad's home button (webOS tablets had no back button).
     * Same order as LunaCE's SystemUiController, Key_CoreNavi_Home: close what's open on top
     * (dashboard, alert, menu, launcher, search), else minimize a maximized card, else
     * toggle the launcher.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = chrome.homePressed()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) chrome.goImmersive()
    }

    companion object {

        /** The shell that is up, if any: a second one hands off to it ([onCreate]). */
        private var live: java.lang.ref.WeakReference<ShellActivity>? = null
        /** onActivityResult code for Android's credential confirmation (matchDevicePasscode). */
        /** Request codes: the storage permissions asked at start, the system's home-role dialog, the lock screen's confirmation, and the bus's permission requests from FIRST_PERMISSION_REQUEST up. */
        private const val STORAGE_REQUEST = 1
        private const val HOME_ROLE_REQUEST = 200
        /** The orientations a card can be fixed at, and the Android orientation each holds the screen in. */
        internal val ORIENTATIONS = mapOf(
            "up" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            "down" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            "right" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            "left" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            "landscape" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            "portrait" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        )
        private const val CONFIRM_LOCK = 201
        private const val FIRST_PERMISSION_REQUEST = 1000
        /** A change of face in the dock: AnimationSettings' dockFadeDockAnimationDuration. */
        /** Android's screen saver asking for Exhibition; see ExhibitionDream. */
        const val EXTRA_EXHIBITION = "exhibition"
        /** Lunacy's app that shows Android's notifications as dashboards. */
        const val ANDROID_NOTIFICATIONS_APP = "org.webosarchive.lunacy.notifications"
        /** Lunacy's app that shows accounts syncing as dashboards, as SystemUI did (SyncUI.syncDashboard). */
        const val SYNC_STATUS_APP = "org.webosarchive.lunacy.syncstatus"
        const val SYNC_STATE_KIND = "com.palm.account.syncstate:1"
    }

    /** A keyboard's printable key may open Just Type (ShellChrome.typed). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (::chrome.isInitialized && chrome.typed(event)) return true
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
