package org.webosarchive.lunacy.card

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * palm://com.palm.activitymanager: webOS's scheduler of work. An activity runs when its
 * trigger has fired (a bus subscription whose reply matches), its schedule is due (a start
 * time, an interval) and its requirements are met (internet, a connection's confidence,
 * charging...); running, it tells its subscribers "start" and calls its callback with
 * "$activity", and whoever handles it adopts it and completes it, perhaps with restart, which
 * arms it again. Persistent activities live in db8 (com.palm.activity:1) and come back when
 * Lunacy starts. The mail services run on this entirely: every sync, upload and send is an
 * activity triggered by a db8 watch.
 *
 * The behaviour is Open webOS's activity manager's (LG's Apache 2.0 release of HP's); the
 * replies that were measured on the reference TouchPad (webOS CE 3.1.0) are its: create's
 * {"activityId","returnValue":true}, events {"activityId","event","returnValue":true}, an
 * unknown id's errorCode 2, and list's activity objects. Lunacy has one bus, so "type.bus"
 * is recorded but not enforced. See Docs/architecture.md, "Activity manager".
 *
 * Main thread throughout.
 */
class ActivityManager(private val context: Context) {
    private lateinit var bus: Bus
    private val main = Handler(Looper.getMainLooper())

    private inner class Sub(val call: Bus.Call, val detailed: Boolean, val who: JSONObject)

    private inner class Activity(var id: Int, var name: String, val creator: JSONObject) {
        var description = ""
        var metadata: JSONObject? = null
        var persist = false; var explicit = false; var continuous = false; var userInitiated = false
        var power = false; var powerDebounce = false
        var immediate = false; var priority = "low"
        /** How type was given: "foreground", "background" or null (immediate and priority). */
        var simpleType: String? = "background"
        var busType = "private"
        var callback: JSONObject? = null
        var trigger: JSONObject? = null
        var schedule: Schedule? = null
        var requirements = JSONObject()

        val subs = ArrayList<Sub>()
        var parent: Sub? = null
        var releasedParent: Sub? = null
        val adopters = ArrayDeque<Sub>()

        var initialized = false
        var running = false
        var paused = false
        var queued = false
        /** The command that is ending it: "cancel", "stop" or "complete". */
        var ending: String? = null
        var terminate = false
        var restart = false
        var focused = false
        var triggerCall: Bus.Call? = null
        /** The reply that fired the trigger, while it has. */
        var fired: JSONObject? = null
        var serial = 0L
        var dbId: String? = null
        var dbRev: Long? = null

        val key get() = "$name\u0000${creatorKey(creator)}"
    }

    private val activities = LinkedHashMap<Int, Activity>()
    private val byName = HashMap<String, Activity>()
    private var nextId = 1
    /**
     * Two steps, as webOS's activity manager took them. It read its persisted activities
     * before it went on the bus, so an id it handed out never clashed with one it was about to
     * restore: here calls wait until they are read ([loaded]). And it ran none until boot had
     * finished and the configurator had run: here until the webOS root and its native
     * services are up ([enable]), or a restored callback to a service not yet on the bus
     * would fail for good.
     */
    private var loaded = false
    private val held = ArrayList<() -> Unit>()
    private var enabled = false
    private val waitingForEnable = ArrayList<Activity>()

    fun register(bus: Bus) {
        this.bus = bus
        for ((m, h) in mapOf<String, (Bus.Call) -> Unit>(
            "create" to ::create, "join" to ::join, "monitor" to ::monitor, "release" to ::release, "adopt" to ::adopt,
            "complete" to ::complete, "schedule" to { c -> withActivity(c) { a -> external(a, "start", c) } },
            "start" to { c -> withActivity(c) { a -> external(a, "start", c) } },
            "stop" to { c -> withActivity(c) { a -> end(a, "stop", c) } },
            "cancel" to { c -> withActivity(c) { a -> end(a, "cancel", c) } },
            "pause" to { c -> withActivity(c) { a -> external(a, "pause", c) } },
            "focus" to ::focus, "unfocus" to ::unfocus, "addFocus" to ::addFocus,
            "list" to ::list, "getDetails" to ::getDetails,
            "enable" to { c -> c.reply(Bus.ok()) }, "disable" to { c -> c.reply(Bus.ok()) },
        )) bus.register(SERVICE, m, Bus.CallHandler { c -> if (loaded) h(c) else held += { h(c) } })
        for (m in listOf("associateApp", "associateService", "associateProcess", "associateNetworkFlow",
                "dissociateApp", "dissociateService", "dissociateProcess", "dissociateNetworkFlow", "unmapProcess"))
            bus.register(SERVICE, m, Bus.CallHandler { it.reply(Bus.ok()) })
        watchNetwork()
        watchBattery()
        load()
    }

    // ---- identity and lookup ----

    /** The caller as webOS's BusId: a service by its name, an app by its id (taken from the bus, not the payload). */
    private fun busId(call: Bus.Call): JSONObject =
        if (bus.has(call.appId)) JSONObject().put("serviceId", call.appId) else JSONObject().put("appId", call.appId.substringBefore(' '))

    private fun creatorKey(o: JSONObject) = o.optString("serviceId").ifEmpty { o.optString("appId").ifEmpty { o.optString("anonId") } }

    private fun lookup(call: Bus.Call): Activity? {
        val p = call.params
        if (p.has("activityId")) {
            val id = p.opt("activityId") as? Number ?: return null.also { call.reply(error(2, "Error retrieving activityId of Activity to operate on")) }
            return activities[id.toInt()] ?: null.also { call.reply(error(2, "activityId not found")) }
        }
        if (p.has("activityName")) {
            val name = p.opt("activityName") as? String ?: return null.also { call.reply(error(2, "Error retrieving activityName of Activity to operate on")) }
            return byName["$name\u0000${creatorKey(busId(call))}"] ?: null.also { call.reply(error(2, "Activity name/creator pair not found")) }
        }
        call.reply(error(2, "Activity ID or name not present in request"))
        return null
    }

