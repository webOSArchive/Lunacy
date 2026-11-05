package org.webosarchive.lunacy.card

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A page's WebSQL may not reach another file by ATTACH (WebSql.attaches). */
class WebSqlTest {
    @Test fun attachIsCaughtBehindCommentsAndCase() {
        assertTrue(WebSql.attaches("ATTACH DATABASE '/data/x.db' AS x"))
        assertTrue(WebSql.attaches("  attach '/x' as y"))
        assertTrue(WebSql.attaches("-- a note\nATTACH '/x' AS y"))
        assertTrue(WebSql.attaches("/* one\n two */ DETACH x"))
    }

    @Test fun theWordElsewhereIsNotAnAttach() {
        assertFalse(WebSql.attaches("SELECT 'ATTACH' FROM t"))
        assertFalse(WebSql.attaches("INSERT INTO attachments VALUES (1)"))
        assertFalse(WebSql.attaches("CREATE TABLE attach_log (a)"))
    }
}
