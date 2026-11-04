package org.webosarchive.lunacy.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

/**
 * Android icons drawn as [iconPx] PNGs and kept by key, a miss too: Android's launcher apps
 * ([AndroidApps]) and the shortcuts pinned to Lunacy ([PinnedShortcuts]). Keys begin with the
 * owner's prefix and the package, so a package that changes can be forgotten.
 */
class AndroidIconCache(private val context: Context, private val prefix: String, val iconPx: Int) {
    private val icons = java.util.Collections.synchronizedMap(HashMap<String, ByteArray?>())

    /** The icon for [key], drawn by [draw] the first time. */
    fun get(key: String, draw: () -> ByteArray?): ByteArray? = synchronized(icons) {
        if (icons.containsKey(key)) icons[key] else draw().also { icons[key] = it }
    }

    fun remove(key: String) { icons.remove(key) }

    /** A package was added, changed or removed: its icons are drawn again next time. */
    fun forget(packageName: String) { icons.keys.removeAll { it.startsWith("$prefix$packageName/") } }

    /**
     * The density to ask an app's art at: one that makes a 48 dp launcher icon at least
     * [iconPx], as a launcher does with ActivityManager's launcherLargeIconDensity.
     */
    val dpi get() = maxOf((iconPx * 160 + 47) / 48, (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).launcherLargeIconDensity)

    fun png(d: Drawable): ByteArray {
        val b = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, iconPx, iconPx)
        d.draw(Canvas(b))
        return png(b)
    }

    fun png(b: Bitmap): ByteArray {
        val scaled = if (b.width == iconPx && b.height == iconPx) b else Bitmap.createScaledBitmap(b, iconPx, iconPx, true)
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
