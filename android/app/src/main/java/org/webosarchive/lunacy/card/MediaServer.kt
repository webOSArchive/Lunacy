package org.webosarchive.lunacy.card

import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Serves /media/internal read-only over HTTP on the loopback interface, for media players.
 * Pages played file:///media/internal/... on webOS (JS services leave files there, like HLS
 * playlists). Chromium hands HLS to Android's MediaPlayer, which runs in the mediaserver process
 * with its own network stack and can't reach app origins, so media URLs for those files point
 * here. Byte ranges are supported, as players seek. The path starts with a random token.
 * See docs/architecture.md, "JS services".
 */
class MediaServer(private val webosRoot: File) {
    private val token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    @Volatile private var socket: ServerSocket? = null

    /** http://127.0.0.1:<port>/<token>, started on first use. */
    @Synchronized fun base(): String {
        val s = socket ?: ServerSocket(0, 16, InetAddress.getByName("127.0.0.1")).also { socket = it; accept(it) }
        return "http://127.0.0.1:${s.localPort}/$token"
    }

    private fun accept(s: ServerSocket) = thread(name = "media-server", isDaemon = true) {
        while (!s.isClosed) {
            val c = try { s.accept() } catch (e: Exception) { break }
            thread(name = "media-conn", isDaemon = true) { c.use { serve(it) } }
        }
    }

    private fun serve(c: Socket) {
        try {
            val input = c.getInputStream()
            val request = readLine(input) ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val l = readLine(input) ?: return
                if (l.isEmpty()) break
                val i = l.indexOf(':')
                if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            val parts = request.split(' ')
            val method = parts.getOrNull(0) ?: ""
            val path = URLDecoder.decode(parts.getOrNull(1)?.substringBefore('?') ?: "", "UTF-8")
            val prefix = "/$token/media/internal/"
            val file = if (path.startsWith(prefix)) UserFiles.resolve(webosRoot, path.removePrefix(prefix)) else null
            val out = c.getOutputStream()
            if (method != "GET" && method != "HEAD") return status(out, 405, "Method Not Allowed")
            if (file == null || !file.isFile) return status(out, 404, "Not Found")
            val len = file.length()
            val range = Regex("bytes=(\\d*)-(\\d*)").find(headers["range"] ?: "")
            var start = 0L
            var end = len - 1
            if (range != null) {
                val (a, b) = range.destructured
                if (a.isEmpty()) { start = maxOf(0, len - (b.toLongOrNull() ?: 0)) } else { start = a.toLong(); if (b.isNotEmpty()) end = minOf(b.toLong(), len - 1) }
                if (start >= len) return status(out, 416, "Range Not Satisfiable")
            }
            val count = end - start + 1
            val head = StringBuilder()
            head.append(if (range != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            head.append("Content-Type: ${typeOf(file.name)}\r\nContent-Length: $count\r\nAccept-Ranges: bytes\r\n")
            if (range != null) head.append("Content-Range: bytes $start-$end/$len\r\n")
            head.append("Cache-Control: no-cache\r\nConnection: close\r\n\r\n")
            out.write(head.toString().toByteArray())
            if (method == "GET") file.inputStream().use { i ->
                i.skip(start)
                val buf = ByteArray(64 * 1024)
                var left = count
                while (left > 0) {
                    val n = i.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n); left -= n
                }
            }
            out.flush()
        } catch (e: Exception) {
            Log.i(AppServer.TAG, "media server: $e")
        }
    }

    private fun status(out: OutputStream, code: Int, text: String) {
        out.write("HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun typeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "m3u8" -> "application/vnd.apple.mpegurl"; "ts" -> "video/mp2t"; "mp4", "m4v" -> "video/mp4"
        "mp3" -> "audio/mpeg"; "m4a", "aac" -> "audio/mp4"; "ogg" -> "audio/ogg"; "wav" -> "audio/wav"
        "webm" -> "video/webm"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"
        else -> "application/octet-stream"
    }
}
