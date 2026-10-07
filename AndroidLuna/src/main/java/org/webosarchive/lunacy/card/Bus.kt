package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONObject

/**
 * The simulated Luna bus. Handlers answer by calling reply with a JSON string. Unknown
 * services get webOS's own error, never a fake success. A call with "subscribe": true stays
 * open: its handler keeps replying as things change, until the page cancels it or its window
 * closes. See Docs/architecture.md, "Luna bus".
 */
class Bus {
    /** A one-shot handler. */
    fun interface Handler { fun handle(appId: String, params: JSONObject, reply: (String) -> Unit) }
    /** A handler that may keep a subscription open. */
    fun interface CallHandler { fun handle(call: Call) }

    /**
     * One bus call. For a subscription, reply may run many times until cancel(). method is
     * everything after the service name, with its category ("time/getSystemTime").
     */
    class Call(val appId: String, val service: String, val method: String, val params: JSONObject, private val send: (String) -> Unit,
               /** Sent on the private bus: see [privileged]. */
               val privateBus: Boolean = false,
               /** The payload as sent: params is {} when it isn't an object (systemservice's getTimeZoneRules takes an array). */
               val raw: String = params.toString()) {
        /** Stays open: "subscribe": true, or a db8 watch ("watch": true on find, or the watch method). */
        val subscribe get() = params.optBoolean("subscribe", false) || params.optBoolean("watch", false) || method == "watch"
        @Volatile var cancelled = false
            private set
        private val onCancel = ArrayList<() -> Unit>()

        fun reply(json: String) { if (!cancelled) send(json) }
        /** Runs when the subscription ends. */
        fun onCancel(f: () -> Unit) = synchronized(onCancel) { if (cancelled) f() else onCancel += f }
        fun cancel() {
            val fs = synchronized(onCancel) { if (cancelled) return; cancelled = true; onCancel.toList() }
            fs.forEach { it() }
        }
    }

    private val handlers = HashMap<String, CallHandler>()  // "service/method"
    /** Services that take every method themselves: JS services, which answer unknown methods. */
    private val services = HashMap<String, CallHandler>()
    /** Open registerServerStatus calls, by the service name each one is watching. */
    private val statusWatchers = ArrayList<Pair<Bus.Call, String>>()

    /** Open com.palm.bus/signal/addmatch calls: the call and the category/method it matches. */
    private val signalWatchers = ArrayList<Triple<Call, String, String>>()

    init {
        register("com.palm.bus", "signal/registerServerStatus", CallHandler { serverStatus(it) })
        register("com.palm.bus", "signal/addmatch", CallHandler { addMatch(it) })
    }

    /**
     * palm://com.palm.bus/signal/addmatch: hear a signal a service sends, by its category and,
     * optionally, method. ls-hubd's own, like registerServerStatus. Measured on the reference
     * TouchPad, on either bus: `{"returnValue":true}` with or without `subscribe`, then the
     * signals themselves; with no category it never answers. App Catalog matches storaged's
     * MSMProgress (USB mass-storage mode), which Lunacy, having no such mode, never sends.
     */
    private fun addMatch(call: Call) {
        val category = call.params.optString("category")
        if (category.isEmpty()) return
        call.reply(ok())
        if (call.cancelled) return
        signalWatchers += Triple(call, category, call.params.optString("method"))
        call.onCancel { signalWatchers.removeAll { it.first === call } }
    }

    /** Sends a signal to whoever matched it with addmatch. */
    fun signal(category: String, method: String, payload: JSONObject) {
        signalWatchers.toList().forEach { (c, cat, m) ->
            if (cat == category && (m.isEmpty() || m == method)) c.reply(payload.toString())
        }
    }

    fun register(service: String, method: String, h: Handler) {
        handlers["$service/$method"] = CallHandler { c -> h.handle(c.appId, c.params, c::reply) }
    }

    fun register(service: String, method: String, h: CallHandler) { handlers["$service/$method"] = h }

