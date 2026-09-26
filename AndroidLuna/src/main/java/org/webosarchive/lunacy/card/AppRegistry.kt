package org.webosarchive.lunacy.card

import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream

/** An installed webOS app, from its appinfo.json. */
class AppInfo(
    val id: String,
    /**
     * The app's folder under /usr/palm/applications. Usually its id, but webOS took the id
     * from appinfo.json and the folder could be named otherwise: the TouchPad's Calculator is
     * com.palm.calculator in com.palm.app.calculator.
     */
    val dir: String = id,
    val title: String,
    val main: String,
    val icon: String,
    val noWindow: Boolean,
    /** "web", or "pdk"/"game" for native apps, which can't run until the PDK layer exists. */
    val type: String,
    val version: String,
    /** Installed from a package (webOS's userInstalled), rather than bundled with Lunacy. */
    val userInstalled: Boolean,
    /**
     * appinfo.json's `visible`. False means the app has no launcher icon: it is part of the
     * platform and another app launches it, as Palm's Video Player is. It still runs, still
     * answers `listApps` and can still be launched by id, exactly as on a device.
     */
    val visible: Boolean,
    /**
     * A Lunacy extension to appinfo.json, only ever used by apps Lunacy ships: the app is an
     * icon that opens one of Android's settings screens (LunacyService.PANELS), because the
     * setting belongs to the host OS and Lunacy would only be pretending to own it. Launching
     * it opens no window. See Docs/architecture.md, "Settings".
     */
    val androidSettings: String,
    /**
     * appinfo.json's `splashicon`: the icon the shell shows on the placeholder card while the
     * app is still loading. Empty if the app doesn't ship one, and then the launcher icon
     * stands in - see [Luna.splashIcon].
     */
    val splashIcon: String,
    /**
     * appinfo.json's `uiRevision`, 1 unless the app says 2 (LunaSysMgr clamps it to that
     * range: `ApplicationDescription.cpp`). It is how an app says which screen it was written
     * for. An app that doesn't say 2 was written for a Pre-sized screen, and a TouchPad ran it
     * in a phone-sized card rather than stretching it across the tablet - see
     * [AppInfo.emulated].
     */
    val uiRevision: Int,
    /** appinfo.json's category and keywords, which decide the launcher page an app lands on. */
    val category: String,
    val keywords: List<String>,
    /** The app's appinfo.json as packaged. */
    val appinfo: JSONObject,
    private val files: AppFiles,
    /** Set for an Android app shown in the launcher (shell/AndroidApps.kt); null for webOS apps. */
    val androidComponent: android.content.ComponentName? = null,
    private val androidIcon: (() -> InputStream?)? = null,
) {
    /**
     * Whether this app runs in the phone-sized card a TouchPad gave an app that never said it
     * had been laid out for a tablet - LunaSysMgr's `Window::Type_Emulated_Card`.
     */
    val emulated get() = uiRevision < 2

    /** The URL of the app's main page, at its webOS path on its own origin. */
    val url get() = AppServer.appUrl(id, main, dir)
    val isWeb get() = type == "web"
    fun openIcon(): InputStream? = androidIcon?.invoke() ?: files.open("${Packages.APPS}/$dir/$icon")
    /** appinfo.json's `miniicon`, "miniicon.png" when it names none (ApplicationDescription). */
    fun openMiniIcon(): InputStream? = if (androidIcon != null) androidIcon.invoke() else files.open("${Packages.APPS}/$dir/" + appinfo.optString("miniicon", "miniicon.png").ifEmpty { "miniicon.png" })
    fun openSplashIcon(): InputStream? =
        if (splashIcon.isEmpty()) null else files.open("${Packages.APPS}/$dir/$splashIcon")

    /** The app's main page as a file:// path, the form webOS's bus answers with. */
    fun filePath(): String = "file:///media/cryptofs/apps/${Packages.APPS}/$dir/$main"

    /** This app's entry in applicationManager/listApps, with the fields a TouchPad returns. */
    fun listEntry(): JSONObject {
        val path = "/media/cryptofs/apps/${Packages.APPS}/$dir/"
        val j = JSONObject()
            .put("id", id).put("main", "file://$path$main").put("version", version)
            .put("category", appinfo.optString("category", "")).put("title", title)
            .put("appmenu", appinfo.optString("appmenu", title)).put("vendor", appinfo.optString("vendor", ""))
            .put("vendorUrl", appinfo.optString("vendorurl", "")).put("size", 0).put("icon", path + icon)
            .put("removable", userInstalled).put("userInstalled", userInstalled).put("hasAccounts", false)
        appinfo.optJSONObject("universalSearch")?.let { j.put("universalSearch", it) }
        if (appinfo.has("uiRevision")) j.put("uiRevision", appinfo.opt("uiRevision"))
        return j.put("tapToShareSupported", false)
    }
}

/**
 * The files under /media/cryptofs/apps: packages installed on the device (Packages.root)
 * first, then apps in the webOS root's /usr/palm/applications - where a package's install
 * script puts a system app, as the webOS Community Account Manager's does - then the apps
 * bundled in the APK's assets/apps/. Every app is served at its /media/cryptofs/apps path.
 */
class AppFiles(private val assets: AssetManager, val root: File, private val system: File? = null, private val apk: File? = null) {
    private val rootPath = root.canonicalPath + File.separator
    private val systemPath = system?.let { it.canonicalPath + File.separator }

    /**
     * The bundled apps' folders, from one read of the APK's index. AssetManager.list reads the
     * APK's whole index on every call, and the configurator lists five folders for every app
     * on the main thread - long enough for Android to report Lunacy as not responding at
     * startup. The bundled apps can't change while Lunacy runs, so the index is read once
     * ([warm], off the main thread) and every listing comes from it.
     */
    @Volatile private var assetIndex: Map<String, List<String>>? = null

