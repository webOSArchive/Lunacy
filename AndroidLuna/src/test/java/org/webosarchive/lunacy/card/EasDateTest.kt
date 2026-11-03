package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Test

/** An EAS "nth weekday of a month" (month, dayOfWeek with 0 = Sunday, week with 5 = the last). */
class EasDateTest {
    private fun day(month: Int, dow: Int, week: Int, year: Int) = SystemService.easDayOfMonth(intArrayOf(month, dow, week), year)

    @Test fun lastSundayOfAMonthThatOverflowsFiveRows() {
        // March 2024 starts on a Friday: its last Sunday, the EU's change, is the 31st.
        assertEquals(31, day(3, 0, 5, 2024))
    }

    @Test fun usRules2026() {
        assertEquals(8, day(3, 0, 2, 2026))   // the second Sunday of March
        assertEquals(1, day(11, 0, 1, 2026))  // the first Sunday of November
    }

    @Test fun lastWhenTheMonthHasOnlyFour() {
        assertEquals(25, day(10, 0, 5, 2026)) // October 2026 has four Sundays
        assertEquals(22, day(2, 1, 5, 2027))  // February 2027 has four Mondays
    }
}