    private inline fun withActivity(call: Bus.Call, f: (Activity) -> Unit) { lookup(call)?.let(f) }

    private fun serialOk(a: Activity, call: Bus.Call): Boolean {
        if (!call.params.has("serial")) return true
        if (a.callback == null) { call.reply(error(-1000, "Attempt to use callback sequence serial number match on Activity that does not have a callback")); return false }
        if (call.params.optLong("serial") != a.serial) { call.reply(error(-1000, "Call sequence serial numbers do not match")); return false }
        return true
    }

    // ---- create ----

    private fun create(call: Bus.Call) {
        val p = call.params
        val spec = p.optJSONObject("activity") ?: return call.reply(error(22, "Activity specification not present"))
        val a = try { parse(spec, busId(call)) } catch (e: SpecError) { return call.reply(error(12, e.message ?: "")) }
        if (!p.optBoolean("subscribe") && !(p.optBoolean("start") && a.callback != null))
            return call.reply(error(22, "Created Activity must specify \"start\" and a Callback if not subscribed"))
        val old = byName[a.key]
        if (old != null && !p.optBoolean("replace")) return call.reply(error(17, "Activity with that name already exists"))
        a.id = newId()
        if (old != null) {
            // The new one takes the old one's place in db8; the old one is cancelled.
            if (a.persist) { a.dbId = old.dbId; a.dbRev = old.dbRev } else old.dbId?.let { dbDel(it) }
            old.dbId = null
            old.terminate = true
            byName.remove(old.key)
            command(old, "cancel")
        }
        activities[a.id] = a
        byName[a.key] = a
        if (p.optBoolean("subscribe")) a.parent = subscribe(a, call)
        val reply = { call.reply(JSONObject().put("activityId", a.id).put("returnValue", true).toString()) }
        val go = {
            reply()
            if (p.optBoolean("start")) {
                start(a)
                if (!a.running) update(a)
            }
        }
        if (a.persist) persist(a, go) else go()
    }

    private class SpecError(text: String) : Exception(text)

    private fun parse(spec: JSONObject, caller: JSONObject): Activity {
        val name = spec.opt("name") as? String ?: throw SpecError("Activity name is required")
        val description = spec.opt("description") as? String ?: throw SpecError("Activity description is required")
        val creator = spec.opt("creator")?.let { c ->
            when (c) {
                is JSONObject -> c
                is String -> c.split(':', limit = 2).takeIf { it.size == 2 }?.let { JSONObject().put(it[0], it[1]) }
                else -> null
            } ?: throw SpecError("Invalid creator specified")
        } ?: caller
        val a = Activity(0, name, creator)
        a.description = description
        spec.opt("metadata")?.let { a.metadata = it as? JSONObject ?: throw SpecError("Object metadata should be set to a JSON object") }
        spec.optJSONObject("type")?.let { parseType(a, it) }
        spec.optJSONObject("callback")?.let { a.callback = url(it, "Callback") }
        spec.optJSONObject("trigger")?.let { a.trigger = parseTrigger(it) }
        spec.optJSONObject("schedule")?.let { a.schedule = Schedule.parse(it) }
        spec.optJSONObject("requirements")?.let { r -> for (k in r.keys()) setRequirement(a, k, r.get(k)) }
        return a
    }

    private fun parseType(a: Activity, t: JSONObject) {
        val fg = t.has("foreground"); val bg = t.has("background")
        if (fg && !t.optBoolean("foreground")) throw SpecError("If present, 'foreground' should be specified as 'true'")
        if (bg && !t.optBoolean("background")) throw SpecError("If present, 'background' should be specified as 'true'")
        if (fg && bg) throw SpecError("Only one of 'foreground' or 'background' should be specified")
        if ((fg || bg) && t.has("immediate")) throw SpecError("Only one of 'foreground', 'background', or 'immediate' should be specified")
        if ((fg || bg) && t.has("priority")) throw SpecError("Only one of 'foreground', 'background', or 'priority' should be set")
        when {
            fg -> { a.immediate = true; a.priority = "normal"; a.simpleType = "foreground" }
            bg -> { a.immediate = false; a.priority = "low"; a.simpleType = "background" }
            else -> {
                a.simpleType = null
                a.immediate = t.optBoolean("immediate")
                a.priority = t.optString("priority", "low").also { if (it !in PRIORITIES) throw SpecError("Invalid priority specified") }
            }
        }
        a.persist = t.optBoolean("persist"); a.explicit = t.optBoolean("explicit"); a.continuous = t.optBoolean("continuous")
        a.userInitiated = t.optBoolean("userInitiated"); a.power = t.optBoolean("power"); a.powerDebounce = t.optBoolean("powerDebounce")
        t.optString("bus").takeIf { it.isNotEmpty() }?.let { if (it != "public" && it != "private") throw SpecError("Illegal bus type seen"); a.busType = it }
    }

    /** A callback or trigger's method, as webOS checked it, reported back in palm:// form. */
    private fun url(o: JSONObject, what: String): JSONObject {
        val m = o.opt("method") as? String ?: throw SpecError("Method URL for $what is required")
        if (m.length < 8) throw SpecError("URL string too short to be valid.")
        if (!m.startsWith("palm://") && !m.startsWith("luna://")) throw SpecError("URL does not start with \"palm://\" or \"luna://\".")
        val rest = m.substring(7)
        if (!rest.contains('/') || rest.endsWith("/") && rest.indexOf('/') == rest.length - 1) throw SpecError("URL does not specify a method.")
        return JSONObject(o.toString()).put("method", "palm://$rest")
    }

