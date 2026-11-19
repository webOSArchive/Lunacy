package org.webosarchive.lunacy.core

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Lunacy's bundled files: the frameworks, the compat layer, the fonts, the bundled apps
 * (apps/<id>/), the webOS root's ROM. On Android they are the APK's assets (AssetManager);
 * on the desktop, folders laid over each other in the same shape ([FolderAssets]).
 */
interface Assets {
    /** The file at [path] (no leading slash); throws IOException if there is none. */
    @Throws(IOException::class)
    fun open(path: String): InputStream

    /** The names in the folder at [path], or null if it isn't one. */
    fun list(path: String): Array<String>?
}

/**
 * [base] without some of what it bundles: whole apps (`apps/<id>`) and paths of the webOS root's
 * ROM (`rootfs/<path>`, dropped from `rootfs.index` and, as links, from `rootfs.links` too). Luna DE leaves out what is Android's
 * side of Lunacy (the Android account and its template, Android's notifications, the shortcuts to
 * Android's settings), which has nothing to stand on at a desktop.
 */
class Without(private val base: Assets, private val apps: Set<String>, private val romPaths: List<String> = emptyList()) : Assets {
    private fun hidden(path: String): Boolean {
        val p = path.trimStart('/')
        if (p.startsWith("apps/")) return p.removePrefix("apps/").substringBefore('/') in apps
        if (p.startsWith("rootfs/")) return romHidden(p.removePrefix("rootfs/"))
        return false
    }

    private fun romHidden(rel: String) = romPaths.any { rel == it || rel.startsWith("$it/") }

    override fun open(path: String): InputStream {
        if (hidden(path)) throw IOException("no asset $path")
        if (path.trimStart('/') == "rootfs.index" && romPaths.isNotEmpty()) {
            val kept = base.open(path).bufferedReader().use { r -> r.readLines().filter { !romHidden(it) } }
            return kept.joinToString("") { it + "\n" }.byteInputStream()
        }
        // "link target" lines: a link that is hidden, or points at what is, isn't made.
        if (path.trimStart('/') == "rootfs.links" && romPaths.isNotEmpty()) {
            val kept = base.open(path).bufferedReader().use { r -> r.readLines().filter { l -> l.split(' ', limit = 2).none { romHidden(it) } } }
            return kept.joinToString("") { it + "\n" }.byteInputStream()
        }
        return base.open(path)
    }

    override fun list(path: String): Array<String>? {
        if (hidden(path)) return null
        val names = base.list(path) ?: return null
        val p = path.trim('/')
        return when {
            p == "apps" -> names.filter { it !in apps }.toTypedArray()
            p.startsWith("rootfs") -> names.filter { !hidden("$p/$it") }.toTypedArray()
            else -> names
        }
    }
}

/**
 * Folders laid over each other as one asset tree, the first holding a path winning: Luna DE's
 * assets, from the same folders Gradle packages into the APK (AndroidLuna/src/main/assets,
 * local-assets, and CoreApps as apps/).
 */
class FolderAssets(private val roots: List<Pair<String, File>>) : Assets {
    /** [roots]: a prefix inside the tree ("" or "apps/") and the folder it is served from. */
    private fun candidates(path: String): List<File> = roots.mapNotNull { (prefix, dir) ->
        if (prefix.isEmpty()) File(dir, path)
        else if (path == prefix.trimEnd('/')) dir
        else if (path.startsWith(prefix)) File(dir, path.removePrefix(prefix))
        else null
    }

    override fun open(path: String): InputStream {
        val f = candidates(path.trimStart('/')).firstOrNull { it.isFile } ?: throw IOException("no asset $path")
        return f.inputStream()
    }

    override fun list(path: String): Array<String>? {
        val p = path.trimStart('/').trimEnd('/')
        val names = LinkedHashSet<String>()
        var any = false
        for (dir in candidates(p)) {
            if (dir.isDirectory) { any = true; dir.list()?.let { names.addAll(it) } }
        }
        // The top of the tree lists every root's prefix too, as the APK's assets/ lists apps/.
        if (p.isEmpty()) roots.forEach { (prefix, _) -> if (prefix.isNotEmpty()) { any = true; names.add(prefix.trimEnd('/').substringBefore('/')) } }
        return if (any) names.sorted().toTypedArray() else null
    }
}

/**
 * The same tree as one zip file, read in place, as Android reads it from the APK: a packaged
 * Luna DE's assets (DesktopLuna/package.js lays [FolderAssets]' folders over each other, the
 * first winning, into it). About twelve thousand small files become one for an installer to
 * write, a virus scanner to read and a Mac's signature to seal. The zip's directory is read once;
 * a file is then a seek away, and several may be open at a time (java.util.zip.ZipFile's streams
 * are independent).
 *
 * It answers as [FolderAssets] does: a folder lists its names sorted, a path that isn't a folder
 * lists as null, and a missing file is an IOException. A folder exists if the zip has an entry
 * for it or for anything inside it.
 */
class ZipAssets(file: File) : Assets {
    private val zip = java.util.zip.ZipFile(file)
    /** Each folder's names ("" is the top). */
    private val folders = HashMap<String, java.util.TreeSet<String>>()

    init {
        folders[""] = java.util.TreeSet()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            val name = e.name.trim('/')
            if (name.isEmpty()) continue
            if (e.isDirectory) folders.getOrPut(name) { java.util.TreeSet() }
            // The entry's name in its folder's list, and each folder above it in the one above that.
            var child = name
            while (true) {
                val parent = child.substringBeforeLast('/', "")
                folders.getOrPut(parent) { java.util.TreeSet() }.add(child.substringAfterLast('/'))
                if (parent.isEmpty()) break
                child = parent
            }
        }
    }

    override fun open(path: String): InputStream {
        val p = path.trim('/')
        val e = zip.getEntry(p)
        if (e == null || e.isDirectory || folders.containsKey(p)) throw IOException("no asset $path")
        return zip.getInputStream(e)
    }

    override fun list(path: String): Array<String>? = folders[path.trim('/')]?.toTypedArray()
}
