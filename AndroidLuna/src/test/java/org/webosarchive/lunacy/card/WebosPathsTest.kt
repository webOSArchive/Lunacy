package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Test

/** WebosRoot.mapPaths: webOS paths in a command line, pointed into the root. */
class WebosPathsTest {
    private fun map(s: String) = WebosRoot.mapPaths(s, "/r")

    @Test fun mapsWebosFolders() {
        assertEquals("ls /r/usr/bin", map("ls /usr/bin"))
        assertEquals("cat '/r/etc/palm/x.conf'", map("cat '/etc/palm/x.conf'"))
        assertEquals("X=/r/var/luna", map("X=/var/luna"))
        assertEquals("cd /r/lib", map("cd /lib"))
        assertEquals("a;/r/tmp/b", map("a;/tmp/b"))
    }

    @Test fun leavesOtherFoldersAlone() {
        assertEquals("ls /usrx", map("ls /usrx"))
        assertEquals("ls /library/x", map("ls /library/x"))
        assertEquals("ls /data/local", map("ls /data/local"))
    }

    @Test fun leavesUrlsAlone() {
        assertEquals("open file:///usr/palm/x.html", map("open file:///usr/palm/x.html"))
    }
}
