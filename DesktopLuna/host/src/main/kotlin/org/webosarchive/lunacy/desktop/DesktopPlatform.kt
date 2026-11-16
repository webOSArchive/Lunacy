package org.webosarchive.lunacy.desktop

import org.webosarchive.lunacy.core.Images
import org.webosarchive.lunacy.core.Scheduler
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Luna DE's main thread: one thread, as Android's main Looper is, so the bus and its services
 * keep the one-thread rule they were written for (Bus.mainOnly).
 */
class DesktopScheduler : Scheduler {
    @Volatile private var thread: Thread? = null
    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "lunacy-main").also { thread = it } }
    private val delayed = ConcurrentHashMap<Runnable, ScheduledFuture<*>>()

    override fun isMainThread() = Thread.currentThread() === thread
    override fun post(r: Runnable) { exec.execute(guard(r)) }
    override fun postDelayed(r: Runnable, delayMs: Long) {
        delayed[r] = exec.schedule({ delayed.remove(r); guard(r).run() }, delayMs, TimeUnit.MILLISECONDS)
    }
    override fun cancel(r: Runnable) { delayed.remove(r)?.cancel(false) }

    /** Runs [block] on the main thread and waits for its value. */
    fun <T> call(block: () -> T): T = if (isMainThread()) block() else exec.submit(Callable { block() }).get()

    private fun guard(r: Runnable) = Runnable {
        try { r.run() } catch (e: Throwable) { org.webosarchive.lunacy.core.Log.e(org.webosarchive.lunacy.core.Lunacy.TAG, "main thread", e) }
    }
}

/** javax.imageio, as LunaCore's [Images]. */
object DesktopImages : Images {
    override fun size(input: InputStream): IntArray? =
        runCatching { ImageIO.read(input)?.let { intArrayOf(it.width, it.height) } }.getOrNull()

    override fun jpegThumbnail(file: File, px: Int): ByteArray? = runCatching {
        val src = ImageIO.read(file) ?: return null
        val scale = minOf(1.0, px.toDouble() / minOf(src.width, src.height))
        val w = maxOf(1, Math.round(src.width * scale).toInt())
        val h = maxOf(1, Math.round(src.height * scale).toInt())
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply { drawImage(src.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null); dispose() }
        ByteArrayOutputStream().also { ImageIO.write(out, "jpg", it) }.toByteArray()
    }.getOrNull()
}

/** ICU4J, as LunaCore's [org.webosarchive.lunacy.core.Collation]: db8's collated indexes sort as a device's do. */
object DesktopCollation : org.webosarchive.lunacy.core.Collation {
    override fun comparator(strength: String): Comparator<String> {
        val c = com.ibm.icu.text.Collator.getInstance().apply {
            this.strength = when (strength) { "primary" -> com.ibm.icu.text.Collator.PRIMARY; "secondary" -> com.ibm.icu.text.Collator.SECONDARY; else -> com.ibm.icu.text.Collator.TERTIARY }
        }
        return Comparator { a, b -> c.compare(a, b) }
    }
}

/** java.nio.file, as LunaCore's [org.webosarchive.lunacy.core.Links]. */
object DesktopLinks : org.webosarchive.lunacy.core.Links {
    override fun readlink(path: String): String? = runCatching {
        val p = java.nio.file.Paths.get(path)
        if (java.nio.file.Files.isSymbolicLink(p)) java.nio.file.Files.readSymbolicLink(p).toString() else null
    }.getOrNull()

    override fun symlink(target: String, path: String) {
        java.nio.file.Files.createSymbolicLink(java.nio.file.Paths.get(path), java.nio.file.Paths.get(target))
    }
}
