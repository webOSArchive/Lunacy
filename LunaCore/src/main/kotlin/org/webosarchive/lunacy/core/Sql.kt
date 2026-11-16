package org.webosarchive.lunacy.core

import java.io.File

/**
 * SQLite, as LunaCore's storage asks for it: db8's tables and WebSQL's databases. Android answers
 * with android.database.sqlite, the desktop with SQLite over JDBC (Docs/luna-de.md, "LunaCore").
 * Only what db8 used of SQLiteDatabase is here, with the same meaning: text and integer
 * arguments, rows read in order, and transactions that nest, the outermost one committing only
 * when every one inside it was marked successful.
 */
fun interface Sql {
    /** The database in [file], made if it isn't there; in memory when [file] is null. */
    fun open(file: File?): SqlDb
}

interface SqlDb {
    /** One statement that returns nothing; [args] are strings, numbers or null. */
    fun exec(sql: String, vararg args: Any?)

    /** The rows of a SELECT, read by [read] before the statement is closed. */
    fun <T> query(sql: String, args: Array<String>? = null, read: (SqlRows) -> T): T

    fun beginTransaction()
    fun setTransactionSuccessful()
    fun endTransaction()
    fun inTransaction(): Boolean
    fun close()

    // ---- for WebSQL, whose statements are a page's own and whose values keep their types ----

    /**
     * A statement that returns rows. [args] are null, Long, Double or String. Each value comes
     * back as SQLite holds it: null, a Long, a Double, a String, or a blob as a String of its
     * bytes (ISO 8859-1). Throws [SqlError].
     */
    fun select(sql: String, args: List<Any?>): SqlTable

    /** A statement that changes rows: how many, and the last row id inserted if it changed any. Throws [SqlError]. */
    fun update(sql: String, args: List<Any?>): SqlChange
}

class SqlTable(val columns: List<String>, val rows: List<List<Any?>>)
class SqlChange(val rowsAffected: Int, val insertId: Long?)

/**
 * A statement that failed, with SQLite's own message (what a device gave a page: "no such table:
 * nosuchtable"), whatever each platform's library wraps it in, and where it failed: preparing
 * (a syntax error to WebSQL), on a constraint, on a full disk, or otherwise while running.
 */
class SqlError(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { PREPARE, CONSTRAINT, FULL, OTHER }
}

/** A query's rows: before the first until [next] says there is one. Columns count from 0. */
interface SqlRows {
    fun next(): Boolean
    fun getString(column: Int): String?
    fun getLong(column: Int): Long
}

/**
 * Sorting text as db8's collated indexes do. Android's java.text.Collator is ICU; the JDK's own
 * isn't, and a test bench that orders results differently from a device misleads, so the desktop
 * answers with ICU4J.
 */
fun interface Collation {
    /** A comparator of the default locale at [strength]: "primary", "secondary" or "tertiary". */
    fun comparator(strength: String): Comparator<String>
}
