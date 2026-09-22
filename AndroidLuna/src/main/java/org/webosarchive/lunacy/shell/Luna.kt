package org.webosarchive.lunacy.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * LunaCE's look: its images (assets/luna/images, Apache 2.0) and fonts. Sizes in the shell
 * are TouchPad pixels. See Docs/luna-shell-reference.md.
 */
class Luna(private val context: Context) {
    /**
     * Android pixels per TouchPad pixel. On tablets the screen's short side is about 768
     * TouchPad px, as on a TouchPad, so the shell keeps the TouchPad's proportions whatever the
     * vendor's density setting. Phones use the Android density until the phone layout exists.
     */
    val density: Float = run {
        val real = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(real)
        // Rounded to a whole number: at fractional scales the slices of nine-patch and
        // -webkit-border-image graphics land between device pixels and show faint seams.
        if (context.resources.configuration.smallestScreenWidthDp >= 600) maxOf(1f, Math.round(minOf(real.widthPixels, real.heightPixels) / 768f).toFloat())
        else real.density
    }
    private val targetDpi = (160 * density).toInt()
    private val bitmaps = HashMap<String, Bitmap?>()
    private val fonts = HashMap<String, Typeface>()

    /** TouchPad pixels to Android pixels. */
    /** SplashIconSize, from the reference TouchPad's luna-platform.conf. */
    val SPLASH_ICON_SIZE = 192

    fun px(tp: Float) = tp * density
    fun px(tp: Int) = (tp * density).toInt()

