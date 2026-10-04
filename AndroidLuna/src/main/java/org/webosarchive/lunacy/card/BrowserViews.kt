package org.webosarchive.lunacy.card

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AbsoluteLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * The bottom layer of `enyo.WebView`: what webOS's BrowserAdapter plugin was, on Android's own
 * WebView. On a device the plugin was an `<object>` in the page, drawn by browserserver; Enyo's
 * BasicWebView talked to it through a set of calls and heard back through callbacks. Here the
 * page's BasicWebView is an empty box, and a native WebView sits over it, a child of the card's
 * own WebView so that it moves, scales and clips with the card. The page tells it where the box
 * is ([place]); the commands are the plugin's ([call]); the callbacks are the plugin's too,
 * called on the BasicWebView by name ([send]). See LunaRuntimes/enyo-1.0/CHANGES.md, 0005.
 *
 * The plugin drew into the page, so Enyo's popups - a dialog, a menu, the bookmarks drawer -
 * came out over the web content. A native view is over the whole page instead, so while a
 * popup overlaps it the page asks for it to be *covered*: the view is drawn into a picture, the
 * page shows the picture in the box, and the view steps aside until the popup has gone.
 *
 * One per card window. A page that opens a window ("target=_blank", window.open) is handed to
 * the app as webOS did: `createPage` with an identifier, and the app opens a card whose
 * BasicWebView asks for that identifier ([pending]).
 */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
class BrowserViews(internal val window: AppWindow, private val webosRoot: File, internal val server: AppServer) {
    private val main = Handler(Looper.getMainLooper())
    private val views = HashMap<Int, Browser>()

    companion object {
        private const val TAG = "LunacyWeb"
        private var nextId = 1
        private var nextPage = 1
        /** Windows a page has opened, waiting for the card that will show them; by identifier. */
        private val pending = HashMap<String, Browser>()
        /** Hosts whose certificate the owner trusted always, for this run. */
        private val trusted = HashSet<String>()
        /** Host and certificate pairs that held up under Lunacy's own trust store. */
        private val verified = HashSet<String>()
        private val worker = Executors.newSingleThreadExecutor()

        /**
         * webOS's load errors as the browser app knows them (Browser.WebKitErrors), from
         * Android's. Anything else is reported as Android's own code, which the app shows as
         * "Unable to Load Page".
         */
        fun webosError(code: Int): Int = when (code) {
            WebViewClient.ERROR_HOST_LOOKUP -> 2006
            WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> 1005
            WebViewClient.ERROR_FILE_NOT_FOUND, WebViewClient.ERROR_FILE -> 14
            else -> code
        }

        /** The certificate problem as one of the codes webOS's dialog words (Browser.showSSLConfirmDialog). */
        fun webosSslCode(error: SslError): Int = when (error.primaryError) {
            SslError.SSL_EXPIRED, SslError.SSL_NOTYETVALID -> 0
            SslError.SSL_IDMISMATCH -> 30
            SslError.SSL_UNTRUSTED -> 18
            else -> 24
        }
    }

    /** JS -> native, from the bridge's thread. Returns the new view's id at once. */
    fun create(identifier: String): Int {
        val id = synchronized(BrowserViews) { nextId++ }
        main.post {
            val adopted = identifier.takeIf { it.isNotEmpty() }?.let { pending.remove(it) }
            val b = adopted ?: Browser(window.context, this)
            b.bind(this, id)
            views[id] = b
            window.addView(b, AbsoluteLayout.LayoutParams(0, 0, 0, 0))
            b.visibility = View.INVISIBLE
            adopted?.release()
        }
        return id
    }

    fun call(id: Int, method: String, argsJson: String) {
        val args = runCatching { JSONArray(argsJson) }.getOrElse { JSONArray() }
        main.post { views[id]?.command(method, args) }
    }

    /**
     * Where the page's box is, in the page's CSS px, with the page's width so the card's scale
     * can be worked out; whether it is shown at all; and whether a popup is over it.
     */
    fun place(id: Int, json: String) {
        val p = runCatching { JSONObject(json) }.getOrNull() ?: return
        main.post { views[id]?.place(p) }
    }

