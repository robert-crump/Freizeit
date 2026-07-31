package com.example.freizeit.util

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastVisitTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 31)
    private val now = today.atTime(12, 0)

    private fun millisDaysAgo(days: Long): Long =
        today.minusDays(days).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun millisMonthsAgo(months: Long): Long =
        today.minusMonths(months).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun millisYearsAgo(years: Long): Long =
        today.minusYears(years).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `null lastVisitedAt means never visited`() {
        assertNull(LastVisit.format(null, now, zone))
    }

    @Test
    fun `same calendar day is Today even shortly after midnight`() {
        val earlierToday = today.atTime(0, 1).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Today", LastVisit.format(earlierToday, now, zone))
    }

    @Test
    fun `a visit just before midnight yesterday is already 1 day ago`() {
        val lateYesterday = today.minusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        assertEquals("1 day ago", LastVisit.format(lateYesterday, now, zone))
    }

    @Test
    fun `two to six days ago uses the plural exact-day count`() {
        assertEquals("2 days ago", LastVisit.format(millisDaysAgo(2), now, zone))
        assertEquals("6 days ago", LastVisit.format(millisDaysAgo(6), now, zone))
    }

    @Test
    fun `seven days ago is the last exact-day bucket`() {
        assertEquals("7 days ago", LastVisit.format(millisDaysAgo(7), now, zone))
    }

    @Test
    fun `eight days ago rolls into the first week bucket`() {
        assertEquals(">1 week ago", LastVisit.format(millisDaysAgo(8), now, zone))
    }

    @Test
    fun `thirteen days ago is still the first week bucket`() {
        assertEquals(">1 week ago", LastVisit.format(millisDaysAgo(13), now, zone))
    }

    @Test
    fun `fourteen days ago rolls into the second week bucket`() {
        assertEquals(">2 weeks ago", LastVisit.format(millisDaysAgo(14), now, zone))
    }

    @Test
    fun `twenty-nine days ago is still a week bucket, not yet a month`() {
        assertEquals(">4 weeks ago", LastVisit.format(millisDaysAgo(29), now, zone))
    }

    @Test
    fun `a full calendar month rolls into the singular month bucket`() {
        assertEquals("1 month ago", LastVisit.format(millisMonthsAgo(1), now, zone))
    }

    @Test
    fun `several months ago uses the plural month bucket`() {
        assertEquals("6 months ago", LastVisit.format(millisMonthsAgo(6), now, zone))
    }

    @Test
    fun `eleven months ago is still a month bucket, not yet a year`() {
        assertEquals("11 months ago", LastVisit.format(millisMonthsAgo(11), now, zone))
    }

    @Test
    fun `a full calendar year rolls into the year bucket`() {
        assertEquals(">1 year ago", LastVisit.format(millisYearsAgo(1), now, zone))
    }

    @Test
    fun `several years ago uses the plural year bucket`() {
        assertEquals(">2 years ago", LastVisit.format(millisYearsAgo(2), now, zone))
    }

    @Test
    fun `a future timestamp from clock skew still reads as Today`() {
        val tomorrow = today.plusDays(1).atTime(0, 1).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Today", LastVisit.format(tomorrow, now, zone))
    }
}
