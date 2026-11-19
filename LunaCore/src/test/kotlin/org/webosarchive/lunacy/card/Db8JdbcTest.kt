package org.webosarchive.lunacy.card

import org.webosarchive.lunacy.core.TestPlatform

/** [Db8Suite] on SQLite over JDBC with ICU's collation: db8 as Luna DE runs it. */
class Db8JdbcTest : Db8Suite() {
    override fun install() = TestPlatform.install()
    override fun idleMain() = TestPlatform.scheduler.idle()
}