    private fun parseTrigger(t: JSONObject): JSONObject {
        if (!t.has("method")) throw SpecError("Method URL for Trigger is required")
        val out = url(t, "Trigger")
        t.optJSONObject("compare")?.let { if (!it.has("key") || !it.has("value")) throw SpecError("Compare Trigger requires key to compare and value to compare against be specified.") }
        t.opt("where")?.let { validWhere(it) }
        return out
    }

    private fun validWhere(w: Any) {
        when (w) {
            is JSONArray -> for (i in 0 until w.length()) validWhere(w.get(i))
            is JSONObject -> when {
                w.has("and") -> validWhere(w.get("and"))
                w.has("or") -> validWhere(w.get("or"))
                w.has("prop") -> if (w.optString("op") !in WHERE_OPS) throw SpecError("Operation must be one of '<', '<=', '=', '>=', '>', '!=', and 'where'")
                else -> throw SpecError("Each where clause must contain \"or\", \"and\", or a \"prop\"erty to compare against")
            }
            else -> throw SpecError("Each where clause must contain \"or\", \"and\", or a \"prop\"erty to compare against")
        }
    }

    private fun newId(): Int {
        while (activities.containsKey(nextId)) nextId++
        return nextId++
    }

    // ---- subscriptions ----

    private fun subscribe(a: Activity, call: Bus.Call): Sub {
        val s = Sub(call, call.params.optBoolean("detailedEvents"), busId(call))
        a.subs += s
        call.onCancel { main.post { dropped(a, s) } }
        return s
    }

    private fun dropped(a: Activity, s: Sub) {
        if (!a.subs.remove(s)) return
        a.adopters.remove(s)
        if (a.releasedParent === s) a.releasedParent = null
        if (a.parent === s) {
            a.parent = null
            val next = a.adopters.removeFirstOrNull()
            if (next != null) { a.parent = next; event(a, "orphan", listOf(next)) }
            else if (a.subs.isNotEmpty()) orphaned(a)
        }
        if (a.subs.isEmpty() && (a.running || a.ending != null || a.initialized)) {
            // Abandoned: nobody is left to finish it, so it ends (and may be armed again).
            if (a.ending == null && (a.running || a.callback == null)) a.ending = "cancel"
            if (a.ending != null) finishIfDone(a)
        }
    }

    private fun orphaned(a: Activity) {
        when {
            a.running -> command(a, "cancel")
            a.initialized && a.callback != null -> {}
            else -> command(a, "cancel")
        }
    }

    private fun join(call: Bus.Call) = withActivity(call) { a ->
        if (!call.subscribe) return@withActivity call.reply(error(22, "Join method calls must subscribe to the Activity"))
        if (!serialOk(a, call)) return@withActivity
        subscribe(a, call)
        call.reply(Bus.ok())
    }

    private fun monitor(call: Bus.Call) = withActivity(call) { a ->
        if (!serialOk(a, call)) return@withActivity
        if (call.subscribe) subscribe(a, call)
        call.reply(JSONObject().put("state", state(a)).put("returnValue", true).toString())
    }

    private fun adopt(call: Bus.Call) = withActivity(call) { a ->
        if (!call.subscribe) return@withActivity call.reply(error(22, "AdoptActivity requires subscription"))
        if (!serialOk(a, call)) return@withActivity
        val s = subscribe(a, call)
        if (a.parent != null) {
            if (!call.params.optBoolean("wait")) return@withActivity call.reply(error(11, "Resource temporarily unavailable"))
            a.adopters += s
            call.reply(JSONObject().put("adopted", false).put("returnValue", true).toString())
        } else {
            a.parent = s
            event(a, "orphan", listOf(s))
            call.reply(JSONObject().put("adopted", true).put("returnValue", true).toString())
            a.releasedParent?.let { event(a, "adopted", listOf(it)) }
        }
    }

    private fun release(call: Bus.Call) = withActivity(call) { a ->
        val me = a.parent?.takeIf { creatorKey(it.who) == creatorKey(busId(call)) }
        if (a.parent == null) return@withActivity call.reply(error(22, "Invalid argument"))
        if (me == null) return@withActivity call.reply(error(13, "Permission denied"))
        a.releasedParent = me
        a.parent = null
        a.adopters.removeFirstOrNull()?.let { a.parent = it; event(a, "orphan", listOf(it)) }
        call.reply(Bus.ok())
    }

    // ---- commands ----

    private fun external(a: Activity, cmd: String, call: Bus.Call) {
        if (a.ending != null) return call.reply(error(-1000, "The Activity Manager has already received a command to end the Activity"))
        when (cmd) {
            "start" -> if (a.paused) { a.paused = false; event(a, "start"); evaluate(a) } else start(a)
            "pause" -> { a.paused = true; event(a, "pause") }
        }
        call.reply(Bus.ok())
    }

    private fun end(a: Activity, cmd: String, call: Bus.Call) {
        if (a.ending != null) return call.reply(error(-1000, "The Activity Manager has already received a command to end the Activity"))
        a.terminate = true
        byName.remove(a.key)
        val done = { call.reply(Bus.ok()) }
        command(a, cmd)
        a.dbId?.let { dbDel(it, done) } ?: done()
        a.dbId = null
    }

    private fun complete(call: Bus.Call) = withActivity(call) { a ->
        if (a.ending != null) return@withActivity call.reply(error(-1000, "The Activity Manager has already received a command to end the Activity"))
        if (!serialOk(a, call)) return@withActivity
        val caller = creatorKey(busId(call))
        if (!call.params.optBoolean("force") && caller != creatorKey(a.creator) && a.parent?.let { creatorKey(it.who) } != caller)
            return@withActivity call.reply(error(13, "Permission denied"))
        val p = call.params
        if (p.optBoolean("restart")) {
            try { applyUpdates(a, p) } catch (e: SpecError) { return@withActivity call.reply(error(-1000, e.message ?: "")) }
            a.restart = true
            command(a, "complete")
            if (a.persist) persist(a) { call.reply(Bus.ok()) } else call.reply(Bus.ok())
        } else {
            a.terminate = true
            command(a, "complete")
            val id = a.dbId; a.dbId = null
            val done = { byName.remove(a.key); call.reply(Bus.ok()) }
            if (id != null) dbDel(id, done) else done()
        }
    }

