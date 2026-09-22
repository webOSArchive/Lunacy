package org.webosarchive.lunacy.card

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/** What a window needs from the shell. */
interface WindowHost {
    val server: AppServer
    val bus: Bus
    /** An app opened a window with window.open; child.attributes says which kind. */
    fun onWindowOpened(parent: AppWindow, child: AppWindow)
    /** PalmSystem banners. addBanner returns the banner's id. */
    fun addBanner(window: AppWindow, message: String, params: String, icon: String, soundClass: String, soundFile: String, duration: Int): Int
    /** PalmSystem.paste: the system clipboard into the focused field of the card that is up. */
    fun paste(window: AppWindow)
    /** PalmSystem.copiedToClipboard: the "Selection Copied" banner. */
    fun copiedToClipboard(window: AppWindow)
    /** PalmSystem.playSoundNotification: a banner's sound without the banner. */
    fun playSound(window: AppWindow, soundClass: String, soundFile: String, duration: Int)
    fun removeBanner(window: AppWindow, id: Int)
    fun clearBanners(window: AppWindow)
    fun onWindowClosed(window: AppWindow)
    fun onStageReady(window: AppWindow)
    /** The page has finished loading; see [ShellActivity.onPageLoaded]. */
    fun onPageLoaded(window: AppWindow)
    /** Where media players fetch /media/internal files: MediaServer's loopback base URL. */
    fun mediaBase(): String
    /**
     * The page has put a frame on the screen. webOS took its loading card away when the app
     * had actually drawn, not when the framework said it was ready: Mojo calls `stageReady`
     * while the scene is still being built, and taking it away then shows a blank card.
     */
    fun onPageDrawn(window: AppWindow)
    /** PalmSystem.activate: bring the window's card forward. */
    fun activate(window: AppWindow)
    /** The page wants the virtual keyboard shown or hidden. */
    fun keyboard(window: AppWindow, show: Boolean)
    /**
     * PalmSystem.setWindowProperties({"blockScreenTimeout": true}): hold the screen on while
     * this window wants it. A video player asks for it for as long as it is playing.
     */
    fun blockScreenTimeout(window: AppWindow, block: Boolean)
    /**
     * PalmSystem.enableFullScreenMode: the card grows over the status bar's space, and the bar
     * goes while it is maximized. The request is on [AppWindow.fullScreen] already.
     */
    fun fullScreen(window: AppWindow)
    /** The app asked for a fixed orientation, or "free" again; it is on [AppWindow.fixedOrientation]. */
    fun orientationRequested(window: AppWindow)
    fun deviceInfo(emulated: Boolean): String
    /** webOS's screen orientation: "up", "down", "left" or "right". */
    fun screenOrientation(): String
    /**
     * webOS's *window* orientation: the screen's, turned by where this device's home button
     * is. Also one of the four names - see [DeviceProfile.windowOrientationFor].
     */
    fun windowOrientation(emulated: Boolean): String
    /**
     * The display's size in webOS pixels, the way round it is now: what `screen.width` and
     * `screen.height` report to a page. Follows the screen round, as a device's did.
     */
    fun screenSize(emulated: Boolean): String
    /** PalmSystem's locale, localeRegion, phoneRegion and timeFormat, as JSON. */
    fun localeInfo(): String
    /** Android pixels per CSS pixel: apps get TouchPad-sized pixels (Docs/architecture.md, Screen size). */
    val pixelScale: Float
}

/**
 * One webOS window: a WebView on its app's origin, with PalmSystem and PalmServiceBridge.
 * A card shows one window; an app can own several. See Docs/architecture.md, "App lifecycle".
 */
