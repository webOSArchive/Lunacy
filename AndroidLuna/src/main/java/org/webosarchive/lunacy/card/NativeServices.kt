package org.webosarchive.lunacy.card

import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Native system services: the TouchPad's own C++ services (mojomail-imap, mojomail-smtp,
 * filecache), run as they shipped. On webOS, ls-hubd started a service the first time it was
 * called, from its D-Bus service file (`/usr/share/dbus-1/system-services/<name>.service`:
 * `Name=` and an `Exec=` command line). Lunacy reads the same files from the webOS root, and
 * the first call starts the binary through the PDK runtime's glibc loader (natively, under
 * libenosys on Android 10 and later, or under qemu on a 64-bit-only device), with Lunacy's
 * liblunaservice.so carrying the bus over the process's stdin and stdout ([ServiceProcess]).
 * See Docs/architecture.md, "Native services".
 *
 * The command line is the device's: its `env` assignments become the environment, with the
 * loader's own variables (LD_LIBRARY_PATH, LD_PRELOAD) looked up in the webOS root, since the
 * loader reads Android's filesystem; every other webOS path the service opens is found there
 * by the runtime's preload. JS services' files (Exec through run-js-service) are JsServices'.
 */
class NativeServices(private val bus: Bus, private val webos: WebosRoot, private val runtime: () -> PdkRuntime) {
    private class Spec(val name: String, val env: Map<String, String>, val exec: String, val args: List<String>)

    private val worker = Executors.newSingleThreadExecutor()
    /** Bus name to its service file's spec. Main thread. */
    private val specs = HashMap<String, Spec>()
    /** Running services, by bus name. Main thread. */
    private val running = HashMap<String, ServiceProcess>()
    private var signatures = HashMap<String, String>()

