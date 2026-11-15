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
