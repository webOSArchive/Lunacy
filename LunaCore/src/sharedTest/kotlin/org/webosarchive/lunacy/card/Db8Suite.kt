package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.webosarchive.lunacy.core.Platform
import java.io.File

/**
 * db8's queries on a real SQLite, through the bus as an app calls them: a kind with indexes,
 * objects put, and find's where, prefix, order, direction and limit, then get, merge and del.
 *
 * One suite for both of LunaCore's SQLites (Docs/luna-de.md, "LunaCore"): AndroidLuna runs it on
 * android.database.sqlite under Robolectric (its Db8Test), LunaCore on SQLite over JDBC, which is
 * Luna DE's (Db8JdbcTest). Each gives its platform ([install]) and a way to let the main thread
 * run what was posted to it ([idleMain]).
 */
abstract class Db8Suite {
    /** LunaCore's platform set, before anything of db8's runs. */
    abstract fun install()
    /** Runs what has been posted to the main thread. */
    abstract fun idleMain()

    private val app = "com.example.dbtest"
    private val kind = "com.example.dbtest.item:1"
    private lateinit var bus: Bus

    /** A call as [app], the reply once db8's worker has answered and the main thread passed it on. */
    private fun call(method: String, params: String): JSONObject {
        var reply: String? = null
        bus.call(app, "palm://com.palm.db/$method", params) { reply = it }
        val until = System.currentTimeMillis() + 5000
        while (reply == null && System.currentTimeMillis() < until) {
            idleMain()
            Thread.sleep(5)
        }
        return JSONObject(reply ?: error("no reply to $method"))
    }

    private fun names(r: JSONObject): List<String> {
        val a = r.getJSONArray("results")
        return (0 until a.length()).map { a.getJSONObject(it).getString("name") }
    }

    @Before fun setUp() {
        install()
        bus = Bus()
        Db8("com.palm.db", null).register(bus)
        val k = call("putKind", JSONObject().put("id", kind).put("owner", app).put("indexes", JSONArray()
            .put(JSONObject().put("name", "name").put("props", JSONArray().put(JSONObject().put("name", "name"))))
            .put(JSONObject().put("name", "size").put("props", JSONArray().put(JSONObject().put("name", "size"))))).toString())
        assertTrue(k.toString(), k.getBoolean("returnValue"))
        val objects = JSONArray()
        for ((n, size) in listOf("apple" to 3, "apricot" to 5, "banana" to 1, "cherry" to 4)) objects.put(JSONObject().put("_kind", kind).put("name", n).put("size", size))
        val p = call("put", JSONObject().put("objects", objects).toString())
        assertTrue(p.toString(), p.getBoolean("returnValue"))
        assertEquals(4, p.getJSONArray("results").length())
    }

    private fun find(query: JSONObject) = call("find", JSONObject().put("query", query.put("from", kind)).toString())

    @Test fun whereAndPrefix() {
        assertEquals(listOf("banana"), names(find(JSONObject().put("where", JSONArray().put(JSONObject().put("prop", "name").put("op", "=").put("val", "banana"))))))
        assertEquals(listOf("apple", "apricot"), names(find(JSONObject().put("where", JSONArray().put(JSONObject().put("prop", "name").put("op", "%").put("val", "ap"))))))
    }

    @Test fun orderDirectionAndLimit() {
        assertEquals(listOf("banana", "apple", "cherry", "apricot"), names(find(JSONObject().put("orderBy", "size"))))
        assertEquals(listOf("apricot", "cherry"), names(find(JSONObject().put("orderBy", "size").put("desc", true).put("limit", 2))))
        assertEquals(listOf("cherry", "apricot"), names(find(JSONObject().put("where", JSONArray().put(JSONObject().put("prop", "size").put("op", ">").put("val", 3))).put("orderBy", "size"))))
    }

    /** An object without the ordered property isn't in its index: a find ordered by it leaves the
     *  object out, one without the order finds it (as measured on the TouchPad, 2026-11-14). */
    @Test fun orderedFindLeavesOutObjectsWithoutTheProperty() {
        val p = call("put", JSONObject().put("objects", JSONArray().put(JSONObject().put("_kind", kind).put("name", "nosize"))).toString())
        assertTrue(p.toString(), p.getBoolean("returnValue"))
        assertEquals(listOf("banana", "apple", "cherry", "apricot"), names(find(JSONObject().put("orderBy", "size"))))
        assertTrue(names(find(JSONObject())).contains("nosize"))
        assertTrue(names(find(JSONObject().put("orderBy", "name"))).contains("nosize"))
    }

