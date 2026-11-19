package org.webosarchive.lunacy.card

import org.json.JSONObject
import org.webosarchive.lunacy.core.NetworkSource

/**
 * palm://com.palm.connectionmanager/getstatus (also getStatus, as the TouchPad accepts both),
 * backed by the host's connectivity ([NetworkSource]: Android's, or the desktop's interfaces).
 * The reply has the reference TouchPad's shape; a subscription gets the status at once and again
 * whenever it changes.
 *
 * While Android keeps Lunacy off the network to save power (Doze, see SleepWake), its source
 * reports no network, and the network is reported down, and up again when it is back. A TouchPad
 * kept its Wi-Fi up through a suspend, but its connections survived that; an app's don't survive
 * Doze, and the down and up is the signal webOS's services already reconnect on (luna-deltas A18).
 */
class ConnectionManager(private val net: NetworkSource) {
    private val subscribers = LinkedHashSet<Bus.Call>()  // main thread
    private var last = ""
    private var watching = false

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
        // A cellular data connection for a service ("proxy" is App Catalog's). Lunacy's device
        // has no modem, and a wifi TouchPad answers exactly this (measured).
        bus.register(SERVICE, "connectCellularDataService") { _, _, reply -> reply(Bus.error("APN Not found in CarrierDB")) }
    }

    private fun startWatching() {
        if (watching) return
        watching = true
        net.watch { changed() }
    }

    /** Tells subscribers the status, if it has changed. */
    fun changed() {
        if (subscribers.isEmpty()) return
        val s = status()
        if (s == last) return
        last = s
        subscribers.toList().forEach { it.reply(s) }
    }

    /** The shell is gone: no more changes for this one. */
    fun stop() = stopWatching()

    private fun stopWatching() {
        if (watching) net.unwatch()
        watching = false
    }

    fun status(): String {
        val active = net.current()
        val online = active != null
        val down = JSONObject().put("state", "disconnected")
        val w = active?.wifi
        val wifi = if (w != null) {
            JSONObject().put("state", "connected")
                .put("ipAddress", w.ipAddress)
                // The TouchPad's Wi-Fi interface is eth0, and an app that shows it should see
                // what a device showed. Android's own name for it is wlan0.
                .put("interfaceName", "eth0")
                .put("ssid", w.ssid)
                .put("bssid", w.bssid)
                .put("networkConfidenceLevel", when (w.level) { 2 -> "excellent"; 1 -> "fair"; else -> "poor" })
                .put("onInternet", "yes")
                // The reference TouchPad answers true (measured 2026-10-07), and Android keeps
                // Wi-Fi connected while the screen is off, so it is true here too. The mail
                // services hold an IMAP IDLE (push) only over an interface that says so.
                .put("isWakeOnWifiEnabled", true)
        } else down
        val wan = if (active?.mobile == true)
            JSONObject().put("state", "connected").put("onInternet", "yes") else JSONObject().put("state", "disconnected")
        return JSONObject().put("returnValue", true).put("isInternetConnectionAvailable", online)
            .put("wifi", wifi).put("wan", wan)
            .put("vpn", JSONObject().put("state", "disconnected")).put("bridge", JSONObject().put("state", "disconnected"))
            .toString()
    }

    companion object { const val SERVICE = "com.palm.connectionmanager" }
}
