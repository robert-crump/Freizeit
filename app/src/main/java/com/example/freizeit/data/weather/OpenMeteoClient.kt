package com.example.freizeit.data.weather

import com.example.freizeit.domain.weather.HourForecast
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.util.Locale

/**
 * Minimal Open-Meteo forecast client (free, keyless). One GET, so plain
 * HttpURLConnection + Gson instead of a client library. Returns null on any
 * network or parse problem — the caller keeps serving its cache.
 */
object OpenMeteoClient {

    private const val TIMEOUT_MS = 10_000
    private val gson = Gson()

    fun fetch(lat: Double, lon: Double, nowMillis: Long = System.currentTimeMillis()): WeatherSnapshot? {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,is_day" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&forecast_days=2&timezone=auto",
            lat, lon
        )
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            try {
                if (connection.responseCode != 200) return null
                val response = InputStreamReader(connection.inputStream, Charsets.UTF_8).use {
                    gson.fromJson(it, ForecastResponse::class.java)
                }
                toSnapshot(response, nowMillis)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Visible for tests: pure mapping from the API shape to the domain snapshot. */
    fun parse(json: String, nowMillis: Long): WeatherSnapshot? = try {
        toSnapshot(gson.fromJson(json, ForecastResponse::class.java), nowMillis)
    } catch (e: Exception) {
        null
    }

    private fun toSnapshot(response: ForecastResponse?, nowMillis: Long): WeatherSnapshot? {
        val current = response?.current ?: return null
        val hourly = response.hourly ?: return null
        val times = hourly.time ?: return null
        val hours = times.indices.mapNotNull { i ->
            HourForecast(
                time = LocalDateTime.parse(times[i]),
                tempC = hourly.temperature.getOrNull(i) ?: return@mapNotNull null,
                precipitationProbability = hourly.precipitationProbability.getOrNull(i) ?: 0,
                weatherCode = hourly.weatherCode.getOrNull(i) ?: return@mapNotNull null
            )
        }
        return WeatherSnapshot(
            fetchedAtMillis = nowMillis,
            currentTempC = current.temperature ?: return null,
            currentWeatherCode = current.weatherCode ?: return null,
            isDay = current.isDay == 1,
            hourly = hours
        )
    }

    private class ForecastResponse(
        val current: Current? = null,
        val hourly: Hourly? = null
    )

    private class Current(
        @SerializedName("temperature_2m") val temperature: Double? = null,
        @SerializedName("weather_code") val weatherCode: Int? = null,
        @SerializedName("is_day") val isDay: Int? = null
    )

    private class Hourly(
        val time: List<String>? = null,
        @SerializedName("temperature_2m") val temperature: List<Double> = emptyList(),
        @SerializedName("precipitation_probability") val precipitationProbability: List<Int> = emptyList(),
        @SerializedName("weather_code") val weatherCode: List<Int> = emptyList()
    )
}
