package org.webosarchive.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Vibrator

/**
 * webOS's Text Assist: the switches its settings app kept (com.palm.app.textassist) and what a
 * correction did to tell you it had happened.
 *
 * Measured on the reference TouchPad (2026-11-04): Auto-Capitalization, Auto-Correction and
 * Double Space For Period, all on (`x_palm_textinput` has spelling and shortcuts set to
 * `autoCorrect`, `x_palm_virtualkeyboard_prefs` has `spaces2period: true`), and a correction
 * makes a sound (`systemSound: soundOnly`; the others were `hapticOnly` and `disabled`).
 */
object TextAssist {
    private const val PREFS = "textassist"

    enum class Alert { SOUND, VIBRATE, MUTE }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCap(c: Context) = prefs(c).getBoolean("autoCapitalization", true)
    fun autoCorrect(c: Context) = prefs(c).getBoolean("autoCorrection", true)
    fun doubleSpace(c: Context) = prefs(c).getBoolean("doubleSpaceForPeriod", true)
    fun alert(c: Context): Alert =
        runCatching { Alert.valueOf(prefs(c).getString("alert", Alert.SOUND.name)!!) }.getOrDefault(Alert.SOUND)

    fun set(c: Context, key: String, on: Boolean) = prefs(c).edit().putBoolean(key, on).apply()
    fun setAlert(c: Context, a: Alert) = prefs(c).edit().putString("alert", a.name).apply()

    /** The plop, or a buzz, or nothing: whatever the owner chose, as webOS's Text Assist offered. */
    class Notifier(private val context: Context) {
        private val pool: SoundPool = if (Build.VERSION.SDK_INT >= 21) SoundPool.Builder().setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            else @Suppress("DEPRECATION") SoundPool(2, AudioManager.STREAM_SYSTEM, 0)
        private val plop: Int = runCatching {
            context.assets.openFd("assist/text_entry_correction.wav").use { pool.load(it, 1) }
        }.getOrDefault(0)

        fun corrected() {
            when (alert(context)) {
                Alert.SOUND -> {
                    // A system sound: not while the ringer is silenced or on vibrate.
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    if (plop != 0 && am.ringerMode == AudioManager.RINGER_MODE_NORMAL) pool.play(plop, 1f, 1f, 1, 0, 1f)
                }
                Alert.VIBRATE -> runCatching {
                    @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VIBRATE_MS)
                }
                Alert.MUTE -> {}
            }
        }

        fun release() = pool.release()
    }

    private const val VIBRATE_MS = 30L
}
