package com.example.freizeit.util

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastVisitTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 1)
    private val now = today.atTime(12, 0)

    private fun millis(date: LocalDate, hour: Int = 12, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `null lastVisitedAt means never visited`() {
        assertNull(LastVisit.format(null, now, zone))
    }

    @Test
    fun `same calendar day is Today even shortly after midnight`() {
        assertEquals("Today", LastVisit.format(millis(today, 0, 1), now, zone))
    }

    @Test
    fun `a visit later today is still Today`() {
        assertEquals("Today", LastVisit.format(millis(today, 23, 59), now, zone))
    }

    @Test
    fun `the previous calendar day is Yesterday, even just before midnight`() {
        assertEquals("Yesterday", LastVisit.format(millis(today.minusDays(1), 23, 59), now, zone))
        assertEquals("Yesterday", LastVisit.format(millis(today.minusDays(1), 0, 1), now, zone))
    }

    @Test
    fun `two days ago and older show the date`() {
        assertEquals("29. Sep 2026", LastVisit.format(millis(today.minusDays(2)), now, zone))
        assertEquals("5. Mar 2024", LastVisit.format(millis(LocalDate.of(2024, 3, 5)), now, zone))
    }

    @Test
    fun `day boundaries follow the given zone`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 23:30 UTC on Sep 30 is already Oct 1 in Berlin.
        val visit = LocalDate.of(2026, 9, 30).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Today", LastVisit.format(visit, now, berlin))
    }
}
