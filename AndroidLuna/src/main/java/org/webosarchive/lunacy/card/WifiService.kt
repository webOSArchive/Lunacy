package org.webosarchive.lunacy.card

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.Inet4Address

/**
 * palm://com.palm.wifi: the Wi-Fi state and its switch (codepoet 2026-11-15; Night Moves turns
 * Wi-Fi off for the night and on in the morning). `getstatus` (with its subscription) and
 * `setstate` over Android's own Wi-Fi: the switch is Android's, the same one the system menu
 * and Android's quick settings work, and the state is Android's, heard as it changes from
 * anywhere. The rest of webOS's wifi service (findnetworks, connect, the profiles) isn't here,
 * and the bus says so with "Unknown method".
 *
 * The whole service is the private bus's: an app outside com.palm.* gets "Service does not
 * exist: com.palm.wifi.", as on the TouchPad (Workbench/probe's bus probe under both ids).
 *
 * Measured on the reference TouchPad (webOS CE 3.1.0, 2026-11-15):
 * - `getstatus` while on and connected: `{"returnValue":true,"wakeOnWlan":"enabled",
 *   "status":"connectionStateChanged","networkInfo":{"profileId":777,"ssid":"Yavin",
 *   "securityType":"wpa-personal","connectState":"ipConfigured","signalBars":3,
 *   "signalLevel":-34},"ipInfo":{"interface":"eth0","ip":"192.168.10.87","subnet":
 *   "255.255.255.0","gateway":"192.168.10.1","dns1":…,"dns2":…},"apInfo":{"bssid":
 *   "94:2A:6F:7E:4C:64","channel":120,"frequency":5600}}`; while off,
 *   `{"returnValue":true,"wakeOnWlan":"enabled","status":"serviceDisabled"}`. A subscription's
 *   first answer is the same, with no "subscribed".
 * - Turning it off sends subscribers `connectionStateChanged` with "connectState":
 *   "notAssociated","lastConnectError":"DisconnectRequest" (networkInfo only, no ipInfo or
 *   apInfo), then `{"…","status":"serviceDisabled"}`. Turning it on sends `serviceEnabled`, then
 *   `connectionStateChanged` for "associating" (networkInfo only), "associated" (with apInfo)
 *   and "ipConfigured" (with ipInfo and apInfo), then `{"returnValue":true,"status":
 *   "signalStrengthChanged","signalBars":3,"signalLevel":-20}` (no wakeOnWlan). A minute
 *   connected after that sent no signal change at all, so Lunacy tells a change of bars only
 *   (Android reports the level every few seconds).
 * - `setstate {"state":"enabled"|"disabled"}` answers `{"returnValue":true}`; the state it
 *   already has, `{"returnValue":false,"errorCode":15,"errorText":"AlreadyEnabled"}` (16,
 *   "AlreadyDisabled"); another string, errorCode 9 "BadArgument"; no state, or one that isn't
 *   a string, errorCode 11 "BadJsonString".
 *
 * Android's differences, each the truth rather than a copy: the interface is Android's
 * ("wlan0"); the network's name is Android's to give only with location access, so ssid is
 * left out without it; profileId is Android's network id. An open network's securityType
 * wasn't measured, and is left out. In airplane mode Android lets only its own settings switch
 * Wi-Fi, so setstate answers an error saying so (the Fold3, 2026-11-15).
 */
@Suppress("DEPRECATION")
class WifiService(context: Context) {
    private val app: Context = context.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager?
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val watchers = LinkedHashSet<Bus.Call>()