    @Test fun getMergeAndDel() {
        val all = find(JSONObject().put("orderBy", "name")).getJSONArray("results")
        val apple = all.getJSONObject(0).getString("_id")
        val m = call("merge", JSONObject().put("objects", JSONArray().put(JSONObject().put("_id", apple).put("size", 9))).toString())
        assertTrue(m.toString(), m.getBoolean("returnValue"))
        val got = call("get", JSONObject().put("ids", JSONArray().put(apple)).toString()).getJSONArray("results").getJSONObject(0)
        assertEquals(9, got.getInt("size")); assertEquals("apple", got.getString("name"))
        assertTrue(call("del", JSONObject().put("ids", JSONArray().put(apple)).toString()).getBoolean("returnValue"))
        assertEquals(listOf("apricot", "banana", "cherry"), names(find(JSONObject().put("orderBy", "name"))))
    }

    /** A merge or put that changes nothing keeps the revision and wakes no watch (MojDb::putObj). */
    @Test fun nothingChangedIsNoUpdate() {
        val apple = names(find(JSONObject())).indexOf("apple").let { find(JSONObject()).getJSONArray("results").getJSONObject(it) }
        val id = apple.getString("_id"); val rev = apple.getLong("_rev")
        var woke = 0
        bus.call(app, "palm://com.palm.db/watch", JSONObject().put("query", JSONObject().put("from", kind)
            .put("where", JSONArray().put(JSONObject().put("prop", "name").put("op", "=").put("val", "apple")))).toString()) { r ->
            if (JSONObject(r).optBoolean("fired")) woke++
        }
        fun revNow() = call("get", JSONObject().put("ids", JSONArray().put(id)).toString()).getJSONArray("results").getJSONObject(0)
        // The same value merged again: nothing.
        val m = call("merge", JSONObject().put("objects", JSONArray().put(JSONObject().put("_id", id).put("size", 3))).toString())
        assertEquals(rev, m.getJSONArray("results").getJSONObject(0).getLong("rev"))
        assertEquals(rev, revNow().getLong("_rev"))
        // A null for a field it doesn't have is a change (db8 keeps the null), the second isn't.
        call("merge", JSONObject().put("query", JSONObject().put("from", kind).put("where", JSONArray()
            .put(JSONObject().put("prop", "name").put("op", "=").put("val", "apple")))).put("props", JSONObject().put("error", JSONObject.NULL)).toString())
        val withNull = revNow()
        assertTrue(withNull.has("error") && withNull.isNull("error"))
        assertTrue(withNull.getLong("_rev") > rev)
        val q = call("merge", JSONObject().put("query", JSONObject().put("from", kind).put("where", JSONArray()
            .put(JSONObject().put("prop", "name").put("op", "=").put("val", "apple")))).put("props", JSONObject().put("error", JSONObject.NULL)).toString())
        assertEquals(1, q.getInt("count"))
        assertEquals(withNull.getLong("_rev"), revNow().getLong("_rev"))
        // The same object put again: nothing.
        val p = call("put", JSONObject().put("objects", JSONArray().put(JSONObject(withNull.toString()))).toString())
        assertEquals(withNull.getLong("_rev"), p.getJSONArray("results").getJSONObject(0).getLong("rev"))
        assertEquals(withNull.getLong("_rev"), revNow().getLong("_rev"))
        // A real change still takes a revision.
        call("merge", JSONObject().put("objects", JSONArray().put(JSONObject().put("_id", id).put("size", 7))).toString())
        assertTrue(revNow().getLong("_rev") > withNull.getLong("_rev"))
        idleMain()
        assertEquals("one watch fire, for the first change", true, woke <= 1)
    }

    @Test fun errorsAsTheTouchPadAnswers() {
        val unknown = call("find", JSONObject().put("query", JSONObject().put("from", "com.example.nope:1")).toString())
        assertFalse(unknown.getBoolean("returnValue")); assertEquals(-3970, unknown.getInt("errorCode"))
        val badType = call("get", JSONObject().put("ids", "x").toString())
        assertEquals(22, badType.getInt("errorCode"))
        assertEquals("invalid parameters: caller='$app' error='invalid type for property 'ids''", badType.getString("errorText"))
    }

