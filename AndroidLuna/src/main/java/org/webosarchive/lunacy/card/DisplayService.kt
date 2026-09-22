package org.webosarchive.lunacy.card

import android.content.Context
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * palm://com.palm.display/control: the screen's brightness and how long it stays on.
 *
 * These two really do belong to the host, and Android will let Lunacy set them, so they are
 * answered for real rather than stored and ignored: webOS's timeout (seconds) and
 * maximumBrightness (1-100) are Android's SCREEN_OFF_TIMEOUT and SCREEN_BRIGHTNESS. A write
 * Android refuses comes back as an error, not a success.
 *
 * Reply shape measured on the reference TouchPad:
 * {"returnValue":true,"timeout":1800,"maximumBrightness":100}.
 */
class DisplayService(private val context: Context) {
    private val statusSubscribers = LinkedHashSet<Bus.Call>()

    fun register(bus: Bus) {
        bus.register(SERVICE, "control/getProperty") { _, p, reply -> reply(getProperty(p)) }
        bus.register(SERVICE, "control/setProperty") { _, p, reply -> reply(setProperty(p)) }
        bus.register(SERVICE, "control/status", Bus.CallHandler { status(it) })
        bus.register(SERVICE, "control/setState") { _, p, reply -> reply(setState(p)) }
    }

    /**
     * webOS's display states. Lunacy answers for the two Exhibition mode uses - Palm's
     * Exhibition app starts it with {"state":"dock"} - and says plainly which of the others
     * it hasn't got rather than accepting them and doing nothing.
     */
    var onDockMode: (Boolean) -> Unit = {}

    private fun setState(p: JSONObject): String = when (p.optString("state")) {
        "dock" -> { onDockMode(true); Bus.ok() }
        "undock" -> { onDockMode(false); Bus.ok() }
        "" -> Bus.error("state is required")
        else -> Bus.error("Lunacy has no display state \"${p.optString("state")}\"")
    }

    /**
     * Whether the screen is on, and how long it stays on. Apps ask while starting (Apollo
     * does). The shape is the reference TouchPad's, measured with luna-send:
     * {"returnValue":true,"event":"request","state":"on","timeout":1800,
     *  "blockDisplay":"true","active":false,"subscribed":false} - blockDisplay really is a
     * string there.
     */
    private fun status(call: Bus.Call) {
        call.reply(statusReply(call.subscribe))
        if (call.subscribe && !call.cancelled) {
            statusSubscribers += call
            call.onCancel { statusSubscribers.remove(call) }
        }
    }

    private fun statusReply(subscribed: Boolean): String = JSONObject()
        .put("returnValue", true).put("event", "request")
        .put("state", if (screenOn()) "on" else "off")
        .put("timeout", timeout()).put("blockDisplay", "false")
        .put("active", screenOn()).put("subscribed", subscribed)
        .toString()

    /** The shell tells this when Android says the screen went on or off. */
    fun screenChanged() {
        val event = statusReply(true)
        statusSubscribers.toList().forEach { it.reply(event) }
    }

    @Suppress("DEPRECATION")
    private fun screenOn(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isScreenOn

    private fun getProperty(p: JSONObject): String {
        val names = p.optJSONArray("properties")
        val wanted = (0 until (names?.length() ?: 0)).map { names!!.getString(it) }
            .ifEmpty { listOf("timeout", "maximumBrightness") }
        val out = JSONObject().put("returnValue", true)
        for (name in wanted) when (name) {
            "timeout" -> out.put("timeout", timeout())
            "maximumBrightness" -> out.put("maximumBrightness", brightness())
            // An unknown property is left out, as the TouchPad leaves out a key it hasn't got.
        }
        return out.toString()
    }

    private fun setProperty(p: JSONObject): String {
        val problems = ArrayList<String>()
        if (p.has("timeout")) {
            val seconds = p.optInt("timeout", 0)
            if (seconds <= 0) problems += "timeout must be a number of seconds"
            else if (!write(Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)) problems += "timeout"
        }
        if (p.has("maximumBrightness")) {
            val percent = p.optInt("maximumBrightness", -1)
            if (percent !in 1..100) problems += "maximumBrightness must be 1 to 100"
            else if (!write(Settings.System.SCREEN_BRIGHTNESS, percent * 255 / 100)) problems += "maximumBrightness"
        }
        return if (problems.isEmpty()) Bus.ok()
        else Bus.error("Android wouldn't let Lunacy set: ${problems.joinToString(", ")}")
    }

    private fun timeout(): Int =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000) / 1000

    /** The screen's brightness as webOS's maximumBrightness, 1 to 100: the system menu's slider. */
    fun brightnessPercent(): Int = brightness()
    fun setBrightnessPercent(percent: Int) { write(Settings.System.SCREEN_BRIGHTNESS, percent.coerceIn(1, 100) * 255 / 100) }

    private fun brightness(): Int {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
        return (raw * 100 / 255).coerceIn(1, 100)
    }

    /** True when Android took the value. It refuses without WRITE_SETTINGS, and says so. */
    private fun write(key: String, value: Int): Boolean = try {
        Settings.System.putInt(context.contentResolver, key, value)
    } catch (e: Exception) {
        Log.w(AppServer.TAG, "display: can't set $key", e)
        false
    }

    /**
     * webOS's Auto Dim, which is a systemservice preference (enableALS) that the display
     * owner acts on. Android's equivalent is the screen's brightness mode.
     */
    fun setAutomaticBrightness(on: Boolean) {
        write(Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
    }

    companion object { const val SERVICE = "com.palm.display" }
}
