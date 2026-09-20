package org.webosarchive.keyboard

/**
 * The US QWERTY tablet layout, ported key for key from LunaCE's
 * `Src/ime/tabletkeymaps/en.h` and `common_keys.h`. Weights, alternate characters and
 * press-and-hold lists are webOS's, not guesses: see README.md.
 */

/** What a key does when it isn't a character. Negative, so characters stay their code point. */
object Fn {
    const val NONE = 0
    const val SHIFT = -1
    const val BACKSPACE = -2
    const val RETURN = -3
    const val TAB = -4
    const val SYMBOL = -5
    const val HIDE = -6
    const val TRACKBALL = -7

    fun isFunction(k: Int) = k < 0
}

/**
 * One key. [weight] is its width in key units, as webOS counted them; a negative weight is a
 * zone that types [main] but draws no key, which is how the left edge of the home row still
 * types "a". [alt] is the character the symbol page types, [altText] the string it types
 * instead (the emoticons), and [extended] the press-and-hold list, whose first character is
 * always the key's own.
 */
class Key(
    val weight: Float,
    val main: Int,
    val alt: Int = main,
    val extended: String? = null,
    val altText: String? = null,
)

private fun k(w: Float, main: Char, alt: Char = main, ext: String? = null, altText: String? = null) =
    Key(w, main.code, alt.code, ext, altText)

private fun fn(w: Float, code: Int) = Key(w, code)

object Keymap {
    /**
     * webOS's five rows. The number row is short (the art gives it a shorter key), the rest
     * are full height; [rowWeights] comes from the art itself in [KeyboardView].
     */
    val rows: Array<Array<Key>> = arrayOf(
        arrayOf(
            k(1f, '1', '!', "1!¹¼½¡"),
            k(1f, '2', '@', "2@²"),
            k(1f, '3', '#', "3#³¾"),
            k(1f, '4', '$', "4$€£¥¢¤"),
            k(1f, '5', '%', "5%‰"),
            k(1f, '6', '^'),
            k(1f, '7', '&'),
            k(1f, '8', '*'),
            k(1f, '9', '(', "9([{"),
            k(1f, '0', ')', "0)]}"),
            fn(2f, Fn.TRACKBALL),
        ),
        arrayOf(
            k(1f, 'q', '`'),
            k(1f, 'w', '~'),
            k(1f, 'e', '€', "eèéêëęē"),
            k(1f, 'r', '£', "r®"),
            k(1f, 't', '\\', "t™þ"),
            k(1f, 'y', '|', "yýÿ"),
            k(1f, 'u', '{', "uùúûüű"),
            k(1f, 'i', '}', "iìíîïİı"),
            k(1f, 'o', '[', "oòóôõöøőœºω"),
            k(1f, 'p', ']', "p§π"),
            fn(1f, Fn.BACKSPACE),
        ),
        arrayOf(
            // A half-width zone at the screen edge that still types "a" (webOS's negative weight).
            k(-0.5f, 'a', '<'),
            k(1f, 'a', '<', "aàáâãäåæª"),
            k(1f, 's', '>', "sšŞßσ"),
            k(1f, 'd', '=', "dð†‡"),
            k(1f, 'f', '+'),
            k(1f, 'g', '×', "gğ"),
            k(1f, 'h', '÷'),
            k(1f, 'j', '°'),
            k(1f, 'k', ';'),
            k(1f, 'l', ':', "lŁ"),
            fn(1.5f, Fn.RETURN),
        ),
        arrayOf(
            fn(1f, Fn.SHIFT),
            k(1f, 'z', 'z', "zžźż", altText = ":-)"),
            k(1f, 'x', 'x', null, altText = ";-)"),
            k(1f, 'c', 'c', "cçć©¢", altText = ":-("),
            k(1f, 'v', 'v', null, altText = ":'("),
            k(1f, 'b', 'b', null, altText = ":-P"),
            k(1f, 'n', 'n', "nñń", altText = ":-O"),
            k(1f, 'm', 'm', "mµ", altText = "<3"),
            k(1f, ',', '/', ",/\\"),
            k(1f, '.', '?', ".?•…¿"),
            fn(1f, Fn.SHIFT),
        ),
        arrayOf(
            fn(1f, Fn.TAB),
            // Two units wide while only one layout is installed: webOS gave the second unit to
            // the language key as soon as there was more than one (TabletKeymap::updateLanguageKey).
            fn(2f, Fn.SYMBOL),
            k(5f, ' '),
            k(1f, '\'', '"', "'\"`‘’“”"),
            k(1f, '-', '_', "-_±¬"),
            fn(1f, Fn.HIDE),
        ),
    )

    /** The label on the symbol key: webOS spaced it with hair spaces. */
    const val SYMBOL_LABEL = "+ = [  ]"

}
