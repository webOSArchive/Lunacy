package org.webosarchive.lunacy.card

import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * webOS's media index for photos and videos: the `com.palm.media.image.file:1`,
 * `com.palm.media.video.file:1` and `com.palm.media.image.album:1` records that Photos & Videos,
 * the photos service, the video player and the system UI read, answered from the files
 * themselves as [FileIndex] answers documents (codepoet's design, luna-deltas A16).
 *
 * On a device filenotifyd built them (filenotifyd-triton's image, video and mediaParent modules,
 * with Palm's photos library for album names), and the photos service then made each one's grid
 * thumbnail and marked it done. Here db8 brings the kinds into line with the folders whenever
 * they are read (Db8.mirror); the photos service still makes the thumbnails, as there. The
 * records are filenotifyd's, measured on the reference TouchPad:
 *
 * - **An image** (JPEG, PNG or BMP, Image.parse): path, type "local", albumPath, createdTime
 *   (the EXIF date, read as local time, with its sub-seconds; 0 without one), thumbnails (the
 *   image itself, "embedded", with its EXIF thumbnail's offset and length), mediaType "image",
 *   appCacheComplete "unattempted" until the photos service has been, and albumId.
 * - **A video** (MP4/QuickTime with a video track, ASF/WMV, AVI; VideoParser): path, type,
 *   albumPath, capturedOnDevice (under /media/internal/DCIM/), createdTime (the movie's own
 *   creation time), modifiedTime and size (the file's), title and searchKey (the movie's title,
 *   or the file name), description "", thumbnails (none: a movie that isn't Palm's camera's
 *   carries none, so the grid shows Photos' video placeholder, as on the TouchPad), duration
 *   in seconds to two places, playbackPosition 0, mediaType "video", appCacheComplete, albumId.
 * - **An album** for each folder holding any (Album.parse, mediaParent's RebuildAlbums): name
 *   (Palm's for its predefined folders, the folder's otherwise; Android's camera folder is
 *   Palm's "Photo roll"), sortKey (priority_name),
 *   searchKey, path, type "local", showAlbum, total {images, videos}, the newest three
 *   images' thumbnails, and modifiedTime: the folder's, read as filenotifyd read it - its UTC
 *   fields taken as local time, so it is off by the timezone (measured: seven hours, in PDT).
 *
 * The kinds, their indexes and consumers are filenotifyd's registrations, and a consumer may
 * create, read, change and delete records (measured with the photos app and service; other
 * apps are refused). Records an account synced (type other than "local") are left alone.
 */
class MediaIndex(private val webosRoot: File) {
    /** A file's record as last read, kept while the file is unchanged: EXIF and movie headers are slow. */
    private class Seen(val modified: Long, val size: Long, val record: JSONObject?)
    private val seen = HashMap<String, Seen>()
    private var scanned = 0L
    private var media: List<JSONObject> = emptyList()
    /** When a pseudo-album with no folder (the media sync roll) was first seen: its modifiedTime. */
    private val firstSeen = HashMap<String, Long>()

    fun attach(db: Db8, bus: Bus) {
        db.configure(listOf(typesKind()), listOf(permissions(TYPES)))
        db.mirror(albumKind(), permissions(ALBUM), initial = setOf("showAlbum"), owns = ::local) { albums() }
        val added = { cacheThumbnails(bus); Unit }
        db.mirror(imageKind(), permissions(IMAGE), initial = setOf("appCacheComplete"), owns = ::local, added = added) { files(db, "image") }
        db.mirror(videoKind(), permissions(VIDEO), initial = setOf("appCacheComplete", "playbackPosition"), owns = ::local, added = added) {
            files(db, "video")
        }
    }

    /**
     * New pictures are indexed: the photos service makes their thumbnails, as filenotifyd had it
     * do after every pass (mediaParent's processPostComplete, "calling into photos service to
     * take over app caching").
     */
    private fun cacheThumbnails(bus: Bus) = android.os.Handler(android.os.Looper.getMainLooper()).post {
        if (bus.has(PHOTOS_SERVICE)) bus.call(INDEXER, "palm://$PHOTOS_SERVICE/cacheThumbnails", "{}", privateBus = true) {}
    }

    private fun local(o: JSONObject) = o.optString("type") == "local"

    /** The images or videos, each with its album's id. Runs on db8's thread. */
    private fun files(db: Db8, type: String): List<JSONObject> {
        val albums = db.mirrorIds(ALBUM)
        return scan().filter { it.optString("mediaType") == type }.map { r ->
            JSONObject(r.toString()).apply { albums[r.optString("albumPath")]?.let { put("albumId", it) } }
        }
    }

    /** One album per folder that holds images or videos. */
    private fun albums(): List<JSONObject> = scan().groupBy { it.optString("albumPath") }.map { (path, items) ->
        val (name, priority) = albumName(path)
        val images = items.filter { it.optString("mediaType") == "image" }
        JSONObject().put("name", name).put("sortKey", "${priority}_$name").put("searchKey", name)
            .put("path", path).put("type", "local").put("showAlbum", true)
            .put("total", JSONObject().put("images", images.size).put("videos", items.size - images.size))
            .put("thumbnails", JSONArray(images.sortedByDescending { it.optDouble("createdTime") }.take(3)
                .map { JSONObject(it.getJSONArray("thumbnails").getJSONObject(0).toString()) }))
            .put("modifiedTime", albumTime(path))
    }

    /** Every image and video under /media/internal, as records without their albumId; read at most every second. */
    @Synchronized private fun scan(): List<JSONObject> {
        val now = android.os.SystemClock.uptimeMillis()
        if (scanned != 0L && now - scanned < 1000) return media
        val out = ArrayList<JSONObject>()
        val paths = HashSet<String>()
        // Lunacy's own tree first: where a mapped folder's name exists there too, that copy is the one.
        walk(UserFiles.own(webosRoot), "/media/internal", 0, out, paths)
        for ((name, dir) in UserFiles.mappedFolders()) walk(dir, "/media/internal/$name", 0, out, paths)
        seen.keys.retainAll(paths)
        media = out; scanned = now
        return out
    }

    private fun walk(dir: File, path: String, depth: Int, out: MutableList<JSONObject>, paths: MutableSet<String>) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val entries = dir.listFiles() ?: return
        for (f in entries.sortedBy { it.name }) {
            if (f.name.startsWith(".")) continue
            val p = "$path/${f.name}"
            if (f.isDirectory) { walk(f, p, depth + 1, out, paths); continue }
            val ext = f.name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext !in IMAGES && ext !in VIDEOS || !f.isFile || !paths.add(p)) continue
            val old = seen[p]
            val record = if (old != null && old.modified == f.lastModified() && old.size == f.length()) old.record
                else (if (ext in IMAGES) image(p, f) else video(p, f)).also { seen[p] = Seen(f.lastModified(), f.length(), it) }
            if (record != null) out += record
            if (out.size >= MAX_FILES) return
        }
    }

    private fun image(path: String, f: File): JSONObject? {
        var created = 0.0
        var offset = 0L; var length = 0L
        try {
            val exif = ExifInterface(f.path)
            (exif.getAttribute(ExifInterface.TAG_DATETIME) ?: exif.getAttribute(TAG_DATETIME_ORIGINAL))?.let { d ->
                val a = d.replace(" ", ":").split(":").map { it.trim().toIntOrNull() }
                created = if (a.size >= 6 && a.take(6).all { it != null }) {
                    // new Date(y, m - 1, d, h, min, s): local time, the month at least January.
                    java.util.Calendar.getInstance().apply {
                        clear(); set(a[0]!!, Math.max(a[1]!! - 1, 0), a[2]!!, a[3]!!, a[4]!!, a[5]!!)
                    }.timeInMillis / 1000.0
                } else 0.0
                val sub = exif.getAttribute(TAG_SUBSEC_TIME) ?: exif.getAttribute(TAG_SUBSEC_TIME_ORIGINAL)
                sub?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { s ->
                    created += s / Math.pow(10.0, s.toString().length.toDouble())
                }
            }
            if (Build.VERSION.SDK_INT >= 24) exif.thumbnailRange?.let { offset = it[0]; length = it[1] }
        } catch (e: Exception) {}
        return JSONObject().put("path", path).put("type", "local").put("albumPath", albumPath(path))
            .put("createdTime", created)
            .put("thumbnails", JSONArray().put(JSONObject().put("type", "embedded")
                .put("data", JSONObject().put("offset", offset).put("length", length).put("path", path))))
            .put("mediaType", "image").put("appCacheComplete", "unattempted")
    }

    private fun video(path: String, f: File): JSONObject? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.path)
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val duration = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
            val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotEmpty() }
                ?: f.name.substringBeforeLast('.').ifEmpty { f.name }
            return JSONObject().put("path", path).put("type", "local").put("albumPath", albumPath(path))
                .put("capturedOnDevice", path.startsWith("/media/internal/DCIM/") || albumPath(path) == CAMERA_ROLL)
                .put("createdTime", movieDate(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)))
                .put("modifiedTime", f.lastModified() / 1000).put("size", f.length())
                .put("title", title).put("searchKey", title).put("description", "")
                .put("thumbnails", JSONArray()).put("duration", Math.round(duration * 100) / 100.0)
                .put("playbackPosition", 0).put("mediaType", "video").put("appCacheComplete", "unattempted")
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { r.release() }
        }
    }

    /** The movie header's creation time (Android gives it as "20261001T120000.000Z"), in seconds; 0 without one. */
    private fun movieDate(s: String?): Long {
        if (s.isNullOrEmpty()) return 0
        for (pattern in listOf("yyyyMMdd'T'HHmmss.SSS'Z'", "yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss")) {
            val f = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val d = runCatching { f.parse(s) }.getOrNull() ?: continue
            // An MP4 with no creation time has 1904 (QuickTime's epoch) or 1970.
            return Math.max(0, d.time / 1000)
        }
        return 0
    }

    /**
     * Palm's Album.getPathFromImage: the camera's roll, the media sync roll, or the folder.
     * Android's camera folder (DCIM/Camera, /media/internal/camera/Camera here) is the roll too,
     * as Palm's camera's DCIM/100PALM was (codepoet, 2026-10-08; luna-deltas A16).
     */
    private fun albumPath(path: String): String {
        val n = path.lowercase(Locale.US)
        if (n.startsWith(ANDROID_CAMERA) && n.indexOf('/', ANDROID_CAMERA.length) == -1) return CAMERA_ROLL
        if (n.startsWith("/media/internal/dcim/")) {
            val roll = n.drop(21).take(7)
            if (roll.length == 7 && roll.take(3).toIntOrNull()?.let { it in 100..999 } == true && roll.drop(3) == "palm") return CAMERA_ROLL
        }
        if (n.startsWith("/media/internal/photos/full resolution/")) return MEDIA_SYNC
        if (n.lastIndexOf('/') == 0) return ALL_IMAGES
        return path.substring(0, path.lastIndexOf('/'))
    }

    /** Album.parse's name and priority: Palm's predefined albums, or the folder's name at 900. */
    private fun albumName(path: String): Pair<String, Int> =
        PREDEFINED[path.lowercase(Locale.US)] ?: (path.substring(path.lastIndexOf('/') + 1) to 900)

    /** RebuildAlbums' modifiedTime: the folder's mtime, its UTC fields read as local time. */
    private fun albumTime(path: String): Long {
        val dir = when (path) {
            CAMERA_ROLL -> UserFiles.resolve(webosRoot, "camera/Camera")?.takeIf { it.isDirectory }
                ?: UserFiles.resolve(webosRoot, "DCIM")
            MEDIA_SYNC, ALL_IMAGES -> null
            else -> UserFiles.resolve(webosRoot, path.removePrefix("/media/internal").trimStart('/'))
        }
        // No folder: filenotifyd took the time it looked; keep the first, so the album stays still.
        val mtime = dir?.takeIf { it.exists() }?.lastModified() ?: firstSeen.getOrPut(path) { System.currentTimeMillis() }
        return (mtime - TimeZone.getDefault().getOffset(mtime)) / 1000
    }

    private fun spec(id: String, indexes: List<Any>, extends: Boolean) = JSONObject().put("id", id).put("owner", OWNER)
        .put("indexes", JSONArray(indexes.map { i ->
            if (i is JSONObject) i
            else (i as List<*>).let { props -> JSONObject().put("name", props.joinToString("_"))
                .put("props", JSONArray(props.map { JSONObject().put("name", it) })) }
        })).apply { if (extends) put("extends", JSONArray().put(TYPES)) }

    private fun named(name: String, vararg props: JSONObject) = JSONObject().put("name", name).put("props", JSONArray(props.toList()))
    private fun prop(name: String, collate: String? = null, tokenize: String? = null) =
        JSONObject().put("name", name).apply { collate?.let { put("collate", it) }; tokenize?.let { put("tokenize", it) } }

    // filenotifyd's registrations (filenotifyd_module_mediaParent.js, _image.js, _video.js).
    private fun typesKind() = spec(TYPES, listOf(
        listOf("path"), listOf("albumPath"), listOf("albumId"), listOf("appCacheComplete", "albumId"),
        listOf("appCacheComplete", "type", "albumId"), listOf("mediaType"), listOf("appCacheComplete", "albumId", "createdTime"),
        named("albumCountChangedWatch", prop("appCacheComplete"), prop("albumId"), prop("_rev")).put("incDel", true),
        listOf("albumPath", "createdTime"), listOf("_del")), extends = false)

    private fun albumKind() = spec(ALBUM, listOf(
        listOf("path"), listOf("type"), listOf("type", "modifiedTime"), listOf("accountId"), listOf("modifiedTime"),
        listOf("toBeDeleted"), listOf("accountId", "modifiedTime"), listOf("name"), listOf("name", "type"), listOf("showAlbum"),
        listOf("showAlbum", "modifiedTime"), listOf("showAlbum", "type", "modifiedTime"), listOf("showAlbum", "accountId", "modifiedTime"),
        named("sortKeyIndex", prop("sortKey", "primary")), named("searchKeyIndex", prop("searchKey", "primary", "default")),
        listOf("_rev")), extends = false)

    private fun imageKind() = spec(IMAGE, listOf(
        listOf("path"), listOf("albumId"), listOf("type"), listOf("albumId", "createdTime"), listOf("appCacheComplete", "albumId"),
        listOf("albumPath", "createdTime"), listOf("mediaType"), listOf("_rev")), extends = true)

    private fun videoKind() = spec(VIDEO, listOf(
        listOf("path"), listOf("albumPath"), listOf("mediaType"), listOf("albumPath", "createdTime"), listOf("albumId"),
        listOf("capturedOnDevice", "albumId"), listOf("appCacheComplete", "albumId"),
        named("loadedVidsIndex", prop("capturedOnDevice"), prop("searchKey", "primary", "default")),
        named("customCameraIndex", prop("capturedOnDevice"), prop("albumId"), prop("createdTime", "primary", "default")),
        named("capturedVidsIndex", prop("capturedOnDevice"), prop("createdTime"), prop("searchKey", "primary", "default")),
        named("sideloadedListIndex", prop("capturedOnDevice"), prop("title", "primary")),
        listOf("capturedOnDevice", "modifiedTime"), listOf("capturedOnDevice", "createdTime"), listOf("_rev")), extends = true)

    private fun permissions(kind: String): JSONArray = JSONArray(CONSUMERS.getValue(kind).map { app ->
        JSONObject().put("type", "db.kind").put("object", kind).put("caller", app).put("operations", JSONObject()
            .put("create", "allow").put("read", "allow").put("update", "allow").put("delete", "allow"))
    })

    companion object {
        const val TYPES = "com.palm.media.types:1"
        const val ALBUM = "com.palm.media.image.album:1"
        const val IMAGE = "com.palm.media.image.file:1"
        const val VIDEO = "com.palm.media.video.file:1"
        private const val OWNER = "com.palm.filenotifyd.js"
        /** filenotifyd's bus name, which called the photos service. */
        private const val INDEXER = "com.palm.filenotifyd"
        private const val PHOTOS_SERVICE = "com.palm.service.photos"
        private val CONSUMERS = mapOf(
            TYPES to listOf("com.palm.app.photos", "com.palm.service.photos", "com.palm.mojo-systemui", "com.palm.systemui"),
            ALBUM to listOf("com.palm.app.photos", "com.palm.service.photos", "com.palm.app.camera", "com.palm.service.mediacache",
                "com.palm.systemui", "com.palm.mojo-systemui"),
            IMAGE to listOf("com.palm.app.photos", "com.palm.app.camera", "com.palm.systemui", "com.palm.mojo-systemui",
                "com.palm.service.mediacache", "com.palm.service.photos"),
            VIDEO to listOf("com.palm.app.camera", "com.palm.app.videoplayer", "com.palm.service.mediacache", "com.palm.systemui",
                "com.palm.mojo-systemui"),
        )
        private const val CAMERA_ROLL = "camera://"
        /** Android's camera folder as apps see it (UserFiles maps DCIM to "camera"), lower case. */
        private const val ANDROID_CAMERA = "/media/internal/camera/camera/"
        private const val MEDIA_SYNC = "mediasync://"
        private const val ALL_IMAGES = "[all]"
        /** Palm's photos library's predefined albums (album.js), in English. */
        private val PREDEFINED = mapOf(
            CAMERA_ROLL to ("Photo roll" to 200), ALL_IMAGES to ("All Images" to 250),
            "/media/internal" to ("Miscellaneous" to 300), MEDIA_SYNC to ("Media Sync" to 400),
            "/media/internal/downloads" to ("Downloads" to 900), "/media/internal/messaging" to ("Messaging" to 900),
            "/media/internal/screencaptures" to ("Screen captures" to 900), "/media/internal/wallpapers" to ("Wallpapers" to 900))
        private val IMAGES = setOf("jpg", "jpeg", "png", "bmp")
        private val VIDEOS = setOf("mp4", "m4v", "mov", "3gp", "3g2", "wmv", "asf", "avi")
        // ExifInterface's names for these came in API 24; the tags are the same.
        private const val TAG_DATETIME_ORIGINAL = "DateTimeOriginal"
        private const val TAG_SUBSEC_TIME = "SubSecTime"
        private const val TAG_SUBSEC_TIME_ORIGINAL = "SubSecTimeOriginal"
        private const val MAX_DEPTH = 12
        private const val MAX_FILES = 5000
    }
}
