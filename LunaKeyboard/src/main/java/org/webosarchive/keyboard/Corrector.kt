// Adapted from Pre Keyboard's Corrector.kt (https://github.com/alan-morford/prekeyboard, at
// 510924a), Copyright 2026 Alan Morford, Apache License 2.0. Changed for LunaKeyboard: the
// keys next to each other are the TouchPad's tablet layout's (Keymap), not the Pre's; and the
// spelling alternates are public, for Text Assist's bubble. See LunaKeyboard/NOTICE.
package org.webosarchive.keyboard

/**
 * Autocorrect: turns a finished word into the word that was probably meant, or leaves it be.
 *
 * - Contractions typed without their apostrophe get it back: "dont" -> "don't", "im" -> "I'm".
 *   Only the ones whose bare form is not itself an everyday word: "well", "were", "its",
 *   "ill", "hell", "shell", "lets" and "wed" are left alone.
 * - A lone "i", and a proper noun typed in lower case, get their capital; a word of two or
 *   three letters only where that is surely what it is.
 * - An unknown word becomes the dictionary word closest to it, where "close" is an edit
 *   distance that knows the keyboard: hitting the key next door, swapping two letters
 *   or doubling one costs less than any other slip. Distance is traded against how common the
 *   word is, so "teh" becomes "the" rather than "ten".
 *
 * Plain Kotlin with no Android in it, so the tests run it directly.
 */
class Corrector(lines: Sequence<String>) {

    private class Entry(val word: String, val score: Int)

    /** Keyed by lower case; the value keeps the word's own case ("I", "London", "don't"). */
    private val words = HashMap<String, Entry>(131072)

    /** Words the owner has kept by undoing a correction: never corrected again. */
    // Read by the suggestion thread while the keyboard may add to it: synchronised.
    private val learned: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

    init {
        for (line in lines) {
            if (line.isEmpty() || line[0] == '#') continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val w = line.substring(0, tab)
            val score = line.substring(tab + 1).trim().toIntOrNull() ?: continue
            val key = w.lowercase()
            val had = words[key]
            // Prefer the lower-case spelling when a word comes both ways ("bill" and "Bill").
            if (had == null || (w == key && had.word != key) || (had.word != key && score > had.score))
                words[key] = Entry(w, score)
        }
    }

    /** Every key, sorted, for finding the words a typed prefix begins. */
    private val sorted: Array<String> = words.keys.toTypedArray().also { it.sort() }

    val size get() = words.size

    /** Every word with its score, for the swipe decoder. */
    fun entries(): Sequence<Pair<String, Int>> = words.values.asSequence().map { it.word to it.score }

    fun learn(word: String) { learned += word.lowercase() }
    fun learnAll(ws: Collection<String>) { for (w in ws) learn(w) }

    fun isKnown(word: String): Boolean = word.lowercase().let { it in words || it in learned }

    /** [word] spelt as the dictionary spells it ("i'm" -> "I'm"), or null for a word it doesn't know. */
    fun spelling(word: String): String? {
        val lower = word.lowercase()
        return words[lower]?.word ?: lower.takeIf { it in learned }
    }

    /** The correction for [typed], or null to leave it as typed. */
    fun correct(typed: String): String? {
        if (typed.isEmpty() || typed.length > MAX_LENGTH) return null
        if (!typed.all { it.isLetter() || it == '\'' } || !typed[0].isLetter()) return null
        val lower = typed.lowercase()
        if (lower in learned) return null
        // A word typed all in capitals, beyond one letter, is meant that way (NASA, OK).
        if (typed.length > 1 && typed.all { it.isUpperCase() || it == '\'' }) return null

        CONTRACTIONS[lower]?.let { return casedLike(typed, it).takeIf { r -> r != typed } }

        words[lower]?.let { e ->
            // Known. Only its capital can be wrong: "i", or "london" for "London". Not a short
            // word's, though: the list's two-letter capitals are chemical symbols and the ends
            // of names ("os" is not osmium's "Os", nor "ur" the city of Ur, "cd" cadmium), and
            // its rarer three-letter ones no likelier ("ing" is not the bank).
            if (typed != lower || e.word == lower) return null
            val sure = lower.length > 3 || (lower.length == 3 && e.score >= SHORT_CAPITAL_SCORE) || lower in SHORT_CAPITALS
            return if (sure) e.word else null
        }
        if (lower.length < 2 || '\'' in lower) return null

        val best = nearest(lower, 1).firstOrNull() ?: return null
        return casedLike(typed, best).takeIf { it != typed }
    }

