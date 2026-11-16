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
    var jsServices: org.webosarchive.lunacy.card.JsServices? = null
    var network: DesktopNetwork? = null

    // ---- events to Electron ----

    private fun event(type: String) = JSONObject().put("type", type)

    /** What Electron is to do next, after waiting up to [ms] for the first. */
    fun take(ms: Long): List<JSONObject> {
        val first = events.poll(ms, TimeUnit.MILLISECONDS) ?: return emptyList()
        return arrayListOf(first).also { events.drainTo(it) }
    }

    fun systemBanner(appId: String, message: String) {
        events.add(event("banner").put("id", nextBanner.getAndIncrement()).put("appId", appId).put("message", message).put("params", "").put("icon", ""))
    }

    // ---- the screen ----

    /** How the device is held: "right" and "left" are the TouchPad's landscape, "up" and "down" its portrait (Docs/spike-1.md). */
    @Volatile var orientation = "right"
        private set
    val portrait get() = orientation == "up" || orientation == "down"

    /** The device turned (Electron's window did): every page hears it. */
    fun turn(to: String) {
        if (to !in listOf("up", "down", "left", "right") || to == orientation) return
        orientation = to
        // But a popped-out window, which is held its own way.
        main.post { windows.values.filter { it.ownOrientation == null }.forEach { it.setScreenOrientation(to) } }
    }

    /** Something Electron tells one page, by its page id: its type, its focus, the back gesture. */
    fun tell(pageId: String, what: String, value: String) {
        val w = windows[pageId] ?: run { pendingTypes[pageId] = value.takeIf { what == "type" } ?: return; return }
        when (what) {
            "type" -> w.type = value.ifEmpty { null }
            "active" -> main.post { w.setStageActive(value == "true") }
            "focus" -> main.post { w.sendFocus(value == "true") }
            "back" -> main.post { w.sendBack() }
            // A window of its own (popped out) turned between wide and tall as it was resized.
            "orientation" -> main.post { w.ownOrientation = value; w.setScreenOrientation(value) }
        }
    }

    /** A window's type told before its page made its first native call. */
    val pendingTypes = ConcurrentHashMap<String, String>()

    // ---- banners ----

    private val nextBanner = java.util.concurrent.atomic.AtomicInteger(1)

    /** PalmSystem.addBannerMessage from [w]: its id, as the page gets it back. */
    fun banner(w: DesktopWindow, message: String, params: String, icon: String): Int {
        val id = nextBanner.getAndIncrement()
        events.add(event("banner").put("id", id).put("page", w.pageId).put("appId", w.appId).put("message", message)
            .put("params", params).put("icon", icon))
        return id
    }
    fun removeBanner(id: Int) { events.add(event("banner-remove").put("id", id)) }
    fun clearBanners(w: DesktopWindow) { events.add(event("banner-clear").put("appId", w.appId)) }
    fun activate(w: DesktopWindow) { events.add(event("activate").put("page", w.pageId)) }
    /** [w]'s page called stageReady or drew: its loading card gives way to it. */
    fun pageReady(w: DesktopWindow) { events.add(event("ready").put("page", w.pageId)) }

    // ---- the apps that are running ----

    /**
     * [appId]'s windows, oldest first. A page that has stopped asking for its scripts has gone
     * without anyone saying so (Electron tells us of every page it destroys; this is for the one
     * it didn't): it is dropped here, so a launch never becomes a relaunch of a window that isn't there.
     */
    private fun windowsOf(appId: String): List<DesktopWindow> {
        windows.values.filter { it.appId == appId && !it.alive }.forEach { Log.w(Lunacy.TAG, "$appId: a page stopped answering; forgotten"); gone(it.pageId) }
        return windows.values.filter { it.appId == appId }.sortedBy { it.pid }
    }

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
        // On Android the Wi-Fi tile opens Android's Wi-Fi settings. Here it is a developer's switch:
        // the connection lost, and back, so an app can be seen in both states (codepoet, 2026-11-16).
        if (app.androidSettings == "wifi") { toggleNetwork(appId); return }
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

    // ---- First Use ----

    /**
     * Luna DE has no First Use (Docs/luna-de.md, "Rule 0": a developer's tool starts ready), so
     * what Lunacy's shell does when First Use ends is done at the first start: the flag that says
     * it ran, and the profile account the accounts service makes (createNovaAccount), which
     * Calendar, Contacts and Memos keep their on-device data on. Then the configurator runs again,
     * for the activity files it holds back until both are done (ShellActivity.afterFirstUse).
     */
    fun finishFirstUse(webosRoot: File, bus: org.webosarchive.lunacy.card.Bus) {
        val prefs = File(webosRoot, "var/luna/preferences")
        val ranFirstUse = File(prefs, "ran-first-use")
        val profileMade = File(prefs, "first-use-profile-created")
        val configureAfter = !ranFirstUse.exists() || !profileMade.exists()
        runCatching { prefs.mkdirs(); ranFirstUse.createNewFile() }
        if (profileMade.exists()) { if (configureAfter) configurator.run(); return }
        bus.call("", "palm://com.palm.accountservices/createNovaAccount", "{\"createDefaultAccount\":true}", privateBus = true) { r ->
            Log.i(Lunacy.TAG, "firstuse-createDefaultAccount: $r")
        }
        // The service answers before the account is made, and writes the flag as it makes it.
        var waited = 0
        val check = object : Runnable {
            override fun run() {
                if (profileMade.exists()) { configurator.run(); return }
                waited += 500
                if (waited < 60_000) main.postDelayed(this, 500)
                else Log.w(Lunacy.TAG, "first use: no profile account after a minute; activities held back until the next start")
            }
        }
        main.postDelayed(check, 500)
    }

    // ---- the simulated network ----

    private fun toggleNetwork(appId: String) {
        val net = network ?: return
        val down = !net.simulatedDown
        net.simulateDown(down)
        events.add(event("network").put("online", !down))
        systemBanner(appId, if (down) "Network connection lost (simulated)" else "Network connection restored")
        Log.i(Lunacy.TAG, "network: simulated ${if (down) "down" else "up"}")
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

    /**
     * What a developer hands Luna DE to install (a drop on its window, the file chooser,
     * LUNADE_INSTALL): a package, installed as any package is, or an app's own folder (the one
     * holding appinfo.json), which is copied in under its id as `palm-package` and an install
     * would have put it. Dropping the folder again replaces the copy.
     */
    fun installPath(path: String) {
        val f = File(path)
        when {
            f.isFile -> install(f.path)
            f.isDirectory && File(f, "appinfo.json").isFile -> main.post { installFolder(f) }
            f.isDirectory -> systemBanner("", "${f.name} has no appinfo.json: not an app's folder")
            else -> systemBanner("", "Can't read $path")
        }
    }

    private fun installFolder(dir: File) {
        val info = runCatching { JSONObject(File(dir, "appinfo.json").readText()) }.getOrNull()
        val id = info?.optString("id").orEmpty()
        if (id.isEmpty() || !Regex("[A-Za-z0-9._-]+").matches(id) || ".." in id) { systemBanner("", "${dir.name}: its appinfo.json names no app id"); return }
        systemBanner("", "Installing ${info!!.optString("title", id)}")
        Thread({
            val error = runCatching {
                val dest = File(packages.root, "${org.webosarchive.lunacy.core.WebosPaths.APPS}/$id")
                val staging = File(packages.root, "${org.webosarchive.lunacy.core.WebosPaths.APPS}/.$id.incoming")
                staging.deleteRecursively()
                // Not what a developer's folder carries beside the app: its repository and its tools' folders.
                dir.copyRecursively(staging, overwrite = true)
                listOf(".git", "node_modules").forEach { File(staging, it).deleteRecursively() }
                dest.deleteRecursively()
                if (!staging.renameTo(dest)) throw java.io.IOException("can't move it into place")
            }.exceptionOrNull()
            main.post {
                if (error != null) { systemBanner("", "Couldn't install ${dir.name}: ${error.message}"); return@post }
                windowsOf(id).forEach { closeWindow(it) }
                appsChanged()
                systemBanner(id, "${registry.get(id)?.title ?: id} installed")
            }
        }, "install-folder").start()
    }

    /** The launcher's Remove, for an app that was installed (never a bundled one). */
    fun remove(appId: String) {
        val app = registry.get(appId)
        if (app == null || !app.userInstalled) { systemBanner("", "$appId isn't an installed app"); return }
        removeInstalled(app) { error -> systemBanner("", if (error == null) "${app.title} removed" else "Couldn't remove ${app.title}: $error") }
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
        jsServices?.reload()
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
 * Luna DE's webOS root as [org.webosarchive.lunacy.card.ScriptHost]: where JS services run, under
 * a Node of Android's major version ([node]), with Lunacy's host scripts copied beside the root.
 *
 * Package scripts aren't run yet (Docs/luna-de.md, open question 2: a script would run as the
 * developer on their own machine), and there is no luna-send for a service's child processes:
 * one that shells out to it gets an error. curl is Lunacy's curl.js, as on Android.
 */
class DesktopRoot(override val root: File, override val hostScripts: File, private val repo: File,
                  private val rom: org.webosarchive.lunacy.card.RomFiles) : org.webosarchive.lunacy.card.ScriptHost {
    override val runsScripts get() = false

    /**
     * LUNADE_NODE if it is set, else the Node 12 DesktopLuna/fetch-node.sh fetched, else whatever
     * `node` the machine has, with a warning: Palm's services were written for node 0.4, Lunacy's
     * host runs them on Node 12, and a newer Node isn't what Android runs.
     */
    override val node: File by lazy {
        val named = System.getenv("LUNADE_NODE")?.takeIf { it.isNotEmpty() }?.let { File(it) }
        val fetched = File(repo, "DesktopLuna/node/bin/node")
        when {
            named != null -> named
            fetched.isFile -> fetched
            else -> {
                val onPath = System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "node") }.firstOrNull { it.isFile && it.canExecute() }
                if (onPath != null) Log.w(Lunacy.TAG, "js services: no Node 12 (run DesktopLuna/fetch-node.sh); using ${onPath.path}, which isn't the version Android runs")
                onPath ?: fetched
            }
        }
    }

    private var prepared = false

    /** The host scripts copied out, and curl in the root's /usr/bin, once a run. */
    @Synchronized override fun prepare() {
        if (prepared) return
        hostScripts.deleteRecursively()
        rom.copyAssets("lunacy/services", hostScripts)
        for (name in listOf("usr/bin/curl", "usr/bin/curl11")) {
            File(root, name).apply {
                parentFile?.mkdirs(); delete()
                writeText("#!/bin/sh\nexec \"${node.path}\" \"${File(hostScripts, "curl.js").path}\" \"\$@\"\n")
                setExecutable(true, false)
            }
        }
        prepared = true
    }

    override fun startBus() {}

    /** A webOS path as the file it is under the root; anything else is left as it is. */
    override fun mapPaths(s: String): String =
        if (ROOTS.any { s == "/$it" || s.startsWith("/$it/") }) File(root, s.trimStart('/')).path else s

    override fun environment(caller: String): Map<String, String> = mapOf(
        "PATH" to listOf("usr/sbin", "usr/bin", "sbin", "bin").joinToString(File.pathSeparator) { File(root, it).path } +
            File.pathSeparator + System.getenv("PATH").orEmpty(),
        "HOME" to File(root, "home/root").path,
        "TMPDIR" to File(root, "tmp").path,
        "LUNACY_WEBOS_ROOT" to root.path,
        "LUNACY_WEBOS_DIRS" to ROOTS.joinToString("|"),
        // The root has no /bin/sh of its own here: a service's shell commands run in the machine's.
        "LUNACY_SHELL" to "/bin/sh",
    )

    private companion object {
        /** The top folders whose absolute paths are webOS's (WebosRoot.ROOTS on Android). */
        val ROOTS = listOf("usr", "media", "bin", "sbin", "var", "etc", "tmp", "home", "opt", "lib")
    }
}
