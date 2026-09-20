package org.webosarchive.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * The keyboard's pictures and letters: LunaCE's key art and HP's Prelude fonts.
 *
 * Everything is drawn at the art's own pixel size. LunaCE calls these the assets' "ideal"
 * sizes, and its own keyboard only departed from them when the owner asked for a smaller
 * keyboard - which cost it sharp glyphs and sharp key edges. Nothing here is ever scaled:
 * keys grow by stretching their flat middles, and never their bevels or their lettering.
 */
class Art(private val context: Context) {
    private val bitmaps = HashMap<String, Bitmap?>()
    private val fonts = HashMap<String, Typeface>()

    /** An image from LunaCE's keyboard-tablet folder, at its own pixel size. */
    fun image(name: String): Bitmap? = bitmaps.getOrPut(name) {
        try {
            context.assets.open("keyboard/$name").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
            }
        } catch (e: Exception) { null }
    }

    /** Prelude, the face webOS set the keyboard in; the system sans if it isn't here. */
    fun font(name: String): Typeface = fonts.getOrPut(name) {
        try { Typeface.createFromAsset(context.assets, "fonts/$name") } catch (e: Exception) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    companion object {
        /** The key plates' nine-tile corner (LunaCE's `m_9tileCorner`). */
        const val CORNER = 13

        /**
         * Draws one state of a key plate. LunaCE's plates are two-state sprites: the released
         * key sits above the pressed one, so a 93x140 image is really two 93x70 keys. A key is
         * as tall as the plate, so only its width ever stretches.
         */
        fun plate(c: Canvas, b: Bitmap, dst: RectF, pressed: Boolean, p: Paint? = null) {
            val h = b.height / 2
            val top = if (pressed) h else 0
            nine(c, b, Rect(0, top, b.width, top + h), dst, CORNER, p)
        }

        /** Stretches src into dst as a nine-slice, keeping the corners at their own size. */
        fun nine(c: Canvas, b: Bitmap, src: Rect, dst: RectF, corner: Int, p: Paint? = null) {
            // On a screen narrow enough to squeeze a key below two corners, the corners give
            // way rather than overlapping each other.
            val dh = Math.min(corner.toFloat(), dst.width() / 2)
            val dv = Math.min(corner.toFloat(), dst.height() / 2)
            val xs = intArrayOf(src.left, src.left + corner, src.right - corner, src.right)
            val ys = intArrayOf(src.top, src.top + corner, src.bottom - corner, src.bottom)
            val dx = floatArrayOf(dst.left, dst.left + dh, dst.right - dh, dst.right)
            val dy = floatArrayOf(dst.top, dst.top + dv, dst.bottom - dv, dst.bottom)
            val s = Rect(); val d = RectF()
            for (i in 0..2) for (j in 0..2) {
                s.set(xs[i], ys[j], xs[i + 1], ys[j + 1]); d.set(dx[i], dy[j], dx[i + 1], dy[j + 1])
                if (s.width() > 0 && s.height() > 0 && d.width() > 0 && d.height() > 0) c.drawBitmap(b, s, d, p)
            }
        }

        /**
         * Stretches an image's middle horizontally, keeping both ends. The popup balloon's
         * pointer is part of its left end, so it has to survive being widened.
         */
        fun stretchH(c: Canvas, b: Bitmap, dst: RectF, left: Int, right: Int, p: Paint? = null) {
            val h = b.height
            c.drawBitmap(b, Rect(0, 0, left, h), RectF(dst.left, dst.top, dst.left + left, dst.bottom), p)
            c.drawBitmap(b, Rect(left, 0, b.width - right, h), RectF(dst.left + left, dst.top, dst.right - right, dst.bottom), p)
            c.drawBitmap(b, Rect(b.width - right, 0, b.width, h), RectF(dst.right - right, dst.top, dst.right, dst.bottom), p)
        }

        /** Draws a bitmap at its own size, centred on a point and snapped to whole pixels. */
        fun centred(c: Canvas, b: Bitmap, cx: Float, cy: Float, p: Paint? = null) {
            c.drawBitmap(b, Math.round(cx - b.width / 2f).toFloat(), Math.round(cy - b.height / 2f).toFloat(), p)
        }
    }
}
