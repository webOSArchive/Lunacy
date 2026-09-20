package org.webosarchive.lunacy.card

import android.content.Context
import java.security.SecureRandom

/**
 * The webOS device Lunacy reports itself as.
 *
 * Lunacy is not pretending to be webOS - the shell, the bus and Device Info all say plainly
 * what they are - but to an *app* it has to be a webOS device, because that is the contract
 * apps were written against: they branch on the model, the platform version and the user
 * agent, and the servers they talk to were built for those values. The webOS device closest
 * to an Android tablet is the TouchPad, and to a phone the Pre3, so Lunacy answers as one of
 * those two, consistently, everywhere an app can look:
 *
 *   - `PalmSystem.deviceInfo` (the shell),
 *   - the user agent, in the page and on the wire (the card host),
 *   - `com.palm.preferences/systemProperties/Get` (SystemProperties),
 *   - `X-Palm-Carrier` on every request the network shim sends (NetShim).
 *
 * A device's own identity - its serial and its nduid - is made up per install rather than
 * copied from anyone's device: it is a TouchPad-shaped serial, not a particular TouchPad's.
 *
 * The TouchPad's values are measured on the reference device (docs/spike-1.md,
 * spike/results/touchpad-net.txt, luna-send on the device). The Pre3's are from the
 * community's record and have **not** been measured on hardware here; the first target is a
 * tablet, so nothing depends on them yet. Confirm them on a Pre3 before trusting them.
 */
enum class DeviceProfile(
    val modelName: String,
    val deviceName: String,
    /** DMMODEL and PRODoID, which apps key off to tell webOS devices apart. */
    val model: String,
    val boardType: String,
    val platformVersion: String,
    val userAgent: String,
    /** X-Palm-Carrier, sent on every request. c090-01 is the WiFi TouchPad's. */
    val carrierCode: String,
    val buildName: String,
    val buildNumber: String,
    /** The shape of an HP serial for this device: prefix plus random, per install. */
    val serialPrefix: String,
    val keyboardAvailable: Boolean,
    val keyboardSlider: Boolean,
    val keyboardType: String,
    val bluetoothAvailable: Boolean,
    val carrierAvailable: Boolean,
    val coreNaviButton: Boolean,
    val swappableBattery: Boolean,
    /** A phone's card fills the screen below the status bar; a tablet's has a minimum. */
    val minimumCardHeight: Int,
    val touchableRows: Int,
) {
    /** Measured on the reference TouchPad (webOS CE 3.1.0). */
    TOUCHPAD(
        modelName = "TouchPad",
        deviceName = "HP TouchPad",
        model = "HSTNH-I29C",
        boardType = "topaz-Wifi-pvt\n",
        platformVersion = "3.1.0",
        userAgent = "Mozilla/5.0 (hp-tablet; Linux; hpwOS/3.1.0; U; en-US) AppleWebKit/534.6 " +
            "(KHTML, like Gecko) wOSSystem/234.83 Safari/534.6 TouchPad/1.0",
        carrierCode = "c090-01",
        buildName = "Nova-HP-Topaz",
        buildNumber = "86",
        serialPrefix = "5CL",
        keyboardAvailable = false,
        keyboardSlider = false,
        keyboardType = "Unknown",
        bluetoothAvailable = false,
        carrierAvailable = false,
        coreNaviButton = false,
        swappableBattery = false,
        minimumCardHeight = 318,
        touchableRows = 14,
    ),

    /** Not measured on hardware: from the community's record. See the note above. */
    PRE3(
        modelName = "Pre3",
        deviceName = "HP Pre3",
        model = "P160UNA",
        boardType = "mantaray-pvt\n",
        platformVersion = "2.2.4",
        userAgent = "Mozilla/5.0 (webOS/2.2.4; U; en-US) AppleWebKit/534.6 (KHTML, like Gecko) " +
            "wOSBrowser/221.56 Safari/534.6 Pre/3.0",
        carrierCode = "c000-01",
        buildName = "Nova-Palm-Mantaray",
        buildNumber = "1",
        serialPrefix = "PRE",
        keyboardAvailable = true,
        keyboardSlider = true,
        keyboardType = "QWERTY",
        bluetoothAvailable = true,
        carrierAvailable = true,
        coreNaviButton = true,
        swappableBattery = true,
        minimumCardHeight = 0,
        touchableRows = 8,
    );

    val modelNameAscii get() = modelName
    val deviceNameShort get() = modelName
    val platformVersionMajor get() = platformVersion.substringBefore('.').toInt()
    val platformVersionMinor get() = platformVersion.split('.').getOrElse(1) { "0" }.toInt()
    val platformVersionDot get() = platformVersion.split('.').getOrElse(2) { "0" }.toInt()

    /** What systemProperties/Get reports for com.palm.properties.version. */
    val versionString get() = if (this == TOUCHPAD) "webOS CE $platformVersion" else "webOS $platformVersion"

    companion object {
        /**
         * The webOS device this screen is most like. The same test the shell uses to decide
         * whether it is drawing a tablet.
         */
        fun forScreen(context: Context): DeviceProfile =
            if (context.resources.configuration.smallestScreenWidthDp >= 600) TOUCHPAD else PRE3

        /**
         * This install's serial, in HP's shape (prefix plus seven characters). Kept, so it
         * doesn't change under an app that remembers it, and generated rather than copied:
         * it says "a TouchPad", not "codepoet's TouchPad".
         */
        fun serial(context: Context, profile: DeviceProfile): String {
            val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
            val key = "serial-${profile.name.lowercase()}"
            prefs.getString(key, null)?.let { return it }
            val alphabet = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            val random = SecureRandom()
            val serial = profile.serialPrefix + (1..7).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            prefs.edit().putString(key, serial).apply()
            return serial
        }

        /** webOS's device id: 40 hex digits, one per install. */
        fun nduid(context: Context): String {
            val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
            prefs.getString("nduid", null)?.let { return it }
            val id = ByteArray(20).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("nduid", id).apply()
            return id
        }
    }
}
