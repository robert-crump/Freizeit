package com.example.freizeit.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats a place's most recent check-in (#73): "Today", "Yesterday", otherwise the date as
 * `d. MMM yyyy` ("29. Sep 2026"). Day boundaries are calendar-based (local date), not
 * elapsed-hours math. English month names, matching the app's English UI.
 */
object LastVisit {

    private val dateFormat = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.ENGLISH)

    /** Null input (never visited) returns null rather than a label. */
    fun format(
        lastVisitedAt: Long?,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        if (lastVisitedAt == null) return null
        val visitDate = Instant.ofEpochMilli(lastVisitedAt).atZone(zone).toLocalDate()
        val today = now.toLocalDate()
        return when {
            !visitDate.isBefore(today) -> "Today"
            visitDate == today.minusDays(1) -> "Yesterday"
            else -> visitDate.format(dateFormat)
        }
    }
}
