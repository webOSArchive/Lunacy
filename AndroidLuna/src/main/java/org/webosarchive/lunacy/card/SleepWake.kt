package org.webosarchive.lunacy.card

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Coming back from the dead, system-wide. A TouchPad suspended whenever its screen was off
 * and nothing held it awake, and woke for its timers; webOS's services were written for that:
 * a timer that fell due fires, and a connection that dropped is reported down and up again,
 * and whoever was waiting picks up. Android's sleep is harder - Doze defers alarms, ignores
 * wake locks and cuts an app off the network, and the cached-app freezer stops a process
 * outright - and it tells an app little: nothing when it is unfrozen or the CPU resumes.
 *
 * So Lunacy treats every sign of having been away as a webOS resume, and answers each the
 * same way: everything that has fallen due runs ([Power.due]: the activity manager's
 * schedules, com.palm.power timeouts), late but not lost. The signs:
 *
 * - **The network.** Android blocks an app's network in Doze without a broadcast; the default
 *   network's callback says so (onBlockedStatusChanged, API 29), and below that, Doze itself
 *   (ACTION_DEVICE_IDLE_MODE_CHANGED, API 23) stands in. [blocked] makes the connection
 *   manager report the network down while it is blocked and up when it is back, which is what
 *   the mail services, Synergy's sync and every requirement on the activity manager already
 *   listen for. Unblocked is a resume.
 * - **Doze ending**, the same broadcast.
 * - **Lost time.** A heartbeat on the uptime clock compares it with the realtime one: time the
 *   CPU spent suspended shows as a gap between them, and time the process spent frozen as a
 *   heartbeat that came very late. Either, past a threshold, is a resume.
 * - **The shell coming to the front** ([check]).
 *
 * A resume with nothing due does nothing, so the signs may overlap freely.
 * See Docs/sleep-and-wake.md.
 *
 * Main thread throughout.
 */
class SleepWake(context: Context, private val power: Power) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Whether Android is keeping Lunacy off the network to save power (Doze, app standby). */
    var blocked = false
        private set
    /** The network was blocked or unblocked: the connection manager tells its subscribers. */
    var onBlockedChanged: () -> Unit = {}
    /** The same, for the activity manager ([ActivityManager.networkBlocked]). */
    var onBlocked: (Boolean) -> Unit = {}

    private var lastRealtime = SystemClock.elapsedRealtime()
    private var lastUptime = SystemClock.uptimeMillis()
    private var lastResume = 0L

    fun start() {
        if (Build.VERSION.SDK_INT >= 23) {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val idle = pm.isDeviceIdleMode
                    Log.i(AppServer.TAG, "sleep: doze ${if (idle) "on" else "off"}")
                    if (Build.VERSION.SDK_INT < 29) setBlocked(idle && !pm.isIgnoringBatteryOptimizations(app.packageName))
                    if (!idle) resume("doze ended")
                }
            }, IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
        }
        if (Build.VERSION.SDK_INT >= 29) {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) { main.post { setBlocked(blocked) } }
            })
        }
        main.postDelayed(::heartbeat, BEAT_MS)
    }

    /** Looks for lost time now: the shell came to the front, say. */
    fun check(why: String) {
        val realtime = SystemClock.elapsedRealtime(); val uptime = SystemClock.uptimeMillis()
        val asleep = (realtime - lastRealtime) - (uptime - lastUptime)
        lastRealtime = realtime; lastUptime = uptime
        if (asleep > LOST_MS) resume("$why, ${asleep / 1000} s asleep")
    }

    private fun heartbeat() {
        val late = SystemClock.uptimeMillis() - lastUptime - BEAT_MS
        check("heartbeat")
        if (late > LOST_MS) resume("${late / 1000} s frozen")
        main.postDelayed(::heartbeat, BEAT_MS)
    }

    private fun setBlocked(b: Boolean) {
        if (b == blocked) return
        blocked = b
        Log.i(AppServer.TAG, "sleep: network ${if (b) "blocked" else "unblocked"}")
        onBlockedChanged()
        onBlocked(b)
        if (!b) resume("network unblocked")
    }

    /** Everything due runs. Signs of one return arrive in bursts; one second covers them. */
    private fun resume(why: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastResume < 1000) return
        lastResume = now
        Log.i(AppServer.TAG, "sleep: resume ($why)")
        power.due()
    }

    companion object {
        private const val BEAT_MS = 60_000L
        private const val LOST_MS = 30_000L
    }
}
