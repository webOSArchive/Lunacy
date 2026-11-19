package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Test

/** A served file is read by its byte-order mark, as WebKit read it ([servedText]). */
class ServedTextTest {
    private val script = "var caps = \"A\u00e9\";\r\n"
    private fun read(bytes: ByteArray) = servedText(bytes.inputStream())

    @Test fun utf16WithItsMark() {
        assertEquals(script, read(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + script.toByteArray(Charsets.UTF_16LE)))
        assertEquals(script, read(byteArrayOf(0xfe.toByte(), 0xff.toByte()) + script.toByteArray(Charsets.UTF_16BE)))
    }

    @Test fun utf8WithAndWithoutAMark() {
        assertEquals(script, read(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + script.toByteArray()))
        assertEquals(script, read(script.toByteArray()))
        assertEquals("", read(ByteArray(0)))
    }

    /** Through the transform a script is served by: the page gets the script, not its mark. */
    @Test fun aUtf16ScriptIsServedAsItsText() {
        val out = JsTransforms.apply((byteArrayOf(0xff.toByte(), 0xfe.toByte()) + script.toByteArray(Charsets.UTF_16LE)).inputStream())
        assertEquals(script, out.readBytes().toString(Charsets.UTF_8))
    }
}
