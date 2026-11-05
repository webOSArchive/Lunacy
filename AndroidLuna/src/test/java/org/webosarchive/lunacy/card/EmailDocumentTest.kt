package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An email's text as a browser view shows it (EmailDocument). */
class EmailDocumentTest {
    @Test fun onlyTheFirstFamilyCountsAndAnUnknownOneIsPrelude() {
        assertEquals("<p style=\"font-family:Prelude\">", EmailDocument.touchpadFonts("<p style=\"font-family:'Proxima Nova', system-ui, Arial, sans-serif\">"))
        assertEquals("<p style=\"font-family:'Arial'\">", EmailDocument.touchpadFonts("<p style=\"font-family:Arial, sans-serif\">"))
        assertEquals("b{font-family:'Prelude Medium' !important}", EmailDocument.touchpadFonts("b{font-family:Prelude Medium, Arial !important}"))
        // The shorthand: the families follow the size.
        assertEquals("p{font:12px/1.4 'Georgia'}", EmailDocument.touchpadFonts("p{font:12px/1.4 Georgia, serif}"))
    }

    @Test fun escapingAndLinks() {
        assertEquals("a &lt;b&gt; &amp; c", EmailDocument.escape("a <b> & c"))
        assertEquals("<\"&é", EmailDocument.unescape("&lt;&quot;&amp;&#233;"))
        assertEquals("see <a href=\"https://x.org/a?b=1\">https://x.org/a?b=1</a> now", EmailDocument.linkify("see https://x.org/a?b=1 now"))
    }

    @Test fun linksLoseTheirTargets() {
        assertEquals("<a href=\"http://x/\">x</a>", EmailDocument.sameWindowLinks("<a href=\"http://x/\" target=\"_blank\">x</a>"))
        assertEquals("<A HREF='y'>y</A>", EmailDocument.sameWindowLinks("<A TARGET='_new' HREF='y'>y</A>"))
        assertEquals("<a href=z>z</a>", EmailDocument.sameWindowLinks("<a href=z target=_blank>z</a>"))
        assertEquals("<base href=\"http://b/\">", EmailDocument.sameWindowLinks("<base href=\"http://b/\" target=\"_blank\">"))
        assertEquals("<area href=\"m\">", EmailDocument.sameWindowLinks("<area href=\"m\" target=\"_top\">"))
        // Only links: a form keeps its target, and text that says target= is left alone.
        val other = "<form target=\"_blank\"></form><p>target=\"_blank\"</p><abbr target=\"x\">a</abbr>"
        assertEquals(other, EmailDocument.sameWindowLinks(other))
    }

    @Test fun insertingAfterATag() {
        assertEquals("<head x><meta></head>", EmailDocument.insertAfter("<head x></head>", "<head[^>]*>", "<meta>"))
        assertNull(EmailDocument.insertAfter("<body></body>", "<head[^>]*>", "<meta>"))
    }
}
