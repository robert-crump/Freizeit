package com.example.freizeit.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Localized medium date+time, in the device's current zone. */
fun formatVisitTimestamp(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

/** Time only, e.g. "3:45 PM" — for check-in history rows in the "Today" section, where the
 *  section header already carries the date. */
fun formatVisitTimeOnly(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(zone)
        .format(Instant.ofEpochMilli(epochMillis))

/** Weekday + time, e.g. "Tue, 3:45 PM" — for check-in history rows outside the "Today" section,
 *  where the section header carries the week/month but not the specific day. */
fun formatVisitWeekdayAndTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).withZone(zone).format(instant)
    return "$weekday, ${formatVisitTimeOnly(epochMillis, zone)}"
}
