package org.webosarchive.lunacy.card

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.GeolocationPermissions
import org.json.JSONObject

/**
 * webOS's location service, palm://com.palm.location, answered from Android's location.
 *
 * On webOS the service owned the device's location sources (GPS, and Google's location service)
 * and decided which app got a position. The Location Services app set how: for applications,
 * "Auto Locate" or "Always Ask"; for websites, "Always Ask" or "Never Share Location", with
 * "Clear My Location Data"; and, from its menu, which sources to use. Asking was SystemUI's
 * popup alert (SystemManagerAlerts' LocationAlert): "Allow the following application to use
 * your location for this session?" with Allow Once and Don't Allow, and for a website Always
 * Allow too. Its answer came back here (acceptLocationRequest and the rest).
 *
 * Here Android owns the sources and its permission covers Lunacy as a whole; this service is the
 * webOS layer over it, so each webOS app and site is still asked, as on webOS, before it learns
 * where the device is. GPS is Android's GPS provider, "Google Services" its network provider.
 * An answer for an app lasts while the app runs ("for this session"). Lunacy ships Always Ask
 * for applications (codepoet, 2026-11-07), where Palm's code started a new device on Auto Locate.
 * Pages ask through their WebView ([pageRequest]): an app's own page as that app, a web page in
 * a browser view as its site.
 *
 * The replies are the reference TouchPad's (webOS CE 3.1.0), measured 2026-11-07: the getters
 * answer a bare object ({"autoLocate":false}), every setter {"returnValue":true}, and a request
 * that can't be answered {"errorCode":N}, N as webOS numbered them ([OFF] measured there with
 * every source off). Nothing is ever made up: no position without one from Android.
 */
