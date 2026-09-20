package org.webosarchive.lunacy.card

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.json.JSONObject

/**
 * webOS's built-in apps, handed to Android's.
 *
 * `applicationManager/open` is how a webOS app opens something it doesn't own: a link, an
 * email, a phone number, a map. It takes either a bare `target` - "open whatever handles this
 * URI" - or the id of one of webOS's own apps with parameters for it. Those apps were part of
 * the OS, and Lunacy doesn't have them; Android does. So `com.palm.app.browser` with a target
 * opens Android's browser, `com.palm.app.email` opens its mail composer, and so on.
 *
 * This is the same bargain as the settings shortcuts (Docs/architecture.md, "Settings"): where
 * the thing belongs to the host OS, hand it over rather than pretend. It is a mapping of
 * webOS's *own* app ids, not a special case for any third-party app - Glimpse, the App Museum
 * and every app menu's Help item all take the same route.
 */
object WebosLinks {
    /** webOS's built-in app ids that Android can answer for. */
    val HANDLED = setOf(
        "com.palm.app.browser", "com.palm.app.email", "com.palm.app.phone",
        "com.palm.app.messaging", "com.palm.app.maps", "com.palm.app.youtube",
    )

    /**
     * The intent for a launch or open request, or null if this isn't one Android can answer.
     * `params` is webOS's own parameter object for the app.
     */
    fun intentFor(appId: String, params: JSONObject?, target: String): Intent? {
        val url = target.ifEmpty { params?.optString("target").orEmpty() }
        return when {
            appId.isEmpty() && url.isNotEmpty() -> view(url)
            appId == "com.palm.app.browser" -> if (url.isEmpty()) null else view(url)
            appId == "com.palm.app.youtube" -> if (url.isEmpty()) null else view(url)
            appId == "com.palm.app.email" -> email(params)
            appId == "com.palm.app.phone" -> phone(params)
            appId == "com.palm.app.messaging" -> message(params)
            appId == "com.palm.app.maps" -> maps(params)
            else -> null
        }
    }

    /** A URI opened as it is: http, https, mailto, tel, sms, geo, market. */
    private fun view(url: String): Intent? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme.isNullOrEmpty()) return null
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /** webOS's email parameters: recipients, summary (the subject) and text. */
    private fun email(p: JSONObject?): Intent {
        val to = recipients(p)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + to.joinToString(",")))
        p?.optString("summary")?.takeIf { it.isNotEmpty() }?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        p?.optString("text")?.takeIf { it.isNotEmpty() }?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        if (to.isNotEmpty()) intent.putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
        return intent
    }

    private fun phone(p: JSONObject?): Intent? {
        val number = p?.optString("number").orEmpty().ifEmpty { p?.optString("target").orEmpty() }
        if (number.isEmpty()) return null
        return Intent(Intent.ACTION_DIAL, Uri.parse(if (number.startsWith("tel:")) number else "tel:$number"))
    }

    private fun message(p: JSONObject?): Intent? {
        val to = recipients(p).firstOrNull().orEmpty()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to"))
        p?.optString("messageText")?.takeIf { it.isNotEmpty() }?.let { intent.putExtra("sms_body", it) }
        return intent
    }

    private fun maps(p: JSONObject?): Intent? {
        val query = p?.optString("query").orEmpty()
        val lat = p?.optString("latitude").orEmpty()
        val lng = p?.optString("longitude").orEmpty()
        val uri = when {
            lat.isNotEmpty() && lng.isNotEmpty() -> "geo:$lat,$lng" + (if (query.isNotEmpty()) "?q=" + Uri.encode(query) else "")
            query.isNotEmpty() -> "geo:0,0?q=" + Uri.encode(query)
            else -> return null
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    }

    /**
     * webOS passed recipients as an array of objects with a `value`, or as a plain string.
     */
    private fun recipients(p: JSONObject?): List<String> {
        val array = p?.optJSONArray("recipients") ?: return listOfNotNull(
            p?.optString("recipient").orEmpty().takeIf { it.isNotEmpty() })
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.optString("value")?.takeIf { it.isNotEmpty() }
                ?: array.optString(i).takeIf { it.isNotEmpty() }
        }
    }

    /** Sends the intent, answering as webOS's applicationManager does. */
    fun open(context: Context, intent: Intent): String {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return Bus.error("Nothing on this device opens ${intent.data}")
        }
        return try {
            context.startActivity(intent)
            Bus.ok(mapOf("processId" to "success"))
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "open ${intent.data} failed", e)
            Bus.error("Couldn't open ${intent.data}: ${e.message}")
        }
    }
}
