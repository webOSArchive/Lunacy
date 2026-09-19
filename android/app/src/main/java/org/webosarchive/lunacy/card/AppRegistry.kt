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
    /** The app's appinfo.json as packaged. */
    val appinfo: JSONObject,
    private val files: AppFiles,
) {
    /** The URL of the app's main page, at its webOS path on its own origin. */
    val url get() = AppServer.appUrl(id, main)
    val isWeb get() = type == "web"
    fun openIcon(): InputStream? = files.open("${Packages.APPS}/$id/$icon")

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

    fun appIds(): List<String> =
        (File(root, Packages.APPS).list().orEmpty().toList() + assets.list("apps").orEmpty()).distinct().sorted()
}

/** Installed apps: bundled apps and installed .ipks, reloaded after each install. */
class AppRegistry(private val files: AppFiles) {
    @Volatile var apps: List<AppInfo> = load()
        private set

    fun reload() { apps = load() }

    fun get(id: String) = apps.firstOrNull { it.id == id }

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
            userInstalled = files.isInstalled(dir),
            appinfo = j,
            files = files,
        )
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "appinfo.json of $dir unreadable: $e")
        null
    }
}
