package org.webosarchive.lunacy.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.util.Log
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.Db8
import org.webosarchive.lunacy.card.ShareToEmail
import org.webosarchive.lunacy.card.UserFiles
import java.io.File

/**
 * Email in Android's Share, and for mailto: links (codepoet, 2026-11-20): someone reading a
 * page in an Android browser, whose only mail is Lunacy's, shares it and Email's compose card
 * opens with the page's title and address.
 *
 * Android lists the manifest's EmailShareAlias, "Email" with Email's icon, only while there
 * is a mail account: a Lunacy without mail is in nobody's share sheet (Zen of Palm). What is
 * shared becomes the launch parameters Palm documented for apps ([ShareToEmail]); the shell
 * opens Email with them and nothing is sent until the owner presses Send.
 *
 * A shared file comes as an address only this process may read, and Email attaches by path,
 * as the mail services read it when they send. So each is copied into Lunacy's own
 * /media/internal first ([ShareToEmail.SHARED_DIR], hidden; a week's copies are kept, for a
 * message waiting in the outbox). Another app chooses that address, so one that names
 * Lunacy's own files is refused: nothing of Lunacy's can be put into a message this way.
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

    /** What an intent shared: text, people, and the addresses of files still to be copied in. */
    class Shared internal constructor(private val subject: String?, private val text: String?, private val to: List<String>,
                                      private val cc: List<String>, private val bcc: List<String>,
                                      private val streams: List<Uri>, private val type: String?, private val mailto: JSONObject?) {
        /**
         * Email's launch parameters, to [done] on whichever thread finishes: at once when there
         * are no files, else after they are copied under [webosRoot]. A file that can't be
         * read is left out and logged; nothing to compose at all never calls [done].
         */
        fun params(context: Context, webosRoot: File, done: (JSONObject) -> Unit) {
            if (mailto != null) { done(mailto); return }
            if (streams.isEmpty()) { ShareToEmail.compose(subject, text, to, cc, bcc)?.let(done); return }
            val app = context.applicationContext
            Thread({
                val media = UserFiles.own(webosRoot)
                val dir = ShareToEmail.newSharedFolder(media)
                val attached = streams.mapNotNull { copy(app, it, dir, type) }
                if (attached.isEmpty()) dir.delete()
                Log.i(AppServer.TAG, "share: ${attached.size} of ${streams.size} files copied in")
                val files = attached.map { (f, mime) -> ShareToEmail.Attachment("/media/internal/${ShareToEmail.SHARED_DIR}/${dir.name}/${f.name}", f.name, mime) }
                ShareToEmail.compose(subject, text, to, cc, bcc, files)?.let(done)
            }, "share-copy").start()
        }
    }

    companion object {
        private const val ALIAS = "org.webosarchive.lunacy.shell.EmailShareAlias"

        /**
         * What [intent] shared for sending, or its mailto: link. Null for any other intent,
         * and for one Android replays from the recent apps, which was acted on when it first
         * came.
         */
        fun shared(intent: Intent): Shared? {
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            return when (intent.action) {
                Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> Shared(subject, text, intent.addresses(Intent.EXTRA_EMAIL),
                    intent.addresses(Intent.EXTRA_CC), intent.addresses(Intent.EXTRA_BCC), streams(intent), intent.type, null)
                Intent.ACTION_SENDTO, Intent.ACTION_VIEW ->
                    intent.dataString?.takeIf { intent.scheme.equals("mailto", ignoreCase = true) }
                        ?.let { ShareToEmail.mailto(it, subject, text) }
                        ?.let { Shared(null, null, emptyList(), emptyList(), emptyList(), emptyList(), null, it) }
                else -> null
            }
        }

        private fun Intent.addresses(key: String): List<String> = getStringArrayExtra(key)?.filterNotNull() ?: emptyList()

        /** EXTRA_STREAM's addresses, one or a list whichever the action; anything else an app put there is ignored. */
        private fun streams(intent: Intent): List<Uri> = try {
            @Suppress("DEPRECATION")
            when (val extra = intent.extras?.get(Intent.EXTRA_STREAM)) {
                is Uri -> listOf(extra)
                is List<*> -> extra.filterIsInstance<Uri>()
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "share: unreadable EXTRA_STREAM: $e"); emptyList()
        }

        /**
         * Whether [uri] is another app's to hand over. It isn't when it names Lunacy's own
         * provider, or a file outside shared storage: the sharing app chose the address, and
         * Lunacy reading it for them would put its own files (mail, passwords' store) in a
         * message.
         */
        internal fun foreign(context: Context, uri: Uri): Boolean = when (uri.scheme?.lowercase()) {
            "content" -> {
                val authority = uri.authority.orEmpty().substringAfterLast('@')
                val owner = runCatching { context.packageManager.resolveContentProvider(authority, 0)?.packageName }.getOrNull()
                owner != null && owner != context.packageName
            }
            "file" -> {
                // Only shared storage, where one app's files are another's to read. Not "anywhere
                // but Lunacy's data": Android reaches that folder by more than one path
                // (/data/data and /data/user/0 are both real on Android 14), so naming what is
                // Lunacy's can't be trusted to catch every way in.
                val f = runCatching { File(uri.path.orEmpty()).canonicalFile }.getOrNull()
                val shared = listOf("/storage/", "/sdcard/", "/mnt/")
                f != null && f.isFile && shared.any { f.path.startsWith(it) } && "/Android/data/${context.packageName}/" !in f.path &&
                    "/Android/obb/${context.packageName}/" !in f.path
            }
            else -> false
        }

        /** Copies [uri]'s file into [dir] under its own name; the copy and its type, or null if it can't be had. */
        private fun copy(context: Context, uri: Uri, dir: File, sharedType: String?): Pair<File, String?>? {
            if (!foreign(context, uri)) { Log.w(AppServer.TAG, "share: refused ${uri.scheme}://${uri.authority.orEmpty()}, not another app's file"); return null }
            return try {
                val cr = context.contentResolver
                var name: String? = null
                if (uri.scheme.equals("content", ignoreCase = true)) {
                    cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0) }
                }
                val mime = cr.getType(uri) ?: sharedType?.takeIf { '*' !in it }
                var safe = ShareToEmail.safeName(name ?: uri.lastPathSegment)
                // A name with no ending gets its type's, so the other end can open it.
                if ('.' !in safe) MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { safe = "$safe.$it" }
                var out = File(dir, safe)
                var n = 2
                while (out.exists()) out = File(dir, "${safe.substringBeforeLast('.')} ${n++}" + (if ('.' in safe) "." + safe.substringAfterLast('.') else ""))
                // A file: address is opened by the resolved path that was checked, not looked up again.
                val input = if (uri.scheme.equals("file", ignoreCase = true)) File(uri.path.orEmpty()).canonicalFile.inputStream() else cr.openInputStream(uri) ?: return null
                input.use { i -> out.outputStream().use { i.copyTo(it, 64 * 1024) } }
                out to mime
            } catch (e: Exception) {
                // SecurityException for an address the app gave no leave to read; IOException for a full disk.
                Log.w(AppServer.TAG, "share: couldn't copy a shared file in: $e")
                null
            }
        }
    }
}
