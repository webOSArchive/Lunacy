package org.webosarchive.lunacy.card

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * palm://com.palm.audio: the parts of webOS's audio service that Sounds & Alerts uses - the
 * system class's volume and mute, and the feedback sounds.
 *
 * - The system class's volume is Android's system stream, 0..100 over the stream's steps. On a
 *   tablet Android ties that stream to its notification volume, so the two move together.
 * - Muting the system class mutes Lunacy's own system sounds: the shell's feedback sounds
 *   ([playFeedback]). Android's own touch sounds are Android's, and Lunacy leaves them alone.
 * - Other classes (media, ringtone) aren't here, and the bus says so.
 *
 * Measured on the reference TouchPad with luna-send (webOS CE 3.1.0):
 * - `system/status`: `{"returnValue":true,"action":"requested","scenario":"system_default",
 *   "volume":72,"active":false,"ringer switch":true,"muted":false,"slider":false,"hac":false,
 *   "subscribed":false}`, with `"subscribed":true` for a subscription. A change is sent to
 *   subscribers as the same object with `"action":"changed","changed":["volume"]` and no
 *   `subscribed`. Setting the volume it already has sends nothing.
 * - `system/setVolume {"volume":72}` and `system/setMuted {"muted":false}` answer
 *   `{"returnValue":true}`. A volume over 100 answers `{"returnValue":false,"errorCode":3,
 *   "errorText":"Invalid 'volume' integer parameter value."}`; a missing or non-integer
 *   parameter, `{"returnValue":false,"errorCode":1,"errorText":"Could not validate json
 *   message against schema"}`. The same schema error comes from `playFeedback` without a name.
 * - `systemsounds/playFeedback {"name":…}` answers `{"returnValue":true}`, even for a sound that
 *   doesn't exist.
 * - A public caller gets the same answers as a system app.
 */
class AudioService(
    context: Context,
    /** Whether the system class starts muted: webOS's systemSounds preference turned off. */
    startMuted: Boolean,
    private val playFeedback: (String) -> Unit,
) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val watchers = ArrayList<Bus.Call>()

    /** The system class is muted: Lunacy's feedback sounds don't play. */
    var muted = startMuted
        private set

    /** The last volume asked for, reported while Android's stream is still at the step it maps to. */
    private var asked = -1
    private var reported = -1

    init {
        reported = volume()
        // Android's own volume keys and its settings change the stream too.
        context.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { changed(listOf("volume")) }
        }, IntentFilter(VOLUME_CHANGED))
    }

    fun register(bus: Bus) {
        bus.register(SERVICE, "system/status", Bus.CallHandler { status(it) })
        bus.register(SERVICE, "system/setVolume", Bus.CallHandler { setVolume(it) })
        bus.register(SERVICE, "system/setMuted", Bus.CallHandler { setMuted(it) })
        bus.register(SERVICE, "systemsounds/playFeedback", Bus.CallHandler { feedback(it) })
    }

    private fun max() = am.getStreamMaxVolume(STREAM).coerceAtLeast(1)
    private fun step(volume: Int) = (volume * max() / 100f).roundToInt()

    /** 0..100. A volume the slider set is kept while the stream is at its step, so it doesn't jump. */
    private fun volume(): Int {
        val now = am.getStreamVolume(STREAM)
        if (asked >= 0 && step(asked) == now) return asked
        asked = -1
        return (now * 100f / max()).roundToInt()
    }

    private fun state(): JSONObject = JSONObject()
        .put("returnValue", true)
        .put("scenario", "system_default")
        .put("volume", volume())
        .put("active", false)
        .put("ringer switch", true)
        .put("muted", muted)
        .put("slider", false)
        .put("hac", false)

    private fun status(c: Bus.Call) {
        val j = state().put("action", "requested").put("subscribed", c.subscribe)
        c.reply(j.toString())
        if (c.subscribe && !c.cancelled) { watchers += c; c.onCancel { watchers.remove(c) } }
    }

    /** Tells subscribers what changed, only if it did: the TouchPad sends nothing for a no-op. */
    private fun changed(keys: List<String>) {
        val real = keys.filter { k -> k != "volume" || volume() != reported }
        reported = volume()
        if (real.isEmpty()) return
        val j = state().put("action", "changed").put("changed", JSONArray(real)).toString()
        watchers.toList().forEach { it.reply(j) }
    }

    private fun setVolume(c: Bus.Call) {
        val v = integer(c.params, "volume") ?: return c.reply(SCHEMA_ERROR)
        if (v !in 0..100) {
            return c.reply(JSONObject().put("returnValue", false).put("errorCode", 3)
                .put("errorText", "Invalid 'volume' integer parameter value.").toString())
        }
        asked = v
        am.setStreamVolume(STREAM, step(v), 0)
        c.reply(Bus.ok())
        changed(listOf("volume"))
    }

    private fun setMuted(c: Bus.Call) {
        val m = c.params.opt("muted") as? Boolean ?: return c.reply(SCHEMA_ERROR)
        val was = muted
        muted = m
        c.reply(Bus.ok())
        if (was != m) changed(listOf("muted"))
    }

    private fun feedback(c: Bus.Call) {
        val name = c.params.opt("name") as? String ?: return c.reply(SCHEMA_ERROR)
        if (!muted) playFeedback(name)
        c.reply(Bus.ok())
    }

    /** A JSON integer, as the service's schema takes it: not a string, not a fraction. */
    private fun integer(p: JSONObject, key: String): Int? = when (val v = p.opt(key)) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> if (v == Math.floor(v)) v.toInt() else null
        else -> null
    }

    companion object {
        const val SERVICE = "com.palm.audio"
        private const val STREAM = AudioManager.STREAM_SYSTEM
        /** AudioManager.VOLUME_CHANGED_ACTION, not public API but sent since Android 4. */
        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private val SCHEMA_ERROR = JSONObject().put("returnValue", false).put("errorCode", 1)
            .put("errorText", "Could not validate json message against schema").toString()
    }
}
