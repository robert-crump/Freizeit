package com.example.freizeit.util

import com.example.freizeit.data.entity.Visit
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** One section of the check-in history list: a bucket label and the visits that fall in it,
 *  in the same relative order they were passed in. */
data class VisitSection(val label: String, val visits: List<Visit>)

/**
 * Groups visits into calendar-based sections — Today, This week, Last week, This month,
 * Last month, then one bucket per older month ("March 2026") — in that priority order, using
 * [locale]'s week start rather than a rolling 7-day window. Empty buckets are omitted.
 * [visits] is expected pre-sorted newest-first (as [com.example.freizeit.data.dao.VisitDao.observeAll]
 * returns it): since the bucket boundaries are non-overlapping, chronologically decreasing
 * intervals, grouping a newest-first list in encounter order naturally yields sections already
 * in priority order without a separate sort step.
 */
fun bucketVisits(
    visits: List<Visit>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): List<VisitSection> {
    val weekFields = WeekFields.of(locale)
    val thisWeekStart = today.with(weekFields.dayOfWeek(), 1L)
    val lastWeekStart = thisWeekStart.minusWeeks(1)
    val thisMonthStart = today.withDayOfMonth(1)
    val lastMonthStart = thisMonthStart.minusMonths(1)
    val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun labelFor(visitDate: LocalDate): String = when {
        visitDate.isEqual(today) -> "Today"
        !visitDate.isBefore(thisWeekStart) -> "This week"
        !visitDate.isBefore(lastWeekStart) -> "Last week"
        !visitDate.isBefore(thisMonthStart) -> "This month"
        !visitDate.isBefore(lastMonthStart) -> "Last month"
        else -> YearMonth.from(visitDate).format(monthYearFormatter)
    }

    val sections = LinkedHashMap<String, MutableList<Visit>>()
    for (visit in visits) {
        val visitDate = Instant.ofEpochMilli(visit.visitedAt).atZone(zone).toLocalDate()
        sections.getOrPut(labelFor(visitDate)) { mutableListOf() }.add(visit)
    }
    return sections.map { (label, list) -> VisitSection(label, list) }
}
