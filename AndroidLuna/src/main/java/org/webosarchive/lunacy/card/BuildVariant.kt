package org.webosarchive.lunacy.card

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Which of a release's APKs this build is: its flavour (arm64 if its native code is, else
 * arm32) and its type (debug if debuggable, else release). The update check looks for this
 * APK on a release page, and Device Info shows it, so a person knows which one to download.
 */
object BuildVariant {
    fun flavour(context: Context): String =
        if (context.applicationInfo.nativeLibraryDir.orEmpty().contains("arm64")) "arm64" else "arm32"

    fun type(context: Context): String =
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) "debug" else "release"

    /** This build's file on a release page: AndroidLuna-arm32-release.apk. */
    fun apkName(context: Context): String = "AndroidLuna-${flavour(context)}-${type(context)}.apk"
}
