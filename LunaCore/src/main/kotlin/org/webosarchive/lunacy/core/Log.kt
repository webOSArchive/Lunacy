package org.webosarchive.lunacy.core

/**
 * Lunacy's log, with android.util.Log's calls, so a class that moves into LunaCore changes only
 * its import. Each platform gives it a [Sink]: AndroidLuna's writes to logcat (LunacyApplication),
 * Luna DE's to the terminal and its log file. Until one is set, lines go to standard error.
 */
object Log {
    /** Where the lines go. priority is android.util.Log's: 2 verbose ... 6 error. */
    fun interface Sink { fun write(priority: Int, tag: String, msg: String, tr: Throwable?) }

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    @Volatile var sink: Sink = Sink { p, tag, msg, tr ->
        System.err.println("${"??VDIWE".getOrElse(p) { '?' }}/$tag: $msg")
        tr?.printStackTrace()
    }

    @JvmStatic fun v(tag: String, msg: String?): Int { sink.write(VERBOSE, tag, msg.orEmpty(), null); return 0 }
    @JvmStatic fun d(tag: String, msg: String?): Int { sink.write(DEBUG, tag, msg.orEmpty(), null); return 0 }
    @JvmStatic fun d(tag: String, msg: String?, tr: Throwable?): Int { sink.write(DEBUG, tag, msg.orEmpty(), tr); return 0 }
    @JvmStatic fun i(tag: String, msg: String?): Int { sink.write(INFO, tag, msg.orEmpty(), null); return 0 }
    @JvmStatic fun i(tag: String, msg: String?, tr: Throwable?): Int { sink.write(INFO, tag, msg.orEmpty(), tr); return 0 }
    @JvmStatic fun w(tag: String, msg: String?): Int { sink.write(WARN, tag, msg.orEmpty(), null); return 0 }
    @JvmStatic fun w(tag: String, msg: String?, tr: Throwable?): Int { sink.write(WARN, tag, msg.orEmpty(), tr); return 0 }
    @JvmStatic fun w(tag: String, tr: Throwable?): Int { sink.write(WARN, tag, "", tr); return 0 }
    @JvmStatic fun e(tag: String, msg: String?): Int { sink.write(ERROR, tag, msg.orEmpty(), null); return 0 }
    @JvmStatic fun e(tag: String, msg: String?, tr: Throwable?): Int { sink.write(ERROR, tag, msg.orEmpty(), tr); return 0 }
}
