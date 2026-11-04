package org.webosarchive.lunacy.card

import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * `/media/internal`, webOS's user storage.
 *
 * On a device this is where the user's own files lived - `downloads`, `music`, `ringtones`,
 * the wallpapers - and apps read and wrote it freely. Lunacy keeps its own copy of the tree
 * (JS services write there, and the shipped wallpapers land there), but the files a person
 * actually has are in Android's shared storage, so the folders a webOS device had are mapped
 * onto Android's. Papyrus's ePub import, the file picker and the media server all see them.
 *
 * Nothing outside those folders is reachable: every path is resolved and then checked against
 * the roots below, so `..` can't climb out.
 *
 * **Android's shared storage (ratchet item).** Reading it needs READ_EXTERNAL_STORAGE, which
 * Android 5 grants at install. From API 23 the user has to be asked, and scoped storage
 * narrows it further on later versions; until then apps see what they saw on webOS.
 */
object UserFiles {
    /** The folders a webOS device had, and Android's equivalent. */
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

    /** Lunacy's own /media/internal. */
    fun own(webosRoot: File): File = File(webosRoot, "media/internal")

    /** A mapped folder's real directory on Android, if that folder exists. */
    fun androidDir(name: String): File? =
        MAPPED[name.lowercase()]?.let { Environment.getExternalStoragePublicDirectory(it) }?.takeIf { it.isDirectory }

    /** The mapped folders this device actually has, as they appear under /media/internal. */
    fun mappedFolders(): List<Pair<String, File>> =
        MAPPED.keys.sorted().mapNotNull { name -> androidDir(name)?.let { name to it } }

    /**
     * Resolves a path relative to /media/internal to a real file, or null if it climbs out of
     * the tree. A first segment that names a mapped folder, and that Lunacy's own tree hasn't
     * got, resolves into Android's storage.
     */
    fun resolve(webosRoot: File, relative: String): File? {
        val rel = relative.trimStart('/')
        val mine = File(own(webosRoot), rel)
        if (mine.exists() && inside(mine, own(webosRoot))) return mine
        val first = rel.substringBefore('/')
        val rest = rel.substringAfter('/', "")
        val dir = androidDir(first) ?: return mine.takeIf { inside(it, own(webosRoot)) }
        val f = if (rest.isEmpty()) dir else File(dir, rest)
        return f.takeIf { inside(it, dir) }
    }

    /** The webOS path (under /media/internal) a real file has, or null if it isn't in the tree. */
    fun webosPath(webosRoot: File, file: File): String? {
        val canonical = file.canonicalPath
        val mine = own(webosRoot).canonicalPath
        if (canonical.startsWith(mine + File.separator)) {
            return "/media/internal/" + canonical.removePrefix(mine + File.separator)
        }
        // The most specific folder: a wallpaper is /media/internal/wallpapers/x, not photos/webOS Wallpapers/x.
        for ((name, dir) in mappedFolders().sortedByDescending { it.second.canonicalPath.length }) {
            val root = dir.canonicalPath
            if (canonical == root) return "/media/internal/$name"
            if (canonical.startsWith(root + File.separator)) {
                return "/media/internal/$name/" + canonical.removePrefix(root + File.separator)
            }
        }
        return null
    }

    /**
     * A mapped folder inside another (wallpapers in Android's Pictures, which is photos): a
     * walk of the outer one skips it, so its files are listed once, under their own name.
     */
    fun nestedRoot(dir: File): Boolean {
        val c = runCatching { dir.canonicalPath }.getOrNull() ?: return false
        val roots = mappedFolders().map { it.second.canonicalPath }
        return c in roots && roots.any { it != c && c.startsWith(it + File.separator) }
    }

    /**
     * Every file in /media/internal as [record] makes it, the way filenotifyd walked user
     * storage: Lunacy's own tree first (where a mapped folder's name exists there too, that copy
     * is the one a path names), then each of Android's mapped folders; names in order, hidden
     * files and folders left out, a folder mapped inside another walked once, each path once,
     * [MAX_DEPTH] deep and [MAX_FILES] records at most. [record] gives null for a file it
     * doesn't index.
     */
    fun walkMedia(webosRoot: File, record: (path: String, f: File) -> JSONObject?): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val paths = HashSet<String>()
        fun walk(dir: File, path: String, depth: Int) {
            if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
            val entries = dir.listFiles() ?: return
            for (f in entries.sortedBy { it.name }) {
                if (f.name.startsWith(".")) continue
                val p = "$path/${f.name}"
                if (f.isDirectory) { if (!nestedRoot(f)) walk(f, p, depth + 1); continue }
                if (!f.isFile || p in paths) continue
                val r = record(p, f) ?: continue
                paths += p
                out += r
                if (out.size >= MAX_FILES) return
            }
        }
        walk(own(webosRoot), "/media/internal", 0)
        for ((name, dir) in mappedFolders()) walk(dir, "/media/internal/$name", 0)
        return out
    }

    private const val MAX_DEPTH = 12
    private const val MAX_FILES = 5000

    /**
     * Whether [f] is [root] or inside it, by their canonical paths: a link or a ".." can't take
     * it out, and a sibling whose name starts the same ("apps2" beside "apps") isn't inside.
     * The one check every webOS path from a page or a service goes through.
     */
    fun inside(f: File, root: File): Boolean = try {
        val c = f.canonicalPath
        val r = root.canonicalPath
        c == r || c.startsWith(r + File.separator)
    } catch (e: Exception) { false }
}
