package org.webosarchive.lunacy.card

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import android.webkit.WebSettings
import org.json.JSONObject
import java.io.File

/**
 * palm://org.webosarchive.lunacy — Lunacy's own service, under its own name. Everything here
 * belongs to Lunacy, not to webOS, so it is kept off webOS's service names and the bus stays
 * honest about which is which (Docs/architecture.md, "Luna bus").
 *
 * - `system/getEnvironment` reports what Lunacy actually runs on, for Lunacy's Device Info.
 * - `system/setHostWallpaper` is the opt-in to Android's wallpaper following webOS's.
 * - `system/setNotificationIcons` chooses colour or grey app icons in the notifications.
 * - `system/saveLog` writes a log a tester can send into Android's Download folder, and
 *   `system/shareLog` hands it to Android's share sheet: Device Info's Save a Log ([LogExport]).
 * - `android/notifications`, `android/openNotification` and `android/dismissNotifications`
 *   are Android's notifications, for Lunacy's Android Notifications app ([AndroidNotifications]).
 * - `android/openSettings` hands a setting that belongs to the host OS to Android's own UI.
 * - `permissions/status` and `permissions/request` are Android's runtime permissions, for
 *   Lunacy's First Use; `firstUse/done` is how First Use says it has run.
 * - `launcher/listApps` and `launcher/setHidden` are for Software Manager: every app the
 *   launcher can show, webOS and Android, and whether the user has hidden it ([HiddenApps]).
 *   `launcher/pinStatus` and `launcher/pin` put a webOS app on Android's own home screen, when
 *   Lunacy isn't the home app (shell/HomeScreenPins.kt).
 *
 *
 * What it needs of the shell comes through [ShellHooks].
 */
