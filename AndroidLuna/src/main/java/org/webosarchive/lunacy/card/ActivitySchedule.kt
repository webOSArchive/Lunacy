package org.webosarchive.lunacy.card

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** An activity spec the activity manager refuses, with the text the TouchPad answers. */
internal class SpecError(text: String) : Exception(text)

/**
 * An activity's schedule, as webOS's activity manager read and ran it: a start, an end, an
 * interval smart (on its fixed ticks) or precise, relative or not, skipping missed runs or not.
 */
internal class Schedule(val spec: JSONObject) {
    var start: Long? = null; var end: Long? = null; var interval: Long? = null
    var precise = false; var relative = false; var skip = false; var local = false
    var lastFinished: Long? = null
    var nextStart: Long? = null
    /** When it was last armed (wall clock). */
    var setAt = 0L
    var due = false

    fun past() = end?.let { e -> (nextStart ?: System.currentTimeMillis()) >= e } ?: false

    /** Works out the next start and clears due, as each queueing did. */
    fun queue() {
        due = false
        setAt = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        val iv = interval
        if (iv == null) { nextStart = start; return }
        // A precise interval with no start ticks from DAY_ONE (2 January 1970), in local time
        // since nothing named UTC, as MojoJsonConverter and PreciseIntervalSchedule had it
        // (RelativeIntervalSchedule too, until it has finished once): the first run is the
        // next tick, never at once. Taken from now, an activity that recreates its own
        // schedule from its callback (the BlueBubbles connector's 2m periodic sync) ran
        // again a few milliseconds later, over and over.
        val dayOne = DAY_ONE - TimeZone.getDefault().getOffset(now)
        val base = when {
            relative -> lastFinished ?: start ?: dayOne
            precise -> start ?: dayOne
            else -> SMART_BASE
        }
        var next = if (now <= base) base else base + ((now - base) / iv + 1) * iv
        val missedFrom = lastFinished ?: if (spec.has("start")) start else null
        if (!skip && missedFrom != null && next - missedFrom > iv) next = now
        if (end != null && end!! - next <= 0) { nextStart = null; return }
        nextStart = next
    }

    fun toJson(current: Boolean): JSONObject {
        val o = JSONObject()
        if (precise) o.put("precise", true)
        if (relative) o.put("relative", true)
        if (current) nextStart?.let { o.put("nextStart", format(it, local)) }
        end?.let { o.put("end", format(it, local)) }
        if (skip) o.put("skip", true)
        lastFinished?.let { o.put("lastFinished", format(it, local)) }
        spec.optString("interval").takeIf { it.isNotEmpty() }?.let { o.put("interval", it) }
        if (current) o.put("scheduled", due)
        if (local) o.put("local", true)
        start?.let { o.put("start", format(it, local)) }
        return o
    }

    companion object {
        /** webOS's Schedule::DAY_ONE: a schedule's start when it names none. */
        const val DAY_ONE = 86_400_000L
        /** Smart intervals tick on a base drawn once, between 23:00 and 05:00 after the epoch. */
        val SMART_BASE = (SMART_EARLIEST_S + (Math.random() * SMART_SPREAD_S).toLong()) * 1000L
        private const val SMART_EARLIEST_S = 23 * 3600L
        private const val SMART_SPREAD_S = 6 * 3600
        private val SMART = setOf(5, 10, 15, 20, 30, 60, 180, 360, 720).map { it * 60_000L }.toSet()

        fun parse(s: JSONObject): Schedule {
            val sc = Schedule(s)
            sc.precise = s.optBoolean("precise"); sc.relative = s.optBoolean("relative"); sc.skip = s.optBoolean("skip")
            val startText = s.opt("start") as? String; val endText = s.opt("end") as? String
            val utc = listOfNotNull(startText, endText).map { it.endsWith("Z") }.distinct()
            if (utc.size > 1) throw SpecError("Start and end time must both be specified in UTC or local time")
            sc.local = s.optBoolean("local") || utc.singleOrNull() == false
            sc.start = startText?.let { time(it, sc.local) }
            sc.end = endText?.let { time(it, sc.local) }
            val iv = s.opt("interval") as? String
            if (iv != null) {
                val ms = duration(iv)
                if (!sc.precise) {
                    if (ms % 60_000L != 0L) throw SpecError("Only durations of even minutes may be specified")
                    if (ms !in SMART && ms % 86_400_000L != 0L) throw SpecError("Interval must be a number of days<n>d, or one of: 12h, 6h, 3h, 1h, 20m, 30m, 15m, 10m or 5m")
                    if (sc.start != null || sc.end != null) throw SpecError("Unless precise time is specified, time intervals may not specify a start or end time")
                    if (sc.relative) throw SpecError("Relative schedules must be precise")
                }
                sc.interval = ms
            } else if (sc.start == null) throw SpecError("Non Interval Schedules must specify a start time")
            (s.opt("lastFinished") as? String)?.let { lf ->
                val t = time(lf, sc.local)
                if (t < System.currentTimeMillis() && (sc.start == null || t > sc.start!!)) sc.lastFinished = t
            }
            return sc
        }

        private fun fmt(local: Boolean) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = if (local) TimeZone.getDefault() else TimeZone.getTimeZone("UTC") }
        fun format(t: Long, local: Boolean) = fmt(local).format(t) + if (local) "" else "Z"
        fun time(s: String, local: Boolean): Long {
            val z = s.endsWith("Z")
            if (!z && s.isNotEmpty() && !s.last().isDigit()) throw SpecError("Start time must end in 'Z' for UTC, or nothing")
            return runCatching { fmt(!z && local).parse(s.removeSuffix("Z"))!!.time }.getOrElse { throw SpecError("Failed to parse start time") }
        }
        fun duration(s: String): Long {
            val m = Regex("^(?:(\\d+)D)?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$", RegexOption.IGNORE_CASE).find(s.trim())
                ?: throw SpecError("Failed to parse scheduling interval")
            val (d, h, mi, se) = m.destructured
            val ms = ((d.toLongOrNull() ?: 0) * 86400 + (h.toLongOrNull() ?: 0) * 3600 + (mi.toLongOrNull() ?: 0) * 60 + (se.toLongOrNull() ?: 0)) * 1000
            if (ms == 0L) throw SpecError("Duration must be non-zero")
            return ms
        }
    }
}
