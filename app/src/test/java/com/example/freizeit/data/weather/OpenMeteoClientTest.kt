package com.example.freizeit.data.weather

import com.example.freizeit.domain.weather.WeatherSnapshot
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoClientTest {

    private val sample = """
        {
          "latitude": 50.94,
          "longitude": 6.96,
          "current": {
            "time": "2026-07-05T14:15",
            "temperature_2m": 22.4,
            "weather_code": 2,
            "is_day": 1
          },
          "hourly": {
            "time": ["2026-07-05T14:00", "2026-07-05T15:00", "2026-07-05T16:00"],
            "temperature_2m": [22.1, 22.9, 21.7],
            "precipitation_probability": [5, 10, 70],
            "weather_code": [2, 3, 61]
          }
        }
    """.trimIndent()

    @Test
    fun `parses the forecast shape into a snapshot`() {
        val snapshot = OpenMeteoClient.parse(sample, nowMillis = 123L)
        assertNotNull(snapshot)
        snapshot!!
        assertEquals(123L, snapshot.fetchedAtMillis)
        assertEquals(22.4, snapshot.currentTempC, 0.001)
        assertEquals(2, snapshot.currentWeatherCode)
        assertTrue(snapshot.isDay)
        assertEquals(3, snapshot.hourly.size)
        assertEquals(LocalDateTime.of(2026, 7, 5, 15, 0), snapshot.hourly[1].time)
        assertEquals(70, snapshot.hourly[2].precipitationProbability)
        assertEquals(61, snapshot.hourly[2].weatherCode)
    }

    @Test
    fun `parsed snapshot answers weather questions`() {
        val snapshot = OpenMeteoClient.parse(sample, nowMillis = 0L)!!
        val now = LocalDateTime.of(2026, 7, 5, 14, 15)
        // Rain at 16:00 (code 61, 70%) falls inside a 3 h window
        assertTrue(snapshot.badWeatherWithin(now, hours = 3))
        assertEquals(1, snapshot.dryHoursAhead(now)) // only 15:00 is dry and ahead
    }

    @Test
    fun `garbage and missing fields return null instead of throwing`() {
        assertNull(OpenMeteoClient.parse("not json at all", 0L))
        assertNull(OpenMeteoClient.parse("{}", 0L))
        assertNull(OpenMeteoClient.parse("""{"current":{"temperature_2m":20.0}}""", 0L))
    }

    @Test
    fun `wet code classification covers the WMO groups`() {
        assertTrue(WeatherSnapshot.isWetCode(61)) // rain
        assertTrue(WeatherSnapshot.isWetCode(75)) // snow
        assertTrue(WeatherSnapshot.isWetCode(95)) // thunderstorm
        org.junit.Assert.assertFalse(WeatherSnapshot.isWetCode(0)) // clear
        org.junit.Assert.assertFalse(WeatherSnapshot.isWetCode(3)) // overcast
    }
}
