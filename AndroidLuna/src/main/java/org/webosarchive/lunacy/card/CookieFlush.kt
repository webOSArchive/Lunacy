package org.webosarchive.lunacy.card

/**
 * Writes WebView's cookies to disk. Chromium flushes them on its own schedule, some seconds
 * after a write; webOS had them on disk at once, and apps (Mojo.Model.Cookie) keep settings and
 * logins there, which Android killing Lunacy in between would lose. Main thread.
 */
object CookieFlush {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val flush = Runnable { android.webkit.CookieManager.getInstance().flush() }
    /** After a write: batched, since a page often writes several at once. */
    fun soon() { main.removeCallbacks(flush); main.postDelayed(flush, 300) }
    fun now() { main.removeCallbacks(flush); flush.run() }
}
