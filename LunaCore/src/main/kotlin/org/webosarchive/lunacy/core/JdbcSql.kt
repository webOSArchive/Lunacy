package org.webosarchive.lunacy.core

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * [Sql] over JDBC: Luna DE's SQLite (the xerial driver, which the host brings), and what
 * LunaCore's own tests run db8 on. Nothing here names the driver: java.sql finds it. Android
 * never uses this; its [Sql] is android.database.sqlite.
 *
 * Transactions nest as SQLiteDatabase's do: the outermost one commits only if it and every
 * transaction inside it were marked successful, and rolls everything back otherwise.
 */
object JdbcSql : Sql {
    override fun open(file: File?): SqlDb {
        file?.parentFile?.mkdirs()
        return JdbcDb(DriverManager.getConnection("jdbc:sqlite:" + (file?.path ?: ":memory:")))
    }
}

private class JdbcDb(private val c: Connection) : SqlDb {
    private var depth = 0
    private var allSuccessful = true
    /** Whether each open transaction, outermost first, has been marked successful. */
    private val marked = ArrayList<Boolean>()

    override fun exec(sql: String, vararg args: Any?) {
        c.prepareStatement(sql).use { st -> bind(st, args); st.execute() }
    }

    override fun <T> query(sql: String, args: Array<String>?, read: (SqlRows) -> T): T =
        c.prepareStatement(sql).use { st ->
            if (args != null) bind(st, args)
            st.executeQuery().use { rs -> read(Rows(rs)) }
        }

    private fun bind(st: PreparedStatement, args: Array<out Any?>) {
        for ((i, a) in args.withIndex()) when (a) {
            null -> st.setNull(i + 1, java.sql.Types.NULL)
            is String -> st.setString(i + 1, a)
            is Double, is Float -> st.setDouble(i + 1, (a as Number).toDouble())
            is Number -> st.setLong(i + 1, a.toLong())
            is Boolean -> st.setLong(i + 1, if (a) 1 else 0)
            is ByteArray -> st.setBytes(i + 1, a)
            else -> st.setString(i + 1, a.toString())
        }
    }

    override fun beginTransaction() {
        if (depth == 0) { c.autoCommit = false; allSuccessful = true }
        depth++
        marked += false
    }

    override fun setTransactionSuccessful() {
        check(depth > 0) { "no transaction" }
        marked[marked.size - 1] = true
    }

    override fun endTransaction() {
        check(depth > 0) { "no transaction" }
        if (!marked.removeAt(marked.size - 1)) allSuccessful = false
        depth--
        if (depth == 0) {
            try { if (allSuccessful) c.commit() else c.rollback() } finally { c.autoCommit = true }
        }
    }

    override fun inTransaction() = depth > 0
    override fun close() { c.close() }

    override fun select(sql: String, args: List<Any?>): SqlTable {
        val st = try { c.prepareStatement(sql) } catch (e: java.sql.SQLException) { throw SqlError(SqlError.Kind.PREPARE, message(e)) }
        try {
            st.use {
                bind(st, args.toTypedArray())
                st.executeQuery().use { rs ->
                    val meta = rs.metaData
                    val columns = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                    val rows = ArrayList<List<Any?>>()
                    while (rs.next()) rows += (1..columns.size).map { i ->
                        when (val v = rs.getObject(i)) {
                            null -> null
                            is Long -> v
                            is Int, is Short, is Byte -> (v as Number).toLong()
                            is Number -> v.toDouble()
                            is ByteArray -> String(v, Charsets.ISO_8859_1)
                            else -> v.toString()
                        }
                    }
                    return SqlTable(columns, rows)
                }
            }
        } catch (e: java.sql.SQLException) { throw SqlError(kind(e), message(e)) }
    }

    override fun update(sql: String, args: List<Any?>): SqlChange {
        val st = try { c.prepareStatement(sql) } catch (e: java.sql.SQLException) { throw SqlError(SqlError.Kind.PREPARE, message(e)) }
        val changed = try {
            st.use { bind(st, args.toTypedArray()); st.executeUpdate() }
        } catch (e: java.sql.SQLException) { throw SqlError(kind(e), message(e)) }
        val id = if (changed > 0) query("SELECT last_insert_rowid()") { it.next(); it.getLong(0) } else null
        return SqlChange(changed, id)
    }

    /** SQLite's primary result code: 19 is a constraint, 13 a full disk. */
    private fun kind(e: java.sql.SQLException) = when (e.errorCode and 0xff) {
        19 -> SqlError.Kind.CONSTRAINT
        13 -> SqlError.Kind.FULL
        else -> SqlError.Kind.OTHER
    }

    /** SQLite's own text out of the driver's "[SQLITE_ERROR] SQL error or missing database (no such table: t)". */
    private fun message(e: java.sql.SQLException): String {
        val m = e.message ?: return e.toString()
        val wrapped = Regex("""^\[SQLITE_[A-Z_]+]\s+.*?\((.*)\)\s*$""", RegexOption.DOT_MATCHES_ALL).find(m)
        return wrapped?.groupValues?.get(1) ?: m
    }

    private class Rows(private val rs: ResultSet) : SqlRows {
        override fun next() = rs.next()
        override fun getString(column: Int): String? = rs.getString(column + 1)
        override fun getLong(column: Int) = rs.getLong(column + 1)
    }
}
