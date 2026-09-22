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
    private lateinit var packages: Packages
    private lateinit var jsServices: org.webosarchive.lunacy.card.JsServices
    private lateinit var mediaServer: org.webosarchive.lunacy.card.MediaServer
    private lateinit var configurator: org.webosarchive.lunacy.card.Configurator
    private lateinit var systemService: org.webosarchive.lunacy.card.SystemService
    private lateinit var displayService: org.webosarchive.lunacy.card.DisplayService
    /** The wallpaper view, reloaded when the preference changes. */
    private lateinit var wallpaperView: ImageView
    /** /media/cryptofs/apps: installed packages first, then the apps bundled in the APK. */
    private lateinit var files: AppFiles
    /** The webOS device Lunacy answers as, for every app-visible surface. */
    private val profile by lazy { org.webosarchive.lunacy.card.DeviceProfile.forScreen(this) }
    private lateinit var statusBar: StatusBar
    private lateinit var cards: CardLayer
    private lateinit var justType: JustType
    private lateinit var quickLaunch: QuickLaunch
    private lateinit var launcher: Launcher
    private var launcherOpen = false
    private lateinit var notifications: Notifications
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
    /** Running apps: app id to all its windows, root first. */
    private val running = LinkedHashMap<String, MutableList<AppWindow>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WebView.setWebContentsDebuggingEnabled(true)
        luna = Luna(this)
        files = AppFiles(assets, java.io.File(filesDir, "cryptofs/apps"))
        jsServices = org.webosarchive.lunacy.card.JsServices(this, bus, files.root)
        server = AppServer(assets, files, jsServices.root, java.io.File(filesDir, "framework-art"))
        mediaServer = org.webosarchive.lunacy.card.MediaServer(jsServices.root)
        registry = AppRegistry(files)
        org.webosarchive.lunacy.card.Http.init(assets)
        packages = Packages(files.root, java.io.File(cacheDir, "packages"))
        registerServices()
        jsServices.reload()

        // The dock draws an icon being dragged out of it above its own bounds.
        val root = FrameLayout(this).apply { clipChildren = false }
        wallpaperView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        showWallpaper()
        root.addView(wallpaperView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        hidden = FrameLayout(this)
        root.addView(hidden, FrameLayout.LayoutParams(1, 1))

        cards = CardLayer(this, luna, this)
        root.addView(cards, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })

        // Overlays, bottom to top as in LunaCE's OverlayWindowManager: launcher, pill, dock.
        // The icon glows first; the launch follows a frame later, so the glow is seen.
        launcher = Launcher(this, luna) { app -> launcher.postDelayed({ launch(app.id); closeLauncher() }, LAUNCH_DELAY_MS) }
        launcher.onRemove = { app -> remove(app) }
        launcher.setApps(registry.launchPoints)
        launcher.dockHeight = luna.px(QuickLaunch.HEIGHT).toFloat()
        launcher.visibility = View.INVISIBLE
        root.addView(launcher, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })

        justType = JustType(this, luna)
        // The pill takes text now, so something else has to hold focus first, or the shell
        // opens with a cursor blinking in it.
        root.isFocusableInTouchMode = true
        val shortSide = (luna.density * 768).toInt()
        root.addView(justType, FrameLayout.LayoutParams((shortSide * JustType.WIDTH_OF_SHORT_SIDE).toInt(), luna.px(JustType.HEIGHT)).apply {
            topMargin = luna.px(StatusBar.HEIGHT + JustType.TOP_GAP); gravity = android.view.Gravity.CENTER_HORIZONTAL
        })

        quickLaunch = QuickLaunch(this, luna, onLaunch = { app -> quickLaunch.postDelayed({ launch(app.id) }, LAUNCH_DELAY_MS) }, onLauncher = { toggleLauncher() })
        quickLaunch.onRemoveItem = { i -> setDock(dock().toMutableList().apply { removeAt(i) }) }
        quickLaunch.onMoveItem = { from, to -> setDock(dock().toMutableList().apply { add(to, removeAt(from)) }) }
        launcher.onDropOnDock = { app, x -> dropOnDock(app, x) }
        launcher.onEditModeChanged = { on -> quickLaunch.editing = on }
        showDock()
        root.addView(quickLaunch, FrameLayout.LayoutParams(MATCH_PARENT, luna.px(QuickLaunch.HEIGHT)).apply { gravity = android.view.Gravity.BOTTOM })

        // Notifications layer (reference §1.1): popup alerts, then the dashboard drop-down, under the status bar.
        statusBar = StatusBar(this, luna)
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
        }, onNoDashboards = { closeMenu() })
        statusBar.onNotificationTap = { if (!notifications.tapBanner()) toggleMenu() }
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

        handleIntent(intent)
    }

    override fun onDestroy() {
        (getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
            .unregisterDisplayListener(displayListener)
        if (watchingPower) { unregisterReceiver(powerReceiver); watchingPower = false }
        super.onDestroy()
    }

    /** Lunacy is single-task: later launch requests (e.g. from adb or, later, Android intents) arrive here. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Android's screen saver asking for Exhibition (ExhibitionDream), and extras for
     * development over adb: launch <appid> [params <json>], install <url or path>.
     */
    private fun handleIntent(intent: android.content.Intent) {
        intent.getStringExtra("install")?.let { install(it) }
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
    }

    override fun onResume() { super.onResume(); inFront = true }

    override fun onPause() { inFront = false; super.onPause() }

    // ---- apps ----

    fun launch(appId: String, params: JSONObject? = null) {
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
        if (!app.isWeb) {
            systemBanner(appId, "${app.title} is a native app; Lunacy can't run those yet")
            return
        }
        val windows = running[appId]
        if (windows != null) {
            // Relaunch: webOS doesn't reload a running app. Unless the app handles the relaunch
            // itself, LunaSysMgr brings its first card forward.
            windows.first().relaunch(params?.toString() ?: "") { handled ->
                Log.i(AppServer.TAG, "relaunch $appId $params handled=$handled")
                if (!handled) cards.cards.firstOrNull { it.window.appId == appId }?.let { cards.maximize(it) }
            }
            return
        }
        val rootWindow = AppWindow(this, appId, this)
        running[appId] = mutableListOf(rootWindow)
        if (app.noWindow) hidden.addView(rootWindow, FrameLayout.LayoutParams(1, 1))
        else showAsCard(rootWindow)
        val url = if (params != null) app.url + "?launchParams=" + android.net.Uri.encode(params.toString()) else app.url
        rootWindow.loadUrl(url)
    }

    private fun showAsCard(w: AppWindow) {
        val card = Card(this, w, luna.px(CardLayer.Params.CORNER))
        cards.add(card)
        cards.openMaximized(card)
    }

    override fun onWindowOpened(parent: AppWindow, child: AppWindow) {
        running.getOrPut(child.appId) { mutableListOf() } += child
        when (child.type) {
            "dashboard" -> notifications.addDashboard(child, icon(child.attributes.optString("icon"), child.appId))
            "popupalert" -> notifications.popups.show(child, child.attributes.optInt("height", 200))
            // An app's own Exhibition view. webOS's dock mode showed it in place of the card
            // view, which is where a maximized card already is, so the shell only has to
            // remember it: it is the window whose closing ends the mode, and the one to close
            // when the mode ends.
            "dockMode" -> { exhibitionWindows += child; showAsCard(child) }
            else -> showAsCard(child)
        }
    }

    override fun onWindowClosed(window: AppWindow) {
        // The app that was exhibiting has gone: so has exhibition mode, or the shell would sit
        // there thinking it is still on and ignore the next press of Start Exhibition.
        if (exhibitionOn && window.appId == exhibitionApp &&
            (window.type == "dockMode" || running[window.appId]?.size == 1)) {
            setExhibition(false)
        }
        cards.cards.firstOrNull { it.window == window }?.let { cards.remove(it) }
        notifications.removeDashboard(window)
        notifications.popups.remove(window)
        closeWindow(window)
    }

    /** An icon from an app's origin (e.g. a dashboard's smallIcon), else the app's own icon. */
    private fun icon(url: String?, appId: String): android.graphics.Bitmap? {
        if (!url.isNullOrEmpty()) server.serve(android.net.Uri.parse(url))?.takeIf { it.statusCode == 200 }?.let { luna.decode(it.data)?.let { b -> return b } }
        return registry.get(appId)?.let { luna.appIcon(it) }
    }

    override fun addBanner(window: AppWindow, message: String, params: String, icon: String): Int {
        val id = bannerIds.getAndIncrement()
        runOnUiThread { notifications.addBanner(Banner(id, window, window.appId, message, icon(icon, window.appId), params)) }
        return id
    }

    /** A banner from the system itself, e.g. the package manager. Tapping it launches appId. */
    private fun systemBanner(appId: String, message: String, params: String = "") =
        notifications.addBanner(Banner(bannerIds.getAndIncrement(), null, appId, message, icon(null, appId), params))

    /**
     * Installs a package, as Preware did for the App Museum: banners report progress, and
     * tapping the "installed" banner launches the app.
     */
    private fun install(source: String, requester: String = "") {
        val name = android.net.Uri.decode(source.substringAfterLast('/'))
        systemBanner(requester, "Installing $name")
        packages.install(source) { r ->
            registry.reload()
            configurator.run()
            jsServices.reload()
            launcher.setApps(registry.launchPoints)
            dockMode.launchPointsChanged()
            val app = r.appIds.firstNotNullOfOrNull { registry.get(it) }
            when {
                r.error != null -> systemBanner(requester, "Couldn't install $name: ${r.error}")
                app == null -> systemBanner(requester, "Installed $name, but it has no app Lunacy can read")
                else -> systemBanner(app.id, "${app.title} installed")
            }
        }
    }
    override fun removeBanner(window: AppWindow, id: Int) = notifications.removeBanner(window, id)
    override fun clearBanners(window: AppWindow) = notifications.clearBanners(window)

    private fun toggleMenu() = if (notifications.menu.isOpen) closeMenu() else openMenu()

    /** The drop-down's right edge lines up with the notification group's right edge. */
    private fun openMenu() {
        if (!notifications.hasDashboards || notifications.popups.showing) return
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
    }

    private fun closeWindow(w: AppWindow) {
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

    override fun onStageReady(window: AppWindow) {}

    override fun mediaBase() = mediaServer.base()

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
            else w.callMojo("positiveSpaceChanged", "${Math.round(cards.width / luna.density)},${Math.round((cards.height - h) / luna.density)}")
        } else {
            val resized = lp.bottomMargin != 0
            lp.bottomMargin = 0; cards.layoutParams = lp
            if (w != null && !resized) w.callMojo("positiveSpaceChanged", "${Math.round(cards.width / luna.density)},${Math.round(cards.height / luna.density)}")
            w?.let { win -> cards.post { win.callMojo("keyboardShown", "false") } }
            w?.removeInputFocus()
            goImmersive()  // Android shows its bars with the keyboard and leaves them up
        }
    }

    private fun hideKeyboard() {
        if (keyboardHeight > 0) imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    override val pixelScale get() = luna.density

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
    override fun deviceInfo(): String {
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
        val w = if (d.naturalLandscape) long else short
        val h = if (d.naturalLandscape) short else long
        return JSONObject(mapOf(
            "modelName" to d.modelName, "modelNameAscii" to d.modelNameAscii,
            "platformVersion" to d.platformVersion, "platformVersionMajor" to d.platformVersionMajor,
            "platformVersionMinor" to d.platformVersionMinor, "platformVersionDot" to d.platformVersionDot,
            "carrierName" to d.carrierName,
            "serialNumber" to org.webosarchive.lunacy.card.DeviceProfile.serial(this, d),
            "screenWidth" to w, "screenHeight" to h,
            "minimumCardWidth" to w, "minimumCardHeight" to d.minimumCardHeight,
            "maximumCardWidth" to w, "maximumCardHeight" to h - d.positiveSpaceTopPadding,
            "touchableRows" to d.touchableRows,
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
    override fun windowOrientation(): String = profile.windowOrientationFor(screenOrientation())

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
        val bmp = chosen?.let { f -> luna.decodeFull(f) } ?: luna.wallpaper()
        if (bmp != null) {
            wallpaperView.setImageBitmap(bmp)
            wallpaperView.background = null
        } else {
            wallpaperView.setImageDrawable(null)
            wallpaperView.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 24, 64), Color.rgb(20, 90, 200)))
        }
    }

    /**
     * A stored preference the shell owns. webOS worked the same way: the service keeps the
     * key, and whoever owns the thing it names acts on it. Keys nothing here owns are kept
     * and nothing more, as they were on a device.
     */
    private fun onPreferenceChanged(key: String, value: Any?) {
        when (key) {
            "wallpaper" -> showWallpaper()
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
        launch(appId, dockModeParams())
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
            .put("cardHeight", Math.round(cards.height / luna.density))
            .put("pixelGrid", pixelGrid(cards.width, cards.height))
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
            // A link, an email, a phone number, a map: webOS's own apps owned these and Lunacy
            // doesn't have them, so Android's answer instead. See WebosLinks.
            val link = org.webosarchive.lunacy.card.WebosLinks.intentFor(id, params, p.optString("target"))
            if (link != null && registry.get(id) == null) {
                reply(org.webosarchive.lunacy.card.WebosLinks.open(this, link))
                return@Handler
            }
            // The App Museum installs through Preware (LuneOS's on LuneOS); Lunacy's package
            // manager answers for it. See Docs/architecture.md, "Package manager and App Museum".
            if (id in INSTALLERS && registry.get(id) == null && params?.optString("type") == "install") {
                val file = params.optString("file")
                if (file.isEmpty()) reply(Bus.error("install: no file given"))
                else { install(file, caller); reply(Bus.ok(mapOf("processId" to "success"))) }
            } else if (registry.get(id) == null) reply(Bus.error("Application not found: $id"))
            else if (registry.get(id)?.isWeb == false) { launch(id); reply(Bus.error("Native apps aren't supported yet: $id")) }
            else { launch(id, params); reply(Bus.ok(mapOf("processId" to "success"))) }
        }
        SystemProperties(this).register(bus)
        // webOS's preference and wallpaper store, and the two display settings Android lets
        // Lunacy really set. See Docs/architecture.md, "Settings".
        displayService = org.webosarchive.lunacy.card.DisplayService(this)
        displayService.onDockMode = { on -> runOnUiThread { setExhibition(on) } }
        displayService.register(bus)
        dockMode = org.webosarchive.lunacy.card.DockMode(this, registry).also { it.register(bus) }
        systemService = org.webosarchive.lunacy.card.SystemService(jsServices.root, java.io.File(filesDir, "systemservice.json"))
        systemService.onPreferenceChanged = { key, value -> onPreferenceChanged(key, value) }
        systemService.register(bus)
        // Lunacy's own service, on its own name: the environment it really runs in, and
        // Android's settings screens for the settings Android owns.
        org.webosarchive.lunacy.card.LunacyService(this, registry, jsServices, jsServices.root) { displayInfo() }.register(bus)
        org.webosarchive.lunacy.card.ConnectionManager(this).register(bus)
        org.webosarchive.lunacy.card.ActivityManager().register(bus)
        val db8 = org.webosarchive.lunacy.card.Db8("com.palm.db", java.io.File(filesDir, "db8.sqlite")).also { it.register(bus) }
        val tempdb = org.webosarchive.lunacy.card.Db8("com.palm.tempdb", null).also { it.register(bus) }
        configurator = org.webosarchive.lunacy.card.Configurator(files, db8, tempdb)
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
        running[app.id]?.toList()?.forEach { w -> onWindowClosed(w) }
        packages.remove(app.id) { error ->
            registry.reload()
            configurator.run()
            jsServices.reload()
            launcher.setApps(registry.launchPoints)
            dockMode.launchPointsChanged()
            showDock()
            systemBanner("", if (error == null) "${app.title} removed" else "Couldn't remove ${app.title}: $error")
        }
    }

    // ---- the dock ----

    private val dockPrefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    /** The dock's apps, as the user arranged them; at first, the first five apps. */
    private fun dock(): List<String> {
        val saved = dockPrefs.getString("dock", null)
            ?: return registry.launchPoints.take(QuickLaunch.MAX_ITEMS).map { it.id }
        val a = runCatching { org.json.JSONArray(saved) }.getOrDefault(org.json.JSONArray())
        return (0 until a.length()).map { a.optString(it) }
    }

    private fun setDock(ids: List<String>) {
        dockPrefs.edit().putString("dock", org.json.JSONArray(ids.distinct().take(QuickLaunch.MAX_ITEMS)).toString()).apply()
        showDock()
    }

    private fun showDock() { quickLaunch.apps = dock().mapNotNull { registry.get(it) } }

    /**
     * A launcher icon dropped on the dock. It joins at the slot under it; one already on the
     * dock moves there. On a full dock it takes the place of the icon it lands on (LunaCE
     * refused a sixth icon; swapping is Lunacy's).
     */
    private fun dropOnDock(app: AppInfo, x: Float) {
        val ids = dock().filter { registry.get(it) != null }.toMutableList()
        val present = ids.indexOf(app.id)
        when {
            present >= 0 -> { ids.removeAt(present); ids.add(quickLaunch.slotAt(x, ids.size + 1), app.id) }
            ids.size < QuickLaunch.MAX_ITEMS -> ids.add(quickLaunch.slotAt(x, ids.size + 1), app.id)
            else -> ids[quickLaunch.slotAt(x, ids.size)] = app.id
        }
        setDock(ids)
    }

    private fun exitEditMode() {
        launcher.exitEditMode()
        quickLaunch.editing = false
    }

    override fun onMaximized(card: Card) {
        quickLaunch.cancelLaunchFeedback()
        // A keyboard asked for while this card was still opening (see keyboard()).
        if (keyboardWanted == card.window) {
            keyboardWanted = null
            card.window.requestFocus()
            imm.showSoftInput(card.window, 0)
        }
        statusBar.title = registry.get(card.window.appId)?.title ?: card.window.appId
        statusBar.setMode(StatusBar.Mode.APP)
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
        if (!launcherOpen) fade(justType, true)
        showDock(true)
        cards.cards.forEach { it.window.setStageActive(false) }
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
        if (exhibitionMenu.visibility == View.VISIBLE) { closeExhibitionMenu(); return }
        if (exhibitionOn) { setExhibition(false); return }
        if (notifications.menu.isOpen) { closeMenu(); return }
        notifications.popups.newest()?.let { onWindowClosed(it); return }
        if (launcher.editing || quickLaunch.editing) { exitEditMode(); return }
        if (launcherOpen) { closeLauncher(); return }
        val max = cards.maximized
        if (max != null) { cards.showCardView(); return }
        toggleLauncher()
    }

    // ---- overlays (reference §3.1, §3.2) ----

    private fun toggleLauncher() = if (launcherOpen) closeLauncher() else openLauncher()

    /** Slides up from below the screen; the cards are hidden once it is fully open. */
    private fun openLauncher() {
        if (cards.maximized != null) return
        launcherOpen = true
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
        cards.visibility = View.VISIBLE
        launcher.animate().translationY(launcher.height.toFloat()).setDuration(LAUNCHER_MS).setInterpolator(Easing.InOutQuint)
            .withEndAction { if (!launcherOpen) { launcher.visibility = View.INVISIBLE; launcher.cancelLaunchFeedback(); exitEditMode() } }.start()
        if (cards.maximized == null) { fade(justType, true); statusBar.setMode(StatusBar.Mode.CARDS) }
    }

    private fun fade(v: View, show: Boolean) {
        v.animate().cancel()
        if (show) v.visibility = View.VISIBLE
        v.animate().alpha(if (show) 1f else 0f).setDuration(FADE_MS).setInterpolator(Easing.OutCubic)
            .withEndAction { if (!show) v.visibility = View.INVISIBLE }.start()
    }

    /** The dock slides by its own height and fades (quickLaunchDuration, quickLaunchFadeDuration). */
    private fun showDock(show: Boolean) {
        val h = quickLaunch.height.toFloat().takeIf { it > 0 } ?: luna.px(QuickLaunch.HEIGHT).toFloat()
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
        const val LAUNCHER_MS = 350L   // reference TouchPad lunaAnimations.conf: launcherDuration 350, curve 15
        const val DOCK_MS = 350L       // quickLaunchDuration
        const val FADE_MS = 200L       // quickLaunchFadeDuration
        /** Long enough for the launch glow to be drawn before the card work starts. */
        const val LAUNCH_DELAY_MS = 60L
        /** Android's screen saver asking for Exhibition; see ExhibitionDream. */
        const val EXTRA_EXHIBITION = "exhibition"
        /** Package installers apps hand .ipks to: Preware on webOS, and LuneOS's Preware. */
        val INSTALLERS = setOf("org.webosinternals.preware", "org.webosports.app.preware")
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
