package org.webosarchive.lunacy.card

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/**
 * webOS's music library: the songs, albums, artists, genres and artist-albums that Music
 * (com.palm.app.musicplayer) and the file picker read from db8, which filenotifyd's audio
 * module built on a device. Here they come from Android's media library, which has already read
 * every song's tags (codepoet, 2026-11-04): a song Android counts as music, in a folder webOS
 * apps can reach (UserFiles), becomes a `com.palm.media.audio.file:1` record, and the four
 * group kinds are worked out from those as filenotifyd worked them out.
 *
 * The records are filenotifyd's, measured on the reference TouchPad with tagged MP3s
 * (2026-11-04):
 * - **A song**: path, title (the file's name without a title tag), artist, albumArtist (the
 *   artist without its own tag), album, genre ("Unknown Artist", "Unknown Album", "Unknown
 *   Genre" where untagged), track {position, total}, disc {position, total}, the sort keys and
 *   searchKey that AudioObject makes, isRingtone false, duration 0, bookmark 0, createdTime 0,
 *   modifiedTime and size (the file's), serviced and hasResizedThumbnails false, and
 *   thumbnails: the cover embedded in the file, `{type: "embedded", data: "path:offset:length"}`,
 *   which Music reads through extractfs, or none.
 * - **An album** for each album and album artist: name, artist (the album artist), genre and
 *   thumbnails of a track on it, total {tracks}. An **artist-album** the same for each album and
 *   (track) artist.
 * - **An artist**: name, total {albums: its artist-albums, tracks}, thumbnails the first cover
 *   of up to three of its artist-albums (null for one without). A **genre**: name, total
 *   {albums: the albums in it, tracks}, thumbnails the first cover of up to three of them.
 *
 * Ringtones stay [MediaIndex]'s: Android files them as notification sounds, not music.
 */
class MusicLibrary(private val context: Context, private val webosRoot: File) {
    private class Cover(val modified: Long, val size: Long, val cover: Pair<Long, Long>?, val albumTag: Boolean?)
    private val covers = HashMap<String, Cover>()
    private var scanned = 0L
    private var songs: List<JSONObject> = emptyList()

    fun attach(db: Db8) {
        val keepers = setOf("serviced", "hasResizedThumbnails")
        db.mirror(spec(PLAYLIST_FILE, listOf(listOf("path"), listOf("_rev"))),
            MediaIndex.musicPermissions(PLAYLIST_FILE)) { playlists() }
        db.configure(listOf(spec(PLAYLIST_OBJECT, listOf(listOf("title"), listOf("_rev")))), listOf(MediaIndex.musicPermissions(PLAYLIST_OBJECT)))
        db.mirror(spec(ALBUM, listOf(KindSpec.index("albumname", KindSpec.prop("name", "primary")), KindSpec.index("serviced", KindSpec.prop("serviced")),
                KindSpec.index("hasResizedThumbnails", KindSpec.prop("hasResizedThumbnails")), listOf("genre"), listOf("artist"),
                listOf("hasResizedThumbnails", "_del"), listOf("_rev"))),
            MediaIndex.musicPermissions(ALBUM), initial = keepers, key = ::albumKey) { albums(songs(), true) }
        db.mirror(spec(ARTIST_ALBUM, listOf(KindSpec.index("albumname", KindSpec.prop("name", "primary")),
                KindSpec.index("artistalbumname", KindSpec.prop("artist"), KindSpec.prop("name", "primary")), KindSpec.index("serviced", KindSpec.prop("serviced")),
                KindSpec.index("hasResizedThumbnails", KindSpec.prop("hasResizedThumbnails")), listOf("artist"),
                listOf("hasResizedThumbnails", "_del"), listOf("_rev"))),
            MediaIndex.musicPermissions(ARTIST_ALBUM), initial = keepers, key = ::albumKey) { albums(songs(), false) }
        db.mirror(spec(ARTIST, listOf(KindSpec.index("artistname", KindSpec.prop("name", "primary")), KindSpec.index("serviced", KindSpec.prop("serviced")),
                KindSpec.index("hasResizedThumbnails", KindSpec.prop("hasResizedThumbnails")), listOf("hasResizedThumbnails", "_del"), listOf("_rev"))),
            MediaIndex.musicPermissions(ARTIST), initial = keepers, key = { it.optString("name") }) { artists(songs()) }
        db.mirror(spec(GENRE, listOf(KindSpec.index("genrename", KindSpec.prop("name", "primary")), KindSpec.index("serviced", KindSpec.prop("serviced")),
                KindSpec.index("hasResizedThumbnails", KindSpec.prop("hasResizedThumbnails")), listOf("hasResizedThumbnails", "_del"), listOf("_rev"))),
            MediaIndex.musicPermissions(GENRE), initial = keepers, key = { it.optString("name") }) { genres(songs()) }
    }

