package org.webosarchive.lunacy.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [ZipAssets] answers as [FolderAssets] does for the same tree: Luna DE reads its assets from
 * folders in a checkout and from one zip when packaged, and the two mustn't differ.
 */
class ZipAssetsTest {
    private val tmp: File = Files.createTempDirectory("zipassets").toFile()
    private val tree = File(tmp, "tree")

    private fun put(path: String, text: String) { File(tree, path).apply { parentFile.mkdirs(); writeText(text) } }

    /** The tree as a zip: every file, and every folder as an entry of its own (as `jar` writes them), stored or deflated. */
    private fun zipped(stored: Boolean): File {
        val out = File(tmp, if (stored) "stored.zip" else "deflated.zip")
        ZipOutputStream(out.outputStream()).use { z ->
            z.setLevel(if (stored) 0 else 9)
            tree.walkTopDown().filter { it != tree }.forEach { f ->
                val name = f.relativeTo(tree).invariantSeparatorsPath
                z.putNextEntry(ZipEntry(if (f.isDirectory) "$name/" else name))
                if (f.isFile) z.write(f.readBytes())
                z.closeEntry()
            }
        }
        return out
    }

    private fun build() {
        put("fw.links", "a b\n")
        put("fw/enyo/1.0/framework/enyo.js", "enyo")
        put("fw/enyo/1.0/framework/source/kernel/log.js", "log")
        put("apps/com.example.one/appinfo.json", "{}")
        put("apps/com.example.two/appinfo.json", "{\"id\":2}")
        put("rootfs/usr/palm/services/x/services.json", "[]")
        File(tree, "rootfs/etc/palm/empty").mkdirs()
        put("luna/images/a.png", "\u0000\u0001binary")
    }

    private fun everyPath(): List<String> = tree.walkTopDown().map { it.relativeTo(tree).invariantSeparatorsPath }.toList()

    @Test fun answersAsTheFoldersDo() {
        build()
        val folders = FolderAssets(listOf("" to tree))
        for (stored in listOf(true, false)) {
            val zip = ZipAssets(zipped(stored))
            for (path in everyPath() + listOf("nothing", "fw/nothing", "apps/com.example.one/appinfo.json/x")) {
                assertArrayEquals("list($path)", folders.list(path), zip.list(path))
                val a = try { folders.open(path).use { it.readBytes() } } catch (e: IOException) { null }
                val b = try { zip.open(path).use { it.readBytes() } } catch (e: IOException) { null }
                assertEquals("open($path) is there", a != null, b != null)
                if (a != null) assertArrayEquals("open($path)", a, b)
            }
        }
    }

    @Test fun leadingAndTrailingSlashesAreTakenAsTheFoldersTakeThem() {
        build()
        val folders = FolderAssets(listOf("" to tree)); val zip = ZipAssets(zipped(true))
        for (path in listOf("/fw", "fw/", "/fw/enyo/", "")) assertArrayEquals("list($path)", folders.list(path), zip.list(path))
        assertEquals("enyo", zip.open("/fw/enyo/1.0/framework/enyo.js").use { it.readBytes().toString(Charsets.UTF_8) })
    }

    @Test fun anEmptyFolderIsAFolder() {
        build()
        val zip = ZipAssets(zipped(true))
        assertEquals(0, zip.list("rootfs/etc/palm/empty")!!.size)
        assertNull(zip.list("rootfs/etc/palm/none"))
    }

    @Test fun severalFilesReadAtOnce() {
        build()
        val zip = ZipAssets(zipped(false))
        val one = zip.open("fw/enyo/1.0/framework/enyo.js"); val two = zip.open("fw/enyo/1.0/framework/source/kernel/log.js")
        assertTrue(two.read() == 'l'.code && one.read() == 'e'.code)
        one.close(); two.close()
    }

    @Test fun aZipWithoutFolderEntriesStillHasItsFolders() {
        build()
        val out = File(tmp, "bare.zip")
        ZipOutputStream(out.outputStream()).use { z ->
            tree.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(tree).invariantSeparatorsPath)); z.write(f.readBytes()); z.closeEntry()
            }
        }
        val zip = ZipAssets(out)
        assertArrayEquals(arrayOf("com.example.one", "com.example.two"), zip.list("apps"))
        assertArrayEquals(arrayOf("framework"), zip.list("fw/enyo/1.0"))
    }
}
