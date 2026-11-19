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

    /** Image decoding; null where the platform has none (an icon's size then isn't checked). */
    @Volatile var images: Images? = null

    /** Reading a media file's own tags, for the media index; null where the platform has none. */
    @Volatile var mediaTags: MediaTags? = null

    /** The host's folders mapped into /media/internal; null where there are none. */
    @Volatile var hostFolders: HostFolders? = null

    @Volatile private var currentLinks: Links? = null

    /** Symbolic links, for the webOS root. */
    var links: Links
        get() = currentLinks ?: throw IllegalStateException("LunaCore: no Links set (Platform.links)")
        set(value) { currentLinks = value }

    @Volatile private var currentSql: Sql? = null

    /** SQLite, for db8 and WebSQL. */
    var sql: Sql
        get() = currentSql ?: throw IllegalStateException("LunaCore: no Sql set (Platform.sql)")
        set(value) { currentSql = value }

    @Volatile private var currentCollation: Collation? = null

    /** Text sorting for db8's collated indexes. */
    var collation: Collation
        get() = currentCollation ?: throw IllegalStateException("LunaCore: no Collation set (Platform.collation)")
        set(value) { currentCollation = value }
}

/**
 * A part of Lunacy's own posts to the main thread, which it can take back all at once when it
 * stops: what a Handler of its own and removeCallbacksAndMessages(null) were on Android.
 */
class ScopedScheduler(private val base: Scheduler = Platform.scheduler) {
    private val pending = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Runnable, Boolean>())

    private fun tracked(f: () -> Unit): Runnable {
        lateinit var r: Runnable
        r = Runnable { if (pending.remove(r)) f() }
        pending.add(r)
        return r
    }

    fun post(f: () -> Unit) { base.post(tracked(f)) }
    fun postDelayed(f: () -> Unit, delayMs: Long) { base.postDelayed(tracked(f), delayMs) }

    /** Everything posted and not yet run is dropped. */
    fun cancelAll() {
        val all = pending.toList()
        pending.clear()
        all.forEach { base.cancel(it) }
    }
}