    private fun albumKey(o: JSONObject) = o.optString("name") + "\u0000" + o.optString("artist")

    /**
     * Playlist files: filenotifyd's playlist module filed each .m3u and .pls under
     * /media/internal as {path, title (the file's name), songPaths (absolute webOS paths), and
     * playlistObjId ""} in `com.palm.media.playlist.file:1`, which Music watches. The kind Music
     * lists them from, `com.palm.media.playlist.object:1`, is registered empty: what made those
     * records on a device is not yet measured.
     */
    private fun playlists(): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        fun walk(dir: File, path: String, depth: Int) {
            if (depth > 6 || out.size >= 500) return
            for (f in dir.listFiles().orEmpty().sortedBy { it.name }) {
                if (f.name.startsWith(".")) continue
                val p = "$path/${f.name}"
                if (f.isDirectory) { if (!UserFiles.nestedRoot(f)) walk(f, p, depth + 1); continue }
                val ext = f.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext != "m3u" && ext != "m3u8" && ext != "pls") continue
                val base = path
                val songs = JSONArray()
                runCatching {
                    f.readLines().map { it.trim() }.forEach { line ->
                        val entry = when {
                            ext == "pls" -> line.takeIf { it.lowercase(Locale.ROOT).startsWith("file") && it.contains('=') }?.substringAfter('=')
                            else -> line.takeIf { it.isNotEmpty() && !it.startsWith("#") }
                        } ?: return@forEach
                        val e = entry.removePrefix("file://").replace('\\', '/')
                        songs.put(if (e.startsWith("/")) e else "$base/$e")
                    }
                }
                out += JSONObject().put("path", p).put("title", f.name.substringBeforeLast('.')).put("songPaths", songs)
                    .put("playlistObjId", "").put(Db8.STAMP, "${f.lastModified()}:${f.length()}")
            }
        }
        walk(UserFiles.own(webosRoot), "/media/internal", 0)
        for ((name, dir) in UserFiles.mappedFolders()) walk(dir, "/media/internal/$name", 0)
        return out
    }

    // ---- the songs ----

    /** Every song, read from Android at most every few seconds; MediaIndex files them with the ringtones. Runs on db8's thread. */
    @Synchronized fun songs(): List<JSONObject> {
        val now = android.os.SystemClock.uptimeMillis()
        if (scanned != 0L && now - scanned < RESCAN_MS) return songs.map { JSONObject(it.toString()) }
        val out = ArrayList<JSONObject>()
        val genres = genresById()
        val seen = HashSet<String>()
        try {
            query()?.use { c ->
                val iId = c.getColumnIndex(MediaStore.Audio.Media._ID)
                val iData = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val iAlbumArtist = c.getColumnIndex(COL_ALBUM_ARTIST)
                val iTrack = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val iGenre = c.getColumnIndex(COL_GENRE)
                while (c.moveToNext() && out.size < MAX_SONGS) {
                    val real = c.getString(iData)?.let { File(it) } ?: continue
                    if (!real.isFile) continue
                    val path = UserFiles.webosPath(webosRoot, real) ?: continue
                    seen += path
                    val genre = (if (iGenre >= 0) c.getString(iGenre) else null) ?: genres[c.getLong(iId)]
                    out += song(path, real, tag(c, iTitle), tag(c, iArtist), tag(c, iAlbum), if (iAlbumArtist >= 0) tag(c, iAlbumArtist) else null,
                        if (iTrack >= 0) c.getInt(iTrack) else 0, genre?.takeIf { it.isNotBlank() })
                }
            }
        } catch (e: SecurityException) {
            // Android hasn't let Lunacy read its media (Android 6 and later ask): no songs, as with no music.
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "music library: $e")
        }
        synchronized(covers) { covers.keys.retainAll(seen) }
        songs = out; scanned = now
        return out.map { JSONObject(it.toString()) }
    }

    private fun query(): Cursor? {
        val base = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TRACK)
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val where = MediaStore.Audio.Media.IS_MUSIC + " != 0"
        // album_artist has been there since Android 4.x, named only from 11; genre came in 11.
        for (cols in listOf(base + COL_ALBUM_ARTIST + COL_GENRE, base + COL_ALBUM_ARTIST, base)) {
            try { return context.contentResolver.query(uri, cols, where, null, null) } catch (e: IllegalArgumentException) {}
        }
        return null
    }

    /** Before Android 11 a song's genre was in its own table: song id to genre name. */
    private fun genresById(): Map<Long, String> {
        if (Build.VERSION.SDK_INT >= 30) return emptyMap()
        val out = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null)?.use { g ->
                while (g.moveToNext()) {
                    val id = g.getLong(0); val name = g.getString(1) ?: continue
                    context.contentResolver.query(MediaStore.Audio.Genres.Members.getContentUri("external", id),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null)?.use { m ->
                        while (m.moveToNext()) out[m.getLong(0)] = name
                    }
                }
            }
        }
        return out
    }

    /** A tag as Android has it, or null: Android writes "<unknown>" for one a file hasn't got. */
    private fun tag(c: Cursor, i: Int): String? =
        c.getString(i)?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }

    private fun song(path: String, f: File, titleTag: String?, artistTag: String?, albumTag: String?, albumArtistTag: String?,
                     trackNo: Int, genreTag: String?): JSONObject {
        val known = covered(path, f)
        // Android names an untagged song's album after its folder; webOS called it Unknown Album.
        val album = albumTag?.takeIf { known.albumTag != false || it != f.parentFile?.name } ?: UNKNOWN_ALBUM
        val artist = artistTag ?: albumArtistTag ?: UNKNOWN_ARTIST
        val albumArtist = albumArtistTag ?: artistTag ?: UNKNOWN_ARTIST
        val title = titleTag ?: f.name.substringBeforeLast('.').ifEmpty { f.name }
        val track = trackNo % 1000; val disc = (trackNo / 1000).takeIf { it > 0 } ?: 1
        val tagged = artistTag != null || albumArtistTag != null || album != UNKNOWN_ALBUM || track != 0 || disc != 1
        val trackAndDisc = "${disc * 100000 + track}"
        val sortKey = if (!tagged) JSONObject().put("trackAndDisc", "100000")
            .put("albumDiscAndTrack", UNKNOWN_ALBUM + "100000")
            .put("albumArtistDiscAndTrack", UNKNOWN_ALBUM + UNKNOWN_ARTIST + "100000")
            .put("artistAlbumDiscAndTrack", UNKNOWN_ARTIST + UNKNOWN_ALBUM + "100000")
        else JSONObject().put("trackAndDisc", trackAndDisc)
            .put("albumDiscAndTrack", "$album\t\t$trackAndDisc")
            .put("albumArtistDiscAndTrack", "$album\t\t$albumArtist\t\t$trackAndDisc")
            .put("artistAlbumDiscAndTrack", "$artist\t\t$album\t\t$trackAndDisc")
        val thumbs = JSONArray()
        known.cover?.let { (offset, length) -> thumbs.put(JSONObject().put("type", "embedded").put("data", "$path:$offset:$length")) }
        return JSONObject().put("path", path).put("title", title).put("artist", artist).put("albumArtist", albumArtist)
            .put("album", album).put("genre", genreTag ?: UNKNOWN_GENRE)
            .put("track", JSONObject().put("position", track).put("total", 0))
            .put("disc", JSONObject().put("position", disc).put("total", 1))
            .put("sortKey", sortKey).put("searchKey", "$artist\t\t$album\t\t$title")
            .put("isRingtone", false).put("duration", 0).put("bookmark", 0).put("createdTime", 0)
            .put("modifiedTime", f.lastModified() / 1000).put("size", f.length())
            .put("serviced", false).put("hasResizedThumbnails", false).put("thumbnails", thumbs)
            .put(Db8.STAMP, "${f.lastModified()}:${f.length()}")
    }

    /** The file's embedded cover, and whether it has an album tag, kept while the file is unchanged. */
    private fun covered(path: String, f: File): Cover {
        synchronized(covers) { covers[path]?.takeIf { it.modified == f.lastModified() && it.size == f.length() }?.let { return it } }
        val cover = runCatching { EmbeddedCover.find(f) }.getOrNull()
        val albumTag = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(f.path); !r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).isNullOrBlank() } finally { r.release() }
        }.getOrNull()
        return Cover(f.lastModified(), f.length(), cover, albumTag).also { synchronized(covers) { covers[path] = it } }
    }

    // ---- the groups ----

    private class Group(val name: String, val artist: String) {
        val tracks = ArrayList<JSONObject>()
    }

    /** Albums by (name, album artist), or artist-albums by (name, artist). */
    private fun albums(all: List<JSONObject>, byAlbumArtist: Boolean): List<JSONObject> {
        val groups = LinkedHashMap<Pair<String, String>, Group>()
        for (s in all) {
            val artist = if (byAlbumArtist) s.optString("albumArtist") else s.optString("artist")
            groups.getOrPut(s.optString("album") to artist) { Group(s.optString("album"), artist) }.tracks += s
        }
        return groups.values.map { g ->
            // filenotifyd took these from a track of the album: the last one it indexed.
            val sample = g.tracks.last()
            JSONObject().put("name", g.name).put("artist", g.artist).put("genre", sample.optString("genre"))
                .put("thumbnails", JSONArray(sample.optJSONArray("thumbnails")?.toString() ?: "[]"))
                .put("total", JSONObject().put("tracks", g.tracks.size))
                .put("serviced", false).put("hasResizedThumbnails", false)
        }
    }

    private fun artists(all: List<JSONObject>): List<JSONObject> {
        val artistAlbums = albums(all, false).groupBy { it.optString("artist") }
        return all.groupBy { it.optString("artist") }.map { (name, tracks) ->
            val own = artistAlbums[name].orEmpty().sortedBy { it.optString("name").lowercase(Locale.ROOT) }
            JSONObject().put("name", name).put("total", JSONObject().put("albums", own.size).put("tracks", tracks.size))
                .put("thumbnails", firstCovers(own)).put("serviced", false).put("hasResizedThumbnails", false)
        }
    }

    private fun genres(all: List<JSONObject>): List<JSONObject> {
        val albumsByGenre = albums(all, true).groupBy { it.optString("genre") }
        return all.groupBy { it.optString("genre") }.map { (name, tracks) ->
            val own = albumsByGenre[name].orEmpty().sortedBy { it.optString("name").lowercase(Locale.ROOT) }
            JSONObject().put("name", name).put("total", JSONObject().put("albums", own.size).put("tracks", tracks.size))
                .put("thumbnails", firstCovers(own)).put("serviced", false).put("hasResizedThumbnails", false)
        }
    }

    /** The first cover of each of up to three albums, null for one without, as filenotifyd's thumbnails[0]. */
    private fun firstCovers(albums: List<JSONObject>): JSONArray {
        val out = JSONArray()
        for (a in albums.take(3)) out.put(a.optJSONArray("thumbnails")?.optJSONObject(0)?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
        return out
    }

    // ---- kinds ----

    private fun spec(id: String, indexes: List<Any>) = KindSpec.kind(id, "com.palm.filenotifyd.js", KindSpec.indexes(indexes))

    companion object {
        const val ALBUM = "com.palm.media.audio.album:1"
        const val ARTIST = "com.palm.media.audio.artist:1"
        const val GENRE = "com.palm.media.audio.genre:1"
        const val ARTIST_ALBUM = "com.palm.media.audio.artistalbum:1"
        const val PLAYLIST_FILE = "com.palm.media.playlist.file:1"
        const val PLAYLIST_OBJECT = "com.palm.media.playlist.object:1"
        private const val COL_ALBUM_ARTIST = "album_artist"
        private const val COL_GENRE = "genre"
        private const val UNKNOWN_ARTIST = "Unknown Artist"
        private const val UNKNOWN_ALBUM = "Unknown Album"
        private const val UNKNOWN_GENRE = "Unknown Genre"
        private const val RESCAN_MS = 5000L
        private const val MAX_SONGS = 20000
    }
}

