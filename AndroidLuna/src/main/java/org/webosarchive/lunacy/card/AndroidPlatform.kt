package org.webosarchive.lunacy.card

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.webosarchive.lunacy.core.Assets
import org.webosarchive.lunacy.core.Images
import java.io.File
import java.io.InputStream

/** The APK's assets, as LunaCore's [Assets]. */
class AndroidAssets(val manager: AssetManager) : Assets {
    override fun open(path: String): InputStream = manager.open(path)
    override fun list(path: String): Array<String>? = manager.list(path)
}

/** BitmapFactory, as LunaCore's [Images]: what AppRegistry and AppServer did with it before LunaCore. */
object AndroidImages : Images {
    override fun size(input: InputStream): IntArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, bounds)
        return if (bounds.outWidth > 0) intArrayOf(bounds.outWidth, bounds.outHeight) else null
    }

    override fun jpegThumbnail(file: File, px: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = px.toFloat() / minOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * scale), Math.round(bmp.height * scale), true) else bmp
        val bytes = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 80, bytes)
        if (out !== bmp) out.recycle()
        bmp.recycle()
        return bytes.toByteArray()
    }
}

/** The WebViews' cookie jar, as LunaCore's [org.webosarchive.lunacy.core.Cookies]: native requests and pages share it. */
object AndroidCookies : org.webosarchive.lunacy.core.Cookies {
    override fun get(url: String): String? = android.webkit.CookieManager.getInstance().getCookie(url)
    override fun set(url: String, setCookie: String) { android.webkit.CookieManager.getInstance().setCookie(url, setCookie) }
}
