package org.webosarchive.lunacy.card

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/**
 * Files under /media/internal, lent to an Android app that Lunacy hands one to (a video to
 * Android's player: [WebosLinks]). From Android 7 an app may not pass another a file:// path,
 * so it gets `content://org.webosarchive.lunacy.media/media/internal/...` instead, readable
 * only through the grant on the intent that carries it: the provider isn't exported.
 */
class MediaFiles : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File {
        val path = uri.path.orEmpty()
        val rel = path.removePrefix(PREFIX).takeIf { path.startsWith(PREFIX) } ?: throw FileNotFoundException(path)
        return UserFiles.resolve(File(context!!.filesDir, "webos"), rel)?.takeIf { it.isFile } ?: throw FileNotFoundException(path)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read-only")
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = mimeOf(uri.path.orEmpty())

    /** What a player asks before it opens the file: its name and size. */
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val f = file(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).apply {
            addRow(columns.map { c -> when (c) { OpenableColumns.DISPLAY_NAME -> f.name; OpenableColumns.SIZE -> f.length(); else -> null } })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        const val AUTHORITY = "org.webosarchive.lunacy.media"
        private const val PREFIX = "/media/internal/"

        /** The content URI for a webOS path under /media/internal, or null for any other. */
        fun uriFor(webosPath: String): Uri? {
            val p = webosPath.removePrefix("file://")
            if (!p.startsWith(PREFIX)) return null
            return Uri.Builder().scheme("content").authority(AUTHORITY).path(p).build()
        }

        fun mimeOf(path: String): String? =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substringAfterLast('.', "").lowercase(Locale.US))
    }
}
