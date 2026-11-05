package org.webosarchive.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

/**
 * webOS CE's emoji layer (LunaCE edc7408, bfce491): the emoji in key order, a page at a time
 * over the character keys of the top four rows, each drawn from its OpenMoji image. The list is
 * CE's own keyboard-emoji.txt and the images the same OpenMoji art; see assets/emoji/NOTICE.
 */
class Emoji(private val context: Context) {
    /** What each emoji key types, in key order: four pages of 38 on this layout. */
    val all: List<String> by lazy {
        try {
            context.assets.open("emoji/keyboard-emoji.txt").bufferedReader().readLines()
                .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                .flatMap { it.trim().split(Regex("\\s+")) }
        } catch (e: Exception) { emptyList() }
    }

    private val bitmaps = HashMap<String, Bitmap?>()

    /**
     * [emoji]'s image, [size] device px square, or null if it isn't there (CE then drew the text).
     * [gray] is the emoji key's own smiley, which sits among the gray function keys: luminance,
     * rescaled so the brightest part (the face) lands on their label gray, as CE drew it.
     */
    fun image(emoji: String, size: Int, gray: Boolean = false): Bitmap? = bitmaps.getOrPut("$emoji@$size${if (gray) "g" else ""}") {
        val source = try {
            context.assets.open("emoji/${fileName(emoji)}.png").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
            }
        } catch (e: Exception) { null } ?: return@getOrPut null
        val scaled = Bitmap.createScaledBitmap(source, size, size, true).copy(Bitmap.Config.ARGB_8888, true)
        if (gray) grayed(scaled, FUNCTION_GRAY)
        scaled
    }

    companion object {
        /** The function keys' label gray, LunaCE's cFunctionColor. */
        const val FUNCTION_GRAY = 0xd2
        /** The emoji the emoji key wears. */
        const val KEY_FACE = "😀"

        /** OpenMoji's name for it: its code points in upper-case hex, joined by "-", without U+FE0F. */
        fun fileName(emoji: String): String {
            val cps = ArrayList<String>()
            var i = 0
            while (i < emoji.length) {
                val cp = emoji.codePointAt(i)
                if (cp != 0xFE0F) cps += Integer.toHexString(cp).uppercase()
                i += Character.charCount(cp)
            }
            return cps.joinToString("-")
        }

        /** Qt's qGray, rescaled so the brightest opaque pixel comes out at [top]. */
        private fun grayed(b: Bitmap, top: Int) {
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            fun lum(p: Int) = (Color.red(p) * 11 + Color.green(p) * 16 + Color.blue(p) * 5) / 32
            var bright = 1
            for (p in px) if (Color.alpha(p) > 128 && lum(p) > bright) bright = lum(p)
            for (i in px.indices) {
                val v = Math.min(255, lum(px[i]) * top / bright)
                px[i] = Color.argb(Color.alpha(px[i]), v, v, v)
            }
            b.setPixels(px, 0, b.width, 0, 0, b.width, b.height)
        }
    }
}