    /** complete's changes to a restarting activity, all or none. */
    private fun applyUpdates(a: Activity, p: JSONObject) {
        val trigger = when (val t = p.opt("trigger")) {
            null -> a.trigger
            false -> null
            is JSONObject -> parseTrigger(t)
            else -> throw SpecError("\"trigger\":false to clear, or valid trigger property must be specified")
        }
        val schedule = when (val s = p.opt("schedule")) { null -> a.schedule; false -> null; is JSONObject -> Schedule.parse(s); else -> throw SpecError("Invalid schedule") }
        val callback = when (val c = p.opt("callback")) { null -> a.callback; is JSONObject -> url(c, "Callback"); else -> throw SpecError("Activity callback may not be removed by Complete") }
        val metadata = when (val m = p.opt("metadata")) { null -> a.metadata; false -> null; is JSONObject -> m; else -> throw SpecError("Attempt to set metadata to a non-object type") }
        val reqs = JSONObject(a.requirements.toString())
        p.optJSONObject("requirements")?.let { r -> for (k in r.keys()) { val v = r.get(k); if (v == false || v == JSONObject.NULL) reqs.remove(k) else reqs.put(k, v) } }
        val probe = Activity(0, a.name, a.creator)
        for (k in reqs.keys()) setRequirement(probe, k, reqs.get(k))
        if (schedule != a.schedule) schedule?.lastFinished = a.schedule?.lastFinished
        a.trigger = trigger; a.schedule = schedule; a.callback = callback; a.metadata = metadata; a.requirements = probe.requirements
    }

    /** A command takes effect: its event goes out, and an ending one starts the end. */
    private fun command(a: Activity, cmd: String) {
        event(a, cmd)
        a.ending = cmd
        disarm(a)
        if (a.queued) { a.queued = false; queue.remove(a) }
        finishIfDone(a)
    }

    // ---- running ----

    private fun start(a: Activity) {
        if (!enabled) { if (a !in waitingForEnable) waitingForEnable += a; return }
        if (a.initialized || a.ending != null) return evaluate(a)
        a.initialized = true
        arm(a)
        evaluate(a)
    }

    private fun arm(a: Activity) {
        a.fired = null
        a.trigger?.let { t -> armTrigger(a, t) }
        a.schedule?.let { s -> s.queue(); scheduleTimer(a) }
    }

    private fun disarm(a: Activity) {
        a.triggerCall?.cancel(); a.triggerCall = null
        timers.remove(a.id)?.let { main.removeCallbacks(it) }
    }

    /** Whether the activity may run now, and if so, runs or queues it. */
    private fun evaluate(a: Activity) {
        if (!a.initialized || a.running || a.queued || a.paused || a.ending != null) return
        if (a.trigger != null && a.fired == null) return
        if (a.schedule != null && !a.schedule!!.due) return
        if (!requirementsMet(a)) return
        if (a.immediate) run(a) else { a.queued = true; queue += a; runQueue() }
    }

    private val queue = ArrayList<Activity>()

    /** One background activity at a time, and two that a user started, as webOS's queues ran them. */
    private fun runQueue() {
        while (true) {
            val bg = activities.values.count { it.running && !it.immediate && !it.userInitiated }
            val ui = activities.values.count { it.running && !it.immediate && it.userInitiated }
            val next = queue.sortedByDescending { PRIORITIES.indexOf(it.priority) }
                .firstOrNull { if (it.userInitiated) ui < 2 else bg < 1 } ?: return
            queue.remove(next); next.queued = false
            run(next)
        }
    }

    private fun run(a: Activity) {
        a.running = true
        event(a, "start")
        a.callback?.let { callCallback(a, it) }
    }

    private fun callCallback(a: Activity, cb: JSONObject) {
        a.serial = (Math.random() * 0xffffffffL).toLong()
        val params = JSONObject((cb.optJSONObject("params") ?: JSONObject()).toString()).put("\$activity", info(a))
        bus.call(SERVICE, cb.getString("method"), params.toString(), privateBus = true) { reply ->
            main.post { callbackReplied(a, reply) }
        }
    }

    private fun callbackReplied(a: Activity, reply: String) {
        if (activities[a.id] !== a || !a.running) return
        val r = runCatching { JSONObject(reply) }.getOrNull() ?: JSONObject()
        if (!r.has("returnValue") || r.optBoolean("returnValue")) return
        val text = r.optString("errorText")
        if (text.startsWith("Service exited")) {
            // The service went away mid-call (a crash, or its process ending): run it again
            // once it can, keeping the trigger and schedule, as webOS requeued a transient
            // failure. After a moment, so a service that dies at once doesn't spin.
            Log.w(AppServer.TAG, "activity ${a.id} ${a.name}: callback failed ($text); trying again")
            a.running = false
            main.postDelayed({ if (activities[a.id] === a && a.ending == null) { event(a, "start"); a.running = true; a.callback?.let { callCallback(a, it) } } }, 5000)
            return
        }
        Log.w(AppServer.TAG, "activity ${a.id} ${a.name}: callback failed: $reply")
        a.terminate = true
        command(a, "cancel")
    }

