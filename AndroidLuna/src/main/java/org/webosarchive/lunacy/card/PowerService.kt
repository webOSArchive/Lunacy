package org.webosarchive.lunacy.card

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * palm://com.palm.power, powerd's bus service: its keyed timers and its power activities.
 *
 * **Power activities** (com/palm/power/activityStart and activityEnd) are an app's own hold on
 * the device: awake for duration_ms under an id, or until the id is ended. Measured on the
 * reference TouchPad 2026-10-08: activityStart needs both "id" and "duration_ms", else
 * {"returnValue":false,"errorText":"Malformed json."}; starting an id again replaces its
 * duration; activityEnd needs "id" and answers {"returnValue":true} whether or not it was
 * running. Ids are powerd's, not per caller (it logged them bare). SimpleChat, IAmA reddit and
 * drPodder hold the device this way while they fetch. Held with [Power.lockFor].
 *
 * **Timeouts** (timeout/set and clear) are powerd's keyed timers, webOS's alarm API for
 * apps and services that don't use the activity manager - Mojo apps' refresh alarms
 * (SimpleChat's half-hourly one) and Palm Clock's older scheduler (powerdmanager.js, key
 * "clockAlarm"). When one fires, powerd calls its uri with its params.
 *
 * The contract, measured on the reference TouchPad (webOS CE 3.1.0) 2026-10-08:
 * - set needs key, uri, params and one of "in" ("HH:MM:SS", under 24 hours) or "at"
 *   ("MM/DD/YYYY HH:MM:SS", UTC); anything missing, another time format or an unknown
 *   parameter is {"returnValue":false,"errorText":"Invalid format for 'timeout/set'."}.
 *   "params" may be any JSON value. The reply is {"returnValue":true,"key":<key>}.
 * - keep_existing:true leaves a timeout already set under that key alone, and says so:
 *   {"returnValue":true,"key":<key>,"kept_existing":true}. Otherwise set replaces it.
 * - A time already past fires at once (an "at" in 2020 fired in the same second).
 * - On firing, powerd held the device awake 5 s ("com.palm.power.timeout_fired").
 * - clear answers {"returnValue":true,"key":<key>}, whether or not there was one; with no key,
 *   {"returnValue":false,"errorText":"Invalid parameters."}.
 * - powerd kept its timeouts in a database, so they outlived a restart; so do these.
 *
 * Keys are the caller's own: powerd logged each as (app id, key), and the caller here is the
 * origin's (rule 10), never a parameter. "wakeup":false timeouts don't wake the device; they
 * fire when it next is awake. See Docs/sleep-and-wake.md.
 *
 * Main thread throughout.
 */
class PowerService(context: Context, private val power: Power) {
    private lateinit var bus: Bus
    private val prefs = context.getSharedPreferences("power-timeouts", Context.MODE_PRIVATE)

    private class Timeout(val owner: String, val key: String, val at: Long, val wake: Boolean,
                          val uri: String, val params: Any, val holdMs: Long) {
        fun json(): String = JSONObject().put("owner", owner).put("key", key).put("at", at).put("wake", wake)
            .put("uri", uri).put("params", params).put("holdMs", holdMs).toString()
    }

    private val timeouts = HashMap<String, Timeout>()

    fun register(bus: Bus) {
        this.bus = bus
        for ((id, v) in prefs.all) {
            val o = runCatching { JSONObject(v as String) }.getOrNull() ?: continue
            timeouts[id] = Timeout(o.getString("owner"), o.getString("key"), o.getLong("at"), o.getBoolean("wake"),
                o.getString("uri"), o.get("params"), o.optLong("holdMs", Power.GRACE_MS))
        }
        bus.register(SERVICE, "timeout/set", Bus.CallHandler { set(it) })
        bus.register(SERVICE, "timeout/clear", Bus.CallHandler { clear(it) })
        bus.register(SERVICE, "com/palm/power/activityStart", Bus.CallHandler { call ->
            val id = call.params.opt("id") as? String
            val ms = call.params.opt("duration_ms") as? Number
            if (id.isNullOrEmpty() || ms == null || ms.toLong() < 0) return@CallHandler call.reply(failure("Malformed json."))
            power.lockFor("activity:$id", ms.toLong())
            call.reply(Bus.ok())
        })
        bus.register(SERVICE, "com/palm/power/activityEnd", Bus.CallHandler { call ->
            val id = call.params.opt("id") as? String
            if (id.isNullOrEmpty()) return@CallHandler call.reply(failure("Malformed json."))
            power.unlock("activity:$id")
            call.reply(Bus.ok())
        })
        power.onDue { due() }
        arm()
    }

    private var enabled = false

    /**
     * Lets timeouts fire: called once the webOS root's services are on the bus, as the
     * activity manager waits ([ActivityManager.enable]). One that fell due while Lunacy wasn't
     * running fires now, late, as powerd's database kept them through a restart.
     */
    fun enable() { enabled = true; due() }

    private fun set(call: Bus.Call) {
        val p = call.params
        val key = p.opt("key") as? String
        val uri = p.opt("uri") as? String
        val inText = p.opt("in") as? String
        val atText = p.opt("at") as? String
        val now = System.currentTimeMillis()
        val at = when {
            inText != null && atText == null -> IN.matchEntire(inText)?.destructured
                ?.let { (h, m, s) -> if (h.toInt() < 24) now + (h.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1000 else null }
            atText != null && inText == null -> runCatching { atFormat().parse(atText)!!.time }.getOrNull()
            else -> null
        }
        // An activity's own lock length comes with the activity it belongs to.
        val duration = p.opt("activity_duration_ms") as? Number
        val validKeys = p.keys().asSequence().all { it in PARAMS } && (duration == null || p.has("activity_id"))
        if (key.isNullOrEmpty() || uri.isNullOrEmpty() || !p.has("params") || at == null || !validKeys ||
            (p.has("wakeup") && p.opt("wakeup") !is Boolean) || (p.has("keep_existing") && p.opt("keep_existing") !is Boolean))
            return call.reply(failure("Invalid format for 'timeout/set'."))
        val id = "${call.appId}\u0000$key"
        if (p.optBoolean("keep_existing") && timeouts.containsKey(id))
            return call.reply(JSONObject().put("returnValue", true).put("key", key).put("kept_existing", true).toString())
        val t = Timeout(call.appId, key, at, p.optBoolean("wakeup"), uri, p.get("params"),
            duration?.toLong()?.coerceIn(0, 900_000) ?: Power.GRACE_MS)
        timeouts[id] = t
        prefs.edit().putString(id, t.json()).apply()
        call.reply(JSONObject().put("returnValue", true).put("key", key).toString())
        if (at <= now) due() else arm()
    }

    private fun clear(call: Bus.Call) {
        val key = call.params.opt("key") as? String ?: return call.reply(failure("Invalid parameters."))
        val id = "${call.appId}\u0000$key"
        if (timeouts.remove(id) != null) { prefs.edit().remove(id).apply(); arm() }
        call.reply(JSONObject().put("returnValue", true).put("key", key).toString())
    }

    /** Fires every timeout whose time has come, late ones included, and arms the next. */
    private fun due() {
        if (!enabled) return
        val now = System.currentTimeMillis()
        val fired = timeouts.filterValues { it.at <= now }
        if (fired.isNotEmpty()) {
            val edit = prefs.edit()
            for ((id, t) in fired) {
                timeouts.remove(id); edit.remove(id)
                Log.i(AppServer.TAG, "power: timeout ${t.key} of ${t.owner} fired${if (now - t.at > 60_000) ", ${(now - t.at) / 1000} s late" else ""}")
                power.hold(t.holdMs)
                // The callback's payload is an object; any other "params" goes as an empty one.
                bus.call(SERVICE, t.uri, (t.params as? JSONObject)?.toString() ?: "{}", privateBus = true) { reply -> if (!reply.contains("\"returnValue\":true")) Log.w(AppServer.TAG, "power: timeout ${t.key}: $reply") }
            }
            edit.apply()
        }
        arm()
    }

    /** powerd's failures carry no errorCode (measured). */
    private fun failure(text: String) = JSONObject().put("returnValue", false).put("errorText", text).toString()

    private fun arm() {
        timeouts.values.minOfOrNull { it.at }?.let { if (it - System.currentTimeMillis() <= Power.EARLY_MS) power.soon(it) }
        power.at(WAKE, timeouts.values.filter { it.wake }.minOfOrNull { it.at }, wake = true)
        power.at(LATE, timeouts.values.filter { !it.wake }.minOfOrNull { it.at }, wake = false)
    }

    companion object {
        const val SERVICE = "com.palm.power"
        private const val WAKE = "com.palm.power.timeout"
        private const val LATE = "com.palm.power.timeout.late"
        private val IN = Regex("^(\\d{2}):([0-5]\\d):([0-5]\\d)$")
        private fun atFormat() =
            SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
        private val PARAMS = setOf("key", "in", "at", "wakeup", "uri", "params", "keep_existing", "activity_id", "activity_duration_ms")
    }
}