    /** An image from LunaCE's images/ folder, at its TouchPad size. */
    fun image(path: String): Bitmap? = bitmaps.getOrPut(path) {
        try {
            context.assets.open("luna/images/$path").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inDensity = 160; inTargetDensity = targetDpi; inScaled = true })
            }
        } catch (e: Exception) { null }
    }

    /** Decodes an image stream drawn in TouchPad px (app icons, dashboard icons) at shell scale. */
    fun decode(stream: java.io.InputStream?): Bitmap? = try {
        stream?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inDensity = 160; inTargetDensity = targetDpi; inScaled = true }) }
    } catch (e: Exception) { null }

    /** An app's icon (bundled or installed), scaled like the Luna images; cached per version. */
    fun appIcon(app: org.webosarchive.lunacy.card.AppInfo): Bitmap? =
        bitmaps.getOrPut("icon:${app.id}:${app.version}") { decode(app.openIcon()) }

    /**
     * An app's mini icon, which a dashboard or banner without an icon of its own shows in the
     * status bar: the app's `miniicon`, or else - as ApplicationDescription::miniIcon made one -
     * its launcher icon squeezed to positiveSpaceBottomPadding (28 px) square and turned grey,
     * each pixel the average of its red, green and blue. Measured on the reference TouchPad:
     * a dashboard from an app without a mini icon shows its icon grey.
     */
    fun miniIcon(app: org.webosarchive.lunacy.card.AppInfo): Bitmap? =
        bitmaps.getOrPut("mini:${app.id}:${app.version}") {
            decode(app.openMiniIcon())?.let { return@getOrPut it }
            val icon = decode(app.openIcon()) ?: return@getOrPut null
            val size = px(28)
            val b = Bitmap.createScaledBitmap(icon, size, size, true).copy(Bitmap.Config.ARGB_8888, true)
            val px = IntArray(size * size)
            b.getPixels(px, 0, size, 0, 0, size, size)
            for (i in px.indices) {
                val c = px[i]
                val avg = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3
                px[i] = (c and 0xFF000000.toInt()) or (avg shl 16) or (avg shl 8) or avg
            }
            b.setPixels(px, 0, size, 0, 0, size, size)
            b
        }

    /**
     * The icon for an app's loading card: its own `splashicon` at SplashIconSize, else its
     * launcher icon at half again, capped at the same size (LunaCE's `CardLoading`; the size
     * is 192 on a TouchPad, from luna-topaz.conf). In TouchPad px, scaled like the rest.
     */
    fun splashIcon(app: org.webosarchive.lunacy.card.AppInfo): Bitmap? =
        bitmaps.getOrPut("splash:${app.id}:${app.version}") {
            val size = px(SPLASH_ICON_SIZE)
            val own = decode(app.openSplashIcon())
            val bmp = own ?: appIcon(app) ?: return@getOrPut null
            val want = if (own != null) size else minOf(size, (maxOf(bmp.width, bmp.height) * 1.5f).toInt())
            val longest = maxOf(bmp.width, bmp.height)
            if (longest <= 0 || longest == want) bmp
            else Bitmap.createScaledBitmap(bmp, bmp.width * want / longest, bmp.height * want / longest, true)
        }

    /** HP's Prelude fonts (assets/luna/fonts, shipped as abandonware); the system sans if one is missing. */
    fun font(name: String, fallbackStyle: Int = Typeface.NORMAL): Typeface = fonts.getOrPut(name) {
        try { Typeface.createFromAsset(context.assets, "luna/fonts/$name") } catch (e: Exception) { Typeface.create("sans-serif", fallbackStyle) }
    }
    val fontMedium get() = font("Prelude-Medium.ttf")
    val fontBold get() = font("Prelude-Bold.ttf", Typeface.BOLD)
    val fontLight get() = font("PreludeWGL-Light.ttf")

    /** An image file at its own size: a wallpaper the user picked, which isn't shell art. */
    fun decodeFull(file: java.io.File): Bitmap? = try {
        BitmapFactory.decodeFile(file.path)
    } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }

    /** A TouchPad wallpaper (assets/luna/wallpapers, shipped as abandonware). */
    fun wallpaper(name: String = DEFAULT_WALLPAPER): Bitmap? = try {
        context.assets.open("luna/wallpapers/$name").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

    /** Draws a Luna image as a nine-slice, insets given in TouchPad px (the image's own pixels). */
    fun nine(c: Canvas, path: String, dst: RectF, l: Int, t: Int, r: Int, b: Int, p: Paint? = null) {
        val bmp = image(path) ?: return
        val k = bmp.width.toFloat() / imageTpWidth(path)
        drawNineSlice(c, bmp, dst, (l * k).toInt(), (t * k).toInt(), (r * k).toInt(), (b * k).toInt(), p, l * density, t * density, r * density, b * density)
    }

    /**
     * Draws one rect of a Luna image, centred at (cx, cy). Source coordinates are the image's
     * own (TouchPad) pixels: LunaCE's two-state sprites keep their states at documented rects
     * inside a larger canvas, not as halves of it (Docs/luna-shell-reference.md §3.8).
     */
    fun sprite(c: Canvas, path: String, cx: Float, cy: Float, x: Int, y: Int, w: Int, h: Int) {
        val bmp = image(path) ?: return
        val k = bmp.width.toFloat() / imageTpWidth(path)
        val src = Rect((x * k).toInt(), (y * k).toInt(), ((x + w) * k).toInt(), ((y + h) * k).toInt())
        val halfW = px(w / 2f); val halfH = px(h / 2f)
        c.drawBitmap(bmp, src, RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH), null)
    }

    /** Tiles a Luna image over dst from its top-left, at TouchPad scale. */
    fun tile(c: Canvas, path: String, dst: RectF, p: Paint? = null) {
        val bmp = image(path) ?: return
        c.save(); c.clipRect(dst)
        var y = dst.top
        while (y < dst.bottom) { var x = dst.left; while (x < dst.right) { c.drawBitmap(bmp, x, y, p); x += bmp.width }; y += bmp.height }
        c.restore()
    }

    private val tpWidths = HashMap<String, Int>()
    /** An image's width in its own (TouchPad) pixels. */
    private fun imageTpWidth(path: String): Int = tpWidths.getOrPut(path) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try { context.assets.open("luna/images/$path").use { BitmapFactory.decodeStream(it, null, o) } } catch (e: Exception) {}
        if (o.outWidth > 0) o.outWidth else 1
    }

    companion object {
        /** The wallpaper the reference TouchPad uses. Choosing one comes with the settings UI. */
        const val DEFAULT_WALLPAPER = "22.jpg"

        /** Draws a bitmap stretched into dst as a nine-slice with the given insets (in bitmap pixels). */
        fun drawNineSlice(c: Canvas, b: Bitmap, dst: RectF, l: Int, t: Int, r: Int, bo: Int, p: Paint? = null, dl: Float = l.toFloat(), dt: Float = t.toFloat(), dr: Float = r.toFloat(), db: Float = bo.toFloat()) {
            val w = b.width; val h = b.height
            val xs = intArrayOf(0, l, w - r, w); val ys = intArrayOf(0, t, h - bo, h)
            val dx = floatArrayOf(dst.left, dst.left + dl, dst.right - dr, dst.right)
            val dy = floatArrayOf(dst.top, dst.top + dt, dst.bottom - db, dst.bottom)
            val src = Rect(); val d = RectF()
            for (i in 0..2) for (j in 0..2) {
                src.set(xs[i], ys[j], xs[i + 1], ys[j + 1]); d.set(dx[i], dy[j], dx[i + 1], dy[j + 1])
                if (src.width() > 0 && src.height() > 0 && d.width() > 0 && d.height() > 0) c.drawBitmap(b, src, d, p)
            }
        }
    }
}
