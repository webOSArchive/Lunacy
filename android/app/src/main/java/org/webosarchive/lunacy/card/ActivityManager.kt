package org.webosarchive.lunacy.card

import org.json.JSONObject

/**
 * palm://com.palm.activitymanager: foreground activities, as Palm's mojoservice creates one for
 * every JS service command. Replies and events are the reference TouchPad's (webOS CE 3.1.0):
 * create answers {"activityId","returnValue":true}, then its subscriber gets "start" (with
 * start: true) and later "complete", "cancel" or "stop"; an unknown id gets errorCode 2.
 * Scheduled, triggered and callback activities (background work) aren't supported yet, and
 * say so rather than being accepted and never run.
 */
class ActivityManager {
    private class Activity(val id: Int, val subscriber: Bus.Call?)
    private val activities = HashMap<Int, Activity>()
    private var nextId = 1

    fun register(bus: Bus) {
        bus.register(SERVICE, "create", Bus.CallHandler { create(it) })
        bus.register(SERVICE, "start", Bus.CallHandler { end(it, "start", remove = false) })
        bus.register(SERVICE, "complete", Bus.CallHandler { end(it, "complete") })
        bus.register(SERVICE, "cancel", Bus.CallHandler { end(it, "cancel") })
        bus.register(SERVICE, "stop", Bus.CallHandler { end(it, "stop") })
    }

    private fun create(call: Bus.Call) {
        val spec = call.params.optJSONObject("activity") ?: return call.reply(Bus.error("activity is required"))
        val unsupported = listOf("schedule", "trigger", "callback", "requirements").filter { spec.has(it) }
        if (unsupported.isNotEmpty()) {
            return call.reply(Bus.error("Lunacy doesn't support activity ${unsupported.joinToString()} yet"))
        }
        val id = nextId++
        val a = Activity(id, call.takeIf { it.subscribe })
        activities[id] = a
        call.reply(JSONObject().put("activityId", id).put("returnValue", true).toString())
        if (call.params.optBoolean("start")) event(a, "start")
        // The creator going away ends its activity.
        a.subscriber?.onCancel { activities.remove(id) }
    }

    private fun end(call: Bus.Call, event: String, remove: Boolean = true) {
        val id = call.params.optInt("activityId", -1)
        val a = activities[id]
            ?: return call.reply(JSONObject().put("errorCode", 2).put("errorText", "activityId not found").put("returnValue", false).toString())
        call.reply(Bus.ok())
        event(a, event)
        if (remove) {
            activities.remove(id)
            a.subscriber?.cancel()
        }
    }

    private fun event(a: Activity, event: String) =
        a.subscriber?.reply(JSONObject().put("activityId", a.id).put("event", event).put("returnValue", true).toString())

    companion object { const val SERVICE = "com.palm.activitymanager" }
}
