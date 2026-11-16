package org.webosarchive.lunacy.desktop

import org.json.JSONObject
import org.webosarchive.lunacy.card.AppFiles
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.ApplicationManager
import org.webosarchive.lunacy.card.Configurator
import org.webosarchive.lunacy.card.LaunchPoints
import org.webosarchive.lunacy.card.Packages
import org.webosarchive.lunacy.card.Placement
import org.webosarchive.lunacy.card.RunningWindow
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Luna DE's shell, as LunaCore's services see it: what the application manager asks of a shell
 * (ApplicationManager.Shell), answered for the desktop. Which apps run and with which windows is
 * kept here from the pages themselves ([windows]); what goes on the screen is Electron's, which
 * hears of it as events ([take]): an app to start, a card to bring forward or close, the launcher
 * to draw again, a banner.
 *
 * Launching follows AndroidLuna's AppLifecycle, which this stands in for until that class moves
 * into LunaCore (Docs/luna-de.md, milestone 3b): a running app is relaunched, not reloaded, and
 * its first card comes forward unless the app handled the relaunch or opened a window for it.
 */
class DesktopShell(
    private val main: DesktopScheduler,
    private val registry: AppRegistry,
    private val files: AppFiles,
    private val packages: Packages,
    private val addedLaunchPoints: LaunchPoints,
    private val data: File,
) : ApplicationManager.Shell {
    /** Every page with a native interface, by its page id, oldest first within an app. */
    val windows = ConcurrentHashMap<String, DesktopWindow>()
    private val events = LinkedBlockingQueue<JSONObject>()
    lateinit var configurator: Configurator
    lateinit var applicationManager: ApplicationManager

    // ---- events to Electron ----

    private fun event(type: String) = JSONObject().put("type", type)

    /** What Electron is to do next, after waiting up to [ms] for the first. */
    fun take(ms: Long): List<JSONObject> {
        val first = events.poll(ms, TimeUnit.MILLISECONDS) ?: return emptyList()
        return arrayListOf(first).also { events.drainTo(it) }
    }

    fun systemBanner(appId: String, message: String) {
        events.add(event("banner").put("appId", appId).put("message", message))
    }

    // ---- the apps that are running ----

    private fun windowsOf(appId: String) = windows.values.filter { it.appId == appId }.sortedBy { it.pid }

    override val running: Map<String, List<RunningWindow>>
        get() = windows.values.sortedBy { it.pid }.groupBy { it.appId }

    override fun closeWindow(window: RunningWindow) {
        (window as? DesktopWindow)?.let { events.add(event("close").put("page", it.pageId)) }
    }

    /** A page has gone: its subscriptions end. */
    fun gone(pageId: String) { windows.remove(pageId)?.close() }

    // ---- launching ----

    override fun launch(appId: String, params: JSONObject?, placement: Placement, launchedBy: String?) {
        main.post { launchNow(appId, params, placement) }
    }

    private fun launchNow(appId: String, params: JSONObject?, placement: Placement) {
        // An added launch point launches its app with its own params.
        addedLaunchPoints.get(appId.removePrefix(AppInfo.LAUNCH_POINT_PREFIX))?.takeIf { appId.startsWith(AppInfo.LAUNCH_POINT_PREFIX) }?.let { lp ->
            launchNow(lp.appId, lp.paramsObject(), placement); return
        }
        val app = registry.get(appId) ?: run { Log.w(Lunacy.TAG, "launch: no app $appId"); return }
        if (!app.isWeb) { systemBanner(appId, "${app.title} is a native app; Luna DE runs web apps"); return }
        if (app.androidSettings.isNotEmpty()) { systemBanner(appId, "${app.title} opens one of Android's settings; there is none on the desktop"); return }
        val root = windowsOf(appId).firstOrNull()
        if (root != null) {
            val before = windowsOf(appId).map { it.pageId }.toSet()
            root.relaunch(params?.toString() ?: "") { handled ->
                // A window the app opened while it handled the relaunch is its answer (AppLifecycle.relaunch).
                val opened = windowsOf(appId).any { it.pageId !in before }
                Log.i(Lunacy.TAG, "relaunch $appId handled=$handled${if (opened) ", opened a window" else ""}")
                if (!handled && !opened) events.add(event("show").put("id", appId))
            }
            return
        }
        val url = AppServer.appUrl(app.id, app.main, app.dir) +
            (if (params != null) "?launchParams=" + URLEncoder.encode(params.toString(), "UTF-8").replace("+", "%20") else "")
        events.add(event("launch").put("id", app.id).put("title", app.title).put("url", url).put("noWindow", app.noWindow)
            .put("noCard", placement != Placement.CARD))
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

    override fun removeInstalled(app: AppInfo, done: (String?) -> Unit) {
        windowsOf(app.id).forEach { closeWindow(it) }
        packages.remove(app.id, app.dir) { error ->
            appsChanged()
            done(error)
        }
    }

    override fun appsChanged() {
        val before = registry.launchPoints.associateBy { it.id }
        registry.reload()
        configurator.run()
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
}

/**
 * Luna DE's webOS root as [org.webosarchive.lunacy.card.ScriptHost]: it has no commands yet, and
 * package scripts aren't run (Docs/luna-de.md, open question 2: a script would run as the
 * developer on their own machine).
 */
class DesktopRoot(override val root: File) : org.webosarchive.lunacy.card.ScriptHost {
    override val runsScripts get() = false
    override fun prepare() {}
    override fun startBus() {}
    /** A webOS path as the file it is under the root; anything else is left as it is. */
    override fun mapPaths(s: String): String =
        if (ROOTS.any { s == "/$it" || s.startsWith("/$it/") }) File(root, s.trimStart('/')).path else s
    override fun environment(caller: String): Map<String, String> = emptyMap()

    private companion object {
        /** The top folders whose absolute paths are webOS's (WebosRoot.ROOTS on Android). */
        val ROOTS = listOf("usr", "media", "bin", "sbin", "var", "etc", "tmp", "home", "opt", "lib")
    }
}
