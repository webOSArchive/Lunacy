package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject

/**
 * The db8 registrations Lunacy makes for kinds it answers itself - filenotifyd's media and
 * document kinds, the Android account's contacts and calendars - built the way their
 * services' own `putKind` and `putPermissions` calls wrote them.
 */
object KindSpec {
    /** An index property: its name, and the collation and tokenizing a search index gives it. */
    fun prop(name: String, collate: String? = null, tokenize: String? = null): JSONObject =
        JSONObject().put("name", name).apply { collate?.let { put("collate", it) }; tokenize?.let { put("tokenize", it) } }

    fun index(name: String, vararg props: JSONObject): JSONObject = JSONObject().put("name", name).put("props", JSONArray(props.toList()))

    /** An index on plain properties in order, named as filenotifyd named such indexes: joined with "_". */
    fun plain(vararg names: String): JSONObject = index(names.joinToString("_"), *names.map { prop(it) }.toTypedArray())

    /** A kind's indexes from a compact table: each a ready index, or a list of plain property names ([plain]). */
    fun indexes(table: List<Any>): JSONArray = JSONArray(table.map { i ->
        if (i is JSONObject) i else plain(*(i as List<*>).map { it.toString() }.toTypedArray())
    })

    fun kind(id: String, owner: String, indexes: JSONArray, extends: String? = null): JSONObject =
        JSONObject().put("id", id).put("owner", owner).put("indexes", indexes)
            .apply { extends?.let { put("extends", JSONArray().put(it)) } }

    /** One `db.kind` permission per caller, each allowing [operations] ("read", "update"…). */
    fun permissions(kind: String, callers: List<String>, vararg operations: String): JSONArray = JSONArray(callers.map { app ->
        JSONObject().put("type", "db.kind").put("object", kind).put("caller", app)
            .put("operations", JSONObject().apply { operations.forEach { put(it, "allow") } })
    })
}
