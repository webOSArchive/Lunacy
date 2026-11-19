package org.webosarchive.lunacy.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** LunaCore's Base64 against the JDK's (which Android 5 hasn't got, hence the codec). */
class Base64CodecTest {
    @Test fun matchesTheJdk() {
        val r = java.util.Random(7)
        for (n in 0..300) {
            val b = ByteArray(n).also { r.nextBytes(it) }
            val text = java.util.Base64.getEncoder().encodeToString(b)
            assertEquals(text, Base64Codec.encode(b))
            assertArrayEquals(b, Base64Codec.decode(text))
        }
    }

    @Test fun decodesAsBase64DefaultDid() {
        // Line breaks and spaces passed over, padding optional, the URL-safe alphabet read too.
        assertArrayEquals("hello world".toByteArray(), Base64Codec.decode("aGVsbG8g\nd29y bGQ"))
        assertArrayEquals(byteArrayOf(-5, -1), Base64Codec.decode("-_8"))
    }
}
