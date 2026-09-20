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
 * honest about which is which (docs/architecture.md, "Luna bus").
 *
 * - `system/getEnvironment` reports what Lunacy actually runs on, for Lunacy's Device Info.
 * - `android/openSettings` hands a setting that belongs to the host OS to Android's own UI.
 */
class LunacyService(
    private val context: Context,
    private val registry: AppRegistry,
    private val jsServices: JsServices,
    private val webosRoot: File,
    private val display: () -> JSONObject,
) {
    fun register(bus: Bus) {
        bus.register(SERVICE, "system/getEnvironment") { _, _, reply -> reply(environment()) }
        bus.register(SERVICE, "files/list") { _, p, reply -> reply(listFiles(p)) }
        bus.register(SERVICE, "android/openSettings") { _, p, reply -> reply(openSettings(p.optString("panel"))) }
        bus.register(SERVICE, "android/settingsPanels") { _, _, reply ->
            reply(Bus.ok(mapOf("panels" to org.json.JSONArray(PANELS.keys.sorted()))))
        }
    }

    // ---- the environment ----

    private fun environment(): String {
        val j = JSONObject().put("returnValue", true)
        j.put("lunacy", JSONObject()
            .put("version", versionName()).put("build", versionCode())
            .put("package", context.packageName)
            .put("reportsWebOS", SystemProperties.WEBOS_VERSION)
            .put("enyo", "1.0")
            .put("node", nodeVersion())
            .put("apps", registry.apps.size)
            .put("services", jsServices.names()))
        j.put("android", JSONObject()
            .put("release", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("build", Build.DISPLAY).put("abi", abi()))
        j.put("device", JSONObject()
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("serial", serial()).put("ram", totalRam())
            .put("storageTotal", storage(true)).put("storageFree", storage(false))
            .put("battery", battery()).put("charging", charging()))
        j.put("webview", webView())
        j.put("display", display())
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
        } else File(webosRoot, path.trimStart('/')).takeIf { it.canonicalPath.startsWith(webosRoot.canonicalPath) }
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
