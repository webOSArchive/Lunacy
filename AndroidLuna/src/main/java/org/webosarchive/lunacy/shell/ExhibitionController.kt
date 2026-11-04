package org.webosarchive.lunacy.shell

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.DockMode

/**
 * Exhibition, webOS's dock mode: the shell's Time face and the apps' Exhibition windows in a
 * dock layer over the card view, the title's drop-down of faces, and Exhibition as Android's
 * screen saver. What it needs of the rest of the shell - its chrome, which a reshell rebuilds,
 * and launching and closing windows - comes through [Host].
 */
class ExhibitionController(private val activity: Activity, private val host: Host) {
    interface Host {
        val luna: Luna
        val registry: AppRegistry
        val dockMode: DockMode
        val statusBar: StatusBar
        val cards: CardLayer
        val menuScrim: View
        /** Whether the shell is the activity in front. */
        val inFront: Boolean
        val launcherOpen: Boolean
        val notificationsMenuOpen: Boolean
        /** Starts an app for the dock: no startup card, its window to [addDockWindow]. */
        fun launchForDock(appId: String, params: JSONObject)
        fun closeWindow(window: AppWindow)
        fun closeMenu()
        fun closeLauncher()
        fun hideKeyboard()
        /** No card holds the screen's orientation now. */
        fun releaseOrientation()
        /** The screen held on or let go, as Exhibition and the cards want it. */
        fun holdScreen()
        /** Back to what was under the dock: the maximized card, or the card view. */
        fun showCardsAgain()
        /** Just Type and the quick-launch dock, hidden while exhibiting. */
        fun showChrome(exhibiting: Boolean)
    }

    /** Exhibition mode: webOS's dock-mode clock, over everything, while it is on. */
    private lateinit var exhibition: ExhibitionLayer
    /** The drop-down the title opens while exhibiting, for changing face. */
    private lateinit var exhibitionMenu: ExhibitionMenu
    /** The app showing in exhibition mode, if it isn't the shell's own Time face. */
    private var exhibitionApp: String? = null
    /**
     * LunaSysMgr's DockModeWindowManager: the apps' Exhibition windows, by app, in a layer of
     * their own over the card view. They are never cards - an app on show in Exhibition is
     * not in the card view, and choosing another one doesn't add a card (codepoet, measured
     * on the reference TouchPad). Only the one on show is visible; the rest stay running
     * behind it, as webOS kept them, until the mode ends.
     */
    private lateinit var dockLayer: FrameLayout
    private val dockWindows = LinkedHashMap<String, AppWindow>()
    /** What the dock shows: an app's dock window, or the Time face. */
    private var dockShown: View? = null
    var exhibitionOn = false
        private set
    /** True while what is exhibiting was started by Android's screen saver (ExhibitionDream). */
    private var dreamExhibition = false
    /** Whether the shell was out of sight when the screen saver put it into exhibition. */
    private var dreamFromBackground = false

