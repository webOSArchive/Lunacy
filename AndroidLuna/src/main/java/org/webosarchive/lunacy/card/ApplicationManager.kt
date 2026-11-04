package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONObject

/**
 * webOS's application manager, `com.palm.applicationManager`, as LunaSysMgr answered it:
 * launch and open (a web page to the Web app, a URL to the app the system's
 * command-resource-handlers send it to, a file to the app that handles its type, an Android
 * link to Android), the content handlers' questions, the package and launch-point lists and
 * their changes, rescan, and the private bus's running and close. What it needs of the shell -
 * starting an app, installing a package, the windows that are up - comes through [Shell].
 */
class ApplicationManager(
    private val context: Context,
    private val registry: AppRegistry,
    private val handlers: ContentHandlers,
    private val addedLaunchPoints: LaunchPoints,
    private val files: AppFiles,
    private val packages: Packages,
    private val jsServices: JsServices,
    private val webos: WebosRoot,
    private val shell: Shell,
) {
    /** What the application manager asks of the shell. */
    interface Shell {
        /** Starts [appId], or brings it forward; see ShellActivity.launch. */
        fun launch(appId: String, params: JSONObject? = null, placement: Placement = Placement.CARD, launchedBy: String? = null)
        /** Installs a package from a URL or path; see ShellActivity.install. */
        fun install(source: String, requester: String = "", onProgress: (Int) -> Unit = {},
                    banners: Boolean = true, onDone: (Packages.Result) -> Unit = {})
        /** Whether this build can run a PDK app. */
        val pdkAvailable: Boolean
        /** Running apps: app id to its windows, root first. */
        val running: Map<String, List<AppWindow>>
        fun closeWindow(window: AppWindow)
        /** The installed apps changed on disk: rescan, reconfigure, and tell the launcher. */
        fun appsChanged()
        /** The launcher and the dock after an added launch point came, went or changed. */
        fun launchPointsChanged()
        fun showDock()
        /** The icons drawn for an app or launch point are stale. */
        fun forgetIcons(key: String)
        /** Removes an installed app's package, closing its windows first; [done] gets null or the error. */
        fun removeInstalled(app: AppInfo, done: (String?) -> Unit)
    }

    private lateinit var bus: Bus

    private val launchHandler = Bus.Handler { caller, p, reply ->
        val id = p.optString("id")
        // An activity's callback carries "$activity" beside the params, and the application
        // manager put it into the params the app gets (luna-sysmgr's
        // ApplicationManagerService): Calendar completes its boot activity with it.
        val params = p.optJSONObject("params").let { pb ->
            val act = p.opt("\$activity") ?: return@let pb
            (pb ?: JSONObject()).put("\$activity", act)
        }
        // A web page is the Web app's, as webOS's command-resource-handlers said (^https?: and
        // ^data: to com.palm.app.browser): a card of its own for each.
        if (id.isEmpty() && isWebUrl(p.optString("target")) && registry.get(BROWSER) != null) {
            shell.launch(BROWSER, JSONObject().put("target", p.optString("target")))
            reply(Bus.ok(mapOf("processId" to "success")))
            return@Handler
        }
        // A bare target an installed app claims in the system's command-resource-handlers - by
        // its redirects, or by its scheme's command (mailto: is Email's) - opened in that app,
        // as webOS's application manager did.
        (handlers.redirectFor(p.optString("target"))?.takeIf { registry.get(it) != null } ?: handlers.commandFor(p.optString("target")))
            ?.takeIf { id.isEmpty() }?.let { app ->
            shell.launch(app, JSONObject().put("target", p.optString("target")), launchedBy = caller)
            reply(Bus.ok(mapOf("processId" to "success")))
            return@Handler
        }
        // A file goes to the app that handles its type (ContentHandlers): a PDF to Adobe
        // Reader, a .doc to Quickoffice, with the target as it was given, as the reference
        // TouchPad launched them (2026-10-09). Whether the file is there is the app's affair.
        if (id.isEmpty() && p.optString("target").isNotEmpty() && !isWebUrl(p.optString("target"))) {
            val target = p.optString("target")
            handlers.forTarget(target)?.let { h ->
                shell.launch(h.appId, JSONObject().put("target", target), launchedBy = caller)
                reply(Bus.ok(mapOf("processId" to "success")))
                return@Handler
            }
            // An Android package goes to Android's installer (WebosLinks.androidPackage).
            WebosLinks.androidPackage(target, jsServices.root)?.let {
                reply(WebosLinks.open(context, it))
                return@Handler
            }
            if (target.startsWith("file:", true) || target.startsWith("/")) {
                reply(JSONObject().put("returnValue", false).put("errorText", "No handler for $target").toString())
                return@Handler
            }
        }
        // A link, an email, a phone number, a map: webOS's own apps owned these and Lunacy
        // doesn't have them, so Android's answer instead. See WebosLinks.
        val link = WebosLinks.intentFor(id, params, p.optString("target"))
        if (link != null && registry.get(id) == null) {
            reply(WebosLinks.open(context, link))
            return@Handler
        }
        // The App Museum installs through Preware (LuneOS's on LuneOS); Lunacy's package
        // manager answers for it. See Docs/architecture.md, "Package manager and App Catalog".
        if (id in INSTALLERS && registry.get(id) == null && params?.optString("type") == "install") {
            // Preware reads "file"; the handler chain the catalogs use also sends the
            // standard "target".
            val file = params.optString("file").ifEmpty { params.optString("target") }
            if (file.isEmpty()) reply(Bus.error("install: no file given"))
            else { shell.install(file, caller); reply(Bus.ok(mapOf("processId" to "success"))) }
        } else if (registry.get(id) == null) reply(Bus.error("Application not found: $id"))
        // A PDK app runs where the runtime is (Docs/pdk.md); elsewhere shell.launch() shows why not.
        else if (registry.get(id)?.isWeb == false) {
            shell.launch(id)
            reply(if (shell.pdkAvailable) Bus.ok(mapOf("processId" to "success"))
                else Bus.error("${registry.get(id)?.title} is a native app; this build of Lunacy can't run it"))
        }
        else {
            // A headless app a service launches (an activity's callback at boot: Calendar
            // and Clock re-arm themselves) gets no startup card: on webOS nothing showed
            // until the app opened a card window, and its own loading card came with that
            // window. Lunacy's placeholder stands in for the tap that launched an app.
            val unseen = registry.get(id)?.noWindow == true && bus.has(caller)
            shell.launch(id, params, if (unseen) Placement.NO_CARD else Placement.CARD, launchedBy = caller); reply(Bus.ok(mapOf("processId" to "success")))
        }
    }

    fun register(bus: Bus) {
        this.bus = bus
        bus.register(Bus.APPLICATION_MANAGER, "launch", launchHandler)
        bus.register(Bus.APPLICATION_MANAGER, "open", launchHandler)
        // What Enyo's CrossAppUI asks for before loading another app's UI. The reference
        // TouchPad answers {"returnValue":true,"appId":...,"basePath":"file://…/index.html"}.
        bus.register(Bus.APPLICATION_MANAGER, "getAppBasePath") { _, p, reply ->
            val app = registry.get(p.optString("appId"))
            reply(if (app == null) Bus.error("Application not found: ${p.optString("appId")}")
                  else Bus.ok(mapOf("appId" to app.id, "basePath" to app.filePath())))
        }
        // Package scripts ask for this after putting an app in /usr/palm/applications
        // themselves. The reference TouchPad answers {"returnValue":true}.
        bus.register(Bus.APPLICATION_MANAGER, "rescan") { _, _, reply ->
            shell.appsChanged()
            reply(Bus.ok())
        }
        bus.registerPrivate(Bus.APPLICATION_MANAGER, "launchPointChanges", wholeService = false, Bus.CallHandler { launchPointChanges(it) })
        // Launch points an app adds: the Web app's Add to Launcher, the PWA Installer's shortcuts.
        bus.register(Bus.APPLICATION_MANAGER, "addLaunchPoint") { _, p, reply -> reply(addLaunchPoint(p)) }
        bus.register(Bus.APPLICATION_MANAGER, "removeLaunchPoint") { _, p, reply ->
            val id = p.optString("launchPointId")
            // The TouchPad's errors here carry no errorCode, and a default launch point's id (not a
            // number) gets an empty errorText (measured 2026-11-04).
            fun fail(text: String) = JSONObject().put("returnValue", false).put("errorText", text).toString()
            reply(if (id.isEmpty()) fail("Must provide a launchPointId")
                  else removeLaunchPoint(id)?.let { fail(it) } ?: Bus.ok())
        }
        bus.register(Bus.APPLICATION_MANAGER, "updateLaunchPointIcon") { caller, p, reply -> reply(updateLaunchPointIcon(caller, p)) }
        bus.register(Bus.APPLICATION_MANAGER, "listAllHandlersForMime") { _, p, reply -> reply(handlersForMime(p)) }
        bus.register(Bus.APPLICATION_MANAGER, "getResourceInfo") { _, p, reply -> reply(resourceInfo(p)) }
        bus.register(Bus.APPLICATION_MANAGER, "getHandlerForExtension") { _, p, reply -> reply(handlers.handlerForExtension(p)) }
        bus.register(Bus.APPLICATION_MANAGER, "getHandlerForUrl") { _, p, reply -> reply(handlers.handlerForUrl(p)) }
        bus.registerPrivate(Bus.APPLICATION_MANAGER, "listPackages", wholeService = false, Bus.CallHandler { c -> c.reply(listPackages()) })
        // App Catalog's installer, over the same install path as Preware's (AppInstallService.kt).
        AppInstallService(object : AppInstallService.Installer {
            override fun install(url: String, requester: String, progress: (Int) -> Unit, done: (Packages.Result) -> Unit) =
                shell.install(url, requester, progress, onDone = done)
            override fun remove(appId: String, done: (String?) -> Unit) {
                val app = registry.get(appId)
                if (app == null || !app.userInstalled) return done("$appId isn't installed")
                shell.removeInstalled(app, done)
            }
            override fun isInstalled(appId: String) = registry.get(appId)?.userInstalled == true
        }).register(bus)
        // appinstaller's own installs and removals, which the SDK's palm-install makes.
        AppInstallService.AppInstaller({ context.filesDir.usableSpace },
            object : AppInstallService.PackageInstaller {
                override fun installFile(file: java.io.File, done: (Packages.Result) -> Unit) =
                    shell.install(file.path, banners = false, onDone = done)
                override fun removableVersion(id: String) = registry.get(id)?.takeIf { it.userInstalled }?.version
                override fun remove(id: String, done: (String?) -> Unit) {
                    val app = registry.get(id) ?: return done("$id isn't installed")
                    shell.removeInstalled(app, done)
                }
            }) { path -> java.io.File(webos.mapPaths(path)) }.register(bus)
        // Which windows are up, and closing one, as palm-launch -c and palm-run use them.
        // Measured on the reference TouchPad (private bus only): running answers
        // {"running":[{"id":…,"processid":"1030"},…],"returnValue":true}, one entry per window
        // (LunaSysMgr's own system UI windows among them, which Lunacy draws natively and so
        // has none of); close {"processId"} answers {"returnValue":true}, an unknown id too, and
        // with none {"returnValue":false,"errorText":"Must provide a valid processId to close"}.
        bus.registerPrivate(Bus.APPLICATION_MANAGER, "running", wholeService = false, Bus.CallHandler { c ->
            val list = org.json.JSONArray()
            for ((id, windows) in shell.running) for (w in windows) list.put(JSONObject().put("id", id).put("processid", w.pid.toString()))
            c.reply(JSONObject().put("running", list).put("returnValue", true).toString())
        })
        bus.registerPrivate(Bus.APPLICATION_MANAGER, "close", wholeService = false, Bus.CallHandler { c ->
            val pid = c.params.optString("processId")
            if (pid.isEmpty()) return@CallHandler c.reply(JSONObject().put("returnValue", false)
                .put("errorText", "Must provide a valid processId to close").toString())
            shell.running.values.flatten().firstOrNull { it.pid.toString() == pid }?.let { shell.closeWindow(it) }
            c.reply(Bus.ok())
        })
        bus.register(Bus.APPLICATION_MANAGER, "listApps") { _, _, reply ->
            reply(Bus.ok(mapOf("apps" to org.json.JSONArray(registry.apps.map { it.listEntry() }))))
        }
    }

    /**
     * applicationManager/listAllHandlersForMime, from the content handlers (ContentHandlers):
     * `{"subscribed":false,"mime":…,"returnValue":true,"resourceHandlers":{"activeHandler":{…}}}`
     * as the reference TouchPad gave it. Packages are answered for by the Preware Lunacy stands
     * in for (INSTALLERS) - codepoet's call: Lunacy won't run Preware, so it may answer as
     * Preware. A type nothing handles gets the device's "No handlers found", and no mime its
     * complaint.
     */
    private fun handlersForMime(p: JSONObject): String {
        val mime = p.optString("mime")
        if (mime.isEmpty() && p.optString("url").isEmpty())
            return JSONObject().put("subscribed", false).put("returnValue", false)
                .put("errorCode", "Must have either an url or a mime parameter").toString()
        val isIpk = mime == IPK_MIME || (mime.isEmpty() && p.optString("url").substringBefore('?').endsWith(".ipk", true))
        val j = JSONObject().put("subscribed", false).put("mime", mime.ifEmpty { IPK_MIME })
        if (!isIpk) {
            val active = handlers.activeHandler(mime)
                ?: return j.put("returnValue", false).put("errorCode", "No handlers found for $mime").toString()
            return j.put("returnValue", true).put("resourceHandlers", JSONObject().put("activeHandler", active)).toString()
        }
        return j.put("returnValue", true).put("resourceHandlers", JSONObject().put("activeHandler", JSONObject()
            .put("mime", IPK_MIME).put("extension", "ipk").put("appId", "org.webosinternals.preware")
            .put("streamable", true).put("index", 0).put("appName", "Preware"))).toString()
    }

    private fun isWebUrl(url: String) = url.startsWith("http://", true) || url.startsWith("https://", true) || url.startsWith("data:", true)

    /**
     * applicationManager/getResourceInfo: which app a URL's content goes to, and whether that
     * app streams it, from the content handlers. Measured on the reference TouchPad
     * (2026-10-04, 2026-10-09): `{"returnValue":true, "uri":…, "appIdByExtension":…,
     * "mimeByExtension":…, "canStream":…}`. Given a mime, the handler is the mime's and there is
     * no mimeByExtension; otherwise the extension's. A web URL nothing handles - a zip,
     * application/octet-stream - goes to the Web app, which downloads it; a file nothing
     * handles is "can't find a handler for the given uri".
     */
    private fun resourceInfo(p: JSONObject): String {
        val uri = p.optString("uri")
        if (uri.isEmpty()) return Bus.error("getResourceInfo: no uri")
        val byMime = handlers.byMime(p.optString("mime"))
        val e = byMime ?: handlers.forTarget(uri)
        val j = JSONObject().put("returnValue", true).put("uri", uri)
        when {
            e != null -> {
                j.put("appIdByExtension", e.appId)
                if (byMime == null) j.put("mimeByExtension", e.mime)
                j.put("canStream", e.streamable)
            }
            isWebUrl(uri) -> j.put("appIdByExtension", BROWSER).put("canStream", false)
            // A scheme's app from the system's table (mailto: to Email), as the TouchPad answers.
            handlers.commandFor(uri) != null -> j.put("appIdByExtension", handlers.commandFor(uri)).put("canStream", false)
            else -> return JSONObject().put("returnValue", false).put("errorText", "can't find a handler for the given uri").toString()
        }
        return j.toString()
    }

    /**
     * applicationManager/listPackages, which App Catalog reads before it follows its installs.
     * Measured on the reference TouchPad: private bus only; `{"returnValue":true, "packages":
     * [...]}`, every package on the device, the system's with `userInstalled` false, each
     * with its apps (as listApps gives them) and its services (their services.json, with the
     * service's `id`). Lunacy knows no package sizes, so they are 0.
     */
    private fun listPackages(): String {
        val list = org.json.JSONArray()
        for (app in registry.apps) {
            if (app.androidComponent != null) continue
            val pkgDir = java.io.File(files.root, "usr/palm/packages/${app.id}")
            val info = runCatching { JSONObject(java.io.File(pkgDir, "packageinfo.json").readText()) }.getOrNull()
            val services = org.json.JSONArray()
            val ids = info?.optJSONArray("services") ?: org.json.JSONArray()
            for (i in 0 until ids.length()) {
                val id = ids.optString(i)
                val json = java.io.File(files.root, "${JsServices.SERVICES}/$id/services.json")
                runCatching { JSONObject(json.readText()) }.getOrNull()?.let { services.put(JSONObject().put("id", id).also { o -> it.keys().forEach { k -> o.put(k, it.get(k)) } }) }
            }
            val entry = app.listEntry()
            val icon = if (java.io.File(pkgDir, "icon.png").isFile) "/media/cryptofs/apps/usr/palm/packages/${app.id}/icon.png" else entry.optString("icon")
            list.put(JSONObject().put("id", info?.optString("id")?.ifEmpty { null } ?: app.id).put("loc_name", app.title)
                .put("package_format_version", 2).put("vendor", entry.optString("vendor")).put("version", app.version)
                .put("size", 0).put("icon", icon).put("userInstalled", app.userInstalled)
                .put("apps", org.json.JSONArray().put(entry)).put("services", services))
        }
        return JSONObject().put("returnValue", true).put("packages", list).toString()
    }

    /** Open applicationManager/launchPointChanges subscriptions. */
    private val launchPointWatchers = ArrayList<Bus.Call>()

    /**
     * applicationManager/launchPointChanges, which App Catalog watches for its install buttons.
     * Measured on the reference TouchPad: private bus only (a public caller gets "Unknown
     * method"), subscriptions only, `{"returnValue":true,"subscribed":true}` first, then each
     * launch point that comes or goes with `"change":"added"` or `"removed"`.
     */
    private fun launchPointChanges(call: Bus.Call) {
        if (!call.subscribe) return call.reply(JSONObject().put("returnValue", false).put("subscribed", false)
            .put("errorText", "Only supports subscriptions").toString())
        call.reply(JSONObject().put("returnValue", true).put("subscribed", true).toString())
        launchPointWatchers += call
        call.onCancel { launchPointWatchers.remove(call) }
    }

    /** An app's default launch point came or went (the shell's rescan). */
    fun launchPointChanged(app: AppInfo, change: String) {
        if (launchPointWatchers.isEmpty()) return
        val e = app.listEntry()
        val j = JSONObject().put("id", app.id).put("version", app.version).put("appId", app.id)
            .put("vendor", e.optString("vendor")).put("vendorUrl", e.optString("vendorUrl")).put("size", 0)
            .put("packageId", app.id).put("removable", app.userInstalled).put("launchPointId", app.id + "_default")
            .put("title", app.title).put("appmenu", e.optString("appmenu")).put("icon", e.optString("icon"))
            .put("change", change).toString()
        launchPointWatchers.toList().forEach { it.reply(j) }
    }

    private fun launchPointChanged(lp: LaunchPoint, change: String) {
        if (launchPointWatchers.isEmpty()) return
        val j = lp.toJson(registry.get(lp.appId)).put("change", change).toString()
        launchPointWatchers.toList().forEach { it.reply(j) }
    }

    /** The launcher, the dock and Just Type after an added launch point came, went or changed. */
    private fun addedLaunchPointsChanged() = shell.launchPointsChanged()

    /**
     * applicationManager/addLaunchPoint, LunaSysMgr's servicecallback_addLaunchPoint: any app
     * may add a launch point for any installed app. `id`, `title`, `icon` and `params` are
     * required; a relative icon is the app's own file, and `file://` is taken off. The reply
     * is `{"returnValue":true,"launchPointId":"…"}`.
     */
    private fun addLaunchPoint(p: JSONObject): String {
        fun fail(text: String) = JSONObject().put("returnValue", false).put("errorText", text).toString()
        if (!p.has("id") || !p.has("title") || !p.has("icon") || !p.has("params")) return fail("Invalid arguments")
        val id = p.optString("id")
        val app = registry.get(id) ?: return fail("Unable to find id: $id")
        val title = p.optString("title")
        var icon = p.optString("icon")
        icon = when {
            icon.contains("://") -> icon.substringAfter("://")
            icon.startsWith("/") -> icon
            else -> "/media/cryptofs/apps/${Packages.APPS}/${app.dir}/$icon"
        }
        // json_object_get_string: an object or array as its JSON, a string as itself.
        val params = when (val v = p.opt("params")) {
            is JSONObject, is org.json.JSONArray -> v.toString()
            null, JSONObject.NULL -> null
            else -> JSONObject.quote(v.toString())
        }
        // LunaSysMgr reads the menu name as "appmenu", though its schema spells it appMenu.
        val appmenu = p.optString("appmenu", title)
        val lp = addedLaunchPoints.add(app, title, appmenu, icon, params, p.optBoolean("removable", true))
            ?: return JSONObject().put("returnValue", true).put("errorText", "Failed to save launch point").toString()
        addedLaunchPointsChanged()
        launchPointChanged(lp, "added")
        return JSONObject().put("returnValue", true).put("launchPointId", lp.launchPointId).toString()
    }

    /** applicationManager/removeLaunchPoint, and the launcher's Remove on a shortcut. Null once removed, else the error's text. */
    fun removeLaunchPoint(launchPointId: String): String? {
        val lp = addedLaunchPoints.get(launchPointId)
        addedLaunchPoints.remove(launchPointId)?.let { return it }
        if (lp != null) {
            shell.forgetIcons(AppInfo.LAUNCH_POINT_PREFIX + launchPointId)
            addedLaunchPointsChanged()
            launchPointChanged(lp, "removed")
        }
        return null
    }

    /**
     * applicationManager/updateLaunchPointIcon: an app may change only its own launch points'
     * icons - caller identity from the bus, never from the params (rule 10).
     */
    private fun updateLaunchPointIcon(caller: String, p: JSONObject): String {
        val id = p.optString("launchPointId")
        if (id.isEmpty()) return Bus.error("Must provide launchPointId")
        if (!p.has("icon")) return Bus.error("Must provide icon path")
        // The app's own icon, its default launch point.
        if (id.endsWith("_default")) registry.get(id.removeSuffix("_default"))?.let { app ->
            if (app.id != caller) return Bus.error("Attempted to change another application's launch point icon")
            val base = "/media/cryptofs/apps/${Packages.APPS}/${app.dir}/"
            val icon = p.optString("icon").removePrefix("file://").removePrefix(base)
            val updated = (if (icon.startsWith("/")) null else registry.setIcon(app.id, icon))
                ?: return Bus.error("Unable to update launch point's icon")
            shell.forgetIcons(app.id)
            shell.launchPointsChanged()
            launchPointChanged(updated, "updated")
            return Bus.ok()
        }
        val lp = addedLaunchPoints.get(id) ?: return Bus.error("launchPointId \"$id\" was not found")
        if (lp.appId != caller) return Bus.error("Attempted to change another application's launch point icon")
        val app = registry.get(lp.appId)
        var icon = p.optString("icon")
        if (!icon.contains("://") && !icon.startsWith("/") && app != null) icon = "/media/cryptofs/apps/${Packages.APPS}/${app.dir}/$icon"
        val updated = addedLaunchPoints.updateIcon(lp, app, icon) ?: return Bus.error("Unable to update launch point's icon")
        shell.forgetIcons(AppInfo.LAUNCH_POINT_PREFIX + id)
        addedLaunchPointsChanged()
        launchPointChanged(updated, "updated")
        return Bus.ok()
    }

    companion object {
        /** Package installers apps hand .ipks to: Preware on webOS, and LuneOS's Preware. */
        val INSTALLERS = setOf("org.webosinternals.preware", "org.webosports.app.preware")
        /** webOS's Web app, which Lunacy ships; web links open in it. */
        const val BROWSER = "com.palm.app.browser"
        const val IPK_MIME = "application/vnd.webos.ipk"
    }
}
