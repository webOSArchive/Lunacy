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
    fun close()
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
