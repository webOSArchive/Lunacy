package org.webosarchive.lunacy.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppFiles
import org.webosarchive.lunacy.card.AppRegistry
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.LauncherPages
import org.webosarchive.lunacy.core.FolderAssets
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import org.webosarchive.lunacy.core.Platform
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Luna DE's host: LunaCore on the desktop. It serves every app origin through AppServer, runs
 * the bus, and answers the page's native interface (window.LunacyNative) for DesktopLuna's
 * Electron shell, over HTTP on the loopback interface. Electron's main process is its only
 * client: it routes the app origins' requests here, naming the app from the request's origin
 * (rule 10). See Docs/luna-de.md.
 *
 *   luna-de-host --repo <Lunacy checkout> --data <folder> [--port <n>] [--assets <zip>]
 *
 * --assets: the asset tree as one zip (a packaged Luna DE's, made by DesktopLuna/package.js), read
 * in place as Android reads the APK's; without it, the checkout's own folders.
 *
 * Prints "LUNA-DE-HOST <port>" on standard output once it listens.
 */
fun main(args: Array<String>) {
    val opt = args.toList().windowed(2, 2, partialWindows = false).associate { it[0].removePrefix("--") to it[1] }
    val repo = File(opt["repo"] ?: error("--repo <Lunacy checkout> is required")).absoluteFile
    val data = File(opt["data"] ?: File(System.getProperty("user.home"), ".luna-de").path).absoluteFile

    Log.sink = Log.Sink { p, tag, msg, tr ->
        System.err.println("${"??VDIWE".getOrElse(p) { '?' }}/$tag: $msg")
        tr?.printStackTrace()
    }
    val main = DesktopScheduler()
    Platform.scheduler = main
    Platform.images = DesktopImages
    Platform.sql = org.webosarchive.lunacy.core.JdbcSql
    Platform.collation = DesktopCollation
    Platform.links = DesktopLinks
    DesktopLinks.record = File(data, "links.json")

    // The same trees Gradle packages into the APK (AndroidLuna/build.gradle.kts): the assets,
    // what fetch-assets.sh laid down, and CoreApps as apps/.
    // Without the apps that are Android's side of Lunacy: Luna DE is a desktop environment that
    // reuses the Android work, not an Android one (codepoet, 2026-11-16).
    val bundled: org.webosarchive.lunacy.core.Assets = opt["assets"]?.let { org.webosarchive.lunacy.core.ZipAssets(File(it)) } ?: FolderAssets(listOf(
        "" to File(repo, "AndroidLuna/src/main/assets"),
        "" to File(repo, "AndroidLuna/local-assets"),
        "apps/" to File(repo, "CoreApps"),
    ))
    Log.i(Lunacy.TAG, "assets: " + (opt["assets"] ?: "the checkout's folders"))
    val assets = org.webosarchive.lunacy.core.Without(bundled, ANDROID_ONLY_APPS, ANDROID_ONLY_ROM)
    // Native requests (the network shim's): the bundled CA certificates, and the host's own cookie
    // jar for now (Docs/luna-de.md: the Electron session's to come).
    org.webosarchive.lunacy.card.Http.init(assets, org.webosarchive.lunacy.core.MemoryCookies())
    val installed = File(data, "cryptofs/apps").apply { mkdirs() }
    val webosRoot = File(data, "webos").apply { mkdirs() }
    val files = AppFiles(assets, installed, webosRoot)
    val registry = AppRegistry(files)
    val server = AppServer(assets, files, webosRoot, File(data, "framework-art"))
    val bus = main.call { Bus() }

    // The webOS root, laid down as on Android (RomFiles): the ROM's /etc/palm and /usr/palm, the
    // folders every TouchPad has, and /media/cryptofs/apps as a link to the installed packages.
    val rom = org.webosarchive.lunacy.card.RomFiles(assets, webosRoot)
    val romStamp = File(webosRoot, ".luna-de-rom")
    val romNow = runCatching { assets.open("rootfs.index").use { it.readBytes() }.contentHashCode().toString() }.getOrDefault("")
    if (!romStamp.isFile || romStamp.readText() != romNow) { rom.syncRom(); romStamp.writeText(romNow) }
    rom.makeFolders(installed)
    rom.writeBuildInfo("Luna DE", "0")

    // db8 and tempdb, with every app's, service's and the system's kinds (Configurator), as the
    // Android shell sets them up (ShellActivity.registerServices).
    val db8 = org.webosarchive.lunacy.card.Db8("com.palm.db", File(data, "db8.sqlite"), debuggable = true)
    val tempdb = org.webosarchive.lunacy.card.Db8("com.palm.tempdb", null)
    // The system service: the time and zones, and preferences, kept in the data folder. What
    // nobody has set is the desktop's own (its locale, a 12-hour clock) on a TouchPad.
    val systemService = org.webosarchive.lunacy.card.SystemService(webosRoot, File(data, "systemservice.json"))
    // The application manager: launch and open, the content handlers, the app and launch point
    // lists, and installing packages (appinstaller, appInstallService), over Luna DE's shell.
    val desktopRoot = DesktopRoot(webosRoot, File(data, "jshost"), repo, rom)
    val packages = org.webosarchive.lunacy.card.Packages(installed, File(data, "cache/packages"), desktopRoot)
    val addedLaunchPoints = org.webosarchive.lunacy.card.LaunchPoints(webosRoot, files)
    val shell = DesktopShell(main, bus, registry, files, packages, addedLaunchPoints, data)
    val windows = shell.windows
    systemService.hostPreference = { key ->
        org.webosarchive.lunacy.card.SystemService.devicePreference(key, java.util.Locale.getDefault(), false,
            shell.device.carrierCode, DEFAULT_WALLPAPER)
    }
    main.call {
        db8.register(bus); tempdb.register(bus)
        systemService.register(bus)
        // Lunacy's own service, on its own name: the environment it really runs in. An app that
        // asks whether it is on Lunacy (App Catalog does, to install through Lunacy's installer
        // rather than look for Preware) finds it here as on Android. What that service does for
        // Android (permissions, Android's settings and notifications) isn't offered.
        bus.register(Lunacy.SERVICE, "system/getEnvironment") { _, _, reply ->
            val profile = shell.device
            reply(JSONObject().put("returnValue", true)
                .put("lunacy", JSONObject().put("version", "Luna DE").put("flavour", "desktop")
                    .put("reportsWebOS", profile.versionString).put("reportsAs", profile.modelName)
                    .put("enyo", "1.0").put("node", desktopRoot.node.path).put("apps", registry.apps.size)
                    .put("services", shell.jsServices?.names() ?: JSONArray()))
                .put("host", JSONObject().put("os", System.getProperty("os.name")).put("version", System.getProperty("os.version"))
                    .put("arch", System.getProperty("os.arch")).put("java", System.getProperty("java.version")))
                .put("webos", JSONObject().put("model", profile.modelName).put("version", profile.versionString))
                .toString())
        }
        // webOS's downloader, which apps hand every file fetch to (App Catalog's Pivot, a podcast's
        // episodes): it writes into the webOS tree.
        org.webosarchive.lunacy.card.DownloadManager(webosRoot).register(bus)
        val network = DesktopNetwork(main)
        shell.network = network
        org.webosarchive.lunacy.card.Http.offline = { network.simulatedDown }
        org.webosarchive.lunacy.card.ConnectionManager(network).register(bus)
        // After db8: the activity manager reads its persisted activities back as it registers.
        val power = DesktopPower(main)
        val activityManager = org.webosarchive.lunacy.card.ActivityManager(DesktopBattery, power).also { it.register(bus) }
        // A launch as Luna DE starts waits for what the boot starts (AppLifecycle.launchFromOutside).
        shell.lifecycle.bootSettled = { !activityManager.bootActivitiesPending() }
        shell.lifecycle.bootPending = { activityManager.bootActivitiesDescribed() }
        // powerd's keyed timers (a Mojo app's refresh alarm) and its power activities, kept across restarts.
        org.webosarchive.lunacy.card.PowerService(FileKeyedStore(File(data, "power-timeouts.json")), power).also { it.register(bus); it.enable() }
        shell.configurator = org.webosarchive.lunacy.card.Configurator(files, db8, tempdb, webosRoot, bus)
        shell.applicationManager = org.webosarchive.lunacy.card.ApplicationManager(registry,
            org.webosarchive.lunacy.card.ContentHandlers(registry, webosRoot), addedLaunchPoints, files, packages, desktopRoot, shell)
            .also { it.register(bus) }
        // What Just Type offers besides apps, from every app's appinfo.json, and the settings
        // Palm's Just Type app keeps for it (com.palm.universalsearch), as on Android.
        shell.universalSearch = org.webosarchive.lunacy.card.UniversalSearch(FileKeyedStore(File(data, "universalsearch.json"))) { registry.apps }
            .also { it.register(bus) }
        shell.configurator.run()
        // JS services: each package under /usr/palm/services, the ROM's and installed ones', in
        // its own Node process, started by its first call (JsServices). /var/log/messages is
        // where their logs and the apps' go, as on a device.
        org.webosarchive.lunacy.card.SysLog.start(webosRoot)
        desktopRoot.prepare()
        val js = org.webosarchive.lunacy.card.JsServices(bus, installed, desktopRoot)
        shell.jsServices = js
        js.reload()
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { js.stopAll() } })
        shell.finishFirstUse(webosRoot, bus)
    }
    Log.i(Lunacy.TAG, "Luna DE host: ${registry.apps.size} apps, data in $data")

    val http = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), opt["port"]?.toInt() ?: 0), 64)
    http.executor = Executors.newCachedThreadPool { r -> Thread(r, "luna-de-http").apply { isDaemon = true } }

    // An app origin's file, as AppServer answers it: GET /serve?u=<the page's URL>.
    http.createContext("/serve") { ex -> ex.use {
        val url = query(ex)["u"].orEmpty()
        val s = server.serve(url)
        if (s == null) { send(ex, 404, "text/plain", "not an app origin".toByteArray()); return@use }
        val type = if (s.encoding != null && (s.mimeType.startsWith("text/") || s.mimeType.endsWith("javascript") || s.mimeType.endsWith("json")))
            "${s.mimeType}; charset=${s.encoding}" else s.mimeType
        s.responseHeaders.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
        val body = s.data?.use { it.readBytes() } ?: ByteArray(0)
        send(ex, s.statusCode, type, body)
    } }

    // The launcher's apps: what the Android launcher draws (AppRegistry.launchPoints).
    http.createContext("/apps") { ex -> ex.use {
        val list = JSONArray()
        registry.launchPoints.sortedBy { it.title.lowercase() }.forEach { a -> list.put(entryOf(a)) }
        send(ex, 200, "application/json; charset=utf-8", list.toString().toByteArray())
    } }

    // The shell's launcher and quick-launch bar, as their owner has arranged them (LunaCore's
    // LauncherLayout, kept in launcher.json): the tabs, each tab's icons and groups in their
    // order, and the bar's apps; before any arranging, the default layout, then each other app
    // on its category's or keywords' page, alphabetically. No "android" tab: on Android it
    // holds Android's own apps (shell/AndroidApps).
    val launcherPages = LauncherPages(assets)
    val launcher = org.webosarchive.lunacy.card.LauncherLayout(launcherPages, FileKeyedStore(File(data, "launcher.json")), setOf("favorites"))
    http.createContext("/shell") { ex -> ex.use {
        val apps = registry.launchPoints
        val phone = shell.device == org.webosarchive.lunacy.card.DeviceProfile.PRE3
        val out = JSONObject()
            .put("pages", JSONArray(launcher.tabs(apps).map { t -> JSONObject().put("designator", t.designator).put("title", t.name)
                .put("permanent", t.permanent).put("apps", org.webosarchive.lunacy.card.LauncherLayout.cells(t.tiles, ::entryOf)) }))
            .put("dock", JSONArray(launcher.dock(apps, phone).map(::entryOf)))
            .put("limits", JSONObject().put("tabs", org.webosarchive.lunacy.card.LauncherLayout.MAX_TABS).put("name", org.webosarchive.lunacy.card.LauncherLayout.MAX_NAME)
                .put("groupName", org.webosarchive.lunacy.card.LauncherLayout.GROUP_NAME))
            // What every app's page says it is: the TouchPad's browser, as on Android (AppWindow).
            .put("userAgent", shell.device.userAgent)
            // The device Luna DE is being, for Electron to size and scale its window by.
            .put("device", JSONObject().put("id", if (shell.filling) "screen" else if (shell.device == org.webosarchive.lunacy.card.DeviceProfile.PRE3) "pre3" else "touchpad")
                .put("name", if (shell.filling) "TouchPad at the window's size" else shell.device.deviceName).put("width", shell.screenWidth).put("height", shell.screenHeight)
                .put("scale", shell.scale).put("orientation", shell.orientation)
                .put("virtualKeyboard", shell.device == org.webosarchive.lunacy.card.DeviceProfile.TOUCHPAD))
            .put("phoneZoom", shell.phoneZoom())
            .put("online", shell.online)
        send(ex, 200, "application/json; charset=utf-8", out.toString().toByteArray())
    } }

    // Text Assist for the on-screen keyboard: what a finished word becomes, and a word put back.
    val textAssist = DesktopTextAssist(File(repo, "LunaKeyboard/src/main/assets/assist/dictionary.txt")).also { it.start() }
    http.createContext("/assist") { ex -> ex.use {
        val q = query(ex)
        q["learn"]?.let { textAssist.learn(it) }
        val out = q["w"]?.let { textAssist.correct(it) } ?: JSONObject()
        send(ex, 200, "application/json; charset=utf-8", out.toString().toByteArray())
    } }

    // Just Type: what a text finds (with content=1, the apps' content searches too), and a choice made.
    val justType by lazy { DesktopJustType(main, bus, registry, shell.universalSearch!!, ::entryOf) }
    http.createContext("/justtype") { ex -> ex.use {
        val q = query(ex)
        send(ex, 200, "application/json; charset=utf-8", justType.find(q["q"].orEmpty(), q["content"] == "1").toString().toByteArray())
    } }
    http.createContext("/justtype-go") { ex -> ex.use {
        val q = query(ex)
        justType.go(q["kind"].orEmpty(), q["id"].orEmpty(), q["q"].orEmpty(), q["i"]?.toIntOrNull() ?: -1)
        send(ex, 204, "text/plain", ByteArray(0))
    } }

    // The launcher as its owner has just left it: {"tabs": [...]} in LauncherLayout's saved
    // shape (a cell an app's id, or a group of ids), and {"dock": [ids]} for the bar.
    http.createContext("/launcher") { ex -> ex.use {
        val body = runCatching { JSONObject(ex.requestBody.readBytes().toString(Charsets.UTF_8)) }.getOrDefault(JSONObject())
        body.optJSONArray("tabs")?.let { launcher.saveTabs(it) }
        body.optJSONArray("dock")?.let { launcher.saveDock(it, shell.device == org.webosarchive.lunacy.card.DeviceProfile.PRE3) }
        send(ex, 204, "text/plain", ByteArray(0))
    } }

    http.createContext("/icon") { ex -> ex.use {
        val bytes = registry.get(query(ex)["id"].orEmpty())?.openIcon()?.use { it.readBytes() }
        if (bytes == null) send(ex, 404, "text/plain", ByteArray(0)) else send(ex, 200, "image/png", bytes)
    } }

    // The icon for an app's loading card: its own `splashicon`, else its launcher icon
    // (Luna.splashIcon; the shell page sizes it as LunaCE's CardLoading does).
    http.createContext("/splash") { ex -> ex.use {
        val app = registry.get(query(ex)["id"].orEmpty())
        val bytes = (app?.openSplashIcon() ?: app?.openIcon())?.use { it.readBytes() }
        if (bytes == null) send(ex, 404, "text/plain", ByteArray(0)) else send(ex, 200, "image/png", bytes)
    } }

    // The page's native interface: POST /native?app=<from the origin>&w=<page>&m=<method>, the
    // arguments as a JSON array; the answer is the method's value as JSON.
    http.createContext("/native") { ex -> ex.use {
        val q = query(ex)
        val app = q["app"].orEmpty()
        // A page's first call makes it its window's page: the window is the one Electron says the
        // page is in (/page, never the page's own word), which this call waits a moment for.
        val w = windows[q["w"].orEmpty()] ?: windows.computeIfAbsent(q["w"].orEmpty()) { id ->
            val viewId = shell.viewOfPage(id)
            DesktopWindow(id, app, bus, main, shell, shell.views[viewId]?.pid).also { n ->
                shell.pendingTypes.remove(id)?.let { n.type = it.ifEmpty { null } }
                if (viewId.isNotEmpty()) main.post { shell.bindPage(viewId, n) }
            }
        }
        if (w.appId != app) { send(ex, 403, "text/plain", "another app's page".toByteArray()); return@use }
        val args = runCatching { JSONArray(ex.requestBody.readBytes().toString(Charsets.UTF_8)) }.getOrDefault(JSONArray())
        send(ex, 200, "application/json; charset=utf-8", w.invoke(q["m"].orEmpty(), args).toByteArray())
    } }

    // What the page is to run (bus replies): GET /poll?app=&w=, held until there is some.
    http.createContext("/poll") { ex -> ex.use {
        val q = query(ex)
        val w = windows[q["w"].orEmpty()]?.takeIf { it.appId == q["app"] }
        val scripts = w?.take(25_000) ?: run { Thread.sleep(1000); emptyList() }
        send(ex, 200, "application/json; charset=utf-8", JSONArray(scripts).toString().toByteArray())
    } }

    // A page has gone (Electron closed or reloaded it): its subscriptions end.
    http.createContext("/gone") { ex -> ex.use {
        shell.gone(query(ex)["w"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
    } }

    // A window a page opened (window.open), which Electron has made a view for; a view that has
    // gone; and a card or dashboard the person closed. The lifecycle decides what follows.
    http.createContext("/opened") { ex -> ex.use {
        val q = query(ex)
        shell.opened(q["view"].orEmpty(), q["parent"].orEmpty(), q["app"].orEmpty(), q["kind"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
    } }
    // Which window a page is in, from Electron, as the page starts.
    http.createContext("/page") { ex -> ex.use {
        val q = query(ex)
        shell.pageIn(q["w"].orEmpty(), q["view"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
    } }
    http.createContext("/view-gone") { ex -> ex.use {
        shell.viewGone(query(ex)["view"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
    } }
    http.createContext("/close") { ex -> ex.use {
        shell.closedByUser(query(ex)["view"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
    } }

    // The shell launches an app (the launcher, the quick-launch bar, LUNA_DE_OPEN): through the
    // same path as applicationManager/launch, so a running app is relaunched, not started twice.
    http.createContext("/launch") { ex -> ex.use {
        val q = query(ex)
        val params = q["params"]?.takeIf { it.isNotEmpty() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        shell.launchFromShell(q["id"].orEmpty(), params)
        send(ex, 204, "text/plain", ByteArray(0))
    } }

    // A package or an app's folder to install, and an installed app to remove (DesktopShell).
    http.createContext("/install") { ex -> ex.use {
        shell.installPath(query(ex)["path"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
    } }
    http.createContext("/remove") { ex -> ex.use {
        val id = query(ex)["id"].orEmpty()
        main.post { shell.remove(id) }
        send(ex, 204, "text/plain", ByteArray(0))
    } }

    // The on-screen keyboard's size, kept where webOS kept it: systemservice's
    // x_palm_virtualkeyboard_settings, a JSON string whose "keyboard size" is -2 (XS), -1 (S),
    // 0 (M, the default) or 1 (L), as read off the reference TouchPad (2026-11-16).
    http.createContext("/keyboard-size") { ex -> ex.use {
        val key = "x_palm_virtualkeyboard_settings"
        fun settings() = runCatching { JSONObject(systemService.get(key) as? String ?: "{}") }.getOrDefault(JSONObject())
        query(ex)["v"]?.toIntOrNull()?.let { size ->
            val s = settings().put("keyboard size", size.coerceIn(-2, 1))
            if (!s.has("layout")) s.put("layout", "English").put("language", "en_us")
            main.call { bus.call("com.palm.systemui", "palm://com.palm.systemservice/setPreferences", JSONObject().put(key, s.toString()).toString(), privateBus = true) { } }
        }
        send(ex, 200, "application/json", JSONObject().put("size", settings().optInt("keyboard size", 0)).toString().toByteArray())
    } }

    // What Electron tells a page (its type, its focus, the back gesture), and the device turning.
    http.createContext("/window") { ex -> ex.use {
        val q = query(ex)
        shell.tell(q["w"].orEmpty(), q["do"].orEmpty(), q["v"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
    } }
    // Luna DE becomes another device (Electron has closed every app first).
    http.createContext("/device") { ex -> ex.use {
        val q = query(ex)
        shell.setDevice(q["v"].orEmpty(), q["w"]?.toIntOrNull() ?: 0, q["h"]?.toIntOrNull() ?: 0)
        send(ex, 204, "text/plain", ByteArray(0))
    } }
    // As this screen, the window changed size (a page's px, as it is held).
    http.createContext("/screen") { ex -> ex.use {
        val q = query(ex)
        shell.setScreen(q["w"]?.toIntOrNull() ?: 0, q["h"]?.toIntOrNull() ?: 0)
        send(ex, 204, "text/plain", ByteArray(0))
    } }
    // An app's Phone Zoom switch, from the bezel: v=1, v=0, or nothing for its default.
    http.createContext("/phone-zoom") { ex -> ex.use {
        val q = query(ex)
        q["app"]?.takeIf { it.isNotEmpty() }?.let { shell.setPhoneZoom(it, when (q["v"]) { "1" -> true; "0" -> false; else -> null }) }
        send(ex, 200, "application/json", shell.phoneZoom().toString().toByteArray())
    } }
    // The system menu's Wi-Fi: the simulated network on (v=1) or off (v=0).
    http.createContext("/network") { ex -> ex.use {
        query(ex)["v"]?.let { shell.setNetwork(it == "1") }
        send(ex, 200, "application/json", JSONObject().put("online", shell.online).toString().toByteArray())
    } }
    http.createContext("/orientation") { ex -> ex.use {
        shell.turn(query(ex)["v"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
    } }

    // What Electron is to do next (DesktopShell's events): GET /events, held until there is some.
    http.createContext("/events") { ex -> ex.use {
        send(ex, 200, "application/json; charset=utf-8", JSONArray(shell.take(25_000)).toString().toByteArray())
    } }

    http.start()
    // Started by Luna DE's window (--with-parent 1): the host's standard input is a pipe only that
    // process holds, so its end is the window's, however it went (quit, killed, crashed), and
    // the host goes too, taking its services with it (the shutdown hook above). Without this a
    // killed Luna DE left its host and their Node running.
    if (opt["with-parent"] == "1") {
        Thread({
            try { while (System.`in`.read() >= 0) { /* nothing is ever sent */ } } catch (e: Exception) { }
            kotlin.system.exitProcess(0)
        }, "parent-watch").apply { isDaemon = true }.start()
    }
    println("LUNA-DE-HOST ${http.address.port}")
    System.out.flush()
}

/**
 * An app as the shell launches it: its page's URL, and whether it has a window of its own or
 * starts headless and opens its cards itself (appinfo.json's noWindow, as Email and Calendar do).
 */
/**
 * Bundled apps Luna DE leaves out, each Android's side of Lunacy: the Android account and its
 * template, Android's notifications as dashboards, First Use and the mail sign-in page (both made
 * for Android), Lunacy's own APK updates, the screen saver's settings, and the tiles that only
 * open one of Android's settings screens. Wi-Fi stays: here it turns a simulated network off and
 * on (DesktopShell.launch).
 */
private val ANDROID_ONLY_APPS = setOf(
    "org.webosarchive.lunacy.androidaccount", "org.webosarchive.lunacy.notifications", "org.webosarchive.lunacy.firstuse",
    "org.webosarchive.lunacy.signin", "com.palm.app.updates", "com.palm.app.exhibitionpreferences",
    "com.palm.app.bluetooth", "com.palm.app.languagepicker", "com.palm.app.dateandtime",
)

/**
 * And of the ROM: the account templates that need Android to sign in or to sync (the Android
 * account; Google and Microsoft through Lunacy's sign-in page and OAuth service), and Exchange,
 * whose transport is one of the TouchPad's own programs left out below: offered, it failed at
 * its first call (codepoet, 2026-11-17: hidden for now).
 */
private val ANDROID_ONLY_ROM = listOf("org.webosarchive.lunacy.androidaccount", "org.webosarchive.lunacy.google",
    "org.webosarchive.lunacy.microsoft", "org.webosarchive.lunacy.microsoft365", "com.palm.eas").map { "usr/palm/public/accounts/$it" } +
    // The TouchPad's own programs and their libraries (the mail and messaging engines), which run
    // on Android under the PDK runtime and nowhere on a desktop: a package leaves them out, and
    // assets that have them (the development machine's) are read as if they hadn't.
    listOf("usr/bin", "usr/lib")

/** The wallpaper Lunacy shows until one is picked (Luna.DEFAULT_WALLPAPER on Android). */
private const val DEFAULT_WALLPAPER = "22.jpg"

private fun entryOf(a: org.webosarchive.lunacy.card.AppInfo) = JSONObject().put("id", a.id).put("title", a.title).put("version", a.version)
    .put("url", AppServer.appUrl(a.id, a.main, a.dir)).put("noWindow", a.noWindow)
    .put("icon", "/icon?id=" + java.net.URLEncoder.encode(a.id, "UTF-8"))
    .put("splash", "/splash?id=" + java.net.URLEncoder.encode(a.id, "UTF-8")).put("splashOwn", a.splashIcon.isNotEmpty())
    // A tile that is one of Luna DE's switches (Wi-Fi: the simulated network), not an app with a card.
    .put("switch", a.androidSettings == "wifi")
    .put("removable", a.userInstalled)

private fun query(ex: HttpExchange): Map<String, String> =
    ex.requestURI.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
        val i = it.indexOf('=')
        if (i < 0) URLDecoder.decode(it, "UTF-8") to "" else URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }

private fun send(ex: HttpExchange, status: Int, type: String, body: ByteArray) {
    ex.responseHeaders.set("Content-Type", type)
    ex.responseHeaders.set("Cache-Control", "no-store")
    if (status == 204 || status == 304) { ex.sendResponseHeaders(status, -1); return }
    ex.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
    if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
}

private inline fun HttpExchange.use(block: () -> Unit) {
    try { block() } catch (e: Exception) {
        Log.e(Lunacy.TAG, "host: ${requestURI}", e)
        runCatching { send(this, 500, "text/plain", e.toString().toByteArray()) }
    } finally { close() }
}
