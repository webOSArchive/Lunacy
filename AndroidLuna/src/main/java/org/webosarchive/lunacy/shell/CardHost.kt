package org.webosarchive.lunacy.shell

import android.app.Activity
import android.os.Handler
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.AppWindow
import org.webosarchive.lunacy.card.DeviceProfile
import org.webosarchive.lunacy.card.EmulatedCard

/**
 * The card layer's one owner: where an app's windows are on the screen. It builds the card
 * layer and the headless windows' holder with the rest of the shell, puts a window in a card
 * (its group, its splash, its emulated chrome), holds a headless app's placeholder card until
 * the app opens a card of its own or settles without one, says when a card's app is ready,
 * and hands every card and headless page to the layer that replaces it when the shell is
 * rebuilt (ShellActivity.reshell). The app lifecycle decides which windows there are; this
 * decides where they go. What it needs of the rest of the shell comes through [Host].
 */
class CardHost(private val activity: Activity, private val host: Host, private val handler: Handler) {
    interface Host {
        /** The shell's art and scale, read afresh: a reshell makes a new one. */
        val luna: Luna
        val registry: AppRegistry
        val sounds: Sounds
        /** What the card layer tells the shell: maximized, card view, reordering and the rest. */
        val listener: CardLayer.Listener
        /** webOS's screen orientation (ShellActivity.screenOrientation). */
        fun screenOrientation(): String
        /** A card is arriving, which ends Exhibition (SystemUiController::cardWindowAdded). */
        fun cardArriving()
        /** No card is maximized any more: the status bar comes back and the screen's orientation goes free. */
        fun noCardUp()
        /** The emulated card's title: the app menu. */
        fun toggleAppMenu()
        /** The emulated card's keyboard button. */
        fun showKeyboard(window: AppWindow)
    }

    lateinit var cards: CardLayer
        private set
    /** Whether [build] has run: the shell's views exist. */
    val built get() = ::cards.isInitialized
    /** Headless root windows of noWindow apps: attached so their scripts run, never shown. */
    lateinit var hidden: FrameLayout
        private set

    /** The app that launched each app, until that launch's card is shown (showAsCard). */
    private val launchers = HashMap<String, String>()

    /**
     * The app opened from its icon on Android's home screen (HomeScreenPins), while its card is
     * the one up: it opened straight away, and leaving it goes back to Android rather than to
     * Lunacy's card view, so Lunacy's launcher never shows. Null otherwise.
     */
    private var directApp: String? = null
    /** Whether [directApp]'s card has been maximized: only then does going to the card view mean leaving. */
    private var directShown = false

