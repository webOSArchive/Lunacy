package org.webosarchive.lunacy.card

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds as Kinds
import android.provider.ContactsContract.Data
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Android's contacts as a webOS account: the Contacts side of the "Android" account (its template
 * and setup page are CoreApps/org.webosarchive.lunacy.androidaccount; [AndroidCalendar] is the
 * other side) mirrors ContactsContract into [KIND], one way. The linker joins those records to the same person in other accounts, as it
 * does for any account's.
 *
 * Nothing is ever written to Android, so no change here can reach an account Android syncs. The
 * template marks the data read-only, so Contacts offers no editing; an edit to such a person
 * makes a linked on-device contact, as for any read-only account, and stays in Lunacy.
 *
 * The copy is made only once the owner adds the account, and is removed with it. It is a diff
 * keyed by Android's lookup key ([Db8.syncAccount]), not [Db8.mirror]: a deleted contact has to
 * leave a tombstone for the linker to let its person go, and Android's provider shouldn't be
 * read every few seconds. If Android can't be read (the permission taken away, say), the last
 * copy stays as it is rather than being taken for an empty list.
 */
class AndroidContacts(context: Context, webosRoot: File, private val db: Db8) : AndroidAccountSide(
    context, db, "androidcontacts", "org.webosarchive.lunacy.androidaccount.contacts", Manifest.permission.READ_CONTACTS, ContactsContract.Contacts.CONTENT_URI, START_DELAY_MS,
) {
    private val photos = File(UserFiles.own(webosRoot), ".contactphotos")

    fun attach(bus: Bus) {
        db.configure(listOf(kindSpec()), emptyList())
        registerAccount(bus, SERVICE, "onEnabled", "onDelete")
    }

    override fun remove(id: String) {
        db.syncAccount(KIND, id, emptyList()) { _, _, removed -> Log.i(AppServer.TAG, "androidcontacts: removed $removed") }
        cleanPhotos(emptySet())
    }

    /** Counts only in the log, here and everywhere in this class: these are real people's details. */
    override fun sync() {
        val id = accountId() ?: return
        if (!granted()) { Log.w(AppServer.TAG, "androidcontacts: no permission; the copy stays as it was"); return }
        startWatching()
        val wanted = HashSet<String>()
        val records = try { read(wanted) } catch (e: Exception) {
            Log.w(AppServer.TAG, "androidcontacts: can't read Android's contacts: ${e.javaClass.simpleName}"); null
        } ?: return
        if (accountId() != id) return
        db.syncAccount(KIND, id, records) { added, updated, removed ->
            Log.i(AppServer.TAG, "androidcontacts: ${records.size} contacts; added $added, changed $updated, removed $removed")
        }
        cleanPhotos(wanted)
    }

    /** One contact as it is built up from its rows in Android's data table. */
    private class Entry(val key: String, val displayName: String) {
        val name = JSONObject().put("givenName", "").put("middleName", "").put("familyName", "").put("honorificPrefix", "").put("honorificSuffix", "")
        var named = false
        var nickname = ""; var note = ""; var birthday = ""; var anniversary = ""
        val phones = JSONArray(); val emails = JSONArray(); val addresses = JSONArray(); val organizations = JSONArray()
        val urls = JSONArray(); val ims = JSONArray(); val relations = JSONArray()
        var photo: JSONArray? = null
        var photoTried = false
        val seen = HashSet<String>()
        /** Android joins several accounts' copies of a contact: one line for each detail. */
        fun add(to: JSONArray, what: String, value: JSONObject) { if (seen.add("$what|$value")) to.put(value) }
    }

    /**
     * Android's contacts, every account's, as com.palm.contact records. Null if Android gave no
     * answer. Not only those Android calls visible (`IN_VISIBLE_GROUP`): an Exchange account's
     * contacts are in no group, and its "ungrouped visible" setting is off unless something wrote
     * one, so on the Kyocera 5,686 of 5,717 contacts were left out, though Android's own Contacts
     * app shows them all. A raw contact marked deleted (waiting for its account to sync the
     * deletion) is left out; the data table has no column for that, so its ids are read first.
     */
    private fun read(wanted: MutableSet<String>): List<JSONObject>? {
        val deleted = HashSet<Long>()
        context.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.DELETED}=1", null, null)?.use { c -> while (c.moveToNext()) deleted += c.getLong(0) } ?: return null
        val cursor = context.contentResolver.query(Data.CONTENT_URI, PROJECTION, null, null,
            "${Data.LOOKUP_KEY}, ${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC") ?: return null
        val out = LinkedHashMap<String, Entry>()
        cursor.use { c ->
            val rawColumn = c.getColumnIndex(Data.RAW_CONTACT_ID)
            while (c.moveToNext()) {
                if (deleted.isNotEmpty() && c.getLong(rawColumn) in deleted) continue
                val key = c.text(Data.LOOKUP_KEY)
                if (key.isEmpty()) continue
                val e = out.getOrPut(key) { Entry(key, c.text(Data.DISPLAY_NAME)) }
                row(c, e)
                if (!e.photoTried) {
                    e.photoTried = true
                    val uri = c.text(Data.PHOTO_URI)
                    if (uri.isNotEmpty()) e.photo = photo(key, c.text(Data.PHOTO_ID), Uri.parse(uri), wanted)
                }
            }
        }
        return out.values.map(::record)
    }

    private fun Cursor.text(column: String): String = getColumnIndex(column).let { if (it < 0 || isNull(it)) "" else getString(it).orEmpty().trim() }
    private fun Cursor.number(column: String): Int = getColumnIndex(column).let { if (it < 0 || isNull(it)) 0 else getInt(it) }

    private fun row(c: Cursor, e: Entry) {
        val primary = c.number(Data.IS_PRIMARY) != 0
        fun line(value: String, type: String) = JSONObject().put("value", value).put("type", type).put("primary", primary)
        when (c.text(Data.MIMETYPE)) {
            Kinds.StructuredName.CONTENT_ITEM_TYPE -> if (!e.named) {
                val given = c.text(Kinds.StructuredName.GIVEN_NAME); val family = c.text(Kinds.StructuredName.FAMILY_NAME)
                if (given.isNotEmpty() || family.isNotEmpty()) {
                    e.named = true
                    e.name.put("givenName", given).put("familyName", family).put("middleName", c.text(Kinds.StructuredName.MIDDLE_NAME))
                        .put("honorificPrefix", c.text(Kinds.StructuredName.PREFIX)).put("honorificSuffix", c.text(Kinds.StructuredName.SUFFIX))
                }
            }
            Kinds.Nickname.CONTENT_ITEM_TYPE -> if (e.nickname.isEmpty()) e.nickname = c.text(Kinds.Nickname.NAME)
            Kinds.Note.CONTENT_ITEM_TYPE -> if (e.note.isEmpty()) e.note = c.text(Kinds.Note.NOTE)
            Kinds.Phone.CONTENT_ITEM_TYPE -> c.text(Kinds.Phone.NUMBER).takeIf { it.isNotEmpty() }?.let {
                e.add(e.phones, "phone", line(it, PHONE_TYPES[c.number(Kinds.Phone.TYPE)] ?: "type_other"))
            }
            Kinds.Email.CONTENT_ITEM_TYPE -> c.text(Kinds.Email.ADDRESS).takeIf { it.isNotEmpty() }?.let {
                e.add(e.emails, "email", line(it, placeType(c.number(Kinds.Email.TYPE), Kinds.Email.TYPE_HOME, Kinds.Email.TYPE_WORK)))
            }
            Kinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                val street = c.text(Kinds.StructuredPostal.STREET).ifEmpty { c.text(Kinds.StructuredPostal.FORMATTED_ADDRESS) }
                if (street.isNotEmpty()) e.add(e.addresses, "address", JSONObject().put("streetAddress", street)
                    .put("locality", c.text(Kinds.StructuredPostal.CITY)).put("region", c.text(Kinds.StructuredPostal.REGION))
                    .put("postalCode", c.text(Kinds.StructuredPostal.POSTCODE)).put("country", c.text(Kinds.StructuredPostal.COUNTRY))
                    .put("type", placeType(c.number(Kinds.StructuredPostal.TYPE), Kinds.StructuredPostal.TYPE_HOME, Kinds.StructuredPostal.TYPE_WORK))
                    .put("primary", primary))
            }
            Kinds.Organization.CONTENT_ITEM_TYPE -> {
                val company = c.text(Kinds.Organization.COMPANY); val title = c.text(Kinds.Organization.TITLE)
                if (company.isNotEmpty() || title.isNotEmpty()) e.add(e.organizations, "organization", JSONObject().put("name", company)
                    .put("department", c.text(Kinds.Organization.DEPARTMENT)).put("title", title).put("type", "").put("startDate", "")
                    .put("endDate", "").put("description", ""))
            }
            Kinds.Website.CONTENT_ITEM_TYPE -> c.text(Kinds.Website.URL).takeIf { it.isNotEmpty() }?.let {
                e.add(e.urls, "url", line(it, URL_TYPES[c.number(Kinds.Website.TYPE)] ?: "type_other"))
            }
            Kinds.Im.CONTENT_ITEM_TYPE -> c.text(Kinds.Im.DATA).takeIf { it.isNotEmpty() }?.let {
                e.add(e.ims, "im", JSONObject().put("value", it).put("type", IM_TYPES[c.number(Kinds.Im.PROTOCOL)] ?: "type_default")
                    .put("label", "").put("primary", primary))
            }
            Kinds.Relation.CONTENT_ITEM_TYPE -> c.text(Kinds.Relation.NAME).takeIf { it.isNotEmpty() }?.let {
                e.add(e.relations, "relation", line(it, RELATION_TYPES[c.number(Kinds.Relation.TYPE)] ?: "type_other"))
            }
            Kinds.Event.CONTENT_ITEM_TYPE -> date(c.text(Kinds.Event.START_DATE))?.let {
                when (c.number(Kinds.Event.TYPE)) {
                    Kinds.Event.TYPE_BIRTHDAY -> if (e.birthday.isEmpty()) e.birthday = it
                    Kinds.Event.TYPE_ANNIVERSARY -> if (e.anniversary.isEmpty()) e.anniversary = it
                }
            }
        }
    }

    private fun placeType(type: Int, home: Int, work: Int) = when (type) { home -> "type_home"; work -> "type_work"; else -> "type_other" }

    /** webOS's YYYY-MM-DD, with the year 1900 for none. Android's own form is --MM-DD. */
    private fun date(text: String): String? {
        Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(text)?.let { return it.value }
        return Regex("""^--(\d{2})-(\d{2})""").find(text)?.let { "1900-${it.groupValues[1]}-${it.groupValues[2]}" }
    }

    /**
     * Every property, always, even when empty: db8 merges an update into the record, so one left
     * out would keep its old value after it was removed on Android.
     */
    private fun record(e: Entry) = JSONObject().put("remoteId", e.key).put("displayName", e.displayName).put("name", e.name)
        .put("nickname", e.nickname).put("note", e.note).put("birthday", e.birthday).put("anniversary", e.anniversary)
        .put("phoneNumbers", e.phones).put("emails", e.emails).put("addresses", e.addresses).put("organizations", e.organizations)
        .put("urls", e.urls).put("ims", e.ims).put("relations", e.relations).put("photos", e.photo ?: JSONArray())

    /**
     * A contact's photo, cut to the two squares webOS keeps (ContactPhoto's sizes), centred. The
     * file names carry the photo's id, so a pass only does this for a photo it hasn't seen.
     */
    private fun photo(key: String, version: String, uri: Uri, wanted: MutableSet<String>): JSONArray? {
        val stem = "$PHOTO_PREFIX${hash(key)}-$version"
        val big = "$stem-big.jpg"; val square = "$stem-square.jpg"
        try {
            if (!File(photos, big).isFile || !File(photos, square).isFile) {
                val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= BIG) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
                val side = minOf(bitmap.width, bitmap.height)
                val crop = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
                photos.mkdirs()
                write(crop, BIG, big); write(crop, SQUARE, square)
                if (crop !== bitmap) crop.recycle()
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "androidcontacts: a photo failed: ${e.javaClass.simpleName}"); return null
        }
        wanted += big; wanted += square
        fun entry(file: String, type: String) = "/media/internal/.contactphotos/$file".let {
            JSONObject().put("value", it).put("localPath", it).put("type", type).put("primary", false)
        }
        return JSONArray().put(entry(square, "type_square")).put(entry(big, "type_big"))
    }

    private fun write(source: Bitmap, side: Int, name: String) {
        val scaled = Bitmap.createScaledBitmap(source, side, side, true)
        val temporary = File(photos, ".$name")
        temporary.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        temporary.renameTo(File(photos, name))
        if (scaled !== source) scaled.recycle()
    }

    /** Only this class's files: the folder also holds photos people chose in Contacts. */
    private fun cleanPhotos(wanted: Set<String>) {
        photos.listFiles()?.filter { it.name.startsWith(PHOTO_PREFIX) && it.name !in wanted }?.forEach { it.delete() }
    }

    private fun hash(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    /** Its indexes are its own: db8 serves a query from the queried kind's. */
    private fun kindSpec() = KindSpec.kind(KIND, SERVICE, JSONArray().put(KindSpec.plain("accountId")), "com.palm.contact:1")

    companion object {
        const val SERVICE = "org.webosarchive.lunacy.androidaccount"
        const val KIND = "org.webosarchive.contact.android:1"
        private const val PHOTO_PREFIX = "android-"
        private const val BIG = 196
        private const val SQUARE = 50
        private const val START_DELAY_MS = 5000L
        private val PROJECTION = arrayOf(Data.LOOKUP_KEY, Data.RAW_CONTACT_ID, Data.DISPLAY_NAME, Data.MIMETYPE, Data.IS_PRIMARY, Data.IS_SUPER_PRIMARY, Data.PHOTO_URI, Data.PHOTO_ID,
            Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10)
        private val PHONE_TYPES = mapOf(Kinds.Phone.TYPE_MOBILE to "type_mobile", Kinds.Phone.TYPE_HOME to "type_home", Kinds.Phone.TYPE_WORK to "type_work",
            Kinds.Phone.TYPE_MAIN to "type_main", Kinds.Phone.TYPE_FAX_HOME to "type_personal_fax", Kinds.Phone.TYPE_FAX_WORK to "type_work_fax",
            Kinds.Phone.TYPE_PAGER to "type_pager", Kinds.Phone.TYPE_ASSISTANT to "type_assistant", Kinds.Phone.TYPE_CAR to "type_car",
            Kinds.Phone.TYPE_RADIO to "type_radio", Kinds.Phone.TYPE_COMPANY_MAIN to "type_company")
        private val URL_TYPES = mapOf(Kinds.Website.TYPE_HOME to "type_home", Kinds.Website.TYPE_HOMEPAGE to "type_homepage", Kinds.Website.TYPE_BLOG to "type_blog",
            Kinds.Website.TYPE_FTP to "type_ftp", Kinds.Website.TYPE_PROFILE to "type_profile", Kinds.Website.TYPE_WORK to "type_work")
        @Suppress("DEPRECATION")
        private val IM_TYPES = mapOf(Kinds.Im.PROTOCOL_AIM to "type_aim", Kinds.Im.PROTOCOL_MSN to "type_msn", Kinds.Im.PROTOCOL_YAHOO to "type_yahoo",
            Kinds.Im.PROTOCOL_SKYPE to "type_skype", Kinds.Im.PROTOCOL_QQ to "type_qq", Kinds.Im.PROTOCOL_GOOGLE_TALK to "type_gtalk",
            Kinds.Im.PROTOCOL_ICQ to "type_icq", Kinds.Im.PROTOCOL_JABBER to "type_jabber")
        private val RELATION_TYPES = mapOf(Kinds.Relation.TYPE_ASSISTANT to "type_assistant", Kinds.Relation.TYPE_BROTHER to "type_brother",
            Kinds.Relation.TYPE_CHILD to "type_child", Kinds.Relation.TYPE_DOMESTIC_PARTNER to "type_domestic_partner", Kinds.Relation.TYPE_FATHER to "type_father",
            Kinds.Relation.TYPE_FRIEND to "type_friend", Kinds.Relation.TYPE_MANAGER to "type_manager", Kinds.Relation.TYPE_MOTHER to "type_mother",
            Kinds.Relation.TYPE_PARENT to "type_parent", Kinds.Relation.TYPE_PARTNER to "type_partner", Kinds.Relation.TYPE_REFERRED_BY to "type_referred_by",
            Kinds.Relation.TYPE_RELATIVE to "type_relative", Kinds.Relation.TYPE_SISTER to "type_sister", Kinds.Relation.TYPE_SPOUSE to "type_spouse")
    }
}
