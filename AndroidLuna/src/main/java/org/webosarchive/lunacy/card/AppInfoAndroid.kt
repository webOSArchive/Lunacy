package org.webosarchive.lunacy.card

import android.content.ComponentName

/** An Android app's component, for the launcher's Android apps (AppInfo.hostApp, in LunaCore). */
val AppInfo.androidComponent: ComponentName? get() = hostApp as? ComponentName
