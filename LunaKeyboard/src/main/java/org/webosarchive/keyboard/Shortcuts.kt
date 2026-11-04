package org.webosarchive.keyboard

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.UserDictionary

/**
 * webOS's Shortcuts and learned words, kept in Android's own personal dictionary, whose entries
 * carry a shortcut beside the word ("ttyl" for "talk to you later") as Text Assist's did. So a
 * word added in Android's settings is one the keyboard knows and never corrects. Android lets
 * only the current keyboard and spell checker read it (from Android 6).
 */
class Shortcuts(private val context: Context, private val onWords: (Set<String>) -> Unit) {
    @Volatile private var map: Map<String, String> = emptyMap()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = load()
    }

    init {
        runCatching { context.contentResolver.registerContentObserver(UserDictionary.Words.CONTENT_URI, true, observer) }
        load()
    }

    private fun load() {
        Thread {
            val m = HashMap<String, String>()
            val words = HashSet<String>()
            runCatching {
                context.contentResolver.query(UserDictionary.Words.CONTENT_URI,
                    arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.SHORTCUT), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val word = c.getString(0) ?: continue
                        words += word
                        val shortcut = c.getString(1) ?: continue
                        if (shortcut.isNotBlank()) m[shortcut.lowercase()] = word
                    }
                }
            }
            map = m
            onWords(words)
        }.start()
    }

    /** What [typed] expands to, if it is a shortcut. */
    fun expand(typed: String): String? = map[typed.lowercase()]

    fun close() = runCatching { context.contentResolver.unregisterContentObserver(observer) }
}