    /** The page is showing the picture of a covered view, so the view can step aside. */
    fun covered(id: Int, generation: Int) { main.post { views[id]?.coveredShown(generation) } }

    fun destroy(id: Int) { main.post { views.remove(id)?.let { window.removeView(it); it.destroy() } } }

    /** The card's window is going: every view with it. */
    fun destroyAll() {
        views.values.forEach { window.removeView(it); it.destroy() }
        views.clear()
    }

    internal fun send(id: Int, method: String, vararg args: Any?) {
        val a = JSONArray(); args.forEach { a.put(it ?: JSONObject.NULL) }
        window.evaluateJavascript("window.__lunacyWebView&&__lunacyWebView($id,${JSONObject.quote(method)},$a)", null)
    }

    /** A webOS path, in the webOS root; /media/internal through UserFiles, as apps see it. */
    internal fun webosFile(path: String): File? {
        val rel = path.trimStart('/')
        if (rel.startsWith(AppServer.MEDIA_INTERNAL)) return UserFiles.resolve(webosRoot, rel.removePrefix(AppServer.MEDIA_INTERNAL))
        if (rel == "media/internal") return UserFiles.own(webosRoot)
        val f = File(webosRoot, rel)
        return f.takeIf { it.canonicalPath.startsWith(webosRoot.canonicalPath + File.separator) }
    }

    /** One native view. Not tied to its first owner: a window a page opens moves to the card the app opens for it. */
    @SuppressLint("ViewConstructor")
    class Browser(context: android.content.Context, private var owner: BrowserViews) : WebView(context) {
        private val main = Handler(Looper.getMainLooper())
        private val window get() = owner.window
        private var id = 0
        private var redirects = ArrayList<Triple<Regex, Boolean, String>>()
        private var blockPopups = true
        /** The plugin's one open dialog: a JsResult, an SSL or an HTTP auth handler. */
        private var dialog: Any? = null
        private var dialogHost = ""
        /** The open window's transport, held until the card that shows it is ready. */
        private var transport: Message? = null
        private var lastTouch = floatArrayOf(0f, 0f)
        private var lastImage: String? = null
        /** The box, in the card's px, and the card's px per CSS px. */
        private var box = Rect()
        private var scale = 1f
        private var shown = false
        private var cover = false
        private var generation = 0

