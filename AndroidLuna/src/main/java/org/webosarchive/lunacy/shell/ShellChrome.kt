package org.webosarchive.lunacy.shell

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.ApplicationManager
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.DisplayService
import org.webosarchive.lunacy.card.FormFactor
import org.webosarchive.lunacy.card.HiddenApps
import org.webosarchive.lunacy.card.LunacyService
import org.webosarchive.lunacy.card.SystemService
import org.webosarchive.lunacy.card.UniversalSearch
import org.webosarchive.lunacy.card.WebosLinks

/** The whole display in real pixels, the way round it is now, bars and cutouts included. */
@Suppress("DEPRECATION")
internal fun Activity.realMetrics() = android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }

/**
 * The shell's own face, LunaSysMgr's SystemUiController and its overlays: the wallpaper, the
 * launcher with its groups and tabs, the Just Type pill and panel, the quick-launch dock, the
 * status bar and its menus, and what the screen does as a card is maximized or the card view
 * comes back, the home button, the keyboard and the screen's orientation. It is built in the
 * layout the screen calls for, phone or tablet, and built again in the other when that
 * changes ([reshell]), the cards ([CardHost]) and the apps ([AppLifecycle]) running on
 * through it. What it needs of the rest of the shell comes through [Host].
 */
class ShellChrome(
    private val activity: Activity,
    private val host: Host,
    private val bus: Bus,
    private val cardHost: CardHost,
    private val lifecycle: AppLifecycle,
    private val exhibitions: ExhibitionController,
    private val notificationHost: NotificationHost,
    private val handler: Handler,
) : CardLayer.Listener {
    interface Host {
        val registry: AppRegistry
        val sounds: Sounds
        val systemService: SystemService
        val displayService: DisplayService
        /** com.palm.universalsearch, once the services are registered. */
        val universalSearch: UniversalSearch?
        /** The apps the user has hidden with Software Manager. */
        val hiddenApps: HiddenApps
        /** What the launcher shows: every app and launch point less the hidden ones. */
        fun launchPoints(): List<AppInfo>
        /** What Just Type can launch. */
        fun justTypeApps(): List<AppInfo>
        /** An app by id, webOS or Android, or an added launch point: what the dock can hold. */
        fun appById(id: String): AppInfo?
        /** The launcher's Remove. */
        fun remove(app: AppInfo)
        /** The launcher's apps changed, for Software Manager to hear. */
        fun launcherAppsChanged()
        /** Asks Android for [permissions]; see ShellActivity.askPermissions. */
        fun askPermissions(permissions: List<String>, done: (granted: Boolean, askAgain: Boolean) -> Unit)
        fun systemBanner(appId: String, message: String)
        /** webOS's screen orientation. */
        fun screenOrientation(): String
        /** The shell's scale changed (a reshell): what was drawn at the old one goes. */
        fun scaleChanged()
    }

    /** The shell's art and scale: a phone's or a tablet's, made again by [reshell]. */
    var luna = Luna(activity, FormFactor.shown == FormFactor.Kind.PHONE)
        private set

    private val cards: CardLayer get() = cardHost.cards
    private val notifications: Notifications get() = notificationHost.notifications
    private val notificationsAtBottom: Boolean get() = notificationHost.notificationsAtBottom

    lateinit var statusBar: StatusBar
        private set
    private lateinit var justType: JustType
    private lateinit var justTypePanel: JustTypePanel
    private lateinit var quickLaunch: QuickLaunch
    lateinit var launcher: Launcher
        private set
    private lateinit var tabDialog: TabDialog
    private lateinit var groupOverlay: GroupOverlay
    var launcherOpen = false
        private set
    private lateinit var systemMenu: SystemMenu
    private lateinit var systemControls: SystemControls
    /** Catches taps outside the dashboard drop-down, which close it. */
    lateinit var menuScrim: View
        private set
    /** The wallpaper view, reloaded when the preference changes. */
    private lateinit var wallpaperView: ImageView

    /** The shell's own root view, under the wallpaper: what [buildShell] lays out and [reshell] replaces. */
    private var shellRoot: FrameLayout? = null

    /**
     * The shell's views, at [luna]'s scale and in its layout: wallpaper, cards, launcher, pill,
     * dock, notifications, menus, status bar. Built once at startup, and again by [reshell]
     * when the layout changes; everything else - the services, the bus, the apps' windows -
     * is the activity's own and outlives it.
     */
    fun buildShell() {
        // The dock draws an icon being dragged out of it above its own bounds.
        val root = FrameLayout(activity).apply { clipChildren = false }
        wallpaperView = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        showWallpaper()
        val screen = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
        screen.addView(wallpaperView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        screen.addView(root, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        cardHost.build(root)
        buildLauncher(root)
        buildJustType(root)
        buildQuickLaunch(root)
        buildBarAndMenus(screen, root)

        fitCutout(screen, root)
        activity.setContentView(screen)
        screen.requestApplyInsets()
        watchKeyboard(root)
        statusBar.onTitleTap = { if (exhibitions.exhibitionOn) exhibitions.toggleExhibitionMenu() else toggleAppMenu() }
        shellRoot = root
    }

    /** The launcher, with its groups' panel and its tab dialog. */
    private fun buildLauncher(root: FrameLayout) {
        // Overlays, bottom to top as in LunaCE's OverlayWindowManager: launcher, pill, dock.
        // The icon glows first; the launch follows a frame later, so the glow is seen.
        launcher = Launcher(activity, luna) { app -> launcher.postDelayed({ launchFromLauncher(app) }, LAUNCH_DELAY_MS) }
        launcher.onRemove = { app -> host.remove(app) }
        launcher.appTitle = { id -> host.registry.get(id)?.title }
        setLauncherApps()
        launcher.dockHeight = luna.px(QuickLaunch.barHeight(luna.phone)).toFloat()
        launcher.visibility = View.INVISIBLE
        root.addView(launcher, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        // [LunaCE] Launcher groups: the panel a group opens into.
        groupOverlay = GroupOverlay(activity, luna)
        root.addView(groupOverlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        launcher.onOpenGroup = { g, from -> groupOverlay.show(g, from) }
        groupOverlay.onLaunch = { app -> launcher.postDelayed({ launchFromLauncher(app) }, LAUNCH_DELAY_MS) }
        // [LunaCE] The group's glow stays while one of its members launches, and goes when
        // the panel is simply dismissed.
        groupOverlay.onDismissed = { g, launched -> if (launched) launcher.showLaunchFeedback(g.id) else launcher.cancelLaunchFeedback() }
        groupOverlay.onPopOut = { g, app -> launcher.popOut(g, app) }
        groupOverlay.onRenamed = { g -> launcher.groupChanged(g) }
        // [LunaCE] Renaming, adding and deleting launcher tabs.
        tabDialog = TabDialog(activity, luna)
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
        justType = JustType(activity, luna)
        // The pill takes text now, so something else has to hold focus first, or the shell
        // opens with a cursor blinking in it.
        root.isFocusableInTouchMode = true
        // The pill is 3/4 of the short side: a TouchPad's 768 px on a tablet, whatever the
        // screen (the shell keeps the TouchPad's proportions there); on a phone, the screen's
        // own, as the Pre3's shell laid it out on its 480 px (Docs/pre3.md).
        val shortSide = if (luna.phone) {
            activity.realMetrics().let { minOf(it.widthPixels, it.heightPixels) }
        } else (luna.density * 768).toInt()
        root.addView(justType, FrameLayout.LayoutParams((shortSide * JustType.WIDTH_OF_SHORT_SIDE).toInt(), luna.px(JustType.HEIGHT)).apply {
            topMargin = luna.px(StatusBar.HEIGHT + JustType.TOP_GAP); gravity = android.view.Gravity.CENTER_HORIZONTAL
        })

        // Just Type, under the status bar, over the cards, launcher, pill and dock.
        justTypePanel = JustTypePanel(activity, luna)
        root.addView(justTypePanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        justType.setOnClickListener { openJustType() }
        justTypePanel.onLaunch = { app -> closeJustType(); lifecycle.launch(app.id) }
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
        justTypePanel.appFor = { id -> host.registry.get(id) }
        host.universalSearch?.let { justTypePanel.search = it }
        justTypePanel.onSearch = { url ->
            closeJustType()
            if (host.registry.get(ApplicationManager.BROWSER) != null) lifecycle.launch(ApplicationManager.BROWSER, JSONObject().put("target", url))
            else WebosLinks.intentFor("", null, url)?.let { WebosLinks.open(activity, it) }
        }
    }

    /** com.palm.universalsearch has come up: Just Type searches with it. */
    fun searchReady(search: UniversalSearch) {
        if (::justTypePanel.isInitialized) justTypePanel.search = search
    }

    /** Just Type's settings changed: its panel reads them again. */
    fun searchChanged() {
        if (::justTypePanel.isInitialized) justTypePanel.reload()
    }

    /** The quick-launch dock along the bottom. */
    private fun buildQuickLaunch(root: FrameLayout) {
        quickLaunch = QuickLaunch(activity, luna, onLaunch = { app -> quickLaunch.postDelayed({ lifecycle.launch(app.id) }, LAUNCH_DELAY_MS) }, onLauncher = { toggleLauncher() })
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
        statusBar = StatusBar(activity, luna)
        statusBar.onBatteryFull = { host.sounds.batteryFull() }
        val popups = PopupLayer(activity, luna)
        root.addView(exhibitions.buildDock(), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        root.addView(popups, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { topMargin = luna.px(StatusBar.HEIGHT) })
        menuScrim = View(activity).apply {
            visibility = View.GONE
            setOnClickListener { closeMenu(); exhibitions.closeExhibitionMenu() }
        }
        root.addView(menuScrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        notificationHost.build(screen, root, popups)
        // The system menu, right-aligned under the bar, its edge 11 px past the screen's.
        systemControls = SystemControls(activity, host::askPermissions, ::openAndroidPanel) { key, value -> host.displayService.write(key, value) }
        systemMenu = SystemMenu(activity, luna, systemControls)
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

    /** The shell is going: what the chrome watches stops. */
    fun stop() {
        if (::systemControls.isInitialized) systemControls.stop()
    }

    /**
     * Another Android app has come over the shell. A launch that Android answers (a settings
     * shortcut, an Android app) covers the shell while the launcher is still sliding away, and
     * a stopped window's animation doesn't finish: the launcher came back on screen but closed,
     * deaf to taps. Finish it now.
     */
    fun covered() {
        if (::launcher.isInitialized && !launcherOpen && launcher.visibility == View.VISIBLE) {
            launcher.animate().cancel()
            launcher.translationY = launcher.height.toFloat()
            launcher.visibility = View.INVISIBLE; launcher.cancelLaunchFeedback(); exitEditMode()
        }
    }

    /** The launcher's apps, again, and Software Manager hears it. */
    fun setLauncherApps() {
        launcher.hidden = host.hiddenApps.all()
        launcher.setApps(host.launchPoints())
        host.launcherAppsChanged()
    }

    // ---- menus ----

    fun toggleMenu() = if (notifications.menu.isOpen) closeMenu() else openMenu()

    /** Opening one status-bar menu closes the other (StatusBar::slotMenuGroupActivated). */
    private fun toggleSystemMenu() {
        if (systemMenu.isOpen) { closeMenu(); return }
        closeMenu()
        // SystemMenu::slotPositiveSpaceChangeFinished: as tall as the room under the bar, plus 10.
        shellRoot?.let { if (it.height > 0) systemMenu.maxHeightPx = it.height - luna.px(StatusBar.HEIGHT) + luna.px(10) }
        systemMenu.batteryPercent = statusBar.batteryPercent
        systemMenu.brightness = (host.displayService.brightnessPercent() - 1) / 99f
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
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }
    private fun setMenuBrightness(percent: Int, done: Boolean) {
        val displayService = host.displayService
        displayService.setBrightnessPercent(percent)
        handler.removeCallbacks(releaseBrightness)
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = displayService.overrideFromPercent(percent) }
        if (done) handler.postDelayed(releaseBrightness, BRIGHTNESS_HOLD_MS)
    }

    /** SystemMenu.qml's closeMenuTimer. A menu closed and opened again meanwhile stays. */
    private var systemMenuCloser: Runnable? = null
    private fun closeSystemMenuIn(delayMs: Long) {
        systemMenuCloser?.let { handler.removeCallbacks(it) }
        if (!systemMenu.isOpen) return
        val r = Runnable { systemMenuCloser = null; if (systemMenu.isOpen) closeMenu() }
        systemMenuCloser = r
        handler.postDelayed(r, delayMs)
    }

    /** One of Android's settings screens, by LunacyService's panel name: where the system menu hands over. */
    private fun openAndroidPanel(panel: String) {
        val action = LunacyService.PANELS[panel] ?: return
        try {
            activity.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "systemMenu: can't open Android's $panel settings", e)
            host.systemBanner("", "Couldn't open Android's settings")
        }
    }

    fun closeMenu() {
        systemMenuCloser?.let { handler.removeCallbacks(it) }; systemMenuCloser = null
        menuScrim.visibility = View.GONE
        statusBar.menuOpen = false
        notifications.menu.close()
        statusBar.systemMenuOpen = false
        systemMenu.close()
    }

    // ---- orientation ----

    /**
     * A card that fixes its orientation turns the whole screen while it is maximized - status
     * bar, card and all - which is what the reference TouchPad does (its luna.conf has
     * displayUiRotates, and LunaSysMgr then rotated the UI rather than the card). webOS's
     * window "up" is portrait on both the TouchPad and a phone. The screen is let go again in
     * card view or when another card comes up; Android then follows the sensor at once, where
     * a TouchPad waited for the next time it was turned.
     */
    fun applyOrientation(window: AppWindow?) {
        val o = window?.takeUnless { it.emulated }?.fixedOrientation
        val want = ShellActivity.ORIENTATIONS[o] ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (activity.requestedOrientation != want) activity.requestedOrientation = want
    }

    /** No card is maximized any more: the status bar comes back and the screen's orientation goes free. */
    fun noCardUp() { statusBar.slide(false); applyOrientation(null) }

    // ---- keyboard ----

    private val imm by lazy { activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager }
    /** The keyboard's height in Android px, 0 when hidden. */
    private var keyboardHeight = 0

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
    fun keyboard(window: AppWindow, show: Boolean) {
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

    /** The emulated card's keyboard button. */
    fun showKeyboardForced(window: AppWindow) {
        window.requestFocus(); imm.showSoftInput(window, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
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
        notificationHost.area?.suspended = h > 0
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

    fun hideKeyboard() {
        if (keyboardHeight > 0) imm.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
    }

    // ---- the layout ----

    /**
     * The layout this shell is drawn in. Its own rather than FormFactor.shown: Android can run
     * a second shell activity in the process (Lunacy as the home app, beside one started
     * another way), and the newer one's decision mustn't stop this one rebuilding.
     */
    private fun shownKind() = if (luna.phone) FormFactor.Kind.PHONE else FormFactor.Kind.TABLET

    /** The screen changed size or shape: a foldable folded or opened may now call for the other layout. */
    fun screenChanged() {
        if (shownKind() != FormFactor.decide(activity)) reshell() else notificationHost.placeNotifications()
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
    fun reshell() {
        val kind = FormFactor.decide(activity)
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
        // The old system menu's watch on the radios goes with it; the new one starts its own.
        systemControls.stop()

        // What the old views hold, before they go.
        val handover = cardHost.handOver()
        val dashboards = notifications.dashboards
        val alerts = notifications.popups.windows
        val banners = notifications.handOverBanners()
        // Every page out of the old views but the cards' own, which come out as their new cards take them.
        for (w in lifecycle.windows) if (w.parent !is Card) (w.parent as? android.view.ViewGroup)?.removeView(w)

        FormFactor.shown = kind
        luna = Luna(activity, kind == FormFactor.Kind.PHONE)
        host.scaleChanged()
        buildShell()

        cardHost.takeOver(handover)
        exhibitions.takeWindowsBack()
        notificationHost.giveNotifications(dashboards, alerts, banners)
        // Once the pages are laid out in their new cards: the viewport is written from the card's size.
        cards.post { lifecycle.windows.forEach { it.rescale() } }
        // Pages loaded under the other layout fetch the images served by layout again.
        lifecycle.windows.forEach { it.evaluateJavascript(AppServer.REFETCH_LAYOUT_IMAGES, null) }
        val maximized = cards.maximized
        if (maximized != null) onMaximized(maximized) else onCardView()
        goImmersive()
    }

    // ---- the wallpaper ----

    /**
     * The wallpaper the system service holds, else the one Lunacy ships. The reference
     * TouchPad's own preference names a file in /media/internal/.wallpapers; Lunacy's is the
     * same shape, so Screen & Lock's "Change Wallpaper" sets this one.
     */
    fun showWallpaper() {
        val systemService = host.systemService
        val chosen = systemService.fileOf(systemService.get("wallpaper") as? JSONObject)
        val dm = activity.realMetrics()
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
    fun wallpaperSource(): () -> java.io.InputStream {
        val systemService = host.systemService
        val chosen = systemService.fileOf(systemService.get("wallpaper") as? JSONObject)
        return { chosen?.inputStream() ?: activity.assets.open("luna/wallpapers/${Luna.DEFAULT_WALLPAPER}") }
    }

    // ---- the dock ----

    private val dockPrefs by lazy { activity.getSharedPreferences("launcher", Activity.MODE_PRIVATE) }

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
                val o = org.json.JSONObject(activity.assets.open("luna/default-dock-positions.json").bufferedReader().use { it.readText() })
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
        val hiddenApps = host.hiddenApps
        dock().forEachIndexed { i, id -> if (id in hiddenApps && id !in all && all.size < quickLaunch.maxItems) all.add(minOf(i, all.size), id) }
        dockPrefs.edit().putString("dock", org.json.JSONArray(all.take(quickLaunch.maxItems)).toString()).apply()
        showDock()
    }

    /** The dock's ids that are still apps and aren't hidden, in the order they are drawn. */
    private fun shownDock() = dock().filter { host.appById(it) != null && it !in host.hiddenApps }

    fun showDock() { quickLaunch.apps = shownDock().mapNotNull { host.appById(it) } }

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

    // ---- the card layer's states ----

    /** Card view, while a launching card waits in it: the dock and the pill stay away, as on a device. */
    override fun onPreparing(card: Card) {
        if (launcherOpen) closeLauncher()
        onCardView()
        fade(justType, false)
        showDock(false)
    }

    override fun onMaximized(card: Card) {
        // A running app brought forward ends Exhibition too (setCardWindowMaximized); leaving
        // it comes back here with the card.
        if (exhibitions.cardArrivedInExhibition()) return
        cardHost.maximized(card)
        lifecycle.shown(card.window)
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
        statusBar.title = if (card.window.emulated) StatusBar.CARRIER_TEXT else host.registry.get(card.window.appId)?.title ?: card.window.appId
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
    fun toggleAppMenu() {
        val card = cards.maximized ?: return
        lifecycle.launch(card.window.appId, JSONObject().put("palm-command", "open-app-menu"))
    }

    override fun onCardView() {
        // Leaving an app opened from Android's home screen goes back there (HomeScreenPins).
        if (cardHost.leavingDirect()) activity.moveTaskToBack(true)
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
        if (host.systemService.get("sysUiEnableNextPrevGestures") != false) homePressed()
    }

    override fun onThrownAway(card: Card) {
        card.window.evaluateJavascript("try{window.close()}catch(e){}", null)
        lifecycle.closeWindow(card.window)
    }

    /**
     * Android's back button is the TouchPad's home button (webOS tablets had no back button).
     * Same order as LunaCE's SystemUiController, Key_CoreNavi_Home: close what's open on top
     * (dashboard, alert, menu, launcher, search), else minimize a maximized card, else
     * toggle the launcher.
     */
    fun homePressed() {
        if (exhibitions.menuOpen) { exhibitions.closeExhibitionMenu(); return }
        if (exhibitions.exhibitionOn) { exhibitions.setExhibition(false); return }
        if (notifications.menu.isOpen || systemMenu.isOpen) { closeMenu(); return }
        notifications.popups.newest()?.let { lifecycle.windowClosed(it); return }
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
        justTypePanel.apps = host.justTypeApps()
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

    /**
     * Just type: a keyboard's printable key in the card view or the launcher opens Just Type
     * with that character, as on a TouchPad with a keyboard paired (codepoet, 2026-10-02).
     * Not while a card is up, a text field (a tab's name, a group's) has the focus, or a
     * modifier is held. True if the key was taken.
     */
    fun typed(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && !justTypePanel.showing && cards.maximized == null &&
            !exhibitions.exhibitionOn && !tabDialog.showing && activity.currentFocus !is android.widget.EditText &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
            val ch = event.unicodeChar
            if (ch != 0 && (ch and android.view.KeyCharacterMap.COMBINING_ACCENT) == 0 && !Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                openJustType(String(Character.toChars(ch)))
                return true
            }
        }
        return false
    }

    // ---- overlays (reference §3.1, §3.2) ----

    private fun toggleLauncher() = if (launcherOpen) closeLauncher() else openLauncher()

    /** Slides up from below the screen; the cards are hidden once it is fully open. */
    fun openLauncher() {
        if (cards.maximized != null) return
        launcherOpen = true
        // SystemUiController::setLauncherShown, and the launcher's own title in the bar
        // (updateStatusBarTitle: "Launcher", with no ▾).
        host.sounds.feedback("LauncherOpenApp")
        statusBar.title = LAUNCHER_TITLE
        launcher.visibility = View.VISIBLE
        statusBar.setMode(StatusBar.Mode.LAUNCHER)
        launcher.translationY = launcher.height.toFloat()
        launcher.animate().translationY(0f).setDuration(LAUNCHER_MS).setInterpolator(Easing.InOutQuint)
            .withEndAction { if (launcherOpen) cards.visibility = View.INVISIBLE }.start()
        fade(justType, false)
    }

    /**
     * An app tapped in the launcher (or an open group). A webOS app's card comes up and the
     * launcher closes. An Android app opens over Lunacy and the launcher stays as it is, its
     * tab, scroll and open group kept: Home from the Android app comes back to it as it was
     * left, as webOS's Home came back to the launcher (codepoet, 2026-11-08).
     */
    private fun launchFromLauncher(app: AppInfo) {
        lifecycle.launch(app.id)
        if (app.androidComponent == null) closeLauncher()
    }

    fun closeLauncher() {
        if (!launcherOpen) return
        launcherOpen = false
        host.sounds.feedback("LauncherCloseApp")
        cards.visibility = View.VISIBLE
        launcher.animate().translationY(launcher.height.toFloat()).setDuration(LAUNCHER_MS).setInterpolator(Easing.InOutQuint)
            .withEndAction { if (!launcherOpen) { launcher.visibility = View.INVISIBLE; launcher.cancelLaunchFeedback(); exitEditMode() } }.start()
        tabDialog.dismiss()
        groupOverlay.close()
        if (cards.maximized == null) { fade(justType, true); statusBar.setMode(StatusBar.Mode.CARDS); statusBar.title = StatusBar.CARRIER_TEXT }
    }

    /** Exhibition shown or gone: the pill and the dock make way for it, or come back. */
    fun showChrome(exhibiting: Boolean) {
        fade(justType, !exhibiting && cards.maximized == null && !launcherOpen)
        showDock(!exhibiting && cards.maximized == null)
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
        activity.window.attributes = activity.window.attributes.apply {
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
                val dm = activity.realMetrics()
                tl = minOf(dm.widthPixels, dm.heightPixels) * ASSUMED_CORNER; tr = tl
            }
            // A side the shell is kept off (the cutout's, turned) is clear of its corner.
            statusBar.corners = (if (l > 0) 0f else tl) to (if (r > 0) 0f else tr)
            // The bottom corners, the same way, for a phone's notification bar along the bottom.
            var bl = corner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)
            var br = corner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)
            if (cut != null && bl == 0f && br == 0f) {
                val dm = activity.realMetrics()
                bl = minOf(dm.widthPixels, dm.heightPixels) * ASSUMED_CORNER; br = bl
            }
            notificationHost.area?.corners = if (b > 0) 0f to 0f else (if (l > 0) 0f else bl) to (if (r > 0) 0f else br)
            // The camera's span across the bar, in the bar's x, a 3 px margin either side.
            val bar = luna.px(StatusBar.HEIGHT)
            statusBar.cutout = cut?.boundingRects?.filter { it.top < bar && !it.isEmpty }
                ?.takeIf { it.isNotEmpty() }
                ?.let { rs -> (rs.minOf { it.left } - l - luna.px(3f))..(rs.maxOf { it.right } - l + luna.px(3f)) }
            insets
        }
    }

    /** Full screen: hide Android's bars; a swipe from the edge shows them for a moment. */
    fun goImmersive() {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    companion object {
        /** How long the slider's override outlasts the finger: the fast ramp (0.7 a second) crossing the whole range, and some. */
        const val BRIGHTNESS_HOLD_MS = 3000L
        /** The launcher's launch point title, com.palm.launcher's on the reference TouchPad. */
        const val LAUNCHER_TITLE = "Launcher"
        /** An unreported rounded corner's radius on a screen with a cutout, as a part of its short side (fitCutout). */
        const val ASSUMED_CORNER = 0.1f
        const val LAUNCHER_MS = 350L   // reference TouchPad lunaAnimations.conf: launcherDuration 350, curve 15
        const val DOCK_MS = 350L       // quickLaunchDuration
        const val FADE_MS = 200L       // quickLaunchFadeDuration
        /** Long enough for the launch glow to be drawn before the card work starts. */
        const val LAUNCH_DELAY_MS = 60L
        /** Just Type's app id on a device (luna-applauncher): what its bus calls come from, and what db8 grants read to. */
        const val JUST_TYPE = "com.palm.launcher"
    }
}