@SuppressLint("ViewConstructor", "SetJavaScriptEnabled", "AddJavascriptInterface")
class AppWindow(
    context: Context,
    val appId: String,
    private val host: WindowHost,
    /**
     * Whether this window is one of webOS's *emulated* cards: the phone-sized card a TouchPad
     * gave an app whose appinfo.json never said `"uiRevision": 2`. Such an app was laid out
     * for a Pre-sized screen, and the device ran it at that size rather than stretching it
     * across the tablet. Everything the window tells the page about the machine follows from
     * it - see [Native.deviceInfo] and [Native.screenSize].
     */
    val emulated: Boolean = false,
) : WebView(context) {
    private val main = Handler(Looper.getMainLooper())
    var stageReady = false
        private set
    /** webOS window attributes from window.open: "window" is card, dashboard or popupalert. */
    var attributes = JSONObject()
    val type: String get() = attributes.optString("window", "card")
    /**
     * The orientation the app has fixed its card at - "up", "down", "left", "right",
     * "landscape" or "portrait" - or null while it lets the card turn with the screen
     * ("free"). Starts as appinfo.json's requestedWindowOrientation, as LunaSysMgr's
     * CardWebApp did; the frameworks then ask for "free" on the app's behalf unless the app
     * says otherwise. An emulated card ignores it (the shell doesn't turn for one).
     */
    @Volatile var fixedOrientation: String? = null
    /** What the app last asked for through PalmSystem.enableFullScreenMode. */
    var fullScreen = false
        private set
    /**
     * setWindowProperties' statusBarColor, as 0xRRGGBB, or null if the app never set one. The
     * shell reads it when the card is maximized, which is when LunaSysMgr did.
     */
    @Volatile var statusBarColor: Int? = null
        private set
    /** Attributes the page announced for the window it is about to open. */
    @Volatile private var pendingAttributes = JSONObject()
    @Volatile private var destroyed = false
    /** This page's open bus calls, by bridge token: subscriptions until they're cancelled. Main thread. */
    private val calls = HashMap<Int, Bus.Call>()
    /**
     * Whether the keyboard resizes this window (webOS's default) or covers it, with
     * Mojo.positiveSpaceChanged reporting the space left (allowResizeOnPositiveSpaceChange).
     */
    @Volatile var keyboardResizes = true
        private set
    private lateinit var net: NetShim
    /** The webOS device this window reports itself as. */
    private val profile = DeviceProfile.forScreen(context)
    /** This window's "process id": webOS gave one per window, and apps print it. */
    private val pid = nextPid.getAndIncrement()

    init {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            @Suppress("DEPRECATION")
            setDatabasePath(context.getDir("websql", Context.MODE_PRIVATE).path)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            // The page's viewport is declared rather than inferred: the compat layer gives
            // every page a viewport meta carrying the card's own width and a scale pinned at
            // 1, merged into whatever the app declared (see "The viewport" in compat.js).
            // With this false the meta is ignored altogether, and the engine works the scale
            // out for itself - which is what left a card drawn at 0.78 after a rotation.
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }
        // The user agent of the webOS device Lunacy answers as (DeviceProfile): a TouchPad's on
        // a tablet, a Pre3's on a phone. It is set for navigator.userAgent, the page's own
        // loads and the network shim alike, so a server can't tell them apart.
        settings.userAgentString = profile.userAgent
        net = NetShim(appId, settings.userAgentString, profile.carrierCode) { id ->
            main.post { if (!destroyed) evaluateJavascript("window.__lunacyNetDone&&__lunacyNetDone($id)", null) }
        }
        // 1 CSS px = 1 TouchPad px, as the shell uses; the layout width follows from it.
        setInitialScale(Math.round(host.pixelScale * 100))
        settings.setSupportZoom(false)
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.i(AppServer.TAG, "[$appId] ${m.messageLevel()} ${m.sourceId()?.substringAfterLast('/')}:${m.lineNumber()} ${m.message()}")
                return true
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val child = AppWindow(context, appId, host, emulated)
                child.attributes = pendingAttributes
                pendingAttributes = JSONObject()
                // Dashboards and popup alerts draw over Luna's dark frames: transparent pages.
                if (child.type == "dashboard" || child.type == "popupalert") child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                (resultMsg.obj as WebViewTransport).webView = child
                resultMsg.sendToTarget()
                host.onWindowOpened(this@AppWindow, child)
                return true
            }
            override fun onCloseWindow(window: WebView) { host.onWindowClosed(this@AppWindow) }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? =
                host.server.serve(req.url)
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                net.reset(); endCalls()
                pageReady = false; told = null
            }
            override fun onPageFinished(view: WebView, url: String) {
                host.onPageLoaded(this@AppWindow)
                // The window.open transport resets the background; apply transparency again.
                if (type == "dashboard" || type == "popupalert") view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // Guarded: a window.open transport finishes with no body, and an unguarded
                // read throws into the app's own console, where it looks like the app's fault.
                view.evaluateJavascript("(function(){var b=document.body;if(!b){return 'no body';}" +
                    "return window.innerWidth+'x'+window.innerHeight+' dpr '+window.devicePixelRatio" +
                    "+' bg html '+getComputedStyle(document.documentElement).backgroundColor" +
                    "+' body '+getComputedStyle(b).backgroundColor" +
                    "+' first '+(b.firstElementChild?b.firstElementChild.className+' '+getComputedStyle(b.firstElementChild).backgroundColor:'');})()") {
                    Log.i(AppServer.TAG, "[$appId] loaded $url viewport $it")
                }
            }
        }
        addJavascriptInterface(Native(), "LunacyNative")
    }

    override fun destroy() {
        destroyed = true
        net.reset()
        endCalls()
        super.destroy()
    }

    /** Ends every open call, when the page goes away. */
    private fun endCalls() {
        val open = calls.values.toList()
        calls.clear()
        open.forEach { it.cancel() }
    }

    /** Calls a lifecycle hook the way LunaSysMgr did: a function on the page's Mojo object. */
    fun callMojo(fn: String, argsJson: String = "") =
        evaluateJavascript("(function(){try{if(window.Mojo&&Mojo.$fn){Mojo.$fn($argsJson);}}catch(e){console.error('Mojo.$fn: '+e);}})()", null)

    /**
     * Drops the page's input focus, the way webOS put its keyboard away. Dismissing the
     * virtual keyboard there didn't hide a view: IMEController::hideIME asked the web app to
     * removeInputFocus, WebKit blurred the focused node and the keyboard went with it. So an
     * app that watches its field's focus - and that is all Mojo gives it - hears about the
     * keyboard going down. Android's IME just hides, leaving the field focused, so the shell
     * has to do this part itself.
     */
    fun removeInputFocus() = evaluateJavascript(
        "(function(){try{if(window.__lunacyRemoveInputFocus){__lunacyRemoveInputFocus();}}catch(e){console.error('removeInputFocus: '+e);}})()", null)

    /**
     * Relaunches the app as LunaSysMgr did: launchParams become params (JSON, or "" for none),
     * then Mojo.relaunch() runs. done gets whether the app handled it; if not, the shell
     * brings the app's first card forward.
     */
    fun relaunch(params: String, done: (Boolean) -> Unit) = evaluateJavascript(
        "(function(){try{return window.__lunacyRelaunch?__lunacyRelaunch(${JSONObject.quote(params)}):false}catch(e){console.error('relaunch: '+e);return false}})()"
    ) { done(it == "true") }

    /**
     * Focus, as LunaSysMgr gave it: PalmSystem.isActivated, which Enyo reads to find the active
     * window (for the app menu, among others), then Mojo.stageActivated or stageDeactivated.
     */
    fun setStageActive(active: Boolean) {
        this.active = active
        if (pageReady) deliverActive()
    }

    /** Whether the shell has this window's card focused. */
    private var active = false
    /** What the page was last told, so that it hears each change once, as it did on webOS. */
    private var told: Boolean? = null
    /**
     * Whether the page can be told: it has called stageReady or drawn a frame. A card is
     * maximized before its page exists, and on a TouchPad the activation comes after
     * stageReady ("APP READY", then "Sending stage activation" in LunaSysMgr's log) - so an
     * app launched straight into a maximized card still hears windowActivated.
     */
    private var pageReady = false

    private fun deliverActive() {
        if (told == active) return
        told = active
        evaluateJavascript("if(window.PalmSystem){PalmSystem.isActivated=$active}", null)
        callMojo(if (active) "stageActivated" else "stageDeactivated")
    }

    private fun onPageReady() {
        if (pageReady) return
        pageReady = true
        // Until now it believed itself inactive, which is how a page starts.
        if (active) deliverActive() else told = false
    }

    /**
     * The screen turned. LunaSysMgr kept PalmSystem.screenOrientation current and called
     * Mojo.screenOrientationChanged; Enyo 1 reads the property when the page's own resize
     * event arrives (enyo.sendOrientationChange), so it is set first.
     */
    fun setScreenOrientation(orientation: String) {
        evaluateJavascript("if(window.PalmSystem){PalmSystem.screenOrientation=${JSONObject.quote(orientation)}}", null)
        callMojo("screenOrientationChanged", JSONObject.quote(orientation))
    }

    /** The webOS back gesture: an ESC key event, which Enyo and Mojo turn into "back". */
    fun sendBack() = evaluateJavascript(
        "(function(){function k(t){var e=document.createEvent('Events');e.initEvent(t,true,true);e.keyCode=27;e.which=27;(document.activeElement||document).dispatchEvent(e);}k('keydown');k('keyup');})()", null)

    private companion object {
        /** Plausible process ids, in the range webOS apps started at. */
        val nextPid = java.util.concurrent.atomic.AtomicInteger(1183)
        val FOUR = setOf("up", "down", "left", "right")
    }

    inner class Native {
        @JavascriptInterface fun deviceInfo(): String = host.deviceInfo(emulated)

        /** Async bus call: queue it, answer later through evaluateJavascript. */
        @JavascriptInterface
        fun call(token: Int, url: String, params: String) {
            main.post {
                val call = host.bus.call(appId, url, params) { reply ->
                    main.post { if (!destroyed) evaluateJavascript("PalmServiceBridge.__reply($token, ${JSONObject.quote(reply)})", null) }
                }
                if (call.subscribe && !call.cancelled) calls[token] = call
            }
        }

        /** PalmServiceBridge.cancel: ends a subscription. */
        @JavascriptInterface
        fun cancel(token: Int) {
            main.post { calls.remove(token)?.cancel() }
        }

        @JavascriptInterface
        fun stageReady() { main.post { stageReady = true; host.onStageReady(this@AppWindow); onPageReady() } }

        /** The compat layer, once the page has actually produced a frame. */
        @JavascriptInterface fun pageDrawn() { main.post { host.onPageDrawn(this@AppWindow); onPageReady() } }

        /** The network shim (assets/lunacy/net.js): cross-origin XHRs, sent natively. */
        @JavascriptInterface fun netSend(id: Int, request: String) = net.send(id, request)
        @JavascriptInterface fun netResult(id: Int): String = net.result(id)
        /** A synchronous XHR: blocks this page's script until the answer is in, as on webOS. */
        @JavascriptInterface fun netSendSync(request: String): String = net.sendSync(request)
        @JavascriptInterface fun netAbort(id: Int) = net.abort(id)

        @JavascriptInterface fun screenOrientation(): String = host.screenOrientation()
        /**
         * This card's own size in device pixels, which is the number the page's viewport has
         * to be told so that one CSS pixel is one device pixel. The window's own size once it
         * has been laid out; the display's until then.
         */
        @JavascriptInterface
        fun cardSize(): String {
            val w = this@AppWindow.width
            val h = this@AppWindow.height
            if (w > 0 && h > 0) return JSONObject().put("width", w).put("height", h).toString()
            return host.screenSize(emulated)
        }
        /**
         * A card fixed at one of the four reports that one, as the reference TouchPad did: asked
         * for "up", its card turned to portrait and read "up" where a free card held the same
         * way reads "right".
         */
        @JavascriptInterface fun windowOrientation(): String =
            fixedOrientation?.takeIf { !emulated && it in FOUR } ?: host.windowOrientation(emulated)
        /** PalmSystem.windowOrientation = o, or setWindowOrientation(o). */
        @JavascriptInterface
        fun requestOrientation(o: String) {
            val v = o.lowercase().takeIf { it in FOUR || it == "landscape" || it == "portrait" }
            main.post { if (fixedOrientation != v) { fixedOrientation = v; host.orientationRequested(this@AppWindow) } }
        }
        @JavascriptInterface fun screenSize(): String = host.screenSize(emulated)
        /** PalmSystem's locale fields and clock format, from Android's own settings. */
        @JavascriptInterface fun localeInfo(): String = host.localeInfo()
        /** The process id in PalmSystem.identifier: one per window, as a device gave. */
        @JavascriptInterface fun processId(): Int = pid
        @JavascriptInterface fun mediaBase(): String = host.mediaBase()
        @JavascriptInterface fun activate() { main.post { host.activate(this@AppWindow) } }
        @JavascriptInterface fun keyboard(show: Boolean) { main.post { host.keyboard(this@AppWindow, show) } }
        @JavascriptInterface fun keyboardResizes(resize: Boolean) { keyboardResizes = resize }
        @JavascriptInterface
        fun blockScreenTimeout(block: Boolean) { main.post { host.blockScreenTimeout(this@AppWindow, block) } }
        @JavascriptInterface
        fun fullScreen(on: Boolean) { main.post { if (fullScreen != on) { fullScreen = on; host.fullScreen(this@AppWindow) } } }
        @JavascriptInterface fun statusBarColor(rgb: Int) { statusBarColor = rgb and 0xFFFFFF }

        @JavascriptInterface fun log(msg: String) { Log.i(AppServer.TAG, "[$appId] palm $msg") }

        /** Called by the page's window.open wrapper just before the native open. */
        @JavascriptInterface
        fun nextWindow(attributesJson: String) {
            pendingAttributes = try { JSONObject(attributesJson) } catch (e: Exception) { JSONObject() }
        }

        @JavascriptInterface
        fun addBanner(message: String, params: String, icon: String, soundClass: String, soundFile: String, duration: Int): Int =
            host.addBanner(this@AppWindow, message, params, icon, soundClass, soundFile, duration)
        @JavascriptInterface fun paste() { main.post { host.paste(this@AppWindow) } }
        /** A page's copy or cut, which this engine won't put on the clipboard itself. */
        @JavascriptInterface
        fun setClipboard(text: String) {
            main.post {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText(appId, text))
            }
        }
        @JavascriptInterface fun copiedToClipboard() { main.post { host.copiedToClipboard(this@AppWindow) } }
        @JavascriptInterface
        fun playSound(soundClass: String, soundFile: String, duration: Int) {
            main.post { host.playSound(this@AppWindow, soundClass, soundFile, duration) }
        }
        @JavascriptInterface fun removeBanner(id: Int) { main.post { host.removeBanner(this@AppWindow, id) } }
        @JavascriptInterface fun clearBanners() { main.post { host.clearBanners(this@AppWindow) } }
    }
}
