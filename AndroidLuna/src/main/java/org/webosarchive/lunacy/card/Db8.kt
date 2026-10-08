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
class Db8(private val service: String, file: File?,
           /** A debuggable build: the SDK's relay may purge (see "purge"). */
           private val debuggable: Boolean = false) {
    private val db: SQLiteDatabase = if (file != null) SQLiteDatabase.openOrCreateDatabase(file, null) else SQLiteDatabase.create(null)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val kinds = LinkedHashMap<String, Kind>()
    private val cache = HashMap<String, LinkedHashMap<String, JSONObject>>()  // kind -> id -> object
    private val watches = ArrayList<Watch>()
    /** Kinds whose records are kept in line with a source of truth outside db8; see [mirror]. */
    private class Mirror(val source: () -> List<JSONObject>, val initial: Set<String>, val owns: (JSONObject) -> Boolean,
                         val added: (() -> Unit)?) { var synced = 0L }
    private val mirrors = HashMap<String, Mirror>()
    private var mirrorTicker: java.util.concurrent.ScheduledExecutorService? = null
    private var rev = 0L
    private var idCounter = 0L

    private class IndexProp(val name: String, val collate: String?, val default: Any? = null,
                            /** A "multi" prop's included props: it stands for all their values. */
                            val include: List<String>? = null)
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
        // db8's RevTimestamp records: which revision the database was at, at each purge.
        db.execSQL("CREATE TABLE IF NOT EXISTS revtimes (rev INTEGER PRIMARY KEY, ts INTEGER)")
        runCatching { db.execSQL("ALTER TABLE permissions ADD COLUMN ops TEXT") }  // databases from before ops
        db.rawQuery("SELECT spec FROM kinds", null).use { c -> while (c.moveToNext()) parseKind(JSONObject(c.getString(0))).let { kinds[it.id] = it } }
        rev = meta("rev"); idCounter = meta("ids")
        if (meta(NESTED_IDS) == 0L) giveNestedIds()
    }

    /**
     * Records stored before Lunacy's db8 gave array objects their _id (assignIds, 2026-10-07)
     * get them now, once, as db8 would have given them when they were written. Without them a
     * profile account from an older Lunacy has capability providers that look disabled, and
     * Calendar finds no account to keep its On-Device calendar on.
     */
    private fun giveNestedIds() {
        var repaired = 0
        db.beginTransaction()
        try {
            db.rawQuery("SELECT id, json FROM objects", null).use { c ->
                while (c.moveToNext()) {
                    val text = c.getString(1)
                    if (!text.contains('[')) continue
                    val o = try { JSONObject(text) } catch (e: Exception) { continue }
                    assignIds(o)
                    val now = o.toString()
                    if (now != JSONObject(text).toString()) {
                        db.update("objects", ContentValues().apply { put("json", now) }, "id = ?", arrayOf(c.getString(0)))
                        repaired++
                    }
                }
            }
            setMeta(NESTED_IDS, 1)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (repaired > 0) Log.i(AppServer.TAG, "$service: gave $repaired stored records their array objects' _ids")
    }

    /** Registers a package's configuration/db files, as webOS's configurator did at install. */
    fun configure(kindFiles: List<JSONObject>, permissionFiles: List<JSONArray>) = worker.execute {
        for (k in kindFiles) try { putKind(CONFIGURATOR, k) } catch (e: Exception) { Log.w(AppServer.TAG, "$service: kind ${k.optString("id")}: ${e.message}") }
        for (p in permissionFiles) try { putPermissions(CONFIGURATOR, JSONObject().put("permissions", p)) } catch (e: Exception) { Log.w(AppServer.TAG, "$service: permissions: ${e.message}") }
    }

    /**
     * A kind whose truth is elsewhere (the files under /media/internal, for FileIndex): db8
     * registers it, and before anything reads or changes it, brings its records into line with
     * [source], at most every [MIRROR_MS]. Each record is matched to its source by `path`: a new
     * one is created, a changed one takes the source's properties (keeping anything an app
     * added), and one whose source has gone is purged, each firing watches as any write does.
     * While a watch is open on the kind it is checked every [MIRROR_WATCH_MS], so a list that
     * is on the screen hears about a file as it arrives.
     *
     * [initial] names properties the source sets only on a new record, which others then own
     * (the photos service marks an image's thumbnails done). [owns] picks the records the source
     * speaks for; the rest are left alone (a photo album synced from an account has no folder).
     * [added] runs, on db8's thread, after a pass that made new records.
     */
    fun mirror(spec: JSONObject, permissions: JSONArray, initial: Set<String> = emptySet(), owns: (JSONObject) -> Boolean = { true },
               added: (() -> Unit)? = null, source: () -> List<JSONObject>) = worker.execute {
        try {
            putKind(CONFIGURATOR, spec)
            putPermissions(CONFIGURATOR, JSONObject().put("permissions", permissions))
        } catch (e: Exception) { Log.w(AppServer.TAG, "$service: mirror ${spec.optString("id")}: ${e.message}"); return@execute }
        mirrors[spec.getString("id")] = Mirror(source, initial, owns, added)
        if (mirrorTicker == null) mirrorTicker = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({
                worker.execute { for ((k, m) in mirrors) if (watches.any { w -> k in w.kinds }) syncMirror(k, m) }
            }, MIRROR_WATCH_MS, MIRROR_WATCH_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    private fun syncMirror(kind: String, m: Mirror) {
        val now = android.os.SystemClock.uptimeMillis()
        if (m.synced != 0L && now - m.synced < MIRROR_MS) return
        m.synced = now
        val files = try { m.source() } catch (e: Exception) { Log.w(AppServer.TAG, "$service: mirror $kind: $e"); return }
        val byPath = LinkedHashMap<String, JSONObject>()
        for (f in files) byPath[f.optString("path")] = f
        val current = objects(kind).values.filter { !it.optBoolean("_del") && m.owns(it) }
        transaction {
            for (o in current) {
                val f = byPath.remove(o.optString("path"))
                if (f == null) { delOne(o, purge = true); continue }
                for (k in m.initial) f.remove(k)
                if (f.keys().asSequence().any { k -> !same(o.opt(k), f.opt(k)) }) mergeOne(o, f)
            }
            for (f in byPath.values) {
                val o = JSONObject(f.toString()).put("_kind", kind).put("_id", newId()).put("_rev", nextRev())
                assignIds(o)
                applyRevSets(null, o)
                store(o)
                changed(null, o)
            }
        }
        if (byPath.isNotEmpty()) m.added?.invoke()
    }

    /** Equal as a source says it: the _ids db8 gave objects inside a record aren't the source's. */
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> {
            val ka = a.keys().asSequence().filter { it != "_id" }.toSet()
            ka == b.keys().asSequence().filter { it != "_id" }.toSet() && ka.all { same(a.opt(it), b.opt(it)) }
        }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b || (a == null && b == JSONObject.NULL) || (a == JSONObject.NULL && b == null)
    }

    /**
     * For a mirror's source, on db8's thread: the mirrored [kind] brought into line, and its
     * records' ids by path (an image's albumId is its folder's album).
     */
    fun mirrorIds(kind: String): Map<String, String> {
        mirrors[kind]?.let { syncMirror(kind, it) }
        return objects(kind).values.filter { !it.optBoolean("_del") }.associate { it.optString("path") to it.getString("_id") }
    }

    /** What an operation could read or change of a mirrored kind: the one it names, or all of them. */
    private fun syncMirrorsFor(method: String, p: JSONObject) {
        if (mirrors.isEmpty() || method !in MIRRORED_OPS) return
        val from = p.optJSONObject("query")?.optString("from")
        // A query of a kind reads every kind that extends it: those are synced too.
        if (from != null && from.isNotEmpty()) { for (k in family(from)) mirrors[k]?.let { syncMirror(k, it) } }
        else for ((k, m) in mirrors) syncMirror(k, m)
    }

    private var bus: Bus? = null

    fun register(bus: Bus) {
        this.bus = bus
        for (m in listOf("putKind", "delKind", "put", "get", "merge", "del", "find", "search", "watch", "reserveIds", "batch", "putPermissions", "purge", "purgeStatus")) {
            bus.register(service, m, Bus.CallHandler { call -> worker.execute { handle(m, call) } })
        }
        bus.register(service, "internal/scheduledPurge", Bus.CallHandler { scheduledPurge(it) })
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
    private fun exec(method: String, caller: String, p: JSONObject, call: Bus.Call?): String {
        syncMirrorsFor(method, p)
        return execOp(method, caller, p, call)
    }

    private fun execOp(method: String, caller: String, p: JSONObject, call: Bus.Call?): String = when (method) {
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
        "purge" -> {
            // A debug build lets the SDK's relay purge too, to test the window without waiting it out.
            if (caller !in ADMINS && !(debuggable && caller == "novacomd")) throw DbError(-3963, "db: permission denied")
            val w = p.opt("window")
            if (w != null && w !is Int && w !is Long) throw DbError(22, "invalid parameters: caller='$caller' error='invalid type for property 'window''")
            JSONObject().put("returnValue", true).put("count", purgeDeleted((w as? Number)?.toInt() ?: PURGE_WINDOW_DAYS)).toString()
        }
        "purgeStatus" -> JSONObject().put("returnValue", true).put("rev", lastPurgedRev()).toString()
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
                // A trailing comma (Calendar's event kind ends its list with one) is a null
                // here; webOS's JSON parser took the list as if it weren't there.
                val ix = a.optJSONObject(i) ?: continue
                val props = ArrayList<IndexProp>()
                // An index that includes deleted objects leads with _del, as db8's did.
                if (ix.optBoolean("incDel")) props += IndexProp("_del", null)
                ix.optJSONArray("props")?.let { pa -> for (j in 0 until pa.length()) pa.getJSONObject(j).let {
                    val include = if (it.optString("type") == "multi") it.optJSONArray("include")?.let { inc ->
                        (0 until inc.length()).mapNotNull { n -> inc.optJSONObject(n)?.optString("name")?.takeIf { x -> x.isNotEmpty() } }
                    } else null
                    props += IndexProp(it.getString("name"), it.optString("collate").ifEmpty { null }, it.opt("default"), include)
                } }
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
     * caller's own entry for the kind, else the longest wildcard entry that matches it (db8
     * keeps callers longest first, LengthComp, so "com.palm.*" wins over "*"), gives the
     * operation's value; a kind with no value for it takes its first super kind's. So the SMTP
     * service, granted on com.palm.mail.account, may update a com.palm.imap.account, which
     * extends it and grants nothing itself.
     */
    private fun permission(caller: String, k: Kind, op: String, seen: HashSet<String>): String? {
        if (!seen.add(k.id)) return null
        var exact: String? = null; var wild: String? = null; var wildLength = -1
        db.rawQuery("SELECT caller, ops FROM permissions WHERE kind = ?", arrayOf(k.id)).use { c ->
            while (c.moveToNext()) {
                val who = c.getString(0)
                if (who == caller) exact = c.getString(1) ?: "{}"
                else if (who.length > wildLength && who.endsWith("*") && caller.startsWith(who.dropLast(1))) {
                    wild = c.getString(1) ?: "{}"; wildLength = who.length
                }
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
    private fun indexed(o: JSONObject, prop: String): Any? =
        value(o, prop) ?: multi(o, prop) ?: defaultOf(o.optString("_kind"), prop, HashSet())

    /**
     * A "multi" prop (MojDbMultiExtractor): not in the object, but the words of the props it
     * includes, as db8 indexed each of them. Calendar's events are found by "searchText",
     * which includes their subject, location and note: every word asked ("?") must start one
     * of theirs, from any of the three.
     */
    private fun multi(o: JSONObject, prop: String): Any? {
        val include = includeOf(o.optString("_kind"), prop, HashSet()) ?: return null
        val out = ArrayList<String>()
        for (name in include) when (val v = value(o, name)) {
            is String -> out += v
            is JSONArray -> for (k in 0 until v.length()) (v.opt(k) as? String)?.let { out += it }
        }
        return if (out.isEmpty()) null else out.joinToString(" ")
    }

    private fun includeOf(kindId: String, prop: String, seen: HashSet<String>): List<String>? {
        val k = kinds[kindId] ?: return null
        if (!seen.add(kindId)) return null
        for (ix in k.indexes) for (p in ix.props) if (p.name == prop && p.include != null) return p.include
        for (e in k.extends) includeOf(e, prop, seen)?.let { return it }
        return null
    }

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

    // ---- purge ----

    /**
     * Deleted objects are kept, whole, marked _del, so that watches and syncs can see the
     * deletion (`delOne`). db8 removes them for good once they have been deleted for longer
     * than its purge window: 7 days on the reference TouchPad (/etc/palm/mojodb.conf
     * "purgeWindow"), with the source's default 14. Each purge notes the revision the database
     * is at with the time (RevTimestamp), takes the newest note older than the window, removes
     * every deleted object at or below that revision, and remembers it as the last purged
     * revision, which purgeStatus reports and syncs read to learn they missed deletions
     * (`MojDb::purge`, `purgeImpl`). Deleting an account (an Email account's mail, a Synergy
     * account's events) comes to this: until it runs, the data is still in the database.
     */
    private fun purgeDeleted(windowDays: Int): Int {
        val now = System.currentTimeMillis()
        var count = 0
        transaction {
            db.insert("revtimes", null, ContentValues().apply { put("rev", nextRev()); put("ts", now) })
            val cut = db.rawQuery("SELECT rev, ts FROM revtimes WHERE ts <= ? ORDER BY ts DESC LIMIT 1",
                arrayOf((now - windowDays * 86_400_000L).toString())).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }
                ?: return@transaction
            val gone = ArrayList<JSONObject>()
            db.rawQuery("SELECT json FROM objects WHERE json LIKE '%\"_del\":true%'", null).use { c ->
                while (c.moveToNext()) {
                    val o = runCatching { JSONObject(c.getString(0)) }.getOrNull() ?: continue
                    if (o.optBoolean("_del") && o.optLong("_rev") <= cut.first) gone += o
                }
            }
            gone.forEach { purge(it) }
            count = gone.size
            if (count > 0) setMeta(LAST_PURGED, cut.first + 1)
            db.delete("revtimes", "ts <= ?", arrayOf(cut.second.toString()))
        }
        if (count > 0) Log.i(AppServer.TAG, "$service: purged $count deleted objects")
        return count
    }

    /** -1 until a purge has removed something, as on the reference TouchPad. */
    private fun lastPurgedRev(): Long = meta(LAST_PURGED) - 1

    /**
     * palm://com.palm.db/internal/scheduledPurge, the daily "mojodbpurge" activity's callback
     * (/etc/palm/activities/com.palm.db, the device's file). As db8's PurgeHandler: answer at
     * once, adopt the activity, purge, and complete it with restart, so it runs again a day on.
     */
    private fun scheduledPurge(call: Bus.Call) {
        val bus = bus ?: return call.reply(error(-1, "db: not ready"))
        val activityId = call.params.optJSONObject("\$activity")?.opt("activityId")
            ?: return call.reply(error(22, "invalid parameters: caller='${call.appId}' error='required property not found - '\$activity''"))
        call.reply(ok())
        val ids = JSONObject().put("activityId", activityId).put("activityName", "mojodb scheduled purge")
        var done = false
        var adopt: Bus.Call? = null
        adopt = bus.call(service, "palm://com.palm.activitymanager/adopt", JSONObject(ids.toString()).put("wait", true).put("subscribe", true).toString(), privateBus = true) { reply ->
            val r = runCatching { JSONObject(reply) }.getOrNull() ?: return@call
            if (done || !(r.optBoolean("adopted") || r.optString("event") == "orphan")) return@call
            done = true
            worker.execute {
                runCatching { purgeDeleted(PURGE_WINDOW_DAYS) }.onFailure { Log.w(AppServer.TAG, "$service: purge failed", it) }
                bus.call(service, "palm://com.palm.activitymanager/complete", JSONObject(ids.toString()).put("restart", true).toString(), privateBus = true) {
                    main.post { adopt?.cancel() }
                }
            }
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
        /** meta: the one-time repair that gave older records' array objects their _ids has run. */
        private const val NESTED_IDS = "nestedIds"
        /** meta: the last purged revision, plus one (0 for none). */
        private const val LAST_PURGED = "lastPurgedRev"
        /** The reference TouchPad's purgeWindow (/etc/palm/mojodb.conf). */
        private const val PURGE_WINDOW_DAYS = 7
        /** db8's admin role (/etc/palm/mojodb.conf), the only callers allowed purge. */
        private val ADMINS = setOf("com.palm.configurator", "com.palm.service.backup", "com.palm.odd.service",
            "com.palm.service.migrationscript", "com.palm.spacecadet", "com.palm.backup.privileged", "com.palm.app.backup.service")
        /** How often a mirrored kind is checked against its source while it is being used, and while watched. */
        private const val MIRROR_MS = 2000L
        private const val MIRROR_WATCH_MS = 5000L
        private val MIRRORED_OPS = setOf("find", "search", "watch", "get", "merge", "del")
        /** db8's page size cap. */
        const val MAX_LIMIT = 500
        /** The caller name for package configuration, which may register any kind. */
        private const val CONFIGURATOR = "com.palm.configurator"
        private const val ID_CHARS = "+0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"
        private val FIRED = JSONObject().put("returnValue", true).put("fired", true).toString()
    }
}
