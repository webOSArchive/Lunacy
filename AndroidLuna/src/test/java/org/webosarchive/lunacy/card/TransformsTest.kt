package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The serve-time transforms, which rewrite every app's files as they are served (rule 5's one
 * allowance), and the other small rules the shell decides by. Each pins what the function does
 * now, measured against the device where its KDoc says so.
 */
class TransformsTest {
    @Test fun aSelfClosedScriptCloses() {
        // The space before the slash stays; it means nothing to the parser.
        assertEquals("""<script src="x.js" ></script><p>""", HtmlTransforms.selfClosingTags("""<script src="x.js" /><p>"""))
        assertEquals("<div class=\"a\"></div>", HtmlTransforms.selfClosingTags("<div class=\"a\"/>"))
    }

    @Test fun voidElementsAndScriptTextAreLeftAlone() {
        assertEquals("<br/><img src=\"a\" />", HtmlTransforms.selfClosingTags("<br/><img src=\"a\" />"))
        val js = "<script>var s = \"<div/>\";</script>"
        assertEquals(js, HtmlTransforms.selfClosingTags(js))
        // A '>' inside a quoted attribute isn't the tag's end.
        assertEquals("<a title=\"x>y\"></a>", HtmlTransforms.selfClosingTags("<a title=\"x>y\"/>"))
        assertEquals("<!-- <div/> -->", HtmlTransforms.selfClosingTags("<!-- <div/> -->"))
    }

    @Test fun useStrictBecomesASameLengthString() {
        val a = "\"use strict\";\nvar x = 1;"
        val b = JsTransforms.sloppyMode(a)
        assertEquals("\"not strict\";\nvar x = 1;", b)
        assertEquals(a.length, b.length)
        assertEquals("function f() { /* c */ 'not strict'; }", JsTransforms.sloppyMode("function f() { /* c */ 'use strict'; }"))
        // Not a directive: a string in the middle of an expression stays.
        assertEquals("var s = x + \"use strict\";", JsTransforms.sloppyMode("var s = x + \"use strict\";"))
    }

    @Test fun mouseTargetIgnoreIsPointerEventsForTheElementOnly() {
        assertEquals(".t{color:red;-webkit-palm-mouse-target:ignore;pointer-events:none}.t > *{pointer-events:auto}",
            CssTransforms.mouseTargetIgnore(".t{color:red;-webkit-palm-mouse-target:ignore}"))
        assertEquals(".a,.b{-webkit-palm-mouse-target: ignore;pointer-events:none}.a > *,.b > *{pointer-events:auto}",
            CssTransforms.mouseTargetIgnore(".a,.b{-webkit-palm-mouse-target: ignore}"))
        assertEquals(".t{color:red}", CssTransforms.mouseTargetIgnore(".t{color:red}"))
    }

    @Test fun aBorderImageGetsABorderStyle() {
        assertEquals(".b{-webkit-border-image:url(x.png) 4 4 4 4;border-style:solid;border-color:transparent}",
            CssTransforms.borderImageNeedsStyle(".b{-webkit-border-image:url(x.png) 4 4 4 4}"))
        assertEquals(".b{-webkit-border-image: none}", CssTransforms.borderImageNeedsStyle(".b{-webkit-border-image: none}"))
    }

    @Test fun palmAppsArePrivileged() {
        assertTrue(Bus.privileged("com.palm.app.email"))
        assertFalse(Bus.privileged("org.webosinternals.preware"))
        assertFalse(Bus.privileged("com.palmx.app"))
    }

    @Test fun phoneOrTablet() {
        // Both clues agree.
        assertEquals(FormFactor.Kind.PHONE, FormFactor.Clues(1080, 1920, 360, 5.0, 1920.0 / 1080).kind)
        assertEquals(FormFactor.Kind.TABLET, FormFactor.Clues(1200, 1920, 800, 10.1, 1.6).kind)
        // They disagree, and the shape decides: a large phone reporting a loose density...
        assertEquals(FormFactor.Kind.PHONE, FormFactor.Clues(1080, 2400, 640, 6.7, 2400.0 / 1080).kind)
        // ...and a small tablet set dense.
        assertEquals(FormFactor.Kind.TABLET, FormFactor.Clues(800, 1280, 533, 8.0, 1.6).kind)
    }
}
