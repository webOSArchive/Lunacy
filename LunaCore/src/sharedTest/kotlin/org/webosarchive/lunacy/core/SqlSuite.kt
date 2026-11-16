package org.webosarchive.lunacy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * What LunaCore's storage counts on from [Sql], held against both implementations: Android's
 * (AndroidLuna's SqlTest, under Robolectric) and JDBC's (LunaCore's SqlJdbcTest). db8 keeps
 * people's mail, contacts and calendars through exactly these calls.
 */
abstract class SqlSuite {
    abstract fun install()
    private lateinit var db: SqlDb

    @Before fun open() {
        install()
        db = Platform.sql.open(null)
        db.exec("CREATE TABLE t (k TEXT PRIMARY KEY, v INTEGER, s TEXT)")
    }

    private fun count() = db.query("SELECT COUNT(*) FROM t") { it.next(); it.getLong(0) }

    @Test fun argumentsKeepTheirTypes() {
        db.exec("INSERT INTO t (k, v, s) VALUES (?, ?, ?)", "a", 9_000_000_000L, null)
        db.exec("INSERT INTO t (k, v, s) VALUES (?, ?, ?)", "b", 7, "text")
        db.query("SELECT k, v, s, typeof(v) FROM t ORDER BY k") { r ->
            assertEquals(true, r.next())
            assertEquals("a", r.getString(0)); assertEquals(9_000_000_000L, r.getLong(1)); assertNull(r.getString(2)); assertEquals("integer", r.getString(3))
            assertEquals(true, r.next())
            assertEquals(7L, r.getLong(1)); assertEquals("text", r.getString(2)); assertEquals("integer", r.getString(3))
            assertEquals(false, r.next())
        }
        assertEquals(1L, db.query("SELECT COUNT(*) FROM t WHERE v <= ?", arrayOf("7")) { it.next(); it.getLong(0) })
    }

    @Test fun insertOrReplaceReplaces() {
        db.exec("INSERT OR REPLACE INTO t (k, v) VALUES (?, ?)", "a", 1)
        db.exec("INSERT OR REPLACE INTO t (k, v) VALUES (?, ?)", "a", 2)
        assertEquals(1L, count())
        assertEquals(2L, db.query("SELECT v FROM t WHERE k = ?", arrayOf("a")) { it.next(); it.getLong(0) })
    }

    @Test fun aTransactionCommitsOnlyWhenMarked() {
        db.beginTransaction()
        try { db.exec("INSERT INTO t (k) VALUES (?)", "kept"); db.setTransactionSuccessful() } finally { db.endTransaction() }
        db.beginTransaction()
        try { db.exec("INSERT INTO t (k) VALUES (?)", "lost") } finally { db.endTransaction() }
        assertEquals(1L, count())
    }

    @Test fun anInnerFailureRollsBackTheOuter() {
        db.beginTransaction()
        try {
            db.exec("INSERT INTO t (k) VALUES (?)", "outer")
            db.beginTransaction()
            try { db.exec("INSERT INTO t (k) VALUES (?)", "inner") } finally { db.endTransaction() }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        assertEquals(0L, count())
        // And both marked: both kept, and the database takes statements outside a transaction again.
        db.beginTransaction()
        try {
            db.exec("INSERT INTO t (k) VALUES (?)", "outer")
            db.beginTransaction()
            try { db.exec("INSERT INTO t (k) VALUES (?)", "inner"); db.setTransactionSuccessful() } finally { db.endTransaction() }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        db.exec("INSERT INTO t (k) VALUES (?)", "after")
        assertEquals(3L, count())
    }
}
