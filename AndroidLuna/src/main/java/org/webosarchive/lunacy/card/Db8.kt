package org.webosarchive.lunacy.card

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Collator
import java.util.concurrent.Executors

/**
 * db8: palm://com.palm.db, and palm://com.palm.tempdb (the same API, in memory), on SQLite.
 * Replies, errors, ids, revisions, paging and watches follow the reference TouchPad's
 * (webOS CE 3.1.0; Workbench/probe/db8probe.sh and db8watch.sh, Docs/db8.md). Kinds hold objects;
 * a query must be served by one of its kind's indexes, as on webOS. Work runs on one thread,
 * in order; objects of a kind are cached in memory once read.
 */
class Db8(private val service: String, file: File?) {
    private val db: SQLiteDatabase = if (file != null) SQLiteDatabase.openOrCreateDatabase(file, null) else SQLiteDatabase.create(null)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val kinds = LinkedHashMap<String, Kind>()
    private val cache = HashMap<String, LinkedHashMap<String, JSONObject>>()  // kind -> id -> object
    private val watches = ArrayList<Watch>()
    private var rev = 0L
    private var idCounter = 0L

    private class IndexProp(val name: String, val collate: String?, val default: Any? = null)
    private class Index(val name: String, val props: List<IndexProp>)
    /** A revision set: [name] holds the _rev at which any of [props] last changed. */
    private class RevSet(val name: String, val props: List<String>)
    private class Kind(val id: String, val owner: String, val extends: List<String>, val indexes: List<Index>, val revSets: List<RevSet>, val spec: JSONObject)
    private class Clause(val prop: String, val op: String, val value: Any?)
    private class Watch(val call: Bus.Call, val kinds: Set<String>, val where: List<Clause>)
    /** A reply with its db8 error, thrown from deep inside an operation. */
    private class DbError(val code: Int, val text: String) : Exception(text)

    init {
        db.execSQL("CREATE TABLE IF NOT EXISTS kinds (id TEXT PRIMARY KEY, spec TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS objects (id TEXT PRIMARY KEY, kind TEXT, json TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS objects_kind ON objects (kind)")
        db.execSQL("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS permissions (kind TEXT, caller TEXT, ops TEXT, PRIMARY KEY (kind, caller))")
        runCatching { db.execSQL("ALTER TABLE permissions ADD COLUMN ops TEXT") }  // databases from before ops
        db.rawQuery("SELECT spec FROM kinds", null).use { c -> while (c.moveToNext()) parseKind(JSONObject(c.getString(0))).let { kinds[it.id] = it } }
        rev = meta("rev"); idCounter = meta("ids")
    }

    /** Registers a package's configuration/db files, as webOS's configurator did at install. */
    fun configure(kindFiles: List<JSONObject>, permissionFiles: List<JSONArray>) = worker.execute {
        for (k in kindFiles) try { putKind(CONFIGURATOR, k) } catch (e: Exception) { Log.w(AppServer.TAG, "$service: kind ${k.optString("id")}: ${e.message}") }
        for (p in permissionFiles) try { putPermissions(CONFIGURATOR, JSONObject().put("permissions", p)) } catch (e: Exception) { Log.w(AppServer.TAG, "$service: permissions: ${e.message}") }
    }

    fun register(bus: Bus) {
        for (m in listOf("putKind", "delKind", "put", "get", "merge", "del", "find", "search", "watch", "reserveIds", "batch", "putPermissions")) {
            bus.register(service, m, Bus.CallHandler { call -> worker.execute { handle(m, call) } })
        }
    }

