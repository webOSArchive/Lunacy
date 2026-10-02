package org.webosarchive.lunacy.card

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface

/**
 * The shell's end of a PDK app's OpenGL ES stream (Docs/pdk.md, "The GL stream"):
 * liblunacygl.so for GLES 1.1 and liblunacygl2.so for GLES 2, both built from
 * LunaRuntimes/pdk/libgles/gl_server.c. It gives the app a
 * framebuffer of its own screen's size, replays the batches the app's libGLES_CM.so sends
 * into it, and at each of the app's swaps draws it into the card's TextureView, letterboxed
 * and turned ([turn]). One thread per app does the GL work, so a slow frame never holds the
 * reader or the UI. Answers for the app - swap acknowledgements, read pixels - go to [answer].
 */
class PdkGl(
    private val appId: String,
    gameWidth: Int,
    gameHeight: Int,
    /** 1: GLES 1.1, replayed by liblunacygl.so; 2: GLES 2, by liblunacygl2.so. */
    private val version: Int,
    private val answer: (ByteArray) -> Unit,
    private val firstFrame: () -> Unit,
) {
    private val thread = HandlerThread("pdk-gl $appId").apply { start() }
    private val handler = Handler(thread.looper)
    private var handle = 0L
    private var shown = false

    init {
        if (version == 2) available2   // loads liblunacygl2.so before the first call
        handler.post {
            handle = if (version == 2) create2(gameWidth, gameHeight) else create(gameWidth, gameHeight)
            if (handle == 0L) Log.w(AppServer.TAG, "pdk [$appId]: no GL context for the app")
        }
    }

    /** The TextureView has a surface: the card can show the app's frames. */
    fun attach(texture: SurfaceTexture) = handler.post {
        if (handle == 0L) return@post
        val surface = Surface(texture)
        if (version == 2) attach2(handle, surface) else attach(handle, surface)
        surface.release()
    }

    /** The turn the picture is shown with: 0, 90, 180 or -90, clockwise as Android counts. */
    fun turn(degrees: Int) = handler.post { if (handle != 0L) { if (version == 2) turn2(handle, degrees) else turn(handle, degrees) } }

    /** A batch from the app, replayed in order on the GL thread. */
    fun replay(batch: ByteArray) = handler.post {
        if (handle == 0L) { answerWithout(batch); return@post }
        (if (version == 2) replay2(handle, batch, batch.size) else replay(handle, batch, batch.size))?.let(answer)
        if (!shown && (if (version == 2) frames2(handle) else frames(handle)) > 0) { shown = true; firstFrame() }
    }

    /**
     * No context (the device's GL refused one): the app still gets its answers - each swap
     * acknowledged, each read as zeros - so it runs on without a picture rather than hang.
     */
    private fun answerWithout(batch: ByteArray) {
        val b = java.nio.ByteBuffer.wrap(batch).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = java.io.ByteArrayOutputStream()
        fun msg(type: Int, payload: Int) {
            val h = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(type).putInt(0).putInt(payload)
            out.write(h.array()); if (payload > 0) out.write(ByteArray(payload))
        }
        while (b.remaining() >= 8) {
            val op = b.int; val len = b.int
            if (len < 0 || len > b.remaining()) break
            val at = b.position()
            when (op) {
                SWAP_OP -> msg(GL_ACK, 0)
                READ_OP -> if (len >= 28) msg(GL_PIXELS, b.getInt(at + 24).coerceIn(0, 64 shl 20))
            }
            b.position(at + len)
        }
        if (out.size() > 0) answer(out.toByteArray())
    }

    fun destroy() = handler.post {
        if (handle != 0L) { if (version == 2) destroy2(handle) else destroy(handle) }
        handle = 0L
        thread.quitSafely()
    }

    companion object {
        val available: Boolean = runCatching { System.loadLibrary("lunacygl") }.isSuccess
        val available2: Boolean by lazy { runCatching { System.loadLibrary("lunacygl2") }.isSuccess }
        private const val SWAP_OP = 0xFFFD; private const val READ_OP = 0xFFFE
        private const val GL_PIXELS = 7; private const val GL_ACK = 8

        @JvmStatic private external fun create(gameW: Int, gameH: Int): Long
        @JvmStatic private external fun attach(handle: Long, surface: Surface)
        @JvmStatic private external fun replay(handle: Long, data: ByteArray, len: Int): ByteArray?
        @JvmStatic private external fun frames(handle: Long): Int
        @JvmStatic private external fun turn(handle: Long, degrees: Int)
        @JvmStatic private external fun destroy(handle: Long)
        @JvmStatic private external fun create2(gameW: Int, gameH: Int): Long
        @JvmStatic private external fun attach2(handle: Long, surface: Surface)
        @JvmStatic private external fun replay2(handle: Long, data: ByteArray, len: Int): ByteArray?
        @JvmStatic private external fun frames2(handle: Long): Int
        @JvmStatic private external fun turn2(handle: Long, degrees: Int)
        @JvmStatic private external fun destroy2(handle: Long)
    }
}
