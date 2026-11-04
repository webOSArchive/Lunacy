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
        bus.register(SERVICE, "control/setProperty", Bus.CallHandler { it.reply(setProperty(it.params, it.privateBus)) })
        bus.register(SERVICE, "control/status", Bus.CallHandler { status(it) })
        bus.register(SERVICE, "control/setState") { _, p, reply -> reply(setState(p)) }
    }

    /**
     * webOS's display states. Lunacy answers for the two Exhibition mode uses - Palm's
     * Exhibition app starts it with {"state":"dock"} - and for "on", which wakes the screen
     * (the SDK's palm-launch and palm-run send it before launching), and says plainly which
     * of the others it hasn't got rather than accepting them and doing nothing. LunaSysMgr
     * answered {"returnValue":true}, and ignored "on" in Exhibition (DisplayManager.cpp).
     */
    var onDockMode: (Boolean) -> Unit = {}
    var inDockMode: () -> Boolean = { false }

    private fun setState(p: JSONObject): String = when (p.optString("state")) {
        "dock" -> { onDockMode(true); Bus.ok() }
        "undock" -> { onDockMode(false); Bus.ok() }
        "on" -> { if (!inDockMode()) wake(); Bus.ok() }
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

    /** Turns the screen on, as a key press would; Android's own timeout turns it off again. */
    @Suppress("DEPRECATION")
    private fun wake() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
            android.os.PowerManager.ON_AFTER_RELEASE, "lunacy:display-on").acquire(1000)
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

    /** [mayAsk]: only a system caller (Screen & Lock) may bring up Android's permission screen. */
    private fun setProperty(p: JSONObject, mayAsk: Boolean): String {
        val problems = ArrayList<String>()
        if (p.has("timeout")) {
            val seconds = p.optInt("timeout", 0)
            if (seconds <= 0) problems += "timeout must be a number of seconds"
            else if (!write(Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000, mayAsk)) problems += "timeout"
        }
        if (p.has("maximumBrightness")) {
            val percent = p.optInt("maximumBrightness", -1)
            if (percent !in 1..100) problems += "maximumBrightness must be 1 to 100"
            else if (!write(Settings.System.SCREEN_BRIGHTNESS, rawFromPercent(percent), mayAsk)) problems += "maximumBrightness"
        }
        return if (problems.isEmpty()) Bus.ok()
        else Bus.error("Android wouldn't let Lunacy set: ${problems.joinToString(", ")}")
    }

    private fun timeout(): Int =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000) / 1000

    /** The screen's brightness as webOS's maximumBrightness, 1 to 100: the system menu's slider. */
    fun brightnessPercent(): Int = brightness()
    fun setBrightnessPercent(percent: Int) { write(Settings.System.SCREEN_BRIGHTNESS, rawFromPercent(percent)) }

    /** [percent] as a window's brightness override, 0…1: the same backlight value the setting gets. */
    fun overrideFromPercent(percent: Int): Float = rawFromPercent(percent) / 255f

    private fun brightness(): Int =
        percentFromRaw(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255))

    /**
     * A slider position as Android's own slider reads it. From Android 9 that slider is
     * perceptual: its position goes through a gamma curve (HLG, BrightnessUtils) to the linear
     * backlight value SCREEN_BRIGHTNESS holds, so half way along is a raw 22 of 255, not 128.
     * Written linearly, the menu's whole right half was the top eighth of Android's slider.
     * Before Android 9 the system's slider was linear, and so is this. (The screen following
     * the slider at once is ShellActivity.setMenuBrightness's.)
     */
    private fun rawFromPercent(percent: Int): Int {
        val p = percent.coerceIn(1, 100) / 100.0
        if (android.os.Build.VERSION.SDK_INT < 28) return Math.round(p * 255).toInt().coerceIn(1, 255)
        val linear = (if (p <= HLG_R) (p / HLG_R) * (p / HLG_R) else Math.exp((p - HLG_C) / HLG_A) + HLG_B) / 12.0
        return (1 + Math.round(linear * 254)).toInt().coerceIn(1, 255)
    }

    private fun percentFromRaw(raw: Int): Int {
        if (android.os.Build.VERSION.SDK_INT < 28) return Math.round(raw * 100 / 255.0).toInt().coerceIn(1, 100)
        val linear = 12.0 * (raw.coerceIn(1, 255) - 1) / 254.0
        val gamma = if (linear <= 1.0) HLG_R * Math.sqrt(linear) else HLG_A * Math.log(linear - HLG_B) + HLG_C
        return Math.round(gamma * 100).toInt().coerceIn(1, 100)
    }

    /**
     * True when Android took the value. It refuses without WRITE_SETTINGS, and says so. From
     * Android 6 that is the user's to grant on Android's own screen, which is opened the first
     * time a write is refused in a run; the write itself still fails, and the next one after
     * the user has said yes will take. An app's own call doesn't open it ([mayAsk] false): only
     * Lunacy and the system's settings apps put Android's screen in front of the user (code
     * review S14, codepoet 2026-11-04).
     */
    fun write(key: String, value: Int, mayAsk: Boolean = true): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(context)) {
            if (mayAsk && !askedToWrite) {
                askedToWrite = true
                try {
                    context.startActivity(android.content.Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:" + context.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.w(AppServer.TAG, "display: can't open the write-settings screen", e)
                }
            }
            Log.w(AppServer.TAG, "display: can't set $key without WRITE_SETTINGS")
            return false
        }
        return try {
            Settings.System.putInt(context.contentResolver, key, value)
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "display: can't set $key", e)
            false
        }
    }
    private var askedToWrite = false

    /**
     * webOS's Auto Dim, which is a systemservice preference (enableALS) that the display
     * owner acts on. Android's equivalent is the screen's brightness mode.
     */
    fun setAutomaticBrightness(on: Boolean) {
        write(Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
    }

    companion object {
        const val SERVICE = "com.palm.display"
        // Android's BrightnessUtils: the Hybrid Log-Gamma curve its brightness slider uses.
        private const val HLG_R = 0.5
        private const val HLG_A = 0.17883277
        private const val HLG_B = 0.28466892
        private const val HLG_C = 0.55991073
    }
}
