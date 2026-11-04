package org.webosarchive.lunacy.card

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Android's calendars as a webOS account: the Calendar side of the Android account
 * ([AndroidContacts] is the other), one way, into [CALENDAR_KIND] and [EVENT_KIND].
 *
 * Nothing is ever written to Android. Calendar treats these calendars as read-only because the
 * records say so (`isReadOnly`; the template's `readOnlyData` is only read by Contacts).
 *
 * The events are a window, a year back and two forward, plus anything repeating that reaches
 * it: years of history would be a heavy copy, and webOS's reminder service reads 500 changes
 * at a time. A repeat webOS can't hold (more than one BYSETPOS day, an hourly one, RDATE) isn't
 * dropped: its occurrences in the window come from Android's Instances table as single events.
 *
 * **Reminders** are the owner's to choose: webOS has no per-calendar switch, so Calendar's
 * setting (setReminders) is an input to the sync. Off, every event carries webOS's "none" alarm,
 * which also closes a reminder showing on the screen; on, Android's. Android's own notifications
 * are separate, so the owner can have either, both or neither.
 *
 * Exceptions are written as dates (yyyyMMdd) in the event's own zone: Calendar walks a repeat's
 * days in that zone, and a date matches whatever the clocks do, where its longer forms are
 * compared at the first occurrence's time of day. An all-day event's noon is the device's, so a change of zone runs a pass.
 */
class AndroidCalendar(context: Context, private val db: Db8) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("androidcalendar", Context.MODE_PRIVATE)
    private val thread = HandlerThread("AndroidCalendar").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private val syncNow = Runnable { sync() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = schedule(CHANGE_DELAY_MS)
    }
    private val zoneChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = schedule(0)
    }
    private var watching = false

    fun attach(bus: Bus) {
        db.configure(listOf(calendarKind(), eventKind()), emptyList())
        bus.register(AndroidContacts.SERVICE, "onCalendarEnabled", Bus.CallHandler(::onEnabled))
        bus.register(AndroidContacts.SERVICE, "onCalendarDelete", Bus.CallHandler(::onDelete))
        bus.register(AndroidContacts.SERVICE, "getReminders", Bus.CallHandler { c -> ifCalendar(c) { c.reply(remindersReply()) } })
        bus.register(AndroidContacts.SERVICE, "setReminders", Bus.CallHandler(::setReminders))
        bus.register(AndroidContacts.SERVICE, "listCalendars", Bus.CallHandler(::listCalendars))
        bus.register(AndroidContacts.SERVICE, "chooseCalendar", Bus.CallHandler(::chooseCalendar))
        if (accountId() != null) handler.post { startWatching(); schedule(START_DELAY_MS) }
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        if (watching) { context.contentResolver.unregisterContentObserver(observer); context.unregisterReceiver(zoneChanged) }
        thread.quitSafely()
    }

    private fun accountId(): String? = prefs.getString("accountId", null)
    private fun remindersOn() = prefs.getBoolean("reminders", true)
    private fun excluded(): Set<String> = prefs.getStringSet("excluded", emptySet()) ?: emptySet()
    private fun remindersReply() = Bus.ok(mapOf("on" to remindersOn(), "active" to (accountId() != null)))

    private fun fromAccounts(call: Bus.Call) = call.appId.substringBefore(' ') == "com.palm.service.accounts"

    /** Calendar's page, the account's page, and the apps that host it. */
    private fun ifCalendar(call: Bus.Call, then: () -> Unit) {
        val app = call.appId.substringBefore(' ')
        if (app in PAGES) then() else call.reply(Bus.error("Not for $app"))
    }

    /** Always true to the accounts service: a failed answer leaves the account stuck. */
    private fun onEnabled(call: Bus.Call) {
        if (!fromAccounts(call)) { call.reply(Bus.error("Only the accounts service calls this")); return }
        val id = call.params.optString("accountId")
        if (id.isEmpty()) { call.reply(Bus.error("accountId is required")); return }
        if (call.params.optBoolean("enabled")) {
            prefs.edit().putString("accountId", id).apply()
            handler.post { handler.removeCallbacksAndMessages(REMOVAL); startWatching(); schedule(0) }
        } else stop(id)
        call.reply(Bus.ok())
    }

    private fun onDelete(call: Bus.Call) {
        if (!fromAccounts(call)) { call.reply(Bus.error("Only the accounts service calls this")); return }
        val id = call.params.optString("accountId")
        val keep = call.params.optBoolean("keepData")
        handler.post {
            handler.removeCallbacksAndMessages(REMOVAL)
            if (id.isNotEmpty() && !keep) remove(id)
        }
        call.reply(Bus.ok())
    }

    /** As [AndroidContacts.stop]: only onDelete says whether to keep the data, so this waits for it. */
    private fun stop(id: String) {
        prefs.edit().remove("accountId").apply()
        handler.post {
            handler.removeCallbacks(syncNow)
            stopWatching()
            handler.postAtTime({ remove(id) }, REMOVAL, android.os.SystemClock.uptimeMillis() + REMOVAL_DELAY_MS)
        }
    }

    private fun remove(id: String) {
        db.syncAccount(EVENT_KIND, id, emptyList())
        db.syncAccount(CALENDAR_KIND, id, emptyList()) { _, _, removed -> Log.i(AppServer.TAG, "androidcalendar: removed $removed calendars") }
    }

    private fun setReminders(call: Bus.Call) = ifCalendar(call) {
        prefs.edit().putBoolean("reminders", call.params.optBoolean("on", true)).apply()
        schedule(0)
        call.reply(remindersReply())
    }

    /** What the account's page offers: leaving one out is how an account set up on both sides isn't shown twice. */
    private fun listCalendars(call: Bus.Call) = ifCalendar(call) {
        handler.post {
            val out = JSONArray()
            val list = try { calendars() } catch (e: Exception) { null }
            list?.forEach { (id, name) -> out.put(JSONObject().put("id", id).put("name", name).put("on", id !in excluded())) }
            // Read here, answered on the bus's thread (Bus: the main thread).
            val answer = if (list == null) Bus.error("Android's calendars can't be read") else Bus.ok(mapOf("calendars" to out))
            mainHandler.post { call.reply(answer) }
        }
    }

    private fun chooseCalendar(call: Bus.Call) = ifCalendar(call) {
        val id = call.params.optString("id")
        val set = excluded().toMutableSet()
        if (call.params.optBoolean("on", true)) set -= id else set += id
        prefs.edit().putStringSet("excluded", set).apply()
        schedule(0)
        call.reply(Bus.ok())
    }

    private fun granted() = context.checkPermission(Manifest.permission.READ_CALENDAR, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED

    private fun startWatching() {
        if (watching || !granted()) return
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        context.registerReceiver(zoneChanged, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
        watching = true
    }

    private fun stopWatching() {
        if (!watching) return
        context.contentResolver.unregisterContentObserver(observer)
        context.unregisterReceiver(zoneChanged)
        watching = false
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(syncNow)
        handler.postDelayed(syncNow, delay)
    }

    /** Counts only in the log: these are people's plans. */
    private fun sync() {
        val id = accountId() ?: return
        if (!granted()) { Log.w(AppServer.TAG, "androidcalendar: no permission; the copy stays as it was"); return }
        startWatching()
        val cals: List<Pair<String, String>>
        val events: List<JSONObject>
        try {
            cals = calendars().filter { it.first !in excluded() }
            events = read(cals.map { it.first }) ?: return
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "androidcalendar: can't read Android's calendars: ${e.javaClass.simpleName}"); return
        }
        if (accountId() != id) return
        db.syncAccount(CALENDAR_KIND, id, cals.map { (c, name) ->
            // No colour or visible: Calendar gives one and the owner may change it, and they stay.
            JSONObject().put("_id", "android-cal-$c").put("remoteId", "cal-$c").put("name", name).put("isReadOnly", true)
                .put("syncSource", "android").put("excludeFromAll", false)
        })
        db.syncAccount(EVENT_KIND, id, events) { added, updated, removed ->
            Log.i(AppServer.TAG, "androidcalendar: ${cals.size} calendars, ${events.size} events; added $added, changed $updated, removed $removed")
        }
    }

    private fun Cursor.text(column: String): String = getColumnIndex(column).let { if (it < 0 || isNull(it)) "" else getString(it).orEmpty() }
    private fun Cursor.number(column: String): Long = getColumnIndex(column).let { if (it < 0 || isNull(it)) 0L else getLong(it) }
    private fun Cursor.has(column: String): Boolean = getColumnIndex(column).let { it >= 0 && !isNull(it) }

    private fun calendars(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        context.contentResolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME), null, null, Calendars._ID)
            ?.use { c -> while (c.moveToNext()) out += c.text(Calendars._ID) to c.text(Calendars.CALENDAR_DISPLAY_NAME) }
            ?: throw IllegalStateException("no answer")
        return out
    }

    /** The shortest reminder of each event in Android's list: webOS holds one for an event. */
    private fun reminders(): Map<String, Int> {
        val out = HashMap<String, Int>()
        context.contentResolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.EVENT_ID, Reminders.MINUTES, Reminders.METHOD), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val method = c.number(Reminders.METHOD).toInt()
                if (method != Reminders.METHOD_ALERT && method != Reminders.METHOD_DEFAULT) continue
                val minutes = c.number(Reminders.MINUTES).toInt().let { if (it == Reminders.MINUTES_DEFAULT) DEFAULT_REMINDER else it }
                val id = c.text(Reminders.EVENT_ID)
                if (minutes < (out[id] ?: Int.MAX_VALUE)) out[id] = minutes
            }
        }
        return out
    }

    /** One event row: what's needed after every row has been seen. */
    private class Row(val id: String, val calendar: String, val json: JSONObject, val start: Long, val rule: String, val unsupported: Boolean, val allDay: Boolean)

    private fun read(calendars: List<String>): List<JSONObject>? {
        if (calendars.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val from = now - WINDOW_BACK_MS; val to = now + WINDOW_AHEAD_MS
        val alarms = if (remindersOn()) reminders() else emptyMap()
        val list = calendars.joinToString(",") { it.toLong().toString() }
        val where = "${Events.DELETED}=0 AND ${Events.CALENDAR_ID} IN ($list) AND " +
            "((${Events.RRULE} IS NOT NULL OR ${Events.RDATE} IS NOT NULL) AND (${Events.LAST_DATE} IS NULL OR ${Events.LAST_DATE}>=$from) OR " +
            "${Events.ORIGINAL_ID} IS NOT NULL OR (${Events.DTSTART}>=$from AND ${Events.DTSTART}<$to))"
        val parents = LinkedHashMap<String, Row>()
        val exceptions = ArrayList<Exception_>()
        (context.contentResolver.query(Events.CONTENT_URI, PROJECTION, where, null, Events._ID) ?: return null).use { c ->
            while (c.moveToNext()) {
                if (c.has(Events.ORIGINAL_ID)) { exceptions += exception(c, alarms); continue }
                row(c, alarms)?.let { parents[it.id] = it }
            }
        }
        val out = ArrayList<JSONObject>()
        val exdates = HashMap<String, MutableList<String>>()
        for (x in exceptions) {
            val parent = parents[x.parent]
            if (parent == null || parent.unsupported) { if (!x.cancelled) x.json?.let { out += it }; continue }
            exdates.getOrPut(x.parent) { ArrayList() } += if (parent.allDay) utcDate(x.instant) else zoneDate(x.instant, parent.json.getString("tzId"))
            x.json?.takeIf { !x.cancelled }?.let {
                out += it.put("parentId", "android-ev-${x.parent}").put("parentDtstart", parent.start)
                    .put("recurrenceId", utcStamp(x.instant))
            }
        }
        for (p in parents.values) {
            if (p.unsupported) { out += expand(p, from, to); continue }
            val own = androidExdates(p.json.optString("androidExdate"), p.allDay, p.json.getString("tzId"))
            val spilled = p.json.getJSONArray("spilledExdates").let { a -> List(a.length()) { a.getString(it) } }
            p.json.remove("androidExdate"); p.json.remove("spilledExdates")
            if (p.rule.isNotEmpty()) p.json.put("exdates", JSONArray((own + exdates[p.id].orEmpty() + spilled).distinct()))
            out += p.json
        }
        return out
    }

    private class Exception_(val parent: String, val instant: Long, val cancelled: Boolean, val json: JSONObject?)

    private fun exception(c: Cursor, alarms: Map<String, Int>) = Exception_(c.text(Events.ORIGINAL_ID), c.number(Events.ORIGINAL_INSTANCE_TIME),
        c.number(Events.STATUS).toInt() == Events.STATUS_CANCELED, row(c, alarms)?.json?.apply { remove("androidExdate") })

    private fun row(c: Cursor, alarms: Map<String, Int>): Row? {
        val id = c.text(Events._ID)
        val allDay = c.number(Events.ALL_DAY) != 0L
        val start = c.number(Events.DTSTART)
        val end = if (c.has(Events.DTEND)) c.number(Events.DTEND) else start + duration(c.text(Events.DURATION))
        val rule = c.text(Events.RRULE)
        val parsed = if (rule.isEmpty()) null else parseRule(rule, allDay)
        val unsupported = (rule.isNotEmpty() && parsed == null) || c.text(Events.RDATE).isNotEmpty() || c.text(Events.EXRULE).isNotEmpty()
        val zone = c.text(Events.EVENT_TIMEZONE).takeIf { !allDay && it != "UTC" && it in ZONES } ?: TimeZone.getDefault().id
        val (from, until) = if (allDay) allDayStart(start) to allDayEnd(start, end) else start to maxOf(end, start)
        // Android has worked out when a repeat ends, so Calendar gets that and not COUNT, which it
        // lets exceptions use up (measured: a repeat with two lost its last occurrence).
        val last = c.number(Events.LAST_DATE)
        var spilled = emptyList<String>()
        if (parsed != null && last > 0 && (parsed.has("count") || parsed.has("until"))) {
            parsed.remove("count")
            val lastStart = lastOccurrence(last - (end - start), start, zone, allDay)
            val until = endOfRepeat(lastStart, end - start, zone, allDay)
            parsed.put("until", until)
            spilled = daysPast(lastStart, until, zone, allDay)
        }
        val json = JSONObject().put("_id", "android-ev-$id").put("remoteId", "ev-$id").put("calendarId", "android-cal-${c.text(Events.CALENDAR_ID)}")
            .put("subject", c.text(Events.TITLE)).put("location", c.text(Events.EVENT_LOCATION)).put("note", c.text(Events.DESCRIPTION))
            .put("dtstart", from).put("dtend", until).put("tzId", zone).put("allDay", allDay)
            .put("transp", if (c.number(Events.AVAILABILITY).toInt() == Events.AVAILABILITY_FREE) "TRANSPARENT" else "OPAQUE")
            .put("rrule", parsed ?: JSONObject.NULL).put("exdates", JSONArray())
            .put("alarm", alarm(alarms[id], allDay)).put("androidExdate", c.text(Events.EXDATE))
            .put("spilledExdates", JSONArray(spilled))
        return Row(id, c.text(Events.CALENDAR_ID), json, from, rule, unsupported, allDay)
    }

    /** A repeat webOS can't express: each occurrence in the window, from Android's own expansion. */
    private fun expand(p: Row, from: Long, to: Long): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val uri = Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        val length = p.json.getLong("dtend") - p.json.getLong("dtstart")
        context.contentResolver.query(uri, arrayOf(Instances.EVENT_ID, Instances.BEGIN, Instances.END), "${Instances.EVENT_ID}=${p.id.toLong()}", null, Instances.BEGIN)?.use { c ->
            while (c.moveToNext()) {
                val begin = c.number(Instances.BEGIN)
                val start = if (p.allDay) allDayStart(begin) else begin
                out += JSONObject(p.json.toString()).put("_id", "android-ev-${p.id}-$begin").put("remoteId", "ev-${p.id}#$begin")
                    .put("dtstart", start).put("dtend", start + length).put("rrule", JSONObject.NULL).put("exdates", JSONArray())
                    .apply { remove("androidExdate"); remove("spilledExdates") }
            }
        }
        return out
    }

    /**
     * webOS's alarm. Its parser takes up to three digits for a unit, so 1440 minutes would be read
     * wrongly and go off at the start; and it keeps only the last of weeks and days, so these are
     * days, hours and minutes. An all-day event sits at noon here and its reminder counts from
     * midnight on Android, hence the 12 hours; on such an event a negative number is a time on
     * the day, 540 minutes after midnight for 9:00.
     */
    private fun alarm(minutes: Int?, allDay: Boolean): JSONArray {
        val value = if (minutes == null) "none" else trigger(maxOf(0, if (allDay) minutes + 720 else minutes))
        return JSONArray().put(JSONObject().put("action", "display").put("alarmTrigger", JSONObject().put("value", value).put("valueType", "DURATION")))
    }

    private fun trigger(total: Int): String {
        val days = total / 1440; val hours = total % 1440 / 60; val minutes = total % 60
        val time = (if (hours > 0) "${hours}H" else "") + (if (minutes > 0) "${minutes}M" else "")
        return when {
            days == 0 && time.isEmpty() -> "-PT0M"
            days == 0 -> "-PT$time"
            time.isEmpty() -> "-P${days}D"
            else -> "-P${days}DT$time"
        }
    }

    /** Android's all-day events are UTC midnights; webOS's importer keeps the day at local noon, which zone changes can't shift. */
    private fun allDayStart(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply { clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0) }.timeInMillis
    }

    /** One second past noon on the last day: Android's end is the next midnight. */
    private fun allDayEnd(utcStart: Long, utcEnd: Long): Long = maxOf(allDayStart(utcStart), allDayStart(maxOf(utcEnd - DAY_MS, utcStart))) + 1000

    /**
     * The start of a repeat's last occurrence, from LAST_DATE less the length. That is the
     * occurrence itself for COUNT, but only the rule's UNTIL otherwise (measured), which can fall
     * before that day's start time, when the day before was the last. A later date than the true
     * last is harmless, as no occurrence falls on it.
     */
    private fun lastOccurrence(end: Long, start: Long, zone: String, allDay: Boolean): Long {
        if (allDay) return end
        val first = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = start }
        val at = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
            timeInMillis = end
            set(Calendar.HOUR_OF_DAY, first.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, first.get(Calendar.MINUTE))
            set(Calendar.SECOND, first.get(Calendar.SECOND)); set(Calendar.MILLISECOND, 0)
        }
        if (at.timeInMillis > end) at.add(Calendar.DAY_OF_MONTH, -1)
        return at.timeInMillis
    }

    /**
     * `until` as Calendar compares it. It walks the event's dates as midnights of the device's
     * clock, and tests each occurrence's real start too, so until has to reach both the end of the
     * last day in the event's zone, on the device's clock, and the last occurrence's real end,
     * which is later when the zone is behind the device. An all-day event's date is its UTC date.
     */
    private fun endOfRepeat(lastStart: Long, length: Long, zone: String, allDay: Boolean): Long {
        if (allDay) return allDayStart(lastStart) + DAY_MS / 2 - 1000
        return maxOf(endOfDay(lastStart, zone), lastStart + length)
    }

    private fun endOfDay(instant: Long, zone: String): Long {
        val at = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = instant }
        return Calendar.getInstance().apply {
            clear(); set(at.get(Calendar.YEAR), at.get(Calendar.MONTH), at.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        }.timeInMillis
    }

    /** The dates after the last that `until` reaches into, as exceptions: they would gain an occurrence. */
    private fun daysPast(lastStart: Long, until: Long, zone: String, allDay: Boolean): List<String> {
        if (allDay) return emptyList()
        val at = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = lastStart }
        val next = Calendar.getInstance().apply { clear(); set(at.get(Calendar.YEAR), at.get(Calendar.MONTH), at.get(Calendar.DAY_OF_MONTH) + 1) }
        val out = ArrayList<String>()
        while (next.timeInMillis <= until) {
            out += "%04d%02d%02d".format(next.get(Calendar.YEAR), next.get(Calendar.MONTH) + 1, next.get(Calendar.DAY_OF_MONTH))
            next.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    private fun zoneDate(instant: Long, zone: String): String = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = instant }.let {
        "%04d%02d%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

    private fun utcStamp(instant: Long): String = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = instant }.let {
        "%04d%02d%02dT%02d%02d%02dZ".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH),
            it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE), it.get(Calendar.SECOND))
    }

    /** Android's EXDATE: comma-separated stamps, with an optional "zone;" before them. */
    private fun androidExdates(text: String, allDay: Boolean, eventZone: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val zone = text.substringBefore(';', "").takeIf { it.isNotEmpty() }?.let(TimeZone::getTimeZone)
        return text.substringAfter(';').split(',').mapNotNull { stamp(it.trim(), zone) }.map { if (allDay) utcDate(it) else zoneDate(it, eventZone) }
    }

    private fun utcDate(instant: Long): String = utcStamp(instant).substring(0, 8)

    /** yyyyMMdd or yyyyMMddTHHmmss, with Z for UTC; a date alone is UTC, as Android's all-day events are. */
    private fun stamp(s: String, zone: TimeZone?): Long? {
        val m = STAMP.matchEntire(s) ?: return null
        val (y, mo, d, h, mi, se, z) = m.destructured
        val tz = if (z == "Z" || h.isEmpty()) TimeZone.getTimeZone("UTC") else zone ?: TimeZone.getDefault()
        return Calendar.getInstance(tz).apply {
            clear(); set(y.toInt(), mo.toInt() - 1, d.toInt(), h.ifEmpty { "0" }.toInt(), mi.ifEmpty { "0" }.toInt(), se.ifEmpty { "0" }.toInt())
        }.timeInMillis
    }

    /** RFC 5545's DURATION in ms: Android's repeats carry this instead of an end. */
    private fun duration(text: String): Long {
        val m = DURATION.matchEntire(text) ?: return HOUR_MS
        val (w, d, h, mi, s) = m.destructured
        val ms = (w.ifEmpty { "0" }.toLong() * 7 * DAY_MS) + d.ifEmpty { "0" }.toLong() * DAY_MS + h.ifEmpty { "0" }.toLong() * HOUR_MS +
            mi.ifEmpty { "0" }.toLong() * 60_000 + s.ifEmpty { "0" }.toLong() * 1000
        return if (ms > 0) ms else HOUR_MS
    }

    /** An RFC 5545 RRULE as Calendar's object, or null if webOS can't hold it. */
    private fun parseRule(text: String, allDay: Boolean): JSONObject? {
        val parts = text.split(';').filter { it.contains('=') }.associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
        val freq = parts["FREQ"]?.uppercase()?.takeIf { it in FREQUENCIES } ?: return null
        val rule = JSONObject().put("freq", freq).put("interval", parts["INTERVAL"]?.toIntOrNull() ?: 1)
        parts["COUNT"]?.toIntOrNull()?.let { rule.put("count", it) }
        parts["UNTIL"]?.let { u ->
            val at = stamp(u, TimeZone.getDefault()) ?: return null
            rule.put("until", if (allDay) allDayStart(at) + DAY_MS / 2 else at)
        }
        rule.put("wkst", DAYS.indexOf(parts["WKST"]?.uppercase() ?: "SU").coerceAtLeast(0))
        val rules = JSONArray()
        for ((name, value) in parts) {
            when (name) {
                "FREQ", "INTERVAL", "COUNT", "UNTIL", "WKST", "BYSETPOS" -> {}
                "BYDAY" -> {
                    val setPosition = parts["BYSETPOS"]
                    val days = value.split(',').map { day ->
                        val m = BYDAY.matchEntire(day.trim().uppercase()) ?: return null
                        val entry = JSONObject().put("day", DAYS.indexOf(m.groupValues[2]))
                        m.groupValues[1].takeIf { it.isNotEmpty() }?.let { entry.put("ord", it.toInt()) }
                        entry
                    }
                    if (setPosition != null) {
                        if (days.size != 1 || setPosition.contains(',')) return null
                        days[0].put("ord", setPosition.toIntOrNull() ?: return null)
                    }
                    rules.put(JSONObject().put("ruleType", "BYDAY").put("ruleValue", JSONArray(days)))
                }
                "BYMONTHDAY", "BYMONTH", "BYWEEKNO", "BYYEARDAY" -> {
                    val list = value.split(',').map { JSONObject().put("ord", it.trim().toIntOrNull() ?: return null) }
                    rules.put(JSONObject().put("ruleType", name).put("ruleValue", JSONArray(list)))
                }
                else -> return null
            }
        }
        if (parts.containsKey("BYSETPOS") && !parts.containsKey("BYDAY")) return null
        if (rules.length() > 0) rule.put("rules", rules)
        return rule
    }

    /** Extends first, as db8's permissions fall back to it; Calendar and the reminder service read through the base kind. */
    private fun calendarKind() = JSONObject().put("id", CALENDAR_KIND).put("owner", AndroidContacts.SERVICE).put("extends", JSONArray().put("com.palm.calendar:1"))
        .put("indexes", JSONArray().put(index("accountId")))

    private fun eventKind() = JSONObject().put("id", EVENT_KIND).put("owner", AndroidContacts.SERVICE).put("extends", JSONArray().put("com.palm.calendarevent:1"))
        .put("indexes", JSONArray().put(index("accountId")).put(index("calendarId")))

    private fun index(prop: String) = JSONObject().put("name", prop).put("props", JSONArray().put(JSONObject().put("name", prop)))

    companion object {
        const val CALENDAR_KIND = "org.webosarchive.calendar.android:1"
        const val EVENT_KIND = "org.webosarchive.calendarevent.android:1"
        private val PAGES = setOf("com.palm.app.calendar", "com.palm.app.contacts", "com.palm.app.accounts")
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 86_400_000L
        private const val WINDOW_BACK_MS = 366 * DAY_MS
        private const val WINDOW_AHEAD_MS = 731 * DAY_MS
        private const val DEFAULT_REMINDER = 10
        private const val CHANGE_DELAY_MS = 3000L
        private const val START_DELAY_MS = 6000L
        private const val REMOVAL_DELAY_MS = 8000L
        private val REMOVAL = Any()
        private val ZONES by lazy { TimeZone.getAvailableIDs().toHashSet() }
        private val FREQUENCIES = setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
        private val DAYS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
        private val BYDAY = Regex("""([+-]?\d{1,2})?(SU|MO|TU|WE|TH|FR|SA)""")
        private val STAMP = Regex("""(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2}))?(Z?)""")
        private val DURATION = Regex("""[+-]?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")
        private val PROJECTION = arrayOf(Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.DTSTART, Events.DTEND,
            Events.DURATION, Events.ALL_DAY, Events.EVENT_TIMEZONE, Events.RRULE, Events.RDATE, Events.EXDATE, Events.EXRULE, Events.ORIGINAL_ID,
            Events.ORIGINAL_INSTANCE_TIME, Events.STATUS, Events.AVAILABILITY, Events.LAST_DATE)
    }
}
