package com.example.freizeit.util

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pure weekday/weekend fixed-time schedule for the widget's scheduled refresh (issue #55):
 * weekdays at [WEEKDAY_TRIGGER_TIME], weekends at [WEEKEND_TRIGGER_TIME] — once per day each, not
 * a repeating interval. Kept separate from
 * [com.example.freizeit.ui.widget.WidgetRefreshScheduler] so the weekday/weekend/boundary
 * arithmetic is unit-testable without WorkManager or Robolectric.
 */
private val WEEKDAY_TRIGGER_TIME: LocalTime = LocalTime.of(12, 0)
private val WEEKEND_TRIGGER_TIME: LocalTime = LocalTime.of(8, 0)

private fun isWeekend(dayOfWeek: DayOfWeek): Boolean =
    dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

/**
 * The next trigger strictly after [from], in [from]'s own zone. Walks forward day by day (a week
 * is always enough to find one) rather than special-casing "today vs. tomorrow" — the
 * weekday->weekend and weekend->weekday boundaries fall out of the same per-day rule as any other
 * day, which is what makes them boundary-safe (the issue's "no duplicate/missed firings across
 * the ... boundaries" AC): whichever moment this is called from — right after firing, at app
 * start, or after a reboot — always yields the one correct next occurrence.
 *
 * Strictly-after (not on-or-after) so calling this again with `from` equal to a trigger instant
 * (e.g. the worker rescheduling right at its own fire time) lands on the *next* day's trigger
 * rather than repeating the same instant.
 */
fun nextWidgetRefreshTrigger(from: ZonedDateTime): ZonedDateTime {
    for (daysAhead in 0..7) {
        val date = from.toLocalDate().plusDays(daysAhead.toLong())
        val triggerTime = if (isWeekend(date.dayOfWeek)) WEEKEND_TRIGGER_TIME else WEEKDAY_TRIGGER_TIME
        val candidate = ZonedDateTime.of(date, triggerTime, from.zone)
        if (candidate.isAfter(from)) return candidate
    }
    // Unreachable: every 7-day window contains at least one weekday and one weekend trigger.
    error("No widget refresh trigger found within a week of $from")
}

/** [nextWidgetRefreshTrigger] expressed as a WorkManager-ready delay. */
fun millisUntilNextWidgetRefresh(from: ZonedDateTime): Long =
    Duration.between(from, nextWidgetRefreshTrigger(from)).toMillis()
