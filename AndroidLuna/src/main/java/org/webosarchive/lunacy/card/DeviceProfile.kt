package org.webosarchive.lunacy.card

import android.content.Context
import android.os.Build

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
 * The TouchPad's values are measured on the reference device (Docs/spike-1.md,
 * Workbench/results/touchpad-net.txt, luna-send on the device). The Pre3's are from the
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
         * This device's serial, in HP's shape (prefix plus seven characters). Derived from the
         * same hardware ids as the device id, so it too survives a reinstall - some apps send
         * it to webOS Archive's services as well - and made up rather than copied: it says
         * "a TouchPad", not "codepoet's TouchPad".
         */
        fun serial(context: Context, profile: DeviceProfile): String {
            val alphabet = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            val digest = java.security.MessageDigest.getInstance("SHA-1")
                .digest(("lunacy-serial:" + profile.name + ":" + derivedNduid(context)).toByteArray())
            return profile.serialPrefix + (0 until 7).map { alphabet[(digest[it].toInt() and 0xff) % alphabet.length] }.joinToString("")
        }

        /**
         * webOS's device id: 40 hex digits.
         *
         * This is the identity webOS Archive's services know a device by - the App Museum and
         * the shared updater library both send it as `clientid` - and app licences were tied
         * to it too. So two things matter about it: it has to be **stable**, and its owner has
         * to be able to **carry one over**.
         *
         * Stable: it is derived from this device's own hardware ids rather than made up, so
         * reinstalling Lunacy gives the same id back, and a service's analytics don't see a
         * new device every time. It is a SHA-1 of them, which is 40 hex digits exactly, as a
         * webOS nduid is; the hardware ids themselves don't leave the device.
         *
         * Carried over: [setNduid] stores one the owner typed in - a TouchPad's own id, say,
         * when they are moving off hardware that is failing - and that one wins until they
         * ask for this device's own back ([clearNduid]). On a real device the id came from the
         * hardware's token and couldn't be changed, but it was never a secret either: every
         * app could read it.
         */
        fun nduid(context: Context): String =
            context.getSharedPreferences("device", Context.MODE_PRIVATE).getString("nduid", null)
                ?: derivedNduid(context)

        /** Whether the id is this device's own or one its owner carried over. */
        fun nduidSource(context: Context): String =
            if (context.getSharedPreferences("device", Context.MODE_PRIVATE).contains("nduid")) USER else DERIVED

        /**
         * This device's own id: the same after a reinstall, different on another device.
         *
         * ANDROID_ID survives reinstalling and changes on a factory reset, which is as close
         * to a webOS nduid as Android offers. From API 26 it is per signing key as well, so an
         * unsigned rebuild would land on a different id; a later target should keep the first
         * one it computes instead (a ratchet item).
         */
        fun derivedNduid(context: Context): String {
            @Suppress("HardwareIds")
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
            @Suppress("DEPRECATION")
            val serial = Build.SERIAL.orEmpty()
            val seed = "lunacy-nduid:" + androidId + ":" + serial + ":" + Build.MANUFACTURER + ":" + Build.MODEL
            // A webOS nduid is 40 hex digits, which is exactly a SHA-1.
            return java.security.MessageDigest.getInstance("SHA-1")
                .digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        /**
         * Stores an id its owner typed in. Accepts it written with spaces or dashes and in any
         * case, which is how people copy one off an old device, and stores webOS's own form:
         * 40 lower-case hex digits. Returns the stored id, or null if it isn't one.
         */
        fun setNduid(context: Context, raw: String): String? {
            val id = raw.filterNot { it == ' ' || it == '-' || it == ':' }.lowercase()
            if (!Regex("^[0-9a-f]{40}$").matches(id)) return null
            context.getSharedPreferences("device", Context.MODE_PRIVATE).edit().putString("nduid", id).apply()
            return id
        }

        /** Back to this device's own id. Deterministic, so nothing sees a new device. */
        fun clearNduid(context: Context): String {
            context.getSharedPreferences("device", Context.MODE_PRIVATE).edit().remove("nduid").apply()
            return derivedNduid(context)
        }

        const val DERIVED = "derived"
        const val USER = "user"
    }
}
