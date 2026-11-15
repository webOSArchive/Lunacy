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
 * - The media class (Music, codepoet 2026-11-04) is Android's music stream the same way:
 *   `media/getVolume`, `media/setVolume`, `media/status`, and `media/lockVolumeKeys`, which
 *   points the device's volume keys at that stream while the caller holds it, as webOS locked
 *   its keys to the media class for the app in front. Its status is the system class's shape
 *   with "scenario":"media_back_speaker", "active":true and "mic_gain":-1, measured on the
 *   TouchPad; `media/getVolume` is `{"returnValue":true,"scenario":"media_back_speaker","volume":70}`
 *   and `media/lockVolumeKeys` `{"returnValue":true}`.
 * - The ringtone class (codepoet 2026-11-15; Night Moves' night and morning schedules) is
 *   Android's ring stream, kept the same way as the system class: the TouchPad keeps the two
 *   apart (a system volume change sent nothing to ringtone subscribers), and so does Android
 *   where the device has a ringer of its own. Its muted is the ring stream's mute.
 * - Other classes (voice, alerts, notifications) aren't here, and the bus says so.
 * - The system and ringtone classes are the private bus's: an app outside com.palm.* gets the
 *   router's "Unknown method" for them, measured with Workbench/probe's bus probe under both
 *   ids on the TouchPad (2026-11-15). The media class answers both.
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
 * - `ringtone/status`: the system class's shape with "scenario":"ringtone_default";
 *   `ringtone/getVolume` `{"returnValue":true,"scenario":"ringtone_default","volume":70}`;
 *   `ringtone/setVolume` and `ringtone/setMuted` the system class's answers and errors. A change
 *   is sent as `"action":"changed","changed":["volume"]`, as for the system class.
 * - An app outside com.palm.* (a probe app, not luna-send) gets `Unknown method "status" for
 *   category "/system"` (and "/ringtone"); `media/status` answers it.
 */
class AudioService(
    context: Context,
    /** Whether the system class starts muted: webOS's systemSounds preference turned off. */
    startMuted: Boolean,
    private val playFeedback: (String) -> Unit,
    /** The volume keys work the media stream (true) or Android's default (false): media/lockVolumeKeys. */
    private val lockVolumeKeys: (Boolean) -> Unit = {},
) {
    private val app: Context = context.applicationContext
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    /** The system class is muted: Lunacy's feedback sounds don't play. */
    var muted = startMuted
        private set

    // Android's own volume keys and its settings change the stream too.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { system.changed(listOf("volume")); ringtone.changed(listOf("volume", "muted").filter { it == "volume" || ringIsMuted() != lastRingMuted }); lastRingMuted = ringIsMuted(); mediaChanged() }
    }

    // ---- the media class ----
    private val mediaWatchers = LinkedHashSet<Bus.Call>()
    private var mediaAsked = -1
    private var mediaReported = -1
    private val locks = LinkedHashSet<Bus.Call>()

    private fun mediaMax() = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private fun mediaVolume(): Int {
        val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (mediaAsked >= 0 && (mediaAsked * mediaMax() / 100f).roundToInt() == now) return mediaAsked
        mediaAsked = -1
        return (now * 100f / mediaMax()).roundToInt()
    }

    private fun mediaState(): JSONObject = JSONObject().put("returnValue", true).put("scenario", "media_back_speaker")
        .put("volume", mediaVolume()).put("mic_gain", -1).put("active", true).put("ringer switch", true)
        .put("muted", false).put("slider", false).put("hac", false)

    private fun mediaStatus(c: Bus.Call) {
        c.reply(mediaState().put("action", "requested").put("subscribed", c.subscribe).toString())
        if (c.subscribe && !c.cancelled) { mediaWatchers += c; c.onCancel { mediaWatchers.remove(c) } }
    }

    private fun mediaChanged() {
        val v = mediaVolume()
        if (v == mediaReported) return
        mediaReported = v
        val j = mediaState().put("action", "changed").put("changed", JSONArray().put("volume")).toString()
        mediaWatchers.toList().forEach { it.reply(j) }
    }

    private fun mediaSetVolume(c: Bus.Call) {
        val v = integer(c.params, "volume") ?: return c.reply(SCHEMA_ERROR)
        if (v !in 0..100) {
            return c.reply(JSONObject().put("returnValue", false).put("errorCode", 3)
                .put("errorText", "Invalid 'volume' integer parameter value.").toString())
        }
        mediaAsked = v
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * mediaMax() / 100f).roundToInt(), 0)
        c.reply(Bus.ok())
        mediaChanged()
    }

    private fun lock(c: Bus.Call) {
        c.reply(Bus.ok())
        if (!c.subscribe || c.cancelled) return
        locks += c
        lockVolumeKeys(true)
        c.onCancel { locks.remove(c); if (locks.isEmpty()) lockVolumeKeys(false) }
    }

    private var lastRingMuted = false

    init {
        mediaReported = mediaVolume()
        lastRingMuted = ringIsMuted()
        app.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED))
    }

    /** The shell is going: the receiver, which holds it, goes too. */
    fun close() { runCatching { app.unregisterReceiver(receiver) } }

    fun register(bus: Bus) {
        system.register(bus)
        ringtone.register(bus)
        bus.register(SERVICE, "systemsounds/playFeedback", Bus.CallHandler { feedback(it) })
        bus.register(SERVICE, "media/status", Bus.CallHandler { mediaStatus(it) })
        bus.register(SERVICE, "media/getVolume", Bus.CallHandler { it.reply(JSONObject().put("returnValue", true)
            .put("scenario", "media_back_speaker").put("volume", mediaVolume()).toString()) })
        bus.register(SERVICE, "media/setVolume", Bus.CallHandler { mediaSetVolume(it) })
        bus.register(SERVICE, "media/lockVolumeKeys", Bus.CallHandler { lock(it) })
    }

    /**
     * One of webOS's volume classes over one of Android's streams: status (with its
     * subscription), getVolume, setVolume and setMuted, as the TouchPad answered them.
     */
    private inner class VolumeClass(
        private val category: String,
        private val stream: Int,
        private val scenario: String,
        /** Whether the class is muted. */
        private val isMuted: () -> Boolean,
        /** Mutes or unmutes it; an error reply's text when Android refuses. */
        private val setMutedTo: (Boolean) -> String?,
    ) {
        private val watchers = LinkedHashSet<Bus.Call>()
        /** The last volume asked for, reported while Android's stream is still at the step it maps to. */
        private var asked = -1
        private var reported = -1

        fun register(bus: Bus) {
            fun private(method: String, h: (Bus.Call) -> Unit) =
                bus.registerPrivate(SERVICE, "$category/$method", wholeService = false, Bus.CallHandler(h))
            private("status") { status(it) }
            private("getVolume") { it.reply(JSONObject().put("returnValue", true).put("scenario", scenario).put("volume", volume()).toString()) }
            private("setVolume") { setVolume(it) }
            private("setMuted") { setMuted(it) }
            reported = volume()
        }

        private fun max() = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        /**
         * webOS's 0..100 as the stream's steps. A volume above 0 is never the bottom step: on
         * Android that is the ringer going silent, which only an app with Do Not Disturb access
         * may do (Night Moves' night volume 1 rounded to 0 on the Pixel's seven steps, and
         * Android threw).
         */
        private fun step(volume: Int) = (volume * max() / 100f).roundToInt().let { if (volume > 0) it.coerceAtLeast(1) else it }

        /** 0..100. A volume the slider set is kept while the stream is at its step, so it doesn't jump. */
        fun volume(): Int {
            val now = am.getStreamVolume(stream)
            if (asked >= 0 && step(asked) == now) return asked
            asked = -1
            return (now * 100f / max()).roundToInt()
        }

        private fun state(): JSONObject = JSONObject()
            .put("returnValue", true)
            .put("scenario", scenario)
            .put("volume", volume())
            .put("active", false)
            .put("ringer switch", true)
            .put("muted", isMuted())
            .put("slider", false)
            .put("hac", false)

        private fun status(c: Bus.Call) {
            c.reply(state().put("action", "requested").put("subscribed", c.subscribe).toString())
            if (c.subscribe && !c.cancelled) { watchers += c; c.onCancel { watchers.remove(c) } }
        }

        /** Tells subscribers what changed, only if it did: the TouchPad sends nothing for a no-op. */
        fun changed(keys: List<String>) {
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
            // Android may still refuse (0 is silent, which needs Do Not Disturb access): an error then.
            try { am.setStreamVolume(stream, step(v), 0) } catch (e: SecurityException) {
                return c.reply(refused(e))
            }
            asked = v
            c.reply(Bus.ok())
            changed(listOf("volume"))
        }

        private fun setMuted(c: Bus.Call) {
            val m = c.params.opt("muted") as? Boolean ?: return c.reply(SCHEMA_ERROR)
            val was = isMuted()
            setMutedTo(m)?.let { return c.reply(it) }
            c.reply(Bus.ok())
            if (was != isMuted()) changed(listOf("muted"))
        }
    }

    private fun refused(e: SecurityException) = JSONObject().put("returnValue", false).put("errorCode", -1)
        .put("errorText", "Android wouldn't let Lunacy set the volume: ${e.message}").toString()

    private val system = VolumeClass("system", AudioManager.STREAM_SYSTEM, "system_default",
        isMuted = { muted }, setMutedTo = { muted = it; null })

    /** The ring stream's own mute: Android 6 and later can say whether it is on; before that, Lunacy remembers. */
    private var ringMuted = false
    private fun ringIsMuted() = if (android.os.Build.VERSION.SDK_INT >= 23) am.isStreamMute(AudioManager.STREAM_RING) else ringMuted

    private val ringtone = VolumeClass("ringtone", AudioManager.STREAM_RING, "ringtone_default",
        isMuted = { ringIsMuted() },
        setMutedTo = { m ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    am.adjustStreamVolume(AudioManager.STREAM_RING, if (m) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
                } else {
                    @Suppress("DEPRECATION") am.setStreamMute(AudioManager.STREAM_RING, m)
                }
                ringMuted = m
                null
            } catch (e: SecurityException) { refused(e) }
        })

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
        /** AudioManager.VOLUME_CHANGED_ACTION, not public API but sent since Android 4. */
        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private val SCHEMA_ERROR = JSONObject().put("returnValue", false).put("errorCode", 1)
            .put("errorText", "Could not validate json message against schema").toString()
    }
}
