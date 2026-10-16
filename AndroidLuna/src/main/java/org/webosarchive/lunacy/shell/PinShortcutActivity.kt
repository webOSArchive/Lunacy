package org.webosarchive.lunacy.shell

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle

/**
 * The home app's answer to ShortcutManager.requestPinShortcut (Android 8 and later): an app,
 * a browser's "Add to Home screen" above all, asks to pin a shortcut and Android starts this
 * with the request. It is accepted at once - the app asking has already shown its own dialog -
 * and shows nothing; the launcher picks the new pin up from LauncherApps ([PinnedShortcuts]).
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 26) accept()
        finish()
    }

    @android.annotation.TargetApi(26)
    private fun accept() {
        val request = runCatching { (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).getPinItemRequest(intent) }.getOrNull() ?: return
        if (request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && request.isValid && request.accept()) {
            PinnedShortcuts.notifyChanged()
        }
    }
}

/**
 * The INSTALL_SHORTCUT broadcast apps sent a home app before Android 8 to pin a shortcut. Only
 * apps holding com.android.launcher.permission.INSTALL_SHORTCUT can reach it (the manifest).
 */
class InstallShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PinnedShortcuts(context, null).addLegacy(intent)
    }
}