    /**
     * Words for the suggestion bar while [typed] is being typed, likeliest first: what
     * autocorrect would make of it (or the word itself, when it is a word), then the commonest
     * words it begins, then the nearest spellings.
     */
    fun suggestions(typed: String, max: Int = 3): List<String> {
        if (typed.isEmpty() || typed.length > MAX_LENGTH || !typed[0].isLetter()) return emptyList()
        val lower = typed.lowercase()
        val out = LinkedHashSet<String>()
        val fix = correct(typed)
        when {
            fix != null -> out += fix
            lower in learned -> out += typed
            else -> words[lower]?.let { out += casedLike(typed, it.word) }
        }
        for (w in completions(lower, 2)) out += casedLike(typed, w)
        if (lower.length >= 2 && '\'' !in lower && typed.all { it.isLetter() })
            for (w in nearest(lower, max)) out += casedLike(typed, w)
        for (w in completions(lower, max + 2)) out += casedLike(typed, w)
        return out.take(max)
    }

    /** The commonest words that start with [prefix] and are longer than it. */
    private fun completions(prefix: String, max: Int): List<String> {
        var lo = 0; var hi = sorted.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (sorted[mid] < prefix) lo = mid + 1 else hi = mid }
        val found = ArrayList<Entry>()
        var i = lo
        var looked = 0
        while (i < sorted.size && sorted[i].startsWith(prefix) && looked < COMPLETION_SCAN) {
            if (sorted[i].length > prefix.length) words[sorted[i]]?.let { found += it }
            i++; looked++
        }
        return found.sortedByDescending { it.score }.take(max).map { it.word }
    }

    // --- Finding the nearest word ------------------------------------------------------------

    private class Ranked(val entry: Entry, val score: Float)

    /** The likeliest other spellings of [typed], in its case, for the bubble. */
    fun alternates(typed: String, max: Int): List<String> {
        val lower = typed.lowercase()
        if (lower.length < 2 || !typed.all { it.isLetter() }) return emptyList()
        // A word that is one ("constantinople", for its capital) has no misspelling to look two
        // slips away for: its other spellings are the words one slip from it, or none.
        return ranked(lower, max, twoSlips = lower !in words).map { casedLike(typed, it.entry.word) }
    }

    private fun nearest(w: String, max: Int): List<String> = ranked(w, max).map { it.entry.word }

    private fun ranked(w: String, max: Int, twoSlips: Boolean = true): List<Ranked> {
        val one = HashMap<String, Float>()
        edits(w, 0f, one)
        pick(w, one, maxCost = if (w.length <= 3) 0.6f else 1.3f, minScore = if (w.length <= 3) 12 else 5, max)
            .let { if (it.isNotEmpty()) return it }
        if (w.length < 5 || !twoSlips) return emptyList()
        // Two slips: every one-slip spelling once more, with a narrower set of edits, keeping
        // only what is a word.
        val two = HashMap<String, Float>()
        val begun = Begun(w)
        for ((s, c) in one) wordsOneSlipFrom(s, c, two, begun)
        return pick(w, two, maxCost = 1.9f, minScore = 9, max)
    }

    /**
     * The best known words among [cands]: common, and cheap to reach. Best first. A word that
     * does not begin as [typed] does ranks lower: the first letter is the one people get right.
     * Two swapped first letters ("hte") are a swap, not a wrong first letter. The charge is on
     * the rank only, so what is near enough to be offered at all is as it was.
     */
    private fun pick(typed: String, cands: Map<String, Float>, maxCost: Float, minScore: Int, max: Int): List<Ranked> {
        val found = ArrayList<Ranked>()
        for ((s, cost) in cands) {
            if (cost > maxCost) continue
            val e = words[s] ?: continue
            if (e.score < minScore) continue
            val swapped = s.length > 1 && typed.length > 1 && s[0] == typed[1] && s[1] == typed[0]
            val changesFirst = s.isNotEmpty() && s[0] != typed[0] && !swapped
            found += Ranked(e, e.score - COST_WEIGHT * (cost + if (changesFirst) FIRST_LETTER else 0f))
        }
        return found.sortedByDescending { it.score }.take(max)
    }

    /** Every spelling one slip away from [w], with what the slip costs, into [out]. */
    private fun edits(w: String, base: Float, out: HashMap<String, Float>) {
        fun add(s: String, c: Float) {
            val total = base + c
            val had = out[s]
            if (had == null || total < had) out[s] = total
        }
        val n = w.length
        val sb = StringBuilder(n + 1)
        for (i in 0 until n) {
            // An extra letter: cheaper when it doubles its neighbour.
            sb.setLength(0); sb.append(w, 0, i).append(w, i + 1, n)
            add(sb.toString(), if (i > 0 && w[i] == w[i - 1]) 0.5f else if (adjacentToNeighbour(w, i)) EXTRA_NEAR else EXTRA_FAR)
            // Two letters swapped.
            if (i < n - 1 && w[i] != w[i + 1]) {
                sb.setLength(0); sb.append(w, 0, i).append(w[i + 1]).append(w[i]).append(w, i + 2, n)
                add(sb.toString(), 0.6f)
            }
            // The wrong key.
            for (c in ALPHABET) {
                if (c == w[i]) continue
                val cost = when {
                    adjacent(w[i], c) -> 0.6f
                    w[i] in VOWELS && c in VOWELS -> WRONG_VOWEL
                    else -> 1.3f
                }
                sb.setLength(0); sb.append(w, 0, i).append(c).append(w, i + 1, n)
                add(sb.toString(), cost)
            }
        }
        // A missed letter.
        for (i in 0..n) {
            for (c in ALPHABET) {
                val doubling = (i > 0 && w[i - 1] == c) || (i < n && w[i] == c)
                sb.setLength(0); sb.append(w, 0, i).append(c).append(w, i, n)
                add(sb.toString(), if (doubling) 0.5f else MISSED)
            }
        }
    }

    /**
     * The words one slip away from [w], with what the slip costs on top of [base], into [out]:
     * the second slip of two, with a narrower set of edits than the first (no key that is
     * neither next door nor a vowel for a vowel; a missed letter only where it doubles its
     * neighbour, is a vowel or sits beside one of them).
     *
     * A slip at place i leaves the letters before it as they are, so it can only make a word
     * if some word begins with them, and a letter put there only if some word goes on with
     * it. The sorted list says so without a spelling being built: for "constantinople", whose
     * 770 one-slip spellings used to be grown into a third of a million strings and looked up
     * one by one, the space bar hung for five seconds on an HP 10 G2 (2026-11-20). What is
     * found, and what it costs, is as before.
     */
    private fun wordsOneSlipFrom(w: String, base: Float, out: HashMap<String, Float>, begun: Begun) {
        fun add(at: Int, c: Float) {
            if (at < 0) return
            val s = sorted[at]
            val total = base + c
            val had = out[s]
            if (had == null || total < had) out[s] = total
        }
        val n = w.length
        // As far as w is still the typed word, what begins with its letters is already known.
        val same = begun.sharedWith(w)
        // sorted[lo, hi) are the words that begin with w's first i letters.
        var lo = 0; var hi = sorted.size
        for (i in 0..n) {
            val known = i <= same
            if (known) { lo = begun.lo[i]; hi = begun.hi[i] }
            if (lo >= hi) break
            if (i < n) {
                // An extra letter.
                add(wordAt(lo, hi, i, NONE, NONE, w, i + 1),
                    if (i > 0 && w[i] == w[i - 1]) 0.5f else if (adjacentToNeighbour(w, i)) EXTRA_NEAR else EXTRA_FAR)
                // Two letters swapped.
                if (i < n - 1 && w[i] != w[i + 1]) add(wordAt(lo, hi, i, w[i + 1], w[i], w, i + 2), 0.6f)
                // The wrong key.
                for (c in ALPHABET) {
                    if (c == w[i]) continue
                    val cost = when {
                        adjacent(w[i], c) -> 0.6f
                        vowel(w[i]) && vowel(c) -> WRONG_VOWEL
                        else -> continue
                    }
                    if (known && !begun.goesOn[i * 26 + (c - 'a')]) continue
                    add(wordAt(lo, hi, i, c, NONE, w, i + 1), cost)
                }
            }
            // A missed letter.
            for (c in ALPHABET) {
                val doubling = (i > 0 && w[i - 1] == c) || (i < n && w[i] == c)
                if (!doubling && !vowel(c) &&
                    !(i > 0 && adjacent(w[i - 1], c)) && !(i < n && adjacent(w[i], c))) continue
                if (known && !begun.goesOn[i * 26 + (c - 'a')]) continue
                add(wordAt(lo, hi, i, c, NONE, w, i), if (doubling) 0.5f else MISSED)
            }
            if (i < n && i >= same) {
                val from = firstWith(lo, hi, i, w[i])
                hi = firstWith(from, hi, i, w[i] + 1)
                lo = from
            }
        }
    }

    /**
     * Where in [sorted] the word is that is spelt: the [depth] letters that sorted[lo, hi) all
     * begin with, then [a] and [b] (each [NONE] for no letter), then [w] from [tail] on. Or -1
     * when that is no word. Letter by letter down the sorted list, so a spelling is given up at
     * the first letter no word goes on with, and none is ever built.
     */
    private fun wordAt(lo: Int, hi: Int, depth: Int, a: Char, b: Char, w: String, tail: Int): Int {
        var from = lo; var to = hi; var d = depth
        if (a != NONE) {
            val f = firstWith(from, to, d, a); to = firstWith(f, to, d, a + 1); from = f; d++
            if (from >= to) return -1
        }
        if (b != NONE) {
            val f = firstWith(from, to, d, b); to = firstWith(f, to, d, b + 1); from = f; d++
            if (from >= to) return -1
        }
        for (k in tail until w.length) {
            val c = w[k]
            val f = firstWith(from, to, d, c); to = firstWith(f, to, d, c + 1); from = f; d++
            if (from >= to) return -1
        }
        return if (sorted[from].length == d) from else -1
    }

    /**
     * What the list holds that begins as the typed word does: for each count of its first
     * letters, where those words are in [sorted], and which letters they go on with. Every
     * one-slip spelling is the typed word up to its slip, so this is worked out once for all
     * of them.
     */
    private inner class Begun(private val typed: String) {
        val lo = IntArray(typed.length + 1)
        val hi = IntArray(typed.length + 1)
        val goesOn = BooleanArray((typed.length + 1) * 26)

        init {
            var a = 0; var b = sorted.size
            for (i in 0..typed.length) {
                lo[i] = a; hi[i] = b
                if (a < b) for (c in 'a'..'z') goesOn[i * 26 + (c - 'a')] = anyGoesOn(a, b, i, c)
                if (i < typed.length && a < b) {
                    val from = firstWith(a, b, i, typed[i])
                    b = firstWith(from, b, i, typed[i] + 1)
                    a = from
                }
            }
        }

        /** How many of [w]'s first letters are the typed word's. */
        fun sharedWith(w: String): Int {
            var k = 0
            val m = minOf(w.length, typed.length)
            while (k < m && w[k] == typed[k]) k++
            return k
        }
    }

    /**
     * In sorted[lo, hi), words that all begin with the same [depth] letters: the first whose
     * next letter is [c] or later. (The word that is just those letters comes before them all.)
     */
    private fun firstWith(lo: Int, hi: Int, depth: Int, c: Char): Int {
        var a = lo; var b = hi
        while (a < b) {
            val m = (a + b) ushr 1
            val word = sorted[m]
            if (word.length <= depth || word[depth] < c) a = m + 1 else b = m
        }
        return a
    }

    private fun anyGoesOn(lo: Int, hi: Int, depth: Int, c: Char): Boolean {
        val at = firstWith(lo, hi, depth, c)
        return at < hi && sorted[at].length > depth && sorted[at][depth] == c
    }

    private fun adjacentToNeighbour(w: String, i: Int) =
        (i > 0 && adjacent(w[i], w[i - 1])) || (i < w.length - 1 && adjacent(w[i], w[i + 1]))

    companion object {
        private const val MAX_LENGTH = 24
        /** A three-letter word known only with capitals gets them when it is at least this common ("Tim", not "Zen"). */
        private const val SHORT_CAPITAL_SCORE = 13
        /** The one- and two-letter words that do get their capitals. */
        private val SHORT_CAPITALS = setOf("i", "ok")
        /** A one-letter prefix begins thousands of words: look through this many at most. */
        private const val COMPLETION_SCAN = 15000
        /**
         * How many log2-frequency points one unit of edit cost is worth. At 4 a word twice as
         * common won over a likelier slip: "trest" became "rest" (drop a letter, 0.7) and not
         * "treat" (the key next door, 0.6). The values below were chosen together, on 3,000
         * made-up touch typos of the list's own words, drawn by how common they are, and
         * checked on two more such sets: 85% fixed and 13% made into the wrong word before,
         * 89% and 10% after.
         */
        private const val COST_WEIGHT = 8f
        /** A letter too many beside a neighbouring key, and anywhere else. A slip onto the key next door (0.6) is likelier than either. */
        private const val EXTRA_NEAR = 0.9f
        private const val EXTRA_FAR = 1.15f
        /** One vowel for another that is not its neighbour: a spelling mistake, not a slip. */
        private const val WRONG_VOWEL = 1.1f
        /** A letter not typed, other than one of a pair. */
        private const val MISSED = 0.9f
        /**
         * What a spelling that changes, drops or misses the first letter is ranked lower by, on
         * top of the slip itself: the first letter is the one people get right. From webOS
         * CE's SmartKey engine (SkCorrector.cpp's firstLetter, c4ed085).
         */
        private const val FIRST_LETTER = 0.3f
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        private const val NONE = '\u0000'
        private const val VOWELS = "aeiouy"

        /**
         * Key centres on the TouchPad's tablet layout, in key widths, from [Keymap]'s own
         * weights: the home row starts half a key in (webOS's half-width zone at the edge), and
         * the bottom row a whole key in, behind shift. Neighbours are keys less than a key and a
         * bit apart, in the same row or the rows either side, diagonals too.
         */
        private val position: Map<Char, Pair<Float, Int>> = HashMap<Char, Pair<Float, Int>>().apply {
            for ((r, row) in Keymap.rows.withIndex()) {
                var x = 0f
                for (key in row) {
                    val w = Math.abs(key.weight)
                    val c = key.main.toChar()
                    if (key.weight > 0 && c in 'a'..'z') put(c, (x + w / 2) to r)
                    x += w
                }
            }
        }

        /** [adjacent] for every pair of letters, worked out once: it is asked a million times for one long word. */
        private val neighbours = BooleanArray(26 * 26).also { t ->
            for (a in 'a'..'z') for (b in 'a'..'z') {
                val p = position[a] ?: continue
                val q = position[b] ?: continue
                t[(a - 'a') * 26 + (b - 'a')] = Math.abs(p.second - q.second) <= 1 && Math.abs(p.first - q.first) <= 1.1f
            }
        }

        fun adjacent(a: Char, b: Char): Boolean =
            a in 'a'..'z' && b in 'a'..'z' && neighbours[(a - 'a') * 26 + (b - 'a')]

        private fun vowel(c: Char) = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y'

        /** [word] with the capitals of [typed]: "Teh" -> "The". A word's own capitals stay. */
        fun casedLike(typed: String, word: String): String =
            if (typed[0].isUpperCase() && word[0].isLowerCase()) word.replaceFirstChar { it.uppercaseChar() } else word

        val CONTRACTIONS: Map<String, String> = mapOf(
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "isnt" to "isn't",
            "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't", "hasnt" to "hasn't",
            "havent" to "haven't", "hadnt" to "hadn't", "doesnt" to "doesn't", "didnt" to "didn't",
            "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't",
            "mustnt" to "mustn't", "neednt" to "needn't", "aint" to "ain't",
            "im" to "I'm", "ive" to "I've", "id" to "I'd",
            "youre" to "you're", "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
            "theyre" to "they're", "theyve" to "they've", "theyll" to "they'll", "theyd" to "they'd",
            "weve" to "we've", "hes" to "he's", "shes" to "she's",
            "thats" to "that's", "whats" to "what's", "wheres" to "where's", "whos" to "who's",
            "hows" to "how's", "theres" to "there's", "heres" to "here's", "itll" to "it'll",
            "thatll" to "that'll", "wouldve" to "would've", "couldve" to "could've",
            "shouldve" to "should've", "mightve" to "might've", "mustve" to "must've",
            "yall" to "y'all", "oclock" to "o'clock", "whatre" to "what're", "whove" to "who've",
        )
    }
}
