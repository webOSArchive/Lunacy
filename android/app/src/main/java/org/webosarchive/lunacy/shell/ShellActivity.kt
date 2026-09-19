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
    private lateinit var statusBar: StatusBar
    private lateinit var cards: CardLayer
    private lateinit var justType: JustType
    private lateinit var quickLaunch: QuickLaunch
    private lateinit var launcher: Launcher
    private var launcherOpen = false
    private lateinit var notifications: Notifications
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
        val files = AppFiles(assets, java.io.File(filesDir, "cryptofs/apps"))
        jsServices = org.webosarchive.lunacy.card.JsServices(this, bus, files.root)
        server = AppServer(assets, files, java.io.File(jsServices.root, "media/internal"))
        mediaServer = org.webosarchive.lunacy.card.MediaServer(java.io.File(jsServices.root, "media/internal"))
        registry = AppRegistry(files)
        org.webosarchive.lunacy.card.Http.init(assets)
        packages = Packages(files.root, java.io.File(cacheDir, "packages"))
        registerServices()
        jsServices.reload()

        val root = FrameLayout(this)
        val wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val wp = luna.wallpaper()
            if (wp != null) setImageBitmap(wp)
            else background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(8, 24, 64), Color.rgb(20, 90, 200)))
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        hidden = FrameLayout(this)
        root.addView(hidden, FrameLayout.LayoutParams(1, 1))

        cards = CardLayer(this, luna, this)
        root.addView(cards, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })

        // Overlays, bottom to top as in LunaCE's OverlayWindowManager: launcher, pill, dock.
        // The icon glows first; the launch follows a frame later, so the glow is seen.
        launcher = Launcher(this, luna) { app -> launcher.postDelayed({ launch(app.id); closeLauncher() }, LAUNCH_DELAY_MS) }
        launcher.onRemove = { app -> remove(app) }
        launcher.setApps(registry.apps)
        launcher.dockHeight = luna.px(QuickLaunch.HEIGHT).toFloat()
        launcher.visibility = View.INVISIBLE
        root.addView(launcher, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })

        justType = JustType(this, luna)
        val shortSide = (luna.density * 768).toInt()
        root.addView(justType, FrameLayout.LayoutParams((shortSide * JustType.WIDTH_OF_SHORT_SIDE).toInt(), luna.px(JustType.HEIGHT)).apply {
            topMargin = luna.px(StatusBar.HEIGHT + JustType.TOP_GAP); gravity = android.view.Gravity.CENTER_HORIZONTAL
        })

        quickLaunch = QuickLaunch(this, luna, onLaunch = { app -> quickLaunch.postDelayed({ launch(app.id) }, LAUNCH_DELAY_MS) }, onLauncher = { toggleLauncher() })
        quickLaunch.apps = registry.apps
        root.addView(quickLaunch, FrameLayout.LayoutParams(MATCH_PARENT, luna.px(QuickLaunch.HEIGHT)).apply { gravity = android.view.Gravity.BOTTOM })

        // Notifications layer (reference §1.1): popup alerts, then the dashboard drop-down, under the status bar.
        statusBar = StatusBar(this, luna)
        val popups = PopupLayer(this, luna)
        root.addView(popups, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        menuScrim = View(this).apply { visibility = View.GONE; setOnClickListener { closeMenu() } }
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

        setContentView(root)
        watchKeyboard(root)
        statusBar.onTitleTap = { toggleAppMenu() }
        onCardView()
        goImmersive()

        handleIntent(intent)
    }

    /** Lunacy is single-task: later launch requests (e.g. from adb or, later, Android intents) arrive here. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Extras for development over adb: launch <appid> [params <json>], install <url or path>. */
    private fun handleIntent(intent: android.content.Intent) {
        intent.getStringExtra("install")?.let { install(it) }
        intent.getStringExtra("launch")?.let { launch(it, intent.getStringExtra("params")?.let { p -> JSONObject(p) }) }
    }

    // ---- apps ----

    fun launch(appId: String, params: JSONObject? = null) {
        val app = registry.get(appId) ?: run { Log.w(AppServer.TAG, "launch: no app $appId"); return }
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
            else -> showAsCard(child)
        }
    }

    override fun onWindowClosed(window: AppWindow) {
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
            launcher.setApps(registry.apps)
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

    /** Only the maximized card's window gets the keyboard, as on webOS. */
    override fun keyboard(window: AppWindow, show: Boolean) {
        if (show) {
            if (cards.maximized?.window != window) return
            window.requestFocus()
            imm.showSoftInput(window, 0)
        } else if (keyboardHeight > 0 && cards.maximized?.window == window) {
            imm.hideSoftInputFromWindow(window.windowToken, 0)
        }
    }

    /**
     * Follows the keyboard. In full screen Android doesn't resize the window for it, so the
     * visible frame gives its height. As LunaSysMgr did: Mojo.keyboardShown(true), then the
     * card shrinks to the space above the keyboard (or, for a window that asked not to be
     * resized, Mojo.positiveSpaceChanged reports that space); on hiding, the card grows back
     * and Mojo.keyboardShown(false) follows.
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
            goImmersive()  // Android shows its bars with the keyboard and leaves them up
        }
    }

    private fun hideKeyboard() {
        if (keyboardHeight > 0) imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    override val pixelScale get() = luna.density

    override fun deviceInfo(): String {
        // In TouchPad px, the unit apps lay out in.
        val dm = android.util.DisplayMetrics().apply {
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(this)
            density = luna.density
        }
        return JSONObject(mapOf(
            "modelName" to "TouchPad", "modelNameAscii" to "TouchPad",
            // webOS CE 3.1.0, the community-supported version, as the reference TouchPad reports.
            "platformVersion" to "3.1.0", "platformVersionMajor" to 3, "platformVersionMinor" to 1, "platformVersionDot" to 0,
            "carrierName" to "", "serialNumber" to SystemProperties.SERIAL,
            "screenWidth" to (dm.widthPixels / dm.density).toInt(), "screenHeight" to (dm.heightPixels / dm.density).toInt(),
            "minimumCardWidth" to (dm.widthPixels / dm.density).toInt(), "minimumCardHeight" to 318,
            "maximumCardWidth" to (dm.widthPixels / dm.density).toInt(), "maximumCardHeight" to ((dm.heightPixels / dm.density) - StatusBar.HEIGHT).toInt(),
            "touchableRows" to 14, "keyboardAvailable" to false, "keyboardSlider" to false, "keyboardType" to "Unknown",
            "wifiAvailable" to true, "bluetoothAvailable" to false, "carrierAvailable" to false,
            "coreNaviButton" to false, "swappableBattery" to false, "dockModeEnabled" to false,
        )).toString()
    }

    private fun registerServices() {
        val launchHandler = Bus.Handler { caller, p, reply ->
            val id = p.optString("id")
            val params = p.optJSONObject("params")
            // The App Museum installs through Preware (LuneOS's on LuneOS); Lunacy's package
            // manager answers for it. See docs/architecture.md, "Package manager and App Museum".
            if (id in INSTALLERS && registry.get(id) == null && params?.optString("type") == "install") {
                val file = params.optString("file")
                if (file.isEmpty()) reply(Bus.error("install: no file given"))
                else { install(file, caller); reply(Bus.ok(mapOf("processId" to "success"))) }
            } else if (registry.get(id) == null) reply(Bus.error("Application not found: $id"))
            else if (registry.get(id)?.isWeb == false) { launch(id); reply(Bus.error("Native apps aren't supported yet: $id")) }
            else { launch(id, params); reply(Bus.ok(mapOf("processId" to "success"))) }
        }
        SystemProperties(this).register(bus)
        org.webosarchive.lunacy.card.ConnectionManager(this).register(bus)
        org.webosarchive.lunacy.card.ActivityManager().register(bus)
        val db8 = org.webosarchive.lunacy.card.Db8("com.palm.db", java.io.File(filesDir, "db8.sqlite")).also { it.register(bus) }
        val tempdb = org.webosarchive.lunacy.card.Db8("com.palm.tempdb", null).also { it.register(bus) }
        configurator = org.webosarchive.lunacy.card.Configurator(packages.root, db8, tempdb)
        configurator.run()
        bus.register("com.palm.applicationManager", "launch", launchHandler)
        bus.register("com.palm.applicationManager", "open", launchHandler)
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
            launcher.setApps(registry.apps)
            systemBanner("", if (error == null) "${app.title} removed" else "Couldn't remove ${app.title}: $error")
        }
    }

    override fun onMaximized(card: Card) {
        quickLaunch.cancelLaunchFeedback()
        statusBar.title = registry.get(card.window.appId)?.title ?: card.window.appId
        statusBar.setMode(StatusBar.Mode.APP)
        if (launcherOpen) closeLauncher()
        fade(justType, false); showDock(false)
        card.window.setStageActive(true)
        cards.cards.filter { it != card }.forEach { it.window.setStageActive(false) }
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
        if (notifications.menu.isOpen) { closeMenu(); return }
        notifications.popups.newest()?.let { onWindowClosed(it); return }
        if (launcherOpen && launcher.editing) { launcher.exitEditMode(); return }
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
            .withEndAction { if (!launcherOpen) { launcher.visibility = View.INVISIBLE; launcher.cancelLaunchFeedback(); launcher.exitEditMode() } }.start()
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
