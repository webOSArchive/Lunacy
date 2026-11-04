package org.webosarchive.lunacy.card

/**
 * The functions the shell calls in a page, named once. Each is defined on the page's side - in
 * Lunacy's injected scripts (assets/lunacy) or in a framework patch (LunaRuntimes) - and every
 * call guards on it being there, so a name changed on one side alone would fail silently:
 * PageHooksTest checks that each name here is defined on the other side.
 */
object PageHooks {
    /** A native network request finished (net.js). */
    const val NET_DONE = "__lunacyNetDone"
    /** A WebSQL statement finished (websql.js). */
    const val SQL_DONE = "__lunacySqlDone"
    /** The page is closing: its unload handlers run (compat.js). */
    const val UNLOAD = "__lunacyUnload"
    /** PalmSystem's removeInputFocus done in the page (compat.js). */
    const val REMOVE_INPUT_FOCUS = "__lunacyRemoveInputFocus"
    /** A relaunch, which answers whether the app handled it (bridge.js). */
    const val RELAUNCH = "__lunacyRelaunch"
    /** The viewport meta written again for the card's size (compat.js). */
    const val VIEWPORT = "__lunacyViewport"
    /** The clipboard pasted into the focused field (bridge.js). */
    const val PASTE = "__lunacyPaste"
    /** A hybrid app's plugin speaking to its page (bridge.js). */
    const val PLUGIN = "__lunacyPlugin"
    /** A native browser view's event, to Enyo's WebView (enyo-1.0 patch 0005). */
    const val WEB_VIEW = "__lunacyWebView"
    /** A bus reply, to PalmServiceBridge (bridge.js). */
    const val REPLY = "PalmServiceBridge.__reply"

    val ALL = listOf(NET_DONE, SQL_DONE, UNLOAD, REMOVE_INPUT_FOCUS, RELAUNCH, VIEWPORT, PASTE, PLUGIN, WEB_VIEW, REPLY)

    /** [name] called with [args] (already JavaScript) if the page defines it. */
    fun call(name: String, vararg args: String) = "window.$name&&$name(${args.joinToString(",")})"
}
