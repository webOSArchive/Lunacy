package org.webosarchive.lunacy.card

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

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
 * Asleep, too, as webOS's was: a schedule falls due on the wall clock and wakes the device for
 * it (an RTC alarm, as the activity manager asked powerd for one), and a "power" activity
 * keeps the CPU up while it runs (powerd's power activity). See [Power].
 *
 * Main thread throughout.
 */
class ActivityManager(private val context: Context, private val power: Power) {
    private lateinit var bus: Bus
    private val main = Handler(Looper.getMainLooper())
    /**
     * The network and the battery, as requirements see them. A change re-evaluates every activity
     * that has requirements, and starts those it has just met.
     */
    private val requirements: ActivityRequirements = ActivityRequirements(context, SERVICE) { change ->
        val before = activities.values.associateWith { requirementsMet(it) }
        change()
        requirementsChanged(before)
    }

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
        /** Set while Android had Lunacy's network blocked, so the block's end brings it due ([networkReturned]). */
        var setWhileBlocked = false
        var focused = false
        var triggerCall: Bus.Call? = null
        /** The reply that fired the trigger, while it has. */
        var fired: JSONObject? = null
        var serial = 0L
        /** Its record in db8, which the activity that replaces it takes over. */
        var record = DbRecord()
        var dbId: String?
            get() = record.id
            set(v) { record.id = v }
        var dbRev: Long?
            get() = record.rev
            set(v) { record.rev = v }

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
    /**
     * Activities run only while both of webOS's switches are on (ActivityManager::IsEnabled:
     * "all bits must be enabled"): [started], Lunacy's own, once the webOS root is up (webOS's
     * UI_ENABLE), and [external], which a system caller turns off and on with `disable` and
     * `enable` (EXTERNAL_ENABLE, on from the start). Off, a new activity waits; one already
     * running carries on.
     */
    private var started = false
    private var external = true
    private val enabled get() = started && external
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
            "enable" to { c -> switchExternal(c, true) }, "disable" to { c -> switchExternal(c, false) },
        )) bus.register(SERVICE, m, Bus.CallHandler { c -> if (loaded) h(c) else held += { h(c) } })
        for (m in listOf("associateApp", "associateService", "associateProcess", "associateNetworkFlow",
                "dissociateApp", "dissociateService", "dissociateProcess", "dissociateNetworkFlow", "unmapProcess"))
            bus.register(SERVICE, m, Bus.CallHandler { it.reply(Bus.ok()) })
        requirements.watch(bus)
        power.onDue { wakeup() }
        load()
    }

    // ---- identity and lookup ----

    /** The caller as webOS's BusId: a service by its name, an app by its id (taken from the bus, not the payload). */
    private fun busId(call: Bus.Call): JSONObject =
        if (bus.has(call.appId)) JSONObject().put("serviceId", call.appId) else JSONObject().put("appId", call.app)

    private fun creatorKey(o: JSONObject) = o.optString("serviceId").ifEmpty { o.optString("appId").ifEmpty { o.optString("anonId") } }

    /** The activity a call names by id or by name, or null once the call has been answered with why not. */
    private fun activityOrReplyError(call: Bus.Call): Activity? {
        val p = call.params
        fun fail(text: String): Activity? { call.reply(Bus.error(text, 2)); return null }
        if (p.has("activityId")) {
            val id = p.opt("activityId") as? Number ?: return fail("Error retrieving activityId of Activity to operate on")
            return activities[id.toInt()] ?: fail("activityId not found")
        }
        if (p.has("activityName")) {
            val name = p.opt("activityName") as? String ?: return fail("Error retrieving activityName of Activity to operate on")
            return byName["$name\u0000${creatorKey(busId(call))}"] ?: fail("Activity name/creator pair not found")
        }
        return fail("Activity ID or name not present in request")
    }

    private inline fun withActivity(call: Bus.Call, f: (Activity) -> Unit) { activityOrReplyError(call)?.let(f) }

    private fun serialOk(a: Activity, call: Bus.Call): Boolean {
        if (!call.params.has("serial")) return true
        if (a.callback == null) { call.reply(Bus.error("Attempt to use callback sequence serial number match on Activity that does not have a callback", -1000)); return false }
        if (call.params.optLong("serial") != a.serial) { call.reply(Bus.error("Call sequence serial numbers do not match", -1000)); return false }
        return true
    }

    // ---- create ----

    private fun create(call: Bus.Call) {
        val p = call.params
        val spec = p.optJSONObject("activity") ?: return call.reply(Bus.error("Activity specification not present", 22))
        val a = try { parse(spec, busId(call)) } catch (e: SpecError) { return call.reply(Bus.error(e.message ?: "", 12)) }
        if (!p.optBoolean("subscribe") && !(p.optBoolean("start") && a.callback != null))
            return call.reply(Bus.error("Created Activity must specify \"start\" and a Callback if not subscribed", 22))
        val old = byName[a.key]
        if (old != null && !p.optBoolean("replace")) return call.reply(Bus.error("Activity with that name already exists", 17))
        a.id = newId()
        if (old != null) {
            // The new one takes the old one's place in db8; the old one is cancelled.
            // The record itself, not its id and revision as they stand: a save of the old one may
            // still be on its way, and the revision it brings back is the one the next save needs.
            if (a.persist) a.record = old.record else old.dbId?.let { dbDel(it) }
            old.record = DbRecord()
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
        // Posted, even on the main thread: a call cancelled already runs this at once, and
        // create hasn't made the subscription the activity's parent yet - dropped then would
        // leave it parented by a subscription that is gone, never to be cancelled.
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
        if (!call.subscribe) return@withActivity call.reply(Bus.error("Join method calls must subscribe to the Activity", 22))
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
        if (!call.subscribe) return@withActivity call.reply(Bus.error("AdoptActivity requires subscription", 22))
        if (!serialOk(a, call)) return@withActivity
        val s = subscribe(a, call)
        if (a.parent != null) {
            if (!call.params.optBoolean("wait")) return@withActivity call.reply(Bus.error("Resource temporarily unavailable", 11))
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
        if (a.parent == null) return@withActivity call.reply(Bus.error("Invalid argument", 22))
        if (me == null) return@withActivity call.reply(Bus.error("Permission denied", 13))
        a.releasedParent = me
        a.parent = null
        a.adopters.removeFirstOrNull()?.let { a.parent = it; event(a, "orphan", listOf(it)) }
        call.reply(Bus.ok())
    }

    // ---- commands ----

    private fun external(a: Activity, cmd: String, call: Bus.Call) {
        if (a.ending != null) return call.reply(Bus.error("The Activity Manager has already received a command to end the Activity", -1000))
        when (cmd) {
            "start" -> if (a.paused) { a.paused = false; event(a, "start"); evaluate(a) } else start(a)
            "pause" -> { a.paused = true; event(a, "pause") }
        }
        call.reply(Bus.ok())
    }

    private fun end(a: Activity, cmd: String, call: Bus.Call) {
        if (a.ending != null) return call.reply(Bus.error("The Activity Manager has already received a command to end the Activity", -1000))
        a.terminate = true
        byName.remove(a.key)
        val done = { call.reply(Bus.ok()) }
        command(a, cmd)
        a.dbId?.let { dbDel(it, done) } ?: done()
        a.dbId = null
    }

    private fun complete(call: Bus.Call) = withActivity(call) { a ->
        if (a.ending != null) return@withActivity call.reply(Bus.error("The Activity Manager has already received a command to end the Activity", -1000))
        if (!serialOk(a, call)) return@withActivity
        val caller = creatorKey(busId(call))
        if (!call.params.optBoolean("force") && caller != creatorKey(a.creator) && a.parent?.let { creatorKey(it.who) } != caller)
            return@withActivity call.reply(Bus.error("Permission denied", 13))
        val p = call.params
        if (p.optBoolean("restart")) {
            try { applyUpdates(a, p) } catch (e: SpecError) { return@withActivity call.reply(Bus.error(e.message ?: "", -1000)) }
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
        if (due.remove(a.id) != null) power.at(TIMER, due.values.minOrNull())
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
        // Locked before it is told to start, as webOS took powerd's lock first.
        if (a.power) power.lock(a.id)
        event(a, "start")
        a.callback?.let { callCallback(a, it) }
    }

    private fun callCallback(a: Activity, cb: JSONObject) {
        a.serial = (Math.random() * SERIAL_RANGE).toLong()
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
        if (text.startsWith(ServiceProcess.EXITED)) {
            // The service went away mid-call (a crash, or its process ending): run it again
            // once it can, keeping the trigger and schedule, as webOS requeued a transient
            // failure. After a moment, so a service that dies at once doesn't spin.
            Log.w(AppServer.TAG, "activity ${a.id} ${a.name}: callback failed ($text); trying again")
            a.running = false
            main.postDelayed({ if (activities[a.id] === a && a.ending == null) { event(a, "start"); a.running = true; a.callback?.let { callCallback(a, it) } } }, RETRY_MS)
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
        power.unlock(a.id, a.powerDebounce)
        a.schedule?.let { if (it.interval != null) it.lastFinished = System.currentTimeMillis() }
        // A bootup requirement is met once a boot (Open webOS's SystemManagerProxy trips each
        // one when boot finishes, and never again): an activity that needed it and has run is
        // over until the next start. Measured on the reference TouchPad: Calendar's
        // calendar.startreminders and calendar.firstrun, persistent, are neither listed nor in
        // db8 once they have run; the configurator creates them again at the next boot.
        val again = a.callback != null && !a.terminate && !a.requirements.has("bootup") &&
            (a.restart || a.persist || a.explicit || (a.schedule?.let { it.interval != null && !it.past() } == true))
        if (again) {
            a.ending = null; a.restart = false; a.initialized = false; a.paused = false
            a.parent = null; a.releasedParent = null; a.adopters.clear()
            start(a)
        } else {
            disarm(a)
            if (a.requirements.has("bootup")) { a.dbId?.let { dbDel(it) }; a.dbId = null }
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
                val match = TriggerMatch.matches(t, r, first)
                first = false
                if (!match) return@post
                a.fired = r
                a.triggerCall?.cancel(); a.triggerCall = null
                evaluate(a)
            }
        }
    }

    // ---- schedules ----

    /** When each armed schedule falls due, wall-clock time, by activity id. */
    private val due = HashMap<Int, Long>()

    /**
     * Arms a schedule on the wall clock. webOS's activity manager kept one wakeup with powerd
     * (PowerdScheduler: palm://com.palm.power/timeout/set, "wakeup":true) for the earliest
     * schedule, so a sleeping device woke for its next sync. A Handler's delay counts only
     * time awake, and a mail check every 15 minutes became one every 15 minutes of use.
     */
    private fun scheduleTimer(a: Activity) {
        val s = a.schedule ?: return
        due.remove(a.id)
        a.setWhileBlocked = s.interval == null && needsNetwork(a) &&
            (blockedSince != null || System.currentTimeMillis() - lastBlocked < BLOCK_LEAD_MS)
        val at = s.nextStart
        if (at != null) due[a.id] = at
        if (at != null && at <= System.currentTimeMillis()) main.post { wakeup() }
        else power.at(TIMER, due.values.minOrNull())
    }

    // ---- Android's network blocks (SleepWake) ----

    /** When the current block began, if Lunacy's network is blocked now. */
    private var blockedSince: Long? = null
    private var lastBlocked = 0L

    private fun needsNetwork(a: Activity) = NETWORK_REQUIREMENTS.any { a.requirements.has(it) }

    /**
     * Android has blocked Lunacy's network (Doze, or a background block), or given it back.
     *
     * A block cuts every connection, and a service takes the cut for a server or network
     * failure and sets a retry for a few minutes on: mojomail's 5 minutes after a dropped IDLE,
     * Synergy's "5m" after a network error. A TouchPad never saw that cut - its connections
     * lived through a suspend - so those retries are Android's doing, and they wait out their
     * minutes after the network is back, push mail with them (measured: 3 min 18 s after a
     * short Doze, Galaxy Tab A7 Lite). So a one-shot schedule that needs the network and was
     * set during a block, or in the moments before Android reported it (the cut arrives first),
     * falls due as the block ends (luna-deltas A18). Intervals keep their own times.
     */
    fun networkBlocked(blocked: Boolean) {
        val now = System.currentTimeMillis()
        if (blocked) {
            blockedSince = now; lastBlocked = now
            // Retries already set just before the report belong to the block too.
            activities.values.filter { it.schedule?.interval == null && needsNetwork(it) && due.containsKey(it.id) &&
                (it.schedule?.setAt ?: 0L) >= now - BLOCK_LEAD_MS }.forEach { it.setWhileBlocked = true }
            return
        }
        blockedSince = null; lastBlocked = now
        for (a in activities.values.toList()) {
            if (!a.setWhileBlocked) continue
            a.setWhileBlocked = false
            if (due.remove(a.id) == null) continue
            Log.i(AppServer.TAG, "activity ${a.id} ${a.name}: set during a network block; due now")
            a.schedule?.due = true
            evaluate(a)
        }
        power.at(TIMER, due.values.minOrNull())
    }

    /**
     * Everything that has fallen due runs, and the next one is armed: when the wakeup goes off,
     * and whenever the device comes back from sleep (a schedule that fell due meanwhile runs
     * now, once, by the rules in [Schedule.queue]).
     */
    private fun wakeup() {
        val now = System.currentTimeMillis()
        for ((id, at) in due.toList()) {
            if (at > now) continue
            due.remove(id)
            activities[id]?.let { a -> a.schedule?.due = true; evaluate(a) }
        }
        val next = due.values.minOrNull()
        if (next != null && next - now <= Power.EARLY_MS) power.soon(next)
        power.at(TIMER, next)
    }

    // ---- requirements ----

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
                if (v !is String || v !in ActivityRequirements.CONFIDENCE) throw SpecError("Invalid connection confidence level specified")
            "battery" -> if (v !is Int || v !in 0..100) throw SpecError("A \"battery\" requirement must specify a value between 0 and 100")
            // A wifi TouchPad had no telephony manager to ask.
            else -> throw SpecError("Manager for requirement not found")
        }
        a.requirements.put(name, v)
    }

    private fun requirementsMet(a: Activity): Boolean = a.requirements.keys().asSequence().all { requirements.met(it, a.requirements.get(it)) }

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
        if (!a.focused) return@withActivity call.reply(Bus.error("Failed to unfocus Activity", 22))
        a.focused = false; event(a, "unfocused")
        call.reply(Bus.ok())
    }

    private fun addFocus(call: Bus.Call) = withActivity(call) { a ->
        val t = call.params.opt("targetActivityId") as? Number ?: return@withActivity call.reply(Bus.error("'targetActivityId' must be specified", 22))
        val target = activities[t.toInt()] ?: return@withActivity call.reply(Bus.error("Target Activity not found", 2))
        if (!a.focused) return@withActivity call.reply(Bus.error("Failed to add focus", 22))
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
        if (a.requirements.length() > 0) o.put("requirements", JSONObject().also { r -> for (k in a.requirements.keys()) r.put(k, requirements.current(k)) })
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
            if (a.requirements.length() > 0) o.put("requirements", if (current) JSONObject().also { r -> for (k in a.requirements.keys()) r.put(k, requirements.current(k)) } else a.requirements)
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

    /**
     * Saves the activity to db8. Saves to one record go one at a time: each sends the revision
     * the last brought back, so a save made while another is on its way waits for it, then
     * sends the activity as it is then. Two at once - an activity created twice in quick
     * succession, the second replacing the first, as the configurator's two runs at start do -
     * sent the same revision, and db8 refused the second ("revision mismatch"), after which
     * every save of that record failed.
     */
    private fun persist(a: Activity, done: () -> Unit = {}) = persistTo(a.record, a, done)

    private fun persistTo(record: DbRecord, a: Activity, done: () -> Unit) {
        // A save waits on the record it was made for, whatever happens to the activity meanwhile.
        if (record.saving) { record.waiting += { persistTo(record, a, done) }; return }
        // Replaced while it waited (create gave the record to its successor and it a new one):
        // the successor saves the record; this one is answered, and the queue goes on.
        if (a.record !== record) { done(); record.waiting.removeFirstOrNull()?.invoke(); return }
        record.saving = true
        val rep = toJson(a, detail = true, subscribers = false, current = false, persisting = true).put("_kind", KIND)
        record.id?.let { rep.put("_id", it) }
        record.rev?.let { rep.put("_rev", it) }
        bus.call(SERVICE, "palm://com.palm.db/put", JSONObject().put("objects", JSONArray().put(rep)).toString(), privateBus = true) { reply ->
            main.post {
                val r = runCatching { JSONObject(reply) }.getOrNull()
                val res = r?.optJSONArray("results")?.optJSONObject(0)
                if (res != null) { record.id = res.optString("id"); record.rev = res.optLong("rev") }
                else Log.w(AppServer.TAG, "activity ${a.id} ${a.name}: not persisted: $reply")
                record.saving = false
                done()
                record.waiting.removeFirstOrNull()?.invoke()
            }
        }
    }

    /** An activity's record in db8: its id and revision, and the saves waiting their turn. */
    private class DbRecord {
        var id: String? = null
        var rev: Long? = null
        var saving = false
        val waiting = ArrayDeque<() -> Unit>()
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
        if (started) return
        started = true
        Log.i(AppServer.TAG, "activities: running")
        startWaiting()
    }

    private fun startWaiting() {
        if (enabled) waitingForEnable.toList().also { waitingForEnable.clear() }.forEach { if (activities[it.id] === it) start(it) }
    }

    /**
     * `enable` and `disable`: the external switch, private callers only, with webOS's refusal
     * for others (Open webOS ActivityCategoryHandler::Enable, `MojErrAccessDenied`); measured
     * on the reference TouchPad, both answer `{"returnValue":true}` to `{}`.
     */
    private fun switchExternal(c: Bus.Call, on: Boolean) {
        if (!c.privateBus) return c.reply(Bus.error("Only callers on the private bus are allowed to enable or disable Activity dispatch", 13))
        if (external != on) {
            external = on
            Log.i(AppServer.TAG, "activities: ${if (on) "dispatch enabled" else "dispatch disabled"} by ${c.appId}")
            startWaiting()
        }
        c.reply(Bus.ok())
    }

    /**
     * The shell is gone, and with it this activity manager: nothing here runs again. The next
     * shell's reads the persisted activities back. Left running, each old one fired every
     * schedule on its own bus beside the new one's.
     */
    fun stop() {
        started = false
        requirements.stop()
        main.removeCallbacksAndMessages(null)
        for (a in activities.values) { a.triggerCall?.cancel(); a.triggerCall = null }
        activities.clear(); byName.clear(); queue.clear(); due.clear(); waitingForEnable.clear()
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
        // The webOS root may have come up first (db8 answers late when Lunacy starts busy):
        // then nothing will call enable again, and what was restored has to start now.
        startWaiting()
    }

    companion object {
        /** How long a callback whose service went away waits before it is tried again. */
        private const val RETRY_MS = 5000L
        /** A callback's serial, which a reply must quote: a random 32-bit number, as webOS's. */
        private const val SERIAL_RANGE = 0xffffffffL
        const val SERVICE = "com.palm.activitymanager"
        /** Its timer with [Power], under the key it had with powerd. */
        private const val TIMER = "com.palm.activitymanager.wakeup"
        private val NETWORK_REQUIREMENTS = listOf("internet", "internetConfidence", "wifi", "wifiConfidence", "wan", "wanConfidence")
        /** How long before Android reports a block a schedule still counts as set by it. */
        private const val BLOCK_LEAD_MS = 10_000L
        private const val KIND = "com.palm.activity:1"
        private val PRIORITIES = listOf("none", "lowest", "low", "normal", "high", "highest")
        private val WHERE_OPS = setOf("<", "<=", "=", "!=", ">=", ">", "where")
    }
}
