package org.webosarchive.lunacy.card

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * One side of the Android account ([AndroidContacts], [AndroidCalendar]): a one-way copy of
 * one of Android's providers into db8, kept while the account's capability is on. What the two
 * share lives here: the account id they copy for, their thread, the observer that schedules a
 * pass when Android's data changes, and the accounts service's `onEnabled`/`onDelete` calls.
 * Each side says how to copy ([sync]) and how to take the copy away ([remove]).
 */
abstract class AndroidAccountSide(
    context: Context,
    private val db: Db8,
    prefsName: String,
    /** Its capability provider's id in the account template, which its sync state is written under. */
    private val capabilityProvider: String,
    private val permission: String,
    private val contentUri: Uri,
    /** How long after Lunacy starts the first pass waits: the shell has more pressing work. */
    private val startDelayMs: Long,
    /**
     * A side whose passes go first: while it has one scheduled or running, this side's waits
     * (up to [MAX_WAIT_MS]). Calendar waits for Contacts: a calendar is big and slow to copy,
     * contacts are quick, and the person should see something arrive (webOS's DAV sync ran
     * calendars first and held contacts back behind them, codepoet 2026-11-07).
     */
    private val after: AndroidAccountSide? = null,
) {
    protected val context: Context = context.applicationContext
    protected val prefs = this.context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val thread = HandlerThread(javaClass.simpleName).apply { start() }
    protected val handler = Handler(thread.looper)
    /** A pass is scheduled or running: a side that comes [after] this one waits for it. */
    @Volatile private var busy = false
    private var waitingSince = 0L
    private val syncNow: Runnable = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (after?.busy == true) {
                if (waitingSince == 0L) waitingSince = now
                if (now - waitingSince < MAX_WAIT_MS) { handler.postDelayed(this, WAIT_STEP_MS); return }
            }
            waitingSince = 0L
            try { sync() } finally { busy = false }
            // A first pass's records are in db8's queue: its sync state ends once they're written.
            initialFor?.let { id -> initialFor = null; db.after { clearSyncState(id) } }
        }
    }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = schedule(CHANGE_DELAY_MS)
    }
    private var watching = false
    private var bus: Bus? = null
    /** The account whose first pass, once made, ends its INITIAL_SYNC state. On [handler]'s thread. */
    private var initialFor: String? = null

    /** One pass: Android's data into db8. On [handler]'s thread. */
    protected abstract fun sync()

    /** The account [id]'s copy taken out of db8. On [handler]'s thread. */
    protected abstract fun remove(id: String)

    /** What else a side watches while it copies (Calendar: the time zone). On [handler]'s thread. */
    protected open fun onWatching(on: Boolean) {}

    protected fun accountId(): String? = prefs.getString("accountId", null)

    /** The accounts service's two calls for this side, under [service], and the first pass if the account is on. */
    protected fun registerAccount(bus: Bus, service: String, enabledMethod: String, deleteMethod: String) {
        this.bus = bus
        bus.register(service, enabledMethod, Bus.CallHandler(::onEnabled))
        bus.register(service, deleteMethod, Bus.CallHandler(::onDelete))
        if (accountId() != null) handler.post { startWatching(); schedule(startDelayMs) }
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        busy = false
        stopWatching()
        thread.quitSafely()
    }

    /**
     * The accounts service, as the account's capability is turned on or off. It always gets
     * true: a failed answer leaves its chain of calls waiting, with the account stuck. Whether
     * the copy could be made shows in the app, not in this reply.
     */
    private fun onEnabled(call: Bus.Call) {
        if (call.app != ACCOUNTS_SERVICE) { call.reply(Bus.error("Only the accounts service calls this")); return }
        val id = call.params.optString("accountId")
        if (id.isEmpty()) { call.reply(Bus.error("accountId is required")); return }
        if (call.params.optBoolean("enabled")) {
            prefs.edit().putString("accountId", id).apply()
            // The accounts service enables an account's capabilities one after another, in no set
            // order: a side that comes after another gives the other's call time to arrive.
            // The first pass is an initial sync, which shows (Sync Status), as a transport's did.
            if (granted()) setSyncState(id, "INITIAL_SYNC")
            handler.post {
                handler.removeCallbacksAndMessages(REMOVAL)
                initialFor = id
                startWatching(); schedule(if (after != null) CHANGE_DELAY_MS else 0)
            }
        } else stop(id)
        call.reply(Bus.ok())
    }

    private fun onDelete(call: Bus.Call) {
        if (call.app != ACCOUNTS_SERVICE) { call.reply(Bus.error("Only the accounts service calls this")); return }
        val id = call.params.optString("accountId")
        val keep = call.params.optBoolean("keepData")
        handler.post {
            handler.removeCallbacksAndMessages(REMOVAL)
            // Remove Account's "keep data" box: the copy stays, as it is, with no account.
            if (id.isNotEmpty() && !keep) remove(id)
        }
        call.reply(Bus.ok())
    }

    /**
     * Off: no more copying, and the copy goes, a moment later. The accounts service turns a
     * capability off before it deletes the account too, and only onDelete says whether to keep
     * the data, so this waits for that. Turning the capability off alone just takes the wait.
     */
    private fun stop(id: String) {
        prefs.edit().remove("accountId").apply()
        handler.post {
            handler.removeCallbacks(syncNow)
            busy = false
            if (initialFor != null) { initialFor = null; mainHandler.post { clearSyncState(id) } }
            stopWatching()
            handler.postAtTime({ remove(id) }, REMOVAL, SystemClock.uptimeMillis() + REMOVAL_DELAY_MS)
        }
    }

    /**
     * The side's com.palm.account.syncstate:1 record in tempdb, as webOS's transports wrote it
     * (mojoservice.transport's SyncStatusManager): one per account and capability provider,
     * which the accounts list and Sync Status read. On the main thread, through the bus.
     */
    private fun setSyncState(accountId: String, state: String) {
        val record = JSONObject().put("_kind", SYNC_STATE_KIND).put("accountId", accountId).put("capabilityProvider", capabilityProvider)
            .put("busAddress", AndroidContacts.SERVICE).put("collectionId", JSONObject.NULL).put("syncState", state)
        clearSyncState(accountId) { bus?.call(AndroidContacts.SERVICE, "palm://com.palm.tempdb/put", JSONObject().put("objects", JSONArray().put(record)).toString()) {} }
    }

    private fun clearSyncState(accountId: String, then: () -> Unit = {}) {
        val bus = bus ?: return
        val where = JSONArray()
            .put(JSONObject().put("prop", "accountId").put("op", "=").put("val", accountId))
            .put(JSONObject().put("prop", "capabilityProvider").put("op", "=").put("val", capabilityProvider))
        val query = JSONObject().put("from", SYNC_STATE_KIND).put("where", where)
        bus.call(AndroidContacts.SERVICE, "palm://com.palm.tempdb/del", JSONObject().put("query", query).toString()) { then() }
    }

    protected fun granted() = context.checkPermission(permission, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED

    protected fun startWatching() {
        if (watching || !granted()) return
        context.contentResolver.registerContentObserver(contentUri, true, observer)
        onWatching(true)
        watching = true
    }

    private fun stopWatching() {
        if (!watching) return
        context.contentResolver.unregisterContentObserver(observer)
        onWatching(false)
        watching = false
    }

    /** Android signals a burst of changes while an account syncs: one pass, once it settles. */
    protected fun schedule(delay: Long) {
        busy = true
        handler.removeCallbacks(syncNow)
        handler.postDelayed(syncNow, delay)
    }

    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    private companion object {
        const val SYNC_STATE_KIND = "com.palm.account.syncstate:1"
        const val ACCOUNTS_SERVICE = "com.palm.service.accounts"
        const val CHANGE_DELAY_MS = 3000L
        const val REMOVAL_DELAY_MS = 8000L
        const val WAIT_STEP_MS = 500L
        const val MAX_WAIT_MS = 120_000L
        val REMOVAL = Any()
    }
}
