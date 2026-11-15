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
 * The shell takes on the rest of the dream's job with it - keeping the screen on (a wake lock
 * while it exhibits), showing over the lock screen, and leaving when the charger is pulled. See
 * ExhibitionController.startDreamExhibition.
 */
class ExhibitionDream : DreamService() {
    @Suppress("DEPRECATION")
    override fun onDreamingStarted() {
        super.onDreamingStarted()
        // Awake across the handover: a dream that ends leaves Android asleep within
        // milliseconds, before the shell's own wake lock (ExhibitionController.holdAwake) is
        // taken. Measured on the Pixel Tablet (2026-11-15): asleep at +104 ms, woken by the
        // shell at +117 ms, wakeUp() or not. This one lapses by itself once the shell holds on.
        (getSystemService(POWER_SERVICE) as android.os.PowerManager)
            .newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "lunacy:exhibition-handover").acquire(HANDOVER_MS)
        startActivity(Intent(this, ShellActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ShellActivity.EXTRA_EXHIBITION, true))
        // Ending it as a touch would (wakeUp) rather than as one that ran out (finish).
        if (android.os.Build.VERSION.SDK_INT >= 29) wakeUp() else finish()
    }

    companion object {
        /** Long enough for a slow shell to come forward and take its own wake lock. */
        const val HANDOVER_MS = 10_000L
    }
}