    private fun handle(method: String, call: Bus.Call) {
        val reply = try {
            exec(method, call.appId, call.params, call)
        } catch (e: DbError) {
            error(e.code, e.text)
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "$service/$method failed", e)
            error(-1, "db: internal error: ${e.message}")
        }
        main.post { call.reply(reply) }
    }

    /** One operation. call is null inside a batch (no watches there). */
    private fun exec(method: String, caller: String, p: JSONObject, call: Bus.Call?): String = when (method) {
        "putKind" -> putKind(caller, p)
        "delKind" -> delKind(caller, p)
        "put" -> put(caller, p)
        "get" -> get(caller, p)
        "merge" -> merge(caller, p)
        "del" -> del(caller, p)
        "find" -> find(caller, p, call, search = false)
        "search" -> find(caller, p, call, search = true)
        "watch" -> watch(caller, p, call)
        "reserveIds" -> JSONObject().put("returnValue", true).put("ids", JSONArray((1..p.optInt("count", 1).coerceIn(1, 1000)).map { newId() })).toString()
        "batch" -> batch(caller, p)
        "putPermissions" -> putPermissions(caller, p)
        else -> error(-1, "Unknown method \"$method\" for category \"/\"")
    }

    // ---- kinds ----

    private fun putKind(caller: String, p: JSONObject): String {
        val id = required(caller, p, "id") as String
        val owner = p.optString("owner", caller)
        val old = kinds[id]
        if (caller != CONFIGURATOR && (!related(caller, owner) || (old != null && !related(caller, old.owner)))) throw DbError(-3963, "db: permission denied")
        val k = parseKind(p)
        db.insertWithOnConflict("kinds", null, ContentValues().apply { put("id", id); put("spec", p.toString()) }, SQLiteDatabase.CONFLICT_REPLACE)
        kinds[id] = k
        return ok()
    }

    private fun delKind(caller: String, p: JSONObject): String {
        val id = required(caller, p, "id") as String
        val k = kind(id)
        if (!related(caller, k.owner)) throw DbError(-3963, "db: permission denied")
        db.delete("objects", "kind = ?", arrayOf(id))
        db.delete("kinds", "id = ?", arrayOf(id))
        kinds.remove(id); cache.remove(id)
        return ok()
    }

    private fun parseKind(p: JSONObject): Kind {
        // Every kind has db8's own _id index (MojDbKind::IdIndexJson), which includes deleted
        // objects: Email's facade finds an email by "_id" = its id, with a watch.
        val indexes = arrayListOf(Index("_id", listOf(IndexProp("_del", null), IndexProp("_id", null))))
        p.optJSONArray("indexes")?.let { a ->
            for (i in 0 until a.length()) {
                val ix = a.getJSONObject(i)
                val props = ArrayList<IndexProp>()
                // An index that includes deleted objects leads with _del, as db8's did.
                if (ix.optBoolean("incDel")) props += IndexProp("_del", null)
                ix.optJSONArray("props")?.let { pa -> for (j in 0 until pa.length()) pa.getJSONObject(j).let { props += IndexProp(it.getString("name"), it.optString("collate").ifEmpty { null }, it.opt("default")) } }
                indexes += Index(ix.optString("name"), props)
            }
        }
        val ext = ArrayList<String>()
        p.optJSONArray("extends")?.let { a -> for (i in 0 until a.length()) ext += a.getString(i) }
        val revSets = ArrayList<RevSet>()
        p.optJSONArray("revSets")?.let { a ->
            for (i in 0 until a.length()) {
                val rs = a.getJSONObject(i)
                val props = rs.optJSONArray("props")?.let { pa -> (0 until pa.length()).map { pa.getJSONObject(it).getString("name") } }.orEmpty()
                revSets += RevSet(rs.getString("name"), props)
            }
        }
        return Kind(p.getString("id"), p.optString("owner"), ext, indexes, revSets, p)
    }

    private fun kind(id: String) = kinds[id] ?: throw DbError(-3970, "kind not registered: '$id'")

    /** The kind and every kind that extends it, directly or not. */
    private fun family(id: String): Set<String> {
        val out = linkedSetOf(id)
        var grew = true
        while (grew) { grew = false; for (k in kinds.values) if (k.id !in out && k.extends.any { it in out }) { out += k.id; grew = true } }
        return out
    }

    /**
     * Who may use a kind: its owner, callers it has granted (putPermissions), and callers in the
     * same package by name (an app and its service, e.g. com.foo.app and com.foo.app.service).
     * The measured case: an unrelated caller is denied with -3963.
     */
    private fun related(caller: String, owner: String) =
        caller == owner || caller.startsWith("$owner.") || owner.startsWith("$caller.")

    /**
     * op is create, read, update or delete. Grants come from putPermissions and packages'
     * configuration/db/permissions files; a caller ending in "*" matches by prefix
     * ("com.palm.service.calendar.*").
     */
    private fun allowed(caller: String, k: Kind, op: String): Boolean {
        if (related(caller, k.owner)) return true
        return permission(caller, k, op, HashSet()) == "allow"
    }

    /**
     * db8's permission engine (MojDbPermissionEngine::check, MojDbKind::objectPermission): the
     * caller's own entry for the kind, else the first wildcard entry that matches it, gives the
     * operation's value; a kind with no value for it takes its first super kind's. So the SMTP
     * service, granted on com.palm.mail.account, may update a com.palm.imap.account, which
     * extends it and grants nothing itself.
     */
    private fun permission(caller: String, k: Kind, op: String, seen: HashSet<String>): String? {
        if (!seen.add(k.id)) return null
        var exact: String? = null; var wild: String? = null
        db.rawQuery("SELECT caller, ops FROM permissions WHERE kind = ?", arrayOf(k.id)).use { c ->
            while (c.moveToNext()) {
                val who = c.getString(0)
                if (who == caller) exact = c.getString(1) ?: "{}"
                else if (wild == null && who.endsWith("*") && caller.startsWith(who.dropLast(1))) wild = c.getString(1) ?: "{}"
            }
        }
        val value = (exact ?: wild)?.let { JSONObject(it).optString(op).ifEmpty { null } }
        return value ?: k.extends.firstOrNull()?.let { kinds[it] }?.let { permission(caller, it, op, seen) }
    }

    private fun checkAccess(caller: String, kindId: String, op: String = "read") {
        if (!allowed(caller, kind(kindId), op)) throw DbError(-3963, "db: permission denied")
    }

    private fun putPermissions(caller: String, p: JSONObject): String {
        val perms = required(caller, p, "permissions") as JSONArray
        for (i in 0 until perms.length()) {
            val pm = perms.getJSONObject(i)
            val k = kind(pm.optString("object"))
            if (caller != CONFIGURATOR && !related(caller, k.owner)) throw DbError(-3963, "db: permission denied")
            db.insertWithOnConflict("permissions", null, ContentValues().apply {
                put("kind", k.id); put("caller", pm.optString("caller")); put("ops", (pm.optJSONObject("operations") ?: JSONObject()).toString())
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        return ok()
    }

    // ---- objects ----

    private fun objects(kind: String): LinkedHashMap<String, JSONObject> = cache.getOrPut(kind) {
        val m = LinkedHashMap<String, JSONObject>()
        db.rawQuery("SELECT id, json FROM objects WHERE kind = ? ORDER BY id", arrayOf(kind)).use { c -> while (c.moveToNext()) m[c.getString(0)] = JSONObject(c.getString(1)) }
        m
    }

    /**
     * The object's kind is looked up first, so only that kind's objects are loaded: walking
     * every kind pulled the whole store - every app's data - into memory on the first get,
     * merge or del from anywhere, and kept it there.
     */
    private fun byId(id: String): JSONObject? {
        for (m in cache.values) m[id]?.let { return it }
        val kind = db.rawQuery("SELECT kind FROM objects WHERE id = ?", arrayOf(id)).use { c -> if (c.moveToNext()) c.getString(0) else null }
        return if (kind != null && kind in kinds) objects(kind)[id] else null
    }

    /**
     * db8's revision sets (MojDbRevisionSet), for the kind and every kind it extends: a new
     * object, or one where any prop of a set changed, gets that set's prop set to its new
     * _rev; otherwise the prop keeps whatever the object carries. The mail services watch
     * these (ImapConfigRev, UpsyncRev, _revSmtp) to hear of changes they must act on.
     */
    private fun applyRevSets(old: JSONObject?, o: JSONObject) {
        val seen = HashSet<String>()
        val todo = ArrayDeque<String>().apply { add(o.optString("_kind")) }
        while (todo.isNotEmpty()) {
            val k = kinds[todo.removeFirst()] ?: continue
            if (!seen.add(k.id)) continue
            todo.addAll(k.extends)
            for (rs in k.revSets) {
                if (old == null || rs.props.any { p -> valueText(old, p) != valueText(o, p) }) o.put(rs.name, o.getLong("_rev"))
            }
        }
    }

    /**
     * db8 (MojDb::assignIds) gives every object inside an array that has no _id one of its own,
     * the next number of the revision counter in hex: on the reference TouchPad an account's
     * capability provider is {"_id":"44e",...} beside the account's _rev 1101. The accounts
     * service tells an enabled capability by that _id, and calls its transport's onEnabled.
     */
    private fun assignIds(o: JSONObject) {
        for (k in o.keys()) when (val v = o.get(k)) {
            is JSONArray -> for (i in 0 until v.length()) (v.opt(i) as? JSONObject)?.let { e ->
                if (!e.has("_id")) e.put("_id", java.lang.Long.toHexString(nextRev()))
                assignIds(e)
            }
            is JSONObject -> assignIds(v)
        }
    }

    private fun valueText(o: JSONObject, path: String): String? = value(o, path)?.toString()

    private fun store(o: JSONObject) {
        val id = o.getString("_id"); val kind = o.getString("_kind")
        db.insertWithOnConflict("objects", null, ContentValues().apply { put("id", id); put("kind", kind); put("json", o.toString()) }, SQLiteDatabase.CONFLICT_REPLACE)
        objects(kind)[id] = o
    }

    private fun purge(o: JSONObject) {
        db.delete("objects", "id = ?", arrayOf(o.getString("_id")))
        objects(o.getString("_kind")).remove(o.getString("_id"))
    }

    private fun put(caller: String, p: JSONObject): String {
        val objs = required(caller, p, "objects") as JSONArray
        val prepared = ArrayList<Pair<JSONObject?, JSONObject>>()
        for (i in 0 until objs.length()) {
            val o = JSONObject(objs.getJSONObject(i).toString())
            val kindId = o.optString("_kind").ifEmpty { throw DbError(-3969, "db: kind not specified") }
            val old = o.optString("_id").takeIf { it.isNotEmpty() }?.let { byId(it) }
            checkAccess(caller, kindId, if (old != null) "update" else "create")
            if (old != null) {
                if (!o.has("_rev")) throw DbError(-3960, "db: _rev must be specified for existing objects")
                checkRev(old, o.optLong("_rev"))
            }
            prepared += old to o
        }
        val results = JSONArray()
        transaction {
            for ((old, o) in prepared) {
                if (!o.has("_id")) o.put("_id", newId())
                o.put("_rev", nextRev())
                assignIds(o)
                applyRevSets(old, o)
                if (old != null && old.optString("_kind") != o.optString("_kind")) purge(old)
                store(o)
                results.put(JSONObject().put("id", o.getString("_id")).put("rev", o.getLong("_rev")))
                changed(old, o)
            }
        }
        return JSONObject().put("returnValue", true).put("results", results).toString()
    }

    private fun checkRev(old: JSONObject, given: Long) {
        val expected = old.optLong("_rev")
        if (given != expected) throw DbError(-3961, "db: revision mismatch - expected $expected, got $given")
    }

    private fun get(caller: String, p: JSONObject): String {
        val ids = required(caller, p, "ids") as JSONArray
        val results = JSONArray()
        for (i in 0 until ids.length()) byId(ids.getString(i))?.let { o -> checkAccess(caller, o.getString("_kind")); results.put(o) }
        return JSONObject().put("returnValue", true).put("results", results).toString()
    }

    private fun merge(caller: String, p: JSONObject): String {
        if (p.has("query")) {
            val props = required(caller, p, "props") as JSONObject
            checkAccess(caller, p.getJSONObject("query").optString("from"), "update")
            val matches = run(caller, p.getJSONObject("query"), search = false, validate = true).first
            transaction { for (o in matches) mergeOne(o, props) }
            return JSONObject().put("returnValue", true).put("count", matches.size).toString()
        }
        val objs = required(caller, p, "objects") as JSONArray
        val results = JSONArray()
        transaction {
            for (i in 0 until objs.length()) {
                val patch = objs.getJSONObject(i)
                val old = patch.optString("_id").takeIf { it.isNotEmpty() }?.let { byId(it) }
                // A new id with a kind is created, as a put would: measured on the reference
                // TouchPad (merge of "probe.fixed.id" into an empty kind returned its id and rev,
                // and get found it). palmprofile keeps its token that way, merging into
                // "com.palm.palmprofile.token" from the first sign-in on.
                // So is one with no _id at all, as db8's put with the merge flag does (MojDb::putImpl):
                // the SMTP service saves a new outgoing message that way.
                if (old == null) {
                    val kindId = patch.optString("_kind").takeIf { it.isNotEmpty() }
                        ?: throw DbError(-3950, "db: object not found")
                    checkAccess(caller, kindId, "create")
                    val o = JSONObject(patch.toString()).put("_rev", nextRev())
                    if (!o.has("_id")) o.put("_id", newId())
                    assignIds(o)
                    applyRevSets(null, o)
                    store(o)
                    changed(null, o)
                    results.put(JSONObject().put("id", o.getString("_id")).put("rev", o.getLong("_rev")))
                    continue
                }
                checkAccess(caller, old.getString("_kind"), "update")
                if (patch.has("_rev")) checkRev(old, patch.optLong("_rev"))
                val o = mergeOne(old, patch)
                results.put(JSONObject().put("id", o.getString("_id")).put("rev", o.getLong("_rev")))
            }
        }
        return JSONObject().put("returnValue", true).put("results", results).toString()
    }

    /** Merges props into an object: objects merge recursively, anything else replaces. */
    private fun mergeOne(old: JSONObject, props: JSONObject): JSONObject {
        val o = JSONObject(old.toString())
        deepMerge(o, props)
        o.put("_id", old.getString("_id")).put("_kind", old.getString("_kind")).put("_rev", nextRev())
        assignIds(o)
        applyRevSets(old, o)
        store(o)
        changed(old, o)
        return o
    }

    private fun deepMerge(into: JSONObject, from: JSONObject) {
        for (k in from.keys()) {
            if (k == "_id" || k == "_rev" || k == "_kind") continue
            val v = from.get(k)
            val cur = into.opt(k)
            if (v is JSONObject && cur is JSONObject) deepMerge(cur, v) else into.put(k, v)
        }
    }

    private fun del(caller: String, p: JSONObject): String {
        val purge = p.optBoolean("purge")
        if (p.has("query")) {
            checkAccess(caller, p.getJSONObject("query").optString("from"), "delete")
            val matches = run(caller, p.getJSONObject("query"), search = false, validate = true).first
            transaction { for (o in matches) delOne(o, purge) }
            return JSONObject().put("returnValue", true).put("count", matches.size).toString()
        }
        val ids = required(caller, p, "ids") as JSONArray
        val results = JSONArray()
        transaction {
            for (i in 0 until ids.length()) {
                val old = byId(ids.getString(i)) ?: continue
                checkAccess(caller, old.getString("_kind"), "delete")
                val o = delOne(old, purge)
                results.put(JSONObject().put("id", old.getString("_id")).also { r -> o?.let { r.put("rev", it.getLong("_rev")) } })
            }
        }
        return JSONObject().put("returnValue", true).put("results", results).toString()
    }

    /** A soft delete marks _del and takes a revision; purge removes the object. */
    private fun delOne(old: JSONObject, purge: Boolean): JSONObject? {
        if (purge) { purge(old); changed(old, null); return null }
        val o = JSONObject(old.toString()).put("_del", true).put("_rev", nextRev())
        applyRevSets(old, o)
        store(o)
        changed(old, o)
        return o
    }

    // ---- queries ----

    private fun find(caller: String, p: JSONObject, call: Bus.Call?, search: Boolean): String {
        val q = required(caller, p, "query") as JSONObject
        if (!search && q.has("filter")) throw DbError(-3978, "db: filter not allowed in find")
        val (all, kindsQueried) = run(caller, q, search, validate = true)
        val limit = q.optInt("limit", MAX_LIMIT).coerceIn(1, MAX_LIMIT)
        val offset = q.optString("page").takeIf { it.isNotEmpty() }?.let { pageOffset(it) } ?: 0
        val page = all.drop(offset).take(limit)
        val select = q.optJSONArray("select")?.let { a -> (0 until a.length()).map { a.getString(it) } }
        val results = JSONArray()
        for (o in page) results.put(if (select == null) o else project(o, select))
        val out = JSONObject().put("returnValue", true).put("results", results)
        if (page.size == limit) out.put("next", pageToken(offset + limit))
        if (search || p.optBoolean("count")) out.put("count", all.size)
        if (call != null && p.optBoolean("watch")) addWatch(call, kindsQueried, clauses(q.optJSONArray("where")))
        return out.toString()
    }

    private fun watch(caller: String, p: JSONObject, call: Bus.Call?): String {
        val q = required(caller, p, "query") as JSONObject
        val (_, kindsQueried) = run(caller, q, search = false, validate = true)
        if (call != null) addWatch(call, kindsQueried, clauses(q.optJSONArray("where")))
        return ok()
    }

    /** The query's matching objects, sorted, and the kinds it covers. */
    private fun run(caller: String, q: JSONObject, search: Boolean, validate: Boolean): Pair<List<JSONObject>, Set<String>> {
        val from = q.optString("from").ifEmpty { throw DbError(22, "invalid parameters: caller='$caller' error='required property not found - 'from' for property 'query''") }
        val k = kind(from)
        checkAccess(caller, from)
        val where = clauses(q.optJSONArray("where"))
        val filter = clauses(q.optJSONArray("filter"))
        val orderBy = q.optString("orderBy").ifEmpty { null }
        // A query that names _del (an index that includes deleted objects) gets them as it asks.
        val incDel = q.optBoolean("incDel") || (where + filter).any { it.prop == "_del" }
        if (validate) {
            if (q.optBoolean("incDel") && orderBy != null) throw DbError(-3978, "db: query order not compatible with where clause")
            // search sorts what it finds itself (MojDbSearchCursor), so only find's order has
            // to be the index's: Email's address field searches people by searchProperty and
            // orders them by sortKey.
            if (!indexed(k, where, if (search) null else orderBy)) throw DbError(-3965, "db: no index for query")
        }
        val family = family(from)
        val collate = orderBy?.let { ob -> k.indexes.flatMap { it.props }.firstOrNull { it.name == ob }?.collate }
        val list = family.flatMap { objects(it).values }
            .filter { o -> (incDel || !o.optBoolean("_del")) && where.all { matches(o, it) } && filter.all { matches(o, it) } }
            .sortedWith(Comparator { a, b ->
                val c = if (orderBy == null) 0 else compare(indexed(a, orderBy), indexed(b, orderBy), collate)
                if (c != 0) c else a.getString("_id").compareTo(b.getString("_id"))
            })
        return (if (q.optBoolean("desc")) list.reversed() else list) to family
    }

    /**
     * Whether an index can serve the query, as db8 requires: the "=" props first (in any order),
     * then at most one other prop (a range, prefix or search), and orderBy on that prop, or on
     * the next one when there's no range.
     */
    private fun indexed(k: Kind, where: List<Clause>, orderBy: String?): Boolean {
        if (where.isEmpty() && (orderBy == null || orderBy == "_id")) return true
        val eq = where.filter { it.op == "=" }.map { it.prop }.toSet()
        val range = where.filter { it.op != "=" }.map { it.prop }.toSet()
        if (range.size > 1) return false
        return k.indexes.any { ix ->
            // An index leading with _del serves a query that doesn't name it, which db8 asks
            // of it as _del = false.
            val props = ix.props.map { it.name }.let { if (it.firstOrNull() == "_del" && "_del" !in eq && "_del" !in range) it.drop(1) else it }
            if (props.size < eq.size || props.take(eq.size).toSet() != eq) return@any false
            val next = props.getOrNull(eq.size)
            when {
                range.isNotEmpty() -> next == range.first() && (orderBy == null || orderBy == next)
                orderBy != null -> orderBy == next || orderBy in eq
                else -> true
            }
        }
    }

    private fun clauses(a: JSONArray?): List<Clause> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { Clause(it.getString("prop"), it.optString("op", "="), it.opt("val")) } }
    }

    private fun value(o: JSONObject, path: String): Any? {
        // Every object is deleted or not: db8 indexed a live one as _del false.
        if (path == "_del") return o.optBoolean("_del")
        return walk(o, path.split('.'), 0)
    }

    /**
     * A dotted path's value. Through an array of objects it is every element's value, as db8
     * indexed it (MojDbIndex: a prop under an array has one index entry per element): Email
     * finds its accounts by "capabilityProviders.capability", inside the account's array.
     */
    private fun walk(cur: Any?, parts: List<String>, i: Int): Any? {
        if (cur == null || cur == JSONObject.NULL) return null
        if (i == parts.size) return cur
        return when (cur) {
            is JSONObject -> walk(cur.opt(parts[i]), parts, i + 1)
            is JSONArray -> {
                val out = JSONArray()
                for (j in 0 until cur.length()) when (val v = walk(cur.opt(j), parts, i)) {
                    null -> {}
                    is JSONArray -> for (k in 0 until v.length()) out.put(v.opt(k))
                    else -> out.put(v)
                }
                if (out.length() == 0) null else out
            }
            else -> null
        }
    }

    /** A clause matches when any of the prop's values (arrays count element by element) matches any given value. */
    /**
     * A prop as db8's indexes held it: an object without the prop is indexed under the
     * index's "default" for it, so it is found by that value (Email's kind gives
     * "flags.visible" the default true, and the mail services never set it on a new message).
     */
    private fun indexed(o: JSONObject, prop: String): Any? = value(o, prop) ?: defaultOf(o.optString("_kind"), prop, HashSet())

    private fun defaultOf(kindId: String, prop: String, seen: HashSet<String>): Any? {
        val k = kinds[kindId] ?: return null
        if (!seen.add(kindId)) return null
        for (ix in k.indexes) for (p in ix.props) if (p.name == prop && p.default != null && p.default != JSONObject.NULL) return p.default
        for (e in k.extends) defaultOf(e, prop, seen)?.let { return it }
        return null
    }

    private fun matches(o: JSONObject, c: Clause): Boolean {
        val v = indexed(o, c.prop)
        val have: List<Any?> = if (v is JSONArray) (0 until v.length()).map { v.opt(it) } else listOf(v)
        val want: List<Any?> = if (c.op == "=" && c.value is JSONArray) (c.value as JSONArray).let { a -> (0 until a.length()).map { a.opt(it) } } else listOf(c.value)
        return have.any { h -> want.any { w -> test(h, c.op, w) } }
    }

    private fun test(h: Any?, op: String, w: Any?): Boolean = when (op) {
        "=" -> compare(h, w, null) == 0 && sameType(h, w)
        "!=" -> !(compare(h, w, null) == 0 && sameType(h, w))
        "<" -> sameType(h, w) && compare(h, w, null) < 0
        "<=" -> sameType(h, w) && compare(h, w, null) <= 0
        ">" -> sameType(h, w) && compare(h, w, null) > 0
        ">=" -> sameType(h, w) && compare(h, w, null) >= 0
        "%" -> h is String && w is String && h.startsWith(w)
        "?" -> h is String && w is String && words(w).all { ww -> words(h).any { it.startsWith(ww) } }
        else -> throw DbError(-3978, "db: invalid operator '$op'")
    }

    private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    private fun rank(v: Any?) = when (v) { null, JSONObject.NULL -> 0; is Boolean -> 1; is Number -> 2; is String -> 3; is JSONArray -> 4; else -> 5 }
    private fun sameType(a: Any?, b: Any?) = rank(a) == rank(b)

    /** db8's order: null, booleans, numbers, strings (by the index's collation if it has one), then the rest. */
    private fun compare(a: Any?, b: Any?, collate: String?): Int {
        val ra = rank(a); val rb = rank(b)
        if (ra != rb) return ra.compareTo(rb)
        return when (a) {
            is Boolean -> a.compareTo(b as Boolean)
            is Number -> a.toDouble().compareTo((b as Number).toDouble())
            is String -> collator(collate)?.compare(a, b as String) ?: a.compareTo(b as String)
            else -> 0
        }
    }

    private val collators = HashMap<String, Collator>()
    private fun collator(collate: String?): Collator? = when (collate) {
        null, "", "default", "identical" -> null
        // One per strength: a sort asked for a new instance per comparison before.
        else -> collators.getOrPut(collate) { Collator.getInstance().apply { strength = when (collate) { "primary" -> Collator.PRIMARY; "secondary" -> Collator.SECONDARY; else -> Collator.TERTIARY } } }
    }

    private fun project(o: JSONObject, select: List<String>): JSONObject {
        val out = JSONObject()
        for (s in select) value(o, s)?.let { v ->
            val parts = s.split('.')
            var cur = out
            for (part in parts.dropLast(1)) cur = cur.optJSONObject(part) ?: JSONObject().also { cur.put(part, it) }
            cur.put(parts.last(), v)
        }
        return out
    }

    private fun pageToken(offset: Int) = Base64.encodeToString("lunacy:$offset".toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    private fun pageOffset(token: String): Int =
        runCatching { String(Base64.decode(token, Base64.URL_SAFE)) }.getOrNull()?.takeIf { it.startsWith("lunacy:") }
            ?.removePrefix("lunacy:")?.toIntOrNull() ?: throw DbError(-3978, "db: invalid page")

    // ---- watches ----

    private fun addWatch(call: Bus.Call, kinds: Set<String>, where: List<Clause>) {
        val w = Watch(call, kinds, where)
        watches += w
        call.onCancel { worker.execute { watches.remove(w) } }
    }

    /** One-shot, as on webOS: a change to anything the query could return fires the watch and ends it. */
    private fun changed(old: JSONObject?, new: JSONObject?) {
        val fired = watches.filter { w ->
            listOfNotNull(old, new).any { o -> o.optString("_kind") in w.kinds && w.where.all { matches(o, it) } }
        }
        for (w in fired) {
            watches.remove(w)
            main.post { w.call.reply(FIRED); w.call.cancel() }
        }
    }

    // ---- batch ----

    private fun batch(caller: String, p: JSONObject): String {
        val ops = required(caller, p, "operations") as JSONArray
        val responses = JSONArray()
        for (i in 0 until ops.length()) {
            val op = ops.getJSONObject(i)
            val r = try { exec(op.optString("method"), caller, op.optJSONObject("params") ?: JSONObject(), null) } catch (e: DbError) { error(e.code, e.text) }
            responses.put(JSONObject(r))
        }
        return JSONObject().put("returnValue", true).put("responses", responses).toString()
    }

    // ---- ids, revisions, helpers ----

    /** "++" and 14 characters in an order-preserving base-64 alphabet: time, then a counter. */
    private fun newId(): String {
        idCounter = maxOf(idCounter + 1, System.currentTimeMillis() shl 20)
        setMeta("ids", idCounter)
        var n = idCounter
        val sb = CharArray(14)
        for (i in 13 downTo 0) { sb[i] = ID_CHARS[(n and 63).toInt()]; n = n ushr 6 }
        return "++" + String(sb)
    }

    private fun nextRev(): Long { rev += 1; setMeta("rev", rev); return rev }
    private fun meta(k: String): Long = db.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(k)).use { if (it.moveToFirst()) it.getLong(0) else 0L }
    private fun setMeta(k: String, v: Long) =
        db.insertWithOnConflict("meta", null, ContentValues().apply { put("k", k); put("v", v) }, SQLiteDatabase.CONFLICT_REPLACE)

    private inline fun transaction(f: () -> Unit) {
        db.beginTransaction()
        try { f(); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    /** A required top-level parameter, or db8's schema error (code 22) naming it. */
    private fun required(caller: String, p: JSONObject, name: String): Any =
        p.opt(name) ?: throw DbError(22, "invalid parameters: caller='$caller' error='required property not found - '$name''")

    private fun ok() = JSONObject().put("returnValue", true).toString()
    private fun error(code: Int, text: String) = JSONObject().put("errorCode", code).put("errorText", text).put("returnValue", false).toString()

    companion object {
        /** db8's page size cap. */
        const val MAX_LIMIT = 500
        /** The caller name for package configuration, which may register any kind. */
        private const val CONFIGURATOR = "com.palm.configurator"
        private const val ID_CHARS = "+0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"
        private val FIRED = JSONObject().put("returnValue", true).put("fired", true).toString()
    }
}