    /** The dock layer, with the Time face in it, built afresh with the shell's views. */
    fun buildDock(): FrameLayout {
        exhibition = ExhibitionLayer(activity, host.luna).apply {
            visibility = View.GONE
            onExit = { setExhibition(false) }
        }
        // The dock: black, as DockModeWindowManager::paint filled it, with the Time face and
        // the apps' dock windows in it, one on show at a time. It covers the cards, launcher
        // and dock; alerts, dashboards and the status bar stay over it, as on a device.
        dockLayer = DockLayer().apply { visibility = View.GONE; setBackgroundColor(android.graphics.Color.BLACK) }
        // A turn or a fold changes the dock's shape: its windows are fitted to it again.
        dockLayer.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) dockLayer.post { dockWindows.values.forEach { layDockWindow(it) } }
        }
        dockLayer.addView(exhibition, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return dockLayer
    }

    /** The title's drop-down of faces. */
    fun buildMenu(): ExhibitionMenu {
        exhibitionMenu = ExhibitionMenu(activity, host.luna).apply {
            visibility = View.GONE
            onChoose = { appId -> chooseExhibition(appId) }
        }
        return exhibitionMenu
    }

    val menuOpen get() = exhibitionMenu.visibility == View.VISIBLE

    /**
     * A window has closed. Out of the exhibition set first: ending the mode below closes what
     * is left in it, and this window mustn't be closed twice over.
     */
    fun windowClosed(window: AppWindow) {
        val docked = dockWindows[window.appId] == window
        if (docked) dockWindows.remove(window.appId)
        // The app that was exhibiting has gone: so has exhibition mode, or the shell would sit
        // there thinking it is still on and ignore the next press of Start Exhibition.
        if (docked && exhibitionOn && window.appId == exhibitionApp) setExhibition(false)
    }

    /** A reshell: the old dock's face is gone with its views. */
    fun forgetShown() { dockShown = null }

    /** A reshell: the dock windows go into the new dock layer. */
    fun takeWindowsBack() {
        for (w in dockWindows.values) {
            dockLayer.addView(w, FrameLayout.LayoutParams(0, 0))
            layDockWindow(w)
        }
    }

    /** The shell is going: the charger is no longer watched. */
    fun stop() {
        if (watchingPower) { activity.unregisterReceiver(powerReceiver); watchingPower = false }
    }

    /**
     * Exhibition as Android's screen saver. The dream itself has stood down by now (it would
     * end at the first touch, and webOS's Exhibition survived being touched), so the shell
     * takes on the rest of a dream's job for as long as it is standing in for one.
     */
    fun startDreamExhibition() {
        // Where to go back to when it ends: out of sight again if the screen saver is what
        // brought the shell forward, and nowhere if its owner was already using it.
        dreamFromBackground = !host.inFront
        dreamExhibition = true
        // A dream shows over the lock screen, and so did a TouchPad in its dock. The keyguard
        // is not dismissed with it: leaving exhibition puts the shell back where it came from,
        // so the lock comes back too.
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        // Android ends a dream when the device comes off its charger. Nothing else would end
        // this one - the screen is held on - so the shell watches for that itself, which is
        // also what a TouchPad did when it was lifted off its Touchstone.
        if (!watchingPower) {
            // The battery's broadcast is sticky, and registering hands back the one last sent,
            // so the charger is known now rather than at the next change.
            dreamPlugged = plugged(activity.registerReceiver(powerReceiver,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)))
            watchingPower = true
        }
        Log.i(AppServer.TAG, "exhibition: Android's screen saver; on a charger = $dreamPlugged")
        setExhibition(true)
    }

    private fun endDreamExhibition() {
        dreamExhibition = false
        dreamPlugged = false
        if (watchingPower) { activity.unregisterReceiver(powerReceiver); watchingPower = false }
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (dreamFromBackground) activity.moveTaskToBack(true)
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
     * Puts an app on show in Exhibition (DockModeWindowManager::switchApplication). One whose
     * dock window is already open is simply shown again; otherwise it is launched for the
     * dock, and its window takes the screen when it opens (addDockWindow).
     */
    /**
     * The status bar's title while [appId] is exhibiting: the app's own title. Measured on the
     * reference TouchPad: Photos in Exhibition reads "Photos & Videos", its title, not the
     * "Photos" of its exhibitionModeOptions (which the Exhibition app lists it by).
     */
    private fun barTitle(appId: String) = host.registry.get(appId)?.title ?: host.dockMode.title(appId)

    private fun exhibit(appId: String) {
        host.statusBar.title = barTitle(appId)
        dockWindows[appId]?.let { showDockFace(it); return }
        host.launchForDock(appId, dockModeParams())
    }

    /**
     * A window for the dock (DockModeWindowManager::addWindow): it fills the positive space,
     * and is shown if its app is the one on show. An app's second dock window replaces its
     * first, as LunaSysMgr closed the one already running.
     */
    fun addDockWindow(w: AppWindow) {
        dockWindows.put(w.appId, w)?.takeIf { it != w }?.let { host.closeWindow(it) }
        w.alpha = 0f
        w.visibility = View.INVISIBLE
        dockLayer.addView(w, FrameLayout.LayoutParams(0, 0))
        layDockWindow(w)
        if (exhibitionOn && w.appId == exhibitionApp) showDockFace(w)
    }

    /**
     * An Exhibition window at the TouchPad's dock size: its screen, 1024 x 768, less the 28 px
     * status bar above, upright or on its side as the dock is shaped. Exhibition was the
     * Touchstone's, and its apps were written for exactly that window - Photos hard-codes its
     * height in it, and on a dock any other height left its hidden controls on the screen. In
     * the positive space under the status bar, as large as fits, centred, black around it
     * (codepoet, 2026-10-19; Docs/luna-deltas.md B16).
     */
    fun layDockWindow(w: AppWindow) {
        val top = host.luna.px(StatusBar.HEIGHT)
        val aw = dockLayer.width; val ah = dockLayer.height - top
        val design = if (aw > ah) android.util.Size(1024, 768 - StatusBar.HEIGHT) else android.util.Size(768, 1024 - StatusBar.HEIGHT)
        w.designSize = design
        val lp = w.layoutParams as? FrameLayout.LayoutParams ?: return
        if (aw <= 0 || ah <= 0) { lp.width = MATCH_PARENT; lp.height = MATCH_PARENT; lp.topMargin = top; w.layoutParams = lp; return }
        val s = minOf(aw.toFloat() / design.width, ah.toFloat() / design.height)
        lp.width = Math.round(design.width * s); lp.height = Math.round(design.height * s)
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
        lp.topMargin = top + (ah - lp.height) / 2
        w.layoutParams = lp
        w.post { w.rescale() }
    }

    /**
     * The dock's layer. The swipe up from the bottom edge that minimizes a card ends the mode
     * instead, as the home button does (SystemUiController: in dock mode both go to
     * enterOrExitDockModeUi(false)). The edge band is the card layer's, dead zone included,
     * so a swipe up never reaches the app on its way.
     */
    private inner class DockLayer : FrameLayout(activity) {
        private var downY = 0f
        private var downX = 0f
        private var edge = false

        override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y
                    edge = e.y >= height - 1 - host.luna.px(CardLayer.Params.EDGE)
                    return edge
                }
                android.view.MotionEvent.ACTION_MOVE -> if (edge && swipedUp(e)) return true
            }
            return false
        }

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
            if (!edge) return false
            if (e.actionMasked == android.view.MotionEvent.ACTION_MOVE && swipedUp(e)) {
                edge = false
                setExhibition(false)
            }
            return true
        }

        private fun swipedUp(e: android.view.MotionEvent) =
            downY - e.y >= host.luna.px(CardLayer.Params.TRIGGER) && downY - e.y > Math.abs(e.x - downX)
    }

    /**
     * Shows [face] - an app's dock window, or the Time face - in place of what the dock was
     * showing, the one fading out as the other fades in (animateWindowChange). Windows not on
     * show stay running out of sight; only the one on show has the focus.
     */
    private fun showDockFace(face: View) {
        val old = dockShown
        dockShown = face
        dockWindows.values.forEach { it.setStageActive(it == face) }
        if (face == exhibition && old != exhibition) exhibition.reset()
        face.animate().cancel()
        face.visibility = View.VISIBLE
        if (old == null || old == face) { face.alpha = 1f; return }
        face.alpha = 0f
        face.animate().alpha(1f).setDuration(DOCK_FADE_MS).setInterpolator(Easing.InOutQuad).start()
        old.animate().cancel()
        old.animate().alpha(0f).setDuration(DOCK_FADE_MS).setInterpolator(Easing.InOutQuad)
            .withEndAction { if (dockShown != old) old.visibility = if (old == exhibition) View.GONE else View.INVISIBLE }
            .start()
    }

    /**
     * Something to show has arrived while Exhibition is on, which ends it: a card added or
     * maximized (SystemUiController::cardWindowAdded, setCardWindowMaximized), or an Android
     * app opened over the shell. Whatever it is, it is what the user is looking at, so the
     * screen saver's end doesn't send the shell out of sight. True if Exhibition was on.
     */
    fun cardArrivedInExhibition(): Boolean {
        if (!exhibitionOn) return false
        dreamFromBackground = false
        setExhibition(false)
        return true
    }

    fun setExhibition(on: Boolean) {
        // Entering again while it is already on is not a no-op: Palm's Exhibition app sends
        // this every time its button is pressed, and if the exhibiting app's window has since
        // closed, or the chosen app has changed, the press has to take effect.
        // Only leaving is idempotent.
        if (!on && !exhibitionOn) return
        exhibitionOn = on
        if (on) {
            host.closeMenu()
            if (host.launcherOpen) host.closeLauncher()
            host.hideKeyboard()
            // The face it was last left on, if that app is still switched on; otherwise the
            // shell's own Time face. webOS launched the app with dockMode set, which is how it
            // knows to show its exhibition view rather than its ordinary one.
            exhibitionApp = host.dockMode.defaultApp()
            // The dock covers the card view, which goes on underneath, untouched.
            host.cards.visibility = View.INVISIBLE
            host.cards.cards.forEach { it.window.setStageActive(false) }
            val app = exhibitionApp
            // Black until the app's window opens, as the dock was.
            dockLayer.visibility = View.VISIBLE
            if (app != null) {
                exhibit(app)
            } else {
                showDockFace(exhibition)
                host.statusBar.title = "Time"
            }
            host.statusBar.setMode(StatusBar.Mode.APP)
            host.statusBar.slide(false)
            host.releaseOrientation()
            host.holdScreen()
        } else {
            closeExhibitionMenu()
            exhibition.animate().cancel()
            exhibition.visibility = View.GONE
            // webOS closed what it had put in the dock when dock mode ended
            // (DockModeWindowManager::setDockModeState, DockModeCloseAppsOnExit), so an app's
            // Exhibition view doesn't turn up anywhere afterwards. An app that was started for
            // the dock ends with its window; one that was already running keeps its host.cards.
            // What was showing is where the next Exhibition opens, as LunaSysMgr's default.
            host.dockMode.setDefault(exhibitionApp)
            exhibitionApp = null
            dockShown = null
            dockWindows.values.toList().forEach { host.closeWindow(it) }
            dockLayer.visibility = View.GONE
            exhibition.alpha = 1f
            host.cards.visibility = View.VISIBLE
            host.holdScreen()
            if (dreamExhibition) endDreamExhibition()
            host.showCardsAgain()
        }
        host.showChrome(on)
    }

    /**
     * The title while exhibiting drops down the faces to choose from: the shell's own Time,
     * and every app its owner turned on in Palm's Exhibition app. webOS drew the same menu
     * from the same place, so the mode can be changed without leaving it.
     */
    fun toggleExhibitionMenu() {
        if (exhibitionMenu.visibility == View.VISIBLE) { closeExhibitionMenu(); return }
        val entries = mutableListOf(ExhibitionMenu.Entry(null, "Time", host.luna.image("dockmode/time-icon-48x48.png")))
        for (app in host.registry.apps) {
            if (!host.dockMode.isEnabled(app.id)) continue
            entries += ExhibitionMenu.Entry(app.id, host.dockMode.title(app.id), host.luna.appIcon(app))
        }
        exhibitionMenu.entries = entries
        exhibitionMenu.visibility = View.VISIBLE
        exhibitionMenu.bringToFront()
        host.statusBar.bringToFront()
        // The title reads "Choose an App" while the menu is down, as it does on a device.
        host.statusBar.title = "Choose an App"
        host.menuScrim.visibility = View.VISIBLE
    }

    fun closeExhibitionMenu() {
        if (exhibitionMenu.visibility != View.VISIBLE) return
        exhibitionMenu.visibility = View.GONE
        if (!host.notificationsMenuOpen) host.menuScrim.visibility = View.GONE
        host.statusBar.title = exhibitionApp?.let { barTitle(it) } ?: "Time"
    }

    /** A face chosen from that menu: null is the shell's own Time. */
    private fun chooseExhibition(appId: String?) {
        closeExhibitionMenu()
        if (appId == exhibitionApp) return
        // The app that was exhibiting stops being the one on show; webOS left it running.
        exhibitionApp = appId
        if (appId == null) {
            showDockFace(exhibition)
            host.statusBar.title = "Time"
        } else {
            exhibit(appId)
        }
    }

    private companion object {
        const val DOCK_FADE_MS = 500L
    }
}
