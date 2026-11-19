package org.webosarchive.lunacy.card

/** A window of a running app, as the application manager lists and closes them: AppWindow on Android. */
interface RunningWindow {
    /** The process id the app's page was told (PalmSystem's), which names the window on the bus. */
    val pid: Int
}
