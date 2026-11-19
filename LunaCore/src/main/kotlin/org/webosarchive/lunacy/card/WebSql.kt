package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.core.Base64Codec
import org.webosarchive.lunacy.core.Log
import org.webosarchive.lunacy.core.Platform
import org.webosarchive.lunacy.core.SqlDb
import org.webosarchive.lunacy.core.SqlError
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The native half of the WebSQL polyfill (assets/lunacy/websql.js), for WebViews that have
 * dropped WebSQL (Chromium 119 and later). One per window, like the network shim; the
 * window's statements run in order on a thread of their own, as WebKit ran them, and never
 * on the main thread.
 *
 * Storage is one SQLite file per app and database name, under `<dir>/<app id>/` (`files/websql` on
 * Android), so
 * an app's data is the app's whichever origin its windows load from. The version lives
 * inside the file in WebKit's own `__WebKitDatabaseInfo__` table, which is also where a
 * database the WebView itself stored before it lost WebSQL keeps it: such a file is copied
 * in the first time the polyfill opens that name ([migrate]), data and version together.
 *
 * Two windows of one app (a card and its dashboard) that open the same name get their own
 * connections to the same file, and SQLite's own locking keeps their transactions apart.
 *
 * Error codes and wording are WebKit's SQLStatement's: a statement that fails to prepare is
 * SYNTAX_ERR (5), a constraint is CONSTRAINT_ERR (6), a full disk QUOTA_ERR (4), any other
 * failure to run DATABASE_ERR (1), and the message is SQLite's own, as the reference TouchPad
 * gave it ("no such table: nosuchtable"; Docs/mojo.md).
 */
