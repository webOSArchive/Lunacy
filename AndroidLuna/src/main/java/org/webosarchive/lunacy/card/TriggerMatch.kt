package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject

/**
 * Whether an activity's trigger fires on a reply, as webOS's activity manager matched them: a
 * `key` present, a `compare` value changed, a `where` clause (MojoDB's ops and value order), or,
 * for a plain trigger, any reply after the first. An error reply fires it, with the error.
 */
object TriggerMatch {
    fun matches(t: JSONObject, r: JSONObject, first: Boolean): Boolean {
        val isBasic = !t.has("key") && !t.has("where") && !t.has("compare")
        return when {
            r.has("returnValue") && !r.optBoolean("returnValue") -> true   // an error fires it, with the error
            t.has("key") -> r.has(t.getString("key"))
            t.has("compare") -> t.getJSONObject("compare").let { c -> r.has(c.getString("key")) && !same(r.get(c.getString("key")), c.get("value")) }
            t.has("where") -> where(r, t.get("where"))
            isBasic -> !first
            else -> false
        }
    }

    fun same(x: Any?, y: Any?) = x == y || (x is Number && y is Number && x.javaClass == y.javaClass && x.toDouble() == y.toDouble())

    fun where(o: JSONObject, w: Any): Boolean = when (w) {
        is JSONArray -> (0 until w.length()).all { where(o, w.get(it)) }
        is JSONObject -> when {
            w.has("and") -> where(o, w.get("and"))
            w.has("or") -> w.get("or").let { or -> if (or is JSONArray) (0 until or.length()).any { where(o, or.get(it)) } else where(o, or) }
            else -> {
                val v = path(o, w.opt("prop")) ?: NONE
                if (v === NONE) false else if (w.optString("op") == "where") true else compareOp(v, w.optString("op"), w.opt("val"))
            }
        }
        else -> false
    }

    private val NONE = Any()

    private fun path(o: JSONObject, prop: Any?): Any? {
        val parts = when (prop) { is String -> prop.split('.'); is JSONArray -> (0 until prop.length()).map { prop.getString(it) }; else -> return null }
        var cur: Any? = o
        for (p in parts) cur = (cur as? JSONObject)?.opt(p) ?: return null
        return cur
    }

    /** MojObject's order: different types order by type, so int 1 and decimal 1.0 differ. */
    private fun compareOp(a: Any?, op: String, b: Any?): Boolean {
        fun rank(v: Any?) = when (v) { null, JSONObject.NULL -> 1; is JSONObject -> 2; is JSONArray -> 3; is String -> 4; is Boolean -> 5; is Double, is Float -> 6; is Number -> 7; else -> 0 }
        val c = if (rank(a) != rank(b)) rank(a).compareTo(rank(b)) else when (a) {
            is String -> a.compareTo(b as String)
            is Boolean -> a.compareTo(b as Boolean)
            is Number -> a.toDouble().compareTo((b as Number).toDouble())
            else -> if (a.toString() == b.toString()) 0 else 1
        }
        return when (op) { "<" -> c < 0; "<=" -> c <= 0; "=" -> c == 0; "!=" -> c != 0; ">=" -> c >= 0; ">" -> c > 0; else -> false }
    }
}
