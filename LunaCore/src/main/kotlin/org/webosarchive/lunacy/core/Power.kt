package org.webosarchive.lunacy.core

/**
 * The host's battery, as the activity manager's requirements see it ("charging", "docked",
 * "battery"): Android's battery broadcasts; a desktop on the mains.
 */
interface BatteryWatch {
    /**
     * Starts telling [onChange] the charge in percent (-1 if unknown) and the charger as webOS
     * named it ("usb", "inductive", "puck" for a dock) or null on battery; once at once.
     */
    fun start(onChange: (percent: Int, charger: String?) -> Unit)
    fun stop()
}

/**
 * Timers that hold through sleep, and the CPU kept up while work runs: what the activity manager
 * asked powerd for on webOS. Android answers with alarms and a wake lock (Power); the desktop,
 * which doesn't sleep under Luna DE's feet, with plain timers.
 */
interface PowerTimers {
    /** [f] runs whatever of its own has fallen due; it is called on every timer and resume. */
    fun onDue(f: () -> Unit)
    /** A time is a few seconds off ([EARLY_MS]): stay awake and sweep again exactly then. */
    fun soon(time: Long)
    /** [client]'s next time (wall-clock ms), and whether it must wake the device; null for none. */
    fun at(client: String, time: Long?, wake: Boolean = true)
    /** The CPU kept up for the activity [id] until [unlock]. */
    fun lock(id: Int)
    fun unlock(id: Int, debounce: Boolean)
    /** Holds [key] for [ms], replacing any hold it already had (com.palm.power's activities). */
    fun lockFor(key: String, ms: Long)
    fun unlock(key: String)
    /** Keeps the CPU up for [ms] (a timeout that has just fired). */
    fun hold(ms: Long)

    companion object {
        /**
         * How near a time must be for [soon] to wait for it awake. Android delivers an alarm
         * early when it batches it with another (a non-waking one went off 1.6 s early beside
         * a waking one, Galaxy Tab A7 Lite in Doze); a sweep that then found nothing due
         * re-armed, and in Doze the next delivery was a minute away.
         */
        const val EARLY_MS = 5_000L
        /** How long the device is held awake for a timer that fired, as powerd held it. */
        const val GRACE_MS = 5_000L
    }
}

/**
 * A few named strings kept across restarts: Android's SharedPreferences (which hold what earlier
 * Lunacys wrote), a file on the desktop. powerd's timeouts are kept in one.
 */
interface KeyedStore {
    fun all(): Map<String, String>
    fun put(key: String, value: String)
    fun remove(key: String)
}