/**
 * Where a song's cover is inside its file, as offset and length: the image filenotifyd named
 * "path:offset:length" for extractfs to read. An ID3v2 APIC (or v2.2 PIC) frame in an MP3, a
 * `covr` atom in an MP4/M4A, a PICTURE block in a FLAC; null for none, or a tag written with
 * unsynchronisation (its bytes aren't the image's).
 */
object EmbeddedCover {
    private const val READ_MAX = 8 * 1024 * 1024

    fun find(f: File): Pair<Long, Long>? = RandomAccessFile(f, "r").use { r ->
        val head = ByteArray(12)
        if (r.read(head) < 12) return null
        when {
            head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() -> id3(r, head)
            head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() && head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte() -> flac(r)
            String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(r, 0, r.length())
            else -> null
        }
    }

    private fun syncsafe(b: ByteArray, at: Int) =
        ((b[at].toInt() and 0x7f) shl 21) or ((b[at + 1].toInt() and 0x7f) shl 14) or ((b[at + 2].toInt() and 0x7f) shl 7) or (b[at + 3].toInt() and 0x7f)

    private fun int(b: ByteArray, at: Int, n: Int): Long { var v = 0L; for (i in 0 until n) v = (v shl 8) or (b[at + i].toLong() and 0xff); return v }

