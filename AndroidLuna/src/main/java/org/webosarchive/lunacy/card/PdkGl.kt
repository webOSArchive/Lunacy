package org.webosarchive.lunacy.card

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface

/**
 * The shell's end of a PDK app's OpenGL ES 1.1 stream (Docs/pdk.md, "Transformers G1"):
 * liblunacygl.so, built from LunaRuntimes/pdk/libgles/gl_server.c, owns a GLES 1.1 context
 * on the card's TextureView and replays the batches the app's libGLES_CM.so sends. One
 * thread per app does the GL work, so a slow frame never holds the reader or the UI.
 *
 * The app drew for its own screen size; the native side letterboxes it into the view, and
 * turns it a quarter when the card says so ([turn]): the turn goes into the projection.
 */
class PdkGl(private val appId: String, private val gameWidth: Int, private val gameHeight: Int) {
    private val thread = HandlerThread("pdk-gl $appId").apply { start() }
    private val handler = Handler(thread.looper)
    private var handle = 0L
    private var wantedTurn = 0
    /** Batches that arrived before the surface did: a game loads its textures at once. GL thread. */
    private val early = ArrayList<ByteArray>()
    private var earlyBytes = 0L

    /** The TextureView has a surface: make the context on it. */
    fun attach(texture: SurfaceTexture) = handler.post {
        if (handle != 0L) return@post
        val surface = Surface(texture)
        handle = create(surface, gameWidth, gameHeight)
        surface.release()
        if (handle == 0L) Log.w(AppServer.TAG, "pdk [$appId]: no GL context for the card")
        else {
            turn(handle, wantedTurn)
            for (b in early) replay(handle, b, b.size)
            if (early.isNotEmpty()) Log.i(AppServer.TAG, "pdk [$appId]: ${early.size} batches replayed from before the surface")
            early.clear()
        }
    }

    /** The turn the picture is shown with: 0, 90, 180 or -90, clockwise as Android counts. */
    fun turn(degrees: Int) = handler.post { wantedTurn = degrees; if (handle != 0L) turn(handle, degrees) }

    /** A batch from the app; replayed in order on the GL thread. Batches before attach wait. */
    fun replay(batch: ByteArray) = handler.post {
        if (handle != 0L) replay(handle, batch, batch.size)
        else if (earlyBytes < EARLY_LIMIT) { early.add(batch); earlyBytes += batch.size }
        else if (earlyBytes == EARLY_LIMIT) { earlyBytes++; Log.w(AppServer.TAG, "pdk [$appId]: no surface yet and ${EARLY_LIMIT shr 20} MB of GL queued; dropping the rest") }
    }

    /** The app's swap: show what the batches drew. [onShown] runs on the GL thread after it. */
    fun swap(onShown: () -> Unit) = handler.post {
        if (handle != 0L) { swap(handle); onShown() }
    }

    fun destroy() = handler.post {
        if (handle != 0L) destroy(handle)
        handle = 0L
        thread.quitSafely()
    }

    companion object {
        val available: Boolean = runCatching { System.loadLibrary("lunacygl") }.isSuccess
        /** A tablet asleep with a game running has no surface; its frames are kept up to this. */
        private const val EARLY_LIMIT = 64L shl 20

        @JvmStatic private external fun create(surface: Surface, gameW: Int, gameH: Int): Long
        @JvmStatic private external fun replay(handle: Long, data: ByteArray, len: Int)
        @JvmStatic private external fun swap(handle: Long)
        @JvmStatic private external fun frames(handle: Long): Int
        @JvmStatic private external fun turn(handle: Long, degrees: Int)
        @JvmStatic private external fun destroy(handle: Long)
    }
}
