package org.webosarchive.lunacy.card

/**
 * An OpenGL ES client's stream as it starts: the replay ([PdkGl]) can begin only once both the
 * mode (its size) and the first batch (which says GLES 1.1 or 2) have come, in whichever order
 * and on whichever thread. Batches that arrive before then are kept and replayed first: the
 * first one holds the textures. Shared by a PDK card ([PdkWindow]) and a hybrid app's plugin
 * ([HybridPlugins]).
 */
class GlStream(
    /** The replay for a size and version, or null when there can't be one (no native side, the client gone). */
    private val make: (width: Int, height: Int, version: Int) -> PdkGl?,
    /** Where a start is tried: the main thread for a card, which builds its view then; at once for a plugin. */
    private val startOn: (() -> Unit) -> Unit = { it() },
    /** The replay has started, with how many batches were waiting for it. On [startOn]'s thread. */
    private val onStarted: (PdkGl, waiting: Int) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    @Volatile var stream: PdkGl? = null
        private set
    private var mode: Pair<Int, Int>? = null
    private var version = 0
    private val early = ArrayList<ByteArray>()

    fun onMode(width: Int, height: Int) {
        synchronized(lock) { mode = width to height }
        startOn { start() }
    }

    fun onBatch(batch: ByteArray, version: Int) {
        stream?.let { it.replay(batch); return }
        synchronized(lock) {
            stream?.let { it.replay(batch); return }
            early.add(batch)
            if (this.version != 0) return
            this.version = version
        }
        startOn { start() }
    }

    fun destroy() = synchronized(lock) { stream?.destroy(); stream = null }

    /**
     * The replay (a native GL context) is made outside the lock, as it was before this class:
     * a batch arriving meanwhile waits in [onBatch] only for the early ones' replay, not for
     * the context's creation. Two starts at once (a plugin starts on whichever thread brought
     * the second of mode and batch) each make one; the first in keeps it, the other is let go.
     */
    private fun start() {
        val (w, h, v) = synchronized(lock) {
            val (w, h) = mode ?: return
            if (stream != null || version == 0) return
            Triple(w, h, version)
        }
        val made = make(w, h, v) ?: return
        val waiting = synchronized(lock) {
            if (stream != null) { made.destroy(); return }
            val waiting = early.size
            early.forEach { made.replay(it) }
            early.clear()
            stream = made
            waiting
        }
        onStarted(made, waiting)
    }
}
