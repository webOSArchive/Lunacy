package org.webosarchive.lunacy.core

/** What every part of Lunacy shares. */
object Lunacy {
    /** The log tag of everything Lunacy writes (AppServer.TAG on Android, from before LunaCore). */
    const val TAG = "Lunacy"

    /** Lunacy's own service on the bus (LunacyService.SERVICE on Android). */
    const val SERVICE = "org.webosarchive.lunacy"
}
