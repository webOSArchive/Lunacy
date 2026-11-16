package org.webosarchive.lunacy.card

import android.os.Looper
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.webosarchive.lunacy.core.SqlSuite

/** LunaCore's [Db8Suite] on android.database.sqlite, as a device runs db8 (Robolectric's real SQLite). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Db8Test : Db8Suite() {
    override fun install() = org.webosarchive.lunacy.shell.LunacyApplication.install()
    override fun idleMain() = shadowOf(Looper.getMainLooper()).idle()
}

/** LunaCore's [SqlSuite] on android.database.sqlite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SqlTest : SqlSuite() {
    override fun install() = org.webosarchive.lunacy.shell.LunacyApplication.install()
}

/** LunaCore's [WebSqlSuite] on android.database.sqlite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebSqlNativeTest : WebSqlSuite() {
    override fun install() = org.webosarchive.lunacy.shell.LunacyApplication.install()
}
