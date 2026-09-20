package org.webosarchive.lunacy.shell

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The gear beside "Lunacy Exhibition" in Android's Daydream settings.
 *
 * Which app exhibits is not an Android setting: it is webOS's, kept as applicationManager's
 * dock-mode launch points, and Palm's Exhibition app is what edits it. So the gear opens that
 * app in the shell rather than putting a second, Android-shaped settings screen in front of
 * the same choice.
 */
class ExhibitionSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, ShellActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("launch", EXHIBITION_APP))
        finish()
    }

    companion object { const val EXHIBITION_APP = "com.palm.app.exhibitionpreferences" }
}
