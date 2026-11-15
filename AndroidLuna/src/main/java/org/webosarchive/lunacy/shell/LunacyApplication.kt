package org.webosarchive.lunacy.shell

import android.app.Application
import android.os.Handler
import android.os.Looper
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Platform
import org.webosarchive.lunacy.core.Scheduler

/**
 * Gives LunaCore its Android platform before anything of Lunacy's runs, in every process:
 * the log goes to logcat, and the main thread is the main Looper (Docs/luna-de.md, "LunaCore").
 */
class LunacyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        install()
    }

    companion object {
        /** Idempotent; also called by code that can run before the Application (tests). */
        fun install() {
            if (Platform.ready) return
            Log.sink = Log.Sink { p, tag, msg, tr ->
                if (tr == null) android.util.Log.println(p, tag, msg)
                else android.util.Log.println(p, tag, msg + "\n" + android.util.Log.getStackTraceString(tr))
            }
            Platform.scheduler = AndroidScheduler()
        }
    }
}

/** The main Looper, as LunaCore's [Scheduler]. */
class AndroidScheduler : Scheduler {
    private val main = Handler(Looper.getMainLooper())
    override fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()
    override fun post(r: Runnable) { main.post(r) }
    override fun postDelayed(r: Runnable, delayMs: Long) { main.postDelayed(r, delayMs) }
    override fun cancel(r: Runnable) { main.removeCallbacks(r) }
}
