package org.webosarchive.lunacy.card

import org.webosarchive.lunacy.core.Assets
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Platform
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The files of Lunacy's webOS root: the ROM laid down from the bundled rootfs/, the folders every
 * TouchPad has, and /etc/palm-build-info. What WebosRoot did on Android before LunaCore, and what
 * Luna DE's host does with the same code (Docs/luna-de.md); the commands in the root (busybox,
 * curl, luna-send) are each platform's own.
 *
 * **The ROM.** What Lunacy ships under `rootfs/` is laid down like a device's ROM, and like a
 * package manager's config files it is updated with care: after an update a file is replaced only
 * if it still holds what the last build shipped. A file a package's script changed (the webOS
 * Community Account Manager patches the palmprofile service in place, as it does on a TouchPad)
 * is kept, and the log says so.
 */
class RomFiles(private val assets: Assets, val root: File) {
    private val links get() = Platform.links

    /**
     * The folders every TouchPad has, which scripts write into without making them, and
     * /media/cryptofs/apps as a link to where packages are installed. media/internal/.developer
     * is developer mode's: the SDK's palm-install puts the package there before installing it,
     * and every Lunacy is in developer mode.
     */
    fun makeFolders(installed: File) {
        for (d in FOLDERS) File(root, d).mkdirs()
        link(installed.path, File(root, "media/cryptofs/apps"))
    }

    /**
     * `/etc/palm-build-info`, which the SDK's tools read before anything else to learn what
     * they are talking to. A device's said `PRODUCT_VERSION_STRING=webOS CE 3.1.0` (the
     * reference TouchPad); Lunacy's says it is Lunacy, and webos-sdk-redux recognises it by
     * name rather than Lunacy passing itself off as a TouchPad (codepoet, 2026-10-05).
     */
    fun writeBuildInfo(versionName: String, buildNumber: String) {
        val text = "PRODUCT_VERSION_STRING=Lunacy $versionName\nBUILDNAME=Lunacy\nBUILDNUMBER=$buildNumber\n"
        val f = File(root, "etc/palm-build-info")
        if (!f.isFile || f.readText() != text) { f.parentFile?.mkdirs(); f.writeText(text) }
    }

    /** A symlink at [at] to [target], unless something is there already. */
    fun link(target: String, at: File) {
        if (at.exists() || links.readlink(at.path) != null) return
        at.parentFile?.mkdirs()
        links.symlink(target, at.path)
    }

    /** Lays assets/rootfs/ into root/, keeping files changed since the last APK laid them down. */
    fun syncRom() {
        val manifest = File(root, ROM_MANIFEST)
        val before = if (manifest.isFile) manifest.readLines().mapNotNull { l ->
            l.split(' ', limit = 2).takeIf { it.size == 2 }?.let { it[1] to it[0] }
        }.toMap() else emptyMap()
        val now = LinkedHashMap<String, String>()
        var kept = 0
        // fetch-assets lists the ROM's files: walking it with AssetManager.list() instead took
        // most of a minute on the HP tablet, because each call reads the APK's whole asset index.
        val index = try { assets.open("$ROM.index").bufferedReader().readLines().filter { it.isNotBlank() } }
            catch (e: IOException) { ArrayList<String>().also { l -> walkAssets(ROM) { l += it } } }
        for (rel in index) {
            // A file the index lists but the APK doesn't carry is skipped, not fatal: aapt drops
            // some names by itself (dotfiles), and the shell must still start.
            val bytes = try { assets.open("$ROM/$rel").use { it.readBytes() } }
                catch (e: IOException) { Log.w(AppServer.TAG, "rootfs: $rel is listed but not in the APK"); continue }
            val sum = sha1(bytes)
            now[rel] = sum
            val f = File(root, rel)
            val onDisk = if (f.isFile) sha1(f.readBytes()) else null
            val shipped = before[rel]
            if (onDisk == null || onDisk == shipped || shipped == null) {
                if (onDisk != sum) { f.parentFile?.mkdirs(); f.writeBytes(bytes) }
            } else if (onDisk != sum) {
                kept++
                Log.i(AppServer.TAG, "rootfs: kept /$rel, changed since Lunacy shipped it")
            }
        }
        // Files the ROM no longer has go too, unless something changed them.
        for ((rel, sum) in before) if (rel !in now) {
            val f = File(root, rel)
            if (f.isFile && sha1(f.readBytes()) == sum) f.delete()
        }
        manifest.parentFile?.mkdirs()
        manifest.writeText(now.entries.joinToString("") { "${it.value} ${it.key}\n" })
        // The ROM's symlinks (fetch-assets' rootfs.links), made as the device had them -
        // version/1.0 -> ../submission/48 - since an APK can't carry a link. An earlier ROM
        // laid the link's target down in its place; that copy goes.
        val links = try { assets.open("$ROM.links").bufferedReader().readLines() } catch (e: IOException) { emptyList() }
        for (l in links) {
            val (rel, target) = l.split(' ', limit = 2).takeIf { it.size == 2 } ?: continue
            relink(relativePath(rel.substringBeforeLast('/', ""), target), File(root, rel))
        }
        Log.i(AppServer.TAG, "rootfs: ${now.size} files and ${links.size} links from the ROM, $kept kept as changed")
    }

    /** A symlink at [at] to [target], replacing whatever was there unless it is that link already. */
    private fun relink(target: String, at: File) {
        val now = links.readlink(at.path)
        if (now == target) return
        if (now != null) at.delete()
        else if (at.isDirectory) at.deleteRecursively()
        else if (at.exists()) at.delete()
        at.parentFile?.mkdirs()
        links.symlink(target, at.path)
    }

    /** [to] as a relative path from the folder [from], both relative to the root: ("a/b/version", "a/b/submission/48") is "../submission/48". */
    private fun relativePath(from: String, to: String): String {
        val f = if (from.isEmpty()) emptyList() else from.split('/')
        val t = to.split('/')
        var common = 0
        while (common < f.size && common < t.size && f[common] == t[common]) common++
        return (List(f.size - common) { ".." } + t.drop(common)).joinToString("/")
    }

    private fun walkAssets(dir: String, rel: String = "", each: (String) -> Unit) {
        val path = if (rel.isEmpty()) dir else "$dir/$rel"
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) { if (rel.isNotEmpty()) each(rel); return }
        children.forEach { walkAssets(dir, if (rel.isEmpty()) it else "$rel/$it", each) }
    }

    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }

    fun copyAssets(from: String, to: File) {
        val children = assets.list(from).orEmpty()
        if (children.isEmpty()) {
            to.parentFile?.mkdirs()
            assets.open(from).use { i -> to.outputStream().use { i.copyTo(it) } }
        } else children.forEach { copyAssets("$from/$it", File(to, it)) }
    }

    companion object {
        const val ROM = "rootfs"
        const val ROM_MANIFEST = ".lunacy-rom"
        val FOLDERS = listOf("media/internal", "media/internal/.developer", "tmp", "var/tmp", "var/run", "var/log", "var/palm", "var/luna/preferences", "var/luna/data", "var/preferences", "var/file-cache", "home/root", "bin", "usr/bin",
            "sbin", "usr/sbin", "usr/lib", "usr/palm/applications", "usr/palm/services", "usr/palm/public",
            "usr/palm/frameworks", "etc/palm", "etc/event.d", "etc/udev/rules.d")
    }
}
