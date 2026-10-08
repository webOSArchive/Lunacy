package org.webosarchive.lunacy.card

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Runs a PDK app's binary as its own process and carries its screen, sound and input
 * (Docs/pdk.md). The binary is a 32-bit ARM glibc executable exactly as it shipped; it is
 * started through the glibc dynamic loader from [PdkRuntime], with SDL rebuilt to talk to
 * this class: the screen surface is a file both sides mmap, and one abstract Unix socket
 * carries frames, touches, keys, audio and PDL requests (LunaRuntimes/pdk, lunacy_protocol.h).
 *
 * Threads: an accept thread, a reader per connection, and a thread copying the binary's
 * stderr to the log. Everything that reaches the window is posted to the main thread.
 */
class PdkHost(
    private val context: Context,
    val appId: String,
    private val app: AppInfo,
    private val runtime: PdkRuntime,
    private val listener: Listener,
    /** Set when this is a hybrid app's plugin rather than a PDK app's card. */
    private val plugin: Plugin? = null,
) {
    /**
     * A hybrid app's plugin (Docs/pdk.md, "Hybrid apps"): the binary its page names in an
     * `<object type="application/x-palm-remote" exe="…">`, relative to the app's folder, and
     * the page's end of libpdl's JS link.
     */
    class Plugin(val exe: String, val link: JsLink)

    /** The page's end of a plugin's JS link. Every call is on the link's reader thread. */
    interface JsLink {
        /** The plugin has connected (PDL_Init). */
        fun onConnected()
        /** PDL_JSRegistrationComplete, with the names its handlers answer to. */
        fun onReady(names: List<String>)
        /** The answer to [jsCall] number [id]: kind 0 a reply, 1 an exception, 2 none. */
        fun onReply(id: Int, kind: Int, value: String)
        /** PDL_CallJS. */
        fun onCallJs(name: String, args: List<String>)
    }

    interface Listener {
        /** The app set its screen size; the frame bitmap is this size from now on. gl: an OpenGL ES mode, no bitmap. */
        fun onMode(width: Int, height: Int, gl: Boolean)
        /** A batch of GL commands from the app's libGLES_CM.so (version 1) or libGLESv2.so (2), its swaps among them. Reader thread. */
        fun onGl(batch: ByteArray, version: Int)
        /** A new frame is in [frame]. Main thread. */
        fun onFrame(frame: Bitmap)
        fun onCaption(title: String)
        /** A PDL call the shell answers for: orientation, fullscreen, vibrate… */
        fun onPdl(request: JSONObject)
        /** The process ended. */
        fun onExit(code: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    private val socketName = "lunacy-pdk-$appId-${System.nanoTime()}"
    // A plugin's own: an app can have several at once.
    private val fbFile = File(context.filesDir, "pdk/fb/$appId" + if (plugin != null) "-plugin-${System.nanoTime()}" else "").also { it.parentFile?.mkdirs() }
    private var server: LocalServerSocket? = null
    private var process: Process? = null
    private var control: OutputStream? = null
    private var track: AudioTrack? = null
    @Volatile private var stopped = false
    /** The screen the app asked for, in its px. */
    @Volatile var width = 0; private set
    @Volatile var height = 0; private set
    private var bpp = 32
    private var mapped: ByteBuffer? = null
    private var frames = arrayOfNulls<Bitmap>(2)
    private var frameIndex = 0

    fun start(): Boolean {
        val lib = runtime.libDir ?: return false
        val loader = runtime.loader
        val emulator = runtime.emulator
        if (loader == null && emulator == null) return false
        try {
            server = LocalServerSocket(socketName)
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "pdk [$appId]: can't listen on $socketName: $e"); return false
        }
        fbFile.delete()
        RandomAccessFile(fbFile, "rw").use { }
        val original = File(app.dir.let { File(runtime.appsRoot, it) }, plugin?.exe ?: app.main)
        // The installer keeps a package's files as they came; a PDK binary wants its
        // execute bit back (the loader runs it, qemu checks it).
        original.setExecutable(true, false)
        val binary = PdkRuntime.withoutExecStack(original)
        val dataDir = File(context.filesDir, "pdk/data/$appId").also { it.mkdirs() }
        val webosRoot = File(context.filesDir, "webos")
        // A 32-bit CPU runs the binary through the loader; a 64-bit-only one runs it under
        // qemu, which loads the program itself and finds the loader under the runtime folder.
        // From Android 10 a native app runs under libenosys.so, which answers the system calls
        // the app's seccomp policy refuses glibc with -ENOSYS instead of letting it be killed.
        val enosys = runtime.enosys?.takeIf { loader != null && android.os.Build.VERSION.SDK_INT >= 29 }
        // The runtime's libraries, then the webOS root's: the TouchPad's own that an app may link
        // by name and the runtime hasn't got (Quick Office's plugin: ICU 3.6, as the mail services).
        val libraryPath = "${lib.path}:${File(webosRoot, "usr/lib").path}"
        val command = if (loader != null) listOfNotNull(enosys?.path, loader.path, "--library-path", libraryPath, binary.path)
            // Under qemu the folders go by their real paths, as for native services: Termux's
            // qemu let glibc's loader decide the sysroot's "/lib" didn't exist once a library
            // wasn't in it, and Quick Office's plugin, whose first libraries are ICU's, then
            // couldn't find libpdl.so (2026-10-07).
            else listOf(emulator!!.path, "-L", lib.parentFile!!.path, "-E", "LD_LIBRARY_PATH=$libraryPath",
                "-E", "LD_PRELOAD=/lib/liblunacy-preload.so", binary.path)
        // In the app's own folder, as LunaSysMgr started a native app; the binary may sit in a
        // subfolder (Transformers: transg1/transg1.exe) and still read its data from the top.
        val appDir = File(runtime.appsRoot, app.dir)
        val pb = ProcessBuilder(command).directory(appDir).redirectErrorStream(true)
        // qemu's own libraries (Termux's build), beside the runtime.
        if (loader == null) runtime.qemuLibDir?.let { pb.environment()["LD_LIBRARY_PATH"] = it.path }
        // The preload (LunaRuntimes/pdk/libpreload): /proc/self/exe as the binary, not the
        // loader (Transformers G1 finds its data from it), and webOS's own paths (/usr/share/fonts,
        // /media/internal...) in the webOS root. Under qemu it goes to the guest by -E above.
        // libenosys is a bionic program, so it hands the preload on to the app itself.
        if (loader != null) pb.environment()[if (enosys != null) "LUNACY_PDK_PRELOAD" else "LD_PRELOAD"] = File(lib, "liblunacy-preload.so").path
        pb.environment()["LUNACY_PDK_EXE"] = binary.path
        pb.environment()["LUNACY_PDK_ROOT"] = File(context.filesDir, "webos").path
        pb.environment().apply {
            put("SDL_VIDEODRIVER", "lunacy"); put("SDL_AUDIODRIVER", "lunacy")
            put("LUNACY_PDK_SOCKET", socketName); put("LUNACY_PDK_FB", fbFile.path)
            put("LUNACY_PDK_APP_DIR", appDir.path); put("LUNACY_PDK_DATA_DIR", dataDir.path)
            put("LUNACY_PDK_SCREEN_W", runtime.screenWidth.toString()); put("LUNACY_PDK_SCREEN_H", runtime.screenHeight.toString())
            put("LUNACY_PDK_DPI", runtime.dpi.toString())
            put("LUNACY_PDK_OS_VERSION", runtime.osVersion); put("LUNACY_PDK_DEVICE_NAME", runtime.deviceName)
            put("LUNACY_PDK_NDUID", runtime.nduid)
            put("LUNACY_PDK_APPINFO_id", app.id); put("LUNACY_PDK_APPINFO_title", app.title); put("LUNACY_PDK_APPINFO_version", app.version)
            put("HOME", dataDir.path); put("TMPDIR", context.cacheDir.path)
            // /media/internal's folders that are Android's (UserFiles), for the preload.
            put("LUNACY_PDK_MEDIA", UserFiles.mappedFolders().joinToString(":") { (name, dir) -> "$name=${dir.path}" })
            // A plugin, as the reference TouchPad's RemoteAdapter started Adobe Reader's
            // (measured 2026-10-07): in the app's folder, which is its HOME too, and keeping its
            // data in /media/internal/appdata/<app id>, where the page reads what it writes.
            if (plugin != null) {
                put("LUNACY_PDK_PLUGIN", "1")
                put("HOME", appDir.path)
                put("LUNACY_PDK_DATA_DIR", "/media/internal/appdata/$appId")
                File(UserFiles.own(webosRoot), "appdata/$appId").mkdirs()
            }
            // Development: `files/pdk/env` holds KEY=VALUE lines added to every PDK process
            // (LD_DEBUG=bindings is how a game's calls into the runtime are seen in order).
            File(context.filesDir, "pdk/env").takeIf { it.isFile }?.readLines()?.forEach { l ->
                val i = l.indexOf('='); if (i > 0 && !l.startsWith("#")) put(l.substring(0, i).trim(), l.substring(i + 1).trim())
            }
        }
        Thread({ accept() }, "pdk-accept-$appId").start()
        process = try { pb.start() } catch (e: Exception) {
            Log.w(AppServer.TAG, "pdk [$appId]: can't start ${binary.path}: $e"); stop(); return false
        }
        Log.i(AppServer.TAG, "pdk [$appId]: started ${binary.name} through ${listOfNotNull(enosys, loader ?: emulator).joinToString(" and ") { it.name }}")
        Thread({
            try {
                process!!.inputStream.bufferedReader().forEachLine { Log.i(AppServer.TAG, "pdk [$appId] $it") }
            } catch (e: Exception) { }
            val code = try { process!!.waitFor() } catch (e: Exception) { -1 }
            Log.i(AppServer.TAG, "pdk [$appId]: exited $code")
            if (!stopped) main.post { listener.onExit(code) }
            stop()
        }, "pdk-log-$appId").start()
        return true
    }

    private fun accept() {
        val s = server ?: return
        while (!stopped) {
            val c = try { s.accept() } catch (e: Exception) { break }
            Thread({ serve(c) }, "pdk-conn-$appId").start()
        }
    }

    private fun serve(c: LocalSocket) {
        try {
            val input = DataInputStream(c.inputStream)
            when (input.read()) {
                'V'.code -> { control = c.outputStream; readControl(input) }
                'P'.code -> readControl(input)   // libpdl's own: requests only, no input goes back down it
                'A'.code -> readAudio(input)
                'G'.code -> { glOut = c.outputStream; readGl(input) }   // libGLES_CM's own (PdkGl)
                'J'.code -> plugin?.link?.let { link -> jsOut = c.outputStream; link.onConnected(); readJs(input, link) } ?: c.close()
                else -> c.close()
            }
        } catch (e: Exception) {
            if (!stopped) Log.i(AppServer.TAG, "pdk [$appId]: connection ended: $e")
        }
    }

    @Volatile private var glOut: java.io.OutputStream? = null
    @Volatile private var jsOut: java.io.OutputStream? = null

    /** A plugin's JS link: what the plugin says to its page. */
    private fun readJs(input: DataInputStream, link: JsLink) {
        while (!stopped) {
            val (type, a, len) = readHeader(input)
            val payload = ByteArray(len); input.readFully(payload)
            when (type) {
                JS_READY -> link.onReady(strings(payload))
                JS_REPLY -> link.onReply(a, if (len > 0) payload[0].toInt() else 2, if (len > 1) String(payload, 1, len - 1) else "")
                CALL_JS -> strings(payload).let { if (it.isNotEmpty()) link.onCallJs(it[0], it.drop(1)) }
            }
        }
    }

    /** NUL-terminated strings, one after another. */
    private fun strings(b: ByteArray): List<String> {
        val out = ArrayList<String>(); var start = 0
        for (i in b.indices) if (b[i] == 0.toByte()) { out += String(b, start, i - start); start = i + 1 }
        return out
    }

    /** Calls a plugin's handler; the answer comes to [JsLink.onReply]. False if the plugin isn't connected. */
    fun jsCall(id: Int, name: String, args: List<String>): Boolean {
        val out = jsOut ?: return false
        val body = java.io.ByteArrayOutputStream()
        for (s in listOf(name) + args) { body.write(s.toByteArray()); body.write(0) }
        val payload = body.toByteArray()
        val b = ByteBuffer.allocate(12 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(JS_CALL).putInt(id).putInt(payload.size).put(payload)
        return try { synchronized(out) { out.write(b.array()) }; true } catch (e: Exception) { false }
    }

    private fun readGl(input: DataInputStream) {
        while (!stopped) {
            val (type, version, len) = readHeader(input)
            val payload = ByteArray(len); input.readFully(payload)
            if (type == GL) listener.onGl(payload, if (version == 2) 2 else 1)
        }
    }

    /** Answers for the app's GL library (swap acknowledgements, read pixels), already framed. */
    fun glAnswer(bytes: ByteArray) {
        val out = glOut ?: return
        try { synchronized(out) { out.write(bytes) } } catch (e: Exception) { }
    }

    private fun readHeader(input: DataInputStream): IntArray {
        val b = ByteArray(12); input.readFully(b)
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        return intArrayOf(bb.int, bb.int, bb.int)
    }

    private fun readControl(input: DataInputStream) {
        while (!stopped) {
            val (type, a, len) = readHeader(input)
            val payload = ByteArray(len); input.readFully(payload)
            val p = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            when (type) {
                VIDEO_MODE -> if (a == 1) glMode(p.int, p.int) else setMode(p.int, p.int, p.int)
                FRAME -> if (a == 0) frame()   // a = 1: a swap from a GL library that isn't Lunacy's; nothing to show
                CAPTION -> String(payload).let { t -> main.post { listener.onCaption(t) } }
                PDL -> runCatching { JSONObject(String(payload)) }.getOrNull()?.let { r -> main.post { listener.onPdl(r) } }
            }
        }
    }

    private fun setMode(w: Int, h: Int, bits: Int) {
        width = w; height = h; bpp = bits
        val size = w.toLong() * h * (bits / 8)
        val channel = RandomAccessFile(fbFile, "r").channel
        // An app that sets two modes in a row (Falling Sand) has shrunk the shared file for
        // the second before this maps it for the first: that mode is stale, and the next
        // message brings the one that holds.
        if (channel.size() < size) {
            channel.close(); mapped = null
            Log.i(AppServer.TAG, "pdk [$appId]: video mode $w x $h superseded before it was shown")
            return
        }
        mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
        channel.close()
        val config = if (bits == 16) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        frames = arrayOf(Bitmap.createBitmap(w, h, config).also { it.setHasAlpha(false) }, Bitmap.createBitmap(w, h, config).also { it.setHasAlpha(false) })
        Log.i(AppServer.TAG, "pdk [$appId]: video mode $w x $h, $bits bpp")
        main.post { listener.onMode(w, h, false) }
    }

    private fun glMode(w: Int, h: Int) {
        width = w; height = h; bpp = 0
        mapped = null
        Log.i(AppServer.TAG, "pdk [$appId]: OpenGL ES mode $w x $h")
        main.post { listener.onMode(w, h, true) }
    }

    /** Copies the shared file into the bitmap the view isn't showing, then hands it over. */
    private fun frame() {
        val buf = mapped ?: return
        val bmp = frames[frameIndex] ?: return
        if (buf.capacity() < bmp.byteCount) return
        buf.rewind()
        bmp.copyPixelsFromBuffer(buf)
        frameIndex = 1 - frameIndex
        main.post { listener.onFrame(bmp) }
    }

    private fun readAudio(input: DataInputStream) {
        while (!stopped) {
            val (type, _, len) = readHeader(input)
            val payload = ByteArray(len); input.readFully(payload)
            when (type) {
                AUDIO_OPEN -> {
                    val p = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                    val freq = p.int; p.int; val channels = p.int; val samples = p.int
                    val ch = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                    val frameBytes = samples * channels * 2
                    val min = AudioTrack.getMinBufferSize(freq, ch, AudioFormat.ENCODING_PCM_16BIT)
                    track?.release()
                    @Suppress("DEPRECATION")
                    track = AudioTrack(AudioManager.STREAM_MUSIC, freq, ch, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frameBytes * 4), AudioTrack.MODE_STREAM).also { it.play() }
                    Log.i(AppServer.TAG, "pdk [$appId]: audio $freq Hz, $channels ch, $samples samples")
                }
                AUDIO_DATA -> track?.write(payload, 0, payload.size)
                AUDIO_CLOSE -> { track?.stop(); track?.release(); track = null }
            }
        }
    }

    // ---- to the app ----

    /*
     * Everything to the app goes out from a thread of its own: a LocalSocket's flush waits
     * until the other end has read every byte, and the app reads its events only when it
     * polls, so a write from the main thread (a touch, the accelerometer) could hold it until
     * Android called the shell unresponsive. No flush: the stream isn't buffered.
     */
    private val sender = android.os.HandlerThread("pdk-send $appId").apply { start() }
    private val sendHandler = android.os.Handler(sender.looper)

    private fun send(type: Int, a: Int, payload: ByteArray = ByteArray(0)) {
        val b = ByteBuffer.allocate(12 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(type).putInt(a).putInt(payload.size).put(payload)
        val bytes = b.array()
        sendHandler.post {
            val out = control ?: return@post
            try { synchronized(out) { out.write(bytes) } } catch (e: Exception) { }
        }
    }

    /** The latest accelerometer reading not yet sent; readings in between are dropped. */
    private val accelLatest = java.util.concurrent.atomic.AtomicReference<IntArray?>(null)

    private fun ints(vararg v: Int): ByteArray {
        val b = ByteBuffer.allocate(4 * v.size).order(ByteOrder.LITTLE_ENDIAN); v.forEach { b.putInt(it) }; return b.array()
    }

    /** A finger, in the app's screen px: action 0 down, 1 move, 2 up. */
    fun touch(finger: Int, action: Int, x: Int, y: Int) = send(TOUCH, finger, ints(action, x, y))
    /** An SDL keysym (SDLK_*), with its unicode if it has one. */
    fun key(down: Boolean, sym: Int, unicode: Int = 0) = send(KEY, if (down) 1 else 0, ints(sym, unicode))
    fun active(gained: Boolean) = send(ACTIVE, if (gained) 1 else 0)
    /** The accelerometer, already as webOS's joystick axes (PdkWindow). */
    fun accel(x: Int, y: Int, z: Int) {
        if (accelLatest.getAndSet(intArrayOf(x, y, z)) != null) return   // one already waiting: it'll carry this
        sendHandler.post { accelLatest.getAndSet(null)?.let { v ->
            val out = control ?: return@post
            val b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(ACCEL).putInt(0).putInt(12).putInt(v[0]).putInt(v[1]).putInt(v[2])
            try { synchronized(out) { out.write(b.array()) } } catch (e: Exception) { }
        } }
    }
    fun quit() = send(QUIT, 0)
    fun pdlReply(id: Int, json: String) = send(PDL_REPLY, id, json.toByteArray())

    /** Asks the app to quit, and ends the process if it hasn't within a moment. */
    fun stop() {
        if (stopped) return
        stopped = true
        quit()
        sendHandler.postDelayed({ sender.quitSafely() }, 1000)
        Thread {
            try { Thread.sleep(500) } catch (e: Exception) { }
            runCatching { process?.destroy() }
            runCatching { server?.close() }
            runCatching { track?.release() }
            fbFile.delete()
        }.start()
    }

    companion object {
        const val VIDEO_MODE = 1; const val FRAME = 2; const val CAPTION = 3; const val PDL = 4; const val GL = 6
        const val AUDIO_OPEN = 10; const val AUDIO_DATA = 11; const val AUDIO_CLOSE = 12
        const val TOUCH = 20; const val KEY = 21; const val QUIT = 22; const val ACTIVE = 23; const val PDL_REPLY = 24; const val ACCEL = 25
        const val JS_READY = 30; const val JS_CALL = 31; const val JS_REPLY = 32; const val CALL_JS = 33
        /** SDL 1.2 keysyms the shell sends. */
        const val SDLK_ESCAPE = 27
    }
}

/**
 * The PDK runtime shipped in the APK (tools/build-pdk.sh): the glibc dynamic loader (the
 * 32-bit build) or a static qemu for 32-bit ARM (the 64-bit build), either of which lives
 * with the native libraries because it has to be exec'd, and the libraries, extracted from
 * the assets to the app's files the first time they are needed. The libraries folder is
 * laid out as a sysroot's /lib, with the loader in it by its own name, which is how qemu
 * finds an executable's interpreter (`-L`).
 */
class PdkRuntime(private val context: Context, val appsRoot: File, val screenWidth: Int, val screenHeight: Int, val dpi: Int,
                 val osVersion: String, val deviceName: String, val nduid: String) {
    val loader: File? = File(context.applicationInfo.nativeLibraryDir, "libld-linux.so").takeIf { it.canExecute() }
    val emulator: File? = File(context.applicationInfo.nativeLibraryDir, "libqemu-arm.so").takeIf { it.canExecute() }
    /** The 32-bit build's guard for the native path on Android 10 and later (LunaRuntimes/pdk/enosys). */
    val enosys: File? = File(context.applicationInfo.nativeLibraryDir, "libenosys.so").takeIf { it.canExecute() }
    val libDir: File? by lazy { extract() }
    /** The emulator's own libraries, extracted beside the runtime (64-bit build). */
    val qemuLibDir: File? by lazy { libDir?.let { File(it.parentFile, "qemu-lib").takeIf { d -> d.isDirectory } } }
    val available get() = loader != null || emulator != null
    /** How this build runs a PDK app, for Device Info and the log. */
    val how get() = when { loader != null -> "native"; emulator != null -> "emulated"; else -> "unavailable" }

    companion object {
        private const val PT_GNU_STACK = 0x6474e551L
        private const val PF_X = 1

        /**
         * A 2010 toolchain left many PDK binaries asking for an executable stack
         * (`PT_GNU_STACK` with its execute bit), usually from one assembly file without a
         * `.note.GNU-stack` section, and Android refuses an app's process that: "cannot
         * enable executable stack as shared object requires: Permission denied" (Transformers
         * G1, 2026-10-02). The one mechanical fix `execstack -c` makes, applied to every PDK
         * binary that asks: the flag cleared in a copy beside the original, named after it
         * with a `.lunacy.` prefix, so a game that finds its data from its own path still
         * does. The package's own file is never written to. Returns the original when it
         * doesn't ask, or isn't an ELF the shell can read.
         */
        fun withoutExecStack(original: File): File {
            try {
                val bytes = original.readBytes()
                if (bytes.size < 52 || bytes[0] != 0x7f.toByte() || bytes[1] != 'E'.code.toByte() || bytes[4] != 1.toByte()) return original
                val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val phoff = bb.getInt(28); val phentsize = bb.getShort(42).toInt(); val phnum = bb.getShort(44).toInt()
                var changed = false
                for (i in 0 until phnum) {
                    val ph = phoff + i * phentsize
                    if (ph + 32 > bytes.size) break
                    if (bb.getInt(ph).toLong() and 0xffffffffL != PT_GNU_STACK) continue
                    val flags = bb.getInt(ph + 24)
                    if (flags and PF_X != 0) { bb.putInt(ph + 24, flags and PF_X.inv()); changed = true }
                }
                if (!changed) return original
                val copy = File(original.parentFile, ".lunacy." + original.name)
                if (!copy.isFile || copy.length() != original.length() || copy.lastModified() < original.lastModified()) {
                    copy.writeBytes(bytes)
                    Log.i(AppServer.TAG, "pdk: ${original.name} asked for an executable stack; running a copy without")
                }
                copy.setExecutable(true, false)
                return copy
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "pdk: couldn't read ${original.name}'s headers: $e"); return original
            }
        }
    }

    /** The "alias target" lines of an asset, as symlinks in dir. */
    private fun links(dir: File, asset: String) {
        runCatching { context.assets.open(asset).bufferedReader().readLines() }.getOrNull()?.forEach { line ->
            val (alias, target) = line.trim().split(Regex("\\s+")).takeIf { it.size == 2 } ?: return@forEach
            val f = File(dir, alias)
            f.delete()
            runCatching { android.system.Os.symlink(target, f.path) }
                .onFailure { Log.w(AppServer.TAG, "pdk: no alias $alias -> $target: ${it.message}") }
        }
    }

    private fun extract(): File? {
        val dir = File(context.filesDir, "pdk/lib")
        val stamp = File(dir, ".version")
        // The build's own id, so a new runtime replaces an extracted one (the NOTICE alone
        // stayed the same across rebuilds and left stale libraries behind).
        val notice = (runCatching { context.assets.open("pdk/BUILD").bufferedReader().readText() }.getOrNull() ?: "") +
            (runCatching { context.assets.open("pdk/NOTICE").bufferedReader().readText() }.getOrNull() ?: return null)
        if (stamp.exists() && stamp.readText() == notice) return dir
        dir.mkdirs()
        val names = context.assets.list("pdk/lib").orEmpty()
        if (names.isEmpty()) return null
        for (n in names) context.assets.open("pdk/lib/$n").use { s -> File(dir, n).outputStream().use { s.copyTo(it) } }
        // Other names apps link the same libraries by (libSDL.so, libdl.so...): symlinks.
        links(dir, "pdk/aliases")
        // The system fonts, where webOS kept them: apps open them by path with SDL_ttf
        // (/usr/share/fonts/PreludeCondensed-Medium.ttf). Laid down once per runtime.
        val fonts = File(context.filesDir, "webos/usr/share/fonts").also { it.mkdirs() }
        for (n in context.assets.list("luna/fonts").orEmpty()) if (n.endsWith(".ttf"))
            context.assets.open("luna/fonts/$n").use { s -> File(fonts, n).outputStream().use { s.copyTo(it) } }
        // The TouchPad's other fonts, by its names (arial.ttf...): free stand-ins (pdk/fonts/NOTICE).
        for (n in context.assets.list("pdk/fonts").orEmpty()) {
            if (n == "aliases") continue
            context.assets.open("pdk/fonts/$n").use { s -> File(fonts, if (n == "NOTICE") "NOTICE.stand-ins" else n).outputStream().use { s.copyTo(it) } }
        }
        links(fonts, "pdk/fonts/aliases")
        val qemuLibs = context.assets.list("pdk/qemu-lib").orEmpty()
        if (qemuLibs.isNotEmpty()) {
            val q = File(dir.parentFile, "qemu-lib").also { it.mkdirs() }
            for (n in qemuLibs) context.assets.open("pdk/qemu-lib/$n").use { s -> File(q, n).outputStream().use { s.copyTo(it) } }
        }
        stamp.writeText(notice)
        Log.i(AppServer.TAG, "pdk: runtime extracted, ${names.size} libraries")
        return dir
    }
}
