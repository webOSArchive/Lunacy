package org.webosarchive.lunacy.card

import org.webosarchive.lunacy.core.TestPlatform

/** [WebSqlSuite] on SQLite over JDBC: WebSQL as Luna DE runs it. */
class WebSqlJdbcTest : WebSqlSuite() {
    override fun install() = TestPlatform.install()
}
