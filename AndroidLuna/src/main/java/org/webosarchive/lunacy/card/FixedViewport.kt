package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The fixed-viewport fallback: the one per-app switch the rules allow (Docs/architecture.md,
 * "No per-app hacks"; Docs/phone.md, "Fixed viewport"). An app with it on has its page laid
 * out [FormFactor.appLayoutWidth] px wide and shown scaled down into a narrower card
 * ([AppWindow.fit]), so an app that hard-codes a TouchPad's screen - Palm's Clock, Calculator
 * and Memos do - fits a phone's card whole. An app with it off is laid out at the card's own
 * width, which every app written for the Pre3 wants: it declared its viewport and sized
 * itself. Nothing about what the app is *told* changes; that is [FormFactor]'s.
 *
 * A user setting, per app, in the "viewport" preferences; an app without one takes its
 * default, which is on only for the bundled Palm apps known to hard-code the screen. The
 * setting goes with the app when it is removed.
 */
object FixedViewport {
    private const val PREFS = "viewport"

    /**
     * Palm's own TouchPad apps that Lunacy ships and that draw a fixed 1024 x 768 screen:
     * Clock's 514 px face, Calculator's 500 x 690 panel, Memos' 943 px grid; and the two whose
     * chrome was laid out for a TouchPad's width - the Web app's action bar has no room left
     * for the address on a 360 px card, and App Catalog's (codepoet, 2026-10-04); and Calendar
     * and Contacts, laid out for a tablet without the sliding panels Enyo stacks on a phone,
     * until they get a phone layout of their own (codepoet, 2026-10-08). A shipped default for
     * the user's switch, not a code path: the apps themselves are untouched.
     */
    val DEFAULT_ON = setOf("com.palm.app.clock", "com.palm.calculator", "com.palm.app.notes",
        "com.palm.app.browser", "com.palm.app.enyo-findapps", "com.palm.app.calendar", "com.palm.app.contacts")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The user's explicit setting, or null for an app that has none. */
    fun setting(context: Context, appId: String): Boolean? =
        prefs(context).let { if (it.contains(appId)) it.getBoolean(appId, false) else null }

    fun default(appId: String) = appId in DEFAULT_ON

    /** Whether the app's page gets the fixed viewport. */
    fun isOn(context: Context, appId: String): Boolean = setting(context, appId) ?: default(appId)

    /** Sets the switch; null puts the app back on its default. */
    fun set(context: Context, appId: String, on: Boolean?) {
        prefs(context).edit().apply { if (on == null) remove(appId) else putBoolean(appId, on) }.apply()
    }

    /** The app is gone, and so is its setting. */
    fun forget(context: Context, appId: String) = set(context, appId, null)

    /** The list Device Info shows: every app, with its switch and where it came from. */
    fun describe(context: Context, apps: List<AppInfo>): JSONArray = JSONArray(apps.map { a ->
        JSONObject().put("id", a.id).put("title", a.title).put("version", a.version)
            .put("userInstalled", a.userInstalled)
            .put("on", isOn(context, a.id))
            .put("default", default(a.id))
            .put("set", setting(context, a.id) != null)
    })
}
