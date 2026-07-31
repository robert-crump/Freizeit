package com.example.freizeit.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Formats a place's most recent check-in as a human-readable recency label, coarsening
 * precision the further back the visit was: exact day counts for the first week, then
 * week buckets, then calendar months, then calendar years. Day/week/month/year boundaries
 * are calendar-based (local date), not elapsed-hours math.
 */
object LastVisit {

    /** Null input (never visited) returns null rather than a label. */
    fun format(
        lastVisitedAt: Long?,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        if (lastVisitedAt == null) return null
        val visitDate = Instant.ofEpochMilli(lastVisitedAt).atZone(zone).toLocalDate()
        val today = now.toLocalDate()

        val totalMonths = ChronoUnit.MONTHS.between(visitDate, today)
        val totalDays = ChronoUnit.DAYS.between(visitDate, today)

        return when {
            totalDays <= 0 -> "Today"
            totalMonths < 1 && totalDays <= 7 -> "${plural(totalDays, "day")} ago"
            totalMonths < 1 -> ">${plural(totalDays / 7, "week")} ago"
            totalMonths < 12 -> "${plural(totalMonths, "month")} ago"
            else -> ">${plural(ChronoUnit.YEARS.between(visitDate, today), "year")} ago"
        }
    }

    private fun plural(count: Long, unit: String): String =
        if (count == 1L) "1 $unit" else "$count ${unit}s"
}