    fun registerService(service: String, h: CallHandler) { services[service] = h; serverStatusChanged(service) }
    fun unregisterService(service: String) { services.remove(service); serverStatusChanged(service) }

    /** Whether anything on this bus answers for a service name. */
    fun has(service: String) = services.containsKey(service) || handlers.keys.any { it.startsWith("$service/") }

    /**
     * palm://com.palm.bus/signal/registerServerStatus: is this service on the bus? Apps wait
     * for it before calling one (Palm's Help app does, before the connection manager). On
     * webOS this is ls-hubd's own signal, so Lunacy's router answers it rather than any
     * service. Measured on the reference TouchPad: {"serviceName":..,"connected":true|false},
     * with no returnValue, and a subscription that answers again when that changes.
     */
    private fun serverStatus(call: Call) {
        val name = call.params.optString("serviceName")
        if (name.isEmpty()) return call.reply(error("Invalid payload."))
        call.reply(JSONObject(mapOf("serviceName" to name, "connected" to has(name))).toString())
        if (call.subscribe && !call.cancelled) {
            statusWatchers += call to name
            call.onCancel { statusWatchers.removeAll { (c, _) -> c === call } }
        }
    }

    /** A service came or went (a package's JS services do): tell whoever is watching it. */
    private fun serverStatusChanged(service: String) {
        val connected = has(service)
        statusWatchers.toList().forEach { (c, name) ->
            if (name == service) c.reply(JSONObject(mapOf("serviceName" to name, "connected" to connected)).toString())
        }
    }

    /**
     * A call. [privateBus] is for callers that are part of the system - JS services and
     * package scripts' luna-send - which webOS put on the private bus; an app's page gets it
     * only if its id is privileged ([privileged]).
     */
    fun call(appId: String, url: String, params: String, privateBus: Boolean = false, reply: (String) -> Unit): Call {
        val m = Regex("^(?:palm|luna)://([^/]+)/(.*?)/?$").find(url)
        val service = m?.groupValues?.get(1) ?: ""
        val method = m?.groupValues?.get(2) ?: ""
        val h = handlers["$service/$method"] ?: services[service]
        Log.i(AppServer.TAG, "bus [$appId] $service/$method ${if (h == null) "UNHANDLED" else ""} $params")
        val p = try { JSONObject(params.ifEmpty { "{}" }) } catch (e: Exception) { JSONObject() }
        val call = Call(appId, service, method, p, reply, privateBus || privileged(appId), params.ifEmpty { "{}" })
        if (h == null) {
            // webOS names the category the method sits in: a call to
            // com.palm.systemservice/wallpaper/listWallpapers is unknown "for category
            // \"/wallpaper\"" (measured on the reference TouchPad).
            val category = "/" + method.substringBeforeLast('/', "")
            val leaf = method.substringAfterLast('/')
            call.reply(error(if (has(service)) "Unknown method \"$leaf\" for category \"$category\"" else "Service does not exist: $service."))
        } else h.handle(call)
        return call
    }

    companion object {
        /**
         * Whether an app's page reaches the private bus: its id starts with "com.palm.".
         * Measured on the reference TouchPad with `Workbench/probe/busprobe.sh` - the same page
         * as com.palm.lunacy.busprobe reached the accounts service's private methods and the
         * palmprofile service (which has no public side), while as org.webosarchive.… and as
         * com.webos.… it got "Unknown method" and "Service does not exist". The community's
         * apps that use the account take com.palm.* ids for exactly this reason.
         */
        fun privileged(appId: String) = appId.startsWith("com.palm.")

        fun error(text: String, code: Int = -1): String =
            JSONObject(mapOf("returnValue" to false, "errorCode" to code, "errorText" to text)).toString()
        fun ok(extra: Map<String, Any?> = emptyMap()): String =
            JSONObject(mapOf("returnValue" to true) + extra).toString()
    }
}
