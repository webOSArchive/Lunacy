package org.webosarchive.lunacy.desktop

import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.Bus
import org.webosarchive.lunacy.card.DeviceProfile
import org.webosarchive.lunacy.card.NetShim
import org.webosarchive.lunacy.card.PageHooks
import org.webosarchive.lunacy.card.jsString
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One page in a Luna DE window: the desktop side of window.LunacyNative, as AppWindow.Native is
 * Android's. A synchronous method answers at once; a bus reply is queued as the script the page
 * runs ([take], which the page's preload polls). A reload is a new page, so a reply meant for
 * the old one never reaches the new one.
 *
 * It answers what an app needs of the device (a TouchPad), the bus, the locale, the network shim
 * and WebSQL. What it doesn't have behind it (plugins, native web views, the clipboard) answers as
 * a device with nothing there would (Docs/luna-de.md).
 */
class DesktopWindow(val pageId: String, val appId: String, private val bus: Bus, private val main: DesktopScheduler,
                    private val shell: DesktopShell) : org.webosarchive.lunacy.card.RunningWindow {
    /** The device this page was started on. */
    private val profile = shell.device
    private val outbox = LinkedBlockingQueue<String>()
    private val calls = HashMap<Int, Bus.Call>()   // on the main thread only
    override val pid = nextPid.getAndIncrement()
    @Volatile private var closed = false
    /** How this window is held when it isn't the device's way: a popped-out window resized to the other shape. */
    @Volatile var ownOrientation: String? = null
    private val portrait get() = ownOrientation?.let { it == "up" || it == "down" } ?: shell.portrait
    /** The window's type as its opener named it ("card", "dashboard", "popupalert"...), or null for an app's first window. */
    @Volatile var type: String? = null

    /**
     * The network shim's native half (net.js): an app's cross-origin XHR sent from here, as the
     * TouchPad sent it (its user agent and X-Palm-Carrier), whatever the server's CORS, and
     * http:// as well as https://. Its answer is fetched by the page once NET_DONE says so.
     */
    private val net = NetShim(appId, profile.userAgent, profile.carrierCode, { 0 }) { id ->
        if (!closed) outbox.add(PageHooks.call(PageHooks.NET_DONE, "$id"))
    }

    /**
     * WebSQL's native half (websql.js), which Electron's Chromium no longer has itself: one SQLite
     * file per app and database name under the data folder, as on Android. Made at the page's
     * first openDatabase.
     */
    private val sql by lazy {
        org.webosarchive.lunacy.card.WebSql(shell.webSqlDir, appId, { 0 }, { id -> if (!closed) outbox.add(PageHooks.call(PageHooks.SQL_DONE, "$id")) })
    }
    @Volatile private var sqlUsed = false

    /** A method of window.LunacyNative, with its arguments; its value as JSON text. */
    fun invoke(method: String, a: JSONArray): String = when (method) {
        "deviceInfo" -> JSONObject.quote(deviceInfo())
        "screenSize" -> JSONObject.quote(JSONObject().put("width", width()).put("height", height()).toString())
        // In the page's own px: the device's over its scale (a Pre3's 480 is 320 to a page).
        "cardSize" -> JSONObject.quote(JSONObject().put("width", Math.round(width() / shell.pageScale(appId, portrait))).put("height", Math.round(height() / shell.pageScale(appId, portrait))).put("scale", 1.0).toString())
        "localeInfo" -> JSONObject.quote(localeInfo())
        // The TouchPad reports "right" held in landscape and "up" in portrait (Docs/spike-1.md).
        "screenOrientation" -> JSONObject.quote(screenOrientation())
        "windowOrientation" -> JSONObject.quote(profile.windowOrientationFor(screenOrientation()))
        "processId" -> pid.toString()
        "activityId" -> "0"
        "mediaBase" -> JSONObject.quote("")
        "localNetworkBlocked" -> "false"
        "netSend" -> { net.send(a.optInt(0), a.optString(1)); "null" }
        "netResult" -> JSONObject.quote(net.result(a.optInt(0)))
        "netSendSync" -> JSONObject.quote(net.sendSync(a.optString(0)))
        "netAbort" -> { net.abort(a.optInt(0)); "null" }
        "sqlOpen" -> { sqlUsed = true; JSONObject.quote(sql.open(a.optString(0), a.optString(1), a.optBoolean(3))) }
        "sqlRequest" -> { sqlUsed = true; sql.request(a.optInt(0), a.optString(1)); "null" }
        "sqlResult" -> JSONObject.quote(sql.result(a.optInt(0)))
        "call" -> call(a.optInt(0), a.optString(1), a.optString(2)).toString()
        "cancel" -> { val t = a.optInt(0); main.post { calls.remove(t)?.cancel() }; "null" }
        "log" -> { Log.i(Lunacy.TAG, "[$appId] palm ${a.optString(0)}"); "null" }
        // Luna DE's own: the page has loaded, and a script's answer (relaunch).
        "pageLoaded", "stageReady", "pageDrawn" -> {
            // The app says its stage is ready (or its page has drawn): its card can come up.
            if (method != "pageLoaded") shell.pageReady(this)
            main.post { pageUp() }; "null"
        }
        "scriptResult" -> { val t = a.optInt(0); val v = a.optBoolean(1); main.post { results.remove(t)?.invoke(v) }; "null" }
        "clipboardHasText" -> "false"
        // PalmSystem.addBannerMessage(message, launch params, icon ...): the shell shows it, and a
        // tap on it launches the app with those params.
        "addBanner" -> shell.banner(this, a.optString(0), a.optString(1), a.optString(2)).toString()
        "removeBanner" -> { shell.removeBanner(a.optInt(0)); "null" }
        "clearBanners" -> { shell.clearBanners(this); "null" }
        // The page wants the keyboard up or down (a field took or lost the focus, or the app
        // said so), and whether its window is resized for it or told the space left instead.
        "keyboard" -> { shell.keyboardWanted(this, a.optBoolean(0)); "null" }
        "keyboardResizes" -> { shell.keyboardResizes(this, a.optBoolean(0)); "null" }
        // PalmSystem.activate(): the window asks to be brought forward.
        "activate" -> { shell.activate(this); "null" }
        "pluginStart" -> "-1"
        "pluginCall" -> JSONObject.quote("{\"exception\":\"plugin not connected\"}")
        "webViewCreate" -> "-1"
        // What the page tells the shell (it drew, it wants the keyboard, the screen to stay on ...)
        // has nothing to act on yet.
        else -> "null"
    }

    /** When the page last asked for its scripts: a live page asks again at once, every 25 s at most. */
    @Volatile var lastPoll = System.currentTimeMillis()
        private set
    @Volatile private var polling = false
    /** Whether the page is still there to take a script: it is waiting for one now, or asked not long ago. */
    val alive get() = polling || System.currentTimeMillis() - lastPoll < STALE_MS

    /** The scripts waiting for the page, after waiting up to [ms] for the first. */
    fun take(ms: Long): List<String> = try { polling = true; takeNow(ms) } finally { polling = false; lastPoll = System.currentTimeMillis() }

    private fun takeNow(ms: Long): List<String> {
        val first = outbox.poll(ms, TimeUnit.MILLISECONDS) ?: return emptyList()
        val all = arrayListOf(first)
        outbox.drainTo(all)
        return all
    }

    // ---- relaunch (AppWindow.relaunch) ----

    private var pageLoaded = false
    private val waitingRelaunches = ArrayList<Pair<String, (Boolean) -> Unit>>()
    private val results = HashMap<Int, (Boolean) -> Unit>()
    private var nextResult = 1

    /**
     * A relaunch goes to the app's page once it has loaded; one that comes while the page is
     * still loading waits for it, as on Android. [done] hears whether the app handled it. On the
     * main thread.
     */
    fun relaunch(params: String, done: (Boolean) -> Unit) {
        if (!pageLoaded) { waitingRelaunches += params to done; return }
        val token = nextResult++
        results[token] = done
        outbox.add("(function(){var r=false;try{r=window.${PageHooks.RELAUNCH}?${PageHooks.RELAUNCH}(${jsString(params)}):false}catch(e){console.error('relaunch: '+e)}" +
            "LunacyNative.scriptResult($token,r===true)})()")
    }

    private fun pageUp() {
        if (!pageLoaded) {
            pageLoaded = true
            val waiting = waitingRelaunches.toList()
            waitingRelaunches.clear()
            waiting.forEach { (params, done) -> relaunch(params, done) }
        }
        if (!pageReady) {
            pageReady = true
            // Until now it believed itself inactive, which is how a page starts.
            if (active || focusPending) deliverActive() else told = false
            focusPending = false
        }
    }

    // ---- what the shell tells the page (AppWindow's, on Android) ----

    private var active = false
    private var told: Boolean? = null
    private var pageReady = false
    private var focusPending = false

    /** Calls a lifecycle hook the way LunaSysMgr did: a function on the page's Mojo object. */
    private fun callMojo(fn: String, argsJson: String = "") {
        if (!closed) outbox.add("(function(){try{if(window.Mojo&&Mojo.$fn){Mojo.$fn($argsJson);}}catch(e){console.error('Mojo.$fn: '+e);}})()")
    }

    /**
     * Focus, as LunaSysMgr gave it: PalmSystem.isActivated, which Enyo reads to find the active
     * window, then Mojo.stageActivated or stageDeactivated; each change told once, and not before
     * the page has said it is ready (AppWindow.setStageActive). On the main thread.
     */
    fun setStageActive(on: Boolean) {
        active = on
        if (pageReady) deliverActive()
    }

    /**
     * A dashboard's focus: told when it is added and each time the drop-down opens or closes,
     * whether or not that changes anything (AppWindow.sendFocus).
     */
    fun sendFocus(on: Boolean) {
        active = on
        told = null
        if (pageReady) deliverActive() else focusPending = true
    }

    private fun deliverActive() {
        if (told == active) return
        told = active
        if (!closed) outbox.add("if(window.PalmSystem){PalmSystem.isActivated=$active}")
        callMojo(if (active) "stageActivated" else "stageDeactivated")
    }

    /** The screen turned: PalmSystem.screenOrientation first, then Mojo.screenOrientationChanged. */
    fun setScreenOrientation(orientation: String) {
        if (!closed) outbox.add("if(window.PalmSystem){PalmSystem.screenOrientation=${JSONObject.quote(orientation)}}")
        callMojo("screenOrientationChanged", JSONObject.quote(orientation))
    }

    /**
     * The virtual keyboard came up or went down over this window, as LunaSysMgr told it
     * (ShellChrome.watchKeyboard on Android): Mojo.keyboardShown, and for a window that isn't
     * resized for the keyboard, Mojo.positiveSpaceChanged with the space left.
     */
    fun keyboardShown(shown: Boolean) = callMojo("keyboardShown", shown.toString())
    fun positiveSpace(size: String) = callMojo("positiveSpaceChanged", size)
    /** The keyboard was put away: the page's input focus goes with it, as on webOS (AppWindow.removeInputFocus). */
    fun removeInputFocus() { if (!closed) outbox.add(PageHooks.call(PageHooks.REMOVE_INPUT_FOCUS)) }

    /** The webOS back gesture: an ESC key event, which Enyo and Mojo turn into "back". */
    fun sendBack() {
        if (!closed) outbox.add("(function(){function k(t){var e=document.createEvent('Events');e.initEvent(t,true,true);e.keyCode=27;e.which=27;(document.activeElement||document).dispatchEvent(e);}k('keydown');k('keyup');})()")
    }

    fun close() {
        closed = true
        net.reset()
        if (sqlUsed) sql.close()
        main.post { calls.values.forEach { it.cancel() }; calls.clear() }
    }

    /** PalmServiceBridge.call, as AppWindow.Native.call: the reply comes back as the page's REPLY hook. */
    private fun call(token: Int, url: String, params: String): Boolean {
        main.post {
            val c = bus.call(appId, url, params) { reply ->
                if (!closed) outbox.add("${PageHooks.REPLY}($token, ${jsString(reply)})")
            }
            if (c.subscribe && !c.cancelled) calls[token] = c
        }
        return Bus.staysOpen(url, params)
    }

    /** The screen as it is held, in the device's own px. */
    private fun width() = if (portrait) minOf(shell.screenWidth, shell.screenHeight) else maxOf(shell.screenWidth, shell.screenHeight)
    private fun height() = if (portrait) maxOf(shell.screenWidth, shell.screenHeight) else minOf(shell.screenWidth, shell.screenHeight)
    private fun screenOrientation() = ownOrientation ?: shell.orientation

    /** ShellActivity.deviceInfo's answer for a TouchPad's screen, the card the whole of it. */
    private fun deviceInfo(): String {
        val d = profile
        return JSONObject(mapOf(
            "modelName" to d.modelName, "modelNameAscii" to d.modelNameAscii,
            "platformVersion" to d.platformVersion, "platformVersionMajor" to d.platformVersionMajor,
            "platformVersionMinor" to d.platformVersionMinor, "platformVersionDot" to d.platformVersionDot,
            "carrierName" to d.carrierName,
            "serialNumber" to DeviceProfile.serialFor(d, "luna-de:" + System.getProperty("user.name")),
            // The screen as the device names it, whichever way it is held: a TouchPad says
            // 1024 x 768, a Pre3 480 x 800 (measured on both, Docs/pre3.md).
            "screenWidth" to shell.screenWidth, "screenHeight" to shell.screenHeight,
            "minimumCardWidth" to shell.screenWidth, "minimumCardHeight" to d.minimumCardHeight,
            "maximumCardWidth" to shell.screenWidth, "maximumCardHeight" to shell.screenHeight - d.positiveSpaceTopPadding,
            "touchableRows" to d.touchableRows,
            "keyboardAvailable" to d.keyboardAvailable, "keyboardSlider" to d.keyboardSlider,
            "keyboardType" to d.keyboardType,
            "wifiAvailable" to true, "bluetoothAvailable" to d.bluetoothAvailable,
            *(if (d.reportsCarrierAvailable) arrayOf("carrierAvailable" to d.carrierAvailable) else emptyArray()),
            "coreNaviButton" to d.coreNaviButton, "swappableBattery" to d.swappableBattery,
            "dockModeEnabled" to true,
        )).toString()
    }

    /** PalmSystem's locale fields, from the desktop's own (ShellActivity.localeInfo's shape). */
    private fun localeInfo(): String {
        val l = Locale.getDefault()
        val language = l.language.lowercase().ifEmpty { "en" }
        val country = l.country.lowercase().ifEmpty { "us" }
        return JSONObject().put("locale", language + "_" + country).put("localeRegion", country)
            .put("phoneRegion", country).put("timeFormat", "HH12").toString()
    }

    companion object {
        /** Plausible process ids, in the range webOS apps started at (AppWindow's). */
        val nextPid = AtomicInteger(1183)
        /** How long a page may go without asking for its scripts before it is taken for gone. */
        const val STALE_MS = 60_000L
    }
}