    /** An ending activity is done when nobody is subscribed any more; then it is armed again or forgotten. */
    private fun finishIfDone(a: Activity) {
        if (a.ending == null || a.subs.isNotEmpty() || activities[a.id] !== a) return
        val wasRunning = a.running
        a.running = false
        a.schedule?.let { if (it.interval != null) it.lastFinished = System.currentTimeMillis() }
        val again = a.callback != null && !a.terminate &&
            (a.restart || a.persist || a.explicit || (a.schedule?.let { it.interval != null && !it.past() } == true))
        if (again) {
            a.ending = null; a.restart = false; a.initialized = false; a.paused = false
            a.parent = null; a.releasedParent = null; a.adopters.clear()
            start(a)
        } else {
            disarm(a)
            activities.remove(a.id)
            if (byName[a.key] === a) byName.remove(a.key)
            waitingForEnable.remove(a)
        }
        if (wasRunning) runQueue()
    }

    // ---- triggers ----

    private fun armTrigger(a: Activity, t: JSONObject) {
        // On the creator's behalf, so db8 grants it what it grants the creator.
        val caller = creatorKey(a.creator).ifEmpty { SERVICE }
        var first = true
        a.triggerCall = bus.call(caller, t.getString("method"), (t.optJSONObject("params") ?: JSONObject()).toString(), privateBus = true) { reply ->
            main.post {
                if (a.triggerCall == null || a.fired != null) return@post
                val r = runCatching { JSONObject(reply) }.getOrNull() ?: JSONObject()
                val isBasic = !t.has("key") && !t.has("where") && !t.has("compare")
                val match = when {
                    r.has("returnValue") && !r.optBoolean("returnValue") -> true   // an error fires it, with the error
                    t.has("key") -> r.has(t.getString("key"))
                    t.has("compare") -> t.getJSONObject("compare").let { c -> r.has(c.getString("key")) && !same(r.get(c.getString("key")), c.get("value")) }
                    t.has("where") -> where(r, t.get("where"))
                    isBasic -> !first
                    else -> false
                }
                first = false
                if (!match) return@post
                a.fired = r
                a.triggerCall?.cancel(); a.triggerCall = null
                evaluate(a)
            }
        }
    }

    private fun same(x: Any?, y: Any?) = x == y || (x is Number && y is Number && x.javaClass == y.javaClass && x.toDouble() == y.toDouble())

    private fun where(o: JSONObject, w: Any): Boolean = when (w) {
        is JSONArray -> (0 until w.length()).all { where(o, w.get(it)) }
        is JSONObject -> when {
            w.has("and") -> where(o, w.get("and"))
            w.has("or") -> w.get("or").let { or -> if (or is JSONArray) (0 until or.length()).any { where(o, or.get(it)) } else where(o, or) }
            else -> {
                val v = path(o, w.opt("prop")) ?: NONE
                if (v === NONE) false else if (w.optString("op") == "where") true else compareOp(v, w.optString("op"), w.opt("val"))
            }
        }
        else -> false
    }

    private val NONE = Any()

    private fun path(o: JSONObject, prop: Any?): Any? {
        val parts = when (prop) { is String -> prop.split('.'); is JSONArray -> (0 until prop.length()).map { prop.getString(it) }; else -> return null }
        var cur: Any? = o
        for (p in parts) cur = (cur as? JSONObject)?.opt(p) ?: return null
        return cur
    }

    /** MojObject's order: different types order by type, so int 1 and decimal 1.0 differ. */
    private fun compareOp(a: Any?, op: String, b: Any?): Boolean {
        fun rank(v: Any?) = when (v) { null, JSONObject.NULL -> 1; is JSONObject -> 2; is JSONArray -> 3; is String -> 4; is Boolean -> 5; is Double, is Float -> 6; is Number -> 7; else -> 0 }
        val c = if (rank(a) != rank(b)) rank(a).compareTo(rank(b)) else when (a) {
            is String -> a.compareTo(b as String)
            is Boolean -> a.compareTo(b as Boolean)
            is Number -> a.toDouble().compareTo((b as Number).toDouble())
            else -> if (a.toString() == b.toString()) 0 else 1
        }
        return when (op) { "<" -> c < 0; "<=" -> c <= 0; "=" -> c == 0; "!=" -> c != 0; ">=" -> c >= 0; ">" -> c > 0; else -> false }
    }

    // ---- schedules ----

    private val timers = HashMap<Int, Runnable>()

    private fun scheduleTimer(a: Activity) {
        val s = a.schedule ?: return
        timers.remove(a.id)?.let { main.removeCallbacks(it) }
        val at = s.nextStart ?: return
        val r = Runnable { timers.remove(a.id); s.due = true; evaluate(a) }
        timers[a.id] = r
        main.postDelayed(r, (at - System.currentTimeMillis()).coerceAtLeast(0))
    }

    private class Schedule(val spec: JSONObject) {
        var start: Long? = null; var end: Long? = null; var interval: Long? = null
        var precise = false; var relative = false; var skip = false; var local = false
        var lastFinished: Long? = null
        var nextStart: Long? = null
        var due = false

        fun past() = end?.let { e -> (nextStart ?: System.currentTimeMillis()) >= e } ?: false

        /** Works out the next start and clears due, as each queueing did. */
        fun queue() {
            due = false
            val now = System.currentTimeMillis()
            val iv = interval
            if (iv == null) { nextStart = start; return }
            val base = when {
                relative -> lastFinished ?: start ?: now
                precise -> start ?: now
                else -> SMART_BASE
            }
            var next = if (now <= base) base else base + ((now - base) / iv + 1) * iv
            val missedFrom = lastFinished ?: if (spec.has("start")) start else null
            if (!skip && missedFrom != null && next - missedFrom > iv) next = now
            if (end != null && end!! - next <= 0) { nextStart = null; return }
            nextStart = next
        }

        fun toJson(current: Boolean): JSONObject {
            val o = JSONObject()
            if (precise) o.put("precise", true)
            if (relative) o.put("relative", true)
            if (current) nextStart?.let { o.put("nextStart", format(it, local)) }
            end?.let { o.put("end", format(it, local)) }
            if (skip) o.put("skip", true)
            lastFinished?.let { o.put("lastFinished", format(it, local)) }
            spec.optString("interval").takeIf { it.isNotEmpty() }?.let { o.put("interval", it) }
            if (current) o.put("scheduled", due)
            if (local) o.put("local", true)
            start?.let { o.put("start", format(it, local)) }
            return o
        }

