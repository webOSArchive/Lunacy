package org.webosarchive.lunacy.spike

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Message
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

private const val TAG = "LunacySpike"

/**
 * Global serve-time CSS transforms. Each is a mechanical rule applied to every app's CSS;
 * none names an app.
 */
object CssTransforms {
    private val rule = Regex("\\{[^{}]*\\}")
    private val borderImage = Regex("-webkit-border-image\\s*:(?!\\s*none)", RegexOption.IGNORE_CASE)

    fun apply(s: InputStream): InputStream {
        val css = s.bufferedReader().readText()
        return ByteArrayInputStream(borderImageNeedsStyle(css).toByteArray())
    }

    /**
     * 2011 WebKit drew -webkit-border-image whatever the border-style. Newer Chromium
     * computes border-width to 0 when border-style is none, so the image vanishes.
     * Appended to the rule: in 2011 WebKit a border-image showed even next to an explicit
     * `border-style: none`, and Enyo's own CSS relies on that.
     */
    fun borderImageNeedsStyle(css: String): String = rule.replace(css) { m ->
        if (borderImage.containsMatchIn(m.value)) m.value.dropLast(1) + ";border-style:solid;border-color:transparent}" else m.value
    }
}

object Paths {
    const val HOST_SUFFIX = ".media.cryptofs.apps"
    const val APPS = "media/cryptofs/apps/usr/palm/applications/"
    fun appUrl(id: String) = "https://$id$HOST_SUFFIX/$APPS$id/index.html"
}

