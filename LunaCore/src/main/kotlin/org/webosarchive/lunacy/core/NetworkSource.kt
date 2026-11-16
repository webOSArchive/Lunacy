package org.webosarchive.lunacy.core

/**
 * The host's network, as the connection manager reports it: Android's ConnectivityManager and
 * WifiManager; on the desktop, the machine's own interfaces.
 */
interface NetworkSource {
    /** The connected network, or null when there is none (or the host keeps Lunacy off it). */
    fun current(): Network?
    /** [onChange] is called, on the main thread, whenever the network may have changed. */
    fun watch(onChange: () -> Unit)
    fun unwatch()

    /** A connection: over Wi-Fi ([wifi]) or a mobile network ([mobile]); another kind is neither. */
    class Network(val wifi: Wifi?, val mobile: Boolean)

    /** [level] is the signal in three steps, 0 to 2. */
    class Wifi(val ipAddress: String, val ssid: String, val bssid: String, val level: Int)
}
