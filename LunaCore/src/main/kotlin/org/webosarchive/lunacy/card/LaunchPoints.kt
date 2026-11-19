package org.webosarchive.lunacy.card

import org.webosarchive.lunacy.core.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * The dynamic launch points, one file each in `/var/luna/launchpoints` of the webOS root, named
 * by its id and holding its JSON, as LunaSysMgr kept them (Settings::lunaLaunchPointsPath).
 * A launch point whose app isn't installed is kept but not shown, as LunaSysMgr only loaded the
 * ones it had an app for.
 */
class LaunchPoints(private val webosRoot: File, private val files: AppFiles) {
    private val dir = File(webosRoot, "var/luna/launchpoints")
    @Volatile var all: List<LaunchPoint> = load()
        private set

    fun get(launchPointId: String) = all.firstOrNull { it.launchPointId == launchPointId }

    private fun load(): List<LaunchPoint> = dir.listFiles().orEmpty().sortedBy { it.name }.mapNotNull { f ->
        try {
            val j = JSONObject(f.readText())
            // LaunchPoint::fromJSON: title, icon, params and id are required.
            if (!j.has("title") || !j.has("icon") || !j.has("params") || !j.has("id")) null
            else LaunchPoint(j.getString("id"), f.name, j.getString("title"), j.optString("appmenu", j.getString("title")),
                j.getString("icon"), j.get("params").toString(), j.optBoolean("removable", false))
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "launch point ${f.name} unreadable: $e"); null
        }
    }

    /** ApplicationManager::addLaunchPoint. Returns the new launch point, or null if its file couldn't be written. */
    @Synchronized fun add(app: AppInfo, title: String, appmenu: String, icon: String, params: String?, removable: Boolean): LaunchPoint? {
        dir.mkdirs()
        // findUniqueFileName: a random number from 1 to 1000000 that names no file yet.
        var id: String
        do { id = (1 + (Math.random() * 1000000).toInt()).toString() } while (File(dir, id).exists())
        val lp = LaunchPoint(app.id, id, title, appmenu, icon, params, removable)
        if (!write(lp, app)) return null
        all = all + lp
        return lp
    }

    /** ApplicationManager::removeLaunchPoint: an error's text, or null once it has gone. */
    @Synchronized fun remove(launchPointId: String): String? {
        // Only a dynamic launch point's id is a number; a default one can't be removed this way.
        if (launchPointId.toIntOrNull() == null) return ""
        val lp = get(launchPointId) ?: return "launch point [$launchPointId] not found"
        // LunaSysMgr's own wording.
        if (!lp.removable) return "launch point [$launchPointId] not marked non-removable"
        if (!File(dir, launchPointId).delete()) return "launch point deletion failed"
        all = all - lp
        return null
    }

    /** ApplicationManager::updateLaunchPointIcon: false if the new icon isn't an image. */
    @Synchronized fun updateIcon(lp: LaunchPoint, app: AppInfo?, icon: String): LaunchPoint? {
        val path = icon.removePrefix("file://")
        if (!isImage(path)) return null
        val updated = LaunchPoint(lp.appId, lp.launchPointId, lp.title, lp.appmenu, path, lp.params, lp.removable)
        write(updated, app)
        all = all.map { if (it === lp) updated else it }
        return updated
    }

    private fun write(lp: LaunchPoint, app: AppInfo?): Boolean = try {
        File(dir, lp.launchPointId).writeText(lp.toJson(app).toString()); true
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "can't write launch point ${lp.launchPointId}: $e"); false
    }

    private fun isImage(path: String): Boolean {
        val images = org.webosarchive.lunacy.core.Platform.images ?: return true
        return open(path)?.use { images.size(it) } != null
    }

    /**
     * A file by its webOS path: user storage, an app's own files (which may be in the APK), or
     * anywhere else in the webOS root, such as the Web app's page icons in /var/luna/data.
     */
    fun open(path: String): InputStream? = try {
        val rel = path.removePrefix("file://").trimStart('/')
        when {
            rel.startsWith(AppServer.MEDIA_INTERNAL) ->
                UserFiles.resolve(webosRoot, rel.removePrefix(AppServer.MEDIA_INTERNAL))?.takeIf { it.isFile }?.inputStream()
            rel.startsWith(CRYPTOFS) -> files.open(rel.removePrefix(CRYPTOFS))
            else -> File(webosRoot, rel).takeIf { it.isFile && UserFiles.inside(it, webosRoot) }?.inputStream()
        }
    } catch (e: Exception) { null }

    companion object {
        private const val CRYPTOFS = "media/cryptofs/apps/"
    }
}
