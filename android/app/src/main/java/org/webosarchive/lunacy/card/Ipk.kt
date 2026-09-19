package org.webosarchive.lunacy.card

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Reads a webOS .ipk: an `ar` archive (or, from some packagers, a tar.gz) holding
 * debian-binary, control.tar.gz and data.tar.gz. Only data.tar.gz is unpacked: its tree is the
 * package's files relative to /media/cryptofs/apps. See docs/architecture.md, "Package manager".
 */
object Ipk {
    class BadPackage(msg: String) : IOException(msg)

    /** Unpacks the package's data.tar.gz into dest. Returns the relative paths of the files written. */
    fun extract(ipk: File, dest: File): List<String> {
        BufferedInputStream(ipk.inputStream()).use { input ->
            input.mark(8)
            val magic = ByteArray(8).also { input.readFully(it) }
            input.reset()
            val data = when {
                String(magic, Charsets.ISO_8859_1) == "!<arch>\n" -> arMember(input, "data.tar.gz")
                magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> tarMember(GZIPInputStream(input), "data.tar.gz")
                else -> null
            } ?: throw BadPackage("no data.tar.gz")
            return untar(GZIPInputStream(data), dest)
        }
    }

    /** Positions input at the named member of an ar archive and returns a stream bounded to it. */
    private fun arMember(input: InputStream, name: String): InputStream? {
        input.skipFully(8)
        val h = ByteArray(60)
        while (true) {
            if (!input.readFullyOrEof(h)) return null
            val member = String(h, 0, 16, Charsets.ISO_8859_1).trim().removeSuffix("/")
            val size = String(h, 48, 10, Charsets.ISO_8859_1).trim().toLongOrNull() ?: throw BadPackage("bad ar header")
            if (member == name) return Bounded(input, size)
            input.skipFully(size + (size and 1))
        }
    }

    /** The outer tar.gz form: finds the named member and returns a stream bounded to it. */
    private fun tarMember(input: InputStream, name: String): InputStream? {
        var found: InputStream? = null
        readTar(input) { path, type, size, body ->
            if (type == '0' && path == name) { found = Bounded(body, size); false } else true
        }
        return found
    }

    private fun untar(input: InputStream, dest: File): List<String> {
        val root = dest.canonicalFile
        val written = ArrayList<String>()
        readTar(input) { path, type, size, body ->
            if (path.isEmpty()) return@readTar true
            // Entries are ./usr/palm/...; anything absolute or climbing out is refused.
            val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.any { it == ".." }) throw BadPackage("unsafe path in package: $path")
            val out = File(root, parts.joinToString("/"))
            when (type) {
                '5' -> out.mkdirs()
                '0' -> {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { o -> Bounded(body, size).copyTo(o) }
                    written += parts.joinToString("/")
                }
                // Links and devices aren't part of any app's contract; skipped, and logged by the caller.
                else -> {}
            }
            true
        }
        return written
    }

    /**
     * Walks a tar stream (ustar, with GNU long names and pax paths). For each entry, visit gets
     * its path, type ('0' file, '5' directory, others as in the header), size and the stream
     * positioned at its body; returning false stops the walk. Unread body bytes are skipped.
     */
    private fun readTar(input: InputStream, visit: (String, Char, Long, InputStream) -> Boolean) {
        val h = ByteArray(512)
        var longName: String? = null
        while (true) {
            if (!input.readFullyOrEof(h) || h.all { it == 0.toByte() }) return
            val type = (h[156].toInt() and 0xff).toChar().let { if (it == '\u0000' || it == '7') '0' else it }
            val size = octal(h, 124, 12)
            val padded = (size + 511) / 512 * 512
            when (type) {
                'L' -> { longName = String(input.readBytes(size), Charsets.UTF_8).trimEnd('\u0000'); input.skipFully(padded - size); continue }
                'x' -> {
                    val pax = String(input.readBytes(size), Charsets.UTF_8)
                    Regex("\\d+ path=([^\n]*)\n").find(pax)?.let { longName = it.groupValues[1] }
                    input.skipFully(padded - size); continue
                }
                'g', 'K' -> { input.skipFully(padded); continue }
            }
            val name = longName ?: run {
                val n = cString(h, 0, 100)
                val prefix = if (String(h, 257, 5, Charsets.ISO_8859_1) == "ustar") cString(h, 345, 155) else ""
                if (prefix.isEmpty()) n else "$prefix/$n"
            }
            longName = null
            val body = Bounded(input, size)
            if (!visit(name, type, size, body)) return
            body.skipRest()
            input.skipFully(padded - size)
        }
    }

    private fun cString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        if (b[off].toInt() and 0x80 != 0) {  // GNU base-256 for large sizes
            var v = 0L
            for (i in off + 1 until off + len) v = (v shl 8) or (b[i].toLong() and 0xff)
            return v
        }
        return cString(b, off, len).trim().ifEmpty { "0" }.toLong(8)
    }

    private fun InputStream.readFully(b: ByteArray) { if (!readFullyOrEof(b)) throw EOFException() }

    /** False at a clean end of stream; throws if the stream ends part-way. */
    private fun InputStream.readFullyOrEof(b: ByteArray): Boolean {
        var n = 0
        while (n < b.size) {
            val r = read(b, n, b.size - n)
            if (r < 0) { if (n == 0) return false else throw EOFException() }
            n += r
        }
        return true
    }

    private fun InputStream.readBytes(n: Long): ByteArray = ByteArray(n.toInt()).also { readFully(it) }

    private fun InputStream.skipFully(n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (r < 0) throw EOFException()
            left -= r
        }
    }

    /** A view of the next `size` bytes of a stream. Closing it doesn't close the stream. */
    private class Bounded(input: InputStream, private var left: Long) : FilterInputStream(input) {
        override fun read(): Int = if (left <= 0) -1 else super.read().also { if (it >= 0) left-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val r = super.read(b, off, minOf(len.toLong(), left).toInt())
            if (r > 0) left -= r
            return r
        }
        override fun available() = minOf(super.available().toLong(), left).toInt()
        override fun markSupported() = false
        override fun close() {}
        fun skipRest() { val buf = ByteArray(8192); while (read(buf) >= 0) {} }
    }
}
