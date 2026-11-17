package org.webosarchive.lunacy.card

/** The kinds of window a page opens (window.open's "window" attribute) that the shell places, by their names on the wire. */
enum class WindowType(val wire: String) {
    CARD("card"),
    DASHBOARD("dashboard"),
    POPUP_ALERT("popupalert"),
    DOCK_MODE("dockMode");

    companion object {
        /**
         * The type by its name whatever its case: Palm's Photos asks for its Exhibition
         * window as "dockmode", and the same unchanged Photos opens it in the dock on the
         * reference TouchPad, where Enyo passes the name through as written. Null for a name
         * the shell doesn't know, which it shows as a card.
         */
        fun named(name: String): WindowType? = values().firstOrNull { it.wire.equals(name, ignoreCase = true) }
    }
}