    /** Registers every native service file's name, and stops a running one whose file changed or went. */
    fun reload() {
        specs.keys.forEach { bus.unregisterService(it) }
        specs.clear()
        val seen = HashMap<String, String>()
        File(webos.root, SERVICE_FILES).listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (!f.name.endsWith(".service")) return@forEach
            val text = try { f.readText() } catch (e: IOException) { return@forEach }
            val spec = parse(text) ?: return@forEach
            specs[spec.name] = spec
            seen[spec.name] = text
            bus.registerService(spec.name, Bus.CallHandler { call(it) })
        }
        for ((name, p) in running.toList()) if (seen[name] != signatures[name]) p.stop()
        signatures = seen
        if (specs.isNotEmpty()) Log.i(AppServer.TAG, "native services: ${specs.keys.sorted()}")
    }

    fun stopAll() { running.values.toList().forEach { it.stop() } }

    private fun call(call: Bus.Call) {
        val spec = specs[call.service] ?: return call.reply(Bus.error("Service does not exist: ${call.service}."))
        val p = running[spec.name] ?: ServiceProcess(bus, spec.name, "native service", worker,
            start = { launch(spec) }, onEnded = { ended -> if (running[spec.name] === ended) running.remove(spec.name) })
            .also { running[spec.name] = it }
        p.request(call)
    }

    private fun launch(spec: Spec): Process {
        webos.prepare()
        val rt = runtime()
        val lib = rt.libDir ?: throw IOException("no PDK runtime in this build")
        val loader = rt.loader
        val emulator = rt.emulator ?: if (loader == null) throw IOException("this build can't run 32-bit ARM binaries") else null
        val root = webos.root
        fun real(p: String) = if (p.startsWith("/")) File(root, p.substring(1)).path else p
        val original = File(real(spec.exec))
        if (!original.isFile) throw IOException("${spec.exec} isn't in the webOS root")
        // The ROM lays files down without their execute bit. The loader doesn't need it, but
        // qemu (a 64-bit-only device) refuses a binary without it, as "Exec format error".
        original.setExecutable(true, false)
        val binary = PdkRuntime.withoutExecStack(original)
        // Development: files/pdk/args/<bus name> holds a command line that replaces the
        // service file's arguments (mojomail's own logging to stdout at debug level, which
        // reaches /var/log/messages: '-c {"log":{"appender":{"type":"stdout"},…}}').
        val args = File(rt.libDir!!.parentFile, "args/${spec.name}").takeIf { it.isFile }?.readText()?.trim()?.let { split(it) } ?: spec.args
        // The loader's search: the service's own folders first (ssl11mail for the mail
        // services), then the runtime's glibc and liblunaservice, then the root's libraries.
        val own = spec.env["LD_LIBRARY_PATH"]?.split(':')?.filter { it.isNotEmpty() }?.map { real(it) }.orEmpty()
        val libraryPath = (own + listOf(lib.path, File(root, "usr/lib").path, File(root, "lib").path)).joinToString(":")
        val preload = (listOf(File(lib, "liblunacy-preload.so").path) +
            spec.env["LD_PRELOAD"]?.split(':', ' ')?.filter { it.isNotEmpty() }?.map { real(it) }.orEmpty()).joinToString(":")
        val enosys = rt.enosys?.takeIf { loader != null && android.os.Build.VERSION.SDK_INT >= 29 }
        val command = if (loader != null) listOfNotNull(enosys?.path, loader.path, "--library-path", libraryPath, binary.path) + args
            else listOf(emulator!!.path, "-L", lib.parentFile!!.path, "-E", "LD_LIBRARY_PATH=$libraryPath", "-E", "LD_PRELOAD=$preload") +
                (if (spec.env["LD_BIND_NOW"] != null) listOf("-E", "LD_BIND_NOW=1") else emptyList()) + listOf(binary.path) + args
        val pb = ProcessBuilder(command).directory(File(root, "home/root").also { it.mkdirs() })
        pb.environment().apply {
            putAll(webos.environment(spec.name))
            for ((k, v) in spec.env) if (k != "LD_LIBRARY_PATH" && k != "LD_PRELOAD") put(k, v)
            if (loader == null) rt.qemuLibDir?.let { put("LD_LIBRARY_PATH", it.path) }
            else { remove("LD_LIBRARY_PATH"); put(if (enosys != null) "LUNACY_PDK_PRELOAD" else "LD_PRELOAD", preload) }
            put("LUNACY_PDK_EXE", binary.path)
            put("LUNACY_PDK_ROOT", root.path)
            // liblunaservice takes stdin and stdout as the bus link.
            put("LUNACY_BUS_STDIO", "1")
        }
        Log.i(AppServer.TAG, "native service ${spec.name}: ${spec.exec} ${args.joinToString(" ")}")
        return pb.start()
    }

    companion object {
        const val SERVICE_FILES = "usr/share/dbus-1/system-services"

        /** A service file's name and command line, or null for one Lunacy doesn't run natively. */
        private fun parse(text: String): Spec? {
            val keys = text.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it).trim() to l.substring(it + 1).trim() } }.toMap()
            val name = keys["Name"] ?: return null
            var words = split(keys["Exec"] ?: return null)
            val env = LinkedHashMap<String, String>()
            if (words.firstOrNull() == "/usr/bin/env") {
                words = words.drop(1)
                while (words.isNotEmpty() && Regex("^[A-Za-z_][A-Za-z0-9_]*=").containsMatchIn(words[0])) {
                    val w = words[0]; env[w.substringBefore('=')] = w.substringAfter('='); words = words.drop(1)
                }
            }
            val exec = words.firstOrNull() ?: return null
            // JS services start through run-js-service or node: JsServices runs those.
            if (exec.endsWith("run-js-service") || exec.endsWith("/node")) return null
            return Spec(name, env, exec, words.drop(1))
        }

        /** A command line's words, with sh's single and double quotes. */
        fun split(line: String): List<String> {
            val out = ArrayList<String>(); val w = StringBuilder(); var quote = 0.toChar(); var any = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quote != 0.toChar() -> if (c == quote) quote = 0.toChar() else if (c == '\\' && quote == '"' && i + 1 < line.length) { w.append(line[++i]) } else w.append(c)
                    c == '\'' || c == '"' -> { quote = c; any = true }
                    c == '\\' && i + 1 < line.length -> { w.append(line[++i]); any = true }
                    c.isWhitespace() -> { if (w.isNotEmpty() || any) { out += w.toString(); w.clear(); any = false } }
                    else -> w.append(c)
                }
                i++
            }
            if (w.isNotEmpty() || any) out += w.toString()
            return out
        }
    }
}
