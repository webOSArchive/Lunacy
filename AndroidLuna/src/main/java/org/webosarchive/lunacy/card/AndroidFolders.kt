package org.webosarchive.lunacy.card

import android.os.Environment
import org.webosarchive.lunacy.core.HostFolders
import java.io.File

/**
 * The folders a webOS device had under /media/internal, and Android's equivalents: what
 * [UserFiles] maps into the webOS tree (LunaCore's [HostFolders]).
 *
 * **Android's shared storage (ratchet item).** Reading it needs READ_EXTERNAL_STORAGE, which
 * Android 5 grants at install. From API 23 the user has to be asked, and scoped storage
 * narrows it further on later versions; until then apps see what they saw on webOS.
 */
object AndroidFolders : HostFolders {
    private val MAPPED: Map<String, String> = mapOf(
        "downloads" to Environment.DIRECTORY_DOWNLOADS,
        "music" to Environment.DIRECTORY_MUSIC,
        "photos" to Environment.DIRECTORY_PICTURES,
        "documents" to "Documents",
        "camera" to Environment.DIRECTORY_DCIM,
        "video" to Environment.DIRECTORY_MOVIES,
        // webOS's ringtones are Android notification sounds, in a folder of their own (Ringtones).
        "ringtones" to Ringtones.ANDROID_DIR,
        // webOS's wallpapers, in Android's pictures under a folder of their own (SharedMedia).
        "wallpapers" to SharedMedia.WALLPAPERS_DIR,
    )

    override val names: List<String> get() = MAPPED.keys.toList()
    override fun dir(name: String): File? = MAPPED[name]?.let { Environment.getExternalStoragePublicDirectory(it) }

    /**
     * What a user's Save a Log needs to tell why pictures are missing (LogExport), counts only:
     * each mapped folder's Android path, whether Lunacy can read it and how many entries it
     * has; the indexes' last results; whether the device has removable storage (an SD card,
     * whose DCIM isn't mapped); and how many HEIC pictures DCIM/Camera holds (the index takes
     * JPEG, PNG and BMP).
     */
    fun diagnose(context: android.content.Context): String {
        val sb = StringBuilder()
        for (name in MAPPED.keys.sorted()) {
            val dir = Environment.getExternalStoragePublicDirectory(MAPPED.getValue(name))
            val entries = dir.listFiles()
            sb.append("$name -> ${dir.path}: ").append(when {
                !dir.exists() -> "no such folder"
                entries == null -> "can't be read"
                else -> "${entries.size} entries"
            }).append('\n')
        }
        for ((k, v) in UserFiles.lastSummaries()) sb.append("last $k: $v\n")
        val volumes = context.getExternalFilesDirs(null).count { it != null }
        sb.append("storage volumes: $volumes").append(if (volumes > 1) " (removable storage present: its DCIM isn't mapped)" else "").append('\n')
        val camera = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera").listFiles()
        if (camera != null) sb.append("DCIM/Camera: ${camera.size} entries, ${camera.count { it.name.lowercase().let { n -> n.endsWith(".heic") || n.endsWith(".heif") } }} HEIC\n")
        return sb.toString()
    }
}