        init {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false
            }
            // webOS's browser had no dark mode: a page that offers one is shown light, whatever
            // Android's own theme is.
            if (android.os.Build.VERSION.SDK_INT >= 29) settings.forceDark = WebSettings.FORCE_DARK_OFF
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = Client()
            webChromeClient = Chrome()
            setDownloadListener { url, _, _, mime, _ -> owner.send(id, "mimeNotSupported", mime.orEmpty(), url) }
            setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN) { lastTouch[0] = e.x; lastTouch[1] = e.y }; false }
            setOnLongClickListener { hold() }
        }

        fun bind(to: BrowserViews, newId: Int) { owner = to; id = newId }

        /** An adopted window's page starts loading once it has a card. */
        fun release() { transport?.sendToTarget(); transport = null }

        // ---- where it is ----

        fun place(p: JSONObject) {
            val pageWidth = p.optDouble("vw", 0.0)
            if (pageWidth <= 0 || window.width == 0) return
            scale = (window.width / pageWidth).toFloat()
            fun px(css: Double) = Math.round(css * scale).toInt()
            val x = p.optDouble("x"); val y = p.optDouble("y")
            val r = Rect(px(x) + window.scrollX, px(y) + window.scrollY,
                px(x + p.optDouble("w")) + window.scrollX, px(y + p.optDouble("h")) + window.scrollY)
            if (r != box) {
                box = r
                layoutParams = AbsoluteLayout.LayoutParams(r.width(), r.height(), r.left, r.top)
            }
            shown = p.optBoolean("visible") && r.width() > 0 && r.height() > 0
            val wantCover = p.optBoolean("covered")
            if (wantCover && !cover) coverNow()
            else if (!wantCover && cover) uncover()
            if (!cover) visibility = if (shown) View.VISIBLE else View.INVISIBLE
        }

        /**
         * A popup is over the box: a picture of the page goes into the box, and the view steps
         * aside when the page says the picture is up ([coveredShown]) - not before, or the box
         * would flash empty.
         */
        private fun coverNow() {
            cover = true
            val g = ++generation
            if (!shown || width == 0 || height == 0) { visibility = View.INVISIBLE; return }
            val bmp = runCatching {
                Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also { draw(Canvas(it)) }
            }.getOrNull() ?: run { visibility = View.INVISIBLE; return }
            worker.execute {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                bmp.recycle()
                val url = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                main.post { if (g == generation && cover) owner.send(id, "_lunacyCover", g, url) }
            }
        }

        fun coveredShown(g: Int) { if (g == generation && cover) visibility = View.INVISIBLE }

        private fun uncover() {
            cover = false
            generation++
            visibility = if (shown) View.VISIBLE else View.INVISIBLE
            owner.send(id, "_lunacyUncover")
        }

        // ---- the plugin's commands ----

        fun command(method: String, a: JSONArray) {
            when (method) {
                "openURL" -> a.optString(0).takeIf { it.isNotEmpty() }?.let { loadUrl(it) }
                "setHTML" -> loadDataWithBaseURL(a.optString(0).ifEmpty { null }, a.optString(1), "text/html", "utf-8", null)
                "reloadPage" -> reload()
                "stopLoad" -> stopLoading()
                "goBack" -> if (canGoBack()) goBack()
                "goForward" -> if (canGoForward()) goForward()
                "clearHistory" -> clearHistory()
                "clearCache" -> clearCache(true)
                "pageFocused" -> if (a.optBoolean(0)) onResume() else onPause()
                "setMinFontSize" -> settings.minimumFontSize = a.optInt(0, 1).coerceIn(1, 72)
                "setEnableJavaScript" -> settings.javaScriptEnabled = a.optBoolean(0, true)
                "setBlockPopups" -> { blockPopups = a.optBoolean(0, true); settings.javaScriptCanOpenWindowsAutomatically = !blockPopups }
                "setAcceptCookies" -> CookieManager.getInstance().setAcceptThirdPartyCookies(this, a.optBoolean(0, true))
                "addUrlRedirect" -> addRedirect(a.optString(0), a.optBoolean(1), a.optString(2))
                "sendDialogResponse" -> answer(a)
                "findInPage" -> findAllAsync(a.optString(0))
                "saveViewToFile" -> saveView(a.optString(0), a.optInt(3), a.optInt(4))
                "generateIconFromFile" -> imageJob { resize(a.optString(0), a.optString(1), 64, 64) }
                "resizeImage" -> imageJob { resize(a.optString(0), a.optString(1), a.optInt(2), a.optInt(3)) }
                "deleteImage" -> imageJob { owner.webosFile(a.optString(0))?.delete() }
                "saveImageAtPoint" -> saveImage(a.optString(2), a.optInt(3))
                // What browserserver did that a WebView does for itself, or that Lunacy places
                // and sizes from the page instead: nothing to do.
                "interrogateClicks", "setShowClickedLink", "setPageIdentifier", "connectBrowserServer",
                "disconnectBrowserServer", "setVisibleSize", "setHeaderHeight", "ignoreMetaTags",
                "setNetworkInterface", "setDNSServers", "selectPopupMenuItem", "handleFlick" -> {}
                else -> Log.i(TAG, "enyo.WebView.$method isn't supported by Lunacy's browser view")
            }
        }

        private fun addRedirect(regex: String, enable: Boolean, cookie: String) {
            val r = runCatching { Regex(regex) }.getOrNull() ?: return
            redirects.removeAll { it.first.pattern == regex }
            redirects.add(Triple(r, enable, cookie))
        }

        /** The answer to the open dialog: "1" accept (with a prompt's text, or a user and password), "0" cancel; "2" is SSL's "trust once". */
        private fun answer(a: JSONArray) {
            val r = a.optString(0)
            when (val d = dialog) {
                is JsPromptResult -> if (r == "1") d.confirm(a.optString(1)) else d.cancel()
                is JsResult -> if (r == "1") d.confirm() else d.cancel()
                is SslErrorHandler -> { if (r == "1") trusted.add(dialogHost); if (r == "1" || r == "2") d.proceed() else d.cancel() }
                is HttpAuthHandler -> if (r == "1") d.proceed(a.optString(1), a.optString(2)) else d.cancel()
            }
            dialog = null
        }

        // ---- images: bookmarks' thumbnails and icons, "Copy To Photos" ----

        private fun imageJob(job: () -> Unit) = worker.execute { runCatching(job).onFailure { Log.w(TAG, "image: $it") } }

        /** The page as it is on screen, scaled into w x h: a bookmark's thumbnail. */
        private fun saveView(path: String, w: Int, h: Int) {
            if (width == 0 || height == 0 || w <= 0 || h <= 0) return
            val full = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) } }.getOrNull() ?: return
            imageJob {
                // The top of the page, cropped to the thumbnail's shape.
                val ch = minOf(full.height, full.width * h / w)
                val crop = Bitmap.createBitmap(full, 0, 0, full.width, ch)
                write(Bitmap.createScaledBitmap(crop, w, h, true), path)
                full.recycle()
            }
        }

        private fun resize(from: String, to: String, w: Int, h: Int) {
            val src = owner.webosFile(from)?.takeIf { it.isFile } ?: return
            val bmp = BitmapFactory.decodeFile(src.path) ?: return
            write(Bitmap.createScaledBitmap(bmp, w.coerceAtLeast(1), h.coerceAtLeast(1), true), to)
        }

        private fun write(bmp: Bitmap, path: String) {
            val f = owner.webosFile(path) ?: return
            f.parentFile?.mkdirs()
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        /** The image last held down on, fetched with the page's cookies into [dir]. */
        private fun saveImage(dir: String, token: Int) {
            val src = lastImage
            val ua = settings.userAgentString
            worker.execute {
                val saved = runCatching {
                    val out = owner.webosFile(dir) ?: error("no folder $dir")
                    out.mkdirs()
                    val bytes: ByteArray
                    var name: String
                    if (src == null) error("no image")
                    if (src.startsWith("data:")) {
                        bytes = Base64.decode(src.substringAfter(','), Base64.DEFAULT)
                        name = "image." + src.substringAfter("image/").substringBefore(';').ifEmpty { "png" }
                    } else {
                        val c = URL(src).openConnection() as HttpURLConnection
                        c.setRequestProperty("User-Agent", ua)
                        CookieManager.getInstance().getCookie(src)?.let { c.setRequestProperty("Cookie", it) }
                        bytes = c.inputStream.use { it.readBytes() }
                        name = Uri.parse(src).lastPathSegment?.takeIf { it.contains('.') } ?: "image.jpg"
                    }
                    var f = File(out, name)
                    var n = 1
                    while (f.exists()) f = File(out, name.substringBeforeLast('.') + "-" + n++ + "." + name.substringAfterLast('.'))
                    f.writeBytes(bytes)
                    dir.trimEnd('/') + "/" + f.name
                }.onFailure { Log.w(TAG, "saveImageAtPoint: $it") }.getOrNull()
                main.post { owner.send(id, "_lunacyCallback", token, saved != null, saved ?: "") }
            }
        }

        // ---- holding a link or an image: the app's context menu ----

        private fun hold(): Boolean {
            val hit = hitTestResult
            val isImage = hit.type == HitTestResult.IMAGE_TYPE || hit.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            val isLink = hit.type == HitTestResult.SRC_ANCHOR_TYPE || hit.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            if (!isImage && !isLink) return false
            lastImage = if (isImage) hit.extra else null
            val pageX = (box.left - window.scrollX + lastTouch[0]) / scale
            val pageY = (box.top - window.scrollY + lastTouch[1]) / scale
            val reply = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(m: Message) {
                    val link = if (isLink) (m.data.getString("url") ?: hit.extra).orEmpty() else ""
                    val info = JSONObject()
                        .put("isNull", false).put("isLink", isLink).put("isImage", isImage)
                        .put("x", pageX).put("y", pageY)
                        .put("linkUrl", link).put("linkText", m.data.getString("title").orEmpty())
                        .put("imageUrl", if (isImage) hit.extra.orEmpty() else "")
                        .put("title", m.data.getString("title").orEmpty())
                    owner.send(id, "eventFired", JSONObject().put("type", "mousehold").put("pageX", pageX).put("pageY", pageY), info)
                }
            }
            requestFocusNodeHref(reply.obtainMessage())
            return true
        }

        // ---- callbacks ----

        private var told = ""
        /** The plugin said this once per change; Android's several callbacks for one load say it once too. */
        private fun titleChanged() {
            val now = JSONArray().put(url.orEmpty()).put(title.orEmpty()).put(canGoBack()).put(canGoForward()).toString()
            if (now == told) return
            told = now
            owner.send(id, "urlTitleChanged", url.orEmpty(), title.orEmpty(), canGoBack(), canGoForward())
        }

        inner class Client : WebViewClient() {
            // An app's own pages, on its origin, come from Lunacy's server as its card's do.
            override fun shouldInterceptRequest(view: WebView, req: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? =
                if (req.url.host.orEmpty().endsWith(AppServer.HOST_SUFFIX)) owner.server.serve(req.url) else null
            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val scheme = Uri.parse(url).scheme?.lowercase().orEmpty()
                redirects.firstOrNull { it.second && it.first.containsMatchIn(url) }?.let {
                    owner.send(id, "urlRedirected", url, it.third); return true
                }
                // Anything that isn't a web page is handed to the app, which opens it the way
                // webOS did (applicationManager/open), as the system's redirects did.
                if (scheme !in setOf("http", "https", "data", "about", "javascript", "blob", "file")) {
                    owner.send(id, "urlRedirected", url, ""); return true
                }
                return false
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                owner.send(id, "loadStarted")
                titleChanged()
            }
            override fun onPageFinished(view: WebView, url: String) {
                owner.send(id, "loadProgressChanged", 100)
                owner.send(id, "loadStopped")
                owner.send(id, "documentLoadFinished")
                titleChanged()
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = titleChanged()
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                owner.send(id, "mainDocumentLoadFailed", "", webosError(errorCode), failingUrl, description)
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val uri = Uri.parse(error.url)
                val host = uri.host.orEmpty()
                if (host in trusted) { handler.proceed(); return }
                // An older Android's store lacks today's roots (Android 5 refuses Wikipedia's).
                // Lunacy's own requests trust the bundled roots as well (Http); if the site's
                // certificate holds up there and is the one this view was shown, it is good.
                // (Expiry and the host name are checked there too.)
                if (error.primaryError == SslError.SSL_UNTRUSTED) {
                    val seen = android.net.http.SslCertificate.saveState(error.certificate)?.getByteArray("x509-certificate")
                    val key = host + "/" + seen?.contentHashCode()
                    if (key in verified) { handler.proceed(); return }
                    val port = if (uri.port > 0) uri.port else 443
                    worker.execute {
                        val good = seen != null && Http.verifiedLeaf(host, port)?.contentEquals(seen) == true
                        main.post { if (good) { verified.add(key); handler.proceed() } else askSsl(handler, host, error) }
                    }
                    return
                }
                askSsl(handler, host, error)
            }
            private fun askSsl(handler: SslErrorHandler, host: String, error: SslError) {
                dialog?.let { (it as? SslErrorHandler)?.cancel() }
                dialog = handler; dialogHost = host
                owner.send(id, "dialogSSLConfirm", host, webosSslCode(error), "")
            }
            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                dialog = handler; dialogHost = host
                owner.send(id, "dialogUserPassword", host)
            }
        }

        inner class Chrome : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) = owner.send(id, "loadProgressChanged", progress)
            override fun onReceivedTitle(view: WebView, title: String?) = titleChanged()
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                dialog = result; owner.send(id, "dialogAlert", message); return true
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                dialog = result; owner.send(id, "dialogConfirm", message); return true
            }
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
                dialog = result; owner.send(id, "dialogPrompt", message, defaultValue.orEmpty()); return true
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (blockPopups && !isUserGesture) return false
                val child = Browser(context, owner)
                val identifier = "lunacy-page-" + synchronized(BrowserViews) { nextPage++ }
                (resultMsg.obj as WebView.WebViewTransport).webView = child
                child.transport = resultMsg
                pending[identifier] = child
                owner.send(id, "createPage", identifier)
                return true
            }
        }
    }
}
