package org.webosarchive.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard's account of the cursor and the word before it (WordTracker), driven as
 * KeyboardService drives it against a field whose selection reports come back late, as a real
 * field's do: each case is something that went wrong on a device.
 */
class WordTrackerTest {
    /** A field, and the reports it owes the keyboard. */
    private class Field(initial: String = "", at: Int = initial.length) {
        var clock = 0L
        val tracker = WordTracker { clock }
        val text = StringBuilder(initial)
        var cursor = at
        private val owed = ArrayList<Int>()
        var external = 0
        private var selected = 0

        init { tracker.start(at, at) }

        private fun read() {
            if (!tracker.needsContext) return
            tracker.context(text.substring(Math.max(0, cursor - WordTracker.ASK), cursor), WordTracker.ASK,
                // As Android answers: what follows the selection, not what is in it.
                text.substring(cursor + selected, Math.min(text.length, cursor + selected + 1)))
        }

        /** Keys typed, with no report delivered between them. */
        fun type(s: String): Field {
            for (c in s) { read(); text.insert(cursor, c); cursor++; owed += cursor; tracker.inserted(c.toString()) }
            return this
        }

        fun backspace(times: Int = 1): Field {
            repeat(times) {
                tracker.deleted()
                if (cursor > 0) { text.deleteCharAt(cursor - 1); cursor--; owed += cursor }
            }
            return this
        }

        /** The field catches up: every report owed, in order. */
        fun settle(): Field {
            for (p in owed) if (tracker.update(p, p)) external++
            owed.clear()
            return this
        }

        /** Only the last report arrives: the field folded the others into it. */
        fun settleFolded(): Field {
            owed.lastOrNull()?.let { if (tracker.update(it, it)) external++ }
            owed.clear()
            return this
        }

        fun tap(pos: Int): Field { cursor = pos; assertTrue(tracker.update(pos, pos)); return this }

        fun select(from: Int, to: Int): Field { cursor = from; assertTrue(tracker.update(from, to)); selected = to - from; return this }

        fun typeOver(s: String): Field {
            read()
            text.delete(cursor, cursor + selected); selected = 0
            return type(s)
        }

        /** The word as the keyboard would correct it at the next space, or null if it would leave it. */
        fun wordToFix(): String? { read(); return tracker.word.takeIf { it.isNotEmpty() && tracker.touched && !tracker.plain } }
    }

    @Test fun aFirstLetterErasedAndTypedOverIsNotPartOfTheWord() {
        // codepoet, 2026-11-20: "T", backspace, then the word; nothing reported until the end.
        val f = Field().type("T").backspace().type("Hello")
        assertEquals("Hello", f.tracker.word)
        f.settle()
        assertEquals(0, f.external)
        assertEquals("Hello", f.wordToFix())
        assertEquals(5, f.tracker.cursor)
    }

    @Test fun reportsArrivingBetweenKeysChangeNothing() {
        val f = Field()
        f.type("T").settle().backspace().type("h").settle().type("ello").settle()
        assertEquals(0, f.external)
        assertEquals("hello", f.wordToFix())
    }

    @Test fun aWordTypedStraightAfterASpaceKeepsItsFirstLetter() {
        // The space's report was still on its way when the next word began.
        val f = Field().type("one").type(" ").type("testing")
        f.settle()
        assertEquals(0, f.external)
        assertEquals("testing", f.wordToFix())
        assertEquals(11, f.tracker.cursor)
    }

    @Test fun aFinishedWordGoneBackIntoIsTheWholeWord() {
        // "testing ", back over the space and two letters, and on: not "ng" on its own.
        val f = Field("testing ").settle()
        f.type("x").backspace().settle()          // the keyboard has read the text by now
        f.backspace(3).type("ng")
        assertEquals("testing", f.wordToFix())
        f.settle()
        assertEquals(0, f.external)
    }

    @Test fun aWordTakenUpAgainAfterATapIsTheWholeWord() {
        val f = Field("some testi more", at = 15).tap(10).type("ng")
        assertEquals("testing", f.wordToFix())
    }

    @Test fun aWordTheCursorWasOnlyPutAfterIsLeftBe() {
        val f = Field("teh", at = 0).tap(3)
        assertEquals(null, f.wordToFix())
    }

    @Test fun lettersTypedInsideAWordAreNotAWord() {
        val f = Field("heo there", at = 9).tap(2).type("ll")
        assertEquals(null, f.wordToFix())
    }

    @Test fun aSelectionTypedOverStartsTheWordAtItsStart() {
        val f = Field("one wrong three").select(4, 9).typeOver("right")
        assertEquals("right", f.wordToFix())
        assertEquals(9, f.tracker.cursor)
        assertEquals("one right three", f.text.toString())
    }

    @Test fun aTapIsNotTakenForTheKeyboardsOwnEdit() {
        val f = Field("hello world").settle()
        f.clock += 1000
        f.tap(5).type("s")
        assertEquals("hellos", f.wordToFix())
        assertEquals(6, f.tracker.cursor)
    }

    @Test fun foldedReportsAreTheKeyboardsOwn() {
        val f = Field().type("ab").backspace().type("c").settleFolded()
        assertEquals(0, f.external)
        assertEquals("ac", f.wordToFix())
    }

    @Test fun aStaleFoldedReportStopsBeingWaitedFor() {
        val f = Field("hello world").settle()
        f.type("a").backspace().type("b").settleFolded()     // 12 and 11 never reported
        f.clock += 1000
        f.cursor = 11
        assertTrue(f.tracker.update(11, 11))
    }

    @Test fun aPlaceReportedTwiceIsStillTheKeyboardsOwn() {
        // Chromium reports the cursor again, unmoved, when a correction's composing text is
        // committed; by then the keyboard has typed on.
        val f = Field().type("teh")
        f.settle()
        f.tracker.replacedBefore(3, "the")
        f.tracker.inserted(" ")
        f.tracker.inserted("a")
        assertFalse(f.tracker.update(3, 3))
        assertFalse(f.tracker.update(3, 3))
        assertFalse(f.tracker.update(4, 4))
        assertFalse(f.tracker.update(5, 5))
        assertEquals(5, f.tracker.cursor)
        assertTrue(f.tracker.touched)
    }

    @Test fun piecesOfAnAddressAreLeftAsTyped() {
        assertEquals(null, Field().type("me@exampel").wordToFix())
        assertEquals(null, Field().type("exampel.com").wordToFix())
        assertEquals(null, Field().type("see /usr/lcoal").wordToFix())
        assertEquals(null, Field().type("#lunacy").wordToFix())
        assertEquals("Then", Field().type("End. Then").wordToFix())
        assertEquals("teh", Field().type("say 'teh").wordToFix())
    }

    @Test fun aDigitDoesNotEndAWord() {
        assertEquals("mp3", Field().type("mp3").tracker.word)
        assertEquals("3rd", Field().type("the 3rd").tracker.word)
    }

    @Test fun backspaceTakesBothHalvesOfAnEmoji() {
        val f = Field("ok 😀").settle()
        f.type("x").backspace()
        assertEquals(2, f.tracker.deleted())
        assertEquals(3, f.tracker.cursor)
    }

    @Test fun backspaceAtTheStartOfAFieldMovesNothing() {
        val f = Field().type("a").backspace().settle()
        assertEquals(0, f.tracker.deleted())
        assertEquals(0, f.tracker.cursor)
        assertFalse(f.tracker.update(0, 0))
    }

    @Test fun aLongWordIsKeptWhole() {
        val f = Field()
        f.type("x".repeat(200) + " internationalisation")
        assertEquals("internationalisation", f.wordToFix())
    }
}
