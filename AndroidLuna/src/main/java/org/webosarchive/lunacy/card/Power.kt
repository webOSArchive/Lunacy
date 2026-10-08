package org.webosarchive.lunacy.card

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * What powerd did for webOS, on Android's terms: timers on the wall clock that may wake a
 * sleeping device, and locks that keep the CPU up. Its clients are the activity manager
 * (Open webOS's PowerdScheduler kept one powerd timeout, key "com.palm.activitymanager.wakeup",
 * for the earliest schedule - seen re-armed in the reference TouchPad's log) and
 * com.palm.power/timeout ([PowerService]).
 *
 * - **Timers.** Each client names the next time it needs ([at]), and whether that time must
 *   wake the device. Lunacy keeps two alarms, the earliest waking one (exact RTC_WAKEUP,
 *   allowed while idle, so Doze defers it no more than it must) and the earliest of the rest
 *   (RTC, delivered when the device is next awake). When one goes off, or the device comes
 *   back from sleep ([SleepWake]), every client is asked to run what has fallen due
 *   ([onDue]): a time that passed while nothing could run is late, never lost, as powerd fired
 *   a timeout set in the past at once (measured).
 * - **Locks.** An activity of type "power" holds one while it runs, as PowerdPowerActivity
 *   held powerd's: 900 s at most (it wasn't renewed), released when it ends, or 12 s later for
 *   "powerDebounce". After a timer fires the device is held for 5 s, as powerd held it
 *   ("com.palm.power.timeout_fired for 5000ms", measured on the reference TouchPad), so what
 *   fell due can start and take its own lock; Android holds it only while the alarm's
 *   broadcast is delivered.
 *
 * Main thread throughout.
 */
class Power(private val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val lock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lunacy:power").apply { setReferenceCounted(false) }
    /** The power activities running, by id. */
    private val held = HashSet<Int>()
    /** Short holds still running: debounces and the grace after a timer. */
    private var holding = 0

    private class Need(val at: Long, val wake: Boolean)
    private val needs = HashMap<String, Need>()
    private val clients = ArrayList<() -> Unit>()

    private fun intent(action: String) = PendingIntent.getBroadcast(app, 0, Intent(action).setPackage(app.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    private val wakeIntent by lazy { intent(WAKEUP) }
    private val lateIntent by lazy { intent(LATE) }

    fun register() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { hold(GRACE_MS); due() }
        }
        val filter = IntentFilter(WAKEUP).apply { addAction(LATE) }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(receiver, filter)
    }

    /** [f] runs whatever of its own has fallen due; it is called on every timer and resume. */
    fun onDue(f: () -> Unit) { clients += f }

    /** Every client runs what has fallen due: a timer went off, or the device came back. */
    fun due() = clients.toList().forEach { it() }

    /**
     * A time is a few seconds off ([EARLY_MS]): rather than let the device sleep and arm an
     * alarm that Doze may hold for minutes, stay awake and sweep again exactly then.
     */
    fun soon(time: Long) {
        val wait = (time - System.currentTimeMillis()).coerceAtLeast(0)
        hold(wait + GRACE_MS)
        main.postDelayed({ due() }, wait)
    }

    /** [client]'s next time (wall-clock ms), and whether it must wake the device; null for none. */
    fun at(client: String, time: Long?, wake: Boolean = true) {
        if (time == null) needs.remove(client) else needs[client] = Need(time, wake)
        arm(wakeIntent, AlarmManager.RTC_WAKEUP, needs.values.filter { it.wake }.minOfOrNull { it.at })
        arm(lateIntent, AlarmManager.RTC, needs.values.filter { !it.wake }.minOfOrNull { it.at })
    }

    private fun arm(pi: PendingIntent, type: Int, time: Long?) {
        if (time == null) { alarms.cancel(pi); return }
        if (Build.VERSION.SDK_INT >= 23) alarms.setExactAndAllowWhileIdle(type, time, pi)
        else alarms.setExact(type, time, pi)
    }

    fun lock(id: Int) { if (held.add(id)) apply() }

    /** Locks held by name for a time (com.palm.power's activities), and when each runs out. */
    private val named = HashMap<String, Runnable>()

    /** Holds [key] for [ms], replacing any hold it already had. */
    fun lockFor(key: String, ms: Long) {
        named.remove(key)?.let { main.removeCallbacks(it) }
        val end = Runnable { named.remove(key); apply() }
        named[key] = end
        main.postDelayed(end, ms)
        apply()
    }

    fun unlock(key: String) { named.remove(key)?.let { main.removeCallbacks(it); apply() } }

    fun unlock(id: Int, debounce: Boolean) {
        if (!held.remove(id)) return
        if (debounce) hold(DEBOUNCE_MS) else apply()
    }

    /** Keeps the CPU up for [ms]. */
    fun hold(ms: Long) {
        holding++
        apply()
        main.postDelayed({ holding--; apply() }, ms)
    }

    private fun apply() {
        if (held.isNotEmpty() || holding > 0 || named.isNotEmpty()) lock.acquire(LOCK_MS)
        else if (lock.isHeld) lock.release()
    }

    companion object {
        private const val WAKEUP = "org.webosarchive.lunacy.POWER_WAKEUP"
        private const val LATE = "org.webosarchive.lunacy.POWER_LATE"
        private const val LOCK_MS = 900_000L
        private const val DEBOUNCE_MS = 12_000L
        const val GRACE_MS = 5_000L
        /**
         * How near a time must be for [soon] to wait for it awake. Android delivers an alarm
         * early when it batches it with another (a non-waking one went off 1.6 s early beside
         * a waking one, Galaxy Tab A7 Lite in Doze); a sweep that then found nothing due
         * re-armed, and in Doze the next delivery was a minute away.
         */
        const val EARLY_MS = 5_000L
    }
}