    private fun id3(r: RandomAccessFile, h: ByteArray): Pair<Long, Long>? {
        val major = h[3].toInt(); val flags = h[5].toInt()
        if (flags and 0x80 != 0) return null
        val size = syncsafe(h, 6)
        if (size <= 0 || size > READ_MAX) return null
        val tag = ByteArray(size); r.seek(10); r.readFully(tag)
        var at = 0
        if (flags and 0x40 != 0 && major >= 3) at += if (major == 4) syncsafe(tag, 0) else (int(tag, 0, 4).toInt() + 4)
        val v22 = major == 2
        val hdr = if (v22) 6 else 10
        while (at + hdr <= size) {
            val id = String(tag, at, if (v22) 3 else 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val len = when { v22 -> int(tag, at + 3, 3).toInt(); major == 4 -> syncsafe(tag, at + 4); else -> int(tag, at + 4, 4).toInt() }
            val body = at + hdr
            if (len <= 0 || body + len > size) break
            if (id == "APIC" || id == "PIC") {
                val enc = tag[body].toInt()
                var p = body + 1
                if (v22) p += 3 else { while (p < body + len && tag[p] != 0.toByte()) p++; p++ }
                p++ // picture type
                // The description, ended by one zero, or two for UTF-16.
                if (enc == 1 || enc == 2) { while (p + 1 < body + len && !(tag[p] == 0.toByte() && tag[p + 1] == 0.toByte())) p += 2; p += 2 }
                else { while (p < body + len && tag[p] != 0.toByte()) p++; p++ }
                val dataLen = body + len - p
                if (dataLen > 0) return (10L + p) to dataLen.toLong()
            }
            at = body + len
        }
        return null
    }

    private fun flac(r: RandomAccessFile): Pair<Long, Long>? {
        var at = 4L
        val h = ByteArray(4)
        while (at + 4 <= r.length()) {
            r.seek(at); r.readFully(h)
            val last = h[0].toInt() and 0x80 != 0; val type = h[0].toInt() and 0x7f
            val len = int(h, 1, 3)
            if (type == 6) {
                val b = ByteArray(minOf(len, 64 * 1024L).toInt()); r.readFully(b)
                var p = 4
                val mime = int(b, p, 4).toInt(); p += 4 + mime
                val desc = int(b, p, 4).toInt(); p += 4 + desc + 16
                val dataLen = int(b, p, 4); p += 4
                return (at + 4 + p) to dataLen
            }
            if (last) break
            at += 4 + len
        }
        return null
    }

    /** moov / udta / meta / ilst / covr / data, within [start, end). */
    private fun mp4(r: RandomAccessFile, start: Long, end: Long, path: List<String> = listOf("moov", "udta", "meta", "ilst", "covr", "data")): Pair<Long, Long>? {
        var at = start
        val h = ByteArray(8)
        while (at + 8 <= end) {
            r.seek(at); r.readFully(h)
            var size = int(h, 0, 4); val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var hdr = 8L
            if (size == 1L) { val x = ByteArray(8); r.readFully(x); size = int(x, 0, 8); hdr = 16 }
            if (size == 0L) size = end - at
            if (size < hdr) return null
            if (type == path[0]) {
                if (path.size == 1) return (at + hdr + 8) to (size - hdr - 8) // data: 4 type + 4 locale, then the image
                // meta has a version and flags before its children.
                val inner = at + hdr + if (type == "meta") 4 else 0
                return mp4(r, inner, at + size, path.drop(1))
            }
            at += size
        }
        return null
    }
}
