package org.webosarchive.lunacy.card

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * palm://com.palm.image: webOS's image service, part of the system service (LunaSysService's
 * ImageServices, released by Open webOS). Contacts crops a contact's photo with `convert` and
 * scales its list photo with `ezResize`.
 *
 * Measured on the reference TouchPad (webOS CE 3.1.0) with a 400x200 test image:
 * - `convert` with focusX/focusY/scale/cropW/cropH scales the source by `scale` and cuts a
 *   cropW x cropH window from it centred on (focusX, focusY) - but never past the scaled
 *   image: a window that would cross an edge is moved inside, and one larger than the scaled
 *   image is cut down to it (scale 0.25 and a 200x200 crop of the 400x200 test image gave
 *   100x50). A scale of exactly 1 gives the whole image, uncropped. With none of those
 *   parameters it just re-encodes.
 * - `ezResize` stretches the source to destSizeW x destSizeH, ignoring its aspect ratio.
 * - `imageInfo` gives width, height, bpp and type ("png", "jpeg").
 * - Every reply carries `"subscribed": false`; a failure is `returnValue: false` with an
 *   `errorCode` string ("source file does not exist").
 *
 * Paths are webOS paths: /media/internal resolves through [UserFiles], anything else inside
 * the webOS root (the file cache's /var/file-cache, for one). The work runs off the main thread.
 */
class ImageService(private val webosRoot: File) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "lunacy-image").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun register(bus: Bus) {
        for ((method, handler) in listOf<Pair<String, (JSONObject) -> JSONObject>>(
            "convert" to ::convert, "ezResize" to ::ezResize, "imageInfo" to ::imageInfo,
        )) bus.register(SERVICE, method) { _, p, reply ->
            worker.execute {
                val r = try { handler(p) } catch (e: ImageError) { fail(e.message ?: "error") } catch (e: Throwable) { fail("$method: $e") }
                main.post { reply(r.toString()) }
            }
        }
    }

    private class ImageError(message: String) : Exception(message)

    private fun ok() = JSONObject().put("subscribed", false).put("returnValue", true)
    private fun fail(text: String) = JSONObject().put("subscribed", false).put("returnValue", false).put("errorCode", text)

    private fun string(p: JSONObject, key: String): String =
        p.optString(key).takeIf { p.has(key) && it.isNotEmpty() } ?: throw ImageError("'$key' parameter missing")

    private fun file(path: String): File? {
        val prefix = "/media/internal"
        if (path == prefix || path.startsWith("$prefix/")) return UserFiles.resolve(webosRoot, path.removePrefix(prefix))
        val f = File(webosRoot, path.trimStart('/'))
        return f.takeIf { it.canonicalPath.startsWith(webosRoot.canonicalPath + File.separator) }
    }

    private fun source(p: JSONObject): File {
        val f = file(string(p, "src"))
        if (f == null || !f.isFile) throw ImageError("source file does not exist")
        return f
    }

    /** The source decoded no larger than it needs to be for [maxSide] output pixels, and the factor it was shrunk by. */
    private fun decode(src: File, maxSide: Int = Int.MAX_VALUE): Pair<Bitmap, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0) throw ImageError("Unable to read image")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw ImageError("Unable to read image")
        return bmp to sample
    }

    private fun save(bmp: Bitmap, dest: String, type: String) {
        val f = file(dest) ?: throw ImageError("Unable to write destination file")
        f.parentFile?.mkdirs()
        val format = when (type.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            else -> throw ImageError("Unsupported destination type: $type")
        }
        f.outputStream().use { if (!bmp.compress(format, 100, it)) throw ImageError("failed to save destination file") }
    }

    private fun canvasFor(w: Int, h: Int, opaque: Boolean): Pair<Bitmap, Canvas> {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (opaque) out.eraseColor(Color.BLACK)
        return out to Canvas(out)
    }

    private fun convert(p: JSONObject): JSONObject {
        val src = source(p)
        val dest = string(p, "dest")
        val type = string(p, "destType")
        val crop = listOf("focusX", "focusY", "scale", "cropW", "cropH").any { p.has(it) }
        if (!crop) {
            val (bmp, _) = decode(src)
            save(bmp, dest, type)
            bmp.recycle()
            return ok()
        }
        val focusX = p.optDouble("focusX", 0.5)
        val focusY = p.optDouble("focusY", 0.5)
        if (focusX !in 0.0..1.0) throw ImageError("'focusX' parameter out of range (must be [0.0,1.0] )")
        if (focusY !in 0.0..1.0) throw ImageError("'focusY' parameter out of range (must be [0.0,1.0] )")
        val scale = p.optDouble("scale", 1.0)
        if (scale <= 0) throw ImageError("'scale' parameter out of range ( must be > 0.0 )")
        val cropW = p.optDouble("cropW", 0.0).toInt()
        val cropH = p.optDouble("cropH", 0.0).toInt()
        if (cropW <= 0 || cropH <= 0) throw ImageError("'cropW' parameter out of range (must be > 0 )")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0) throw ImageError("Unable to read image")
        if (scale == 1.0) {
            val (bmp, _) = decode(src)
            save(bmp, dest, type)
            bmp.recycle()
            return ok()
        }
        // The scaled image, and the window cut from it: clamped to its size, then kept inside it.
        val sw = bounds.outWidth * scale; val sh = bounds.outHeight * scale
        val outW = minOf(cropW.toDouble(), sw).roundToInt().coerceAtLeast(1)
        val outH = minOf(cropH.toDouble(), sh).roundToInt().coerceAtLeast(1)
        val left = (focusX * sw - outW / 2.0).coerceIn(0.0, maxOf(0.0, sw - outW))
        val top = (focusY * sh - outH / 2.0).coerceIn(0.0, maxOf(0.0, sh - outH))
        // A photo is decoded only as finely as the output needs.
        val (bmp, sample) = decode(src, (maxOf(bounds.outWidth, bounds.outHeight) * minOf(1.0, scale * 2)).toInt().coerceAtLeast(1))
        val s = scale * sample  // per decoded pixel
        val (out, canvas) = canvasFor(outW, outH, type.lowercase() != "png")
        val m = Matrix().apply {
            postScale(s.toFloat(), s.toFloat())
            postTranslate(-left.toFloat(), -top.toFloat())
        }
        canvas.drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
        save(out, dest, type)
        out.recycle()
        return ok()
    }

    private fun ezResize(p: JSONObject): JSONObject {
        val src = source(p)
        val dest = string(p, "dest")
        val type = string(p, "destType")
        if (!p.has("destSizeW")) throw ImageError("'destSizeW' missing")
        if (!p.has("destSizeH")) throw ImageError("'destSizeH' missing")
        val w = p.optInt("destSizeW"); val h = p.optInt("destSizeH")
        if (w <= 0 || h <= 0) throw ImageError("ezResize: unable to allocate memory for QImage")
        val (bmp, _) = decode(src, maxOf(w, h) * 2)
        val (out, canvas) = canvasFor(w, h, type.lowercase() != "png")
        canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
        save(out, dest, type)
        out.recycle()
        return ok()
    }

    private fun imageInfo(p: JSONObject): JSONObject {
        val src = source(p)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, o)
        if (o.outWidth <= 0) throw ImageError("Unable to read image")
        val type = o.outMimeType?.substringAfter('/')?.lowercase().orEmpty()
        return ok().put("width", o.outWidth).put("height", o.outHeight).put("bpp", 8).put("type", type)
    }

    companion object { const val SERVICE = "com.palm.image" }
}
