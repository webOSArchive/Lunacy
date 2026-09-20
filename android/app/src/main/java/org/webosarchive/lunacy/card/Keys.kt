package org.webosarchive.lunacy.card

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import org.json.JSONObject

/**
 * palm://com.palm.keys: the headset and the media keys.
 *
 * Apps ask for these while starting - Apollo asks for both - and a device answers them only
 * to a subscriber. Measured on the reference TouchPad: a call without `subscribe` is refused
 * with "We were expecting a subscribe type message, but we did not recieve one." (webOS's own
 * spelling), and a subscriber gets {"returnValue":true,"subscribed":true} and then an event
 * whenever the state changes.
 *
 * Lunacy backs the headset with Android's, and has no media keys of its own to report.
 */
class Keys(private val context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val headsetSubscribers = LinkedHashSet<Bus.Call>()
    private var receiver: BroadcastReceiver? = null

    fun register(bus: Bus) {
        bus.register(SERVICE, "headset/status", Bus.CallHandler { headset(it) })
        // Lunacy has no media keys. The subscription is honest - it stays open and would
        // deliver if there were any - and it never claims a key was pressed.
        bus.register(SERVICE, "media/status", Bus.CallHandler { call ->
            if (!call.subscribe) call.reply(needsSubscription()) else call.reply(subscribed())
        })
    }

    private fun headset(call: Bus.Call) {
        if (!call.subscribe) return call.reply(needsSubscription())
        call.reply(subscribed())
        headsetSubscribers += call
        call.onCancel { headsetSubscribers.remove(call); if (headsetSubscribers.isEmpty()) stopWatching() }
        startWatching()
    }

    /** webOS's own wording, typo and all (measured on the reference TouchPad). */
    private fun needsSubscription(): String = JSONObject()
        .put("errorCode", -1)
        .put("errorText", "We were expecting a subscribe type message, but we did not recieve one.")
        .put("returnValue", false)
        .put("subscribed", false)
        .toString()

    private fun subscribed(): String =
        JSONObject().put("returnValue", true).put("subscribed", true).toString()

    private fun startWatching() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val plugged = i.getIntExtra("state", 0) == 1
                val mic = i.getIntExtra("microphone", 0) == 1
                val event = JSONObject()
                    .put("returnValue", true)
                    .put("state", if (plugged) (if (mic) "headset" else "headset_mic_less") else "none")
                    .toString()
                headsetSubscribers.toList().forEach { it.reply(event) }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
    }

    private fun stopWatching() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    @Suppress("DEPRECATION")
    fun headsetConnected(): Boolean = audio.isWiredHeadsetOn

    companion object { const val SERVICE = "com.palm.keys" }
}
