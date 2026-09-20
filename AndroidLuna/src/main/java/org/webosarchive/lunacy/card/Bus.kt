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
    class Call(val appId: String, val service: String, val method: String, val params: JSONObject, private val send: (String) -> Unit) {
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

    init {
        register("com.palm.bus", "signal/registerServerStatus", CallHandler { serverStatus(it) })
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

    fun call(appId: String, url: String, params: String, reply: (String) -> Unit): Call {
        val m = Regex("^(?:palm|luna)://([^/]+)/(.*?)/?$").find(url)
        val service = m?.groupValues?.get(1) ?: ""
        val method = m?.groupValues?.get(2) ?: ""
        val h = handlers["$service/$method"] ?: services[service]
        Log.i(AppServer.TAG, "bus [$appId] $service/$method ${if (h == null) "UNHANDLED" else ""} $params")
        val p = try { JSONObject(params.ifEmpty { "{}" }) } catch (e: Exception) { JSONObject() }
        val call = Call(appId, service, method, p, reply)
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
        fun error(text: String, code: Int = -1): String =
            JSONObject(mapOf("returnValue" to false, "errorCode" to code, "errorText" to text)).toString()
        fun ok(extra: Map<String, Any?> = emptyMap()): String =
            JSONObject(mapOf("returnValue" to true) + extra).toString()
    }
}