/** One card: a WebView serving a webOS app from its own origin. */
class CardActivity : Activity() {
    private lateinit var web: WebView          // root window
    private val windows = mutableListOf<WebView>()  // all windows, top last
    private lateinit var stack: FrameLayout
    private val main = Handler(Looper.getMainLooper())
    private var injectBridge = true

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injectBridge = intent.getBooleanExtra("bridge", true)
        val url = intent.getStringExtra("url") ?: return finish()
        Log.i(TAG, "open $url bridge=$injectBridge webview=${WebView::class.java.`package`}")
        WebView.setWebContentsDebuggingEnabled(true)
        stack = FrameLayout(this)
        setContentView(stack)
        web = newWindow()
        web.loadUrl(url)
    }

    /** One webOS window (card, dashboard, popup). Children come from window.open. */
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun newWindow(): WebView {
        val w = WebView(this)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            @Suppress("DEPRECATION")
            setDatabasePath(getDir("websql", MODE_PRIVATE).path)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.i(TAG, "console[${windows.indexOf(w)}] ${m.messageLevel()} ${m.sourceId()?.substringAfterLast('/')}:${m.lineNumber()} ${m.message()}")
                return true
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val child = newWindow()
                Log.i(TAG, "window.open -> window ${windows.indexOf(child)} (dialog=$isDialog)")
                (resultMsg.obj as WebView.WebViewTransport).webView = child
                resultMsg.sendToTarget()
                return true
            }
            override fun onCloseWindow(window: WebView) {
                Log.i(TAG, "window.close ${windows.indexOf(window)}")
                closeWindow(window)
            }
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? =
                serve(req.url)
            override fun onPageFinished(view: WebView, url: String) { Log.i(TAG, "loaded[${windows.indexOf(w)}] $url") }
        }
        if (injectBridge) w.addJavascriptInterface(Native(w), "LunacyNative")
        windows += w
        stack.addView(w)
        return w
    }

    private fun closeWindow(w: WebView) {
        windows.remove(w); stack.removeView(w); w.destroy()
        if (windows.isEmpty()) finish()
    }

    private fun top() = windows.last()


    override fun onBackPressed() {
        // webOS back gesture arrives as Escape (keyCode 27).
        top().evaluateJavascript("(function(){var e=document.createEvent('Events');e.initEvent('keydown',true,true);e.keyCode=27;e.which=27;(document.activeElement||document).dispatchEvent(e);var u=document.createEvent('Events');u.initEvent('keyup',true,true);u.keyCode=27;u.which=27;(document.activeElement||document).dispatchEvent(u);})()", null)
    }

    override fun onDestroy() { windows.toList().forEach { it.destroy() }; super.onDestroy() }

    // ---- serving ----

    private fun serve(uri: Uri): WebResourceResponse? {
        val host = uri.host ?: return null
        if (!host.endsWith(Paths.HOST_SUFFIX)) return null  // real network
        val path = uri.path.orEmpty().trimStart('/')
        if (path.startsWith("__lunacy/")) return asset(path.removePrefix("__lunacy/"), path)
        val fw = Regex("(?:^|.*/)usr/palm/frameworks/enyo/[^/]+/(.*)").matchEntire(path)
        val assetPath = when {
            fw != null -> "fw/enyo/1.0/" + fw.groupValues[1]
            path.startsWith(Paths.APPS) -> "apps/" + path.removePrefix(Paths.APPS)
            else -> null
        }
        val resp = assetPath?.let { asset(it, path) }
        if (resp == null) Log.w(TAG, "404 $uri")
        return resp ?: WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private fun asset(assetPath: String, name: String): WebResourceResponse? {
        val stream: InputStream = try { assets.open(assetPath) } catch (e: IOException) { return null }
        val mime = mimeOf(name)
        val body = when {
            mime == "text/html" && !assetPath.startsWith("fw/enyo/1.0/support/docs") -> injectInto(stream)
            mime == "text/css" -> CssTransforms.apply(stream)
            else -> stream
        }
        return WebResourceResponse(mime, "utf-8", 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), body)
    }

    /** Global serve-time transform: the bridge script becomes the first script in the page. */
    private fun injectInto(s: InputStream): InputStream {
        val html = s.bufferedReader().readText()
        var tag = "<script src=\"/__lunacy/compat.js\"></script>"
        if (injectBridge) tag += "<script src=\"/__lunacy/bridge.js\"></script>"
        val m = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        val out = if (m != null) html.substring(0, m.range.last + 1) + tag + html.substring(m.range.last + 1) else tag + html
        return ByteArrayInputStream(out.toByteArray())
    }

    private fun mimeOf(p: String) = when (p.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"; "js" -> "application/javascript"; "css" -> "text/css"
        "json" -> "application/json"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"; "svg" -> "image/svg+xml"; "ico" -> "image/x-icon"
        "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ttf" -> "font/ttf"; "woff" -> "font/woff"
        else -> "application/octet-stream"
    }

    // ---- bridge ----

    inner class Native(private val w: WebView) {
        @JavascriptInterface
        fun deviceInfo(): String = JSONObject(mapOf(
            "modelName" to "HP TouchPad", "modelNameAscii" to "HP TouchPad",
            "platformVersion" to "3.0.5", "platformVersionMajor" to 3, "platformVersionMinor" to 0,
            "platformVersionDot" to 5, "screenWidth" to resources.displayMetrics.widthPixels,
            "screenHeight" to resources.displayMetrics.heightPixels, "keyboardAvailable" to false,
            "wifiAvailable" to true, "carrierName" to "", "serialNumber" to "lunacy",
        )).toString()

        /** Async bus call: queue it, answer later. The spike implements no services. */
        @JavascriptInterface
        fun call(token: Int, url: String, params: String) {
            Log.i(TAG, "bus $url $params")
            val reply = JSONObject(mapOf("returnValue" to false, "errorCode" to -1,
                "errorText" to "Lunacy spike: service not implemented: $url")).toString()
            main.post { w.evaluateJavascript("PalmServiceBridge.__reply($token, ${JSONObject.quote(reply)})", null) }
        }

        @JavascriptInterface
        fun log(msg: String) { Log.i(TAG, "palm $msg") }
    }
}
