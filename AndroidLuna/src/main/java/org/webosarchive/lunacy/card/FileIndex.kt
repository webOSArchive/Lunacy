package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * webOS's file index for documents: the `com.palm.media.misc.file:1` records that Quick Office,
 * Adobe Reader and the system UI list files from, answered from the files themselves.
 *
 * On a device filenotifyd kept these records as a copy of /media/internal, indexing each file
 * that wasn't audio, an image or a video, and a copy that was often behind. Here db8 brings the
 * kind into line with the folders whenever it is read (Db8.mirror), so a file copied into
 * Android's Documents or Download is in the list at once. The records are what filenotifyd's
 * "unknown" module wrote (filenotifyd-triton, filenotifyd_module_unknown.js), measured on the
 * reference TouchPad: name (the file name without its extension), searchKey (the same), path,
 * extension, size, and modifiedTime in seconds. Hidden files and folders are left out, as there.
 * The kind and its consumers are filenotifyd's own registration: those apps may read the
 * records and add to them (measured), others are refused.
 *
 * codepoet's design (2026-10-07): the index was one of webOS's worst parts and an awkward fit for
 * Android's shared folders, so the files are the truth and db8 answers from them. Media kinds
 * are not part of this yet.
 */
class FileIndex(private val webosRoot: File) {

    /** Registers the kind and makes db8 answer it from the folders. */
    fun attach(db: Db8) = db.mirror(kindSpec(), permissions(), ::records)

    /** Every document under /media/internal, as filenotifyd's records. Runs on db8's thread. */
    private fun records(): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        val own = UserFiles.own(webosRoot)
        // Lunacy's own tree first: where a mapped folder's name exists there too, UserFiles
        // resolves to Lunacy's copy, so that is the file the path names.
        walk(own, "/media/internal", 0, out, seen)
        for ((name, dir) in UserFiles.mappedFolders()) walk(dir, "/media/internal/$name", 0, out, seen)
        return out
    }

    private fun walk(dir: File, path: String, depth: Int, out: MutableList<JSONObject>, seen: MutableSet<String>) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val entries = dir.listFiles() ?: return
        for (f in entries.sortedBy { it.name }) {
            if (f.name.startsWith(".")) continue
            val p = "$path/${f.name}"
            if (f.isDirectory) {
                walk(f, p, depth + 1, out, seen)
            } else if (f.isFile && isDocument(f.name) && seen.add(p)) {
                out += record(p, f)
                if (out.size >= MAX_FILES) return
            }
        }
    }

    private fun record(path: String, f: File): JSONObject {
        val name = nameOf(path)
        return JSONObject().put("path", path).put("name", name).put("searchKey", name)
            .put("extension", extensionOf(path)).put("size", f.length()).put("modifiedTime", f.lastModified() / 1000)
    }

    /** filenotifyd's parseNameFromPath: the last segment, up to its last dot. */
    private fun nameOf(path: String): String {
        val slash = path.lastIndexOf('/')
        val start = if (slash != -1 && slash + 1 < path.length) slash + 1 else 0
        val dot = path.lastIndexOf('.')
        val end = if (dot > slash) dot else path.length
        return path.substring(start, end)
    }

    /** filenotifyd's parseExtensionFromPath: after the last dot, if it is in the last segment. */
    private fun extensionOf(path: String): String {
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.')
        return if (slash >= dot || dot == -1) "" else path.substring(dot + 1)
    }

    /** Audio, images and video had kinds of their own; everything else is a document. */
    private fun isDocument(name: String) = name.substringAfterLast('.', "").lowercase() !in MEDIA

    /** filenotifyd_module_unknown.js's registration: the kind and its indexes. */
    private fun kindSpec(): JSONObject {
        fun props(vararg p: JSONObject) = JSONArray(p.toList())
        fun prop(name: String, collate: String? = null, tokenize: String? = null) =
            JSONObject().put("name", name).apply { collate?.let { put("collate", it) }; tokenize?.let { put("tokenize", it) } }
        fun index(name: String, p: JSONArray) = JSONObject().put("name", name).put("props", p)
        return JSONObject().put("id", KIND).put("owner", OWNER).put("indexes", JSONArray(listOf(
            index("path", props(prop("path"))),
            index("rev", props(prop("_rev"))),
            index("revPath", props(prop("_rev"), prop("path"))),
            index("searchKeyIndex", props(prop("searchKey", "primary", "default"))),
            index("searchKeyIndexOnExtension", props(prop("extension"), prop("searchKey", "primary", "default"))),
            index("listIndexOnExtension", props(prop("extension"), prop("name", "primary"))),
            index("listIndex", props(prop("name", "primary"))),
        )))
    }

    /** Its consumers, which may read the records and add to them (measured with Quick Office's id). */
    private fun permissions(): JSONArray = JSONArray(CONSUMERS.map { app ->
        JSONObject().put("type", "db.kind").put("object", KIND).put("caller", app)
            .put("operations", JSONObject().put("read", "allow").put("update", "allow"))
    })

    companion object {
        const val KIND = "com.palm.media.misc.file:1"
        private const val OWNER = "com.palm.filenotifyd"
        private val CONSUMERS = listOf("com.palm.systemui", "com.quickoffice.webos", "com.quickoffice.ar", "com.palm.mojo-systemui")
        private val MEDIA = setOf(
            "mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "flac", "amr", "qcp", "wma", "mid", "midi",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic",
            "mp4", "m4v", "mov", "3gp", "3g2", "avi", "mkv", "webm", "wmv")
        private const val MAX_DEPTH = 12
        private const val MAX_FILES = 5000
    }
}
