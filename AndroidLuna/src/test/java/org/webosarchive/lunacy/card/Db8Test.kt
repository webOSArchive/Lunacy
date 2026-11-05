package org.webosarchive.lunacy.card

import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * db8's queries on a real SQLite, through the bus as an app calls them: a kind with indexes,
 * objects put, and find's where, prefix, order, direction and limit, then get, merge and del.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Db8Test {
    private val app = "com.example.dbtest"
    private val kind = "com.example.dbtest.item:1"
    private lateinit var bus: Bus

    /** A call as [app], the reply once db8's worker has answered and the main thread passed it on. */
    private fun call(method: String, params: String): JSONObject {
        var reply: String? = null
        bus.call(app, "palm://com.palm.db/$method", params) { reply = it }
        val until = System.currentTimeMillis() + 5000
        while (reply == null && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return JSONObject(reply ?: error("no reply to $method"))
    }

    private fun names(r: JSONObject): List<String> {
        val a = r.getJSONArray("results")
        return (0 until a.length()).map { a.getJSONObject(it).getString("name") }
    }

    @Before fun setUp() {
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

    @Test fun errorsAsTheTouchPadAnswers() {
        val unknown = call("find", JSONObject().put("query", JSONObject().put("from", "com.example.nope:1")).toString())
        assertFalse(unknown.getBoolean("returnValue")); assertEquals(-3970, unknown.getInt("errorCode"))
        val badType = call("get", JSONObject().put("ids", "x").toString())
        assertEquals(22, badType.getInt("errorCode"))
        assertEquals("invalid parameters: caller='$app' error='invalid type for property 'ids''", badType.getString("errorText"))
    }
}
