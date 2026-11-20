package org.webosarchive.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Autocorrect against the word lists the keyboard ships (Corrector): what it fixes, and what it leaves. */
class CorrectorTest {
    private val c = File("src/main/assets/assist/dictionary.txt").bufferedReader().use { list ->
        File("src/main/assets/assist/words.txt").bufferedReader().use { more -> Corrector(list.lineSequence() + more.lineSequence()) }
    }

    private fun fixes(vararg pairs: Pair<String, String?>) { for ((typed, want) in pairs) assertEquals(typed, want, c.correct(typed)) }

    @Test fun slipsAreFixed() = fixes("teh" to "the", "Teh" to "The", "adn" to "and", "recieve" to "receive",
        "keybaord" to "keyboard", "dont" to "don't", "im" to "I'm", "i" to "I", "london" to "London")

    @Test fun wordsAreLeft() = fixes("the" to null, "well" to null, "its" to null, "NASA" to null, "hello" to null)

    @Test fun webOSsOwnNamesAreNotTheirNeighbours() = fixes("webos" to "webOS", "touchpad" to "TouchPad",
        "pre" to null, "Pre" to null, "mojo" to null, "homebrew" to null, "hp" to null)

    @Test fun abbreviationsAndChatWordsAreLeft() = fixes("usb" to "USB", "gif" to null, "haha" to null,
        "hahaha" to null, "lol" to null, "hm" to null, "jk" to null, "app" to null, "max" to null, "gmail" to "Gmail")

    @Test fun shortWordsDontGetAChemicalSymbolsCapitals() = fixes("os" to null, "ur" to null, "cd" to null, "mg" to null,
        "th" to null, "ed" to null, "le" to null, "ing" to null, "zen" to null, "ok" to "OK", "tim" to "Tim")
}
