package org.webosarchive.lunacy.card

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.json.JSONObject

/**
 * palm://com.palm.btmonitor: the Bluetooth radio and its switch (codepoet 2026-11-15; Night
 * Moves turns Bluetooth off for the night and on in the morning). `monitor/getradiostate`,
 * `monitor/subscribenotifications` (the radio's part of it) and `monitor/radioon` and
 * `radiooff` over Android's own adapter: the same radio the system menu switches, heard as it
 * changes from anywhere. The rest of btmonitor (devices, pairing, the profiles) isn't here, and
 * the bus says so with "Unknown method".
 *
 * The whole service is the private bus's: an app outside com.palm.* gets "Service does not
 * exist: com.palm.btmonitor.", as luna-send -P did on the TouchPad.
 *
 * Measured on the reference TouchPad (webOS CE 3.1.0, 2026-11-15):
 * - `getradiostate {}`: `{"returnValue":true,"radio":"on"}`; "off", and "turningoff" or
 *   "turningon" between the two.
 * - `subscribenotifications {"subscribe":true}`: `{"returnValue":true,"subscribed":true,
 *   "radio":"on","bteaspolicy":2}`, then for the radio `{"notification":"notifnradioturningoff",
 *   "error":0}`, `notifnradiooff` about 2 s later, and `notifnradioturningon`, `notifnradioon`
 *   (8 s on the TouchPad).
 * - `radiooff {}` and `radioon {"visible":true,"connectable":true}` answer `{"returnValue":true}`.
 *   The state it already has first: errorCode 12, "BtMtrRadioOff: BT is already OFF" or
 *   "BtMtrRadioOn: BT is already ON". Then radioon's booleans, visible before connectable: one
 *   missing or not a boolean is errorCode 134217729, "BtMtrRadioOn: Error getting boolean -
 *   Visible" (or "- Connectable").
 *
 * Android's differences: `visible` (discoverable) is Android's to grant only through its own
 * dialog, which a schedule at night mustn't raise, so the radio comes on connectable and
 * discoverable only while Android's Bluetooth screen is open (luna-deltas C4). `bteaspolicy` is
 * Exchange's AllowBluetooth, 2 (allowed): Lunacy enforces no Bluetooth policy. In airplane mode
 * Android may refuse to switch the radio, and radioon and radiooff say so.
 */
@Suppress("DEPRECATION", "MissingPermission")
class BtMonitorService(context: Context) {
    private val app: Context = context.applicationContext
    private val bt: BluetoothAdapter? = try { BluetoothAdapter.getDefaultAdapter() } catch (e: Exception) { null }
    private val watchers = LinkedHashSet<Bus.Call>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val n = when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_TURNING_OFF -> "notifnradioturningoff"
                BluetoothAdapter.STATE_OFF -> "notifnradiooff"
                BluetoothAdapter.STATE_TURNING_ON -> "notifnradioturningon"
                BluetoothAdapter.STATE_ON -> "notifnradioon"
                else -> return
            }
            val j = JSONObject().put("notification", n).put("error", 0).toString()
            watchers.toList().forEach { it.reply(j) }
        }
    }

    init {
        if (bt != null) app.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    /** The shell is going: the receiver goes too. */
    fun close() { if (bt != null) runCatching { app.unregisterReceiver(receiver) } }

    fun register(bus: Bus) {
        // A device with no Bluetooth has no btmonitor.
        if (bt == null) return
        fun private(method: String, h: (Bus.Call) -> Unit) = bus.registerPrivate(SERVICE, method, wholeService = true, Bus.CallHandler(h))
        private("monitor/getradiostate") { it.reply(JSONObject().put("returnValue", true).put("radio", radio()).toString()) }
        private("monitor/subscribenotifications") { subscribe(it) }
        private("monitor/radioon") { radioOn(it) }
        private("monitor/radiooff") { radioOff(it) }
    }

    private fun radio() = when (bt?.state) {
        BluetoothAdapter.STATE_ON -> "on"
        BluetoothAdapter.STATE_TURNING_ON -> "turningon"
        BluetoothAdapter.STATE_TURNING_OFF -> "turningoff"
        else -> "off"
    }

    private fun subscribe(c: Bus.Call) {
        c.reply(JSONObject().put("returnValue", true).put("subscribed", c.subscribe).put("radio", radio())
            .put("bteaspolicy", EAS_ALLOW_BLUETOOTH).toString())
        if (c.subscribe && !c.cancelled) { watchers += c; c.onCancel { watchers.remove(c) } }
    }

    private fun radioOn(c: Bus.Call) {
        val b = bt ?: return c.reply(Bus.noService(SERVICE))
        if (b.state == BluetoothAdapter.STATE_ON) return c.reply(Bus.error("BtMtrRadioOn: BT is already ON", 12))
        for (k in listOf("visible" to "Visible", "connectable" to "Connectable")) {
            if (c.params.opt(k.first) !is Boolean) return c.reply(Bus.error("BtMtrRadioOn: Error getting boolean - ${k.second}", BAD_BOOLEAN))
        }
        if (!switch(b, true)) return c.reply(refused(true))
        c.reply(Bus.ok())
    }

    private fun radioOff(c: Bus.Call) {
        val b = bt ?: return c.reply(Bus.noService(SERVICE))
        if (b.state == BluetoothAdapter.STATE_OFF) return c.reply(Bus.error("BtMtrRadioOff: BT is already OFF", 12))
        if (!switch(b, false)) return c.reply(refused(false))
        c.reply(Bus.ok())
    }

    /**
     * Android's switch. Allowed to apps targeting API 32 and below (Lunacy targets 24 and 28)
     * with BLUETOOTH_ADMIN; refused in airplane mode unless Bluetooth is one of its toggleable
     * radios, and by a few vendors' builds.
     */
    private fun switch(b: BluetoothAdapter, on: Boolean): Boolean =
        try { if (on) b.enable() else b.disable() } catch (e: SecurityException) { false }

    private fun refused(on: Boolean): String {
        val airplane = android.provider.Settings.Global.getInt(app.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        return Bus.error(if (airplane) "Android lets only its own settings switch Bluetooth in airplane mode"
            else "Android wouldn't switch Bluetooth ${if (on) "on" else "off"}")
    }

    companion object {
        const val SERVICE = "com.palm.btmonitor"
        /** What the TouchPad answered for radioon's missing or wrong booleans. */
        private const val BAD_BOOLEAN = 134217729
        /** Exchange's AllowBluetooth: 2 allows it all, what the TouchPad reported with no policy. */
        private const val EAS_ALLOW_BLUETOOTH = 2
    }
}
