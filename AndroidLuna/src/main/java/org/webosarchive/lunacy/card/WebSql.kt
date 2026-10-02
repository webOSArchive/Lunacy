package org.webosarchive.lunacy.card

import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteProgram
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
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
 * Storage is one SQLite file per app and database name, under `files/websql/<app id>/`, so
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
    private val context: Context,
    private val appId: String,
    private val deliver: (Int) -> Unit,
) {
    private val worker = Executors.newSingleThreadExecutor()
    // Typed as Map: ConcurrentHashMap.keySet() compiles to a KeySetView call Android 5 lacks.
    private val results: MutableMap<Int, String> = ConcurrentHashMap()
    /** Open databases by name, and the ones with a transaction open. Worker thread only. */
    private val open = HashMap<String, SQLiteDatabase>()
    private val inTransaction = HashMap<String, Boolean>()  // name -> readOnly
    /** Which page's requests these are; a new page's start over (see NetShim.page). */
    @Volatile private var page = 0

    /** A statement's failure, with its SQLError code. */
    private class SqlFailure(val code: Int, message: String) : Exception(message)

    /**
     * openDatabase: opens (creating, or migrating, as needed) and answers with the stored
     * version, `{"version": v, "created": bool}`, or `{"mismatch": stored}` when the page
     * asked for a version the database isn't at, or `{"error": text}`. Synchronous for the
     * page, which needs the answer to return from openDatabase; the work itself runs on the
     * worker so that every use of a connection is from one thread.
     */
    fun open(name: String, version: String, host: String, hasCreationCallback: Boolean): String =
        try {
            worker.submit(Callable { openOn(name, version, host, hasCreationCallback) }).get()
        } catch (e: Exception) {
            JSONObject().put("error", e.cause?.message ?: e.message ?: "unable to open database").toString()
        }

    private fun openOn(name: String, version: String, host: String, hasCreationCallback: Boolean): String {
        val db = open[name] ?: openFile(name, host).also { open[name] = it }
        db.execSQL("CREATE TABLE IF NOT EXISTS $INFO_TABLE (key TEXT NOT NULL ON CONFLICT FAIL UNIQUE ON CONFLICT REPLACE, value TEXT NOT NULL ON CONFLICT FAIL)")
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

    private fun openFile(name: String, host: String): SQLiteDatabase {
        val dir = File(context.filesDir, "websql/$appId").also { it.mkdirs() }
        val file = File(dir, Base64.encodeToString(name.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) + ".db")
        if (!file.exists()) migrate(file, host, name)
        return SQLiteDatabase.openOrCreateDatabase(file, null)
    }

    /**
     * A database the WebView stored while it still had WebSQL: Chromium kept an index,
     * `Databases.db`, mapping an origin (`https_host_0`) and a name to a numbered file in
     * that origin's folder. Copied in whole, version table and all, so an app that kept its
     * data in the WebView's WebSQL finds it after a WebView update takes WebSQL away.
     */
    private fun migrate(target: File, host: String, name: String) {
        val data = context.filesDir.parentFile ?: return
        val origin = "https_${host}_0"
        for (dir in listOf("app_webview/Default/databases", "app_webview/databases", "app_websql/databases")) {
            val base = File(data, dir)
            val index = File(base, "Databases.db")
            if (!index.isFile) continue
            try {
                SQLiteDatabase.openDatabase(index.path, null, SQLiteDatabase.OPEN_READONLY).use { idx ->
                    idx.rawQuery("SELECT id FROM Databases WHERE origin = ? AND name = ?", arrayOf(origin, name)).use { c ->
                        if (!c.moveToFirst()) return@use
                        val source = File(base, "$origin/${c.getLong(0)}")
                        if (!source.isFile) return@use
                        source.copyTo(target)
                        Log.i(AppServer.TAG, "websql [$appId] migrated \"$name\" from $dir (${source.length()} bytes)")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "websql [$appId] migrating \"$name\" from $dir: $e")
            }
        }
    }

    private fun readVersion(db: SQLiteDatabase): String? =
        db.rawQuery("SELECT value FROM $INFO_TABLE WHERE key = ?", arrayOf(VERSION_KEY)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun writeVersion(db: SQLiteDatabase, version: String) =
        db.execSQL("INSERT INTO $INFO_TABLE (key, value) VALUES (?, ?)", arrayOf(VERSION_KEY, version))

    /** Queues a request; deliver(id) is called when result(id) has its answer. */
    fun request(id: Int, request: String) {
        val from = page
        worker.execute {
            if (from != page) return@execute
            val r = try { handle(JSONObject(request)) } catch (e: Exception) { error(UNKNOWN_ERR, e.message ?: e.toString()) }
            if (from != page) return@execute
            results[id] = r.toString()
            deliver(id)
        }
    }

    fun result(id: Int): String = results.remove(id) ?: error(UNKNOWN_ERR, "no result").toString()

    /** A new page in the window: its transactions are rolled back, its answers dropped. */
    fun reset() {
        page++
        results.clear()
        worker.execute { rollbackAll() }
    }

    /** The window is going away. */
    fun close() {
        page++
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

    private fun begin(name: String, db: SQLiteDatabase, readOnly: Boolean, oldVersion: String?): JSONObject {
        if (inTransaction.containsKey(name)) rollback(name, db)
        try {
            db.beginTransactionNonExclusive()
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

    private fun commit(name: String, db: SQLiteDatabase, newVersion: String?): JSONObject {
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

    private fun rollback(name: String, db: SQLiteDatabase): JSONObject {
        if (inTransaction.remove(name) != null) runCatching { db.endTransaction() }
        return ok()
    }

    /** Runs the batch in order, stopping at the first failure: the page decides what follows it. */
    private fun exec(name: String, db: SQLiteDatabase, statements: JSONArray): JSONObject {
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

    private fun statement(db: SQLiteDatabase, sql: String, args: JSONArray, readOnly: Boolean): JSONObject {
        val type = DatabaseUtils.getSqlStatementType(sql)
        val query = type == DatabaseUtils.STATEMENT_SELECT || LEADS_WITH_ROWS.containsMatchIn(sql)
        if (readOnly && !query) throw SqlFailure(SYNTAX_ERR, "not authorized")
        val expected = parameterCount(sql)
        if (expected >= 0 && expected != args.length()) {
            throw SqlFailure(SYNTAX_ERR, "number of '?'s in statement string does not match argument count")
        }
        return if (query) select(db, sql, args) else update(db, sql, args)
    }

    private fun select(db: SQLiteDatabase, sql: String, args: JSONArray): JSONObject {
        // Prepared here (SQLiteQuery compiles as it is made), bound typed in the factory.
        val cursor = try {
            db.rawQueryWithFactory({ _, driver, editTable, q -> bind(q, args); SQLiteCursor(driver, editTable, q) }, sql, null, "")
        } catch (e: Exception) {
            throw SqlFailure(SYNTAX_ERR, message(e))
        }
        val rows = JSONArray()
        val columns = JSONArray()
        try {
            cursor.use { c ->
                for (col in c.columnNames) columns.put(col)
                while (c.moveToNext()) {
                    val row = JSONArray()
                    for (i in 0 until c.columnCount) row.put(value(c, i))
                    rows.put(row)
                }
            }
        } catch (e: Exception) {
            throw SqlFailure(stepCode(e), message(e))
        }
        return JSONObject().put("columns", columns).put("rows", rows).put("rowsAffected", 0).put("insertId", JSONObject.NULL)
    }

    private fun update(db: SQLiteDatabase, sql: String, args: JSONArray): JSONObject {
        val st = try { db.compileStatement(sql) } catch (e: Exception) { throw SqlFailure(SYNTAX_ERR, message(e)) }
        val changed = try {
            st.use { bind(it, args); it.executeUpdateDelete() }
        } catch (e: Exception) {
            throw SqlFailure(stepCode(e), message(e))
        }
        // WebKit set insertId whenever the statement changed rows: the last rowid inserted.
        val insertId: Any = if (changed > 0) DatabaseUtils.longForQuery(db, "SELECT last_insert_rowid()", null) else JSONObject.NULL
        return JSONObject().put("columns", JSONArray()).put("rows", JSONArray()).put("rowsAffected", changed).put("insertId", insertId)
    }

    private fun bind(p: SQLiteProgram, args: JSONArray) {
        for (i in 0 until args.length()) {
            val v = args.opt(i)
            when (v) {
                null, JSONObject.NULL -> p.bindNull(i + 1)
                is Int -> p.bindLong(i + 1, v.toLong())
                is Long -> p.bindLong(i + 1, v)
                is Number -> p.bindDouble(i + 1, v.toDouble())
                else -> p.bindString(i + 1, v.toString())
            }
        }
    }

    private fun value(c: Cursor, i: Int): Any = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_BLOB -> String(c.getBlob(i), Charsets.ISO_8859_1)
        else -> c.getString(i)
    }

    private fun stepCode(e: Exception) = when (e) {
        is SQLiteConstraintException -> CONSTRAINT_ERR
        is SQLiteFullException -> QUOTA_ERR
        else -> DATABASE_ERR
    }

    /**
     * SQLite's own message, as the device gave it. Android appends its own "(code 1
     * SQLITE_ERROR): , while compiling: …"; a page read the message against SQLite's text.
     */
    private fun message(e: Exception): String {
        val m = e.message ?: return e.toString()
        return ANDROID_SUFFIX.find(m)?.let { m.substring(0, it.range.first) } ?: m
    }

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

        private val ANDROID_SUFFIX = Regex(""" \(code \d+""")
        /** Statements that return rows but which getSqlStatementType doesn't call SELECT. */
        private val LEADS_WITH_ROWS = Regex("""^\s*(WITH|PRAGMA|EXPLAIN)\b""", RegexOption.IGNORE_CASE)

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
