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
 * - `android/openSettings` hands a setting that belongs to the host OS to Android's own UI.
 * - `permissions/status` and `permissions/request` are Android's runtime permissions, for
 *   Lunacy's First Use; `firstUse/done` is how First Use says it has run.
 *
 * [requestPermissions] is the shell's: a runtime permission is asked for by an Activity, and
 * answered to it, with whether Android would put the question again (from Android 11 a
 * refusal given twice is final, and later asks are refused without a dialog). [startForResult]
 * is the shell's too, for the system dialogs that must be started for a result (the home
 * role).
 */
class LunacyService(
    private val context: Context,
    private val registry: AppRegistry,
    private val jsServices: JsServices,
    private val webosRoot: File,
    private val display: () -> JSONObject,
    private val requestPermissions: (List<String>, (granted: Boolean, askAgain: Boolean) -> Unit) -> Unit,
    private val startForResult: (Intent) -> Boolean,
    /** The layout setting changed: the shell restarts to take it up (ShellActivity.recreate). */
    private val onLayoutChanged: () -> Unit = {},
    /** How to read the wallpaper the shell shows, for [HostWallpaper]; asked on the main thread. */
    private val wallpaper: () -> () -> java.io.InputStream = { { throw java.io.FileNotFoundException("no wallpaper") } },
) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** The two answers that take real time - Node's version is asked of Node, and a listing walks a folder - come from here. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun register(bus: Bus) {
        bus.register(SERVICE, "system/getEnvironment") { _, _, reply -> worker.execute { val r = environment(); main.post { reply(r) } } }
        bus.register(SERVICE, "files/list") { _, p, reply -> worker.execute { val r = runCatching { listFiles(p) }.getOrElse { Bus.error("files/list: $it") }; main.post { reply(r) } } }
        bus.register(SERVICE, "system/setDeviceId") { caller, p, reply -> reply(setDeviceId(caller, p)) }
        bus.register(SERVICE, "system/setLayout") { caller, p, reply -> reply(setLayout(caller, p)) }
        bus.register(SERVICE, "system/setFixedViewport") { caller, p, reply -> reply(setFixedViewport(caller, p)) }
        bus.register(SERVICE, "system/setHostWallpaper") { caller, p, reply -> setHostWallpaper(caller, p, reply) }
        bus.register(SERVICE, "android/openSettings") { _, p, reply -> reply(openSettings(p.optString("panel"))) }
        bus.register(SERVICE, "android/settingsPanels") { _, _, reply ->
            reply(Bus.ok(mapOf("panels" to org.json.JSONArray(PANELS.keys.sorted()))))
        }
        bus.register(SERVICE, "permissions/status") { _, _, reply -> reply(permissionsStatus()) }
        bus.register(SERVICE, "permissions/request") { caller, p, reply -> requestPermission(caller, p, reply) }
        bus.register(SERVICE, "firstUse/done") { caller, _, reply -> reply(firstUseDone(caller)) }
    }

    // ---- Android's permissions, for First Use ----

    /**
     * What Lunacy may do on this device. Each permission says whether Android asks for it at
     * all (`asked`: from Android 6; Android 5 granted everything at install) and whether it
     * is granted now. `storage` is the shared storage behind /media/internal;
     * `systemSettings` is "Modify system settings", which Screen & Lock's brightness needs;
     * `homeLauncher` is whether Lunacy is the device's home screen, which is offered on
     * every Android version and is the owner's choice rather than a permission.
     */
    private fun permissionsStatus(): String {
        val asked = Build.VERSION.SDK_INT >= 23
        return Bus.ok(mapOf(
            "storage" to JSONObject().put("asked", asked).put("granted", storageGranted()),
            "systemSettings" to JSONObject().put("asked", asked).put("granted", systemSettingsGranted()),
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

    private fun storageGranted(): Boolean = STORAGE.all {
        context.checkPermission(it, android.os.Process.myPid(), android.os.Process.myUid()) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun systemSettingsGranted(): Boolean = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(context)

    /**
     * Asks Android. Storage is a dialog, answered when the user has: the reply waits for it,
     * and says whether Android would ask again (`askAgain`); once it won't, the only way left
     * is Android's own settings screen for Lunacy, which `viaSettings` opens instead.
     * "Modify system settings" has no dialog; Android grants it only on its own screen, which
     * is opened, and the reply says so (`opened`): the caller sees the grant in the next
     * `permissions/status`. Only Lunacy's own apps may ask: an installed app has no business
     * putting Android's dialogs in front of the user.
     */
    private fun requestPermission(caller: String, p: JSONObject, reply: (String) -> Unit) {
        if (!ownApp(caller)) {
            reply(Bus.error("Only Lunacy's own apps can ask for Android's permissions (asked by $caller)", -1)); return
        }
        when (val which = p.optString("permission")) {
            "storage" -> {
                if (storageGranted()) { reply(Bus.ok(mapOf("permission" to which, "granted" to true))); return }
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
                requestPermissions(STORAGE) { granted, askAgain ->
                    reply(Bus.ok(mapOf("permission" to which, "granted" to granted, "askAgain" to askAgain)))
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
                        startForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                    } else false
                } else false
                val ok = opened || runCatching {
                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
                }.getOrElse { Log.w(AppServer.TAG, "permissions: can't open the home settings", it); false }
                reply(if (ok) Bus.ok(mapOf("permission" to which, "granted" to false, "opened" to true))
                      else Bus.error("Couldn't open Android's home screen choice"))
            }
            else -> reply(Bus.error("No permission \"$which\": storage, systemSettings or homeLauncher"))
        }
    }

    /** First Use has run. The shell won't launch it again, and asks for storage itself from then on if it is missing. */
    private fun firstUseDone(caller: String): String {
        if (!ownApp(caller)) return Bus.error("Only Lunacy's own apps can finish First Use (asked by $caller)", -1)
        setFirstUseDone(context)
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
        j.put("display", display())
        j.put("layout", FormFactor.describe(context).put("appLayoutWidth", FormFactor.appLayoutWidth(context)))
        j.put("hostWallpaper", HostWallpaper.isOn(context))
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
     * shell is showing restarts the shell, once the reply is on its way; the width is taken
     * up when an app next loads a page.
     */
    private fun setLayout(caller: String, p: JSONObject): String {
        if (!ownApp(caller)) return Bus.error("Only Lunacy's own apps can set the layout (asked by $caller)", -1)
        val before = FormFactor.of(context)
        if (p.has("layout") && !FormFactor.setSetting(context, p.optString("layout"))) {
            return Bus.error("layout is auto, phone or tablet")
        }
        if (p.has("appLayoutWidth")) FormFactor.setAppLayoutWidth(context, p.optInt("appLayoutWidth"))
        val restart = FormFactor.of(context) != before
        if (restart) main.postDelayed({ onLayoutChanged() }, RESTART_DELAY_MS)
        return Bus.ok(mapOf("layout" to FormFactor.describe(context).put("appLayoutWidth", FormFactor.appLayoutWidth(context)), "restart" to restart))
    }

    /**
     * The fixed-viewport fallback for one app (FixedViewport): `id`, and `on` true or false,
     * or absent to put the app back on its default. Lunacy's own apps only. Takes effect
     * when the app's window next loads a page.
     */
    private fun setFixedViewport(caller: String, p: JSONObject): String {
        if (!ownApp(caller)) return Bus.error("Only Lunacy's own apps can set an app's viewport (asked by $caller)", -1)
        val id = p.optString("id")
        val app = registry.get(id) ?: return Bus.error("No app $id")
        FixedViewport.set(context, id, if (p.has("on")) p.optBoolean("on") else null)
        return Bus.ok(mapOf("id" to id, "on" to FixedViewport.isOn(context, id), "default" to FixedViewport.default(id), "title" to app.title))
    }

    /**
     * Whether Android's wallpaper follows webOS's ([HostWallpaper]): `on` true or false.
     * Lunacy's own apps only. Turning it on sets Android's wallpaper straight away, and the
     * reply waits for that, so a device that refuses says so.
     */
    private fun setHostWallpaper(caller: String, p: JSONObject, reply: (String) -> Unit) {
        if (!ownApp(caller)) return reply(Bus.error("Only Lunacy's own apps can set Android's wallpaper (asked by $caller)", -1))
        if (!p.has("on")) return reply(Bus.error("on is required"))
        val on = p.optBoolean("on")
        if (!on) {
            HostWallpaper.setOn(context, false)
            return reply(Bus.ok(mapOf("on" to false)))
        }
        val open = wallpaper()
        worker.execute {
            val failed = HostWallpaper.apply(context, open)
            if (failed == null) HostWallpaper.setOn(context, true)
            main.post { reply(failed?.let { Bus.error(it) } ?: Bus.ok(mapOf("on" to true))) }
        }
    }

    private fun setDeviceId(caller: String, p: JSONObject): String {
        if (!ownApp(caller)) {
            return Bus.error("Only Lunacy's own apps can set the device id (asked by $caller)", -1)
        }
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
    private fun ownApp(appId: String): Boolean {
        val known = appId == "com.palm.app.deviceinfo" || appId == "com.palm.app.screenlock" || appId.startsWith("org.webosarchive.lunacy")
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
        val internal = path.removePrefix("/media/internal").trimStart('/')
        val root = if (path.trimEnd('/') == "/media/internal" || path.startsWith("/media/internal/")) {
            UserFiles.resolve(webosRoot, internal)
        } else File(webosRoot, path.trimStart('/')).takeIf { it.canonicalPath.startsWith(webosRoot.canonicalPath + File.separator) || it.canonicalPath == webosRoot.canonicalPath }
        if (root == null || !root.isDirectory) return Bus.error("No such folder: $path")
        // Lunacy's own folders, then the Android ones mapped in beside them (UserFiles), which
        // is where the files a person actually has live.
        val subfolders = (root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()) +
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

    private fun rootName(path: String) = path.trimEnd('/').substringAfterLast('/').ifEmpty { "Files" }

    /** The path an app sees: a webOS one, whichever side of the mapping the file is really on. */
    private fun webosPath(f: File): String =
        UserFiles.webosPath(webosRoot, f) ?: ("/" + f.canonicalPath.removePrefix(webosRoot.canonicalPath).trimStart('/'))

    /** webOS's attachment types, by extension: what a FilePicker's fileType filters on. */
    private fun typeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "image"
        "mp3", "m4a", "aac", "wav", "ogg", "oga", "flac" -> "audio"
        "mp4", "m4v", "mov", "avi", "mkv", "webm", "3gp" -> "video"
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "html", "htm",
        "epub", "mobi", "prc", "azw", "fb2", "cbz", "cbr", "csv", "md" -> "document"
        else -> "other"
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
        private val STORAGE = listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        /** Long enough for the setting app's reply to land and its selector to close before the shell goes. */
        const val RESTART_DELAY_MS = 400L
        fun firstUseDone(context: Context): Boolean =
            context.getSharedPreferences("device", Context.MODE_PRIVATE).getBoolean("firstUseDone", false)
        fun setFirstUseDone(context: Context) {
            context.getSharedPreferences("device", Context.MODE_PRIVATE).edit().putBoolean("firstUseDone", true).apply()
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
            "display" to Settings.ACTION_DISPLAY_SETTINGS,
            "sound" to Settings.ACTION_SOUND_SETTINGS,
            "security" to Settings.ACTION_SECURITY_SETTINGS,
            "date" to Settings.ACTION_DATE_SETTINGS,
            "locale" to Settings.ACTION_LOCALE_SETTINGS,
            "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            "apps" to Settings.ACTION_APPLICATION_SETTINGS,
            "accounts" to Settings.ACTION_SYNC_SETTINGS,
            "about" to Settings.ACTION_DEVICE_INFO_SETTINGS,
            "settings" to Settings.ACTION_SETTINGS,
            "input" to Settings.ACTION_INPUT_METHOD_SETTINGS,
            "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
        )
    }
}
