package com.example.freizeit.data.weather

import android.content.Context
import com.example.freizeit.domain.weather.HourForecast
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.google.gson.Gson
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Weather with stale-beats-spinner semantics: [snapshot] immediately serves
 * the last fetch persisted to disk (surviving process death and offline
 * starts); [refresh] re-fetches in the background only when the cache is
 * older than [STALE_AFTER_MS] and silently keeps the cache on failure.
 */
class WeatherRepository(private val context: Context) {

    private val gson = Gson()
    private val cacheFile: File get() = File(context.filesDir, "weather_cache.json")

    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot

    suspend fun loadCache() {
        if (_snapshot.value != null) return
        _snapshot.value = withContext(Dispatchers.IO) { readCache() }
    }

    suspend fun refresh(lat: Double, lon: Double) {
        loadCache()
        val cached = _snapshot.value
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMillis < STALE_AFTER_MS) return
        val fresh = withContext(Dispatchers.IO) { OpenMeteoClient.fetch(lat, lon) } ?: return
        _snapshot.value = fresh
        withContext(Dispatchers.IO) { writeCache(fresh) }
    }

    private fun readCache(): WeatherSnapshot? = try {
        if (!cacheFile.exists()) null
        else gson.fromJson(cacheFile.readText(), CachedWeather::class.java)?.toSnapshot()
    } catch (e: Exception) {
        null
    }

    private fun writeCache(snapshot: WeatherSnapshot) {
        try {
            cacheFile.writeText(gson.toJson(CachedWeather.from(snapshot)))
        } catch (e: Exception) {
            // cache write is best-effort
        }
    }

    /** Disk shape with ISO time strings — Gson can't reflect over LocalDateTime. */
    private class CachedWeather(
        val fetchedAtMillis: Long = 0,
        val currentTempC: Double = 0.0,
        val currentWeatherCode: Int = -1,
        val isDay: Boolean = true,
        val hours: List<CachedHour> = emptyList()
    ) {
        class CachedHour(
            val time: String = "",
            val tempC: Double = 0.0,
            val precipitationProbability: Int = 0,
            val weatherCode: Int = 0
        )

        fun toSnapshot(): WeatherSnapshot? {
            if (currentWeatherCode < 0) return null
            return WeatherSnapshot(
                fetchedAtMillis = fetchedAtMillis,
                currentTempC = currentTempC,
                currentWeatherCode = currentWeatherCode,
                isDay = isDay,
                hourly = hours.map {
                    HourForecast(
                        LocalDateTime.parse(it.time), it.tempC,
                        it.precipitationProbability, it.weatherCode
                    )
                }
            )
        }

        companion object {
            fun from(s: WeatherSnapshot) = CachedWeather(
                fetchedAtMillis = s.fetchedAtMillis,
                currentTempC = s.currentTempC,
                currentWeatherCode = s.currentWeatherCode,
                isDay = s.isDay,
                hours = s.hourly.map {
                    CachedHour(
                        it.time.toString(), it.tempC,
                        it.precipitationProbability, it.weatherCode
                    )
                }
            )
        }
    }

    companion object {
        private const val STALE_AFTER_MS = 30L * 60 * 1000
    }
}
