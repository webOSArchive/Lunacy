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

    // The same trees Gradle packages into the APK (AndroidLuna/build.gradle.kts): the assets,
    // what fetch-assets.sh laid down, and CoreApps as apps/.
    val assets = FolderAssets(listOf(
        "" to File(repo, "AndroidLuna/src/main/assets"),
        "" to File(repo, "AndroidLuna/local-assets"),
        "apps/" to File(repo, "CoreApps"),
    ))
    // Native requests (the network shim's): the bundled CA certificates, and the host's own cookie
    // jar for now (Docs/luna-de.md: the Electron session's to come).
    org.webosarchive.lunacy.card.Http.init(assets, org.webosarchive.lunacy.core.MemoryCookies())
    val installed = File(data, "cryptofs/apps").apply { mkdirs() }
    val webosRoot = File(data, "webos").apply { mkdirs() }
    val files = AppFiles(assets, installed)
    val registry = AppRegistry(files)
    val server = AppServer(assets, files, webosRoot, File(data, "framework-art"))
    val bus = main.call { Bus() }
    val windows = ConcurrentHashMap<String, DesktopWindow>()
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
        val pages = launcherPages.initialPages.map { (d, name) -> d to (name to ArrayList<org.webosarchive.lunacy.card.AppInfo>()) }
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
        send(ex, 200, "application/json; charset=utf-8", out.toString().toByteArray())
    } }

    http.createContext("/icon") { ex -> ex.use {
        val bytes = registry.get(query(ex)["id"].orEmpty())?.openIcon()?.use { it.readBytes() }
        if (bytes == null) send(ex, 404, "text/plain", ByteArray(0)) else send(ex, 200, "image/png", bytes)
    } }

    // The page's native interface: POST /native?app=<from the origin>&w=<page>&m=<method>, the
    // arguments as a JSON array; the answer is the method's value as JSON.
    http.createContext("/native") { ex -> ex.use {
        val q = query(ex)
        val app = q["app"].orEmpty()
        val w = windows.computeIfAbsent(q["w"].orEmpty()) { DesktopWindow(it, app, bus, main) }
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
        windows.remove(query(ex)["w"].orEmpty())?.close()
        send(ex, 204, "text/plain", ByteArray(0))
    } }

    http.start()
    println("LUNADE-HOST ${http.address.port}")
    System.out.flush()
}

/**
 * An app as the shell launches it: its page's URL, and whether it has a window of its own or
 * starts headless and opens its cards itself (appinfo.json's noWindow, as Email and Calendar do).
 */
private fun entryOf(a: org.webosarchive.lunacy.card.AppInfo) = JSONObject().put("id", a.id).put("title", a.title)
    .put("url", AppServer.appUrl(a.id, a.main, a.dir)).put("noWindow", a.noWindow)
    .put("icon", "/icon?id=" + java.net.URLEncoder.encode(a.id, "UTF-8"))

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
