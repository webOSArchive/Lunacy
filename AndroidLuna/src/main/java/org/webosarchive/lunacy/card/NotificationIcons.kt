package org.webosarchive.lunacy.card

import android.content.Context

/**
 * Whether an app's icon in the notifications - a dashboard's or banner's with no icon of its
 * own - shows in colour or grey. webOS did both: the TouchPad turned it grey, the Pre3 showed
 * it as it is. Lunacy leaves it to the owner, in Screen & Lock, the same on every layout
 * (codepoet, 2026-10-16; Docs/luna-deltas.md B13). Colour unless they choose grey.
 */
object NotificationIcons {
    private const val PREFS = "notificationicons"
    private const val KEY = "colour"

    /** The shell's, while it runs: told when the setting changes, on the main thread. */
    @Volatile var onChanged: (() -> Unit)? = null

    fun colour(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setColour(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        onChanged?.let { f -> android.os.Handler(android.os.Looper.getMainLooper()).post(f) }
    }
}
