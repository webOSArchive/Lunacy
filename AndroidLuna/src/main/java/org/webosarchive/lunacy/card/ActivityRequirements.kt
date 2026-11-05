package org.webosarchive.lunacy.card

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/**
 * What an activity's requirements are measured against, as webOS's activity manager watched
 * them: the connection manager's status (internet, wifi, wan and their confidence) and the
 * battery (its level, and a charger or dock). [onChange] is handed each change to make, so the
 * manager can see which activities' requirements it meets before and after.
 */
class ActivityRequirements(
    private val context: Context,
    /** The activity manager's own name, which it watches the connection manager as. */
    private val service: String,
    private val onChange: (change: () -> Unit) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var bus: Bus

    private var netStatus: JSONObject? = null
    private var batteryPercent = -1
    private var charger: String? = null

    private fun confidence(o: JSONObject?): Int {
        if (o == null || o.optString("state") != "connected" || o.optString("onInternet") != "yes") return -1
        return CONFIDENCE.indexOf(o.optString("networkConfidenceLevel"))
    }

    fun met(name: String, v: Any): Boolean {
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

    fun current(name: String): Any {
        val s = netStatus
        return when (name) {
            "internet" -> s ?: false
            "wifi" -> s?.optJSONObject("wifi") ?: false
            "wan" -> s?.optJSONObject("wan") ?: false
            "internetConfidence" -> maxOf(confidence(s?.optJSONObject("wifi")), confidence(s?.optJSONObject("wan"))).let { if (it < 0) "unknown" else CONFIDENCE[it] }
            "wifiConfidence" -> confidence(s?.optJSONObject("wifi")).let { if (it < 0) "unknown" else CONFIDENCE[it] }
            "wanConfidence" -> confidence(s?.optJSONObject("wan")).let { if (it < 0) "unknown" else CONFIDENCE[it] }
            "battery" -> batteryPercent
            else -> met(name, true)
        }
    }

    /** The connection manager's status, as the device's activity manager subscribed to it. */
    private var networkWatch: Bus.Call? = null

    private fun watchNetwork() {
        networkWatch = bus.call(service, "palm://com.palm.connectionmanager/getstatus", "{\"subscribe\":true}", privateBus = true) { reply ->
            main.post {
                val r = runCatching { JSONObject(reply) }.getOrNull() ?: return@post
                onChange { netStatus = r }
            }
        }
    }

    private var batteryReceiver: BroadcastReceiver? = null

    private fun watchBattery() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                onChange {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    batteryPercent = if (level < 0 || scale <= 0) -1 else level * 100 / scale
                    charger = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                        BatteryManager.BATTERY_PLUGGED_USB -> "usb"; BatteryManager.BATTERY_PLUGGED_WIRELESS -> "inductive"
                        BatteryManager.BATTERY_PLUGGED_AC -> "usb"; else -> null
                    }
                }
            }
        }
        batteryReceiver = r
        context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    /** Starts watching the network and the battery. */
    fun watch(bus: Bus) {
        this.bus = bus
        watchNetwork()
        watchBattery()
    }

    fun stop() {
        batteryReceiver?.let { context.unregisterReceiver(it) }; batteryReceiver = null
        networkWatch?.cancel(); networkWatch = null
    }

    companion object {
        /** The connection confidence levels, lowest first. */
        val CONFIDENCE = listOf("none", "poor", "fair", "excellent")
    }
}
