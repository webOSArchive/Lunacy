package org.webosarchive.lunacy.card

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The PDK runtime shipped in the APK (tools/build-pdk.sh): the glibc dynamic loader (the
 * 32-bit build) or a static qemu for 32-bit ARM (the 64-bit build), either of which lives
 * with the native libraries because it has to be exec'd, and the libraries, extracted from
 * the assets to the app's files the first time they are needed. The libraries folder is
 * laid out as a sysroot's /lib, with the loader in it by its own name, which is how qemu
 * finds an executable's interpreter (`-L`).
 */
class PdkRuntime(private val context: Context, val appsRoot: File, val screenWidth: Int, val screenHeight: Int, val dpi: Int,
                 val osVersion: String, val deviceName: String, val nduid: String,
                 /** The webOS root a start script runs in: busybox's commands, webOS's paths. */
                 val webos: WebosRoot? = null) {
    /**
     * The 32-bit glibc loader, for the native path. Both builds carry it; the 64-bit one uses
     * it only where the device can still run 32-bit code (a Galaxy Z Fold3 can, a Pixel Tablet
     * can't), and otherwise runs the app under qemu. Native is the TouchPad's own way and much
     * the faster, and qemu fails some apps a device runs (Docs/pdk.md, "Native on 64-bit").
     */
    val loader: File? = File(context.applicationInfo.nativeLibraryDir, "libld-linux.so")
        .takeIf { it.canExecute() && android.os.Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() && !forceEmulated(context) }
    val emulator: File? = File(context.applicationInfo.nativeLibraryDir, "libqemu-arm.so").takeIf { it.canExecute() }
    /** The native path's guard on Android 10 and later (LunaRuntimes/pdk/enosys). */
    val enosys: File? = File(context.applicationInfo.nativeLibraryDir, "libenosys.so").takeIf { it.canExecute() }
    /** A start script's shell, and the preload that runs the script's webOS binaries (LunaRuntimes/pdk/execpdk). */
    val busybox: File? = File(context.applicationInfo.nativeLibraryDir, "libbusybox.so").takeIf { it.canExecute() }
    val execPdk: File? = File(context.applicationInfo.nativeLibraryDir, "libexecpdk.so").takeIf { it.isFile }
    val libDir: File? by lazy { extract() }
    /** The emulator's own libraries, extracted beside the runtime (64-bit build). */
    val qemuLibDir: File? by lazy { libDir?.let { File(it.parentFile, "qemu-lib").takeIf { d -> d.isDirectory } } }
    val available get() = loader != null || emulator != null
    /** PDL_GetHardwareID: HARDWARE_TOUCHPAD (601) on the TouchPad profile, HARDWARE_PRE_3 (501) on the phone one. */
    val hardwareId get() = if (screenWidth >= screenHeight) 601 else 501
    /** How this build runs a PDK app, for Device Info and the log. */
    val how get() = when { loader != null -> "native"; emulator != null -> "emulated"; else -> "unavailable" }

    companion object {
        /**
         * A development switch: `files/pdk/emulate` present runs PDK apps under qemu even where
         * the device runs 32-bit code, so the emulated path can be worked on with a device that
         * runs both (and compared with native on the same machine).
         */
        fun forceEmulated(context: Context) = File(context.filesDir, "pdk/emulate").exists()
        private const val PT_GNU_STACK = 0x6474e551L
        private const val PT_INTERP = 3L
        private const val PF_X = 1
        /** The loader webOS's binaries name, which the runtime provides (pdk/lib/ld-linux.so.3). */
        private const val LOADER = "/lib/ld-linux.so.3"

        /** [f], an installed app's file under [appsRoot], by its webOS path; null for any other. */
        fun webosPath(f: File, appsRoot: File): String? {
            val rel = f.path.removePrefix(appsRoot.path)
            return if (rel != f.path && rel.startsWith("/")) "/" + AppServer.APPS.trimEnd('/') + rel else null
        }

        /** Whether a package's file is a script (`#!`), run by its interpreter. */
        fun isScript(f: File): Boolean = head(f, 2).contentEquals(byteArrayOf('#'.code.toByte(), '!'.code.toByte()))

        private fun isElf(f: File): Boolean = head(f, 4).contentEquals(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))

        private fun head(f: File, n: Int): ByteArray =
            runCatching { f.inputStream().use { s -> ByteArray(n).also { if (s.read(it) != n) return ByteArray(0) } } }.getOrDefault(ByteArray(0))

        /** The other binaries in a main binary's folder, readied as main is. */
        fun readySiblings(main: File) {
            main.parentFile?.listFiles()?.filter { it.isFile && it != main && !it.name.startsWith(".lunacy.") && !it.name.contains(".so") && isElf(it) }
                ?.forEach { it.setExecutable(true, false); runnable(it) }
        }

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
         *
         * The copy also names webOS's loader, /lib/ld-linux.so.3, as its interpreter when the
         * binary names another: qemu (a 64-bit-only device) loads the interpreter the binary
         * names, and webOS CE's IM transport names CE's own, /media/cryptofs/synergy-glibc/lib/
         * ld-linux.so.3, which Lunacy doesn't have, so it exited at once on the Pixel Tablet
         * (2026-10-13). The native path runs the loader itself and never reads the name.
         */
        fun runnable(original: File): File {
            try {
                val bytes = original.readBytes()
                if (bytes.size < 52 || bytes[0] != 0x7f.toByte() || bytes[1] != 'E'.code.toByte() || bytes[4] != 1.toByte()) return original
                val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val phoff = bb.getInt(28); val phentsize = bb.getShort(42).toInt(); val phnum = bb.getShort(44).toInt()
                var changed = false
                for (i in 0 until phnum) {
                    val ph = phoff + i * phentsize
                    if (ph + 32 > bytes.size) break
                    val type = bb.getInt(ph).toLong() and 0xffffffffL
                    if (type == PT_INTERP) {
                        val off = bb.getInt(ph + 4); val size = bb.getInt(ph + 16)
                        if (off < 0 || size <= LOADER.length || off + size > bytes.size) continue
                        val name = String(bytes, off, size, Charsets.ISO_8859_1).substringBefore('\u0000')
                        if (name != LOADER) {
                            java.util.Arrays.fill(bytes, off, off + size, 0)
                            LOADER.toByteArray(Charsets.ISO_8859_1).copyInto(bytes, off)
                            changed = true
                        }
                        continue
                    }
                    if (type != PT_GNU_STACK) continue
                    val flags = bb.getInt(ph + 24)
                    if (flags and PF_X != 0) { bb.putInt(ph + 24, flags and PF_X.inv()); changed = true }
                }
                if (!changed) return original
                val copy = File(original.parentFile, ".lunacy." + original.name)
                if (!copy.isFile || copy.length() != original.length() || copy.lastModified() < original.lastModified()) {
                    copy.writeBytes(bytes)
                    Log.i(AppServer.TAG, "pdk: running a copy of ${original.name} without an executable stack and with webOS's loader")
                }
                copy.setExecutable(true, false)
                return copy
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "pdk: couldn't read ${original.name}'s headers: $e"); return original
            }
        }
    }

    /**
     * A start script readied to run as LunaSysMgr ran it, or null when this build can't: a copy
     * beside it, `.lunacy.<name>` so its own folder is still `dirname $0`, with webOS's paths
     * pointed into the webOS root as a package script's are ([WebosRoot.mapPaths]); and every
     * webOS binary in the app's folder given its execute bit and, where it needs one, its
     * runnable copy ([runnable]), since the script may run any of them.
     */
    fun scriptCopy(original: File, appDir: File): File? {
        val root = webos ?: return null
        root.prepare()
        if (busybox == null || execPdk == null || !File(root.root, "bin/sh").exists()) {
            Log.w(AppServer.TAG, "pdk: no shell for ${original.name} in this build"); return null
        }
        appDir.walkTopDown().filter { it.isFile && !it.name.startsWith(".lunacy.") && !it.name.contains(".so") && isElf(it) }.forEach {
            it.setExecutable(true, false); runnable(it)
        }
        val copy = File(original.parentFile, ".lunacy." + original.name)
        copy.writeText(root.mapPaths(original.readText()))
        copy.setExecutable(true, false)
        return copy
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
