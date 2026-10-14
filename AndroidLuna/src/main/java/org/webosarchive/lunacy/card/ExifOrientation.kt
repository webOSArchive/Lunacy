package org.webosarchive.lunacy.card

import android.webkit.WebResourceRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream

/**
 * webOS's WebKit drew a JPEG's stored pixels and ignored its EXIF orientation, in an `<img>`, a
 * CSS background and a canvas alike; Chromium honours it from 81 on, in all of them, and no
 * style turns that off for a picture an app loads with `new Image()` and draws on a canvas of
 * its own (measured on the Fold3's WebView 154). Photos & Videos 3.2.0's editor reads the
 * orientation itself (an XHR of the file) and applies it, so a sideways photo turned twice.
 *
 * So a JPEG the card host serves to an image load reads orientation 1 (normal), as the
 * TouchPad's engine read every one. A request that reads the bytes (an XHR) gets the file as
 * it is. Only the tag's value changes, in place, so the length is the file's.
 */
object ExifOrientation {
    /** How far into a file the EXIF block is looked for: APP1 comes first, and is at most 64 KB. */
    private const val HEAD = 70 * 1024

    /** Whether the engine asks for the resource to draw it: an image load's Accept starts with image/. */
    fun isImageLoad(req: WebResourceRequest): Boolean =
        req.requestHeaders.entries.any { (k, v) -> k.equals("Accept", ignoreCase = true) && v.startsWith("image/") }

    /** [stream], a JPEG, with its EXIF orientation reading normal. */
    fun ignored(stream: InputStream): InputStream {
        val head = ByteArray(HEAD)
        var n = 0
        while (n < HEAD) { val r = stream.read(head, n, HEAD - n); if (r < 0) break; n += r }
        clear(head, n)
        return SequenceInputStream(ByteArrayInputStream(head, 0, n), stream)
    }

    /** Sets IFD0's Orientation (0x0112) in the APP1 Exif segment of b[0, n) to 1. */
    private fun clear(b: ByteArray, n: Int) {
        fun u8(i: Int) = b[i].toInt() and 0xff
        if (n < 4 || u8(0) != 0xff || u8(1) != 0xd8) return
        var i = 2
        while (i + 4 <= n && u8(i) == 0xff) {
            val marker = u8(i + 1)
            val len = (u8(i + 2) shl 8) or u8(i + 3)
            if (marker == 0xda || len < 2) return  // the image data: no EXIF before it
            if (marker == 0xe1 && i + 10 <= n && String(b, i + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                val t = i + 10  // the TIFF header
                val end = minOf(n, i + 2 + len)
                if (t + 8 > end) return
                val le = u8(t) == 0x49
                fun u16(p: Int) = if (le) u8(p) or (u8(p + 1) shl 8) else (u8(p) shl 8) or u8(p + 1)
                fun u32(p: Int) = if (le) u16(p).toLong() or (u16(p + 2).toLong() shl 16) else (u16(p).toLong() shl 16) or u16(p + 2).toLong()
                val ifd = t + u32(t + 4).toInt()
                if (ifd < t || ifd + 2 > end) return
                for (e in 0 until u16(ifd)) {
                    val p = ifd + 2 + e * 12
                    if (p + 12 > end) return
                    if (u16(p) == 0x0112) {
                        // A SHORT, in the first two bytes of the value field.
                        if (le) { b[p + 8] = 1; b[p + 9] = 0 } else { b[p + 8] = 0; b[p + 9] = 1 }
                        return
                    }
                }
                return
            }
            i += 2 + len
        }
    }
}
