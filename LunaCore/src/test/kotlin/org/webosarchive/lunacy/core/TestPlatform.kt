package org.webosarchive.lunacy.core

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * LunaCore's platform for its own tests: SQLite over JDBC and ICU's collation, which are Luna
 * DE's, and a main thread that is the test's own, running what was posted when the test says so.
 */
object TestPlatform {
    val scheduler = TestScheduler()

    fun install() {
        scheduler.adopt()
        Platform.scheduler = scheduler
        Platform.sql = JdbcSql
        Platform.collation = Collation { strength ->
            val c = com.ibm.icu.text.Collator.getInstance().apply {
                this.strength = when (strength) { "primary" -> com.ibm.icu.text.Collator.PRIMARY; "secondary" -> com.ibm.icu.text.Collator.SECONDARY; else -> com.ibm.icu.text.Collator.TERTIARY }
            }
            Comparator { a, b -> c.compare(a, b) }
        }
    }
}

class TestScheduler : Scheduler {
    @Volatile private var main: Thread = Thread.currentThread()
    private val queue = ConcurrentLinkedQueue<Runnable>()
    private val delayed = ConcurrentLinkedQueue<Pair<Long, Runnable>>()

    /** The calling thread is the main thread from now on (each test runs on its own). */
    fun adopt() { main = Thread.currentThread(); queue.clear(); delayed.clear() }
    override fun isMainThread() = Thread.currentThread() === main
    override fun post(r: Runnable) { queue.add(r) }
    override fun postDelayed(r: Runnable, delayMs: Long) { delayed.add(System.currentTimeMillis() + delayMs to r) }
    override fun cancel(r: Runnable) { queue.remove(r); delayed.removeIf { it.second === r } }

    /** Runs everything posted, and what was delayed and is due, on the calling (main) thread. */
    fun idle() {
        val now = System.currentTimeMillis()
        delayed.filter { it.first <= now }.forEach { delayed.remove(it); queue.add(it.second) }
        while (true) { (queue.poll() ?: return).run() }
    }
}
