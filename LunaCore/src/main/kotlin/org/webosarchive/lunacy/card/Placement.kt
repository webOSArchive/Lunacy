package org.webosarchive.lunacy.card

/** Where a launched app's first window goes. */
enum class Placement {
    /**
     * Its loading card at once, the page behind it, as a launch from the launcher was on
     * webOS. A headless app's card is a placeholder its first card window fills.
     */
    CARD,
    /**
     * No loading card: a card once the app opens one, or headless. What a service's launch
     * gets (an activity's callback at boot): on webOS nothing showed until the app opened a
     * card window.
     */
    NO_CARD,
    /** Started for Exhibition: its first window is a dock window, never a card (codepoet). */
    DOCK,
}
