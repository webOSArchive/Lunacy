package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * WebSQL's native half, as a page's polyfill (assets/lunacy/websql.js) drives it: open, a
 * transaction's statements, and the errors a page reads by code and message. One suite for both
 * SQLites, as [Db8Suite] is: Android's under Robolectric (AndroidLuna's WebSqlNativeTest) and
 * JDBC's (LunaCore's WebSqlJdbcTest). The codes and wording are WebKit's SQLStatement's, with
 * SQLite's own message, which is what each platform has to give alike.
 */
abstract class WebSqlSuite {
    abstract fun install()
    private lateinit var sql: WebSql
    private var next = 1

    @Before fun open() {
        install()
        val dir = File.createTempFile("websql", "").also { it.delete(); it.mkdirs(); it.deleteOnExit() }
        sql = WebSql(dir, "com.example.sqltest", { 0 }, { id -> done[id]?.countDown() })
        val o = JSONObject(sql.open("notes", "1.0", false))
        assertEquals("1.0", o.getString("version")); assertTrue(o.getBoolean("created"))
    }

    private val done = java.util.concurrent.ConcurrentHashMap<Int, CountDownLatch>()

    private fun request(body: JSONObject): JSONObject {
        val id = next++
        done[id] = CountDownLatch(1)
        sql.request(id, body.put("db", "notes").toString())
        assertTrue("an answer", done[id]!!.await(5, TimeUnit.SECONDS))
        return JSONObject(sql.result(id))
    }

    private fun begin(readOnly: Boolean = false) = request(JSONObject().put("op", "begin").put("readOnly", readOnly))
    private fun commit() = request(JSONObject().put("op", "commit"))
    private fun exec(vararg statements: Pair<String, List<Any?>>): JSONArray =
        request(JSONObject().put("op", "exec").put("statements", JSONArray(statements.map { (s, a) ->
            JSONObject().put("sql", s).put("args", JSONArray(a.map { it ?: JSONObject.NULL }))
        }))).getJSONArray("results")

    @Test fun rowsKeepTheirTypesAndInsertIds() {
        assertTrue(begin().getBoolean("ok"))
        val r = exec("CREATE TABLE note (id INTEGER PRIMARY KEY, title TEXT UNIQUE, stars REAL, body)" to emptyList(),
            "INSERT INTO note (title, stars, body) VALUES (?, ?, ?)" to listOf("first", 4.5, null),
            "INSERT INTO note (title, stars, body) VALUES (?, ?, ?)" to listOf("second", 3, "text"),
            "SELECT id, title, stars, body FROM note ORDER BY id" to emptyList())
        assertEquals(1, r.getJSONObject(1).getInt("rowsAffected")); assertEquals(1, r.getJSONObject(1).getInt("insertId"))
        assertEquals(2, r.getJSONObject(2).getInt("insertId"))
        val t = r.getJSONObject(3)
        assertEquals("[\"id\",\"title\",\"stars\",\"body\"]", t.getJSONArray("columns").toString())
        val first = t.getJSONArray("rows").getJSONArray(0)
        assertEquals(1, first.getInt(0)); assertEquals("first", first.getString(1)); assertEquals(4.5, first.getDouble(2), 0.0); assertTrue(first.isNull(3))
        assertEquals("text", t.getJSONArray("rows").getJSONArray(1).getString(3))
        assertTrue(commit().getBoolean("ok"))
    }

    @Test fun errorsHaveWebKitsCodesAndSqlitesWords() {
        begin()
        exec("CREATE TABLE note (id INTEGER PRIMARY KEY, title TEXT UNIQUE)" to emptyList(), "INSERT INTO note (title) VALUES (?)" to listOf("one"))
        // No such table: fails to prepare, a syntax error (5), with SQLite's own message.
        val missing = exec("SELECT * FROM nosuchtable" to emptyList()).getJSONObject(0).getJSONObject("error")
        assertEquals(WebSql.SYNTAX_ERR, missing.getInt("code")); assertEquals("no such table: nosuchtable", missing.getString("message"))
        // A constraint (6).
        val twice = exec("INSERT INTO note (title) VALUES (?)" to listOf("one")).getJSONObject(0).getJSONObject("error")
        assertEquals(WebSql.CONSTRAINT_ERR, twice.getInt("code"))
        assertTrue(twice.getString("message"), twice.getString("message").contains("UNIQUE constraint failed: note.title"))
        // The wrong number of arguments, as WebKit refused it.
        val args = exec("INSERT INTO note (title) VALUES (?)" to emptyList()).getJSONObject(0).getJSONObject("error")
        assertEquals(WebSql.SYNTAX_ERR, args.getInt("code"))
        assertEquals("number of '?'s in statement string does not match argument count", args.getString("message"))
        // A batch stops at its first failure.
        assertEquals(1, exec("SELECT * FROM nope" to emptyList(), "SELECT 1" to emptyList()).length())
        commit()
    }

    @Test fun aPageCannotAttachOrWriteInAReadTransaction() {
        begin(); exec("CREATE TABLE note (title)" to emptyList()); commit()
        begin(readOnly = true)
        assertEquals("not authorized", exec("INSERT INTO note VALUES ('x')" to emptyList()).getJSONObject(0).getJSONObject("error").getString("message"))
        assertEquals(0, exec("SELECT * FROM note" to emptyList()).getJSONObject(0).getJSONArray("rows").length())
        commit()
        begin()
        assertEquals("not authorized", exec("ATTACH DATABASE '/tmp/x.db' AS x" to emptyList()).getJSONObject(0).getJSONObject("error").getString("message"))
        commit()
    }

    @Test fun aRolledBackTransactionLeavesNothingAndVersionsHold() {
        begin(); exec("CREATE TABLE note (title)" to emptyList()); commit()
        begin(); exec("INSERT INTO note VALUES ('lost')" to emptyList())
        assertTrue(request(JSONObject().put("op", "rollback")).getBoolean("ok"))
        begin()
        assertEquals(0, exec("SELECT * FROM note" to emptyList()).getJSONObject(0).getJSONArray("rows").length())
        commit()
        // Opened again at another version: a mismatch, naming the stored one. Then changeVersion.
        assertEquals("1.0", JSONObject(sql.open("notes", "2.0", false)).getString("mismatch"))
        assertEquals(WebSql.VERSION_ERR, request(JSONObject().put("op", "begin").put("oldVersion", "0.5")).getJSONObject("error").getInt("code"))
        assertTrue(request(JSONObject().put("op", "begin").put("oldVersion", "1.0")).getBoolean("ok"))
        assertTrue(request(JSONObject().put("op", "commit").put("newVersion", "2.0")).getBoolean("ok"))
        assertEquals("2.0", JSONObject(sql.open("notes", "", false)).getString("version"))
    }
}
