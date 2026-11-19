package org.webosarchive.lunacy.card

import org.json.JSONObject
import org.webosarchive.lunacy.core.BatteryWatch
import org.webosarchive.lunacy.core.Platform

/**
 * What an activity's requirements are measured against, as webOS's activity manager watched
 * them: the connection manager's status (internet, wifi, wan and their confidence) and the
 * battery (its level, and a charger or dock). [onChange] is handed each change to make, so the
 * manager can see which activities' requirements it meets before and after.
 */
class ActivityRequirements(
    /** The host's battery, or null where there is none to watch. */
    private val battery: BatteryWatch?,
    /** The activity manager's own name, which it watches the connection manager as. */
    private val service: String,
    private val onChange: (change: () -> Unit) -> Unit,
) {
    private val main = Platform.scheduler
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

    private fun watchBattery() {
        battery?.start { percent, plugged -> onChange { batteryPercent = percent; charger = plugged } }
    }

    /** Starts watching the network and the battery. */
    fun watch(bus: Bus) {
        this.bus = bus
        watchNetwork()
        watchBattery()
    }

    fun stop() {
        battery?.stop()
        networkWatch?.cancel(); networkWatch = null
    }

    companion object {
        /** The connection confidence levels, lowest first. */
        val CONFIDENCE = listOf("none", "poor", "fair", "excellent")
    }
}
