package com.example.freizeit.domain.weather

import java.time.LocalDateTime

data class HourForecast(
    val time: LocalDateTime,
    val tempC: Double,
    val precipitationProbability: Int,
    val weatherCode: Int
)

/**
 * A point-in-time weather picture from Open-Meteo: current conditions plus
 * the next ~24 hourly forecasts. Times are local to the forecast location
 * (the API is queried with timezone=auto).
 */
data class WeatherSnapshot(
    val fetchedAtMillis: Long,
    val currentTempC: Double,
    val currentWeatherCode: Int,
    val isDay: Boolean,
    val hourly: List<HourForecast>
) {

    /** True when rain/snow is falling now or likely within the next [hours]. */
    fun badWeatherWithin(from: LocalDateTime, hours: Int): Boolean {
        if (isWetCode(currentWeatherCode)) return true
        val until = from.plusHours(hours.toLong())
        return hourly.any { hour ->
            !hour.time.isBefore(from.minusHours(1)) && hour.time.isBefore(until) &&
                (isWetCode(hour.weatherCode) || hour.precipitationProbability >= 60)
        }
    }

    /** Consecutive dry forecast hours ahead of [from], capped at the forecast horizon. */
    fun dryHoursAhead(from: LocalDateTime): Int {
        if (isWetCode(currentWeatherCode)) return 0
        var count = 0
        for (hour in hourly.filter { !it.time.isBefore(from) }) {
            if (isWetCode(hour.weatherCode) || hour.precipitationProbability >= 60) break
            count++
        }
        return count
    }

    /** Short outlook for the weather strip, e.g. "dry for the next 5 h". */
    fun outlook(from: LocalDateTime): String {
        if (isWetCode(currentWeatherCode)) {
            val firstDry = hourly.firstOrNull {
                !it.time.isBefore(from) && !isWetCode(it.weatherCode) && it.precipitationProbability < 60
            }
            return if (firstDry != null) "drier around ${"%02d:00".format(firstDry.time.hour)}"
            else "rain for the rest of the day"
        }
        val dry = dryHoursAhead(from)
        return when {
            dry >= 10 -> "dry all day"
            dry >= 1 -> "dry for the next $dry h"
            else -> "rain expected soon"
        }
    }

    companion object {
        /** WMO weather code groups that mean falling precipitation. */
        fun isWetCode(code: Int): Boolean =
            code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

        fun describeCode(code: Int): String = when (code) {
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            in 51..57 -> "Drizzle"
            in 61..67 -> "Rain"
            in 71..77 -> "Snow"
            in 80..82 -> "Showers"
            85, 86 -> "Snow showers"
            in 95..99 -> "Thunderstorm"
            else -> "Unknown"
        }

        fun emojiForCode(code: Int, isDay: Boolean): String = when (code) {
            0 -> if (isDay) "☀️" else "🌙"
            1, 2 -> if (isDay) "🌤️" else "☁️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..57 -> "🌦️"
            in 61..67 -> "🌧️"
            in 71..77 -> "🌨️"
            in 80..82 -> "🌧️"
            85, 86 -> "🌨️"
            in 95..99 -> "⛈️"
            else -> "🌡️"
        }
    }
}
