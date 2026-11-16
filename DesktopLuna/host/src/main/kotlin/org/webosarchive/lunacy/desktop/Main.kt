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
 *   lunade-host --repo <Lunacy checkout> --data <folder> [--port <n>]
 *
 * Prints "LUNADE-HOST <port>" on standard output once it listens.
 */
fun main(args: Array<String>) {
    val opt = args.toList().windowed(2, 2, partialWindows = false).associate { it[0].removePrefix("--") to it[1] }
    val repo = File(opt["repo"] ?: error("--repo <Lunacy checkout> is required")).absoluteFile
    val data = File(opt["data"] ?: File(System.getProperty("user.home"), ".lunade").path).absoluteFile

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

    // The same trees Gradle packages into the APK (AndroidLuna/build.gradle.kts): the assets,
    // what fetch-assets.sh laid down, and CoreApps as apps/.
    // Without the apps that are Android's side of Lunacy: Luna DE is a desktop environment that
    // reuses the Android work, not an Android one (codepoet, 2026-11-16).
    val assets = org.webosarchive.lunacy.core.Without(FolderAssets(listOf(
        "" to File(repo, "AndroidLuna/src/main/assets"),
        "" to File(repo, "AndroidLuna/local-assets"),
        "apps/" to File(repo, "CoreApps"),
    )), ANDROID_ONLY_APPS, ANDROID_ONLY_ROM)
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
    val romStamp = File(webosRoot, ".lunade-rom")
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
    val shell = DesktopShell(main, registry, files, packages, addedLaunchPoints, data)
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
        org.webosarchive.lunacy.card.ActivityManager(DesktopBattery, power).register(bus)
        // powerd's keyed timers (a Mojo app's refresh alarm) and its power activities, kept across restarts.
        org.webosarchive.lunacy.card.PowerService(FileKeyedStore(File(data, "power-timeouts.json")), power).also { it.register(bus); it.enable() }
        shell.configurator = org.webosarchive.lunacy.card.Configurator(files, db8, tempdb, webosRoot, bus)
        shell.applicationManager = org.webosarchive.lunacy.card.ApplicationManager(registry,
            org.webosarchive.lunacy.card.ContentHandlers(registry, webosRoot), addedLaunchPoints, files, packages, desktopRoot, shell)
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
    http.executor = Executors.newCachedThreadPool { r -> Thread(r, "lunade-http").apply { isDaemon = true } }

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

    // The shell's launcher and quick-launch bar, placed by LunaCore's rules (LauncherPages), as
    // AndroidLuna's launcher places a fresh install's apps: the default layout first, then each
    // other app on its category's or keywords' page, alphabetically. The user's own tabs and
    // groups (Launcher.setApps on Android) come with editable tabs here.
    val launcherPages = LauncherPages(assets)
    http.createContext("/shell") { ex -> ex.use {
        val apps = registry.launchPoints
        val byId = apps.associateBy { it.id }
        fun entry(a: org.webosarchive.lunacy.card.AppInfo) = entryOf(a)
        // No "android" tab: on Android it holds Android's own apps (shell/AndroidApps).
        val pages = launcherPages.initialPages.filter { (_, name) -> name != "android" }.map { (d, name) -> d to (name to ArrayList<org.webosarchive.lunacy.card.AppInfo>()) }
        val placed = HashSet<String>()
        for ((designator, ids) in launcherPages.defaultLayout) {
            val page = pages.firstOrNull { it.first == designator }?.second?.second ?: continue
            ids.mapNotNull { byId[it] }.filter { placed.add(it.id) }.forEach { page += it }
        }
        for (a in apps.filter { it.id !in placed }.sortedBy { it.title.lowercase() }) {
            val d = launcherPages.designatorFor(a)
            (pages.firstOrNull { it.first == d } ?: pages.first()).second.second += a
        }
        val out = JSONObject()
            .put("pages", JSONArray(pages.map { (d, p) -> JSONObject().put("designator", d).put("title", p.first).put("apps", JSONArray(p.second.map(::entry))) }))
            .put("dock", JSONArray(launcherPages.quickLaunch().mapNotNull { byId[it] }.map(::entry)))
            // What every app's page says it is: the TouchPad's browser, as on Android (AppWindow).
            .put("userAgent", shell.device.userAgent)
            // The device Luna DE is being, for Electron to size and scale its window by.
            .put("device", JSONObject().put("id", if (shell.device == org.webosarchive.lunacy.card.DeviceProfile.PRE3) "pre3" else "touchpad")
                .put("name", shell.device.deviceName).put("width", shell.screenWidth).put("height", shell.screenHeight)
                .put("scale", shell.scale).put("orientation", shell.orientation)
                .put("virtualKeyboard", shell.device == org.webosarchive.lunacy.card.DeviceProfile.TOUCHPAD))
        send(ex, 200, "application/json; charset=utf-8", out.toString().toByteArray())
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
        val w = windows.computeIfAbsent(q["w"].orEmpty()) { id ->
            DesktopWindow(id, app, bus, main, shell).also { n -> shell.pendingTypes.remove(id)?.let { n.type = it.ifEmpty { null } } }
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

    // The shell launches an app (the launcher, the quick-launch bar, LUNADE_OPEN): through the
    // same path as applicationManager/launch, so a running app is relaunched, not started twice.
    http.createContext("/launch") { ex -> ex.use {
        val q = query(ex)
        val params = q["params"]?.takeIf { it.isNotEmpty() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        shell.launch(q["id"].orEmpty(), params)
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
        shell.setDevice(query(ex)["v"].orEmpty())
        send(ex, 204, "text/plain", ByteArray(0))
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
    println("LUNADE-HOST ${http.address.port}")
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
 * account; Google and Microsoft through Lunacy's sign-in page and OAuth service).
 */
private val ANDROID_ONLY_ROM = listOf("org.webosarchive.lunacy.androidaccount", "org.webosarchive.lunacy.google",
    "org.webosarchive.lunacy.microsoft", "org.webosarchive.lunacy.microsoft365").map { "usr/palm/public/accounts/$it" }

/** The wallpaper Lunacy shows until one is picked (Luna.DEFAULT_WALLPAPER on Android). */
private const val DEFAULT_WALLPAPER = "22.jpg"

private fun entryOf(a: org.webosarchive.lunacy.card.AppInfo) = JSONObject().put("id", a.id).put("title", a.title)
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
