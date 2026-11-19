package org.webosarchive.lunacy.core

/** [SqlSuite] on SQLite over JDBC, Luna DE's. */
class SqlJdbcTest : SqlSuite() {
    override fun install() = TestPlatform.install()
}
