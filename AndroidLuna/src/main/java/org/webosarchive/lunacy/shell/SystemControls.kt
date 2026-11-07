package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import org.webosarchive.lunacy.card.AppServer

/**
 * What the system menu shows and does, from Android's own state: LunaSysMgr's SystemMenu.cpp
 * and the services it talked to (wifi, bluetooth, the airplane-mode, rotation-lock and mute
 * preferences). Each setting is Android's, not a copy: the menu reads it and writes it, and
 * hears it change from anywhere (Android's quick settings included).
 *
 * Where Android keeps a setting from apps the menu hands over to Android's screen for it, as
 * webOS handed over to its preferences app (Docs/luna-deltas.md B3): airplane mode always,
 * a Wi-Fi network that needs a password, a Bluetooth device to connect.
 */
@Suppress("DEPRECATION")
class SystemControls(
    private val activity: Activity,
    private val askPermissions: (List<String>, (granted: Boolean, askAgain: Boolean) -> Unit) -> Unit,
    /** Opens one of Android's settings screens by LunacyService's panel name. */
    private val openPanel: (String) -> Unit,
    /** Writes a Settings.System value, asking for WRITE_SETTINGS as the brightness slider does. */
    private val writeSystemSetting: (String, Int) -> Boolean,
) {
    /** Anything the menu shows changed. */
    var onChange: () -> Unit = {}

    private val context: Context get() = activity
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
    private val bt: BluetoothAdapter? = try { BluetoothAdapter.getDefaultAdapter() } catch (e: Exception) { null }
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---- Wi-Fi (SystemMenu::slotWifiStateChanged, WiFiElement.qml) ----

    /** One network in the list: WiFiElement's model row. */
    data class Network(
        val ssid: String, val bars: Int, val secured: Boolean, val netId: Int,
        /** "", "connecting", "associated", "ipConfigured", "ipFailed", "associationFailed". */
        val connStatus: String,
    ) {
        val connected get() = connStatus == "ipConfigured"
    }

    val wifiAvailable get() = wifi != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
    val wifiOn get() = wifi?.isWifiEnabled == true
    var wifiSsid = ""; private set
    /** webOS's connection state for [wifiSsid]: "associating", "associated", "ipConfigured" or "". */
    var wifiConnState = ""; private set
    var networks: List<Network> = emptyList(); private set
    /** The list is being fetched: the drawer's spinner. */
    var scanning = false; private set
    /** A network the user picked, until it connects or fails. */
    private var joining = ""

    fun setWifi(on: Boolean) {
        if (wifi == null) return
        // Android keeps this from apps targeting 29 and above; Lunacy targets 24 and 28.
        if (!wifi.setWifiEnabled(on)) { Log.w(AppServer.TAG, "systemMenu: Android refused Wi-Fi on=$on"); openPanel("wifi") }
        if (!on) { networks = emptyList(); scanning = false; closeOnConnect = false }
        else { scanning = true; closeOnConnect = true }
        onChange()
    }

    private val locationPermissions = org.webosarchive.lunacy.card.LunacyService.LOCATION
    private val locationEnough = org.webosarchive.lunacy.card.LunacyService.LOCATION_ANY

    /**
     * The networks around: the drawer opening asks for them, as SystemMenu::slotWifiMenuOpened
     * did. Android only names them to an app with location access. First Use explains that and
     * asks; an install that never went through that page is asked here, once ever, and after a
     * refusal the list stays empty until location is allowed in Android's settings for Lunacy.
     */
    fun scan() {
        if (!wifiOn) return
        val granted = locationEnough.all { context.checkSelfPermissionCompat(it) }
        if (!granted) {
            if (org.webosarchive.lunacy.card.LunacyService.locationAsked(context)) return
            org.webosarchive.lunacy.card.LunacyService.setLocationAsked(context)
            scanning = true; onChange()
            askPermissions(locationPermissions) { _, _ -> if (locationEnough.all { context.checkSelfPermissionCompat(it) }) scan() else { scanning = false; onChange() } }
            return
        }
        scanning = true
        readNetworks()
        if (!wifi!!.startScan()) { scanning = false }
        onChange()
    }

    fun stopScanning() { scanning = false; joining = ""; closeOnConnect = false }

    /** WiFiElement's coloseOnConnect: Wi-Fi turned on, or a network picked, from the menu. */
    private var closeOnConnect = false

    private fun Context.checkSelfPermissionCompat(p: String) =
        android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun readNetworks() {
        val w = wifi ?: return
        if (!wifiOn) { networks = emptyList(); return }
        // The network's name is Android's to give only once location access is allowed.
        if (wifiSsid.isEmpty()) readWifiConnection(null)
        val results: List<ScanResult> = try { w.scanResults ?: emptyList() } catch (e: SecurityException) { emptyList() }
        val saved: Map<String, Int> = try {
            (w.configuredNetworks ?: emptyList()).associate { unquote(it.SSID) to it.networkId }
        } catch (e: SecurityException) { emptyMap() }
        // Strongest first, one row per name (an SSID on two bands is one network to a person).
        val byName = LinkedHashMap<String, ScanResult>()
        for (r in results.sortedByDescending { it.level }) {
            if (r.SSID.isNullOrEmpty()) continue
            if (r.SSID !in byName) byName[r.SSID] = r
        }
        val list = byName.values.map { r ->
            val status = when {
                r.SSID == wifiSsid -> statusOf(wifiConnState)
                r.SSID == joining -> "connecting"
                else -> ""
            }
            Network(r.SSID, WifiManager.calculateSignalLevel(r.level, 4).coerceIn(0, 3), secured(r), saved[r.SSID] ?: 0, status)
        }
        // WiFiElement moves the connected network to the top.
        networks = list.sortedBy { if (it.connStatus.isNotEmpty()) 0 else 1 }
    }

    private fun statusOf(state: String) = when (state) {
        "associating" -> "connecting"
        else -> state
    }

    private fun secured(r: ScanResult) = r.capabilities.let { it.contains("WEP") || it.contains("PSK") || it.contains("EAP") || it.contains("SAE") }

    /**
     * SystemMenu::slotWifiNetworkSelected: the network already up (or failed) opens the Wi-Fi
     * preferences; a known one, or an open one, is joined in place; a secured one Android has
     * no password for opens Android's Wi-Fi screen to ask for it, as webOS opened its own.
     * Returns whether the menu should close now.
     */
    fun join(n: Network): Boolean {
        val w = wifi ?: return true
        when (n.connStatus) {
            "connecting" -> return false
            "ipConfigured", "associated", "ipFailed", "associationFailed" -> { openPanel("wifi"); return true }
        }
        var netId = n.netId
        if (netId == 0 && !n.secured) {
            netId = w.addNetwork(WifiConfiguration().apply {
                SSID = "\"" + n.ssid + "\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            })
        }
        if (netId <= 0) { openPanel("wifi"); return true }
        joining = n.ssid; closeOnConnect = true
        if (!w.enableNetwork(netId, true)) { joining = ""; openPanel("wifi"); return true }
        w.reconnect()
        networks = networks.map { if (it.ssid == n.ssid) it.copy(connStatus = "connecting") else it.copy(connStatus = "") }
        onChange()
        return false
    }

    @SuppressLint("MissingPermission")
    private fun readWifiConnection(info: NetworkInfo?) {
        val w = wifi ?: return
        val ssid = unquote(w.connectionInfo?.ssid ?: "").let { if (it == "<unknown ssid>") "" else it }
        val state = when (info?.detailedState) {
            NetworkInfo.DetailedState.CONNECTING, NetworkInfo.DetailedState.AUTHENTICATING -> "associating"
            NetworkInfo.DetailedState.OBTAINING_IPADDR -> "associated"
            NetworkInfo.DetailedState.CONNECTED -> "ipConfigured"
            NetworkInfo.DetailedState.FAILED -> "associationFailed"
            null -> if (ssid.isNotEmpty() && w.connectionInfo?.networkId != -1) "ipConfigured" else ""
            else -> ""
        }
        wifiSsid = ssid; wifiConnState = if (ssid.isEmpty()) "" else state
        if (state == "ipConfigured" && closeOnConnect && (joining.isEmpty() || ssid == joining)) { joining = ""; closeOnConnect = false; onConnected() }
        if (state == "associationFailed" && ssid == joining) joining = ""
    }

    /** A network the menu joined came up: WiFiElement closes the menu a second later. */
    var onConnected: () -> Unit = {}

    private fun unquote(s: String) = s.removeSurrounding("\"")

    /** The drawer header's state: the network's name while one is up, else ON or OFF. */
    fun wifiHeaderText() = when {
        !wifiOn -> "OFF"
        wifiSsid.isNotEmpty() && wifiConnState in setOf("associating", "associated", "ipConfigured") -> wifiSsid
        else -> "ON"
    }

    // ---- Bluetooth (SystemMenu::slotBluetoothPowerStateChanged, BluetoothElement.qml) ----

    data class Device(val name: String, val address: String, val connected: Boolean)

    val bluetoothAvailable get() = bt != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    val bluetoothOn get() = bt?.state == BluetoothAdapter.STATE_ON
    val bluetoothTurningOn get() = bt?.state == BluetoothAdapter.STATE_TURNING_ON
    var devices: List<Device> = emptyList(); private set
    private val connectedAddresses = HashSet<String>()

    @SuppressLint("MissingPermission")
    fun setBluetooth(on: Boolean) {
        val a = bt ?: return
        val ok = try { if (on) a.enable() else a.disable() } catch (e: SecurityException) { false }
        if (!ok) { Log.w(AppServer.TAG, "systemMenu: Android refused Bluetooth on=$on"); openPanel("bluetooth") }
        onChange()
    }

    /** The paired devices, with which are connected now (A2DP, headset, input). */
    @SuppressLint("MissingPermission")
    fun loadDevices() {
        val a = bt ?: return
        if (!bluetoothOn) { devices = emptyList(); onChange(); return }
        readDevices()
        for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, INPUT_DEVICE)) {
            try {
                a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        try { proxy.connectedDevices.forEach { connectedAddresses += it.address } } catch (e: Exception) {}
                        a.closeProfileProxy(p, proxy)
                        activity.runOnUiThread { readDevices(); onChange() }
                    }
                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
            } catch (e: Exception) { }
        }
        onChange()
    }

    @SuppressLint("MissingPermission")
    private fun readDevices() {
        val bonded = try { bt?.bondedDevices ?: emptySet() } catch (e: SecurityException) { emptySet<BluetoothDevice>() }
        devices = bonded.map { Device(it.name ?: it.address, it.address, it.address in connectedAddresses) }
            .sortedBy { if (it.connected) 0 else 1 }
    }

    fun bluetoothHeaderText() = when {
        !bluetoothOn -> "OFF"
        else -> devices.firstOrNull { it.connected }?.name ?: "ON"
    }

    // ---- the three toggles ----

    val airplaneOn get() = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    /** Wi-Fi Preferences and Bluetooth Preferences: webOS's com.palm.app.wifi and .bluetooth, here Android's screens. */
    fun openWifiPrefs() = openPanel("wifi")
    fun openBluetoothPrefs() = openPanel("bluetooth")

    /** Android keeps airplane mode from every app; its own screen sets it. */
    fun openAirplane() = openPanel("airplane")

    val rotationLocked get() = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    /**
     * SystemMenu::slotRotationLockTriggered: locking holds the screen the way it is turned
     * now (Android's USER_ROTATION), unlocking lets it follow the sensor again.
     */
    fun toggleRotationLock() {
        if (rotationLocked) {
            writeSystemSetting(Settings.System.ACCELEROMETER_ROTATION, 1)
        } else {
            val rotation = activity.windowManager.defaultDisplay.rotation
            if (writeSystemSetting(Settings.System.USER_ROTATION, rotation)) writeSystemSetting(Settings.System.ACCELEROMETER_ROTATION, 0)
        }
        onChange()
    }

    /** webOS's mute was the ringer switch, as the status bar's icon reads it. */
    val muted get() = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL

    /**
     * Mute Sound: the ringer to vibrate where there is a vibrator, else to silent. Silent is
     * Do Not Disturb's from Android 7, which is the user's to allow Lunacy on Android's own
     * screen; that screen opens the first time it is needed.
     */
    fun toggleMute() {
        val want = if (muted) AudioManager.RINGER_MODE_NORMAL
            else if ((context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator?)?.hasVibrator() == true) AudioManager.RINGER_MODE_VIBRATE
            else AudioManager.RINGER_MODE_SILENT
        try {
            audio.ringerMode = want
        } catch (e: SecurityException) {
            Log.w(AppServer.TAG, "systemMenu: ringer mode $want needs Do Not Disturb access")
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e2: Exception) { Log.w(AppServer.TAG, "systemMenu: no Do Not Disturb access screen", e2) }
            }
        }
        onChange()
    }

    // ---- listening ----

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    if (!wifiOn) { networks = emptyList(); scanning = false }
                    readWifiConnection(null)
                    if (wifiOn && drawerWifiOpen) scan()
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    readWifiConnection(i.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO))
                    if (drawerWifiOpen) readNetworks()
                }
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> { scanning = false; if (drawerWifiOpen) readNetworks() }
                BluetoothAdapter.ACTION_STATE_CHANGED -> { if (bluetoothOn) loadDevices() else { devices = emptyList(); connectedAddresses.clear() } }
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val d: BluetoothDevice? = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null) { if (i.action == BluetoothDevice.ACTION_ACL_CONNECTED) connectedAddresses += d.address else connectedAddresses -= d.address }
                    readDevices()
                }
            }
            onChange()
        }
    }

    /** Whether the Wi-Fi drawer is open: only then is the list kept fresh. */
    var drawerWifiOpen = false

    private var listening = false
    fun start() {
        if (listening) return
        listening = true
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION); addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION); addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        readWifiConnection(null)
        if (bluetoothOn) loadDevices()
    }

    fun stop() {
        if (!listening) return
        listening = false
        try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
    }

    companion object {
        /** BluetoothProfile.HID_HOST, public only from Android 9 (as INPUT_DEVICE before it, hidden). */
        private const val INPUT_DEVICE = 4
    }
}
