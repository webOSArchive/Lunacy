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
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.Packages
import org.webosarchive.lunacy.card.SystemProperties
import org.webosarchive.lunacy.card.WindowHost

/**
 * The simulated Luna shell: wallpaper, cards, status bar, "Just type", quick launch.
 * Starts as a normal full-screen activity; nothing here assumes it isn't the home launcher.
 */
class ShellActivity : Activity(), WindowHost, CardLayer.Listener {
    override lateinit var server: AppServer
    override val bus = Bus()
    private lateinit var luna: Luna
    private lateinit var registry: AppRegistry
    /** Launch points apps have added (applicationManager/addLaunchPoint). */
    private lateinit var addedLaunchPoints: org.webosarchive.lunacy.card.LaunchPoints
    private lateinit var packages: Packages
    private lateinit var jsServices: org.webosarchive.lunacy.card.JsServices
    private lateinit var webos: org.webosarchive.lunacy.card.WebosRoot
    private lateinit var mediaServer: org.webosarchive.lunacy.card.MediaServer
    private lateinit var configurator: org.webosarchive.lunacy.card.Configurator
    private lateinit var systemService: org.webosarchive.lunacy.card.SystemService
    private lateinit var audio: org.webosarchive.lunacy.card.AudioService
    private lateinit var displayService: org.webosarchive.lunacy.card.DisplayService
    /** The wallpaper view, reloaded when the preference changes. */
    private lateinit var wallpaperView: ImageView
    /** /media/cryptofs/apps: installed packages first, then the apps bundled in the APK. */
    private lateinit var files: AppFiles
    /** The webOS device Lunacy answers as, for every app-visible surface. */
    private val profile by lazy { org.webosarchive.lunacy.card.DeviceProfile.forScreen(this) }
    /** The PDK runtime (Docs/pdk.md): present in the 32-bit build, absent in the 64-bit one. */
    private val pdkRuntime by lazy {
        val d = profile
        // A PDK app saw its device's own screen, whatever way it was held: the TouchPad's
        // 1024 x 768 landscape panel, the Pre3's 480 x 800 portrait one. The card scales it.
        val (w, h, dpi) = if (d == org.webosarchive.lunacy.card.DeviceProfile.PRE3) Triple(480, 800, 260) else Triple(1024, 768, 132)
        org.webosarchive.lunacy.card.PdkRuntime(this, java.io.File(filesDir, "cryptofs/apps/usr/palm/applications"),
            w, h, dpi, d.platformVersion, d.modelNameAscii, org.webosarchive.lunacy.card.DeviceProfile.nduid(this))
    }
    /** Proof of concept: Android's own apps in the launcher, for Lunacy as the home screen. */
    private val androidApps by lazy { AndroidApps(this, files, luna.px(Launcher.Params.ICON.toInt())) }
    private var androidById: Map<String, AppInfo> = emptyMap()
    /** Whether the shell is on screen (between onStart and onStop): a Home press then is Lunacy's own. */
    private var started = false
    override fun onStart() { super.onStart(); started = true }
    override fun onStop() { started = false; super.onStop() }