class LunacyService(
    private val context: Context,
    private val registry: AppRegistry,
    private val jsServices: JsServices,
    private val webosRoot: File,
    /** What the service asks of the shell. */
    private val shell: ShellHooks,
) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** The two answers that take real time - Node's version is asked of Node, and a listing walks a folder - come from here. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val logs = LogExport(context, webosRoot)

    fun register(bus: Bus) {
        bus.register(SERVICE, "system/getEnvironment") { _, _, reply -> worker.execute { val r = environment(); main.post { reply(r) } } }
        bus.register(SERVICE, "files/list") { _, p, reply -> worker.execute { val r = runCatching { listFiles(p) }.getOrElse { Bus.error("files/list: $it") }; main.post { reply(r) } } }
        bus.own("system/setDeviceId", "set the device id") { c -> c.reply(setDeviceId(c.params)) }
        bus.own("system/setLayout", "set the layout") { c -> c.reply(setLayout(c.params)) }
        bus.own("system/setFixedViewport", "set an app's viewport") { c -> c.reply(setFixedViewport(c.params)) }
        bus.own("system/setHostWallpaper", "set Android's wallpaper") { c -> setHostWallpaper(c.params, c::reply) }
        bus.own("system/setNotificationIcons", "set the notification icons") { c -> c.reply(setNotificationIcons(c.params)) }
        // Device Info's Save a Log: a file a tester can send, and Android's share sheet for it.
        bus.own("system/saveLog", "save a log") { c -> worker.execute { val r = logs.save(environment(), permissionsStatus()); main.post { c.reply(r) } } }
        bus.own("system/shareLog", "share a log") { c -> c.reply(logs.share(c.params.optString("path"))) }
        bus.register(SERVICE, "android/openSettings") { _, p, reply -> reply(openSettings(p.optString("panel"))) }
        bus.register(SERVICE, "android/settingsPanels") { _, _, reply ->
            reply(Bus.ok(mapOf("panels" to org.json.JSONArray(PANELS.keys.sorted()))))
        }
        bus.register(SERVICE, "permissions/status") { _, _, reply -> reply(permissionsStatus()) }
        bus.register(SERVICE, "permissions/request") { caller, p, reply -> requestPermission(caller, p, reply) }
        bus.own("firstUse/done", "finish First Use") { c -> c.reply(firstUseDone()) }
        bus.own("launcher/listApps", "list the launcher's apps") { c -> listLauncherApps(c) }
        bus.own("launcher/setHidden", "hide an app") { c -> c.reply(setHidden(c.params)) }
        bus.own("launcher/pinStatus", "pin apps") { c -> c.reply(shell.pinStatus().toString()) }
        bus.own("launcher/pin", "pin apps") { c -> c.reply(pin(c.params)) }
        bus.own("android/notifications", "read Android's notifications") { c -> watchNotifications(c) }
        bus.own("android/openNotification", "open Android's notifications") { c -> c.reply(openNotification(c.params)) }
        bus.own("android/dismissNotifications", "dismiss Android's notifications") { c -> c.reply(dismissNotifications(c.params)) }
        bus.own("android/mediaControl", "control Android's players") { c -> c.reply(mediaControl(c.params)) }
    }

    /**
     * A method only Lunacy's own apps may call ([ownApp]); anyone else is told so, with what
     * they asked to do.
     */
    private fun Bus.own(method: String, what: String, h: Bus.CallHandler) = register(SERVICE, method, Bus.CallHandler { c ->
        if (ownApp(c.appId)) h.handle(c) else c.reply(Bus.error("Only Lunacy's own apps can $what (asked by ${c.appId})", -1))
    })

    // ---- Android's notifications, for the Android Notifications app ----

    private val notificationWatchers = LinkedHashSet<Bus.Call>()

    /**
     * Every Android notification Lunacy shows, by app, with `alerts` for the ones just come
     * in that want a banner, and `access` for whether the owner has allowed Lunacy to read
     * them. A subscription hears it again whenever they change. Lunacy's own apps only.
     */
    private fun watchNotifications(call: Bus.Call) {
        call.reply(AndroidNotifications.snapshot(context, consume = true).toString())
        if (call.subscribe && !call.cancelled) {
            notificationWatchers += call
            call.onCancel { notificationWatchers.remove(call) }
        }
    }

    /** Android's notifications changed: whoever is watching hears them; a banner is told once. */
    fun notificationsChanged() {
        if (notificationWatchers.isEmpty()) return
        val r = AndroidNotifications.snapshot(context, consume = true).toString()
        notificationWatchers.toList().forEach { it.reply(r) }
    }

    /** Whether the Android Notifications app is listening: if not, the shell starts it. */
    val notificationsWatched get() = notificationWatchers.isNotEmpty()

    /** A notification tapped: `key`. Its own intent is sent, as Android's shade does. */
    private fun openNotification(p: JSONObject): String {
        val key = p.optString("key").ifEmpty { return Bus.error("key is required") }
        return AndroidNotifications.open(context, key)?.let { Bus.error(it) } ?: Bus.ok()
    }

    /** A media dashboard's button: `package`, `command` (previous, playpause, next, open). AndroidMedia. */
    private fun mediaControl(p: JSONObject): String {
        val pkg = p.optString("package").ifEmpty { return Bus.error("package is required") }
        val command = p.optString("command").ifEmpty { return Bus.error("command is required") }
        return AndroidMedia.control(context, pkg, command)?.let { Bus.error(it) } ?: Bus.ok()
    }

    /** Notifications swiped away: `keys`. They go from Android too. */
    private fun dismissNotifications(p: JSONObject): String {
        val a = p.optJSONArray("keys") ?: return Bus.error("keys is required")
        AndroidNotifications.dismiss((0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() })
        return Bus.ok()
    }

    // ---- the launcher's apps, for Software Manager ----

    private val launcherWatchers = LinkedHashSet<Bus.Call>()
    /** Each icon as a data: URL, by app id and version: Android's icons have no file a page could load. */
    private val iconUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Every app the launcher can show, in its order, with `hidden` for the ones the user has
     * taken off it. A subscription hears the list again whenever it changes: an app installed
     * or removed, or one hidden or shown.
     */
    private fun listLauncherApps(call: Bus.Call) {
        sendLauncherApps(listOf(call))
        if (call.subscribe && !call.cancelled) {
            launcherWatchers += call
            call.onCancel { launcherWatchers.remove(call) }
        }
    }

    /** The launcher's apps changed: whoever is watching hears the new list. */
    fun launcherAppsChanged() { if (launcherWatchers.isNotEmpty()) sendLauncherApps(launcherWatchers.toList()) }

    private fun sendLauncherApps(to: List<Bus.Call>) {
        // The list and the icons are the shell's and are read here, on the main thread; turning
        // forty-odd icons into PNGs is not, so that happens on the worker.
        val apps = shell.launcherApps().map { app ->
            val key = app.id + ":" + app.version
            Triple(app, key, if (iconUrls.containsKey(key)) null else shell.appIcon(app))
        }
        val hiddenNow = shell.hidden?.all().orEmpty()
        worker.execute {
            val list = org.json.JSONArray()
            for ((app, key, bmp) in apps) {
                val icon = iconUrls[key] ?: bmp?.let { dataUrl(it) }?.also { iconUrls[key] = it } ?: ""
                val android = app.androidComponent != null
                list.put(JSONObject()
                    .put("id", app.id).put("title", app.title)
                    .put("vendor", if (android) app.androidComponent!!.packageName else app.appinfo.optString("vendor", ""))
                    // A launch point's version carries its icon, for the launcher's cache: AppInfo.forLaunchPoint.
                    .put("version", if (app.launchPoint != null) app.version.substringBefore(':') else app.version).put("icon", icon)
                    .put("kind", if (android) "android" else "webos")
                    .put("hidden", app.id in hiddenNow)
                    // Software Manager is how an app comes back, so it can't hide itself.
                    .put("canHide", app.id != SOFTWARE_MANAGER))
            }
            val r = Bus.ok(mapOf("apps" to list))
            main.post { to.forEach { it.reply(r) } }
        }
    }

    /** A launcher icon as a PNG data: URL, at most 128 px on a side. */
    private fun dataUrl(bmp: android.graphics.Bitmap): String {
        val side = maxOf(bmp.width, bmp.height)
        val scaled = if (side <= 128) bmp else android.graphics.Bitmap.createScaledBitmap(bmp, bmp.width * 128 / side, bmp.height * 128 / side, true)
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /** A webOS app on Android's home screen: its icon there opens it straight away. */
    private fun pin(p: JSONObject): String {
        val id = p.optString("id")
        val app = shell.launcherApps().firstOrNull { it.id == id } ?: return Bus.error("No launcher app $id", -1)
        if (app.androidComponent != null) return Bus.error("${app.title} is Android's own: it is on Android's launcher already", -1)
        return shell.pinToHome(app, shell.appIcon(app))?.let { Bus.error(it, -1) } ?: Bus.ok()
    }

    private fun setHidden(p: JSONObject): String {
        val id = p.optString("id")
        if (id.isEmpty() || !p.has("hidden")) return Bus.error("setHidden needs an id and hidden", -1)
        if (id == SOFTWARE_MANAGER) return Bus.error("Software Manager can't be hidden: it is how hidden apps come back", -1)
        val store = shell.hidden ?: return Bus.error("Hiding apps is not available", -1)
        if (shell.launcherApps().none { it.id == id }) return Bus.error("No launcher app $id", -1)
        store.set(id, p.optBoolean("hidden"))
        return Bus.ok()
    }

    // ---- Android's permissions, for First Use ----

    /**
     * What Lunacy may do on this device. Each permission says whether Android asks for it at
     * all (`asked`: from Android 6; Android 5 granted everything at install) and whether it
     * is granted now. `storage` is the shared storage behind /media/internal; `location` is
     * what Android asks before it names the Wi-Fi networks around (the system menu's list);
     * `systemSettings` is "Modify system settings", which Screen & Lock's brightness needs;
     * `homeLauncher` is whether Lunacy is the device's home screen, which is offered on
     * every Android version and is the owner's choice rather than a permission.
     */
    private fun permissionsStatus(): String {
        val asked = Build.VERSION.SDK_INT >= 23
        return Bus.ok(mapOf(
            "storage" to JSONObject().put("asked", asked).put("granted", storageGranted()),
            "location" to JSONObject().put("asked", asked).put("granted", granted(LOCATION_ANY)),
            "contacts" to JSONObject().put("asked", asked).put("granted", granted(CONTACTS)),
            "calendar" to JSONObject().put("asked", asked).put("granted", granted(CALENDAR)),
            "systemSettings" to JSONObject().put("asked", asked).put("granted", systemSettingsGranted()),
            "background" to JSONObject().put("asked", asked).put("granted", backgroundAllowed(context)),
            "homeLauncher" to JSONObject().put("asked", true).put("granted", isDefaultHome()),
            "firstUseDone" to firstUseDone(context),
        ))
    }

    /** Whether Android starts Lunacy as the home screen: the default handler for the HOME intent is Lunacy's own activity. */
    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * An account's setup page runs inside the app that is adding the account, and calls the bus
     * as that app: the Android account's page asks through bundled Contacts, Calendar or Accounts.
     */
    private fun hostsAccountPages(appId: String) =
        appId in ACCOUNT_PAGE_HOSTS && registry.get(appId)?.userInstalled == false

    private fun storageGranted(): Boolean = granted(STORAGE)

    private fun granted(permissions: List<String>): Boolean = permissions.all {
        context.checkPermission(it, android.os.Process.myPid(), android.os.Process.myUid()) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun systemSettingsGranted(): Boolean = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(context)

    /**
     * Asks Android. Storage, contacts and calendars are a dialog, answered when the user has: the reply waits for it,
     * and says whether Android would ask again (`askAgain`); once it won't, the only way left
     * is Android's own settings screen for Lunacy, which `viaSettings` opens instead.
     * "Modify system settings" has no dialog; Android grants it only on its own screen, which
     * is opened, and the reply says so (`opened`): the caller sees the grant in the next
     * `permissions/status`. Only Lunacy's own apps may ask: an installed app has no business
     * putting Android's dialogs in front of the user.
     */
    private fun requestPermission(caller: String, p: JSONObject, reply: (String) -> Unit) {
        val which = p.optString("permission")
        if (!ownApp(caller) && !((which == "contacts" || which == "calendar") && hostsAccountPages(caller))) {
            reply(Bus.error("Only Lunacy's own apps can ask for Android's permissions (asked by $caller)", -1)); return
        }
        when (which) {
            "storage", "location", "contacts", "calendar" -> {
                val permissions = when (which) { "storage" -> STORAGE; "location" -> LOCATION; "contacts" -> CONTACTS; else -> CALENDAR }
                // Asked once: the system menu doesn't put Android's question up again (SystemControls.scan).
                if (which == "location") setLocationAsked(context)
                // Location: approximate alone is enough (Android 12 lets the owner choose it).
                val enough = if (which == "location") LOCATION_ANY else permissions
                if (granted(enough)) { reply(Bus.ok(mapOf("permission" to which, "granted" to true))); return }
                if (p.optBoolean("viaSettings")) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    reply(try {
                        context.startActivity(intent)
                        Bus.ok(mapOf("permission" to which, "granted" to false, "opened" to true))
                    } catch (e: Exception) {
                        Log.w(AppServer.TAG, "permissions: can't open Lunacy's app settings", e)
                        Bus.error("Couldn't open Android's settings for Lunacy: ${e.message}")
                    })
                    return
                }
                shell.requestPermissions(permissions) { granted, askAgain ->
                    reply(Bus.ok(mapOf("permission" to which, "granted" to (granted || granted(enough)), "askAgain" to askAgain)))
                }
            }
            "systemSettings" -> {
                if (systemSettingsGranted()) { reply(Bus.ok(mapOf("permission" to which, "granted" to true))); return }
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    reply(Bus.ok(mapOf("permission" to which, "granted" to false, "opened" to true)))
                } catch (e: Exception) {
                    Log.w(AppServer.TAG, "permissions: can't open the write-settings screen", e)
                    reply(Bus.error("Couldn't open Android's screen for modifying system settings: ${e.message}"))
                }
            }
            "homeLauncher" -> {
                if (isDefaultHome()) { reply(Bus.ok(mapOf("permission" to which, "granted" to true))); return }
                // From Android 10 the system puts up its own "set as default home" dialog, which
                // has to be started for a result; before that, Android's Home settings screen.
                val opened = if (Build.VERSION.SDK_INT >= 29) {
                    val roles = context.getSystemService(android.app.role.RoleManager::class.java)
                    if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                        shell.startForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                    } else false
                } else false
                val ok = opened || runCatching {
                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
                }.getOrElse { Log.w(AppServer.TAG, "permissions: can't open the home settings", it); false }
                reply(if (ok) Bus.ok(mapOf("permission" to which, "granted" to false, "opened" to true))
                      else Bus.error("Couldn't open Android's home screen choice"))
            }
            "background" -> {
                if (backgroundAllowed(context)) { reply(Bus.ok(mapOf("permission" to which, "granted" to true))); return }
                setBackgroundAsked(context)
                // Android's own question ("let the app always run in the background?"), or failing
                // that the list of apps under battery optimisation.
                val ok = listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + context.packageName)),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                ).any { i -> runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
                reply(if (ok) Bus.ok(mapOf("permission" to which, "granted" to false, "opened" to true))
                      else Bus.error("Couldn't open Android's battery settings"))
            }
            else -> reply(Bus.error("No permission \"$which\": ${PERMISSIONS.joinToString(", ")}"))
        }
    }

    /** First Use has run. The shell won't launch it again, and asks for storage itself from then on if it is missing. */
    private fun firstUseDone(): String {
        setFirstUseDone(context)
        // First Use had its Running in the Background page; the shell doesn't put it up again.
        setBackgroundAsked(context)
        shell.onFirstUseDone()
        return Bus.ok()
    }

    // ---- the environment ----

    private fun environment(): String {
        val j = JSONObject().put("returnValue", true)
        j.put("lunacy", JSONObject()
            .put("version", versionName()).put("build", versionCode())
            .put("package", context.packageName)
            .put("reportsWebOS", DeviceProfile.forScreen(context).versionString)
            .put("reportsAs", DeviceProfile.forScreen(context).modelName)
            .put("enyo", "1.0")
            .put("node", nodeVersion())
            .put("apps", registry.apps.size)
            .put("services", jsServices.names()))
        j.put("android", JSONObject()
            .put("release", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("build", Build.DISPLAY).put("abi", abi()))
        val profile = DeviceProfile.forScreen(context)
        j.put("webos", JSONObject()
            .put("model", profile.modelName)
            .put("version", profile.versionString)
            .put("serial", DeviceProfile.serial(context, profile))
            .put("nduid", DeviceProfile.nduid(context))
            .put("nduidSource", DeviceProfile.nduidSource(context)))
        j.put("device", JSONObject()
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("serial", serial()).put("ram", totalRam())
            .put("storageTotal", storage(true)).put("storageFree", storage(false))
            .put("battery", battery()).put("charging", charging()))
        j.put("webview", webView())
        j.put("display", shell.display())
        j.put("layout", FormFactor.describe(context).put("appLayoutWidth", FormFactor.appLayoutWidth(context)))
        j.put("hostWallpaper", HostWallpaper.isOn(context))
        j.put("colourNotificationIcons", NotificationIcons.colour(context))
        // Android's navigation: "gesture" takes the side edges for its back gesture (Screen &
        // Lock greys out Lunacy's edge swipe then), "buttons" leaves them to Lunacy. Android 10
        // brought gesture navigation; before it there were only buttons.
        j.put("navigationMode", if (Build.VERSION.SDK_INT >= 29 &&
            android.provider.Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2) "gesture" else "buttons")
        j.put("androidNotifications", AndroidNotifications.accessGranted(context))
        // A notification light to blink (Screen & Lock's Blink Notifications, NotificationLight).
        j.put("notificationLight", org.webosarchive.lunacy.shell.NotificationLight.deviceHasLight())
        j.put("software", FixedViewport.describe(context, registry.apps.filter { it.androidComponent == null }.sortedBy { it.title.lowercase() }))
        return j.toString()
    }

    private fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    @Suppress("DEPRECATION")
    private fun versionCode(): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }.getOrDefault(0)

    private fun abi(): String {
        @Suppress("DEPRECATION")
        return Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI
    }

    @Suppress("DEPRECATION")
    private fun serial(): String = Build.SERIAL ?: "unknown"

    private fun totalRam(): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return info.totalMem
    }

    /** The user-visible storage, which is where installed apps and /media/internal live. */
    @Suppress("DEPRECATION")
    private fun storage(total: Boolean): Long {
        val path: File = context.filesDir
        val s = StatFs(path.path)
        val block = s.blockSize.toLong()
        return block * (if (total) s.blockCount.toLong() else s.availableBlocks.toLong())
    }

    private fun batteryIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun battery(): Int {
        val i = batteryIntent() ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        return if (level < 0) -1 else level * 100 / scale
    }

    private fun charging(): Boolean = (batteryIntent()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    /**
     * The WebView behind every card. Its own user agent names the Chromium it is (app windows
     * report the TouchPad's instead), and the package that provides it can be updated on
     * Android 5, so both are worth reporting.
     */
    private fun webView(): JSONObject {
        val ua = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("")
        val version = Regex("Chrome/([0-9.]+)").find(ua)?.groupValues?.get(1) ?: ""
        val pkg = WEBVIEW_PACKAGES.firstOrNull { p ->
            runCatching { context.packageManager.getPackageInfo(p, 0) }.isSuccess
        }
        val pkgVersion = pkg?.let { runCatching { context.packageManager.getPackageInfo(it, 0).versionName }.getOrNull() }
        return JSONObject()
            .put("chromium", version.substringBefore('.'))
            .put("version", pkgVersion ?: version)
            .put("package", pkg ?: "")
    }

    /** Asked of Node itself, once: the runtime that runs apps' JS services. */
    private fun nodeVersion(): String {
        cachedNode?.let { return it }
        val v = runCatching {
            val node = File(context.applicationInfo.nativeLibraryDir, "liblunacynode.so")
            if (!node.canExecute()) return@runCatching ""
            val p = ProcessBuilder(node.path, "-e", "process.stdout.write(process.versions.node)")
                .apply { environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir }
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out.takeIf { Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$").matches(it) } ?: ""
        }.getOrDefault("")
        cachedNode = v
        return v
    }

    /**
     * Sets webOS's device id (nduid), or goes back to this device's own with
     * `{"reset": true}`.
     *
     * Why this exists: webOS Archive's services know a device by its nduid - the App Museum
     * and the shared updater library both send it as `clientid` - and app licences were tied
     * to it. Someone moving off a TouchPad that is failing can carry its id across and be the
     * same device here, which is the whole point of Lunacy. On a device the id came from the
     * hardware's token and couldn't be changed, but it was never a secret either: every app
     * could read it.
     *
     * Only Lunacy's own apps may call this. The caller's id comes from the window it was
     * called in, never from anything the page says (rule 10), so an installed app can't
     * quietly change the device's identity underneath its owner.
     */
    /**
     * The layout setting (FormFactor): `layout` "auto", "phone" or "tablet", and
     * `appLayoutWidth`, the width in px a phone lays an app's page out at (0: the card's own).
     * Lunacy's own apps only: Device Info, and Screen & Lock, where the layout is a setting
     * of the screen (codepoet, 2026-10-03). A layout that comes out different from what the
     * shell is showing rebuilds the shell, its cards kept, once the reply is on its way
     * (ShellActivity.reshell); the width is taken
     * up when an app next loads a page.
     */
    private fun setLayout(p: JSONObject): String {
        val before = FormFactor.of(context)
        if (p.has("layout") && !FormFactor.setSetting(context, p.optString("layout"))) {
            return Bus.error("layout is auto, phone or tablet")
        }
        if (p.has("appLayoutWidth")) FormFactor.setAppLayoutWidth(context, p.optInt("appLayoutWidth"))
        val restart = FormFactor.decide(context) != before
        if (restart) main.postDelayed({ shell.onLayoutChanged() }, RESTART_DELAY_MS)
        return Bus.ok(mapOf("layout" to FormFactor.describe(context).put("appLayoutWidth", FormFactor.appLayoutWidth(context)), "restart" to restart))
    }

    /**
     * The fixed-viewport fallback for one app (FixedViewport): `id`, and `on` true or false,
     * or absent to put the app back on its default. Lunacy's own apps only. Takes effect
     * when the app's window next loads a page.
     */
    private fun setFixedViewport(p: JSONObject): String {
        val id = p.optString("id")
        val app = registry.get(id) ?: return Bus.error("No app $id")
        FixedViewport.set(context, id, if (p.has("on")) p.optBoolean("on") else null)
        return Bus.ok(mapOf("id" to id, "on" to FixedViewport.isOn(context, id), "default" to FixedViewport.default(id), "title" to app.title))
    }

    /**
     * Whether apps' icons in the notifications show in colour or grey ([NotificationIcons]):
     * `colour` true or false. Lunacy's own apps only (Screen & Lock). The icons showing change at once.
     */
    private fun setNotificationIcons(p: JSONObject): String {
        if (!p.has("colour")) return Bus.error("colour is true or false")
        NotificationIcons.setColour(context, p.optBoolean("colour"))
        return Bus.ok(mapOf("colour" to NotificationIcons.colour(context)))
    }

    /**
     * Whether Android's wallpaper follows webOS's ([HostWallpaper]): `on` true or false.
     * Lunacy's own apps only. Turning it on sets Android's wallpaper straight away, and the
     * reply waits for that, so a device that refuses says so.
     */
    private fun setHostWallpaper(p: JSONObject, reply: (String) -> Unit) {
        if (!p.has("on")) return reply(Bus.error("on is required"))
        val on = p.optBoolean("on")
        if (!on) {
            HostWallpaper.setOn(context, false)
            return reply(Bus.ok(mapOf("on" to false)))
        }
        val open = shell.wallpaper()
        worker.execute {
            val failed = HostWallpaper.apply(context, open)
            if (failed == null) HostWallpaper.setOn(context, true)
            main.post { reply(failed?.let { Bus.error(it) } ?: Bus.ok(mapOf("on" to true))) }
        }
    }

    private fun setDeviceId(p: JSONObject): String {
        // Back to the id derived from this device's hardware. Never a new random one: a
        // service counting devices shouldn't see a new one every time somebody taps a button.
        if (p.optBoolean("reset")) {
            return Bus.ok(mapOf("nduid" to DeviceProfile.clearNduid(context), "source" to DeviceProfile.DERIVED))
        }
        val raw = p.optString("nduid")
        if (raw.isEmpty()) return Bus.error("nduid is required")
        val id = DeviceProfile.setNduid(context, raw)
            ?: return Bus.error("A webOS device id is 40 hexadecimal digits; that is ${raw.trim().length} characters")
        return Bus.ok(mapOf("nduid" to id, "source" to DeviceProfile.USER))
    }

    /**
     * An app Lunacy ships and owns, rather than one the user installed. An installed package
     * with the same id replaces the bundled app, so the registry has the last word.
     */
    internal fun ownApp(appId: String): Boolean {
        val known = appId in OWN_APPS || appId.startsWith("org.webosarchive.lunacy")
        return known && registry.get(appId)?.userInstalled == false
    }

    // ---- files, for Lunacy's file picker ----

    /**
     * Lists a folder of the webOS tree, grouped by the folder each file sits in, the way
     * webOS's picker showed albums. webOS had a media indexer and its db8 kinds for this;
     * Lunacy hasn't, so the picker reads the tree directly - through Lunacy's own service,
     * not a webOS name.
     */
    private fun listFiles(p: JSONObject): String {
        val path = p.optString("path").ifEmpty { "/media/internal" }
        val types = p.optJSONArray("types")?.let { a -> (0 until a.length()).map { a.getString(it).lowercase() } }
        if (types != null && "ringtone" in types) return listRingtones()
        val internal = path.removePrefix("/media/internal").trimStart('/')
        val root = if (path.trimEnd('/') == "/media/internal" || path.startsWith("/media/internal/")) {
            UserFiles.resolve(webosRoot, internal)
        } else File(webosRoot, path.trimStart('/')).takeIf { UserFiles.inside(it, webosRoot) }
        if (root == null || !root.isDirectory) return Bus.error("No such folder: $path")
        // Lunacy's own folders, then the Android ones mapped in beside them (UserFiles), which
        // is where the files a person actually has live.
        val subfolders = (root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && !UserFiles.nestedRoot(it) } ?: emptyList()) +
            (if (root == UserFiles.own(webosRoot)) UserFiles.mappedFolders().map { it.second }.filter { m ->
                root.listFiles().orEmpty().none { it.canonicalPath == m.canonicalPath }
            } else emptyList())
        val albums = org.json.JSONArray()
        // The folder itself first, then its subfolders, as the picker lists them.
        for (dir in listOf(root) + subfolders.sortedBy { it.name.lowercase() }) {
            val files = org.json.JSONArray()
            dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?.forEach { f ->
                    val type = typeOf(f.name)
                    if (types != null && type !in types) return@forEach
                    files.put(JSONObject()
                        .put("name", f.name)
                        .put("fullPath", webosPath(f))
                        .put("attachmentType", type)
                        .put("size", f.length()))
                }
            if (files.length() > 0) {
                albums.put(JSONObject()
                    .put("name", if (dir == root) rootName(path) else dir.name)
                    .put("path", webosPath(dir))
                    .put("files", files))
            }
        }
        return JSONObject().put("returnValue", true).put("path", path).put("albums", albums).toString()
    }

    /**
     * webOS's "ringtone" type: the sounds in /media/internal/ringtones (Android's
     * Notifications/webOS, Ringtones), each with its title, as Palm's ringtone picker listed
     * them from the media index: its title and artist from its tags, as filenotifyd read them.
     */
    private fun listRingtones(): String {
        val dir = UserFiles.resolve(webosRoot, "ringtones")
        val files = org.json.JSONArray()
        dir?.listFiles()?.filter { it.isFile && !it.name.startsWith(".") && typeOf(it.name) == "audio" }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { f ->
                val path = "${Ringtones.WEBOS_DIR}/${f.name}"
                val r = MediaIndex.audioRecord(path, f)
                files.put(JSONObject().put("name", f.name).put("title", r.optString("title")).put("artist", r.optString("artist"))
                    .put("searchKey", r.optString("searchKey"))
                    .put("fullPath", path).put("attachmentType", "audio").put("size", f.length()))
            }
        val albums = org.json.JSONArray()
        if (files.length() > 0) albums.put(JSONObject().put("name", "Ringtones").put("path", Ringtones.WEBOS_DIR).put("files", files))
        return JSONObject().put("returnValue", true).put("path", Ringtones.WEBOS_DIR).put("albums", albums).toString()
    }

    private fun rootName(path: String) = path.trimEnd('/').substringAfterLast('/').ifEmpty { "Files" }

    /** The path an app sees: a webOS one, whichever side of the mapping the file is really on. */
    private fun webosPath(f: File): String =
        UserFiles.webosPath(webosRoot, f) ?: ("/" + f.canonicalPath.removePrefix(webosRoot.canonicalPath).trimStart('/'))

    /**
     * webOS's attachment types, by extension: what a FilePicker's fileType filters on. A
     * document is whatever the file index calls one, as on a device: every file that isn't
     * audio, an image or a video.
     */
    private fun typeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "image"
        "mp3", "m4a", "aac", "wav", "ogg", "oga", "flac" -> "audio"
        "mp4", "m4v", "mov", "avi", "mkv", "webm", "3gp" -> "video"
        else -> if (FileIndex.isDocument(name)) "document" else "other"
    }

    // ---- Android's own settings ----

    /**
     * Opens one of Android's settings screens. A webOS setting that belongs to the host OS
     * (Wi-Fi, security, sound) is handed over rather than faked: Lunacy doesn't own it.
     */
    private fun openSettings(panel: String): String {
        val action = PANELS[panel.lowercase()] ?: return Bus.error("No Android settings panel \"$panel\"")
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return Bus.error("This device has no screen for \"$panel\"")
        }
        return try {
            context.startActivity(intent)
            Bus.ok(mapOf("panel" to panel))
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "openSettings $panel failed", e)
            Bus.error("Couldn't open Android's $panel settings: ${e.message}")
        }
    }

    companion object {
        const val SERVICE = "org.webosarchive.lunacy"
        /** Lunacy's First Use app, launched by the shell on the first start (ShellActivity). */
        const val FIRST_USE_APP = "org.webosarchive.lunacy.firstuse"
        /** Software Manager, which hides and shows the launcher's apps. */
        const val SOFTWARE_MANAGER = "com.palm.app.swmanager"
        /** Bundled settings apps that are Lunacy's own beside its org.webosarchive.lunacy ones ([ownApp]). */
        private val OWN_APPS = setOf("com.palm.app.deviceinfo", "com.palm.app.screenlock", SOFTWARE_MANAGER)
        /** Bundled apps that host an account's setup page ([hostsAccountPages]). */
        private val ACCOUNT_PAGE_HOSTS = setOf("com.palm.app.contacts", "com.palm.app.calendar", "com.palm.app.accounts")
        /** What permissions/request takes: its `when`, in order. */
        private val PERMISSIONS = listOf("storage", "location", "contacts", "calendar", "systemSettings", "background", "homeLauncher")
        private val STORAGE = listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        /**
         * Location: Android names the Wi-Fi networks around only to an app with it (SystemControls),
         * and webOS apps the owner allows get positions from it (LocationService). Precise is asked
         * for with approximate, for GPS; approximate alone ([LOCATION_ANY]) is enough to count.
         */
        val LOCATION = listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val LOCATION_ANY = listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        private val CONTACTS = listOf(android.Manifest.permission.READ_CONTACTS)
        private val CALENDAR = listOf(android.Manifest.permission.READ_CALENDAR)
        /** Long enough for the setting app's reply to land and its selector to close before the shell goes. */
        const val RESTART_DELAY_MS = 400L
        fun firstUseDone(context: Context): Boolean =
            context.getSharedPreferences("device", Context.MODE_PRIVATE).getBoolean("firstUseDone", false)
        fun setFirstUseDone(context: Context) {
            context.getSharedPreferences("device", Context.MODE_PRIVATE).edit().putBoolean("firstUseDone", true).apply()
        }

        /**
         * Whether Android leaves Lunacy out of battery optimisation (Doze's app standby), so its
         * mail, calendars and messaging aren't held back. Android 5 has no such optimisation.
         */
        fun backgroundAllowed(context: Context): Boolean = if (Build.VERSION.SDK_INT < 23) true
            else (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        /**
         * Whether Android's location question has been put once, by First Use (which explains
         * it) or by the system menu's Wi-Fi list: after that the menu doesn't ask again
         * unexplained, and a refusal stands until the owner changes it in Android's settings.
         */
        fun locationAsked(context: Context): Boolean =
            context.getSharedPreferences("device", Context.MODE_PRIVATE).getBoolean("locationAsked", false)
        fun setLocationAsked(context: Context) {
            context.getSharedPreferences("device", Context.MODE_PRIVATE).edit().putBoolean("locationAsked", true).apply()
        }
        /** Whether the owner has been asked for [backgroundAllowed] once: by First Use, or by its one page after an update. */
        fun backgroundAsked(context: Context): Boolean =
            context.getSharedPreferences("device", Context.MODE_PRIVATE).getBoolean("backgroundAsked", false)
        fun setBackgroundAsked(context: Context) {
            context.getSharedPreferences("device", Context.MODE_PRIVATE).edit().putBoolean("backgroundAsked", true).apply()
        }
        private var cachedNode: String? = null
        /** The packages that can provide the WebView on the Android versions Lunacy targets. */
        private val WEBVIEW_PACKAGES = listOf(
            "com.google.android.webview", "com.android.webview",
            "com.google.android.trichromelibrary", "com.android.chrome",
        )
        /** webOS settings that belong to the host OS, and the Android screen that owns each. */
        val PANELS: Map<String, String> = mapOf(
            "wifi" to Settings.ACTION_WIFI_SETTINGS,
            "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            "display" to Settings.ACTION_DISPLAY_SETTINGS,
            "sound" to Settings.ACTION_SOUND_SETTINGS,
            "security" to Settings.ACTION_SECURITY_SETTINGS,
            "date" to Settings.ACTION_DATE_SETTINGS,
            "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            "locale" to Settings.ACTION_LOCALE_SETTINGS,
            "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            "apps" to Settings.ACTION_APPLICATION_SETTINGS,
            "accounts" to Settings.ACTION_SYNC_SETTINGS,
            "about" to Settings.ACTION_DEVICE_INFO_SETTINGS,
            "settings" to Settings.ACTION_SETTINGS,
            "input" to Settings.ACTION_INPUT_METHOD_SETTINGS,
            "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
            // Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, public from Android 5.1; the
            // screen is there on 5.0 under the same action.
            "notificationaccess" to "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
        )
    }
}

