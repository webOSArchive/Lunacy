package org.webosarchive.lunacy.card

import android.content.Context
import android.webkit.WebSettings

/**
 * The WebView's Chromium major version. A page can't read it: its user agent is the webOS
 * device's (DeviceProfile). Workarounds for one engine's bugs are kept to that engine by it.
 */
object Engine {
    @Volatile var major = 0
        private set

    /** Reads it once; on the main thread, before the first page loads. */
    fun init(context: Context) {
        if (major != 0) return
        major = runCatching {
            Regex("Chrome/(\\d+)").find(WebSettings.getDefaultUserAgent(context))?.groupValues?.get(1)?.toInt()
        }.getOrNull() ?: -1
    }
}
