package org.webosarchive.lunacy.card

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * webOS's thumbnailer, extractfs: not a service and not a file anyone wrote, but a FUSE
 * filesystem mounted at `/var/luna/data/extractfs` that answered a read with the named image
 * scaled down. Pages read it by URL (the card host, [AppServer]) and services by path (cp and
 * read streams, through host.js and [ServiceProcess]); both come here.
 *
 * Two forms of path follow the mount point, both measured on the reference TouchPad:
 *
 * - **Short**, `<source>:<x>:<y>:<width>:<height>:<mode>`, which apps use (drPodder asks for
 *   every podcast's cover at `:0:0:56:56:3`). Measured 2026-09-22 by reading the synthetic
 *   files off the mount:
 *
 *   | asked for | source | came back |
 *   |---|---|---|
 *   | `:0:0:56:56:3` | 480 x 480 | 56 x 56 |
 *   | `:0:0:56:56:3` | 700 x 875 | **45 x 56** |
 *   | `:0:0:100:50:3` | 700 x 875 | **40 x 50** |
 *
 *   So the box is a bound, not a shape: the image is scaled to fit inside it with its aspect
 *   ratio kept, never cropped or stretched, and never scaled up. Modes 0 and 3 returned the
 *   same bytes, and the first two numbers were 0 in everything seen, so neither is acted on.
 *   The device returned an uncompressed BMP; this returns a PNG, which no page can tell apart
 *   and which is a tenth the size.
 * - **Long**, `<source>::3:<w0>:<h0>:<hpad>:<vpad>:<format>:<offset>:<length>:<width>:<height>:<crop>`
 *   (an empty field after the source), which the photos service uses (its generateExtractFsUrl;
 *   the formats and crop modes are named in its comments). The format is 0 bitmap, 1 JPEG,
 *   2 PNG, 3 JSON, 4 the source's bytes, 5 JPEG; a non-zero offset and length name an image
 *   embedded in the source (an EXIF or video thumbnail). Measured 2026-10-08 the same way:
 *
 *   | source | box, crop | JSON output, and the JPEG's size |
 *   |---|---|---|
 *   | 1024 x 1024 | 200 x 200, 3 | 200 x 200 |
 *   | 1024 x 768 | 200 x 200, 3 | 200 x 150 |
 *   | 1024 x 768 | 2000 x 2000, 3 | **2000 x 1500** |
 *   | 1024 x 768 | 500 x 200, 2 | 267 x 200 |
 *   | 1024 x 1024 | 500 x 200, 4 | **500 x 200**, cropped from the centre |
 *   | 800 x 2400 | 80 x 200, 4 | **80 x 200**, cropped from the centre |
 *
 *   So crop 3 (and 2, in all that was seen) fits the image inside the box - growing it, unlike
 *   the short form - and crop 4 fills the box and loses the overflow evenly from both sides.
 *   JSON is `{"original-width":%d,"original-height":%d,"output-width":%d,"output-height":%d}`.
 *   Formats 0 and 2 both came back as a BMP; this gives a PNG for both, as for the short form.
 */
object Extractfs {
    class Result(val bytes: ByteArray, val mime: String)

    /** Opens a webOS path, or null if there is no such file; called more than once per read. */
    fun interface Opener { fun open(path: String): (() -> InputStream?)? }

    /** What a read of `/var/luna/data/extractfs<spec>` returns, or null where the device's read failed. */
    fun read(spec: String, opener: Opener): Result? = try {
        val parts = spec.split(':')
        if (parts.size >= 13 && parts[parts.size - 12] == "" && parts[parts.size - 11] == "3") long(parts, opener) else short(parts, opener)
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "extractfs $spec: $e")
        null
    }

    private fun short(parts: List<String>, opener: Opener): Result? {
        if (parts.size < 6) return null
        val box = parts.takeLast(5).map { it.toIntOrNull() ?: return null }
        val w = box[2]; val h = box[3]
        if (w !in 1..4096 || h !in 1..4096) return null
        val source = Source(opener.open(parts.dropLast(5).joinToString(":")) ?: return null, 0, 0)
        val size = source.size() ?: return null
        val (ow, oh) = fit(size, w, h)
        return Result(encode(source.scaled(ow, oh, false) ?: return null, Bitmap.CompressFormat.PNG), "image/png")
    }

    private fun long(parts: List<String>, opener: Opener): Result? {
        val n = parts.takeLast(11).map { it.toIntOrNull() ?: return null }
        val format = n[5]; val offset = n[6]; val length = n[7]; val w = n[8]; val h = n[9]; val crop = n[10]
        if (w !in 1..4096 || h !in 1..4096) return null
        val source = Source(opener.open(parts.dropLast(12).joinToString(":")) ?: return null, offset, length)
        if (format == 4) return Result(source.bytes() ?: return null, "application/octet-stream")
        val size = source.size() ?: return null
        val fill = crop == 4
        val (ow, oh) = if (fill) w to h else fit(size, w, h, grow = true)
        if (format == 3) {
            return Result(("{\"original-width\":${size.first},\"original-height\":${size.second}," +
                "\"output-width\":$ow,\"output-height\":$oh}").toByteArray(), "application/json")
        }
        val bmp = source.scaled(ow, oh, fill) ?: return null
        return if (format == 1 || format == 5) Result(encode(bmp, Bitmap.CompressFormat.JPEG), "image/jpeg")
        else Result(encode(bmp, Bitmap.CompressFormat.PNG), "image/png")
    }

    /** Fits [size] inside w x h, keeping its shape; growing it only if [grow]. */
    private fun fit(size: Pair<Int, Int>, w: Int, h: Int, grow: Boolean = false): Pair<Int, Int> {
        val scale = minOf(w.toFloat() / size.first, h.toFloat() / size.second).let { if (grow) it else minOf(it, 1f) }
        return Math.max(1, Math.round(size.first * scale)) to Math.max(1, Math.round(size.second * scale))
    }

    private fun encode(bmp: Bitmap, format: Bitmap.CompressFormat): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(format, if (format == Bitmap.CompressFormat.JPEG) 90 else 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    /** The image to read: a whole file, or [length] bytes at [offset] in it. */
    private class Source(val open: () -> InputStream?, val offset: Int, val length: Int) {
        private var embedded: ByteArray? = null

        fun bytes(): ByteArray? {
            if (length <= 0) return open()?.use { it.readBytes() }
            embedded?.let { return it }
            return open()?.use { s ->
                var skip = offset.toLong()
                while (skip > 0) { val k = s.skip(skip); if (k <= 0) return null; skip -= k }
                val b = ByteArray(length); var at = 0
                while (at < length) { val r = s.read(b, at, length - at); if (r < 0) return null; at += r }
                b
            }?.also { embedded = it }
        }

        private fun decode(o: BitmapFactory.Options): Bitmap? =
            if (length > 0) bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size, o) }
            else open()?.use { BitmapFactory.decodeStream(it, null, o) }

        fun size(): Pair<Int, Int>? {
            // inJustDecodeBounds makes decode return null and fill in the options.
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decode(o)
            return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
        }

        /** The image at w x h: fitted (already the right shape), or filling it and cropped from the centre. */
        fun scaled(w: Int, h: Int, fill: Boolean): Bitmap? {
            val (sw, sh) = size() ?: return null
            var sample = 1
            while (sw / (sample * 2) >= w && sh / (sample * 2) >= h) sample *= 2
            val bmp = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val src = if (fill) {
                val scale = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
                val cw = Math.min(bmp.width, Math.round(w / scale)); val ch = Math.min(bmp.height, Math.round(h / scale))
                Rect((bmp.width - cw) / 2, (bmp.height - ch) / 2, (bmp.width - cw) / 2 + cw, (bmp.height - ch) / 2 + ch)
            } else Rect(0, 0, bmp.width, bmp.height)
            if (src.width() == w && src.height() == h && src.left == 0 && src.top == 0) return bmp
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(bmp, src, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle()
            return out
        }
    }
}
