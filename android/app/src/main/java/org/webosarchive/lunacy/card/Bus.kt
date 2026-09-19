package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONObject

/**
 * The simulated Luna bus. Handlers answer by calling reply with a JSON string. Unknown
 * services get webOS's own error, never a fake success. A call with "subscribe": true stays
 * open: its handler keeps replying as things change, until the page cancels it or its window
 * closes. See docs/architecture.md, "Luna bus".
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

    fun register(service: String, method: String, h: Handler) {
        handlers["$service/$method"] = CallHandler { c -> h.handle(c.appId, c.params, c::reply) }
    }

    fun register(service: String, method: String, h: CallHandler) { handlers["$service/$method"] = h }

    fun registerService(service: String, h: CallHandler) { services[service] = h }
    fun unregisterService(service: String) { services.remove(service) }

    fun call(appId: String, url: String, params: String, reply: (String) -> Unit): Call {
        val m = Regex("^(?:palm|luna)://([^/]+)/(.*?)/?$").find(url)
        val service = m?.groupValues?.get(1) ?: ""
        val method = m?.groupValues?.get(2) ?: ""
        val h = handlers["$service/$method"] ?: services[service]
        Log.i(AppServer.TAG, "bus [$appId] $service/$method ${if (h == null) "UNHANDLED" else ""} $params")
        val p = try { JSONObject(params.ifEmpty { "{}" }) } catch (e: Exception) { JSONObject() }
        val call = Call(appId, service, method, p, reply)
        if (h == null) {
            val known = handlers.keys.any { it.startsWith("$service/") }
            call.reply(error(if (known) "Unknown method \"$method\" for category \"/\"" else "Service does not exist: $service."))
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
