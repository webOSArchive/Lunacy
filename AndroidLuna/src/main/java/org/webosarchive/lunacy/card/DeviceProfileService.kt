package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONObject

/**
 * palm://com.palm.deviceprofile: the device's identity for the system's own services. The
 * palmprofile service sends it with every sign-in, and apps with privileged ids read it.
 *
 * Measured on the reference TouchPad (webOS CE 3.1.0): it is on the private bus only - a
 * public caller is told the service does not exist - and `getDeviceProfile` answers
 * `{deviceInfo: {…}, returnValue: true}` with the fields below, `getDeviceId` with the nduid.
 * The values are the ones Lunacy already answers with elsewhere (the nduid, the serial and the
 * model, from [DeviceProfile]), so they can't disagree. What Lunacy has no value for is empty,
 * as a wifi TouchPad's phone number and carrier are: the MAC addresses (Android doesn't give
 * them out), HP's product numbers, and HP's device-management credentials (the dmSets, nonces
 * and passwords its update servers issued), which were never issued to Lunacy.
 */
class DeviceProfileService(private val context: Context, private val profile: DeviceProfile) {
    fun register(bus: Bus) {
        bus.register(SERVICE, "getDeviceProfile", Bus.CallHandler { c ->
            c.reply(if (!c.privateBus) absent() else Bus.ok(mapOf("deviceInfo" to deviceInfo())))
        })
        bus.register(SERVICE, "getDeviceId", Bus.CallHandler { c ->
            c.reply(if (!c.privateBus) absent() else Bus.ok(mapOf("deviceId" to DeviceProfile.nduid(context))))
        })
    }

    private fun absent() = Bus.error("Service does not exist: $SERVICE.")

    private fun deviceInfo(): JSONObject {
        val build = "${profile.buildName}-${profile.buildNumber}"
        return JSONObject()
            .put("deviceId", "").put("nduId", DeviceProfile.nduid(context)).put("phoneNumber", "").put("carrier", "")
            .put("carrierROM", build).put("network", "none").put("dataNetwork", "unknown").put("firmwareVersion", "")
            .put("buildTime", "").put("softwareVersion", build).put("deviceModel", profile.model)
            .put("hardwareType", profile.boardType.trim().substringBefore('-')).put("hardwareVersion", "")
            .put("platform", "Nova").put("platformVersion", "")
            .put("serialNumber", DeviceProfile.serial(context, profile)).put("HPSerialNumber", "").put("productSku", "")
            .put("WIFIoADDR", "").put("BToADDR", "").put("dmSets", "").put("serverAuthType", "")
            .put("serverNonce", "").put("serverPwd", "").put("clientNonce", "").put("clientPwd", "")
            .put("clientCredential", "").put("softwareBuildBranch", "").put("swUpdateTarget", "")
    }

    companion object { const val SERVICE = "com.palm.deviceprofile" }
}