    /** What the launcher shows: webOS apps, the launch points they added, then Android's. */
    private fun launchPoints(): List<AppInfo> {
        val android = androidApps.list()
        androidById = android.associateBy { it.id }
        return registry.launchPoints + addedTiles() + android
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
    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            // An update removes the old package and adds the new; wait for the second.
            if (intent.action == android.content.Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(android.content.Intent.EXTRA_REPLACING, false)) return
            if (pkg == packageName) return
            androidApps.forget(pkg)
            androidById.keys.filter { it.startsWith("${AndroidApps.PREFIX}$pkg/") }.forEach { luna.forgetIcons(it) }
            launcher.setApps(launchPoints())
            showDock()
        }
    }
    private lateinit var statusBar: StatusBar
    private lateinit var cards: CardLayer
    private lateinit var justType: JustType
    private lateinit var justTypePanel: JustTypePanel
    private lateinit var quickLaunch: QuickLaunch
    private lateinit var launcher: Launcher
    private lateinit var tabDialog: TabDialog
    private lateinit var groupOverlay: GroupOverlay
    private var launcherOpen = false
    private lateinit var notifications: Notifications
    private lateinit var systemMenu: SystemMenu
    /** Exhibition mode: webOS's dock-mode clock, over everything, while it is on. */
    private lateinit var exhibition: ExhibitionLayer
    private lateinit var dockMode: org.webosarchive.lunacy.card.DockMode
    /** The drop-down the title opens while exhibiting, for changing face. */
    private lateinit var exhibitionMenu: ExhibitionMenu
    /** The app showing in exhibition mode, if it isn't the shell's own Time face. */
    private var exhibitionApp: String? = null
    /** What this turn in Exhibition opened, and so what leaving it closes again. */
    private val exhibitionOpened = LinkedHashSet<String>()
    private val exhibitionWindows = LinkedHashSet<AppWindow>()
    private var exhibitionOn = false
    /** True while what is exhibiting was started by Android's screen saver (ExhibitionDream). */
    private var dreamExhibition = false
    /** Whether the shell was out of sight when the screen saver put it into exhibition. */
    private var dreamFromBackground = false
    /** Whether the shell is the activity in front, which decides the two above. */
    private var inFront = false
    /** Catches taps outside the dashboard drop-down, which close it. */
    private lateinit var menuScrim: View
    private val bannerIds = java.util.concurrent.atomic.AtomicInteger(1)
    /** Headless root windows of noWindow apps: attached so their scripts run, never shown. */
    private lateinit var hidden: FrameLayout
    /** A debuggable build (the debug build type), which keeps the adb development extras. */
    private val devBuild by lazy { applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    /** Running apps: app id to all its windows, root first. */
    private val running = LinkedHashMap<String, MutableList<AppWindow>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WebView.setWebContentsDebuggingEnabled(true)
        luna = Luna(this)
        val installed = java.io.File(filesDir, "cryptofs/apps")
        // The webOS root filesystem services and package scripts see as /. Its ROM is laid down
        // (again after an update) off the main thread, below; a service's first call and a
        // package script wait for it (WebosRoot.prepare is synchronized).
        webos = org.webosarchive.lunacy.card.WebosRoot(this, bus, installed).apply { lunaSend.start() }
        // What webOS's syslogd kept, and novacomd's device side: the SDK's tools over adb.
        org.webosarchive.lunacy.card.SysLog.start(webos.root)
        org.webosarchive.lunacy.card.Novacom(webos) { org.webosarchive.lunacy.card.DeviceProfile.nduid(this) }.start()
        files = AppFiles(assets, installed, webos.root, java.io.File(applicationInfo.sourceDir))
        jsServices = org.webosarchive.lunacy.card.JsServices(bus, files.root, webos)
        server = AppServer(assets, files, jsServices.root, java.io.File(filesDir, "framework-art"))
        mediaServer = org.webosarchive.lunacy.card.MediaServer(jsServices.root)
        registry = AppRegistry(files)
        addedLaunchPoints = org.webosarchive.lunacy.card.LaunchPoints(webos.root, files)
        org.webosarchive.lunacy.card.Http.init(assets)
        packages = Packages(files.root, java.io.File(cacheDir, "packages"), webos)
        registerServices()
        jsServices.reload()
        Thread({
            val t = System.currentTimeMillis()
            webos.prepare()
            files.warm()
            Log.i(org.webosarchive.lunacy.card.AppServer.TAG, "webOS root ready in ${System.currentTimeMillis() - t} ms")
            // What the ROM brought: its services and their db8 kinds, and any system apps.
            runOnUiThread { jsServices.reload(); appsChanged(); FontWarmer(this, server).warmWhenIdle() }
        }, "webos-root").start()

        // The dock draws an icon being dragged out of it above its own bounds.
        val root = FrameLayout(this).apply { clipChildren = false }
        wallpaperView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        showWallpaper()
        root.addView(wallpaperView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        hidden = FrameLayout(this)
        root.addView(hidden, FrameLayout.LayoutParams(1, 1))

        // The whole screen, so that a full-screen card can be laid out under the status bar;
        // every other card starts below it (CardLayer.inset).
        cards = CardLayer(this, luna, this).apply { inset = luna.px(StatusBar.HEIGHT); feedback = { sounds.feedback(it) }
            // A phone's card view is the Pre3's: its own card ratios (Docs/pre3.md).
            phone = luna.phone
            // The TouchPad's landscape turned end for end, which Lunacy calls "left".
            upsideDown = { screenOrientation() == "left" } }
        sounds.preload()
        root.addView(cards, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Overlays, bottom to top as in LunaCE's OverlayWindowManager: launcher, pill, dock.
        // The icon glows first; the launch follows a frame later, so the glow is seen.
        launcher = Launcher(this, luna) { app -> launcher.postDelayed({ launch(app.id); closeLauncher() }, LAUNCH_DELAY_MS) }
        launcher.onRemove = { app -> remove(app) }
        launcher.appTitle = { id -> registry.get(id)?.title }
        launcher.setApps(launchPoints())
        registerReceiver(packageReceiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
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

        justType = JustType(this, luna)
        // The pill takes text now, so something else has to hold focus first, or the shell
        // opens with a cursor blinking in it.
        root.isFocusableInTouchMode = true
        // The pill is 3/4 of the short side: a TouchPad's 768 px on a tablet, whatever the
        // screen (the shell keeps the TouchPad's proportions there); on a phone, the screen's
        // own, as the Pre3's shell laid it out on its 480 px (Docs/pre3.md).
        val shortSide = if (luna.phone) {
            android.util.DisplayMetrics().also { @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(it) }.let { minOf(it.widthPixels, it.heightPixels) }
        } else (luna.density * 768).toInt()
        root.addView(justType, FrameLayout.LayoutParams((shortSide * JustType.WIDTH_OF_SHORT_SIDE).toInt(), luna.px(JustType.HEIGHT)).apply {
            topMargin = luna.px(StatusBar.HEIGHT + JustType.TOP_GAP); gravity = android.view.Gravity.CENTER_HORIZONTAL
        })

        // Just Type, under the status bar, over the cards, launcher, pill and dock.
        justTypePanel = JustTypePanel(this, luna)
        root.addView(justTypePanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        justType.setOnClickListener { openJustType() }
        justTypePanel.onLaunch = { app -> closeJustType(); launch(app.id) }
        justTypePanel.onSearch = { url ->
            closeJustType()
            if (registry.get(BROWSER) != null) launch(BROWSER, JSONObject().put("target", url))
            else org.webosarchive.lunacy.card.WebosLinks.intentFor("", null, url)?.let { org.webosarchive.lunacy.card.WebosLinks.open(this, it) }
        }

        quickLaunch = QuickLaunch(this, luna, onLaunch = { app -> quickLaunch.postDelayed({ launch(app.id) }, LAUNCH_DELAY_MS) }, onLauncher = { toggleLauncher() })
        // By position in the dock as drawn, which leaves out apps since removed.
        quickLaunch.onRemoveItem = { i -> setDock(shownDock().toMutableList().apply { removeAt(i) }) }
        quickLaunch.onMoveItem = { from, to -> setDock(shownDock().toMutableList().apply { add(to, removeAt(from)) }) }
        launcher.onDropOnDock = { app, x -> dropOnDock(app, x) }
        launcher.onEditModeChanged = { on -> quickLaunch.editing = on }
        showDock()
        root.addView(quickLaunch, FrameLayout.LayoutParams(MATCH_PARENT, luna.px(QuickLaunch.barHeight(luna.phone))).apply { gravity = android.view.Gravity.BOTTOM })

        // Notifications layer (reference §1.1): popup alerts, then the dashboard drop-down, under the status bar.
        statusBar = StatusBar(this, luna)
        statusBar.onBatteryFull = { sounds.batteryFull() }
        val popups = PopupLayer(this, luna)
        root.addView(popups, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        menuScrim = View(this).apply {
            visibility = View.GONE
            setOnClickListener { closeMenu(); closeExhibitionMenu() }
        }
        root.addView(menuScrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val menu = DashboardMenu(this, luna) { w -> notifications.removeDashboard(w); closeWindow(w) }
        root.addView(menu, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = luna.px(StatusBar.HEIGHT); gravity = android.view.Gravity.TOP or android.view.Gravity.START
        })
        notifications = Notifications(luna, statusBar, menu, popups, onBannerTap = { b ->
            closeMenu()
            launch(b.appId, try { JSONObject(b.params) } catch (e: Exception) { null })
        }, onNoDashboards = { closeMenu() }, onBannerShown = { b ->
            sounds.play(b.appId, b.soundClass, b.soundFile, b.soundDuration)
        })
        statusBar.onNotificationTap = { if (!notifications.tapBanner()) toggleMenu() }
        // The system menu, right-aligned under the bar, its edge 11 px past the screen's.
        systemMenu = SystemMenu(this, luna)
        root.addView(systemMenu, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = luna.px(StatusBar.HEIGHT); gravity = android.view.Gravity.TOP or android.view.Gravity.END
        })
        systemMenu.translationX = luna.px(SystemMenu.EDGE_OFFSET).toFloat()
        // SystemMenu::slotMenuBrightnessChanged: 0…1 along the slider is 1…100 %.
        systemMenu.onBrightness = { v, _ -> displayService.setBrightnessPercent(Math.round(v * 99) + 1) }
        statusBar.onSystemTap = { toggleSystemMenu() }
        statusBar.onSystemInfoChanged = { systemMenu.batteryPercent = statusBar.batteryPercent }
        root.addView(statusBar, FrameLayout.LayoutParams(MATCH_PARENT, luna.px(StatusBar.HEIGHT)))

        exhibition = ExhibitionLayer(this, luna).apply {
            visibility = View.GONE
            onExit = { setExhibition(false) }
        }
        root.addView(exhibition, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        exhibitionMenu = ExhibitionMenu(this, luna).apply {
            visibility = View.GONE
            onChoose = { appId -> chooseExhibition(appId) }
        }
        root.addView(exhibitionMenu, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = luna.px(StatusBar.HEIGHT); gravity = android.view.Gravity.TOP or android.view.Gravity.START
        })

        setContentView(root)
        // The splash was the window's background (Theme.Lunacy.Splash), which is what Android
        // painted while this was all being set up. The shell covers it now, so let the logo go
        // rather than hold the bitmap behind an opaque wallpaper for the rest of the session.
        root.post { window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)) }
        reportedOrientation = screenOrientation()
        (getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
            .registerDisplayListener(displayListener, android.os.Handler(android.os.Looper.getMainLooper()))
        seedMediaInternal()
        watchKeyboard(root)
        statusBar.onTitleTap = { if (exhibitionOn) toggleExhibitionMenu() else toggleAppMenu() }
        onCardView()
        goImmersive()

        // First Use, as a webOS device put it in front of a new owner: once, on the first
        // start, it asks for Android's permissions (LunacyService). After it has run, the
        // shell asks for storage itself if it is still missing, as it did before First Use.
        if (!org.webosarchive.lunacy.card.LunacyService.firstUseDone(this)) {
            cards.post { launch(org.webosarchive.lunacy.card.LunacyService.FIRST_USE_APP) }
        } else {
            askForStorage()
        }

        handleIntent(intent)
    }

    override fun onDestroy() {
        (getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
            .unregisterDisplayListener(displayListener)
        if (watchingPower) { unregisterReceiver(powerReceiver); watchingPower = false }
        if (::launcher.isInitialized) unregisterReceiver(packageReceiver)
        if (watchingSystem) { unregisterReceiver(systemReceiver); watchingSystem = false }
        // Everything that would outlive the activity: the apps' pages (each a renderer in this
        // process), the services' Node processes, the luna-send socket and the players. Android
        // re-creates the activity for a font-size or language change, and without this the old
        // one's cards ran on, unreachable, beside the new one's.
        pendingLoads.clear()
        for (w in running.values.flatten()) { (w.parent as? android.view.ViewGroup)?.removeView(w); w.destroy() }
        running.clear()
        if (::jsServices.isInitialized) jsServices.stopAll()
        if (::webos.isInitialized) webos.lunaSend.stop()
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
                android.content.Intent.ACTION_SCREEN_ON, android.content.Intent.ACTION_SCREEN_OFF -> displayService.screenChanged()
            }
        }
    }

    /** Lunacy is single-task: later launch requests (e.g. from adb or, later, Android intents) arrive here. */
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
     * auto|phone|tablet (the FormFactor setting; the shell restarts), launcher (opens it).
     * The shell is exported to every app on the device, so the ones that install a package
     * or run a command - install, sh, rawsh - are a debug build's only ([devBuild]).
     */
    private fun handleIntent(intent: android.content.Intent) {
        if (devBuild) intent.getStringExtra("install")?.let { install(it) }
        intent.getStringExtra("layout")?.let { v ->
            if (org.webosarchive.lunacy.card.FormFactor.setSetting(this, v)) { intent.removeExtra("layout"); recreate() }
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
        intent.getStringExtra("launch")?.let { id ->
            val raw = intent.getStringExtra("params")
            val params = raw?.let {
                runCatching { JSONObject(it) }
                    .onFailure { e -> Log.w(AppServer.TAG, "launch $id: params is not JSON ($e): $raw") }
                    .getOrNull()
            }
            launch(id, params)
        }
        if (intent.getBooleanExtra(EXTRA_EXHIBITION, false)) startDreamExhibition()
        // Android 5's am has no send-trim-memory; this stands in for it (Docs/dev-workflow.md).
        if (intent.hasExtra("trimMemory")) onTrimMemory(intent.getIntExtra("trimMemory", 0))
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
    private val memoryCheck = object : Runnable {
        override fun run() {
            if (memoryState == "normal") return
            val mi = android.app.ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
            if (android.os.SystemClock.uptimeMillis() - lastTrim >= MEMORY_CALM_MS && !mi.lowMemory) setMemoryState("normal")
            else wallpaperView.postDelayed(this, MEMORY_CHECK_MS)
        }
    }

    private fun setMemoryState(state: String) {
        if (state == memoryState) return
        memoryState = state
        Log.i(AppServer.TAG, "memory $state")
        // Root pages only, as LunaSysMgr sent it: a child window is the same app.
        val arg = JSONObject().put("state", state).toString()
        running.values.mapNotNull { it.firstOrNull() }.forEach { it.callMojo("lowMemoryNotification", arg) }
        wallpaperView.removeCallbacks(memoryCheck)
        if (state != "normal") wallpaperView.postDelayed(memoryCheck, MEMORY_CHECK_MS)
    }

    override fun onResume() {
        super.onResume(); inFront = true
    }

    override fun onPause() { inFront = false; org.webosarchive.lunacy.card.CookieFlush.now(); super.onPause() }

    // ---- apps ----

    /**
     * [startupCard]: the launch puts the app's loading card up at once, as a launch from the
     * launcher did on webOS. Exhibition's launches didn't (codepoet): the app is started for
     * the dock, and only what it opens for the dock is shown.
     */
    fun launch(appId: String, params: JSONObject? = null, startupCard: Boolean = true) {
        // An added launch point launches its app with its own params.
        addedById[appId]?.launchPoint?.let { lp -> launch(lp.appId, lp.paramsObject(), startupCard); return }
        androidById[appId]?.let { a -> if (!androidApps.launch(a)) systemBanner("", "Couldn't open ${a.title}"); return }
        val app = registry.get(appId) ?: run { Log.w(AppServer.TAG, "launch: no app $appId"); return }
        // A shortcut to one of Android's settings screens: no window, no pretending that
        // Lunacy owns the setting. Only apps Lunacy ships declare this.
        if (app.androidSettings.isNotEmpty()) {
            bus.call(appId, "palm://${org.webosarchive.lunacy.card.LunacyService.SERVICE}/android/openSettings",
                JSONObject().put("panel", app.androidSettings).toString()) { reply ->
                runOnUiThread {
                    val r = runCatching { JSONObject(reply) }.getOrNull()
                    if (r?.optBoolean("returnValue") != true) {
                        systemBanner(appId, r?.optString("errorText").orEmpty().ifEmpty { "Couldn't open ${app.title}" })
                    }
                }
            }
            return
        }
        val windows = running[appId]
        if (!app.isWeb) {
            // A PDK app: its own process, in a card of its own kind (Docs/pdk.md).
            if (windows != null) { cards.cards.firstOrNull { it.window.appId == appId }?.let { cards.maximize(it) }; return }
            if (!pdkRuntime.available) {
                systemBanner(appId, "${app.title} is a native app; it needs Lunacy's 32-bit build on a 32-bit ARM device")
                return
            }
            val w = org.webosarchive.lunacy.card.PdkWindow(this, appId, this, app, pdkRuntime)
            running[appId] = mutableListOf(w)
            showAsCard(w)
            if (!w.start()) { systemBanner(appId, "Couldn't start ${app.title}"); onWindowClosed(w) }
            return
        }
        if (windows != null) {
            // Relaunch: webOS doesn't reload a running app. Unless the app handles the relaunch
            // itself, LunaSysMgr brings its first card forward.
            windows.first().relaunch(params?.toString() ?: "") { handled ->
                Log.i(AppServer.TAG, "relaunch $appId $params handled=$handled")
                if (!handled) cards.cards.firstOrNull { it.window.appId == appId }?.let { cards.maximize(it) }
            }
            return
        }
        // The emulated card is a tablet's answer to a phone app. A Pre3 ran the same app at
        // its own size, so on a phone - where apps are told they are on a Pre3 - there is
        // nothing to emulate.
        val emulate = profile == org.webosarchive.lunacy.card.DeviceProfile.TOUCHPAD && registry.get(appId)?.emulated == true
        val t0 = android.os.SystemClock.uptimeMillis()
        val rootWindow = AppWindow(this, appId, this, emulate)
        rootWindow.fixedOrientation = fixedOrientationOf(app)
        running[appId] = mutableListOf(rootWindow)
        val url = if (params != null) app.url + "?launchParams=" + android.net.Uri.encode(params.toString()) else app.url
        // The startup card first, the page behind it: everything the launch can put off until
        // the card is on the screen waits for it (loadWhenShown). A headless app (Clock) opens
        // its card itself once its page has run, so for it the card is a placeholder with the
        // app's splash, which its first card window fills (onWindowOpened); an app that never
        // opens one loses the placeholder once it is up and running (withdrawPlaceholder).
        if (!startupCard) {
            if (app.noWindow) hidden.addView(rootWindow, FrameLayout.LayoutParams(1, 1)) else showAsCard(rootWindow, splash = false)
            rootWindow.loadUrl(url)
            return
        }
        showAsCard(rootWindow, placeholder = app.noWindow)
        Log.i(AppServer.TAG, "launch $appId: window and card in ${android.os.SystemClock.uptimeMillis() - t0} ms")
        loadWhenShown(rootWindow, url, t0)
    }

    /** The card waiting for a headless app's first card window, if it is [window]'s. */
    private fun placeholderOf(window: AppWindow): Card? = cards.cards.firstOrNull { it.awaitingWindow && it.window == window }

    /** The app is up and running with no card of its own: the placeholder goes, and the root goes on headless. */
    private fun withdrawPlaceholder(window: AppWindow) {
        cards.postDelayed({
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
        cards.postDelayed(load, CardLayer.Params.SLIDE_MS + 50)
    }

    /** Card view, while a launching card waits in it: the dock and the pill stay away, as on a device. */
    override fun onPreparing(card: Card) {
        if (launcherOpen) closeLauncher()
        onCardView()
        fade(justType, false)
        showDock(false)
    }

    private fun showAsCard(w: AppWindow, parent: AppWindow? = null, placeholder: Boolean = false, splash: Boolean = true) {
        // An app that never said it was laid out for a tablet gets the phone-sized card a
        // TouchPad gave it, rather than being stretched across the whole one.
        val emu = if (!w.emulated) null else Pair(
            luna.px(org.webosarchive.lunacy.card.EmulatedCard.WIDTH.toFloat()).toInt(),
            luna.px((org.webosarchive.lunacy.card.EmulatedCard.HEIGHT - profile.positiveSpaceTopPadding).toFloat()).toInt())
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
        card.fullScreen = w.fullScreen && !w.emulated
        card.awaitingWindow = placeholder
        // A card an app opens while its own card is up joins that card's group
        // (CardWindowManager::prepareAddWindowSibling: the active card is focused and the
        // launch came from the same app).
        val up = cards.maximized?.takeIf { parent != null && it.window.appId == w.appId }
        cards.add(card, up)
        cards.openLaunching(card)
    }

    /** appinfo.json's requestedWindowOrientation, which every card of the app starts with. */
    private fun fixedOrientationOf(app: AppInfo?): String? =
        app?.appinfo?.optString("requestedWindowOrientation")?.lowercase()
            ?.takeIf { it in setOf("up", "down", "left", "right", "landscape", "portrait") }

    override fun onWindowOpened(parent: AppWindow, child: AppWindow) {
        running.getOrPut(child.appId) { mutableListOf() } += child
        child.fixedOrientation = fixedOrientationOf(registry.get(child.appId))
        when (child.type) {
            "dashboard" -> notifications.addDashboard(child, icon(child.attributes.optString("icon"), child.appId))
            "popupalert" -> notifications.popups.show(child, child.attributes.optInt("height", 200))
            // The headless app's first card: it fills the card its launch put up, and the root
            // goes on where a headless root lives.
            "card" -> placeholderOf(parent)?.let { card ->
                val root = card.attach(child)
                hidden.addView(root, FrameLayout.LayoutParams(1, 1))
                card.fullScreen = child.fullScreen && !child.emulated
                if (cards.maximized == card) onMaximized(card)
            } ?: showAsCard(child, parent)
            // An app's own Exhibition view. webOS's dock mode showed it in place of the card
            // view, which is where a maximized card already is, so the shell only has to
            // remember it: it is the window whose closing ends the mode, and the one to close
            // when the mode ends.
            // No loading card for the dock's window, as on a device (codepoet).
            "dockMode" -> { exhibitionWindows += child; showAsCard(child, splash = false) }
            else -> showAsCard(child, parent)
        }
    }

    override fun onWindowClosed(window: AppWindow) {
        // Out of the exhibition set first: ending the mode below closes what is left in it,
        // and this window mustn't be closed twice over.
        exhibitionWindows.remove(window)
        // The app that was exhibiting has gone: so has exhibition mode, or the shell would sit
        // there thinking it is still on and ignore the next press of Start Exhibition.
        if (exhibitionOn && window.appId == exhibitionApp &&
            (window.type == "dockMode" || running[window.appId]?.size == 1)) {
            setExhibition(false)
        }
        cards.cards.firstOrNull { it.window == window }?.let { cards.remove(it) }
        // A full-screen card that closes itself while maximized takes the bar's absence with it,
        // and one that had fixed the screen's orientation lets it go.
        if (cards.maximized == null) { statusBar.slide(false); applyOrientation(null) }
        notifications.removeDashboard(window)
        notifications.popups.remove(window)
        closeWindow(window)
    }

    /**
     * An icon from an app's origin (a dashboard's `icon`, a banner's), else the app's mini
     * icon, as LunaSysMgr fell back for both (DashboardWindow::icon, BannerMessageHandler::generateIcon).
     */
    private fun icon(url: String?, appId: String): android.graphics.Bitmap? {
        if (!url.isNullOrEmpty()) server.serve(android.net.Uri.parse(url))?.takeIf { it.statusCode == 200 }?.let { luna.decode(it.data)?.let { b -> return b } }
        return registry.get(appId)?.let { luna.miniIcon(it) }
    }

    override fun addBanner(window: AppWindow, message: String, params: String, icon: String, soundClass: String, soundFile: String, duration: Int): Int {
        val id = bannerIds.getAndIncrement()
        runOnUiThread { notifications.addBanner(Banner(id, window, window.appId, message, icon(icon, window.appId), params, soundClass, soundFile, duration)) }
        return id
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
        target.evaluateJavascript("window.__lunacyPaste&&__lunacyPaste(${JSONObject.quote(text)})", null)
    }

    /** The last "Selection Copied" banner, which the next one replaces (WebAppManager::copiedToClipboard). */
    private var copiedBanner: Pair<AppWindow, Int>? = null

    override fun copiedToClipboard(window: AppWindow) {
        copiedBanner?.let { (w, id) -> notifications.removeBanner(w, id) }
        val id = bannerIds.getAndIncrement()
        notifications.addBanner(Banner(id, window, window.appId, "Selection Copied", icon(null, window.appId), "{ }"))
        copiedBanner = window to id
    }

    override fun playSound(window: AppWindow, soundClass: String, soundFile: String, duration: Int) =
        sounds.play(window.appId, soundClass, soundFile, duration)

    /** A banner from the system itself, e.g. the package manager. Tapping it launches appId. */
    private fun systemBanner(appId: String, message: String, params: String = "") =
        notifications.addBanner(Banner(bannerIds.getAndIncrement(), null, appId, message, icon(null, appId), params))

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
    /** The installed apps changed (an install, a removal, a script's rescan): the launcher hears it. */
    private fun appsChanged() {
        val start = System.currentTimeMillis()
        val before = registry.launchPoints.associateBy { it.id }
        registry.reload()
        configurator.run()
        Log.i(org.webosarchive.lunacy.card.AppServer.TAG, "apps rescanned in ${System.currentTimeMillis() - start} ms")
        launcher.setApps(launchPoints())
        dockMode.launchPointsChanged()
        val after = registry.launchPoints.associateBy { it.id }
        for ((id, app) in after) if (id !in before) launchPointChanged(app, "added")
        for ((id, app) in before) if (id !in after) launchPointChanged(app, "removed")
    }

    /**
     * applicationManager/listAllHandlersForMime. Lunacy has no registry of content handlers
     * yet; it answers for packages, which it installs itself, as the Preware it stands in for
     * (INSTALLERS) - codepoet's call: Lunacy won't run Preware, so it may answer as Preware.
     * The reply is the reference TouchPad's, with Preware installed; any other mime gets its
     * "No handlers found" answer, and no mime its complaint.
     */
    private fun handlersForMime(p: JSONObject): String {
        val mime = p.optString("mime")
        if (mime.isEmpty() && p.optString("url").isEmpty())
            return JSONObject().put("subscribed", false).put("returnValue", false)
                .put("errorCode", "Must have either an url or a mime parameter").toString()
        val isIpk = mime == IPK_MIME || (mime.isEmpty() && p.optString("url").substringBefore('?').endsWith(".ipk", true))
        val j = JSONObject().put("subscribed", false).put("mime", mime.ifEmpty { IPK_MIME })
        if (!isIpk) return j.put("returnValue", false).put("errorCode", "No handlers found for $mime").toString()
        return j.put("returnValue", true).put("resourceHandlers", JSONObject().put("activeHandler", JSONObject()
            .put("mime", IPK_MIME).put("extension", "ipk").put("appId", "org.webosinternals.preware")
            .put("streamable", true).put("index", 0).put("appName", "Preware"))).toString()
    }

    private fun isWebUrl(url: String) = url.startsWith("http://", true) || url.startsWith("https://", true) || url.startsWith("data:", true)

    /**
     * applicationManager/getResourceInfo: which app a URL's content goes to, and whether that
     * app streams it. Measured on the reference TouchPad (2026-10-04): `{"returnValue":true,
     * "uri":…, "appIdByExtension":…, "canStream":…}`; an mp3 goes to the streaming music player
     * (canStream true), and a type nothing handles - a zip, application/octet-stream - to the
     * Web app, which downloads it. Lunacy's handlers are its video player for video; anything
     * else is the Web app's.
     */
    private fun resourceInfo(p: JSONObject): String {
        val uri = p.optString("uri")
        if (uri.isEmpty()) return Bus.error("getResourceInfo: no uri")
        val ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri).lowercase()
        val mime = p.optString("mime").ifEmpty { android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext).orEmpty() }
        val video = registry.get(VIDEO_PLAYER)?.takeIf { mime.startsWith("video/") }
        return Bus.ok(mapOf("uri" to uri, "appIdByExtension" to (video?.id ?: BROWSER), "canStream" to (video != null)))
    }

    /**
     * applicationManager/listPackages, which App Catalog reads before it follows its installs.
     * Measured on the reference TouchPad: private bus only; `{"returnValue":true, "packages":
     * [...]}`, every package on the device, the system's with `userInstalled` false, each
     * with its apps (as listApps gives them) and its services (their services.json, with the
     * service's `id`). Lunacy knows no package sizes, so they are 0.
     */
    private fun listPackages(): String {
        val list = org.json.JSONArray()
        for (app in registry.apps) {
            if (app.androidComponent != null) continue
            val pkgDir = java.io.File(files.root, "usr/palm/packages/${app.id}")
            val info = runCatching { JSONObject(java.io.File(pkgDir, "packageinfo.json").readText()) }.getOrNull()
            val services = org.json.JSONArray()
            val ids = info?.optJSONArray("services")
            for (i in 0 until (ids?.length() ?: 0)) {
                val id = ids!!.optString(i)
                val json = java.io.File(files.root, "${org.webosarchive.lunacy.card.JsServices.SERVICES}/$id/services.json")
                runCatching { JSONObject(json.readText()) }.getOrNull()?.let { services.put(JSONObject().put("id", id).also { o -> it.keys().forEach { k -> o.put(k, it.get(k)) } }) }
            }
            val entry = app.listEntry()
            val icon = if (java.io.File(pkgDir, "icon.png").isFile) "/media/cryptofs/apps/usr/palm/packages/${app.id}/icon.png" else entry.optString("icon")
            list.put(JSONObject().put("id", info?.optString("id")?.ifEmpty { null } ?: app.id).put("loc_name", app.title)
                .put("package_format_version", 2).put("vendor", entry.optString("vendor")).put("version", app.version)
                .put("size", 0).put("icon", icon).put("userInstalled", app.userInstalled)
                .put("apps", org.json.JSONArray().put(entry)).put("services", services))
        }
        return JSONObject().put("returnValue", true).put("packages", list).toString()
    }

    /** Open applicationManager/launchPointChanges subscriptions. */
    private val launchPointWatchers = ArrayList<Bus.Call>()

    /**
     * applicationManager/launchPointChanges, which App Catalog watches for its install buttons.
     * Measured on the reference TouchPad: private bus only (a public caller gets "Unknown
     * method"), subscriptions only, `{"returnValue":true,"subscribed":true}` first, then each
     * launch point that comes or goes with `"change":"added"` or `"removed"`.
     */
    private fun launchPointChanges(call: Bus.Call) {
        if (!call.privateBus) return call.reply(Bus.error("Unknown method \"launchPointChanges\" for category \"/\""))
        if (!call.subscribe) return call.reply(JSONObject().put("returnValue", false).put("subscribed", false)
            .put("errorText", "Only supports subscriptions").toString())
        call.reply(JSONObject().put("returnValue", true).put("subscribed", true).toString())
        launchPointWatchers += call
        call.onCancel { launchPointWatchers.remove(call) }
    }

    private fun launchPointChanged(app: AppInfo, change: String) {
        if (launchPointWatchers.isEmpty()) return
        val e = app.listEntry()
        val j = JSONObject().put("id", app.id).put("version", app.version).put("appId", app.id)
            .put("vendor", e.optString("vendor")).put("vendorUrl", e.optString("vendorUrl")).put("size", 0)
            .put("packageId", app.id).put("removable", app.userInstalled).put("launchPointId", app.id + "_default")
            .put("title", app.title).put("appmenu", e.optString("appmenu")).put("icon", e.optString("icon"))
            .put("change", change).toString()
        launchPointWatchers.toList().forEach { it.reply(j) }
    }

    private fun launchPointChanged(lp: org.webosarchive.lunacy.card.LaunchPoint, change: String) {
        if (launchPointWatchers.isEmpty()) return
        val j = lp.toJson(registry.get(lp.appId)).put("change", change).toString()
        launchPointWatchers.toList().forEach { it.reply(j) }
    }

    /** The launcher, the dock and Just Type after an added launch point came, went or changed. */
    private fun addedLaunchPointsChanged() {
        launcher.setApps(launchPoints())
        showDock()
    }

    /**
     * applicationManager/addLaunchPoint, LunaSysMgr's servicecallback_addLaunchPoint: any app
     * may add a launch point for any installed app. `id`, `title`, `icon` and `params` are
     * required; a relative icon is the app's own file, and `file://` is taken off. The reply
     * is `{"returnValue":true,"launchPointId":"…"}`.
     */
    private fun addLaunchPoint(p: JSONObject): String {
        fun fail(text: String) = JSONObject().put("returnValue", false).put("errorText", text).toString()
        if (!p.has("id") || !p.has("title") || !p.has("icon") || !p.has("params")) return fail("Invalid arguments")
        val id = p.optString("id")
        val app = registry.get(id) ?: return fail("Unable to find id: $id")
        val title = p.optString("title")
        var icon = p.optString("icon")
        icon = when {
            icon.contains("://") -> icon.substringAfter("://")
            icon.startsWith("/") -> icon
            else -> "/media/cryptofs/apps/${Packages.APPS}/${app.dir}/$icon"
        }
        // json_object_get_string: an object or array as its JSON, a string as itself.
        val params = when (val v = p.opt("params")) {
            is JSONObject, is org.json.JSONArray -> v.toString()
            null, JSONObject.NULL -> null
            else -> JSONObject.quote(v.toString())
        }
        // LunaSysMgr reads the menu name as "appmenu", though its schema spells it appMenu.
        val appmenu = p.optString("appmenu", title)
        val lp = addedLaunchPoints.add(app, title, appmenu, icon, params, p.optBoolean("removable", true))
            ?: return JSONObject().put("returnValue", true).put("errorText", "Failed to save launch point").toString()
        addedLaunchPointsChanged()
        launchPointChanged(lp, "added")
        return JSONObject().put("returnValue", true).put("launchPointId", lp.launchPointId).toString()
    }

    /** applicationManager/removeLaunchPoint, and the launcher's Remove on a shortcut. Null once removed, else the error's text. */
    private fun removeLaunchPoint(launchPointId: String): String? {
        val lp = addedLaunchPoints.get(launchPointId)
        addedLaunchPoints.remove(launchPointId)?.let { return it }
        if (lp != null) {
            luna.forgetIcons(AppInfo.LAUNCH_POINT_PREFIX + launchPointId)
            addedLaunchPointsChanged()
            launchPointChanged(lp, "removed")
        }
        return null
    }

    /**
     * applicationManager/updateLaunchPointIcon: an app may change only its own launch points'
     * icons - caller identity from the bus, never from the params (rule 10).
     */
    private fun updateLaunchPointIcon(caller: String, p: JSONObject): String {
        val id = p.optString("launchPointId")
        if (id.isEmpty()) return Bus.error("Must provide launchPointId")
        if (!p.has("icon")) return Bus.error("Must provide icon path")
        val lp = addedLaunchPoints.get(id) ?: return Bus.error("launchPointId \"$id\" was not found")
        if (lp.appId != caller) return Bus.error("Attempted to change another application's launch point icon")
        val app = registry.get(lp.appId)
        var icon = p.optString("icon")
        if (!icon.contains("://") && !icon.startsWith("/") && app != null) icon = "/media/cryptofs/apps/${Packages.APPS}/${app.dir}/$icon"
        val updated = addedLaunchPoints.updateIcon(lp, app, icon) ?: return Bus.error("Unable to update launch point's icon")
        luna.forgetIcons(AppInfo.LAUNCH_POINT_PREFIX + id)
        addedLaunchPointsChanged()
        launchPointChanged(updated, "updated")
        return Bus.ok()
    }

    override fun removeBanner(window: AppWindow, id: Int) = notifications.removeBanner(window, id)
    override fun clearBanners(window: AppWindow) = notifications.clearBanners(window)

    private fun toggleMenu() = if (notifications.menu.isOpen) closeMenu() else openMenu()

    /** Opening one status-bar menu closes the other (StatusBar::slotMenuGroupActivated). */
    private fun toggleSystemMenu() {
        if (systemMenu.isOpen) { closeMenu(); return }
        closeMenu()
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
        val menu = notifications.menu
        menu.measure(0, 0)
        (menu.layoutParams as FrameLayout.LayoutParams).leftMargin =
            // 15 px past the group's right edge, measured against the reference TouchPad.
            (statusBar.notificationRight + luna.px(15f) - menu.measuredWidth).toInt().coerceAtLeast(0)
        menu.requestLayout()
        menuScrim.visibility = View.VISIBLE
        statusBar.menuOpen = true
        menu.open()
    }

    private fun closeMenu() {
        menuScrim.visibility = View.GONE
        statusBar.menuOpen = false
        notifications.menu.close()
        statusBar.systemMenuOpen = false
        systemMenu.close()
    }

    private fun closeWindow(w: AppWindow) {
        pendingLoads.remove(w)
        val list = running[w.appId] ?: return
        list.remove(w)
        (w.parent as? android.view.ViewGroup)?.removeView(w)
        w.destroy()
        // An app ends when its last visible window closes; its headless root goes with it.
        val app = registry.get(w.appId)
        val visible = list.filter { it.parent != hidden }
        if (visible.isEmpty() && (app?.noWindow == true || list.isEmpty())) {
            list.forEach { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }
            running.remove(w.appId)
        }
        if (cards.cards.isEmpty()) onCardView()
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
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    /** Runtime permission requests made on the bus (LunacyService), each answered to its caller when Android has. */
    private val permissionCallbacks = mutableMapOf<Int, (Boolean, Boolean) -> Unit>()
    private var nextPermissionCode = 100

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
        cards.postDelayed({
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
        val want = when (o) {
            "up" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "down" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            "right" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "left" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
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
        if (screenAwake.isEmpty()) {
            this.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    private fun watchKeyboard(root: View) = root.viewTreeObserver.addOnGlobalLayoutListener {
        val r = android.graphics.Rect()
        root.getWindowVisibleDisplayFrame(r)
        val h = (root.rootView.height - r.bottom).let { if (it > root.rootView.height / 6) it else 0 }
        if (h == keyboardHeight) return@addOnGlobalLayoutListener
        keyboardHeight = h
        val w = cards.maximized?.window
        val lp = cards.layoutParams as FrameLayout.LayoutParams
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
    }

    private fun hideKeyboard() {
        if (keyboardHeight > 0) imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    override val pixelScale get() = luna.density
    // A phone's: a tablet's card is a TouchPad's size already, and its landscape card, shorter
    // than the 640 px a phone's shorter side is laid out at, would otherwise be shrunk.
    override fun fixedViewportWidth(appId: String) =
        if (org.webosarchive.lunacy.card.FormFactor.isPhone(this) && org.webosarchive.lunacy.card.FixedViewport.isOn(this, appId))
            org.webosarchive.lunacy.card.FormFactor.appLayoutWidth(this) else 0

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
        val dm = android.util.DisplayMetrics().apply {
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(this)
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
            w = org.webosarchive.lunacy.card.EmulatedCard.WIDTH
            h = org.webosarchive.lunacy.card.EmulatedCard.HEIGHT
            minCardHeight = org.webosarchive.lunacy.card.EmulatedCard.MINIMUM_CARD_HEIGHT
            rows = org.webosarchive.lunacy.card.EmulatedCard.TOUCHABLE_ROWS
        }
        return JSONObject(mapOf(
            "modelName" to d.modelName, "modelNameAscii" to d.modelNameAscii,
            "platformVersion" to d.platformVersion, "platformVersionMajor" to d.platformVersionMajor,
            "platformVersionMinor" to d.platformVersionMinor, "platformVersionDot" to d.platformVersionDot,
            "carrierName" to d.carrierName,
            "serialNumber" to org.webosarchive.lunacy.card.DeviceProfile.serial(this, d),
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
                .put("width", org.webosarchive.lunacy.card.EmulatedCard.WIDTH)
                .put("height", org.webosarchive.lunacy.card.EmulatedCard.HEIGHT)
                .toString()
        }
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(dm)
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
    }

    /**
     * The wallpaper the system service holds, else the one Lunacy ships. The reference
     * TouchPad's own preference names a file in /media/internal/.wallpapers; Lunacy's is the
     * same shape, so Screen & Lock's "Change Wallpaper" sets this one.
     */
    private fun showWallpaper() {
        val chosen = systemService.fileOf(systemService.get("wallpaper") as? JSONObject)
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(dm)
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
                if (org.webosarchive.lunacy.card.HostWallpaper.isOn(this)) {
                    val open = wallpaperSource()
                    Thread { org.webosarchive.lunacy.card.HostWallpaper.follow(applicationContext, open) }.start()
                }
            }
            // webOS's Auto Dim: the display owner acts on it, and here that is Android's.
            "enableALS" -> displayService.setAutomaticBrightness(value == true)
        }
    }

    /**
     * What a device shipped with: the TouchPad's wallpapers in the user's own storage, where
     * a picker can find them. Copied once per Lunacy build, off the main thread.
     */
    private fun seedMediaInternal() = Thread {
        try {
            val dir = java.io.File(jsServices.root, "media/internal/wallpapers")
            val stamp = java.io.File(dir, ".lunacy-apk")
            val apk = packageManager.getPackageInfo(packageName, 0).lastUpdateTime.toString()
            if (stamp.isFile && stamp.readText() == apk) return@Thread
            dir.mkdirs()
            for (name in assets.list("luna/wallpapers").orEmpty()) {
                if (!name.endsWith(".jpg", true) && !name.endsWith(".png", true)) continue
                val out = java.io.File(dir, name)
                if (out.isFile) continue
                assets.open("luna/wallpapers/$name").use { i -> out.outputStream().use { i.copyTo(it) } }
            }
            stamp.writeText(apk)
            Log.i(AppServer.TAG, "wallpapers in ${dir.path}")
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "couldn't put the wallpapers in /media/internal", e)
        }
    }.start()

    /**
     * Exhibition as Android's screen saver. The dream itself has stood down by now (it would
     * end at the first touch, and webOS's Exhibition survived being touched), so the shell
     * takes on the rest of a dream's job for as long as it is standing in for one.
     */
    private fun startDreamExhibition() {
        // Where to go back to when it ends: out of sight again if the screen saver is what
        // brought the shell forward, and nowhere if its owner was already using it.
        dreamFromBackground = !inFront
        dreamExhibition = true
        // A dream shows over the lock screen, and so did a TouchPad in its dock. The keyguard
        // is not dismissed with it: leaving exhibition puts the shell back where it came from,
        // so the lock comes back too.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        // Android ends a dream when the device comes off its charger. Nothing else would end
        // this one - the screen is held on - so the shell watches for that itself, which is
        // also what a TouchPad did when it was lifted off its Touchstone.
        if (!watchingPower) {
            // The battery's broadcast is sticky, and registering hands back the one last sent,
            // so the charger is known now rather than at the next change.
            dreamPlugged = plugged(registerReceiver(powerReceiver,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)))
            watchingPower = true
        }
        Log.i(AppServer.TAG, "exhibition: Android's screen saver; on a charger = $dreamPlugged")
        setExhibition(true)
    }

    private fun endDreamExhibition() {
        dreamExhibition = false
        dreamPlugged = false
        if (watchingPower) { unregisterReceiver(powerReceiver); watchingPower = false }
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (dreamFromBackground) moveTaskToBack(true)
    }

    private var watchingPower = false
    /** Whether the device has been on a charger since the screen saver started exhibiting. */
    private var dreamPlugged = false

    /**
     * The charger, watched through the battery's own broadcast rather than
     * ACTION_POWER_DISCONNECTED: this one is sticky, so registering says at once whether the
     * device is on a charger now, and it is the one a device really sends. (The reference
     * tablet never broadcasts ACTION_POWER_DISCONNECTED for a simulated charger, so watching
     * for that instead would be untestable over adb; see Docs/android5-setup.md.)
     */
    private val powerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (plugged(intent)) { dreamPlugged = true; return }
            if (!dreamPlugged) return
            Log.i(AppServer.TAG, "exhibition: off the charger, leaving")
            setExhibition(false)
        }
    }

    private fun plugged(battery: android.content.Intent?): Boolean =
        (battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    /**
     * Exhibition mode, which webOS entered when a TouchPad was put on its Touchstone and
     * Palm's Exhibition app starts with display/control/setState {"state":"dock"}. The clock
     * is the shell's own, as LunaSysMgr's was; the status bar says "Time", as a device does,
     * and the home button comes back out.
     */
    /**
     * The launch parameters webOS gave an app it was putting into Exhibition, exactly as
     * LunaSysMgr's DockModeWindowManager::launchApp built them: the window type by name and
     * `dockMode`. Apps test both - AccuWeather opens its exhibition view only when
     * `windowType == "dockModeWindow"` *and* `dockMode == true`, and shows its ordinary
     * interactive view otherwise - so a launch missing either one looks like the app ignoring
     * Exhibition.
     */
    private fun dockModeParams() = JSONObject()
        .put("windowType", "dockModeWindow").put("dockMode", true)

    /**
     * Puts an app on show in Exhibition. An app that wasn't already running was started for
     * the dock, so it is remembered as this turn's, to be closed again when the mode ends -
     * webOS closed what it had put in the dock (DockModeWindowManager::closeApp). One its
     * owner already had open keeps its own cards; only the window it opens for the dock goes.
     */
    private fun exhibit(appId: String) {
        if (running[appId] == null) exhibitionOpened += appId
        launch(appId, dockModeParams(), startupCard = false)
        statusBar.title = dockMode.title(appId)
    }

    private fun setExhibition(on: Boolean) {
        // Entering again while it is already on is not a no-op: Palm's Exhibition app sends
        // this every time its button is pressed, and if the exhibiting app's card has since
        // been thrown away, or the chosen app has changed, the press has to take effect.
        // Only leaving is idempotent.
        if (!on && !exhibitionOn) return
        exhibitionOn = on
        if (on) {
            closeMenu()
            if (launcherOpen) closeLauncher()
            // The app its owner chose, if any; otherwise the shell's own Time face. webOS
            // launched the chosen app with dockMode set, which is how it knows to show its
            // exhibition view rather than its ordinary one.
            exhibitionApp = dockMode.enabledApp()
            val app = exhibitionApp?.let { registry.get(it) }
            if (app != null) {
                exhibition.visibility = View.GONE
                exhibit(app.id)
            } else {
                exhibition.reset()
                exhibition.visibility = View.VISIBLE
                exhibition.bringToFront()
                statusBar.title = "Time"
            }
            statusBar.bringToFront()
            statusBar.setMode(StatusBar.Mode.APP)
            // The screen is meant to stay on while it is exhibiting: that is the point of a dock.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            closeExhibitionMenu()
            exhibition.visibility = View.GONE
            // webOS closed what it had put in the dock when dock mode ended
            // (DockModeWindowManager::closeApp), so an app's Exhibition view doesn't turn up in
            // the card view afterwards. An app that was already running keeps its own cards:
            // only the window it opened for the dock goes.
            exhibitionApp = null
            exhibitionOpened.toList().forEach { id -> running[id]?.toList()?.forEach { w -> onWindowClosed(w) } }
            exhibitionWindows.toList().forEach { onWindowClosed(it) }
            exhibitionOpened.clear()
            exhibitionWindows.clear()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (dreamExhibition) endDreamExhibition()
            if (cards.maximized != null) onMaximized(cards.maximized!!) else onCardView()
        }
        // An app's exhibition view is its own card, shown as it is; the Time face replaces it.
        cards.visibility = if (on && exhibitionApp == null) View.INVISIBLE else View.VISIBLE
        fade(justType, !on && cards.maximized == null && !launcherOpen)
        showDock(!on && cards.maximized == null)
    }

    /** What the shell knows about the screen: real pixels, and the TouchPad px apps lay out in. */
    private fun displayInfo(): JSONObject {
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(dm)
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
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(dm)
        fun css(px: Int) = if (px <= 0 || dm.density <= 0f) px else Math.round(Math.round(px / dm.density) * dm.density)
        val cw = css(w); val ch = css(h)
        return if (cw == w && ch == h) "1:1" else "$cw \u00d7 $ch"
    }

    private fun registerServices() {
        val launchHandler = Bus.Handler { caller, p, reply ->
            val id = p.optString("id")
            val params = p.optJSONObject("params")
            // A web page is the Web app's, as webOS's command-resource-handlers said (^https?: and
            // ^data: to com.palm.app.browser): a card of its own for each.
            if (id.isEmpty() && isWebUrl(p.optString("target")) && registry.get(BROWSER) != null) {
                launch(BROWSER, JSONObject().put("target", p.optString("target")))
                reply(Bus.ok(mapOf("processId" to "success")))
                return@Handler
            }
            // A link, an email, a phone number, a map: webOS's own apps owned these and Lunacy
            // doesn't have them, so Android's answer instead. See WebosLinks.
            val link = org.webosarchive.lunacy.card.WebosLinks.intentFor(id, params, p.optString("target"))
            if (link != null && registry.get(id) == null) {
                reply(org.webosarchive.lunacy.card.WebosLinks.open(this, link))
                return@Handler
            }
            // The App Museum installs through Preware (LuneOS's on LuneOS); Lunacy's package
            // manager answers for it. See Docs/architecture.md, "Package manager and App Catalog".
            if (id in INSTALLERS && registry.get(id) == null && params?.optString("type") == "install") {
                // Preware reads "file"; the handler chain the catalogs use also sends the
                // standard "target".
                val file = params.optString("file").ifEmpty { params.optString("target") }
                if (file.isEmpty()) reply(Bus.error("install: no file given"))
                else { install(file, caller); reply(Bus.ok(mapOf("processId" to "success"))) }
            } else if (registry.get(id) == null) reply(Bus.error("Application not found: $id"))
            // A PDK app runs where the runtime is (Docs/pdk.md); elsewhere launch() shows why not.
            else if (registry.get(id)?.isWeb == false) {
                launch(id)
                reply(if (pdkRuntime.available) Bus.ok(mapOf("processId" to "success"))
                    else Bus.error("${registry.get(id)?.title} is a native app; this build of Lunacy can't run it"))
            }
            else { launch(id, params); reply(Bus.ok(mapOf("processId" to "success"))) }
        }
        SystemProperties(this).register(bus)
        // webOS's preference and wallpaper store, and the two display settings Android lets
        // Lunacy really set. See Docs/architecture.md, "Settings".
        displayService = org.webosarchive.lunacy.card.DisplayService(this)
        displayService.onDockMode = { on -> runOnUiThread { setExhibition(on) } }
        displayService.inDockMode = { exhibitionOn }
        displayService.register(bus)
        dockMode = org.webosarchive.lunacy.card.DockMode(this, registry).also { it.register(bus) }
        systemService = org.webosarchive.lunacy.card.SystemService(jsServices.root, java.io.File(filesDir, "systemservice.json"))
        systemService.onPreferenceChanged = { key, value -> onPreferenceChanged(key, value) }
        systemService.hostPreference = { key -> hostPreference(key) }
        systemService.register(bus)
        registerReceiver(systemReceiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_CHANGED); addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_SCREEN_ON); addAction(android.content.Intent.ACTION_SCREEN_OFF)
        })
        watchingSystem = true
        audio = org.webosarchive.lunacy.card.AudioService(this, startMuted = systemService.get("systemSounds") == false) { sounds.feedback(it) }
        audio.register(bus)
        // Lunacy's own service, on its own name: the environment it really runs in, and
        // Android's settings screens for the settings Android owns.
        org.webosarchive.lunacy.card.LunacyService(this, registry, jsServices, jsServices.root, { displayInfo() }, ::askPermissions,
            { intent -> runCatching { startActivityForResult(intent, 200) }.isSuccess }, onLayoutChanged = { recreate() },
            wallpaper = ::wallpaperSource).register(bus)
        org.webosarchive.lunacy.card.ConnectionManager(this).register(bus)
        // Secrets, where the accounts service keeps each account's credentials.
        org.webosarchive.lunacy.card.KeyManager(java.io.File(filesDir, "keymanager.json")).register(bus)
        org.webosarchive.lunacy.card.DeviceProfileService(this, profile).register(bus)
        org.webosarchive.lunacy.card.ActivityManager().register(bus)
        // webOS's downloader, which apps hand every file fetch to: drPodder's episodes and
        // album art, MeTube's "download first". It writes into the webOS tree.
        org.webosarchive.lunacy.card.DownloadManager(jsServices.root).register(bus)
        val db8 = org.webosarchive.lunacy.card.Db8("com.palm.db", java.io.File(filesDir, "db8.sqlite")).also { it.register(bus) }
        val tempdb = org.webosarchive.lunacy.card.Db8("com.palm.tempdb", null).also { it.register(bus) }
        configurator = org.webosarchive.lunacy.card.Configurator(files, db8, tempdb, webos.root)
        configurator.run()
        bus.register("com.palm.applicationManager", "launch", launchHandler)
        bus.register("com.palm.applicationManager", "open", launchHandler)
        // What Enyo's CrossAppUI asks for before loading another app's UI. The reference
        // TouchPad answers {"returnValue":true,"appId":...,"basePath":"file://…/index.html"}.
        bus.register("com.palm.applicationManager", "getAppBasePath") { _, p, reply ->
            val app = registry.get(p.optString("appId"))
            reply(if (app == null) Bus.error("Application not found: ${p.optString("appId")}")
                  else Bus.ok(mapOf("appId" to app.id, "basePath" to app.filePath())))
        }
        org.webosarchive.lunacy.card.Keys(this).register(bus)
        // Package scripts ask for this after putting an app in /usr/palm/applications
        // themselves. The reference TouchPad answers {"returnValue":true}.
        bus.register("com.palm.applicationManager", "rescan") { _, _, reply ->
            appsChanged()
            reply(Bus.ok())
        }
        bus.register("com.palm.applicationManager", "launchPointChanges", Bus.CallHandler { launchPointChanges(it) })
        // Launch points an app adds: the Web app's Add to Launcher, the PWA Installer's shortcuts.
        bus.register("com.palm.applicationManager", "addLaunchPoint") { _, p, reply -> runOnUiThread { reply(addLaunchPoint(p)) } }
        bus.register("com.palm.applicationManager", "removeLaunchPoint") { _, p, reply ->
            runOnUiThread {
                val id = p.optString("launchPointId")
                reply(if (id.isEmpty()) Bus.error("Must provide a launchPointId")
                      else removeLaunchPoint(id)?.let { Bus.error(it) } ?: Bus.ok())
            }
        }
        bus.register("com.palm.applicationManager", "updateLaunchPointIcon") { caller, p, reply -> runOnUiThread { reply(updateLaunchPointIcon(caller, p)) } }
        bus.register("com.palm.applicationManager", "listAllHandlersForMime") { _, p, reply -> reply(handlersForMime(p)) }
        bus.register("com.palm.applicationManager", "getResourceInfo") { _, p, reply -> reply(resourceInfo(p)) }
        org.webosarchive.lunacy.card.UniversalSearch(this).register(bus)
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
        bus.register("com.palm.applicationManager", "listPackages", Bus.CallHandler { c ->
            c.reply(if (!c.privateBus) Bus.error("Unknown method \"listPackages\" for category \"/\"") else listPackages())
        })
        // App Catalog's installer, over the same install path as Preware's (AppInstallService.kt).
        org.webosarchive.lunacy.card.AppInstallService(object : org.webosarchive.lunacy.card.AppInstallService.Installer {
            override fun install(url: String, requester: String, progress: (Int) -> Unit, done: (Packages.Result) -> Unit) =
                this@ShellActivity.install(url, requester, progress, onDone = done)
            override fun remove(appId: String, done: (String?) -> Unit) {
                val app = registry.get(appId)
                if (app == null || !app.userInstalled) return done("$appId isn't installed")
                packages.remove(appId, app.dir) { error -> appsChanged(); jsServices.reload(); done(error) }
            }
            override fun isInstalled(appId: String) = registry.get(appId)?.userInstalled == true
        }).register(bus)
        // appinstaller's own installs and removals, which the SDK's palm-install makes.
        org.webosarchive.lunacy.card.AppInstallService.AppInstaller({ filesDir.usableSpace },
            object : org.webosarchive.lunacy.card.AppInstallService.PackageInstaller {
                override fun installFile(file: java.io.File, done: (Packages.Result) -> Unit) =
                    install(file.path, banners = false, onDone = done)
                override fun removableVersion(id: String) = registry.get(id)?.takeIf { it.userInstalled }?.version
                override fun remove(id: String, done: (String?) -> Unit) {
                    val app = registry.get(id) ?: return done("$id isn't installed")
                    running[id]?.toList()?.forEach { w -> onWindowClosed(w) }
                    packages.remove(id, app.dir) { error -> appsChanged(); jsServices.reload(); showDock(); done(error) }
                }
            }) { path -> java.io.File(webos.mapPaths(path)) }.register(bus)
        // Which windows are up, and closing one, as palm-launch -c and palm-run use them.
        // Measured on the reference TouchPad (private bus only): running answers
        // {"running":[{"id":…,"processid":"1030"},…],"returnValue":true}, one entry per window
        // (LunaSysMgr's own system UI windows among them, which Lunacy draws natively and so
        // has none of); close {"processId"} answers {"returnValue":true}, an unknown id too, and
        // with none {"returnValue":false,"errorText":"Must provide a valid processId to close"}.
        bus.register("com.palm.applicationManager", "running", Bus.CallHandler { c ->
            if (!c.privateBus) return@CallHandler c.reply(Bus.error("Unknown method \"running\" for category \"/\""))
            val list = org.json.JSONArray()
            for ((id, windows) in running) for (w in windows) list.put(JSONObject().put("id", id).put("processid", w.pid.toString()))
            c.reply(JSONObject().put("running", list).put("returnValue", true).toString())
        })
        bus.register("com.palm.applicationManager", "close", Bus.CallHandler { c ->
            if (!c.privateBus) return@CallHandler c.reply(Bus.error("Unknown method \"close\" for category \"/\""))
            val pid = c.params.optString("processId")
            if (pid.isEmpty()) return@CallHandler c.reply(JSONObject().put("returnValue", false)
                .put("errorText", "Must provide a valid processId to close").toString())
            running.values.flatten().firstOrNull { it.pid.toString() == pid }?.let { onWindowClosed(it) }
            c.reply(Bus.ok())
        })
        bus.register("com.palm.applicationManager", "listApps") { _, _, reply ->
            reply(Bus.ok(mapOf("apps" to org.json.JSONArray(registry.apps.map { it.listEntry() }))))
        }
    }

    // ---- shell state ----

    /**
     * Removes an installed app (the launcher's Remove): its windows close, then its package
     * goes, and the launcher, services and db8 kinds are reloaded.
     */
    private fun remove(app: AppInfo) {
        if (app.androidComponent != null) { androidApps.uninstall(app); return }
        app.launchPoint?.let { lp -> removeLaunchPoint(lp.launchPointId); return }
        running[app.id]?.toList()?.forEach { w -> onWindowClosed(w) }
        org.webosarchive.lunacy.card.FixedViewport.forget(this, app.id)
        packages.remove(app.id, app.dir) { error ->
            appsChanged()
            jsServices.reload()
            showDock()
            systemBanner("", if (error == null) "${app.title} removed" else "Couldn't remove ${app.title}: $error")
        }
    }

    // ---- the dock ----

    private val dockPrefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    /** The dock's apps, as the user arranged them; at first, the first five apps. */
    private fun dock(): List<String> {
        val saved = dockPrefs.getString("dock", null)
            ?: return registry.launchPoints.take(quickLaunch.maxItems).map { it.id }
        val a = runCatching { org.json.JSONArray(saved) }.getOrDefault(org.json.JSONArray())
        return (0 until a.length()).map { a.optString(it) }
    }

    private fun setDock(ids: List<String>) {
        dockPrefs.edit().putString("dock", org.json.JSONArray(ids.distinct().take(quickLaunch.maxItems)).toString()).apply()
        showDock()
    }

    /** The dock's ids that are still apps, in the order they are drawn. */
    private fun shownDock() = dock().filter { appById(it) != null }

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
        card.window.setStageActive(true)
        cards.cards.filter { it != card }.forEach { it.window.setStageActive(false) }
        // The active card's page holds the focus, as webOS's did. Without it document.hasFocus()
        // is false, and Chromium then sets activeElement without dispatching focus or focusin -
        // so an app that focuses a field itself is invisible to the bridge and gets no keyboard.
        card.window.requestFocus()
    }

    /**
     * The title while exhibiting drops down the faces to choose from: the shell's own Time,
     * and every app its owner turned on in Palm's Exhibition app. webOS drew the same menu
     * from the same place, so the mode can be changed without leaving it.
     */
    private fun toggleExhibitionMenu() {
        if (exhibitionMenu.visibility == View.VISIBLE) { closeExhibitionMenu(); return }
        val entries = mutableListOf(ExhibitionMenu.Entry(null, "Time", luna.image("dockmode/time-icon-48x48.png")))
        for (app in registry.apps) {
            if (!dockMode.isEnabled(app.id)) continue
            entries += ExhibitionMenu.Entry(app.id, dockMode.title(app.id), luna.appIcon(app))
        }
        exhibitionMenu.entries = entries
        exhibitionMenu.visibility = View.VISIBLE
        exhibitionMenu.bringToFront()
        statusBar.bringToFront()
        // The title reads "Choose an App" while the menu is down, as it does on a device.
        statusBar.title = "Choose an App"
        menuScrim.visibility = View.VISIBLE
    }

    private fun closeExhibitionMenu() {
        if (exhibitionMenu.visibility != View.VISIBLE) return
        exhibitionMenu.visibility = View.GONE
        if (!notifications.menu.isOpen) menuScrim.visibility = View.GONE
        statusBar.title = exhibitionApp?.let { dockMode.title(it) } ?: "Time"
    }

    /** A face chosen from that menu: null is the shell's own Time. */
    private fun chooseExhibition(appId: String?) {
        closeExhibitionMenu()
        if (appId == exhibitionApp) return
        // The app that was exhibiting stops being the one on show; webOS left it running.
        exhibitionApp = appId
        if (appId == null) {
            exhibition.reset()
            exhibition.visibility = View.VISIBLE
            exhibition.bringToFront()
            statusBar.bringToFront()
            statusBar.title = "Time"
            cards.visibility = View.INVISIBLE
        } else {
            exhibition.visibility = View.GONE
            cards.visibility = View.VISIBLE
            exhibit(appId)
        }
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
        if (exhibitionMenu.visibility == View.VISIBLE) { closeExhibitionMenu(); return }
        if (exhibitionOn) { setExhibition(false); return }
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
        justTypePanel.apps = registry.launchPoints + addedById.values + androidById.values
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
        /** The launcher's launch point title, com.palm.launcher's on the reference TouchPad. */
        const val LAUNCHER_TITLE = "Launcher"
        const val LAUNCHER_MS = 350L   // reference TouchPad lunaAnimations.conf: launcherDuration 350, curve 15
        const val DOCK_MS = 350L       // quickLaunchDuration
        const val FADE_MS = 200L       // quickLaunchFadeDuration
        /** Long enough for the launch glow to be drawn before the card work starts. */
        const val LAUNCH_DELAY_MS = 60L
        /** Android's screen saver asking for Exhibition; see ExhibitionDream. */
        const val EXTRA_EXHIBITION = "exhibition"
        const val MEMORY_CHECK_MS = 10_000L
        /** WindowedWebApp's kShowWindowTimeoutMs: how long a loaded page gets to call stageReady. */
        const val SHOW_WINDOW_TIMEOUT_MS = 3000L
        /** How long after a headless app is up its placeholder card waits for it to open one. */
        const val PLACEHOLDER_GRACE_MS = 1000L
        const val MEMORY_CALM_MS = 30_000L
        /** Package installers apps hand .ipks to: Preware on webOS, and LuneOS's Preware. */
        val INSTALLERS = setOf("org.webosinternals.preware", "org.webosports.app.preware")
        /** webOS's Web app, which Lunacy ships; web links open in it. */
        const val BROWSER = "com.palm.app.browser"
        const val VIDEO_PLAYER = "com.palm.app.videoplayer"
        const val IPK_MIME = "application/vnd.webos.ipk"
    }

    /**
     * Just type: a keyboard's printable key in the card view or the launcher opens Just Type
     * with that character, as on a TouchPad with a keyboard paired (codepoet, 2026-10-02).
     * Not while a card is up, a text field (a tab's name, a group's) has the focus, or a
     * modifier is held.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && !justTypePanel.showing && cards.maximized == null &&
            !exhibitionOn && !tabDialog.showing && currentFocus !is android.widget.EditText &&
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