    /**
     * The headless windows' holder, then the card layer, the whole screen, so that a
     * full-screen card can be laid out under the status bar; every other card starts below it
     * (CardLayer.inset).
     */
    fun build(root: FrameLayout) {
        hidden = FrameLayout(activity)
        root.addView(hidden, FrameLayout.LayoutParams(1, 1))
        val luna = host.luna
        cards = CardLayer(activity, luna, host.listener).apply { inset = luna.px(StatusBar.HEIGHT); feedback = { host.sounds.feedback(it) }
            // A phone's card view is the Pre3's: its own card ratios (Docs/pre3.md).
            phone = luna.phone
            // The TouchPad's landscape turned end for end, which Lunacy calls "left".
            upsideDown = { host.screenOrientation() == "left" } }
        root.addView(cards, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** [appId]'s launch was asked for by [launchedBy] (LunaSysMgr's launchingAppId), for the group its card joins. */
    fun launchedBy(appId: String, launchedBy: String?) {
        if (launchedBy != null && launchedBy != appId) launchers[appId] = launchedBy else launchers.remove(appId)
    }

    /** Opens [appId] for its icon on Android's home screen: its card comes up at once, and leaving it leaves Lunacy. */
    fun openDirect(appId: String) {
        directApp = appId
        directShown = false
    }

    /** A running app's card comes up at once for its icon on Android's home screen, from the card view otherwise. */
    private fun maximizeMs(appId: String) = if (appId == directApp) 0L else CardLayer.Params.MAXIMIZE_MS

    /** Brings [appId]'s first card up. */
    fun bringForward(appId: String) {
        cards.cards.firstOrNull { it.window.appId == appId }?.let { cards.maximize(it, maximizeMs(appId)) }
    }

    /** [w] goes on headless, attached so its scripts run, never shown. */
    fun hide(w: AppWindow) = hidden.addView(w, FrameLayout.LayoutParams(1, 1))

    /** Whether [w] is a headless window. */
    fun isHidden(w: AppWindow) = w.parent == hidden

    fun showAsCard(w: AppWindow, parent: AppWindow? = null, placeholder: Boolean = false, splash: Boolean = true) {
        // A card arriving ends Exhibition (SystemUiController::cardWindowAdded): an app opened
        // from a dashboard, or one that opens a card of its own.
        host.cardArriving()
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
     * An app's card window opened by [parent]: a headless app's first card fills the card its
     * launch put up, and the root goes on where a headless root lives; any other is a card of
     * its own.
     */
    fun cardOpened(parent: AppWindow, child: AppWindow) {
        val card = placeholderOf(parent)
        if (card == null) showAsCard(child, parent)
        else {
            val root = card.attach(child)
            hide(root)
            card.fullScreen = child.fullScreen && !child.emulated
            if (cards.maximized == card) host.listener.onMaximized(card)
        }
    }

    /** [window] has closed: its card goes, and with no card up the bar and the orientation are let go. */
    fun windowClosed(window: AppWindow) {
        cards.cards.firstOrNull { it.window == window }?.let { cards.remove(it) }
        // A full-screen card that closes itself while maximized takes the bar's absence with it,
        // and one that had fixed the screen's orientation lets it go.
        if (cards.maximized == null) host.noCardUp()
    }

    /** The card waiting for a headless app's first card window, if it is [window]'s. */
    private fun placeholderOf(window: AppWindow): Card? = cards.cards.firstOrNull { it.awaitingWindow && it.window == window }

    /** The app is up and running with no card of its own: the placeholder goes, and the root goes on headless. */
    private fun withdrawPlaceholder(window: AppWindow) {
        // On the activity's window rather than the card layer, which a rebuild replaces (reshell).
        activity.window.decorView.postDelayed({
            val card = placeholderOf(window) ?: return@postDelayed
            Log.i(AppServer.TAG, "launch ${window.appId}: no card opened; the placeholder goes")
            card.awaitingWindow = false
            (window.parent as? android.view.ViewGroup)?.removeView(window)
            hide(window)
            cards.remove(card)
            if (cards.maximized == null) host.noCardUp()
            if (cards.cards.isEmpty()) host.listener.onCardView()
        }, PLACEHOLDER_GRACE_MS)
    }

    /**
     * When a card's app counts as ready, as WindowedWebApp decided it: when the page calls
     * stageReady, or - a page that never does - 3 s after it finished loading
     * (kShowWindowTimeoutMs). Having drawn doesn't count: measured with the slow probe, the
     * reference TouchPad kept a page that had painted but not called stageReady off the screen
     * until it did. Here the card waits in the card view (CardLayer.openLaunching); being ready
     * maximizes it, and lets its placeholder go once the page has drawn as well (onPageDrawn).
     */
    fun pageLoaded(window: AppWindow) {
        handler.postDelayed({
            if (window.stageReady) return@postDelayed
            if (placeholderOf(window) != null) withdrawPlaceholder(window)
            else cardOf(window)?.let { cards.ready(it) }
        }, SHOW_WINDOW_TIMEOUT_MS)
    }

    /** The framework says the app is ready: a launching card maximizes, and its placeholder goes once the page has drawn. */
    fun stageReady(window: AppWindow) {
        if (placeholderOf(window) != null) withdrawPlaceholder(window)
        else cardOf(window)?.let { cards.ready(it) }
    }

    /** The app has drawn: the loading placeholder on its card can go. */
    fun pageDrawn(window: AppWindow) {
        cardOf(window)?.drawn = true
    }

    /** The card whose page is [window]; a placeholder's headless root is nobody's page. */
    private fun cardOf(window: AppWindow): Card? = cards.cards.firstOrNull { it.window == window && !it.awaitingWindow }

    /** A card for [w], drawn at the shell's scale. */
    private fun newCard(w: AppWindow, splash: Boolean): Card {
        val luna = host.luna
        // An app that never said it was laid out for a tablet gets the phone-sized card a
        // TouchPad gave it, rather than being stretched across the whole one.
        val emu = if (!w.emulated) null else Pair(
            luna.px(EmulatedCard.WIDTH.toFloat()).toInt(),
            luna.px((EmulatedCard.HEIGHT - DeviceProfile.TOUCHPAD.positiveSpaceTopPadding).toFloat()).toInt())
        val card = Card(activity, w, luna.px(CardLayer.Params.CORNER), emu,
            if (emu == null) null else luna.image("emucard-device-frame.png"),
            // webOS held the card's space with the app's own icon until it had drawn.
            if (splash) host.registry.get(w.appId)?.let { CardSplash(activity, luna, luna.splashIcon(it)) } else null,
            if (emu == null) null else luna, host.registry.get(w.appId)?.title ?: w.appId)
        // The emulated card's own chrome: its title is the app menu, its strip the back gesture,
        // its button the keyboard (EmulatedCardWindow).
        card.onChrome = { action ->
            when (action) {
                Card.ChromeAction.APP_MENU -> host.toggleAppMenu()
                Card.ChromeAction.BACK -> w.sendBack()
                Card.ChromeAction.KEYBOARD -> host.showKeyboard(w)
            }
        }
        return card
    }

    /**
     * PalmSystem.enableFullScreenMode. The card is laid out over the bar's space at once, as
     * LunaSysMgr resized the window at once, and the bar slides away if the card is up. Going
     * back, the bar slides in first and the card shrinks under it when it has arrived, so
     * nothing shows through the gap. LunaSysMgr left an emulated card's bar alone
     * (SystemUiController::applyWindowProperties), and so does this.
     */
    fun fullScreen(window: AppWindow, statusBar: StatusBar) {
        if (window.emulated) return
        val card = cards.cards.firstOrNull { it.window == window } ?: return
        when {
            window.fullScreen -> { card.fullScreen = true; if (cards.maximized == card) statusBar.slide(true) }
            cards.maximized == card -> statusBar.slide(false) { if (!window.fullScreen) card.fullScreen = false }
            else -> card.fullScreen = false
        }
    }

    /** PalmSystem.activate: the window's card comes up. */
    fun activate(window: AppWindow) {
        cards.cards.firstOrNull { it.window == window }?.let { if (cards.maximized != it) cards.maximize(it) }
    }

    /** [card] is up: the card opened from Android's home screen has been seen. */
    fun maximized(card: Card) {
        if (card.window.appId == directApp) directShown = true
    }

    /** The card view is showing: true if that means leaving the app opened from Android's home screen, for Android. */
    fun leavingDirect(): Boolean {
        if (directApp == null || !directShown) return false
        directApp = null; directShown = false
        return true
    }

    /** What the old layers hold, for the shell rebuilt in the other layout: the cards, and the headless pages. */
    class Handover internal constructor(internal val cards: CardLayer.Handover, internal val headless: List<android.view.View>)

    /** Stops the old layers and says what they hold; the pages stay in them until [takeOver]. */
    fun handOver() = Handover(cards.handOver(), (0 until hidden.childCount).map { hidden.getChildAt(it) })

    /** The new layers, [build] already, take every card - its frame made again at the new scale - and every headless page. */
    fun takeOver(h: Handover) {
        cards.takeOver(h.cards) { c ->
            val splashing = c.splashing
            newCard(c.release(), splashing).also { n ->
                n.fullScreen = c.fullScreen; n.awaitingWindow = c.awaitingWindow
                n.drawn = c.drawn; n.ready = c.ready
            }
        }
        // Out of the old holder, which goes with the old shell.
        h.headless.forEach { (it.parent as? android.view.ViewGroup)?.removeView(it); hidden.addView(it, FrameLayout.LayoutParams(1, 1)) }
    }

    companion object {
        /** WindowedWebApp's kShowWindowTimeoutMs: how long a loaded page gets to call stageReady. */
        const val SHOW_WINDOW_TIMEOUT_MS = 3000L
        /** How long after a headless app is up its placeholder card waits for it to open one. */
        const val PLACEHOLDER_GRACE_MS = 1000L
    }
}
