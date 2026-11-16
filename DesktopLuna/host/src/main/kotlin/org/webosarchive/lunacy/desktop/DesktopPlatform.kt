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

    override fun coverThumbnail(file: File, px: Int): ByteArray? = runCatching {
        val src = ImageIO.read(file) ?: return null
        val scale = maxOf(px.toDouble() / src.width, px.toDouble() / src.height)
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

/**
 * The desktop's network as webOS's connection manager reports it: the machine's first interface
 * that is up with an address, shown as the TouchPad's Wi-Fi (a TouchPad had nothing else), at
 * full signal and with no network name, which Java can't ask the host for. Checked every few
 * seconds while something is subscribed.
 */
class DesktopNetwork(private val main: DesktopScheduler) : org.webosarchive.lunacy.core.NetworkSource {
    private var tick: Runnable? = null
    private var changed: (() -> Unit)? = null

    /**
     * The connection lost, for a developer to see their app without one (the Wi-Fi tile in Luna
     * DE): the connection manager reports no network, its subscribers hear it at once, and native
     * requests fail (Http.offline). The machine's own network is untouched.
     */
    @Volatile var simulatedDown = false
        private set

    /** On the main thread. */
    fun simulateDown(down: Boolean) {
        simulatedDown = down
        changed?.invoke()
    }

    override fun current(): org.webosarchive.lunacy.core.NetworkSource.Network? {
        if (simulatedDown) return null
        val address = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
        }.getOrNull() ?: return null
        return org.webosarchive.lunacy.core.NetworkSource.Network(
            org.webosarchive.lunacy.core.NetworkSource.Wifi(address.hostAddress ?: "", "", "", 2), mobile = false)
    }

    override fun watch(onChange: () -> Unit) {
        changed = onChange
        if (tick != null) return
        val r = object : Runnable { override fun run() { onChange(); main.postDelayed(this, 5_000) } }
        tick = r
        main.postDelayed(r, 5_000)
    }

    override fun unwatch() { tick?.let { main.cancel(it) }; tick = null; changed = null }
}

/** A desktop is on the mains: fully charged, on a charger, as the activity manager's requirements see it. */
object DesktopBattery : org.webosarchive.lunacy.core.BatteryWatch {
    override fun start(onChange: (percent: Int, charger: String?) -> Unit) = onChange(100, "usb")
    override fun stop() {}
}

/**
 * The activity manager's timers on the desktop: plain ones on the main thread. The machine going
 * to sleep under Luna DE delays them until it is back, which is when [due] runs anyway; there is
 * no CPU to keep up for an activity.
 */
class DesktopPower(private val main: DesktopScheduler) : org.webosarchive.lunacy.core.PowerTimers {
    private val clients = ArrayList<() -> Unit>()
    private val needs = HashMap<String, Long>()
    private var armed: Runnable? = null

    override fun onDue(f: () -> Unit) { clients += f }
    private fun due() = clients.toList().forEach { it() }

    override fun soon(time: Long) {
        main.postDelayed({ due() }, (time - System.currentTimeMillis()).coerceAtLeast(0))
    }

    override fun at(client: String, time: Long?, wake: Boolean) {
        if (time == null) needs.remove(client) else needs[client] = time
        armed?.let { main.cancel(it) }
        armed = null
        val next = needs.values.minOrNull() ?: return
        val r = Runnable { armed = null; due() }
        armed = r
        main.postDelayed(r, (next - System.currentTimeMillis()).coerceAtLeast(0))
    }

    override fun lock(id: Int) {}
    override fun unlock(id: Int, debounce: Boolean) {}
}
