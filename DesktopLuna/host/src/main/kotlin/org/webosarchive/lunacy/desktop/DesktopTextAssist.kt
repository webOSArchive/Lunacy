package org.webosarchive.lunacy.desktop

import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.keyboard.Corrector
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Lunacy
import java.io.File

/**
 * webOS's Text Assist for Luna DE's on-screen keyboard: LunaKeyboard's Corrector over its own
 * word list (LunaKeyboard/src/main/assets/assist/dictionary.txt), read in the background as the
 * host starts. The keyboard (shell page) asks it what a finished word should become, and tells
 * it the words the person put back by undoing a correction, which are never corrected again.
 */
class DesktopTextAssist(private val dictionary: File) {
    @Volatile private var corrector: Corrector? = null

    fun start() {
        Thread({
            corrector = runCatching { dictionary.bufferedReader().useLines { Corrector(it) } }
                .onFailure { Log.w(Lunacy.TAG, "text assist: no word list at $dictionary: $it") }.getOrNull()
        }, "text-assist").apply { isDaemon = true }.start()
    }

    /** What [typed] becomes ({"fix": word or null, "alternates": [...]}): null while the word list is still being read. */
    fun correct(typed: String): JSONObject {
        val c = corrector ?: return JSONObject().put("ready", false)
        val fix = c.correct(typed)
        val alternates = if (fix == null) emptyList() else c.alternates(typed, ALTERNATES).filter { it != fix }
        return JSONObject().put("ready", true).put("fix", fix ?: JSONObject.NULL).put("alternates", JSONArray(alternates))
    }

    fun learn(word: String) { corrector?.learn(word) }

    private companion object {
        /** Other spellings offered beside the original (KeyboardService.ALTERNATES). */
        const val ALTERNATES = 4
    }
}