    /** Text sorts as ICU sorts it at the index's strength, on both platforms: case and accents
     *  apart at "tertiary", together at "primary". */
    @Test fun collatedOrder() {
        val k = "com.example.dbtest.word:1"
        assertTrue(call("putKind", JSONObject().put("id", k).put("owner", app).put("indexes", JSONArray()
            .put(JSONObject().put("name", "w1").put("props", JSONArray().put(JSONObject().put("name", "word").put("collate", "primary"))))).toString()).getBoolean("returnValue"))
        val objects = JSONArray()
        for (w in listOf("zebra", "Banana", "apple", "\u00e9clair", "cherry", "Apple", "eclair2")) objects.put(JSONObject().put("_kind", k).put("word", w))
        assertTrue(call("put", JSONObject().put("objects", objects).toString()).getBoolean("returnValue"))
        val r = call("find", JSONObject().put("query", JSONObject().put("from", k).put("orderBy", "word")).toString()).getJSONArray("results")
        val words = (0 until r.length()).map { r.getJSONObject(it).getString("word").lowercase() }
        // Upper case doesn't sort before lower, and the accented e sorts with e.
        assertEquals(listOf("apple", "apple", "banana", "cherry", "\u00e9clair", "eclair2", "zebra"), words)
    }

    /** A database file as Lunacy 1.0 wrote it (the same tables, by the same statements) opens
     *  with its kinds, objects, revisions and permissions, and takes new writes. */
    @Test fun opensADatabaseWrittenBefore() {
        val file = File.createTempFile("db8-upgrade", ".sqlite").also { it.delete(); it.deleteOnExit() }
        val raw = Platform.sql.open(file)
        raw.exec("CREATE TABLE IF NOT EXISTS kinds (id TEXT PRIMARY KEY, spec TEXT)")
        raw.exec("CREATE TABLE IF NOT EXISTS objects (id TEXT PRIMARY KEY, kind TEXT, json TEXT)")
        raw.exec("CREATE INDEX IF NOT EXISTS objects_kind ON objects (kind)")
        raw.exec("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v INTEGER)")
        raw.exec("CREATE TABLE IF NOT EXISTS permissions (kind TEXT, caller TEXT, ops TEXT, PRIMARY KEY (kind, caller))")
        raw.exec("CREATE TABLE IF NOT EXISTS revtimes (rev INTEGER PRIMARY KEY, ts INTEGER)")
        val old = "com.example.old:1"
        raw.exec("INSERT INTO kinds (id, spec) VALUES (?, ?)", old, JSONObject().put("id", old).put("owner", "com.example.other")
            .put("indexes", JSONArray().put(JSONObject().put("name", "n").put("props", JSONArray().put(JSONObject().put("name", "n"))))).toString())
        raw.exec("INSERT INTO objects (id, kind, json) VALUES (?, ?, ?)", "++OLD1", old, "{\"_id\":\"++OLD1\",\"_kind\":\"$old\",\"_rev\":41,\"n\":1}")
        raw.exec("INSERT INTO objects (id, kind, json) VALUES (?, ?, ?)", "++OLD2", old, "{\"_id\":\"++OLD2\",\"_kind\":\"$old\",\"_rev\":42,\"n\":2,\"_del\":true}")
        raw.exec("INSERT INTO meta (k, v) VALUES (?, ?)", "rev", 42L)
        raw.exec("INSERT INTO permissions (kind, caller, ops) VALUES (?, ?, ?)", old, app, "{\"read\":\"allow\",\"update\":\"allow\"}")
        raw.close()

        val b = Bus()
        val db = Db8("com.palm.db", file)
        db.register(b)
        fun ask(method: String, params: JSONObject): JSONObject {
            var reply: String? = null
            b.call(app, "palm://com.palm.db/$method", params.toString()) { reply = it }
            val until = System.currentTimeMillis() + 5000
            while (reply == null && System.currentTimeMillis() < until) { idleMain(); Thread.sleep(5) }
            return JSONObject(reply ?: error("no reply to $method"))
        }
        val found = ask("find", JSONObject().put("query", JSONObject().put("from", old)))
        assertTrue(found.toString(), found.getBoolean("returnValue"))
        assertEquals(1, found.getJSONArray("results").length())
        assertEquals("++OLD1", found.getJSONArray("results").getJSONObject(0).getString("_id"))
        // Its permission lets this app change it, and the revision carries on from the file's.
        val m = ask("merge", JSONObject().put("objects", JSONArray().put(JSONObject().put("_id", "++OLD1").put("n", 5))))
        assertTrue(m.toString(), m.getBoolean("returnValue"))
        assertTrue(m.getJSONArray("results").getJSONObject(0).getLong("rev") > 42)
        db.close()
    }
}
