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
) {
    /**
     * Whether this app runs in the phone-sized card a TouchPad gave an app that never said it
     * had been laid out for a tablet - LunaSysMgr's `Window::Type_Emulated_Card`.
     */
    val emulated get() = uiRevision < 2

    /** The URL of the app's main page, at its webOS path on its own origin. */
    val url get() = AppServer.appUrl(id, main)
    val isWeb get() = type == "web"
    fun openIcon(): InputStream? = files.open("${Packages.APPS}/$id/$icon")
    fun openSplashIcon(): InputStream? =
        if (splashIcon.isEmpty()) null else files.open("${Packages.APPS}/$id/$splashIcon")

    /** The app's main page as a file:// path, the form webOS's bus answers with. */
    fun filePath(): String = "file:///media/cryptofs/apps/${Packages.APPS}/$id/$main"

    /** This app's entry in applicationManager/listApps, with the fields a TouchPad returns. */
    fun listEntry(): JSONObject {
        val dir = "/media/cryptofs/apps/${Packages.APPS}/$id/"
        val j = JSONObject()
            .put("id", id).put("main", "file://$dir$main").put("version", version)
            .put("category", appinfo.optString("category", "")).put("title", title)
            .put("appmenu", appinfo.optString("appmenu", title)).put("vendor", appinfo.optString("vendor", ""))
            .put("vendorUrl", appinfo.optString("vendorurl", "")).put("size", 0).put("icon", dir + icon)
            .put("removable", userInstalled).put("userInstalled", userInstalled).put("hasAccounts", false)
        appinfo.optJSONObject("universalSearch")?.let { j.put("universalSearch", it) }
        if (appinfo.has("uiRevision")) j.put("uiRevision", appinfo.opt("uiRevision"))
        return j.put("tapToShareSupported", false)
    }
}

/**
 * The files under /media/cryptofs/apps: packages installed on the device (Packages.root)
 * first, then the apps bundled in the APK's assets/apps/.
 */
class AppFiles(private val assets: AssetManager, val root: File) {
    private val rootPath = root.canonicalPath + File.separator

    /** rel is relative to /media/cryptofs/apps, e.g. "usr/palm/applications/<id>/index.html". */
    fun open(rel: String): InputStream? {
        val f = File(root, rel)
        if (f.isFile && f.canonicalPath.startsWith(rootPath)) return f.inputStream()
        if (!rel.startsWith("${Packages.APPS}/")) return null
        return try { assets.open("apps/" + rel.removePrefix("${Packages.APPS}/")) } catch (e: IOException) { null }
    }

    fun isInstalled(id: String) = File(root, "${Packages.APPS}/$id/appinfo.json").isFile

    /**
     * The names in a folder under /media/cryptofs/apps, installed packages and bundled apps
     * merged, the way [open] merges files. A bundled app's configuration lives in the APK, so
     * anything that walks an app's folders has to look in both.
     */
    fun list(rel: String): List<String> {
        val installed = File(root, rel).list().orEmpty().toList()
        val bundled = if (rel.startsWith("${Packages.APPS}/"))
            assets.list("apps/" + rel.removePrefix("${Packages.APPS}/")).orEmpty().toList()
        else emptyList()
        return (installed + bundled).distinct().sorted()
    }

    fun appIds(): List<String> =
        (File(root, Packages.APPS).list().orEmpty().toList() + assets.list("apps").orEmpty()).distinct().sorted()
}

/** Installed apps: bundled apps and installed .ipks, reloaded after each install. */
class AppRegistry(private val files: AppFiles) {
    @Volatile var apps: List<AppInfo> = load()
        private set

    fun reload() { apps = load() }

    fun get(id: String) = apps.firstOrNull { it.id == id }

    /** The apps with an icon: what the launcher and the dock draw. See [AppInfo.visible]. */
    val launchPoints get() = apps.filter { it.visible }

    private fun load(): List<AppInfo> = files.appIds().mapNotNull { read(it) }

    private fun read(dir: String): AppInfo? = try {
        val text = files.open("${Packages.APPS}/$dir/appinfo.json")?.use { it.bufferedReader().readText() } ?: return null
        // Some packaged appinfo.json files start with a BOM, which webOS tolerated.
        val j = JSONObject(text.trimStart('\uFEFF'))
        AppInfo(
            id = dir,  // the directory is the id webOS launched it by
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
