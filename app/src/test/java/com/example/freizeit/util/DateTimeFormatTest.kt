package com.example.freizeit.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeFormatTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `history row timestamp shows a short date instead of the weekday`() {
        val millis = LocalDateTime.of(2026, 9, 29, 15, 45).atZone(zone).toInstant().toEpochMilli()

        val text = formatVisitDateAndTime(millis, zone, Locale.ENGLISH)

        assertTrue(text, text.startsWith("29 Sep, "))
    }
}
