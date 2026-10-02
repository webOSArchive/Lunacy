package org.webosarchive.lunacy.card

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Phone or tablet: the one decision everything else about the layout follows from. The shell
 * draws its phone layout (Docs/phone.md) on a phone, and apps are told they are on a Pre3
 * there and a TouchPad on a tablet ([DeviceProfile.forScreen]), so the two can never disagree.
 *
 * Decided from the screen - its size, its pixel count and its shape, which are the clues a
 * screen gives - unless the owner has said which they want: the `layout` preference,
 * "auto", "phone" or "tablet", set from the Device Info app (`system/setLayout` on Lunacy's
 * own service) or over adb (`--es layout phone`). [describe] says what was decided and why,
 * for Device Info to show.
 */
object FormFactor {
    enum class Kind { PHONE, TABLET }

    const val AUTO = "auto"
    const val PHONE = "phone"
    const val TABLET = "tablet"

    /**
     * Android's own line between a phone and a tablet, in density-independent pixels of the
     * screen's short side; the `sw600dp` every Android app's tablet resources sit behind.
     */
    const val TABLET_SHORT_SIDE_DP = 600
    /** A screen smaller than this across the diagonal is held in one hand, whatever it claims its density is. */
    const val TABLET_DIAGONAL_INCHES = 7.0
    /**
     * A screen this much taller than it is wide is a phone's shape: 16:9 and the taller
     * screens since. A TouchPad is 4:3 and most tablets are 16:10 or squarer.
     */
    const val PHONE_ASPECT = 1.7

    private const val PREFS = "device"
    private const val KEY = "layout"

    /** The owner's setting: "auto", "phone" or "tablet". */
    fun setting(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AUTO) ?: AUTO

    /** Stores a setting; returns false for a value that isn't one. Takes effect when the shell restarts. */
    fun setSetting(context: Context, value: String): Boolean {
        if (value != AUTO && value != PHONE && value != TABLET) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
        return true
    }

    /**
     * On a phone, the width in px an app's page is laid out at, scaled down to fit the card
     * (Docs/phone.md, "App scale"): a TouchPad app (Palm's Clock is 514 px wide, Memos 640)
     * gets room to lay out in, and the card then shows it whole, smaller. 0 lays pages out at
     * the card's own width. Nothing on a tablet.
     */
    const val APP_LAYOUT_WIDTH_DEFAULT = 640
    private const val APP_WIDTH_KEY = "appLayoutWidth"

    fun appLayoutWidth(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(APP_WIDTH_KEY, APP_LAYOUT_WIDTH_DEFAULT)

    fun setAppLayoutWidth(context: Context, width: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(APP_WIDTH_KEY, width.coerceIn(0, 2048)).apply()

    /** What the shell is: the setting if there is one, else what the screen says. */
    fun of(context: Context): Kind = when (setting(context)) {
        PHONE -> Kind.PHONE
        TABLET -> Kind.TABLET
        else -> detect(context)
    }

    fun isPhone(context: Context) = of(context) == Kind.PHONE

    /** The screen's clues, and the answer they give. */
    class Clues(val widthPx: Int, val heightPx: Int, val shortSideDp: Int, val diagonalInches: Double, val aspect: Double) {
        val smallInDp get() = shortSideDp < TABLET_SHORT_SIDE_DP
        val smallInInches get() = diagonalInches < TABLET_DIAGONAL_INCHES
        val phoneShaped get() = aspect >= PHONE_ASPECT
        /**
         * The size in density-independent pixels and the size in inches each say phone or
         * tablet; when they agree that is the answer, and when they don't (a small tablet set
         * to a dense configuration, a large phone that reports a loose one) the shape decides.
         */
        val kind: Kind get() = when {
            smallInDp && smallInInches -> Kind.PHONE
            !smallInDp && !smallInInches -> Kind.TABLET
            else -> if (phoneShaped) Kind.PHONE else Kind.TABLET
        }
        val reasons: List<String> get() = listOf(
            "short side $shortSideDp dp (${if (smallInDp) "under" else "at least"} $TABLET_SHORT_SIDE_DP)",
            "${"%.1f".format(diagonalInches)} in across (${if (smallInInches) "under" else "at least"} ${TABLET_DIAGONAL_INCHES.toInt()})",
            "${"%.2f".format(aspect)}:1 shape (${if (phoneShaped) "a phone's" else "a tablet's"}; the line is $PHONE_ASPECT:1)",
        )
    }

    fun clues(context: Context): Clues {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
        val short = min(m.widthPixels, m.heightPixels); val long = max(m.widthPixels, m.heightPixels)
        val shortDp = (short / (m.densityDpi / 160f)).toInt()
        // The physical dpi, where the panel reports a plausible one; the logical density
        // otherwise (some panels report nonsense, and a 0 would divide).
        fun plausible(dpi: Float) = if (dpi in 60f..1000f) dpi else m.densityDpi.toFloat()
        val xdpi = plausible(m.xdpi); val ydpi = plausible(m.ydpi)
        val diagonal = sqrt((m.widthPixels / xdpi).toDouble().let { it * it } + (m.heightPixels / ydpi).toDouble().let { it * it })
        return Clues(m.widthPixels, m.heightPixels, shortDp, diagonal, long.toDouble() / short)
    }

    fun detect(context: Context): Kind = clues(context).kind

    /** For Device Info and `system/getEnvironment`: the setting, the decision, and the clues. */
    fun describe(context: Context): JSONObject {
        val c = clues(context)
        return JSONObject()
            .put("setting", setting(context))
            .put("layout", if (of(context) == Kind.PHONE) PHONE else TABLET)
            .put("detected", if (c.kind == Kind.PHONE) PHONE else TABLET)
            .put("reasons", org.json.JSONArray(c.reasons))
            .put("screen", "${c.widthPx} × ${c.heightPx}")
            .put("shortSideDp", c.shortSideDp)
            .put("diagonalInches", Math.round(c.diagonalInches * 10) / 10.0)
            .put("aspect", Math.round(c.aspect * 100) / 100.0)
    }
}
