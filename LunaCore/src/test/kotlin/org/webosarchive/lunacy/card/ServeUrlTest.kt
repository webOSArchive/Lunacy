package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An app-origin URL read as android.net.Uri reads one: by its delimiters, and never refused ([ServeUrl]). */
class ServeUrlTest {
    private val origin = "https://com.palm.app.messaging.media.cryptofs.apps"

    /**
     * The shell's sound lookup sends a device path as it is. java.net.URI threw on the space, the
     * URL had no host, and a chosen ringtone played the class's default sound (2026-11-17).
     */
    @Test fun aPathWithSpacesAndBracketsIsRead() {
        val u = ServeUrl("$origin/media/internal/ringtones/Cymbells (short).mp3")
        assertEquals("com.palm.app.messaging.media.cryptofs.apps", u.host)
        assertEquals("/media/internal/ringtones/Cymbells (short).mp3", u.path)
        assertEquals("/media/internal/ringtones/Cymbells (short).mp3", u.encodedPath)
        assertEquals("com.palm.app.messaging", AppServer.appIdOf("$origin/media/internal/ringtones/Cymbells (short).mp3"))
    }

    @Test fun anEncodedPathIsDecodedAndKeptAsSent() {
        val u = ServeUrl("$origin/media/internal/ringtones/Cymbells%20(short).mp3?x=a%20b&y#frag?not")
        assertEquals("/media/internal/ringtones/Cymbells (short).mp3", u.path)
        assertEquals("/media/internal/ringtones/Cymbells%20(short).mp3", u.encodedPath)
        assertEquals("a b", u.getQueryParameter("x"))
        assertEquals("", u.getQueryParameter("y"))
        assertNull(u.getQueryParameter("z"))
    }

    @Test fun otherCharactersJavaNetUriRefuses() {
        for (name in listOf("a|b.png", "50%.txt", "café \"x\".mp3", "{a}^`.js", "a[1].js")) {
            val u = ServeUrl("$origin/media/internal/$name")
            assertEquals(name, "com.palm.app.messaging.media.cryptofs.apps", u.host)
            assertEquals(name, "/media/internal/$name", u.path)
        }
    }

    @Test fun theAuthoritysParts() {
        assertEquals("example.com", ServeUrl("http://user:pw@example.com:8080/a").host)
        assertEquals("[::1]", ServeUrl("http://[::1]:8080/a").host)
        assertEquals("[::1]", ServeUrl("http://[::1]/a").host)
        assertEquals("", ServeUrl("http://example.com").path)
        assertEquals("example.com", ServeUrl("http://example.com?q=1").host)
        assertEquals("1", ServeUrl("http://example.com?q=1").getQueryParameter("q"))
    }

    @Test fun whatIsNotAnOriginUrl() {
        assertNull(ServeUrl("").host)
        assertNull(ServeUrl("/media/internal/a b.txt").host)
        assertEquals("/media/internal/a b.txt", ServeUrl("/media/internal/a b.txt").path)
        assertNull(ServeUrl("file:///media/internal/a.txt").host.takeUnless { it == "" })
        assertEquals("/media/internal/a.txt", ServeUrl("file:///media/internal/a.txt").path)
        assertNull(AppServer.appIdOf("about:blank"))
        assertNull(AppServer.appIdOf("https://example.com/x y"))
    }
}
