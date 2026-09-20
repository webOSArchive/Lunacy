package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONObject

/**
 * palm://com.palm.preferences/systemProperties/Get: read-only device properties.
 *
 * Values and reply shapes are the reference TouchPad's (webOS CE 3.1.0), except this
 * install's own ids. Which device Lunacy answers as is DeviceProfile's decision, so the
 * properties, deviceInfo, the user agent and X-Palm-Carrier can never disagree.
 *
 * A subscription gets the one reply and no updates, as on the TouchPad.
 */
class SystemProperties(private val context: Context) {
    private val profile = DeviceProfile.forScreen(context)
    /** Read afresh each time: the device id can be changed while Lunacy is running. */
    private val live: Map<String, () -> String> = mapOf(
        "com.palm.properties.nduid" to { DeviceProfile.nduid(context) },
        "com.palm.properties.ProdSN" to { DeviceProfile.serial(context, profile) },
    )
    private val values: Map<String, String> = mapOf(
        "com.palm.properties.version" to profile.versionString,
        "com.palm.properties.deviceName" to profile.deviceName,
        "com.palm.properties.deviceNameShort" to profile.deviceNameShort,
        "com.palm.properties.DMMODEL" to profile.model,
        // The product id apps key off to tell one webOS device from another: Palm's Help app
        // picks its help content by it. The reference TouchPad reports the same string as
        // DMMODEL.
        "com.palm.properties.PRODoID" to profile.model,
        "com.palm.properties.boardType" to profile.boardType,
        "com.palm.properties.DMCARRIER" to "",
        "com.palm.properties.productLineVersion" to "1.0",
        "com.palm.properties.buildName" to profile.buildName,
        "com.palm.properties.buildNumber" to profile.buildNumber,
        "com.palm.properties.browserOsName" to "hpwOS",
    )

    fun register(bus: Bus) {
        bus.register("com.palm.preferences", "systemProperties/Get") { _, p, reply ->
            val key = p.optString("key")
            val v = live[key]?.invoke() ?: values[key]
            // The TouchPad's error has no errorCode.
            reply(if (v == null) JSONObject().put("returnValue", false).put("errorText", "no such key").toString()
                  else JSONObject().put(key, v).put("returnValue", true).toString())
        }
    }
}
