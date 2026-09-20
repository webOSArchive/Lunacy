package org.webosarchive.lunacy.shell

import android.content.Intent
import android.service.dreams.DreamService

/**
 * webOS's Exhibition mode, offered to Android as a screen saver - "Daydream" on Android 5.
 *
 * A TouchPad went into Exhibition when it was set down on its Touchstone: the screen became a
 * clock, or whatever app its owner had chosen, and stayed that way until it was picked up
 * again. Android starts a dream when a device is docked or charging and left alone. That is
 * the same idea, so Lunacy offers itself as one: Settings > Display > Daydream > Lunacy
 * Exhibition, and the tablet on its charger shows what a TouchPad on its Touchstone showed.
 *
 * This service draws nothing of its own. Exhibition belongs to the shell - the Time face is
 * LunaSysMgr's QML in [ExhibitionLayer], and an exhibiting app is a real card with the whole
 * Luna bus behind it - so the dream hands over to [ShellActivity] and stands down. What it
 * sends is what Palm's Exhibition app sends when its Start Exhibition button is pressed, so
 * there is one way into the mode, not two.
 *
 * Standing down is what makes the mode outlast the dream, which is what a dock mode is for:
 * Android would end a dream at the first touch, and webOS's Exhibition survived being touched.
 * The shell takes on the rest of the dream's job with it - keeping the screen on, showing over
 * the lock screen, and leaving when the charger is pulled. See ShellActivity.startDreamExhibition.
 */
class ExhibitionDream : DreamService() {
    override fun onDreamingStarted() {
        super.onDreamingStarted()
        startActivity(Intent(this, ShellActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ShellActivity.EXTRA_EXHIBITION, true))
        finish()
    }
}
