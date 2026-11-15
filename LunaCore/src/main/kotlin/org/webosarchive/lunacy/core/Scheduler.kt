package org.webosarchive.lunacy.core

/**
 * The main thread, as LunaCore needs it: the bus and its services live on one thread
 * (Bus.mainOnly), and work elsewhere comes back to it. Android's is the main Looper
 * (AndroidScheduler); Luna DE's is a single-thread executor that gives the same guarantee.
 */
interface Scheduler {
    fun isMainThread(): Boolean
    fun post(r: Runnable)
    fun postDelayed(r: Runnable, delayMs: Long)
    fun cancel(r: Runnable)
}

/**
 * What the platform has given LunaCore. Each platform sets these once, as its process starts,
 * before any of LunaCore runs: AndroidLuna in LunacyApplication.onCreate, Luna DE in its main.
 */
object Platform {
    @Volatile private var current: Scheduler? = null

    var scheduler: Scheduler
        get() = current ?: throw IllegalStateException("LunaCore: no Scheduler set (Platform.scheduler)")
        set(value) { current = value }

    /** Whether a platform has set its scheduler yet. */
    val ready: Boolean get() = current != null
}