class LocationService(
    private val context: Context,
    /** An app's title, for the prompt. */
    private val appTitle: (String) -> String?,
    /** Puts the prompt up (the Location alert, in [ALERTS_APP]); false when it can't be. */
    private val showPrompt: (type: String, id: String, name: String) -> Boolean,
    /** Android's runtime permission dialog (ShellActivity.askPermissions). */
    private val askAndroid: (List<String>, (granted: Boolean, askAgain: Boolean) -> Unit) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("location", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

    /** Apps answered for this session: they keep the answer while they run ([appEnded]). */
    private val sessionAllowed = HashSet<String>()
    private val sessionDenied = HashSet<String>()

    /** One question for the owner, and everything waiting on its answer. */
    private class Ask(val type: String, val id: String, val name: String) {
        val waiters = ArrayList<(Boolean) -> Unit>()
    }
    private val queue = ArrayDeque<Ask>()
    private var showing: Ask? = null
    private val promptTimeout = Runnable { showing?.let { answer(it.type, it.id, "ignore") } }

    fun register(bus: Bus) {
        current = this
        bus.register(SERVICE, "getCurrentPosition", Bus.CallHandler(::getCurrentPosition))
        bus.register(SERVICE, "startTracking", Bus.CallHandler(::startTracking))
        bus.register(SERVICE, "getLocationServicePrefs") { _, _, reply -> reply(prefsJson().toString()) }
        getter(bus, "getAutoLocate", "autoLocate", false)
        getter(bus, "getUseGps", "useGps", true)
        getter(bus, "getGeotagPhotos", "geotagPhotos", false)
        getter(bus, "getUseBackgroundDataCollection", "useBackgroundDataCollection", false)
        getter(bus, "getWebSetting", "webSetting", true)
        setter(bus, "setAutoLocate", "autoLocate")
        setter(bus, "setUseGps", "useGps")
        setter(bus, "setUseGoogle", "useGoogle")
        setter(bus, "setWebSetting", "webSetting")
        setter(bus, "setGeotagPhotos", "geotagPhotos")
        setter(bus, "setUseBackgroundDataCollection", "useBackgroundDataCollection")
        // Palm's terms of use were for its Google location service, long gone; Android's network
        // location has its own consent, on Android's side. Taking them is a no-op that succeeds,
        // and isTermsOfUseAccepted is always true, so Location Services never shows them.
        settingCall(bus, "acceptTermsOfUse") { _ -> }
        settingCall(bus, "rejectTermsOfUse") { _ -> }
        settingCall(bus, "clearWebSetting") { _ ->
            prefs.edit().remove(WEB_ALLOWED).apply()
            runCatching { GeolocationPermissions.getInstance().clearAll() }
        }
        for ((method, verdict) in listOf("acceptLocationRequest" to "accept", "acceptAlwaysLocationRequest" to "always",
                "rejectLocationRequest" to "reject", "ignoreLocationRequest" to "ignore")) {
            bus.register(SERVICE, method, Bus.CallHandler { c ->
                // Only the prompt answers it: an app that could would answer for itself.
                if (c.app != ALERTS_APP) { c.reply(Bus.error("Only the system's location alert answers a location request", -1)); return@CallHandler }
                val appId = c.params.optString("appId"); val url = c.params.optString("url")
                if (appId.isNotEmpty()) answer("app", appId, verdict) else if (url.isNotEmpty()) answer("web", url, verdict)
                c.reply(Bus.ok())
            })
        }
    }

    fun close() {
        if (current === this) current = null
        main.removeCallbacksAndMessages(null)
        for (l in listeners.toList()) runCatching { manager?.removeUpdates(l) }
        listeners.clear()
    }

    /** The app has ended: its session's answer goes with it. */
    fun appEnded(appId: String) {
        sessionAllowed.remove(appId); sessionDenied.remove(appId)
    }

    // ---- the settings ----

    private fun pref(key: String, default: Boolean) = prefs.getBoolean(key, default)

    private fun gpsAvailable() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    private fun prefsJson() = JSONObject()
        .put("autoLocate", pref("autoLocate", false))
        // The Geotag Photos switch was the TouchPad Camera's; Lunacy has no Camera, so it is hidden.
        .put("cameraAvailable", false)
        .put("geotagPhotos", pref("geotagPhotos", false))
        .put("gpsAvailable", gpsAvailable())
        .put("isTermsOfUseAccepted", true)
        .put("useBackgroundDataCollection", pref("useBackgroundDataCollection", false))
        .put("useGoogle", pref("useGoogle", true))
        .put("useGps", pref("useGps", true) && gpsAvailable())
        .put("webSetting", pref("webSetting", true))

    private fun getter(bus: Bus, method: String, key: String, default: Boolean) =
        bus.register(SERVICE, method) { _, _, reply ->
            val v = if (key == "useGps") pref(key, default) && gpsAvailable() else pref(key, default)
            reply(JSONObject().put(key, v).toString())
        }

    /** A setting is the owner's: Location Services (or Lunacy's own apps, or the system's) sets it, never an app for itself. */
    private fun settingCall(bus: Bus, method: String, f: (JSONObject) -> Unit) =
        bus.register(SERVICE, method, Bus.CallHandler { c ->
            if (!c.privateBus && !c.app.startsWith("org.webosarchive.lunacy")) {
                c.reply(Bus.error("Only Location Services changes location settings", -1)); return@CallHandler
            }
            f(c.params)
            c.reply(Bus.ok())
        })

    private fun setter(bus: Bus, method: String, key: String) = settingCall(bus, method) { p ->
        // Measured: a setter without its value still answers true and changes nothing.
        if (p.has(key)) prefs.edit().putBoolean(key, p.optBoolean(key)).apply()
    }

    // ---- asking ----

    /**
     * Whether [id] may have the location now: an app (its page or its service call) or a site.
     * [done] gets [OK], or the errorCode to answer with. [joinPending]: a second request while
     * the owner is being asked waits for that answer (a page) or is told a request is pending
     * (a service call, [PENDING]).
     */
    private fun authorize(type: String, id: String, name: String, joinPending: Boolean, done: (Int) -> Unit) {
        if (type == "app") {
            if (pref("autoLocate", false) || id in sessionAllowed) { withAndroid(done); return }
            if (id in sessionDenied) { done(DENIED); return }
        } else {
            if (!pref("webSetting", true)) { done(DENIED); return }
            if (siteKey(id) in webAllowed()) { withAndroid(done); return }
        }
        val pending = (listOfNotNull(showing) + queue).firstOrNull { it.type == type && it.id == id }
        if (pending != null && !joinPending) { done(PENDING); return }
        val ask = pending ?: Ask(type, id, name).also { queue.add(it) }
        ask.waiters += { ok -> if (ok) withAndroid(done) else done(DENIED) }
        showNext()
    }

    private fun showNext() {
        if (showing != null) return
        val ask = queue.removeFirstOrNull() ?: return
        showing = ask
        if (!showPrompt(ask.type, ask.id, ask.name)) { finish(ask, false); return }
        main.postDelayed(promptTimeout, PROMPT_TIMEOUT_MS)
    }

    /** The owner's answer, from the alert: accept (Allow Once), always (Always Allow, a site's), reject, or ignore (the alert went without a tap). */
    private fun answer(type: String, id: String, verdict: String) {
        val ask = showing?.takeIf { it.type == type && it.id == id }
        val allowed = verdict == "accept" || verdict == "always"
        if (type == "app") {
            when (verdict) {
                "accept", "always" -> { sessionAllowed += id; sessionDenied -= id }
                "reject" -> { sessionDenied += id; sessionAllowed -= id }
            }
        } else if (verdict == "always") {
            prefs.edit().putStringSet(WEB_ALLOWED, webAllowed() + siteKey(id)).apply()
        }
        if (ask != null) finish(ask, allowed)
    }

    private fun finish(ask: Ask, allowed: Boolean) {
        main.removeCallbacks(promptTimeout)
        if (showing === ask) showing = null
        ask.waiters.forEach { it(allowed) }
        main.post { showNext() }
    }

    private fun webAllowed(): Set<String> = prefs.getStringSet(WEB_ALLOWED, emptySet()) ?: emptySet()

    /** A site by its scheme, host and port: what a WebView's origin names. */
    private fun siteKey(url: String) = runCatching {
        val u = android.net.Uri.parse(url); "${u.scheme}://${u.host}${if (u.port > 0) ":" + u.port else ""}"
    }.getOrDefault(url)

    /** The webOS answer was yes: Android's own permission next, asked for if it never has been. */
    private fun withAndroid(done: (Int) -> Unit) {
        if (androidGranted()) { done(OK); return }
        askAndroid(LunacyService.LOCATION) { _, _ ->
            LunacyService.setLocationAsked(context)
            done(if (androidGranted()) OK else DENIED)
        }
    }

    private fun androidGranted() = Build.VERSION.SDK_INT < 23 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun fineGranted() = Build.VERSION.SDK_INT < 23 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ---- WebView pages ----

    /**
     * A page's navigator.geolocation asking (WebChromeClient.onGeolocationPermissionsShowPrompt):
     * an app's own page as the app ([type] "app", [id] its app id), a web page as its site ("web").
     * The page's WebView reads Android's location itself once allowed.
     */
    fun pageRequest(type: String, id: String, origin: String, callback: GeolocationPermissions.Callback) = main.post {
        val name = if (type == "app") appTitle(id).orEmpty() else (android.net.Uri.parse(origin).host ?: origin)
        authorize(type, if (type == "app") id else origin, name, joinPending = true) { code ->
            callback.invoke(origin, code == OK, false)
        }
    }

    // ---- positions ----

    /** Android's providers that the settings and Android allow now: GPS (with precise location) and the network's. */
    private fun providers(): List<String> {
        val m = manager ?: return emptyList()
        val out = ArrayList<String>()
        if (pref("useGps", true) && gpsAvailable() && fineGranted() && runCatching { m.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false))
            out += LocationManager.GPS_PROVIDER
        if (pref("useGoogle", true) && runCatching { m.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false))
            out += LocationManager.NETWORK_PROVIDER
        return out
    }

    private fun error(code: Int) = JSONObject().put("errorCode", code).toString()

    private fun result(l: Location) = JSONObject()
        .put("errorCode", OK)
        .put("timestamp", l.time.toDouble())
        .put("latitude", l.latitude)
        .put("longitude", l.longitude)
        .put("horizAccuracy", if (l.hasAccuracy()) l.accuracy.toDouble() else -1.0)
        .put("heading", if (l.hasBearing()) l.bearing.toDouble() else -1.0)
        .put("velocity", if (l.hasSpeed()) l.speed.toDouble() else -1.0)
        .put("altitude", if (l.hasAltitude()) l.altitude else -1.0)
        .put("vertAccuracy", if (Build.VERSION.SDK_INT >= 26 && l.hasVerticalAccuracy()) l.verticalAccuracyMeters.toDouble() else -1.0)
        .toString()

    private val listeners = ArrayList<LocationListener>()

    /**
     * getCurrentPosition {accuracy, maximumAge, responseTime}: accuracy 1 (high) to 3 (low),
     * maximumAge in seconds (a fix that old will do), responseTime 1 (under 5 s) to 3 (longer).
     * A high-accuracy request waits for GPS while there is time, keeping a network fix in hand.
     */
    private fun getCurrentPosition(call: Bus.Call) {
        val p = call.params
        val accuracy = p.optInt("accuracy", 3)
        val maxAgeMs = p.optLong("maximumAge", 0) * 1000
        val timeoutMs = when (p.optInt("responseTime", 2)) { 1 -> 5_000L; 3 -> 60_000L; else -> 20_000L }
        authorize("app", call.app, appTitle(call.app).orEmpty(), joinPending = false) { code ->
            if (code != OK) { call.reply(error(code)); return@authorize }
            val sources = providers()
            if (sources.isEmpty()) { call.reply(error(OFF)); return@authorize }
            fix(sources, accuracy, maxAgeMs, timeoutMs) { l -> call.reply(if (l != null) result(l) else error(TIMEOUT)) }
        }
    }

    @Suppress("MissingPermission")
    private fun fix(sources: List<String>, accuracy: Int, maxAgeMs: Long, timeoutMs: Long, done: (Location?) -> Unit) {
        val m = manager ?: return done(null)
        if (maxAgeMs > 0) {
            val recent = sources.mapNotNull { runCatching { m.getLastKnownLocation(it) }.getOrNull() }
                .filter { System.currentTimeMillis() - it.time <= maxAgeMs }
                .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
            if (recent != null) return done(recent)
        }
        val wantGps = accuracy == 1 && LocationManager.GPS_PROVIDER in sources
        var best: Location? = null
        var finished = false
        lateinit var listener: LocationListener
        val end = Runnable {
            if (finished) return@Runnable
            finished = true
            runCatching { m.removeUpdates(listener) }; listeners.remove(listener)
            done(best)
        }
        listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                if (finished) return
                if (best == null || (l.hasAccuracy() && l.accuracy < (best?.accuracy ?: Float.MAX_VALUE))) best = l
                if (!wantGps || l.provider == LocationManager.GPS_PROVIDER) { main.removeCallbacks(end); end.run() }
            }
            @Deprecated("Android 5 calls it") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listeners += listener
        for (s in sources) runCatching { m.requestLocationUpdates(s, 0L, 0f, listener, Looper.getMainLooper()) }
            .onFailure { Log.w(AppServer.TAG, "location: $s: ${it.javaClass.simpleName}") }
        main.postDelayed(end, timeoutMs)
    }

    /** startTracking {subscribe: true}: every new fix, until the subscription ends. */
    @Suppress("MissingPermission")
    private fun startTracking(call: Bus.Call) {
        authorize("app", call.app, appTitle(call.app).orEmpty(), joinPending = false) { code ->
            if (code != OK) { call.reply(error(code)); return@authorize }
            val sources = providers()
            val m = manager
            if (sources.isEmpty() || m == null) { call.reply(error(OFF)); return@authorize }
            val listener = object : LocationListener {
                override fun onLocationChanged(l: Location) = call.reply(result(l))
                @Deprecated("Android 5 calls it") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            listeners += listener
            for (s in sources) runCatching { m.requestLocationUpdates(s, TRACK_MS, 0f, listener, Looper.getMainLooper()) }
            call.onCancel { main.post { runCatching { m.removeUpdates(listener) }; listeners.remove(listener) } }
        }
    }

    companion object {
        const val SERVICE = "com.palm.location"
        /** Lunacy's host for SystemUI's alerts: it puts the Location alert up. */
        const val ALERTS_APP = "org.webosarchive.lunacy.systemalerts"
        /** The service while Lunacy runs, for the WebViews' prompts. */
        @Volatile var current: LocationService? = null

        // webOS's errorCodes for a location request.
        const val OK = 0
        const val TIMEOUT = 1
        /** Every source off, or Android's location off (measured on the TouchPad with every source off). */
        const val OFF = 5
        /** Not allowed: the owner said no, or Android's permission isn't there. */
        const val DENIED = 6
        /** This app's request is already waiting on the owner. */
        const val PENDING = 7

        private const val WEB_ALLOWED = "webAllowed"
        /** An alert left with no answer and never closed counts as ignored after this. */
        private const val PROMPT_TIMEOUT_MS = 120_000L
        private const val TRACK_MS = 1_000L
    }
}
