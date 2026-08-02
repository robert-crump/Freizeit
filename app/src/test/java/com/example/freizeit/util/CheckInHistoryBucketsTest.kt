package com.example.freizeit.util

import com.example.freizeit.data.entity.Visit
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInHistoryBucketsTest {

    private val zone = ZoneId.of("UTC")

    private fun visitOn(date: LocalDate, id: Long = date.toEpochDay()): Visit =
        Visit(
            id = id,
            placeId = "place-$id",
            visitedAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            source = Visit.SOURCE_MANUAL,
            snapshotName = "Place $id",
            snapshotLat = 0.0,
            snapshotLon = 0.0,
            snapshotCategory = "cafe"
        )

    private fun labelsOf(sections: List<VisitSection>) = sections.map { it.label }

    @Test
    fun `today's visit lands in Today, not This week`() {
        val today = LocalDate.of(2026, 7, 15)
        val sections = bucketVisits(listOf(visitOn(today)), today, zone, Locale.UK)
        assertEquals(listOf("Today"), labelsOf(sections))
    }

    @Test
    fun `a visit earlier this week lands in This week`() {
        val today = LocalDate.of(2026, 7, 15) // Wednesday; Mon-start week is Jul13-Jul19
        val visit = visitOn(LocalDate.of(2026, 7, 13)) // Monday, start of this week
        val sections = bucketVisits(listOf(visit), today, zone, Locale.UK)
        assertEquals(listOf("This week"), labelsOf(sections))
    }

    @Test
    fun `a visit in the prior calendar week lands in Last week`() {
        val today = LocalDate.of(2026, 7, 15) // Mon-start last week is Jul6-Jul12
        val visit = visitOn(LocalDate.of(2026, 7, 12))
        val sections = bucketVisits(listOf(visit), today, zone, Locale.UK)
        assertEquals(listOf("Last week"), labelsOf(sections))
    }

    @Test
    fun `week-start locale variation changes which bucket a visit falls into`() {
        val today = LocalDate.of(2026, 7, 15) // Wednesday
        val visit = visitOn(LocalDate.of(2026, 7, 12)) // Sunday

        // Monday-start locale: Jul12 (Sun) is the last day of *last* week (Jul6-Jul12).
        val mondayStart = bucketVisits(listOf(visit), today, zone, Locale.UK)
        assertEquals(listOf("Last week"), labelsOf(mondayStart))

        // Sunday-start locale: Jul12 (Sun) is the first day of *this* week (Jul12-Jul18).
        val sundayStart = bucketVisits(listOf(visit), today, zone, Locale.US)
        assertEquals(listOf("This week"), labelsOf(sundayStart))
    }

    @Test
    fun `an earlier-this-month visit outside this and last week lands in This month`() {
        val today = LocalDate.of(2026, 7, 15)
        val visit = visitOn(LocalDate.of(2026, 7, 3))
        val sections = bucketVisits(listOf(visit), today, zone, Locale.UK)
        assertEquals(listOf("This month"), labelsOf(sections))
    }

    @Test
    fun `a visit in the previous calendar month lands in Last month`() {
        val today = LocalDate.of(2026, 7, 15)
        val visit = visitOn(LocalDate.of(2026, 6, 15))
        val sections = bucketVisits(listOf(visit), today, zone, Locale.UK)
        assertEquals(listOf("Last month"), labelsOf(sections))
    }

    @Test
    fun `a visit two months back lands in an older month-year bucket`() {
        val today = LocalDate.of(2026, 7, 15)
        val visit = visitOn(LocalDate.of(2026, 5, 20))
        val sections = bucketVisits(listOf(visit), today, zone, Locale.US)
        assertEquals(listOf("May 2026"), labelsOf(sections))
    }

    @Test
    fun `month-year label is locale-formatted with full month name and year`() {
        val today = LocalDate.of(2026, 7, 15)
        val visit = visitOn(LocalDate.of(2026, 5, 20))
        val sections = bucketVisits(listOf(visit), today, zone, Locale.GERMANY)
        assertEquals(listOf("Mai 2026"), labelsOf(sections))
    }

    @Test
    fun `year rollover - a December visit is Last month when now is early January`() {
        val today = LocalDate.of(2027, 1, 5)
        val visit = visitOn(LocalDate.of(2026, 12, 15))
        val sections = bucketVisits(listOf(visit), today, zone, Locale.UK)
        assertEquals(listOf("Last month"), labelsOf(sections))
    }

    @Test
    fun `year rollover - an older visit from two months back keeps its own past year in the label`() {
        val today = LocalDate.of(2027, 1, 5)
        val visit = visitOn(LocalDate.of(2026, 11, 15))
        val sections = bucketVisits(listOf(visit), today, zone, Locale.US)
        assertEquals(listOf("November 2026"), labelsOf(sections))
    }

    @Test
    fun `empty buckets are omitted entirely`() {
        val today = LocalDate.of(2026, 7, 15)
        // Only a Today visit and an older-month visit: everything in between must be absent.
        val sections = bucketVisits(
            listOf(visitOn(today, id = 1), visitOn(LocalDate.of(2026, 5, 20), id = 2)),
            today,
            zone,
            Locale.US
        )
        assertEquals(listOf("Today", "May 2026"), labelsOf(sections))
    }

    @Test
    fun `section order follows bucket priority given newest-first input`() {
        val today = LocalDate.of(2026, 7, 15)
        val visits = listOf(
            visitOn(today, id = 1),                       // Today
            visitOn(LocalDate.of(2026, 7, 14), id = 2),    // This week
            visitOn(LocalDate.of(2026, 7, 8), id = 3),     // Last week
            visitOn(LocalDate.of(2026, 7, 2), id = 4),     // This month
            visitOn(LocalDate.of(2026, 6, 10), id = 5),    // Last month
            visitOn(LocalDate.of(2026, 5, 1), id = 6)      // Older
        )
        val sections = bucketVisits(visits, today, zone, Locale.UK)
        assertEquals(
            listOf("Today", "This week", "Last week", "This month", "Last month", "May 2026"),
            labelsOf(sections)
        )
        sections.forEach { assertEquals(1, it.visits.size) }
    }

    @Test
    fun `no visits produces no sections`() {
        val today = LocalDate.of(2026, 7, 15)
        assertEquals(emptyList<VisitSection>(), bucketVisits(emptyList(), today, zone, Locale.UK))
    }
}
