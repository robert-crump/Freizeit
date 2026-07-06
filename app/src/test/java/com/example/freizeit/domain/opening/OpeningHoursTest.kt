package com.example.freizeit.domain.opening

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningHoursTest {

    // July 2026: 4th = Saturday, 5th = Sunday, 6th = Monday, 8th = Wednesday
    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 7, day, hour, minute)

    private fun status(hours: String?, day: Int, hour: Int, minute: Int = 0): OpenStatus =
        OpeningHours.statusAt(hours, at(day, hour, minute))

    @Test
    fun `always open`() {
        assertEquals(OpenStatus.OPEN, status("24/7", 4, 3))
        assertEquals(OpenStatus.OPEN, status("24/7", 8, 23, 59))
    }

    @Test
    fun `weekday range with hours`() {
        val h = "Mo-Fr 08:00-18:00"
        assertEquals(OpenStatus.OPEN, status(h, 8, 10))
        assertEquals(OpenStatus.CLOSED, status(h, 8, 19))
        assertEquals(OpenStatus.CLOSED, status(h, 8, 7, 59))
        assertEquals(OpenStatus.CLOSED, status(h, 4, 10)) // Saturday not mentioned
        assertEquals(OpenStatus.OPEN, status(h, 8, 8, 0)) // start inclusive
        assertEquals(OpenStatus.CLOSED, status(h, 8, 18, 0)) // end exclusive
    }

    @Test
    fun `split hours have a lunch gap`() {
        val h = "Mo-Fr 08:00-12:00,14:00-18:00"
        assertEquals(OpenStatus.OPEN, status(h, 8, 9))
        assertEquals(OpenStatus.CLOSED, status(h, 8, 13))
        assertEquals(OpenStatus.OPEN, status(h, 8, 15))
    }

    @Test
    fun `overnight span wraps into the next day`() {
        val h = "Mo-Su 18:00-02:00"
        assertEquals(OpenStatus.OPEN, status(h, 4, 23))
        assertEquals(OpenStatus.OPEN, status(h, 5, 1)) // Sunday 01:00, Saturday's spill
        assertEquals(OpenStatus.CLOSED, status(h, 5, 3))
        assertEquals(OpenStatus.CLOSED, status(h, 5, 12))
    }

    @Test
    fun `later rule overrides earlier for its days`() {
        val h = "Mo-Su 09:00-18:00; Su off"
        assertEquals(OpenStatus.OPEN, status(h, 4, 10))
        assertEquals(OpenStatus.CLOSED, status(h, 5, 10)) // Sunday
    }

    @Test
    fun `day list without times means open all day`() {
        val h = "Sa,Su"
        assertEquals(OpenStatus.OPEN, status(h, 4, 6))
        assertEquals(OpenStatus.CLOSED, status(h, 6, 12)) // Monday
    }

    @Test
    fun `PH rules are skipped, not fatal`() {
        assertEquals(OpenStatus.OPEN, status("Mo-Fr 08:00-18:00; PH off", 8, 10))
        // A value consisting only of PH rules tells us nothing
        assertEquals(OpenStatus.UNKNOWN, status("PH off", 8, 10))
    }

    @Test
    fun `unsupported syntax yields UNKNOWN, never CLOSED`() {
        assertEquals(OpenStatus.UNKNOWN, status("sunrise-sunset", 8, 3))
        assertEquals(OpenStatus.UNKNOWN, status("May-Sep Mo-Fr 08:00-18:00", 8, 10))
        assertEquals(OpenStatus.UNKNOWN, status("Mo-Fr 08:00+", 8, 3))
        assertEquals(OpenStatus.UNKNOWN, status(null, 8, 10))
        assertEquals(OpenStatus.UNKNOWN, status("  ", 8, 10))
    }

    @Test
    fun `times without day spec apply every day`() {
        val h = "08:00-20:00"
        assertEquals(OpenStatus.OPEN, status(h, 5, 12))
        assertEquals(OpenStatus.CLOSED, status(h, 5, 21))
    }
}