        companion object {
            /** Smart intervals tick on a base drawn once, between 23:00 and 05:00 after the epoch. */
            val SMART_BASE = (23 * 3600 + (Math.random() * 6 * 3600).toLong()) * 1000L
            private val SMART = setOf(5, 10, 15, 20, 30, 60, 180, 360, 720).map { it * 60_000L }.toSet()

            fun parse(s: JSONObject): Schedule {
                val sc = Schedule(s)
                sc.precise = s.optBoolean("precise"); sc.relative = s.optBoolean("relative"); sc.skip = s.optBoolean("skip")
                val startText = s.opt("start") as? String; val endText = s.opt("end") as? String
                val utc = listOfNotNull(startText, endText).map { it.endsWith("Z") }.distinct()
                if (utc.size > 1) throw SpecError("Start and end time must both be specified in UTC or local time")
                sc.local = s.optBoolean("local") || utc.singleOrNull() == false
                sc.start = startText?.let { time(it, sc.local) }
                sc.end = endText?.let { time(it, sc.local) }
                val iv = s.opt("interval") as? String
                if (iv != null) {
                    val ms = duration(iv)
                    if (!sc.precise) {
                        if (ms % 60_000L != 0L) throw SpecError("Only durations of even minutes may be specified")
                        if (ms !in SMART && ms % 86_400_000L != 0L) throw SpecError("Interval must be a number of days<n>d, or one of: 12h, 6h, 3h, 1h, 20m, 30m, 15m, 10m or 5m")
                        if (sc.start != null || sc.end != null) throw SpecError("Unless precise time is specified, time intervals may not specify a start or end time")
                        if (sc.relative) throw SpecError("Relative schedules must be precise")
                    }
                    sc.interval = ms
                } else if (sc.start == null) throw SpecError("Non Interval Schedules must specify a start time")
                (s.opt("lastFinished") as? String)?.let { lf ->
                    val t = time(lf, sc.local)
                    if (t < System.currentTimeMillis() && (sc.start == null || t > sc.start!!)) sc.lastFinished = t
                }
                return sc
            }

            private fun fmt(local: Boolean) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = if (local) TimeZone.getDefault() else TimeZone.getTimeZone("UTC") }
            fun format(t: Long, local: Boolean) = fmt(local).format(t) + if (local) "" else "Z"
            fun time(s: String, local: Boolean): Long {
                val z = s.endsWith("Z")
                if (!z && s.isNotEmpty() && !s.last().isDigit()) throw SpecError("Start time must end in 'Z' for UTC, or nothing")
                return runCatching { fmt(!z && local).parse(s.removeSuffix("Z"))!!.time }.getOrElse { throw SpecError("Failed to parse start time") }
            }
            fun duration(s: String): Long {
                val m = Regex("^(?:(\\d+)D)?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$", RegexOption.IGNORE_CASE).find(s.trim())
                    ?: throw SpecError("Failed to parse scheduling interval")
                val (d, h, mi, se) = m.destructured
                val ms = ((d.toLongOrNull() ?: 0) * 86400 + (h.toLongOrNull() ?: 0) * 3600 + (mi.toLongOrNull() ?: 0) * 60 + (se.toLongOrNull() ?: 0)) * 1000
                if (ms == 0L) throw SpecError("Duration must be non-zero")
                return ms
            }
        }
    }

    // ---- requirements ----

    private var netStatus: JSONObject? = null
    private var batteryPercent = -1
    private var charger: String? = null

    private fun setRequirement(a: Activity, name: String, v: Any) {
        when (name) {
            // false asks for nothing, and is dropped: measured on the reference TouchPad, where
            // the mail services ask {"internet":false} and the activity has no requirements;
            // anything else but true is refused with this text.
            "internet", "wifi", "wan", "charging", "docked", "bootup", "never" -> {
                if (v == false) { a.requirements.remove(name); return }
                if (v != true) throw SpecError("If an '$name' requirement is specified, the only legal value is 'true'")
            }
            "internetConfidence", "wifiConfidence", "wanConfidence" ->
                if (v !is String || v !in CONFIDENCE) throw SpecError("Invalid connection confidence level specified")
            "battery" -> if (v !is Int || v !in 0..100) throw SpecError("A \"battery\" requirement must specify a value between 0 and 100")
            // A wifi TouchPad had no telephony manager to ask.
            else -> throw SpecError("Manager for requirement not found")
        }
        a.requirements.put(name, v)
    }

    private fun confidence(o: JSONObject?): Int {
        if (o == null || o.optString("state") != "connected" || o.optString("onInternet") != "yes") return -1
        return CONFIDENCE.indexOf(o.optString("networkConfidenceLevel"))
    }

    private fun met(name: String, v: Any): Boolean {
        val s = netStatus
        val wifi = s?.optJSONObject("wifi"); val wan = s?.optJSONObject("wan")
        return when (name) {
            "internet" -> s?.optBoolean("isInternetConnectionAvailable") == true
            "wifi" -> wifi?.optString("state") == "connected" && wifi.optString("onInternet") == "yes"
            "wan" -> wan?.optString("state") == "connected" && wan.optString("network") != "unusable" && wan.optString("onInternet") == "yes"
            "internetConfidence" -> maxOf(confidence(wifi), confidence(wan)).let { it >= 0 && it >= CONFIDENCE.indexOf(v) }
            "wifiConfidence" -> confidence(wifi).let { it >= 0 && it >= CONFIDENCE.indexOf(v) }
            "wanConfidence" -> confidence(wan).let { it >= 0 && it >= CONFIDENCE.indexOf(v) }
            "charging" -> charger != null
            "docked" -> charger == "puck"
            "battery" -> batteryPercent >= (v as Int)
            "bootup" -> true
            else -> false
        }
    }

    private fun current(name: String): Any {
        val s = netStatus
        return when (name) {
            "internet" -> s ?: false
            "wifi" -> s?.optJSONObject("wifi") ?: false
            "wan" -> s?.optJSONObject("wan") ?: false
            "internetConfidence" -> maxOf(confidence(s?.optJSONObject("wifi")), confidence(s?.optJSONObject("wan"))).let { if (it < 0) "unknown" else CONFIDENCE[it] }
            "wifiConfidence" -> confidence(s?.optJSONObject("wifi")).let { if (it < 0) "unknown" else CONFIDENCE[it] }
            "wanConfidence" -> confidence(s?.optJSONObject("wan")).let { if (it < 0) "unknown" else CONFIDENCE[it] }
            "battery" -> batteryPercent
            else -> met(name, requirementsValue(name))
        }
    }

    private fun requirementsValue(name: String): Any = true

    private fun requirementsMet(a: Activity) = a.requirements.keys().asSequence().all { met(it, a.requirements.get(it)) }

    /** The connection manager's status, as the device's activity manager subscribed to it. */
    private fun watchNetwork() {
        bus.call(SERVICE, "palm://com.palm.connectionmanager/getstatus", "{\"subscribe\":true}", privateBus = true) { reply ->
            main.post {
                val r = runCatching { JSONObject(reply) }.getOrNull() ?: return@post
                val before = activities.values.associateWith { requirementsMet(it) }
                netStatus = r
                requirementsChanged(before)
            }
        }
    }

    private fun watchBattery() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val before = activities.values.associateWith { requirementsMet(it) }
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                batteryPercent = if (level < 0 || scale <= 0) -1 else level * 100 / scale
                charger = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                    BatteryManager.BATTERY_PLUGGED_USB -> "usb"; BatteryManager.BATTERY_PLUGGED_WIRELESS -> "inductive"
                    BatteryManager.BATTERY_PLUGGED_AC -> "usb"; else -> null
                }
                requirementsChanged(before)
            }
        }
        context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun requirementsChanged(before: Map<Activity, Boolean>) {
        for (a in activities.values.toList()) {
            if (a.requirements.length() == 0) continue
            update(a)
            if (before[a] == false && requirementsMet(a)) evaluate(a)
        }
    }

    // ---- focus ----

    private fun focus(call: Bus.Call) = withActivity(call) { a ->
        if (!a.focused) {
            activities.values.filter { it.focused }.forEach { it.focused = false; event(it, "unfocused") }
            a.focused = true; event(a, "focused")
        }
        call.reply(Bus.ok())
    }

    private fun unfocus(call: Bus.Call) = withActivity(call) { a ->
        if (!a.focused) return@withActivity call.reply(error(22, "Failed to unfocus Activity"))
        a.focused = false; event(a, "unfocused")
        call.reply(Bus.ok())
    }

    private fun addFocus(call: Bus.Call) = withActivity(call) { a ->
        val t = call.params.opt("targetActivityId") as? Number ?: return@withActivity call.reply(error(22, "'targetActivityId' must be specified"))
        val target = activities[t.toInt()] ?: return@withActivity call.reply(error(2, "Target Activity not found"))
        if (!a.focused) return@withActivity call.reply(error(22, "Failed to add focus"))
        if (!target.focused) { target.focused = true; event(target, "focused") }
        call.reply(Bus.ok())
    }

    // ---- events and reports ----

    private fun event(a: Activity, name: String, to: List<Sub> = a.subs.toList()) {
        for (s in to) {
            val o = JSONObject().put("activityId", a.id).put("event", name).put("returnValue", true)
            if (s.detailed) o.put("\$activity", info(a))
            s.call.reply(o.toString())
        }
    }

    /** "update", to detailed subscribers only. */
    private fun update(a: Activity) = event(a, "update", a.subs.filter { it.detailed })

    /** "$activity", as a callback and a detailed event carry it. */
    private fun info(a: Activity): JSONObject {
        val o = JSONObject().put("activityId", a.id)
        if (a.callback != null) o.put("callback", JSONObject().put("serial", a.serial))
        if (a.trigger != null) o.put("trigger", a.fired ?: false)
        if (a.requirements.length() > 0) o.put("requirements", JSONObject().also { r -> for (k in a.requirements.keys()) r.put(k, current(k)) })
        o.put("creator", a.creator).put("name", a.name)
        a.metadata?.let { o.put("metadata", it) }
        return o
    }

    private fun state(a: Activity): String = when {
        a.ending != null -> when (a.ending) {
            "cancel" -> if (a.subs.isEmpty()) "cancelled" else "cancelling"
            "stop" -> if (a.subs.isEmpty()) "stopped" else "stopping"
            else -> "complete"
        }
        a.paused -> "paused"
        a.running -> "running"
        a.queued -> "queued"
        !a.initialized -> "init"
        a.requirements.length() > 0 && !requirementsMet(a) && (a.trigger == null || a.fired != null) && (a.schedule == null || a.schedule!!.due) -> "blocked"
        else -> "waiting"
    }

    private fun toJson(a: Activity, detail: Boolean, subscribers: Boolean, current: Boolean, persisting: Boolean = false): JSONObject {
        val o = JSONObject().put("activityId", a.id).put("name", a.name).put("description", a.description)
        a.metadata?.let { o.put("metadata", it) }
        o.put("creator", a.creator)
        if (!persisting) o.put("focused", a.focused).put("state", state(a))
        if (subscribers) {
            a.parent?.let { o.put("parent", it.who) }
            o.put("subscribers", JSONArray(a.subs.map { it.who }))
            o.put("adopters", JSONArray(a.adopters.map { it.who }))
        }
        if (detail) {
            val t = JSONObject().put("bus", a.busType)
            if (a.persist) t.put("persist", true); if (a.explicit) t.put("explicit", true)
            if (a.continuous) t.put("continuous", true); if (a.userInitiated) t.put("userInitiated", true)
            if (a.power) t.put("power", true); if (a.powerDebounce) t.put("powerDebounce", true)
            when (a.simpleType) { "foreground" -> t.put("foreground", true); "background" -> t.put("background", true); else -> t.put("immediate", a.immediate).put("priority", a.priority) }
            o.put("type", t)
            a.callback?.let { o.put("callback", if (current) (if (a.serial != 0L) JSONObject().put("serial", a.serial) else JSONObject()) else it) }
            a.schedule?.let { o.put("schedule", it.toJson(current)) }
            a.trigger?.let { o.put("trigger", if (current) (a.fired ?: false) else it) }
            if (a.requirements.length() > 0) o.put("requirements", if (current) JSONObject().also { r -> for (k in a.requirements.keys()) r.put(k, current(k)) } else a.requirements)
        }
        return o
    }

    private fun list(call: Bus.Call) {
        val p = call.params
        val out = JSONArray()
        for (a in activities.values) out.put(toJson(a, p.optBoolean("details"), p.optBoolean("subscribers"), p.optBoolean("current")))
        call.reply(JSONObject().put("activities", out).put("returnValue", true).toString())
    }

    private fun getDetails(call: Bus.Call) = withActivity(call) { a ->
        call.reply(JSONObject().put("activity", toJson(a, true, true, call.params.optBoolean("current"))).put("returnValue", true).toString())
    }

    // ---- persistence ----

    private fun persist(a: Activity, done: () -> Unit = {}) {
        val rep = toJson(a, detail = true, subscribers = false, current = false, persisting = true).put("_kind", KIND)
        a.dbId?.let { rep.put("_id", it) }
        a.dbRev?.let { rep.put("_rev", it) }
        bus.call(SERVICE, "palm://com.palm.db/put", JSONObject().put("objects", JSONArray().put(rep)).toString(), privateBus = true) { reply ->
            main.post {
                val r = runCatching { JSONObject(reply) }.getOrNull()
                val res = r?.optJSONArray("results")?.optJSONObject(0)
                if (res != null) { a.dbId = res.optString("id"); a.dbRev = res.optLong("rev") }
                else Log.w(AppServer.TAG, "activity ${a.id} ${a.name}: not persisted: $reply")
                done()
            }
        }
    }

    private fun dbDel(id: String, done: () -> Unit = {}) {
        bus.call(SERVICE, "palm://com.palm.db/del", JSONObject().put("ids", JSONArray().put(id)).toString(), privateBus = true) { main.post(done) }
    }

    /**
     * Lets activities run: called once the webOS root's services are on the bus. Each
     * restored activity is started, so its trigger and schedule are armed again; with no
     * subscribers, its callback is how its service hears of it.
     */
    fun enable() {
        if (enabled) return
        enabled = true
        Log.i(AppServer.TAG, "activities: running")
        waitingForEnable.toList().also { waitingForEnable.clear() }.forEach { if (activities[it.id] === it) start(it) }
    }

    /** Reads the persisted activities back, with their ids, then answers the calls held meanwhile. */
    private fun load() {
        val found = ArrayList<JSONObject>()
        fun page(token: String?) {
            val q = JSONObject().put("from", KIND)
            token?.let { q.put("page", it) }
            bus.call(SERVICE, "palm://com.palm.db/find", JSONObject().put("query", q).toString(), privateBus = true) { reply ->
                main.post {
                    val r = runCatching { JSONObject(reply) }.getOrNull()
                    r?.optJSONArray("results")?.let { a -> for (i in 0 until a.length()) found += a.getJSONObject(i) }
                    val next = r?.optString("next")?.takeIf { it.isNotEmpty() }
                    if (next != null) page(next) else restored(found, r)
                }
            }
        }
        page(null)
    }

    private fun restored(found: List<JSONObject>, last: JSONObject?) {
        if (last != null && !last.optBoolean("returnValue", false)) Log.w(AppServer.TAG, "activities: can't read the persisted ones: $last")
        var n = 0
        for (rec in found.sortedByDescending { it.optLong("_rev") }) {
            val a = try { parse(rec, rec.optJSONObject("creator") ?: JSONObject()) } catch (e: SpecError) { null }
            val id = rec.optInt("activityId", 0)
            if (a == null || id <= 0 || activities.containsKey(id) || byName.containsKey(a.key)) { dbDel(rec.optString("_id")); continue }
            a.id = id
            a.persist = true
            a.dbId = rec.optString("_id"); a.dbRev = rec.optLong("_rev")
            rec.optJSONObject("schedule")?.optString("lastFinished")?.takeIf { it.isNotEmpty() }?.let { lf ->
                runCatching { a.schedule?.lastFinished = Schedule.time(lf, a.schedule?.local == true) }
            }
            activities[id] = a; byName[a.key] = a
            waitingForEnable += a
            n++
        }
        loaded = true
        Log.i(AppServer.TAG, "activities: $n restored")
        held.toList().also { held.clear() }.forEach { it() }
    }

    private fun error(code: Int, text: String) =
        JSONObject().put("returnValue", false).put("errorCode", code).put("errorText", text).toString()

    companion object {
        const val SERVICE = "com.palm.activitymanager"
        private const val KIND = "com.palm.activity:1"
        private val PRIORITIES = listOf("none", "lowest", "low", "normal", "high", "highest")
        private val CONFIDENCE = listOf("none", "poor", "fair", "excellent")
        private val WHERE_OPS = setOf("<", "<=", "=", "!=", ">=", ">", "where")
    }
}
