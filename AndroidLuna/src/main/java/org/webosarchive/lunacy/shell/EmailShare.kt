package org.webosarchive.lunacy.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.Db8
import org.webosarchive.lunacy.card.ShareToEmail

/**
 * Email in Android's Share, and for mailto: links (codepoet, 2026-11-20): someone reading a
 * page in an Android browser, whose only mail is Lunacy's, shares it and Email's compose card
 * opens with the page's title and address.
 *
 * Android lists the manifest's EmailShareAlias, "Email" with Email's icon, only while there
 * is a mail account: a Lunacy without mail is in nobody's share sheet (Zen of Palm). What is
 * shared becomes the launch parameters Palm documented for apps ([ShareToEmail]); the shell
 * opens Email with them and nothing is sent until the owner presses Send. Text only so far: a
 * shared file (EXTRA_STREAM) would have to be copied into the webOS files first.
 */
class EmailShare(private val context: Context, private val db8: Db8) {
    /** Watches the accounts, and checks once now. */
    fun start() {
        db8.onWrites(BackgroundWork.ACCOUNT_KIND) { recount() }
        recount()
    }

    private fun recount() = db8.count(BackgroundWork.ACCOUNT_KIND, { BackgroundWork.isMail(it) }) { n -> offer(n > 0) }

    private fun offer(on: Boolean) {
        val alias = ComponentName(context, ALIAS)
        val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        try {
            val pm = context.packageManager
            if (pm.getComponentEnabledSetting(alias) == want) return
            pm.setComponentEnabledSetting(alias, want, PackageManager.DONT_KILL_APP)
            Log.i(AppServer.TAG, "share: Email is ${if (on) "offered to" else "withdrawn from"} Android's Share")
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "share: couldn't ${if (on) "offer" else "withdraw"} Email: $e")
        }
    }

    companion object {
        private const val ALIAS = "org.webosarchive.lunacy.shell.EmailShareAlias"

        /**
         * Email's launch parameters for [intent], if it is something shared for sending or a
         * mailto: link. Null for any other intent, and for one Android replays from the recent
         * apps, which was acted on when it first came.
         */
        fun params(intent: Intent): JSONObject? {
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            return when (intent.action) {
                Intent.ACTION_SEND -> ShareToEmail.compose(subject, text,
                    intent.addresses(Intent.EXTRA_EMAIL), intent.addresses(Intent.EXTRA_CC), intent.addresses(Intent.EXTRA_BCC))
                Intent.ACTION_SENDTO, Intent.ACTION_VIEW ->
                    intent.dataString?.takeIf { intent.scheme.equals("mailto", ignoreCase = true) }?.let { ShareToEmail.mailto(it, subject, text) }
                else -> null
            }
        }

        private fun Intent.addresses(key: String): List<String> = getStringArrayExtra(key)?.filterNotNull() ?: emptyList()
    }
}
