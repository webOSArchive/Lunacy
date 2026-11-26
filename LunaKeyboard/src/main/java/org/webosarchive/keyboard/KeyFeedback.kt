package org.webosarchive.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * What a key does as it goes down, besides typing: webOS's tap sound, and a light buzz.
 *
 * The sound is LunaSysMgr's: `TabletKeyboard::makeSound` on every new touch of a key, every
 * repeat of a held one and every step of the scroll ball, through
 * `SysmgrIMEModel::keyDownAudioFeedback`, which picks one of four samples - space, backspace,
 * return, and "key" for all the rest (the scroll ball steps with backspace's). It is on unless
 * turned off, as webOS's `TapSounds` preference was (`VirtualKeyboardPreferences`), and like
 * any system sound it is silent while the ringer is.
 *
 * The buzz is not webOS's (a TouchPad has no vibrator) but Android's habit: off unless turned
 * on, in three strengths. It is separate from Text Assist's Vibrate, which buzzes only when a
 * word is corrected ([TextAssist.Notifier]).
 */
class KeyFeedback(context: Context) {

    enum class Sound(val asset: String) { KEY("key"), SPACE("space"), BACKSPACE("backspace"), RETURN("return") }

    private val context = context.applicationContext

    private val pool: SoundPool = if (Build.VERSION.SDK_INT >= 21) SoundPool.Builder().setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        else @Suppress("DEPRECATION") SoundPool(MAX_STREAMS, AudioManager.STREAM_SYSTEM, 0)

    private val samples: Map<Sound, Int> = Sound.values().associateWith { s ->
        runCatching { this.context.assets.openFd("sounds/${s.asset}.wav").use { pool.load(it, 1) } }.getOrDefault(0)
    }

    private val audio = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** A key went down, or repeated, or the scroll ball stepped. */
    fun press(sound: Sound) {
        if (tapSounds(context) && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val id = samples[sound] ?: 0
            if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
        }
        if (vibrate(context)) buzz(context, vibrateLevel(context))
    }

    fun release() = pool.release()

    companion object {
        private const val PREFS = "keys"
        private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** webOS's TapSounds, on unless turned off. */
        fun tapSounds(c: Context) = prefs(c).getBoolean("tapSounds", true)
        fun setTapSounds(c: Context, on: Boolean) = prefs(c).edit().putBoolean("tapSounds", on).apply()

        fun vibrate(c: Context) = prefs(c).getBoolean("vibrate", false)
        fun setVibrate(c: Context, on: Boolean) = prefs(c).edit().putBoolean("vibrate", on).apply()

        /** 1 to [LEVELS]: Light, Firmer, Strong. */
        fun vibrateLevel(c: Context) = prefs(c).getInt("vibrateLevel", 1).coerceIn(1, LEVELS)
        fun setVibrateLevel(c: Context, level: Int) = prefs(c).edit().putInt("vibrateLevel", level.coerceIn(1, LEVELS)).apply()

        const val LEVELS = 3
        val LEVEL_NAMES = listOf("Light", "Firmer", "Strong")

        // Each level a step firmer where the motor can be told a strength (out of 255), and a
        // little longer for one that can't, or an Android before 8. From Pre Keyboard's Haptics.
        private val KEY_MS = longArrayOf(12L, 15L, 18L)
        private val KEY_STRENGTH = intArrayOf(70, 130, 200)

        /** Typing quickly, taps overlap: this many sound at once. */
        private const val MAX_STREAMS = 4

        private var vibrator: Vibrator? = null
        private var looked = false

        private fun vibrator(c: Context): Vibrator? {
            if (!looked) {
                looked = true
                vibrator = runCatching {
                    val v = if (Build.VERSION.SDK_INT >= 31)
                        (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                    else @Suppress("DEPRECATION") c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    v.takeIf { it.hasVibrator() }
                }.getOrNull()
            }
            return vibrator
        }

        /** One key's buzz at [level]; the setup screen uses it to let a strength be felt as it is chosen. */
        fun buzz(c: Context, level: Int) {
            val v = vibrator(c.applicationContext) ?: return
            val i = level.coerceIn(1, LEVELS) - 1
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    val amp = if (v.hasAmplitudeControl()) KEY_STRENGTH[i] else VibrationEffect.DEFAULT_AMPLITUDE
                    v.vibrate(VibrationEffect.createOneShot(KEY_MS[i], amp))
                } else @Suppress("DEPRECATION") v.vibrate(KEY_MS[i])
            }
        }
    }
}
