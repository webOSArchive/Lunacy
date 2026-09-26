package org.webosarchive.lunacy.shell

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.webosarchive.lunacy.card.AppServer

/**
 * Has Prelude decoded before the first app asks for it.
 *
 * Every page waits for its fonts before its own scripts run (AppServer, "the fonts"), and
 * that wait is only as short as the fonts are quick. Chromium checks and decodes a web font
 * once per URL and keeps the result for every card, but the first time costs about 0.4 s on the
 * HP 10 G2 - and so does the first WebView, which Chromium sets up on first use. Left to the
 * first app, both land on its launch. So once the shell is up and idle, a WebView nobody sees
 * loads a page that asks for the default faces, and is thrown away when they are in.
 *
 * A device had its fonts installed and needed none of this; it is here so an app's first
 * script measures text in Prelude without the first launch paying for it.
 */
class FontWarmer(private val context: Context, private val server: AppServer) {
    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null

    /** Runs once the main thread has nothing else to do. */
    fun warmWhenIdle() {
        Looper.myQueue().addIdleHandler { warm(); false }
    }

    private fun warm() {
        if (view != null) return
        val start = System.currentTimeMillis()
        val v = WebView(context)
        view = v
        v.settings.javaScriptEnabled = true
        v.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? =
                server.serve(req.url)
        }
        v.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                if (title != AppServer.WARM_DONE) return
                Log.i(AppServer.TAG, "fonts warmed in ${System.currentTimeMillis() - start} ms")
                finish()
            }
        }
        v.loadUrl(AppServer.WARM_URL)
        // Whatever happens, the view doesn't outlive the warm-up.
        main.postDelayed({ finish() }, 10_000)
    }

    private fun finish() {
        val v = view ?: return
        view = null
        main.post { v.stopLoading(); v.destroy() }
    }
}
