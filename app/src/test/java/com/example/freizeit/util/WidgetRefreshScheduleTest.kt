package com.example.freizeit.util

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshScheduleTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(iso: String): ZonedDateTime = ZonedDateTime.parse(iso).withZoneSameInstant(zone)

    @Test
    fun `weekday morning triggers the same day at noon`() {
        // Monday
        val from = at("2026-09-14T09:00:00+02:00")
        assertEquals(at("2026-09-14T12:00:00+02:00"), nextWidgetRefreshTrigger(from))
    }

    @Test
    fun `weekday afternoon rolls to the next weekday at noon`() {
        // Tuesday afternoon -> Wednesday noon
        val from = at("2026-09-15T13:00:00+02:00")
        assertEquals(at("2026-09-16T12:00:00+02:00"), nextWidgetRefreshTrigger(from))
    }

    @Test
    fun `exactly at the weekday trigger rolls to the next weekday, not the same instant`() {
        val from = at("2026-09-14T12:00:00+02:00")
        assertEquals(at("2026-09-15T12:00:00+02:00"), nextWidgetRefreshTrigger(from))
    }

    @Test
    fun `friday afternoon rolls over the weekday-to-weekend boundary to saturday 8am`() {
        // Friday -> Saturday
        val from = at("2026-09-18T13:00:00+02:00")
        assertEquals(at("2026-09-19T08:00:00+02:00"), nextWidgetRefreshTrigger(from))
    }

    @Test
    fun `saturday morning before 8am triggers the same day`() {
        val from = at("2026-09-19T06:00:00+02:00")
        assertEquals(at("2026-09-19T08:00:00+02:00"), nextWidgetRefreshTrigger(from))
    }

    @Test
    fun `exactly at the weekend trigger rolls to the next day, not the same instant`() {
        val from = at("2026-09-19T08:00:00+02:00")
        assertEquals(at("2026-09-20T08:00:00+02:00"), nextWidgetRefreshTrigger(from))
    }

    @Test
    fun `saturday after 8am rolls to sunday 8am`() {
        val from = at("2026-09-19T09:00:00+02:00")
        assertEquals(at("2026-09-20T08:00:00+02:00"), nextWidgetRefreshTrigger(from))
    }

    @Test
    fun `sunday afternoon rolls over the weekend-to-weekday boundary to monday noon`() {
        // Sunday -> Monday
        val from = at("2026-09-20T09:30:00+02:00")
        assertEquals(at("2026-09-21T12:00:00+02:00"), nextWidgetRefreshTrigger(from))
    }

    @Test
    fun `millisUntilNextWidgetRefresh matches the duration to the next trigger`() {
        val from = at("2026-09-14T09:00:00+02:00")
        val expected = Duration.between(from, at("2026-09-14T12:00:00+02:00")).toMillis()
        assertEquals(expected, millisUntilNextWidgetRefresh(from))
    }
}
