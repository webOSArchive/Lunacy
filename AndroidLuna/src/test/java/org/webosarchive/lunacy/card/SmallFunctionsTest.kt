package org.webosarchive.lunacy.card

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SmallFunctionsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun versions() {
        assertTrue(isNewerVersion("1.2.10", "1.2.9"))
        assertFalse(isNewerVersion("1.2.9", "1.2.10"))
        assertFalse(isNewerVersion("1.0", "1.0.0"))
        // A part that isn't a number counts as 0.
        assertFalse(isNewerVersion("1.0.1b", "1.0.0"))
    }

    @Test fun busRedactsSecrets() {
        assertEquals("""{"password":"…","user":"x"}""", Bus.redact("""{"password":"a\"b","user":"x"}"""))
        assertEquals("""{"accessToken" : "…"}""", Bus.redact("""{"accessToken" : "t"}"""))
    }

    @Test fun busReplies() {
        assertTrue(Bus.privileged("com.palm.app.email"))
        assertFalse(Bus.privileged("org.webosarchive.x"))
        val e = JSONObject(Bus.error("x"))
        assertFalse(e.getBoolean("returnValue")); assertEquals(-1, e.getInt("errorCode")); assertEquals("x", e.getString("errorText"))
    }

    @Test fun lineSeparatorsAreEscapedForScripts() {
        assertEquals("a\\u2028b\\u2029c", jsSafe("a b c"))
    }

    private fun app(devices: String): File = tmp.newFolder().also {
        File(it, "metadata.json").writeText("""{"version":"1.0.0","devices":$devices}""")
    }

    @Test fun portability() {
        assertEquals(601, Portability.nappId(501))
        assertEquals(701, Portability.nappId(601))
        // A Pre3-only app on the TouchPad (PDL 601) runs as a Pre3: PDL 501 at 480 x 800.
        val d = Portability.emulate(app("[601]"), 601)!!
        assertEquals(501, d.id); assertEquals(480, d.width); assertEquals(800, d.height)
        assertNull(Portability.emulate(app("[701]"), 601))
        assertNull(Portability.emulate(tmp.newFolder(), 601))
    }
}
