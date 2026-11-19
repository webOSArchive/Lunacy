package org.webosarchive.lunacy.desktop

import org.webosarchive.lunacy.core.MediaTags
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * What a media file says about itself, as LunaCore's [MediaTags], read here by hand: a desktop's
 * Java has no ExifInterface and no MediaMetadataRetriever, and the media index needs little.
 *
 * - **A JPEG's EXIF** (the APP1 segment's TIFF): DateTime from the first directory, else
 *   DateTimeOriginal from the Exif one; the sub-seconds beside it; and the embedded thumbnail's
 *   place from the second directory. A PNG or BMP has none.
 * - **A movie's header**, for MP4 and QuickTime (`moov/mvhd`): its length and creation time, and
 *   whether any track is video (`trak/mdia/hdlr`). No title is read. ASF/WMV and AVI are taken as
 *   movies by their names, with no length: their headers aren't read.
 * - **A song's tags** aren't read: a ringtone is listed by its file name, as one without tags is.
 */
object DesktopMediaTags : MediaTags {
    override fun exif(f: File): MediaTags.Exif? = runCatching {
        val head = f.inputStream().use { it.readNBytes(256 * 1024) }
        if (head.size < 4 || head[0] != 0xFF.toByte() || head[1] != 0xD8.toByte()) return null
        var at = 2
        while (at + 4 <= head.size && head[at] == 0xFF.toByte()) {
            val marker = head[at + 1].toInt() and 0xFF
            val size = ((head[at + 2].toInt() and 0xFF) shl 8) or (head[at + 3].toInt() and 0xFF)
            if (marker == 0xDA || size < 2) break   // the picture itself: no EXIF before it
            if (marker == 0xE1 && at + 10 <= head.size && String(head, at + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                return tiff(head, at + 10, minOf(head.size, at + 2 + size))
            }
            at += 2 + size
        }
        null
    }.getOrNull()

    /** The TIFF at [start] of [b], which ends before [end]; offsets in it count from [start]. */
    private fun tiff(b: ByteArray, start: Int, end: Int): MediaTags.Exif? {
        if (start + 8 > end) return null
        val order = when (String(b, start, 2, Charsets.ISO_8859_1)) { "II" -> ByteOrder.LITTLE_ENDIAN; "MM" -> ByteOrder.BIG_ENDIAN; else -> return null }
        val buf = ByteBuffer.wrap(b).order(order)
        fun u16(o: Int) = buf.getShort(o).toInt() and 0xFFFF
        fun u32(o: Int) = buf.getInt(o).toLong() and 0xFFFFFFFFL
        /** A directory's entries by tag, as (type, count, the offset of its value field), and the next directory. */
        fun dir(offset: Long): Pair<Map<Int, Triple<Int, Long, Int>>, Long> {
            val o = start + offset.toInt()
            if (offset <= 0 || o + 2 > end) return emptyMap<Int, Triple<Int, Long, Int>>() to 0L
            val n = u16(o)
            val entries = HashMap<Int, Triple<Int, Long, Int>>()
            for (i in 0 until n) {
                val e = o + 2 + i * 12
                if (e + 12 > end) break
                entries[u16(e)] = Triple(u16(e + 2), u32(e + 4), e + 8)
            }
            val next = o + 2 + n * 12
            return entries to (if (next + 4 <= end) u32(next) else 0L)
        }
        fun ascii(e: Triple<Int, Long, Int>?): String? {
            if (e == null || e.first != 2 || e.second <= 0 || e.second > 64) return null
            val n = e.second.toInt()
            val o = if (n <= 4) e.third else start + u32(e.third).toInt()
            if (o < 0 || o + n > end) return null
            return String(b, o, n, Charsets.ISO_8859_1).trimEnd('\u0000', ' ').takeIf { it.isNotEmpty() }
        }
        fun number(e: Triple<Int, Long, Int>?): Long = when (e?.first) { 3 -> u16(e.third).toLong(); 4 -> u32(e.third); else -> 0L }

        val (first, second) = dir(u32(start + 4))
        val exif = dir(number(first[0x8769])).first
        val thumbs = dir(second).first
        val offset = number(thumbs[0x0201]); val length = number(thumbs[0x0202])
        val there = offset > 0 && length > 0 && start + offset + length <= end
        return MediaTags.Exif(ascii(first[0x0132]) ?: ascii(exif[0x9003]), ascii(exif[0x9290]) ?: ascii(exif[0x9291]),
            if (there) start + offset else 0L, if (there) length else 0L)
    }

    override fun video(f: File): MediaTags.Video? {
        val ext = f.name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext in setOf("wmv", "asf", "avi")) return MediaTags.Video(0, null, null)
        return runCatching { RandomAccessFile(f, "r").use { r -> movie(r) } }.getOrNull()
    }

    /** One atom's children: each one's type, where its contents start and where they end. */
    private fun atoms(r: RandomAccessFile, from: Long, to: Long): List<Triple<String, Long, Long>> {
        val out = ArrayList<Triple<String, Long, Long>>()
        var at = from
        val head = ByteArray(8)
        while (at + 8 <= to && out.size < 4096) {
            r.seek(at); r.readFully(head)
            var size = ByteBuffer.wrap(head).int.toLong() and 0xFFFFFFFFL
            var body = at + 8
            if (size == 1L) { size = r.readLong(); body = at + 16 }
            else if (size == 0L) size = to - at
            if (size < body - at || at + size > to) break
            out += Triple(String(head, 4, 4, Charsets.ISO_8859_1), body, at + size)
            at += size
        }
        return out
    }

    private fun movie(r: RandomAccessFile): MediaTags.Video? {
        val moov = atoms(r, 0, r.length()).firstOrNull { it.first == "moov" } ?: return null
        val inside = atoms(r, moov.second, moov.third)
        val video = inside.filter { it.first == "trak" }.any { trak ->
            atoms(r, trak.second, trak.third).filter { it.first == "mdia" }.any { mdia ->
                atoms(r, mdia.second, mdia.third).firstOrNull { it.first == "hdlr" }?.let { hdlr ->
                    // version and flags, then pre_defined, then the handler's type.
                    if (hdlr.third - hdlr.second < 12) false
                    else { r.seek(hdlr.second + 8); ByteArray(4).also { r.readFully(it) }.let { String(it, Charsets.ISO_8859_1) == "vide" } }
                } == true
            }
        }
        if (!video) return null
        val mvhd = inside.firstOrNull { it.first == "mvhd" } ?: return MediaTags.Video(0, null, null)
        r.seek(mvhd.second)
        val version = r.readUnsignedByte(); r.skipBytes(3)
        val created: Long; val scale: Long; val length: Long
        if (version == 1) { created = r.readLong(); r.readLong(); scale = r.readInt().toLong() and 0xFFFFFFFFL; length = r.readLong() }
        else { created = r.readInt().toLong() and 0xFFFFFFFFL; r.readInt(); scale = r.readInt().toLong() and 0xFFFFFFFFL; length = r.readInt().toLong() and 0xFFFFFFFFL }
        // Seconds from 1904 (QuickTime's epoch); none written is 0, which the index takes as no date.
        val unix = created - 2082844800L
        val date = if (unix > 0) SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(unix * 1000)) else null
        return MediaTags.Video(if (scale > 0) length * 1000 / scale else 0, null, date)
    }

    override fun audio(f: File): MediaTags.Audio? = null
}
