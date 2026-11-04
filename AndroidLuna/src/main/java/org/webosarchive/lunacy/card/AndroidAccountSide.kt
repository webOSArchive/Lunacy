package org.webosarchive.lunacy.card

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock

/**
 * One side of the Android account ([AndroidContacts], [AndroidCalendar]): a one-way copy of
 * one of Android's providers into db8, kept while the account's capability is on. What the two
 * share lives here: the account id they copy for, their thread, the observer that schedules a
 * pass when Android's data changes, and the accounts service's `onEnabled`/`onDelete` calls.
 * Each side says how to copy ([sync]) and how to take the copy away ([remove]).
 */
abstract class AndroidAccountSide(
    context: Context,
    prefsName: String,
    private val permission: String,
    private val contentUri: Uri,
    /** How long after Lunacy starts the first pass waits: the shell has more pressing work. */
    private val startDelayMs: Long,
) {
    protected val context: Context = context.applicationContext
    protected val prefs = this.context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val thread = HandlerThread(javaClass.simpleName).apply { start() }
    protected val handler = Handler(thread.looper)
    private val syncNow = Runnable { sync() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = schedule(CHANGE_DELAY_MS)
    }
    private var watching = false

    /** One pass: Android's data into db8. On [handler]'s thread. */
    protected abstract fun sync()

    /** The account [id]'s copy taken out of db8. On [handler]'s thread. */
    protected abstract fun remove(id: String)

    /** What else a side watches while it copies (Calendar: the time zone). On [handler]'s thread. */
    protected open fun onWatching(on: Boolean) {}

    protected fun accountId(): String? = prefs.getString("accountId", null)

    /** The accounts service's two calls for this side, under [service], and the first pass if the account is on. */
    protected fun registerAccount(bus: Bus, service: String, enabledMethod: String, deleteMethod: String) {
        bus.register(service, enabledMethod, Bus.CallHandler(::onEnabled))
        bus.register(service, deleteMethod, Bus.CallHandler(::onDelete))
        if (accountId() != null) handler.post { startWatching(); schedule(startDelayMs) }
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
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
            handler.post { handler.removeCallbacksAndMessages(REMOVAL); startWatching(); schedule(0) }
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
            stopWatching()
            handler.postAtTime({ remove(id) }, REMOVAL, SystemClock.uptimeMillis() + REMOVAL_DELAY_MS)
        }
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
        handler.removeCallbacks(syncNow)
        handler.postDelayed(syncNow, delay)
    }

    private companion object {
        const val ACCOUNTS_SERVICE = "com.palm.service.accounts"
        const val CHANGE_DELAY_MS = 3000L
        const val REMOVAL_DELAY_MS = 8000L
        val REMOVAL = Any()
    }
}
