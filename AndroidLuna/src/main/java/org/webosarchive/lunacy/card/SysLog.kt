package org.webosarchive.lunacy.card

import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.LinkedBlockingQueue

/**
 * `/var/log/messages` in the webOS root, where webOS's syslogd put every app's console output
 * and where `palm-log` reads it (`tail -f`, or `grep <appid>`, over novacom: [Novacom]).
 *
 * Lines are shaped as the reference TouchPad's (webOS CE 3.1.0, measured 2026-10-05 with an
 * app that logs at every level):
 *
 *     2026-10-05T12:41:33.212872Z [42102] webos-device user.notice LunaSysMgr: {LunaSysMgrJS}: <appid>: <message>, file:///media/cryptofs/apps/usr/palm/applications/<appid>/index.html:2
 *
 * with Lunacy's own name in the machine field. console.log, info and debug are `user.notice`, warn `user.warning` and error `user.crit`.
 * The number in brackets counts the seconds the device has been awake (it stands still while
 * the TouchPad sleeps), which is Android's uptime. The file is kept under [LIMIT] by moving it
 * to `messages.0` and starting it again empty, in place, so a `tail -f` that is following it
 * carries on.
 */
object SysLog {
    const val LIMIT = 1024 * 1024L
    /** The machine field: the TouchPad's said webos-device; this is Lunacy's own. */
    private const val HOST = "lunacy"
    private val queue = LinkedBlockingQueue<String>()
    @Volatile private var file: File? = null
    private val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** Starts writing to root/var/log/messages. */
    @Synchronized fun start(root: File) {
        if (file != null) return
        file = File(root, "var/log/messages").also { it.parentFile?.mkdirs() }
        Thread({ while (true) write(queue.take()) }, "syslog").apply { isDaemon = true }.start()
    }

    /** A page's console message, as LunaSysMgr logged it. level is Android's ConsoleMessage level. */
    fun console(appId: String, level: android.webkit.ConsoleMessage.MessageLevel, message: String, source: String?, line: Int) {
        val priority = when (level) {
            android.webkit.ConsoleMessage.MessageLevel.WARNING -> "warning"
            android.webkit.ConsoleMessage.MessageLevel.ERROR -> "crit"
            else -> "notice"
        }
        log(priority, "LunaSysMgr", "{LunaSysMgrJS}: $appId: ${message.replace('\n', ' ')}, ${webosSource(source)}:$line")
    }

    fun log(priority: String, process: String, text: String) {
        if (file == null) return
        val now = System.currentTimeMillis()
        // Android's clock has milliseconds; the TouchPad printed microseconds.
        val micros = (now % 1000) * 1000
        val time = synchronized(stamp) { stamp.format(Date(now)) }
        queue.offer("$time.${"%06d".format(micros)}Z [${SystemClock.uptimeMillis() / 1000}] $HOST user.$priority $process: $text\n")
    }

    /** A page's URL as the file it was on webOS: Lunacy serves each app at its webOS path. */
    private fun webosSource(source: String?): String {
        if (source.isNullOrEmpty()) return ""
        val path = runCatching { android.net.Uri.parse(source).path }.getOrNull() ?: return source
        return if (path.startsWith("/")) "file://$path" else source
    }

    private fun write(line: String) {
        val f = file ?: return
        try {
            if (f.length() > LIMIT) {
                f.copyTo(File(f.parentFile, "messages.0"), overwrite = true)
                RandomAccessFile(f, "rw").use { it.setLength(0) }
            }
            f.appendText(line)
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "syslog: $e")
        }
    }
}
