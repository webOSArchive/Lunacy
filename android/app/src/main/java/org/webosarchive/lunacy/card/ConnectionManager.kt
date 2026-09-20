package org.webosarchive.lunacy.card

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import org.json.JSONObject

/**
 * palm://com.palm.connectionmanager/getstatus (also getStatus, as the TouchPad accepts both),
 * backed by Android's connectivity. The reply has the reference TouchPad's shape; a
 * subscription gets the status at once and again whenever it changes.
 */
class ConnectionManager(private val context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val subscribers = LinkedHashSet<Bus.Call>()  // main thread
    private var last = ""
    private var receiver: BroadcastReceiver? = null

    fun register(bus: Bus) {
        val h = Bus.CallHandler { call ->
            val s = status()
            call.reply(s)
            if (call.subscribe && !call.cancelled) {
                subscribers += call
                call.onCancel { subscribers.remove(call); if (subscribers.isEmpty()) stopWatching() }
                last = s
                startWatching()
            }
        }
        bus.register(SERVICE, "getstatus", h)
        bus.register(SERVICE, "getStatus", h)
    }

    private fun startWatching() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val s = status()
                if (s == last) return
                last = s
                subscribers.toList().forEach { it.reply(s) }
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION); addAction(WifiManager.RSSI_CHANGED_ACTION)
        })
    }

    private fun stopWatching() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }

    @Suppress("DEPRECATION")
    fun status(): String {
        val active = cm.activeNetworkInfo
        val online = active?.isConnected == true
        val down = JSONObject().put("state", "disconnected")
        val wifi = if (active?.isConnected == true && active.type == ConnectivityManager.TYPE_WIFI) {
            val info = wm.connectionInfo
            val ip = info.ipAddress
            JSONObject().put("state", "connected")
                .put("ipAddress", "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}")
                // The TouchPad's Wi-Fi interface is eth0, and an app that shows it should see
                // what a device showed. Android's own name for it is wlan0.
                .put("interfaceName", "eth0")
                .put("ssid", info.ssid.orEmpty().removeSurrounding("\""))
                .put("bssid", info.bssid.orEmpty().uppercase())
                .put("networkConfidenceLevel", when (WifiManager.calculateSignalLevel(info.rssi, 3)) { 2 -> "excellent"; 1 -> "fair"; else -> "poor" })
                .put("onInternet", "yes")
                .put("isWakeOnWifiEnabled", false)
        } else down
        val wan = if (active?.isConnected == true && active.type == ConnectivityManager.TYPE_MOBILE)
            JSONObject().put("state", "connected").put("onInternet", "yes") else JSONObject().put("state", "disconnected")
        return JSONObject().put("returnValue", true).put("isInternetConnectionAvailable", online)
            .put("wifi", wifi).put("wan", wan)
            .put("vpn", JSONObject().put("state", "disconnected")).put("bridge", JSONObject().put("state", "disconnected"))
            .toString()
    }

    companion object { const val SERVICE = "com.palm.connectionmanager" }
}
