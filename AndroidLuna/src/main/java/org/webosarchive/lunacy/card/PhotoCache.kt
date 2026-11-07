package org.webosarchive.lunacy.card

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Photos & Videos' screennails at the screen's size (LunaRuntimes/photos-service, 0001).
 *
 * The photos service fits each picture's screennail, what Photos' full view shows, into a box the
 * screen's size: the screen's longer side in pixels, both ways ([screenPx], given to JS services
 * as LUNACY_SCREEN_PX). Screennails made at another size, by the TouchPad's 1024 x 768 box before
 * 2026-11-07 or on another screen, are cleared once: their files deleted and `appScreenNail`
 * nulled, so Photos asks the service for each again as it shows it.
 */
object PhotoCache {
    /** The longer side, in pixels, of the device's largest screen (a foldable's inner one). */
    @Volatile var screenPx = 0
        private set

    fun measure(context: Context) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        screenPx = dm.displays.maxOfOrNull { d ->
            val p = android.graphics.Point(); @Suppress("DEPRECATION") d.getRealSize(p); maxOf(p.x, p.y)
        } ?: 0
    }

    /** Clears screennails made at another size; once db8 and the webOS root are there. */
    fun refresh(context: Context, bus: Bus, webosRoot: File) {
        val prefs = context.getSharedPreferences("photos", Context.MODE_PRIVATE)
        if (screenPx <= 0 || prefs.getInt(SIDE, 0) == screenPx) return
        val generated = File(webosRoot, "media/internal/.photosApp/Generated")
        val gone = generated.listFiles()?.filter { it.name.startsWith("appScreenNail-") }?.count { it.delete() } ?: 0
        val p = JSONObject().put("query", JSONObject().put("from", MediaIndex.IMAGE))
            .put("props", JSONObject().put("appScreenNail", JSONObject.NULL))
        // As the photos service, whose property it is.
        bus.call(PHOTOS_SERVICE, "palm://com.palm.db/merge", p.toString(), privateBus = true) { r ->
            if (Bus.succeeded(r)) {
                prefs.edit().putInt(SIDE, screenPx).apply()
                Log.i(AppServer.TAG, "photos: screennails cleared for a ${screenPx}-px screen ($gone files)")
            } else Log.w(AppServer.TAG, "photos: couldn't clear screennails: ${Bus.errorText(r)}")
        }
    }

    private const val SIDE = "screenNailSide"
    private const val PHOTOS_SERVICE = "com.palm.service.photos"
}
