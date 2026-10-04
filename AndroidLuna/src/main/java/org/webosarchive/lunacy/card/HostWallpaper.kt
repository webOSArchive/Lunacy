package org.webosarchive.lunacy.card

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.InputStream

/**
 * Android's own wallpaper, following webOS's: an opt-in (codepoet, 2026-10-04), off until the
 * owner turns it on in Screen & Lock. webOS had no host to share a wallpaper with, so nothing
 * Lunacy draws changes; the shell keeps drawing its own wallpaper view either way. What
 * follows is Android's home screen and, from Android 7, its lock screen. Android 5 and 6 have
 * one wallpaper, which their own lock screen shows.
 *
 * Turning it off leaves Android's wallpaper as it is: the one the owner had before is gone
 * once it has been replaced, and Android's default would be no nearer to it.
 */
object HostWallpaper {
    private const val PREFS = "hostwallpaper"
    private const val KEY = "on"
    private const val TAG = "HostWallpaper"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /**
     * Sets Android's wallpaper to the image [open] reads: the shell's, which is the owner's
     * pick or else the one Lunacy ships. Slow (Android decodes and stores the image): never
     * on the main thread. Returns why it couldn't, or null.
     */
    fun apply(context: Context, open: () -> InputStream): String? {
        val wm = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= 24 && !wm.isSetWallpaperAllowed) return "Android doesn't allow apps to set the wallpaper on this device"
        return try {
            open().use { input ->
                if (Build.VERSION.SDK_INT >= 24) {
                    wm.setStream(input, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                } else {
                    wm.setStream(input)
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "setting Android's wallpaper", e)
            "Unable to set Android's wallpaper: ${e.message ?: e}"
        }
    }

    /** [apply] if the owner has turned it on. */
    fun follow(context: Context, open: () -> InputStream) {
        if (isOn(context)) apply(context, open)
    }
}
