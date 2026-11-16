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
 * First light (2026-11-15) answers what an Enyo app needs to start: the device (a TouchPad,
 * 1024 x 768), the bus, the locale. The rest answers as a device with nothing behind it would,
 * or not at all; the network shim and WebSQL aren't offered yet, so net.js and websql.js stand
 * aside (Docs/luna-de.md).
 */
class DesktopWindow(val pageId: String, val appId: String, private val bus: Bus, private val main: DesktopScheduler,
                    private val shell: DesktopShell) : org.webosarchive.lunacy.card.RunningWindow {
    private val profile = DeviceProfile.TOUCHPAD
    private val outbox = LinkedBlockingQueue<String>()
    private val calls = HashMap<Int, Bus.Call>()   // on the main thread only
    override val pid = nextPid.getAndIncrement()
    @Volatile private var closed = false
    private val portrait get() = shell.portrait
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

    /** A method of window.LunacyNative, with its arguments; its value as JSON text. */
    fun invoke(method: String, a: JSONArray): String = when (method) {
        "deviceInfo" -> JSONObject.quote(deviceInfo())
        "screenSize" -> JSONObject.quote(JSONObject().put("width", width()).put("height", height()).toString())
        "cardSize" -> JSONObject.quote(JSONObject().put("width", width()).put("height", height()).put("scale", 1.0).toString())
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
        "call" -> call(a.optInt(0), a.optString(1), a.optString(2)).toString()
        "cancel" -> { val t = a.optInt(0); main.post { calls.remove(t)?.cancel() }; "null" }
        "log" -> { Log.i(Lunacy.TAG, "[$appId] palm ${a.optString(0)}"); "null" }
        // Luna DE's own: the page has loaded, and a script's answer (relaunch).
        "lunadeLoaded", "stageReady", "pageDrawn" -> { main.post { pageUp() }; "null" }
        "lunadeResult" -> { val t = a.optInt(0); val v = a.optBoolean(1); main.post { results.remove(t)?.invoke(v) }; "null" }
        "clipboardHasText" -> "false"
        // PalmSystem.addBannerMessage(message, launch params, icon ...): the shell shows it, and a
        // tap on it launches the app with those params.
        "addBanner" -> shell.banner(this, a.optString(0), a.optString(1), a.optString(2)).toString()
        "removeBanner" -> { shell.removeBanner(a.optInt(0)); "null" }
        "clearBanners" -> { shell.clearBanners(this); "null" }
        // PalmSystem.activate(): the window asks to be brought forward.
        "activate" -> { shell.activate(this); "null" }
        "pluginStart" -> "-1"
        "pluginCall" -> JSONObject.quote("{\"exception\":\"plugin not connected\"}")
        "webViewCreate" -> "-1"
        // What the page tells the shell (it drew, it wants the keyboard, the screen to stay on ...)
        // has nothing to act on yet.
        else -> "null"
    }

    /** The scripts waiting for the page, after waiting up to [ms] for the first. */
    fun take(ms: Long): List<String> {
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
            "LunacyNative.lunadeResult($token,r===true)})()")
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

    /** The webOS back gesture: an ESC key event, which Enyo and Mojo turn into "back". */
    fun sendBack() {
        if (!closed) outbox.add("(function(){function k(t){var e=document.createEvent('Events');e.initEvent(t,true,true);e.keyCode=27;e.which=27;(document.activeElement||document).dispatchEvent(e);}k('keydown');k('keyup');})()")
    }

    fun close() {
        closed = true
        net.reset()
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

    private fun width() = if (portrait) 768 else 1024
    private fun height() = if (portrait) 1024 else 768
    private fun screenOrientation() = shell.orientation

    /** ShellActivity.deviceInfo's answer for a TouchPad's screen, the card the whole of it. */
    private fun deviceInfo(): String {
        val d = profile
        return JSONObject(mapOf(
            "modelName" to d.modelName, "modelNameAscii" to d.modelNameAscii,
            "platformVersion" to d.platformVersion, "platformVersionMajor" to d.platformVersionMajor,
            "platformVersionMinor" to d.platformVersionMinor, "platformVersionDot" to d.platformVersionDot,
            "carrierName" to d.carrierName,
            "serialNumber" to DeviceProfile.serialFor(d, "lunade:" + System.getProperty("user.name")),
            "screenWidth" to width(), "screenHeight" to height(),
            "minimumCardWidth" to width(), "minimumCardHeight" to d.minimumCardHeight,
            "maximumCardWidth" to width(), "maximumCardHeight" to height() - d.positiveSpaceTopPadding,
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
    }
}