    /** Reads the index now; call it off the main thread before the first rescan. */
    fun warm() { index() }

    private fun index(): Map<String, List<String>> = assetIndex ?: synchronized(this) {
        assetIndex ?: readIndex().also { assetIndex = it }
    }

    private fun readIndex(): Map<String, List<String>> {
        val dirs = HashMap<String, LinkedHashSet<String>>()
        val t = System.currentTimeMillis()
        try {
            java.util.zip.ZipFile(apk ?: return emptyMap()).use { zip ->
                for (e in zip.entries()) {
                    val name = e.name
                    if (!name.startsWith("assets/apps/")) continue
                    // Every folder on the way down lists the next name.
                    val parts = name.removePrefix("assets/").trimEnd('/').split('/')
                    for (i in 1 until parts.size) {
                        dirs.getOrPut(parts.subList(0, i).joinToString("/")) { LinkedHashSet() }.add(parts[i])
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(AppServer.TAG, "can't read the APK's index: $e"); return emptyMap()
        }
        Log.i(AppServer.TAG, "bundled apps indexed in ${System.currentTimeMillis() - t} ms")
        return dirs.mapValues { it.value.toList() }
    }

    private fun assetList(path: String): List<String> =
        if (apk == null) assets.list(path)?.toList().orEmpty() else index()[path].orEmpty()

    /** rel is relative to /media/cryptofs/apps, e.g. "usr/palm/applications/<id>/index.html". */
    fun open(rel: String): InputStream? {
        val f = File(root, rel)
        if (f.isFile && f.canonicalPath.startsWith(rootPath)) return f.inputStream()
        if (!rel.startsWith("${Packages.APPS}/")) return null
        if (system != null) {
            val s = File(system, rel)
            if (s.isFile && s.canonicalPath.startsWith(systemPath!!)) return s.inputStream()
        }
        return try { assets.open("apps/" + rel.removePrefix("${Packages.APPS}/")) } catch (e: IOException) { null }
    }

    fun isInstalled(id: String) = File(root, "${Packages.APPS}/$id/appinfo.json").isFile

    /**
     * The names in a folder under /media/cryptofs/apps, installed packages, system apps and
     * bundled apps merged, the way [open] merges files. A bundled app's configuration lives in
     * the APK, so anything that walks an app's folders has to look in all of them.
     */
    fun list(rel: String): List<String> {
        val installed = File(root, rel).list().orEmpty().toList()
        val sys = if (system != null && rel.startsWith("${Packages.APPS}/")) File(system, rel).list().orEmpty().toList() else emptyList()
        val bundled = if (rel.startsWith("${Packages.APPS}/"))
            assetList("apps/" + rel.removePrefix("${Packages.APPS}/"))
        else emptyList()
        return (installed + sys + bundled).distinct().sorted()
    }

    fun appIds(): List<String> =
        (File(root, Packages.APPS).list().orEmpty().toList() +
            (system?.let { File(it, Packages.APPS).list() }.orEmpty()) +
            assetList("apps")).distinct().sorted()
}

/** Installed apps: bundled apps and installed .ipks, reloaded after each install. */
class AppRegistry(private val files: AppFiles) {
    @Volatile var apps: List<AppInfo> = load()
        private set

    fun reload() { apps = load() }

    fun get(id: String) = apps.firstOrNull { it.id == id }

    /** The app in a folder under /usr/palm/applications, as a package unpacked it. */
    fun inDir(dir: String) = apps.firstOrNull { it.dir == dir }

    /** The apps with an icon: what the launcher and the dock draw. See [AppInfo.visible]. */
    val launchPoints get() = apps.filter { it.visible }

    /** An id found in two folders is the first's, as [AppFiles] lists installed packages first. */
    private fun load(): List<AppInfo> = files.appIds().mapNotNull { read(it) }.distinctBy { it.id }

    private fun read(dir: String): AppInfo? = try {
        val text = files.open("${Packages.APPS}/$dir/appinfo.json")?.use { it.bufferedReader().readText() } ?: return null
        // Some packaged appinfo.json files start with a BOM, which webOS tolerated.
        val j = JSONObject(text.trimStart('\uFEFF'))
        AppInfo(
            // webOS launched an app by appinfo.json's id, not by its folder's name.
            id = j.optString("id").ifEmpty { dir },
            dir = dir,
            title = j.optString("title", dir),
            main = j.optString("main", "index.html"),
            icon = j.optString("icon", "icon.png"),
            noWindow = j.optBoolean("noWindow", false),
            type = j.optString("type", "web"),
            version = j.optString("version", ""),
            androidSettings = j.optString("lunacyAndroidSettings", ""),
            // Palm spells it all lower case; every app of theirs that has one writes it so.
            splashIcon = j.optString("splashicon", j.optString("splashIcon", "")),
            // Palm's own apps write it as a number and some write it as a string, so read
            // either, and clamp to 1..2 as LunaSysMgr does.
            uiRevision = (j.opt("uiRevision")?.let { r ->
                (r as? Number)?.toInt() ?: r.toString().trim().toIntOrNull()
            } ?: 1).coerceIn(1, 2),
            category = j.optString("category", ""),
            keywords = j.optJSONArray("keywords")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            userInstalled = files.isInstalled(dir),
            // Palm wrote it as the string "false"; optBoolean reads either spelling.
            visible = j.optBoolean("visible", true),
            appinfo = j,
            files = files,
        )
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "appinfo.json of $dir unreadable: $e")
        null
    }
}