class WebSql(
    /** Where every app's databases are kept. */
    private val dir: File,
    private val appId: String,
    /** Which page of the window is loaded: an earlier page's answers are dropped (see NetShim's). */
    private val page: () -> Int,
    private val deliver: (Int) -> Unit,
    /**
     * Brings in a database the host's own engine stored under [host] and [name] before it lost
     * WebSQL, as the file [target] (Android's WebView; see AndroidWebSqlMigration). Null where
     * there is none to bring.
     */
    private val migrate: ((target: File, host: String, name: String) -> Unit)? = null,
) {
    private val worker = Executors.newSingleThreadExecutor()
    // Typed as Map: ConcurrentHashMap.keySet() compiles to a KeySetView call Android 5 lacks.
    private val results: MutableMap<Int, String> = ConcurrentHashMap()
    /** Open databases by name, and the ones with a transaction open. Worker thread only. */
    private val open = HashMap<String, SqlDb>()
    private val inTransaction = HashMap<String, Boolean>()  // name -> readOnly
    /** The window is going: nothing more is answered. */
    @Volatile private var closed = false

    /** A statement's failure, with its SQLError code. */
    private class SqlFailure(val code: Int, message: String) : Exception(message)

    /**
     * openDatabase: opens (creating, or migrating, as needed) and answers with the stored
     * version, `{"version": v, "created": bool}`, or `{"mismatch": stored}` when the page
     * asked for a version the database isn't at, or `{"error": text}`. Synchronous for the
     * page, which needs the answer to return from openDatabase; the work itself runs on the
     * worker so that every use of a connection is from one thread.
     */
    fun open(name: String, version: String, hasCreationCallback: Boolean): String =
        try {
            worker.submit(Callable { openOn(name, version, hasCreationCallback) }).get()
        } catch (e: Exception) {
            JSONObject().put("error", e.cause?.message ?: e.message ?: "unable to open database").toString()
        }

    private fun openOn(name: String, version: String, hasCreationCallback: Boolean): String {
        val db = open[name] ?: openFile(name).also { open[name] = it }
        db.exec("CREATE TABLE IF NOT EXISTS $INFO_TABLE (key TEXT NOT NULL ON CONFLICT FAIL UNIQUE ON CONFLICT REPLACE, value TEXT NOT NULL ON CONFLICT FAIL)")
        val stored = readVersion(db)
        val r = JSONObject()
        if (stored == null) {
            // New: it takes the version asked for, unless a creation callback is going to
            // set it up, in which case it starts at "" (the standard's processing model).
            val v = if (hasCreationCallback) "" else version
            writeVersion(db, v)
            return r.put("version", v).put("created", true).toString()
        }
        if (version.isNotEmpty() && version != stored) return r.put("mismatch", stored).toString()
        return r.put("version", stored).put("created", false).toString()
    }

    private fun openFile(name: String): SqlDb {
        val dir = File(dir, appId).also { it.mkdirs() }
        // The name as a file's: URL-safe Base64 without padding (android.util.Base64's URL_SAFE | NO_PADDING | NO_WRAP).
        val file = File(dir, Base64Codec.encode(name.toByteArray()).replace('+', '-').replace('/', '_').trimEnd('=') + ".db")
        // The WebView kept it under the app's own origin, worked out from its id rather than
        // taken from the page, which could name another app's (code review S14, rule 10).
        // Lowercased, as Chromium writes a host (and as location.host read it before): an app
        // id with capitals would otherwise miss its own database.
        if (!file.exists()) migrate?.invoke(file, appId.lowercase(java.util.Locale.ROOT) + AppServer.HOST_SUFFIX, name)
        return Platform.sql.open(file)
    }

    private fun readVersion(db: SqlDb): String? =
        db.query("SELECT value FROM $INFO_TABLE WHERE key = ?", arrayOf(VERSION_KEY)) { c -> if (c.next()) c.getString(0) else null }

    private fun writeVersion(db: SqlDb, version: String) =
        db.exec("INSERT INTO $INFO_TABLE (key, value) VALUES (?, ?)", VERSION_KEY, version)

    /** Queues a request; deliver(id) is called when result(id) has its answer. */
    fun request(id: Int, request: String) {
        val from = page()
        worker.execute {
            if (from != page() || closed) return@execute
            val r = try { handle(JSONObject(request)) } catch (e: Exception) { error(UNKNOWN_ERR, e.message ?: e.toString()) }
            if (from != page() || closed) return@execute
            results[id] = r.toString()
            deliver(id)
        }
    }

    fun result(id: Int): String = results.remove(id) ?: error(UNKNOWN_ERR, "no result").toString()

    /** A new page in the window (its count already moved on): its transactions are rolled back, its answers dropped. */
    fun reset() {
        results.clear()
        worker.execute { rollbackAll() }
    }

    /** The window is going away. */
    fun close() {
        closed = true
        results.clear()
        worker.execute {
            rollbackAll()
            open.values.forEach { runCatching { it.close() } }
            open.clear()
        }
        worker.shutdown()
    }

    private fun rollbackAll() {
        for (name in inTransaction.keys.toList()) runCatching { open[name]?.endTransaction() }
        inTransaction.clear()
    }

    // ---- the transaction protocol, worker thread ----

    private fun handle(r: JSONObject): JSONObject {
        val name = r.optString("db")
        val db = open[name] ?: return error(DATABASE_ERR, "unable to open database")
        return when (r.optString("op")) {
            "begin" -> begin(name, db, r.optBoolean("readOnly"), if (r.has("oldVersion")) r.getString("oldVersion") else null)
            "exec" -> exec(name, db, r.getJSONArray("statements"))
            "commit" -> commit(name, db, if (r.has("newVersion")) r.getString("newVersion") else null)
            "rollback" -> rollback(name, db)
            else -> error(UNKNOWN_ERR, "unknown operation")
        }
    }

    private fun begin(name: String, db: SqlDb, readOnly: Boolean, oldVersion: String?): JSONObject {
        if (inTransaction.containsKey(name)) rollback(name, db)
        try {
            db.beginTransaction()
        } catch (e: Exception) {
            return error(DATABASE_ERR, "unable to begin transaction (${message(e)})")
        }
        inTransaction[name] = readOnly
        // changeVersion's preflight: the database must be at the version the app says it is.
        if (oldVersion != null && readVersion(db) != oldVersion) {
            rollback(name, db)
            return error(VERSION_ERR, "current version of the database and `oldVersion` argument do not match")
        }
        return ok()
    }

    private fun commit(name: String, db: SqlDb, newVersion: String?): JSONObject {
        if (!inTransaction.containsKey(name)) return error(DATABASE_ERR, "unable to commit transaction (none open)")
        try {
            if (newVersion != null) writeVersion(db, newVersion)
            db.setTransactionSuccessful()
            db.endTransaction()
        } catch (e: Exception) {
            inTransaction.remove(name)
            runCatching { if (db.inTransaction()) db.endTransaction() }
            return error(DATABASE_ERR, "unable to commit transaction (${message(e)})")
        }
        inTransaction.remove(name)
        return ok()
    }

    private fun rollback(name: String, db: SqlDb): JSONObject {
        if (inTransaction.remove(name) != null) runCatching { db.endTransaction() }
        return ok()
    }

    /** Runs the batch in order, stopping at the first failure: the page decides what follows it. */
    private fun exec(name: String, db: SqlDb, statements: JSONArray): JSONObject {
        val readOnly = inTransaction[name] ?: return error(DATABASE_ERR, "no transaction is open")
        val results = JSONArray()
        for (i in 0 until statements.length()) {
            val s = statements.getJSONObject(i)
            val r = try {
                statement(db, s.getString("sql"), s.optJSONArray("args") ?: JSONArray(), readOnly)
            } catch (e: SqlFailure) {
                results.put(error(e.code, e.message ?: ""))
                break
            }
            results.put(r)
        }
        return JSONObject().put("results", results)
    }

    private fun statement(db: SqlDb, sql: String, args: JSONArray, readOnly: Boolean): JSONObject {
        val type = statementType(sql)
        // WebKit's authorizer refused these (DatabaseAuthorizer::allowAttach): a page could
        // otherwise open any file Lunacy can, its cookies among them.
        if (type == "ATT" || attaches(sql)) throw SqlFailure(SYNTAX_ERR, "not authorized")
        val query = type == "SEL" || LEADS_WITH_ROWS.containsMatchIn(sql)
        if (readOnly && !query) throw SqlFailure(SYNTAX_ERR, "not authorized")
        val expected = parameterCount(sql)
        if (expected >= 0 && expected != args.length()) {
            throw SqlFailure(SYNTAX_ERR, "number of '?'s in statement string does not match argument count")
        }
        return if (query) select(db, sql, args) else update(db, sql, args)
    }

    /** A page's arguments as SQLite takes them: null, an integer, a real, or text. */
    private fun values(args: JSONArray): List<Any?> = (0 until args.length()).map { i ->
        when (val v = args.opt(i)) {
            null, JSONObject.NULL -> null
            is Int -> v.toLong()
            is Long -> v
            is Number -> v.toDouble()
            else -> v.toString()
        }
    }

    private fun select(db: SqlDb, sql: String, args: JSONArray): JSONObject {
        val t = try { db.select(sql, values(args)) } catch (e: SqlError) { throw SqlFailure(code(e), e.message ?: "") }
        val columns = JSONArray()
        for (col in t.columns) columns.put(col)
        val rows = JSONArray()
        for (r in t.rows) rows.put(JSONArray().also { row -> for (v in r) row.put(v ?: JSONObject.NULL) })
        return JSONObject().put("columns", columns).put("rows", rows).put("rowsAffected", 0).put("insertId", JSONObject.NULL)
    }

    private fun update(db: SqlDb, sql: String, args: JSONArray): JSONObject {
        val c = try { db.update(sql, values(args)) } catch (e: SqlError) { throw SqlFailure(code(e), e.message ?: "") }
        // WebKit set insertId whenever the statement changed rows: the last rowid inserted.
        return JSONObject().put("columns", JSONArray()).put("rows", JSONArray()).put("rowsAffected", c.rowsAffected)
            .put("insertId", c.insertId ?: JSONObject.NULL)
    }

    /** WebKit's SQLStatement codes: failing to prepare is a syntax error, then the constraint, the full disk, the rest. */
    private fun code(e: SqlError) = when (e.kind) {
        SqlError.Kind.PREPARE -> SYNTAX_ERR
        SqlError.Kind.CONSTRAINT -> CONSTRAINT_ERR
        SqlError.Kind.FULL -> QUOTA_ERR
        SqlError.Kind.OTHER -> DATABASE_ERR
    }

    /** An exception's text for a transaction that wouldn't begin or commit. */
    private fun message(e: Exception): String = e.message ?: e.toString()

    private fun error(code: Int, message: String) = JSONObject().put("error", JSONObject().put("code", code).put("message", message))
    private fun ok() = JSONObject().put("ok", true)

    companion object {
        const val UNKNOWN_ERR = 0
        const val DATABASE_ERR = 1
        const val VERSION_ERR = 2
        const val QUOTA_ERR = 4
        const val SYNTAX_ERR = 5
        const val CONSTRAINT_ERR = 6

        /** WebKit's own version table, which Chromium kept, so a migrated file carries its version. */
        const val INFO_TABLE = "__WebKitDatabaseInfo__"
        const val VERSION_KEY = "WebKitDatabaseVersionKey"

        /**
         * A statement's kind by its first three letters, as android.database.DatabaseUtils
         * told them apart on Android 5 ("SEL", "ATT", ...): what decides which statements return
         * rows and which a page may not run.
         */
        internal fun statementType(sql: String): String {
            val t = sql.trim()
            return if (t.length < 3) "" else t.substring(0, 3).uppercase(java.util.Locale.ROOT)
        }

        /** Statements that return rows but which getSqlStatementType doesn't call SELECT. */
        private val LEADS_WITH_ROWS = Regex("""^\s*(WITH|PRAGMA|EXPLAIN)\b""", RegexOption.IGNORE_CASE)
        private val ATTACH = Regex("""^(?:\s|--[^\n]*(?:\n|$)|/\*.*?\*/)*(ATTACH|DETACH)\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        /** Whether [sql] attaches or detaches a database, after any comments: refused, so a page's database can't reach another file. */
        internal fun attaches(sql: String) = ATTACH.containsMatchIn(sql)

        /**
         * How many `?` placeholders a statement has, outside its literals and comments, so
         * a statement given the wrong number of arguments fails as WebKit's did (SQLite
         * itself would silently bind NULL for the missing ones). -1 when it uses named or
         * numbered parameters, which are left to SQLite.
         */
        fun parameterCount(sql: String): Int {
            var n = 0
            var i = 0
            val len = sql.length
            while (i < len) {
                val ch = sql[i]
                when {
                    ch == '\'' || ch == '"' || ch == '`' -> {
                        i++
                        while (i < len) {
                            if (sql[i] == ch) { if (i + 1 < len && sql[i + 1] == ch) i++ else break }
                            i++
                        }
                    }
                    ch == '[' -> { while (i < len && sql[i] != ']') i++ }
                    ch == '-' && i + 1 < len && sql[i + 1] == '-' -> { while (i < len && sql[i] != '\n') i++ }
                    ch == '/' && i + 1 < len && sql[i + 1] == '*' -> {
                        i += 2
                        while (i + 1 < len && !(sql[i] == '*' && sql[i + 1] == '/')) i++
                        i++
                    }
                    ch == '?' -> {
                        if (i + 1 < len && sql[i + 1].isDigit()) return -1
                        n++
                    }
                    (ch == ':' || ch == '@' || ch == '$') && i + 1 < len && (sql[i + 1].isLetter() || sql[i + 1] == '_') -> return -1
                }
                i++
            }
            return n
        }
    }
}
