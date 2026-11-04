package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONObject

/**
 * The simulated Luna bus. Handlers answer by calling reply with a JSON string. Unknown
 * services get webOS's own error, never a fake success. A call with "subscribe": true stays
 * open: its handler keeps replying as things change, until the page cancels it or its window
 * closes. See Docs/architecture.md, "Luna bus".
 *
 * **The bus lives on the main thread.** Calls, registrations and cancels happen there, and
 * handlers run there, so its tables and every service's subscriber lists are plain
 * collections. A caller on another thread (a page's bridge, a service process, db8's worker)
 * posts to the main thread first; a handler that works elsewhere replies from the main thread.
 * [mainOnly] holds every entry point to it: a debug build stops at a call from elsewhere, and
 * a release build logs it (audited across the bundled apps on 2026-11-04: none).
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
        /** Stays open ([staysOpen]). */
        val subscribe get() = staysOpen(method, params)
        /** The calling app's id, without the " <pid>" LS2 adds to a service process's name. */
        val app get() = appId.substringBefore(' ')
        @Volatile var cancelled = false
            private set
        private val onCancel = ArrayList<() -> Unit>()

        fun reply(json: String) { if (!cancelled) send(json) }
        /** Runs when the subscription ends. */
        fun onCancel(f: () -> Unit) = synchronized(onCancel) { if (cancelled) f() else onCancel += f }
        fun cancel() {
            mainOnly("cancel $service/$method")
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

    /** The shell that owned this bus is gone: see [close]. */
    @Volatile private var closed = false

    /**
     * Ends this bus: its shell was destroyed. Nothing on it is called again, so a timer or
     * reply still pending in a part of the old shell can't start a service or reach db8 beside
     * the new shell's (four shells' activity managers each ran webCal's sync at once, and each
     * sync added the feed's events again).
     */
    fun close() {
        closed = true
        (statusWatchers.map { it.first } + signalWatchers.map { it.first }).forEach { it.cancel() }
    }

    init {
        register(BUS, "signal/registerServerStatus", CallHandler { serverStatus(it) })
        register(BUS, "signal/addmatch", CallHandler { addMatch(it) })
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
        mainOnly("register")
        handlers["$service/$method"] = CallHandler { c -> h.handle(c.appId, c.params, c::reply) }
    }

    fun register(service: String, method: String, h: CallHandler) { mainOnly("register"); handlers["$service/$method"] = h }

    /**
     * A method only the private bus reaches. A public caller gets what the TouchPad gave: for a
     * service with no public side at all ([wholeService]), "Service does not exist" (the
     * systemmanager, the device profile, the installers); for a private method on a public
     * service, the router's own "Unknown method" (applicationManager's).
     */
    fun registerPrivate(service: String, method: String, wholeService: Boolean, h: CallHandler) =
        register(service, method, CallHandler { c ->
            if (c.privateBus) h.handle(c)
            else c.reply(if (wholeService) noService(service) else unknownMethod(method))
        })

    fun registerService(service: String, h: CallHandler) { mainOnly("registerService"); services[service] = h; serverStatusChanged(service) }
    fun unregisterService(service: String) { mainOnly("unregisterService"); services.remove(service); serverStatusChanged(service) }

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
        mainOnly("call $url")
        val (service, method) = serviceAndMethod(url)
        val h = handlers["$service/$method"] ?: services[service]
        Log.i(AppServer.TAG, "bus [$appId] $service/$method ${if (h == null) "UNHANDLED" else ""} ${if (personal(appId, service, params)) "(${params.length} characters)" else redact(params)}")
        val p = try { JSONObject(params.ifEmpty { "{}" }) } catch (e: Exception) { JSONObject() }
        val call = Call(appId, service, method, p, reply, privateBus || privileged(appId), params.ifEmpty { "{}" })
        if (closed) {
            Log.w(AppServer.TAG, "bus closed: [$appId] $service/$method not sent")
            call.cancel()
        } else if (h == null) {
            // webOS names the category the method sits in: a call to
            // com.palm.systemservice/wallpaper/listWallpapers is unknown "for category
            // \"/wallpaper\"" (measured on the reference TouchPad).
            call.reply(if (has(service)) unknownMethod(method) else noService(service))
        } else h.handle(call)
        return call
    }

    companion object {
        /**
         * Whether a call stays open: "subscribe": true, or a db8 watch ("watch": true on find,
         * or the watch method). One rule for the bus, luna-send's relay and the pages' bridge.
         */
        fun staysOpen(method: String, params: JSONObject) =
            params.optBoolean("subscribe", false) || params.optBoolean("watch", false) || method == "watch"

        /** ls-hubd's own name: its signals live here. */
        const val BUS = "com.palm.bus"
        /** LunaSysMgr's application manager: launching, launch points, dock mode, packages. */
        const val APPLICATION_MANAGER = "com.palm.applicationManager"

        private val URL = Regex("^(?:palm|luna)://([^/]+)/(.*?)/?$")

        /** A bus URL's service and method ("time/getSystemTime", its category included). */
        fun serviceAndMethod(url: String): Pair<String, String> =
            URL.find(url).let { (it?.groupValues?.get(1) ?: "") to (it?.groupValues?.get(2) ?: "") }

        /** [staysOpen] for a call by its URL and payload, before it is made (luna-send's relay, the pages' bridge). */
        fun staysOpen(url: String, payload: String): Boolean =
            staysOpen(serviceAndMethod(url).second, runCatching { JSONObject(payload.ifEmpty { "{}" }) }.getOrDefault(JSONObject()))

        /** Whether a reply says it worked: its returnValue, read as JSON (never by searching the text). */
        fun succeeded(reply: String): Boolean = parse(reply)?.optBoolean("returnValue") ?: false
        fun errorText(reply: String): String = parse(reply)?.optString("errorText").orEmpty()
        fun errorCode(reply: String): Int? = parse(reply)?.takeIf { it.has("errorCode") }?.optInt("errorCode")
        private fun parse(reply: String) = runCatching { JSONObject(reply) }.getOrNull()

        fun noService(service: String) = error("Service does not exist: $service.")

        /** webOS names the category the method sits in ("/wallpaper" for wallpaper/listWallpapers; measured). */
        fun unknownMethod(method: String) =
            error("Unknown method \"${method.substringAfterLast('/')}\" for category \"/${method.substringBeforeLast('/', "")}\"")

        /** A debug build stops at a break of [mainOnly]'s rule; set by the shell as it starts. */
        @Volatile var strict = false

        /** The bus's one-thread rule: see the class doc. */
        fun mainOnly(what: String) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return
            val e = IllegalStateException("bus: $what off the main thread (${Thread.currentThread().name})")
            if (strict) throw e
            Log.e(AppServer.TAG, e.message, e)
        }

        /**
         * Whether an app's page reaches the private bus: its id starts with "com.palm.".
         * Measured on the reference TouchPad with `Workbench/probe/busprobe.sh` - the same page
         * as com.palm.lunacy.busprobe reached the accounts service's private methods and the
         * palmprofile service (which has no public side), while as org.webosarchive.… and as
         * com.webos.… it got "Unknown method" and "Service does not exist". The community's
         * apps that use the account take com.palm.* ids for exactly this reason.
         */
        fun privileged(appId: String) = appId.startsWith("com.palm.")

        /**
         * The log's copy of a call's parameters, with passwords and the like blanked: the bus
         * log is in Android's log on every build, and Email's account checks carry the
         * account's password. Only the log line changes; the service gets the call as sent.
         */
        private val SECRET = Regex("""("(?:password|passwd|pin|secret|token|authToken|accessToken|refreshToken|client_secret)"\s*:\s*)"(?:[^"\\]|\\.)*"""", RegexOption.IGNORE_CASE)
        fun redact(params: String): String = SECRET.replace(params) { it.groupValues[1] + "\"…\"" }

        /**
         * Whether a call may carry people's details or plans, which the bus log leaves out: every
         * call of Contacts, Calendar and their services, and any with a contact, person or event
         * in it. Other apps look people up through the contacts services, and a merge by `_id`
         * names no kind, so an event is also told by its fields.
         */
        private fun personal(appId: String, service: String, params: String) =
            handlesPeople(appId) || handlesPeople(service) || PERSONAL.any { it in params }
        private val PERSONAL = listOf("com.palm.contact", "com.palm.person", "org.webosarchive.contact", "com.palm.calendarevent",
            "org.webosarchive.calendar", "com.palm.service.calendar.reminders", "\"dtstart\"", "\"alarm\"")

        /** Contacts, Calendar and their services, whose calls and consoles print names, numbers and events. */
        fun handlesPeople(id: String) = PEOPLE_APPS.any { id.startsWith(it) }
        private val PEOPLE_APPS = listOf("com.palm.app.contacts", "com.palm.service.contacts", "com.palm.app.calendar",
            "com.palm.service.calendar", "org.webosarchive.webcal")

        fun error(text: String, code: Int = -1): String =
            JSONObject(mapOf("returnValue" to false, "errorCode" to code, "errorText" to text)).toString()
        fun ok(extra: Map<String, Any?> = emptyMap()): String =
            JSONObject(mapOf("returnValue" to true) + extra).toString()
    }
}