/**
 * What [LunacyService] asks of the shell. [requestPermissions] is the shell's because a
 * runtime permission is asked for by an Activity, and answered to it, with whether Android
 * would put the question again (from Android 11 a refusal given twice is final, and later asks
 * are refused without a dialog); [startForResult] likewise, for the system dialogs that must be
 * started for a result (the home role).
 */
interface ShellHooks {
    /** The display's facts, for system/getEnvironment. */
    fun display(): JSONObject
    fun requestPermissions(permissions: List<String>, answer: (granted: Boolean, askAgain: Boolean) -> Unit)
    fun startForResult(intent: Intent): Boolean
    /** The layout setting changed: the shell restarts to take it up (ShellActivity.recreate). */
    fun onLayoutChanged()
    /** How to read the wallpaper the shell shows, for [HostWallpaper]; asked on the main thread. */
    fun wallpaper(): () -> java.io.InputStream
    /** First Use has just finished: what a webOS device did at the next boot follows (ShellActivity.afterFirstUse). */
    fun onFirstUseDone()
    /** The apps the user has hidden from the launcher. */
    val hidden: HiddenApps?
    /** Every app the launcher could show, hidden or not: webOS, the launch points they added, then Android's. Asked on the main thread. */
    fun launcherApps(): List<AppInfo>
    /** An app's launcher icon, as the launcher draws it. Asked on the main thread. */
    fun appIcon(app: AppInfo): android.graphics.Bitmap?
    /** Whether Lunacy is the home app, and whether the home app takes pins: {lunacyIsHome, canPin}. */
    fun pinStatus(): JSONObject
    /** Asks Android's home app to pin a webOS app; null when asked, else what went wrong. */
    fun pinToHome(app: AppInfo, icon: android.graphics.Bitmap?): String?
}
