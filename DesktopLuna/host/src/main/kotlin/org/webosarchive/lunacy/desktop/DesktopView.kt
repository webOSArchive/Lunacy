package org.webosarchive.lunacy.desktop

import org.webosarchive.lunacy.card.LifecycleWindow
import org.webosarchive.lunacy.card.WindowType

/**
 * One window of a running app in Luna DE, as LunaCore's AppLifecycle keeps it: AppWindow's
 * counterpart. On the screen it is a view in Electron (a WebContentsView), named there by
 * [id]; what it shows is a page ([DesktopWindow]), which a reload replaces while the window
 * stays. The host makes an app's first window before Electron has a view for it (ids "h1",
 * "h2" ...), and Electron's view follows from the first event that names it; a window a page
 * opens (window.open) is Electron's first ("e1" ...), and the host hears of it (DesktopShell.opened).
 *
 * Everything here is the main thread's, but [pid] and [id].
 */
class DesktopView(
    val id: String,
    override val appId: String,
    override val type: WindowType?,
    private val shell: DesktopShell,
    pid: Int? = null,
) : LifecycleWindow {
    /** The process id its pages are told (PalmSystem's), the window's for as long as it lives. */
    override val pid = pid ?: DesktopWindow.nextPid.getAndIncrement()

    /** appinfo.json's requestedWindowOrientation. Kept; Luna DE doesn't hold a card to it yet (Docs/luna-de.md). */
    override var fixedOrientation: String? = null

    /** Where the window is on the screen (DesktopShell, as AppLifecycle.Screen). */
    enum class Place { NONE, CARD, PLACEHOLDER, HIDDEN, DASHBOARD, ALERT, DOCK }
    var place = Place.NONE

    /** The page it is showing, once that page has made its first native call. */
    @Volatile var page: DesktopWindow? = null
        private set
    private var closed = false
    /** Relaunches that came before the window had a page: they wait for it, as on Android (AppWindow.relaunch). */
    private val waitingRelaunches = ArrayList<Pair<String, (Boolean) -> Unit>>()

    /** [p] is the page this window shows now. */
    fun bind(p: DesktopWindow) {
        page = p
        p.view = this
        val waiting = waitingRelaunches.toList()
        waitingRelaunches.clear()
        waiting.forEach { (params, done) -> p.relaunch(params, done) }
    }

    /** [p] has gone (a reload, or the window closing). */
    fun unbind(p: DesktopWindow) { if (page === p) page = null }

    override fun load(url: String) = shell.load(this, url)

    override fun relaunch(params: String, done: (Boolean) -> Unit) {
        page?.relaunch(params, done) ?: run { waitingRelaunches += params to done }
    }

    override fun callMojo(fn: String, argsJson: String) { page?.callMojo(fn, argsJson) }

    /** Electron closes the view, which runs the page's unload handlers first. */
    override fun close() {
        if (closed) return
        closed = true
        shell.close(this)
    }

    override fun destroy() = close()
}
