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
 * Both devices' values are measured on hardware: the TouchPad's on the reference device
 * (Docs/spike-1.md, Workbench/results/touchpad-net.txt, luna-send on the device), the Pre3's
 * on 2026-09-21 (Docs/pre3.md). The Pre3 measured was an AT&T unit, so the values that
 * carriers change - buildName, carrierName, carrierCode - are that unit's; an unlocked Pre3
 * will differ, and Docs/pre3.md says so.
 */
enum class DeviceProfile(
    /** deviceInfo.modelName. Palm branded the phone "Pre" with a macron, so this isn't ASCII. */
    val modelName: String,
    /** deviceInfo.modelNameAscii, and the deviceNameShort property. */
    val modelNameAscii: String,
    val deviceName: String,
    /** DMMODEL and PRODoID, which apps key off to tell webOS devices apart. */
    val model: String,
    val boardType: String,
    val platformVersion: String,
    val userAgent: String,
    /** X-Palm-Carrier, sent on every request. c090-01 is the WiFi TouchPad's, c001-01 an AT&T Pre3's. */
    val carrierCode: String,
    /** deviceInfo.carrierName and the DMCARRIER property. Empty on a WiFi TouchPad, "ATT" on that Pre3. */
    val carrierName: String,
    /** The productLineVersion property, which is also the device version the user agent ends with. */
    val productLineVersion: String,
    /** The browserOsName property. 2.2.4 hasn't got it, so null means "don't answer that key". */
    val browserOsName: String?,
    val buildName: String,
    val buildNumber: String,
    /** The shape of an HP serial for this device: prefix plus random, per install. */
    val serialPrefix: String,
    /** Characters after the prefix. A TouchPad serial is 10 long, the Pre3's 14. */
    val serialBodyLength: Int,
    val keyboardAvailable: Boolean,
    val keyboardSlider: Boolean,
    val keyboardType: String,
    val bluetoothAvailable: Boolean,
    val carrierAvailable: Boolean,
    val coreNaviButton: Boolean,
    val swappableBattery: Boolean,
    val minimumCardHeight: Int,
    val touchableRows: Int,
    /**
     * Whether deviceInfo reports the screen's long side as its width. A TouchPad says
     * 1024 x 768 whichever way up it is; a Pre3 says 480 x 800. Neither changes when the
     * screen turns - rotation reaches apps through screenOrientation - so this is just which
     * way round the device names its own screen.
     */
    val naturalLandscape: Boolean,
    /**
     * PositiveSpaceTopPadding from the device's luna-platform.conf, which is what
     * deviceInfo.maximumCardHeight is short of the screen: 28 on the TouchPad (its status bar
     * height), 42 on the Pre3.
     */
    val positiveSpaceTopPadding: Int,
    /** Whether deviceInfo carries a carrierAvailable member at all. The Pre3's hasn't got one. */
    val reportsCarrierAvailable: Boolean,
) {
    /** Measured on the reference TouchPad (webOS CE 3.1.0). */
    TOUCHPAD(
        modelName = "TouchPad",
        modelNameAscii = "TouchPad",
        deviceName = "HP TouchPad",
        model = "HSTNH-I29C",
        boardType = "topaz-Wifi-pvt\n",
        platformVersion = "3.1.0",
        userAgent = "Mozilla/5.0 (hp-tablet; Linux; hpwOS/3.1.0; U; en-US) AppleWebKit/534.6 " +
            "(KHTML, like Gecko) wOSSystem/234.83 Safari/534.6 TouchPad/1.0",
        carrierCode = "c090-01",
        carrierName = "",
        productLineVersion = "1.0",
        browserOsName = "hpwOS",
        buildName = "Nova-HP-Topaz",
        buildNumber = "86",
        serialPrefix = "5CL",
        serialBodyLength = 7,
        keyboardAvailable = false,
        keyboardSlider = false,
        keyboardType = "Unknown",
        // The TouchPad has Bluetooth and reports it, whatever Lunacy can do with it: a service
        // an app then calls gets an honest error, as any unimplemented one does.
        bluetoothAvailable = true,
        carrierAvailable = false,
        coreNaviButton = false,
        swappableBattery = false,
        minimumCardHeight = 318,
        touchableRows = 14,
        naturalLandscape = true,
        positiveSpaceTopPadding = 28,
        reportsCarrierAvailable = true,
    ),

    /**
     * Measured on an AT&T Pre3 (HP webOS 2.2.4) on 2026-09-21: Docs/pre3.md. The user agent
     * and X-Palm-Carrier were read off the wire from an app context, the rest from
     * PalmSystem.deviceInfo and systemProperties/Get on the device.
     *
     * Carrier-specific, and this unit's rather than every Pre3's: buildName, carrierName and
     * carrierCode. An unlocked Pre3 would report its own; none was available to measure.
     */
    PRE3(
        // Palm's own branding, and what deviceInfo reports: "Pre" with a macron.
        modelName = "Prē3",
        modelNameAscii = "Pre3",
        deviceName = "HP Pre3",
        model = "HSTNH-F30CN",
        // No trailing newline, where the TouchPad's boardType has one.
        boardType = "mantaray-pvt",
        platformVersion = "2.2.4",
        // 2.2.4 names the product token webOSSystem, where 3.x uses wOSSystem, and has no
        // device-class field before "Linux".
        userAgent = "Mozilla/5.0 (Linux; webOS/2.2.4; U; en-US) AppleWebKit/534.6 " +
            "(KHTML, like Gecko) webOSSystem/221.56 Safari/534.6 Pre/3.0",
        carrierCode = "c001-01",
        carrierName = "ATT",
        productLineVersion = "3.0",
        // 2.2.4 answers "no such key" for browserOsName.
        browserOsName = null,
        buildName = "Nova-ATT-Mantaray",
        buildNumber = "2211",
        // One unit's shape: four characters and ten more. Confirm on a second Pre3.
        serialPrefix = "MTRE",
        serialBodyLength = 10,
        keyboardAvailable = true,
        keyboardSlider = true,
        keyboardType = "QWERTY",
        bluetoothAvailable = true,
        carrierAvailable = false,
        // False on the device, though the Pre3 has a gesture area with a light bar.
        coreNaviButton = false,
        swappableBattery = true,
        minimumCardHeight = 318,
        touchableRows = 14,
        naturalLandscape = false,
        positiveSpaceTopPadding = 42,
        // The Pre3's deviceInfo has no carrierAvailable member at all.
        reportsCarrierAvailable = false,
    );

    val deviceNameShort get() = modelNameAscii
    val platformVersionMajor get() = platformVersion.substringBefore('.').toInt()
    val platformVersionMinor get() = platformVersion.split('.').getOrElse(1) { "0" }.toInt()
    val platformVersionDot get() = platformVersion.split('.').getOrElse(2) { "0" }.toInt()

    /** What systemProperties/Get reports for com.palm.properties.version. */
    val versionString get() = if (this == TOUCHPAD) "webOS CE $platformVersion" else "HP webOS $platformVersion"

    companion object {
        /**
         * The webOS device this screen is most like. The same test the shell uses to decide
         * whether it is drawing a tablet.
         */
        fun forScreen(context: Context): DeviceProfile =
            if (context.resources.configuration.smallestScreenWidthDp >= 600) TOUCHPAD else PRE3

        /**
         * This device's serial, in HP's shape for this profile. Derived from the
         * same hardware ids as the device id, so it too survives a reinstall - some apps send
         * it to webOS Archive's services as well - and made up rather than copied: it says
         * "a TouchPad", not "codepoet's TouchPad".
         */
        fun serial(context: Context, profile: DeviceProfile): String {
            val alphabet = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            val digest = java.security.MessageDigest.getInstance("SHA-1")
                .digest(("lunacy-serial:" + profile.name + ":" + derivedNduid(context)).toByteArray())
            return profile.serialPrefix + (0 until profile.serialBodyLength).map { alphabet[(digest[it].toInt() and 0xff) % alphabet.length] }.joinToString("")
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
