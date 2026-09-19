package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom

/**
 * palm://com.palm.preferences/systemProperties/Get: read-only device properties. Values and
 * reply shapes are the reference TouchPad's (webOS CE 3.1.0), except the device's own ids.
 * A subscription gets the one reply and no updates, as on the TouchPad.
 */
class SystemProperties(context: Context) {
    private val values: Map<String, String> = mapOf(
        // webOS's device id: 40 hex digits. Each Lunacy install makes its own and keeps it.
        "com.palm.properties.nduid" to nduid(context),
        "com.palm.properties.ProdSN" to SERIAL,
        "com.palm.properties.version" to "webOS CE 3.1.0",
        "com.palm.properties.deviceName" to "HP TouchPad",
        "com.palm.properties.deviceNameShort" to "TouchPad",
        "com.palm.properties.DMMODEL" to "HSTNH-I29C",
        "com.palm.properties.DMCARRIER" to "",
        "com.palm.properties.productLineVersion" to "1.0",
        "com.palm.properties.buildName" to "Nova-HP-Topaz",
        "com.palm.properties.buildNumber" to "86",
        "com.palm.properties.browserOsName" to "hpwOS",
    )

    fun register(bus: Bus) {
        bus.register("com.palm.preferences", "systemProperties/Get") { _, p, reply ->
            val key = p.optString("key")
            val v = values[key]
            // The TouchPad's error has no errorCode.
            reply(if (v == null) JSONObject().put("returnValue", false).put("errorText", "no such key").toString()
                  else JSONObject().put(key, v).put("returnValue", true).toString())
        }
    }

    companion object {
        /** deviceInfo's serialNumber and the ProdSN property. */
        const val SERIAL = "lunacy"

        private fun nduid(context: Context): String {
            val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
            prefs.getString("nduid", null)?.let { return it }
            val id = ByteArray(20).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("nduid", id).apply()
            return id
        }
    }
}
