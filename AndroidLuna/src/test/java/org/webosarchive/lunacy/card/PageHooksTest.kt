package org.webosarchive.lunacy.card

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every function the shell calls in a page is defined on the page's side: in Lunacy's injected
 * scripts or in a framework patch. A call guards on the function being there, so without this
 * a name changed on one side only would fail silently.
 */
class PageHooksTest {
    private val module = File(System.getProperty("user.dir"))
    private val pageSide: String by lazy {
        val roots = listOf(File(module, "src/main/assets/lunacy"), File(module, "../LunaRuntimes"))
        roots.flatMap { r -> r.walkTopDown().filter { it.isFile && (it.extension == "js" || it.extension == "patch") }.toList() }
            .joinToString("\n") { it.readText() }
    }

    @Test fun everyHookIsDefinedInThePage() {
        for (name in PageHooks.ALL) {
            val short = name.substringAfterLast('.')
            // `window.x = function`, or a function defined first and assigned by name (`= apply;`).
            val defined = Regex("""\w+\.${Regex.escape(short)}\s*=\s*(?:function\b|[A-Za-z_$][\w$]*\s*;)""").containsMatchIn(pageSide)
            assertTrue("$name is called by the shell but defined nowhere in the page's scripts", defined)
        }
    }

    @Test fun aCallGuardsOnTheHook() {
        assertTrue(PageHooks.call(PageHooks.PASTE, "\"x\"") == "window.__lunacyPaste&&__lunacyPaste(\"x\")")
    }
}