    /** What subscribers were last told, so a repeat of the same state sends nothing. */
    private var lastSent = ""
    private var lastEnabled = wifi?.isWifiEnabled == true
    private var lastConnect = ""
    private var lastBars = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> enabledChanged()
                WifiManager.NETWORK_STATE_CHANGED_ACTION ->
                    connectionChanged(i.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO))
                WifiManager.RSSI_CHANGED_ACTION -> signalChanged()
            }
        }
    }

    init {
        if (wifi != null) {
            if (lastEnabled) lastConnect = connectState(currentInfo())
            app.registerReceiver(receiver, IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
            })
        }
    }

    /** The shell is going: the receiver goes too. */
    fun close() { if (wifi != null) runCatching { app.unregisterReceiver(receiver) } }

    fun register(bus: Bus) {
        // A device with no Wi-Fi has no wifi service.
        if (wifi == null) return
        bus.registerPrivate(SERVICE, "getstatus", wholeService = true, Bus.CallHandler { getstatus(it) })
        bus.registerPrivate(SERVICE, "setstate", wholeService = true, Bus.CallHandler { setstate(it) })
    }

    private fun getstatus(c: Bus.Call) {
        c.reply(status().toString())
        if (c.subscribe && !c.cancelled) { watchers += c; c.onCancel { watchers.remove(c) } }
    }

    private fun setstate(c: Bus.Call) {
        val w = wifi ?: return c.reply(Bus.noService(SERVICE))
        val state = c.params.opt("state") as? String ?: return c.reply(Bus.error("BadJsonString", 11))
        val on = when (state) {
            "enabled" -> true
            "disabled" -> false
            else -> return c.reply(Bus.error("BadArgument", 9))
        }
        if (w.isWifiEnabled == on) return c.reply(if (on) Bus.error("AlreadyEnabled", 15) else Bus.error("AlreadyDisabled", 16))
        // Android keeps this from apps targeting 29 and above; Lunacy targets 24 and 28. It can
        // still refuse (airplane mode on some devices): an error then, never a success.
        if (!w.setWifiEnabled(on)) {
            val airplane = android.provider.Settings.Global.getInt(app.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0
            return c.reply(Bus.error(if (airplane) "Android lets only its own settings switch Wi-Fi in airplane mode"
                else "Android wouldn't switch Wi-Fi ${if (on) "on" else "off"}"))
        }
        c.reply(Bus.ok())
    }

    // ---- what Android says, in webOS's words ----

    private fun base() = JSONObject().put("returnValue", true).put("wakeOnWlan", "enabled")

    /** getstatus's answer now. */
    private fun status(): JSONObject {
        val w = wifi ?: return base().put("status", "serviceDisabled")
        if (!w.isWifiEnabled) return base().put("status", "serviceDisabled")
        val connect = connectState(currentInfo())
        if (connect.isEmpty() || connect == "notAssociated") return base().put("status", "serviceEnabled")
        return connection(connect, null)
    }

    private fun currentInfo(): NetworkInfo? = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)

    /** webOS's connectState for Android's detailed state; "" when there is nothing to tell. */
    private fun connectState(info: NetworkInfo?): String = when (info?.detailedState) {
        NetworkInfo.DetailedState.CONNECTING, NetworkInfo.DetailedState.AUTHENTICATING,
        NetworkInfo.DetailedState.SCANNING -> "associating"
        NetworkInfo.DetailedState.OBTAINING_IPADDR -> "associated"
        NetworkInfo.DetailedState.CONNECTED -> "ipConfigured"
        NetworkInfo.DetailedState.FAILED -> "associationFailed"
        NetworkInfo.DetailedState.DISCONNECTED -> "notAssociated"
        else -> ""
    }

    /** A connectionStateChanged answer: networkInfo always, apInfo once associated, ipInfo once configured. */
    private fun connection(connect: String, lastError: String?): JSONObject {
        val w = wifi!!
        val ci = w.connectionInfo
        val net = JSONObject()
        if (ci != null && ci.networkId != -1) net.put("profileId", ci.networkId)
        val ssid = ci?.ssid?.removeSurrounding("\"").orEmpty()
        if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
            net.put("ssid", ssid)
            securityType(ssid)?.let { net.put("securityType", it) }
        }
        net.put("connectState", connect)
        if (lastError != null) net.put("lastConnectError", lastError)
        // Android's -127 is no reading at all (disconnected, or still associating): left out.
        if (ci != null && ci.rssi > NO_RSSI) net.put("signalBars", bars(ci.rssi)).put("signalLevel", ci.rssi)
        val j = base().put("status", "connectionStateChanged").put("networkInfo", net)
        if (connect == "ipConfigured") ipInfo()?.let { j.put("ipInfo", it) }
        if ((connect == "associated" || connect == "ipConfigured") && ci != null) {
            val bssid = ci.bssid
            if (!bssid.isNullOrEmpty() && bssid != "02:00:00:00:00:00") {
                j.put("apInfo", JSONObject().put("bssid", bssid.uppercase()).put("channel", channel(ci.frequency)).put("frequency", ci.frequency))
            }
        }
        return j
    }

    /** webOS's three bars from the signal, as the system menu counts them. */
    private fun bars(rssi: Int) = WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)

    private fun channel(mhz: Int) = when {
        mhz == 2484 -> 14
        mhz in 2412..2472 -> (mhz - 2407) / 5
        mhz in 5000..5900 -> (mhz - 5000) / 5
        mhz in 5925..7125 -> (mhz - 5950) / 5
        else -> 0
    }

    /** The connected network's security, from the access points around ("wpa-personal", "wep", "enterprise"). */
    private fun securityType(ssid: String): String? {
        val caps = try { wifi?.scanResults?.firstOrNull { it.SSID == ssid }?.capabilities } catch (e: SecurityException) { null }
            ?: return null
        return when {
            caps.contains("EAP") -> "enterprise"
            caps.contains("PSK") || caps.contains("SAE") -> "wpa-personal"
            caps.contains("WEP") -> "wep"
            else -> null
        }
    }

    /** The interface, address, subnet, gateway and name servers of the Wi-Fi network. */
    private fun ipInfo(): JSONObject? {
        val network = cm.allNetworks.firstOrNull { cm.getNetworkInfo(it)?.type == ConnectivityManager.TYPE_WIFI } ?: return null
        val lp = cm.getLinkProperties(network) ?: return null
        val addr = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val j = JSONObject()
        lp.interfaceName?.let { j.put("interface", it) }
        j.put("ip", addr.address.hostAddress)
        j.put("subnet", mask(addr.prefixLength))
        lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.let { j.put("gateway", it.gateway!!.hostAddress) }
        lp.dnsServers.filterIsInstance<Inet4Address>().take(2).forEachIndexed { n, a -> j.put("dns${n + 1}", a.hostAddress) }
        return j
    }

    private fun mask(prefix: Int): String {
        val m = if (prefix <= 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return "${m shr 24 and 255}.${m shr 16 and 255}.${m shr 8 and 255}.${m and 255}"
    }

    // ---- subscribers ----

    private fun send(j: JSONObject) {
        val s = j.toString()
        if (s == lastSent) return
        lastSent = s
        watchers.toList().forEach { it.reply(s) }
    }

    private fun enabledChanged() {
        val w = wifi ?: return
        val state = w.wifiState
        val on = when (state) {
            WifiManager.WIFI_STATE_ENABLED -> true
            WifiManager.WIFI_STATE_DISABLED -> false
            else -> return  // enabling or disabling: webOS told the end, not the way there
        }
        if (on == lastEnabled) return
        lastEnabled = on
        if (!on) {
            // The TouchPad said the connection went before the service did.
            if (lastConnect.isNotEmpty() && lastConnect != "notAssociated") send(connection("notAssociated", "DisconnectRequest").also { it.remove("ipInfo"); it.remove("apInfo") })
            lastConnect = ""; lastBars = -1
            send(base().put("status", "serviceDisabled"))
        } else {
            send(base().put("status", "serviceEnabled"))
        }
    }

    private fun connectionChanged(info: NetworkInfo?) {
        if (wifi?.isWifiEnabled != true) return
        val connect = connectState(info)
        if (connect.isEmpty() || connect == lastConnect) return
        // Android says DISCONNECTED as Wi-Fi turns on, before anything was ever connected.
        if (connect == "notAssociated" && lastConnect.isEmpty()) return
        lastConnect = connect
        send(connection(connect, null))
    }

    private fun signalChanged() {
        if (lastConnect != "ipConfigured") return
        val ci = wifi?.connectionInfo ?: return
        if (ci.rssi <= NO_RSSI) return
        // Android tells every few seconds; the TouchPad sent none in a minute connected, and one
        // just after joining: a change of bars is told, a wobble within them isn't.
        val b = bars(ci.rssi)
        if (b == lastBars) return
        lastBars = b
        // The TouchPad's own shape: no wakeOnWlan here.
        send(JSONObject().put("returnValue", true).put("status", "signalStrengthChanged").put("signalBars", b).put("signalLevel", ci.rssi))
    }

    companion object {
        const val SERVICE = "com.palm.wifi"
        /** WifiInfo's INVALID_RSSI. */
        private const val NO_RSSI = -127
    }
}
