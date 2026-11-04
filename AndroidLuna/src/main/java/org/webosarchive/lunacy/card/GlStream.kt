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

    private fun start() {
        val (s, waiting) = synchronized(lock) {
            val (w, h) = mode ?: return
            if (stream != null || version == 0) return
            val s = make(w, h, version) ?: return
            val waiting = early.size
            early.forEach { s.replay(it) }
            early.clear()
            stream = s
            s to waiting
        }
        onStarted(s, waiting)
    }
}
