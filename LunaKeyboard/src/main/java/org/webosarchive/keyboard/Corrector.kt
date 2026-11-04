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
 * - A lone "i", and a proper noun typed in lower case, get their capital.
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
            // Known. Only its capital can be wrong: "i", or "london" for "London".
            return if (typed == lower && e.word != lower) e.word else null
        }
        if (lower.length < 2 || '\'' in lower) return null

        val best = nearest(lower) ?: return null
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

    private fun nearest(w: String): String? = nearest(w, 1).firstOrNull()

    /** The likeliest other spellings of [typed], in its case, for the bubble. */
    fun alternates(typed: String, max: Int): List<String> {
        val lower = typed.lowercase()
        if (lower.length < 2 || !typed.all { it.isLetter() }) return emptyList()
        return nearest(lower, max).map { casedLike(typed, it) }
    }

    private fun nearest(w: String, max: Int): List<String> {
        val one = HashMap<String, Float>()
        edits(w, 0f, one, full = true)
        pick(one, maxCost = if (w.length <= 3) 0.6f else 1.3f, minScore = if (w.length <= 3) 12 else 5, max)
            .let { if (it.isNotEmpty()) return it }
        if (w.length < 5) return emptyList()
        // Two slips: grow every one-slip spelling once more, with a narrower set of edits so a
        // long word stays quick even on an old phone.
        val two = HashMap<String, Float>()
        for ((s, c) in one) edits(s, c, two, full = false)
        return pick(two, maxCost = 1.9f, minScore = 9, max)
    }

    /** The best known words among [cands]: common, and cheap to reach. Best first. */
    private fun pick(cands: Map<String, Float>, maxCost: Float, minScore: Int, max: Int): List<String> {
        val found = ArrayList<Pair<Entry, Float>>()
        for ((s, cost) in cands) {
            if (cost > maxCost) continue
            val e = words[s] ?: continue
            if (e.score < minScore) continue
            found += e to (e.score - COST_WEIGHT * cost)
        }
        return found.sortedByDescending { it.second }.take(max).map { it.first.word }
    }

    /** Every spelling one slip away from [w], with what the slip costs, into [out]. */
    private fun edits(w: String, base: Float, out: HashMap<String, Float>, full: Boolean) {
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
            add(sb.toString(), if (i > 0 && w[i] == w[i - 1]) 0.5f else if (adjacentToNeighbour(w, i)) 0.7f else 1f)
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
                    w[i] in VOWELS && c in VOWELS -> 0.9f
                    else -> if (full) 1.3f else continue
                }
                sb.setLength(0); sb.append(w, 0, i).append(c).append(w, i + 1, n)
                add(sb.toString(), cost)
            }
        }
        // A missed letter.
        for (i in 0..n) {
            for (c in ALPHABET) {
                val doubling = (i > 0 && w[i - 1] == c) || (i < n && w[i] == c)
                if (!full && !doubling && c !in VOWELS &&
                    !(i > 0 && adjacent(w[i - 1], c)) && !(i < n && adjacent(w[i], c))) continue
                sb.setLength(0); sb.append(w, 0, i).append(c).append(w, i, n)
                add(sb.toString(), if (doubling) 0.5f else 1f)
            }
        }
    }

    private fun adjacentToNeighbour(w: String, i: Int) =
        (i > 0 && adjacent(w[i], w[i - 1])) || (i < w.length - 1 && adjacent(w[i], w[i + 1]))

    companion object {
        private const val MAX_LENGTH = 24
        /** A one-letter prefix begins thousands of words: look through this many at most. */
        private const val COMPLETION_SCAN = 15000
        /** How many log2-frequency points one unit of edit cost is worth. */
        private const val COST_WEIGHT = 4f
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
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

        fun adjacent(a: Char, b: Char): Boolean {
            val p = position[a] ?: return false
            val q = position[b] ?: return false
            return Math.abs(p.second - q.second) <= 1 && Math.abs(p.first - q.first) <= 1.1f
        }

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
