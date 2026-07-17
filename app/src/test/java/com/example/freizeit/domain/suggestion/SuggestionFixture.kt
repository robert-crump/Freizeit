package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.weather.HourForecast
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.util.LatLon
import java.time.LocalDateTime

/**
 * The canned world the scenario tests run against: home in central Cologne,
 * a handful of places at known distances (0.009° latitude ≈ 1 km), and
 * weather factories aligned to the scenario's clock.
 */
object SuggestionFixture {

    val HOME = LatLon(50.94, 6.96)

    private fun poi(
        id: String,
        category: String,
        kmNorth: Double,
        name: String,
        hours: String? = null
    ) = Poi(
        id = id,
        category = category,
        lat = HOME.lat + kmNorth * 0.009,
        lon = HOME.lon,
        name = name,
        openingHours = hours
    )

    // Travel estimate at these distances (×1.3 detour, 250 m/min bike):
    // 0.5 km → 3 min, 0.8 km → 4 min, 1.2 km → 6 min, 12 km → 62 min.
    val playgroundNear = poi("node/1", "playground", 0.5, "Spielplatz Stadtgarten")
    val playgroundFar = poi("node/2", "playground", 12.0, "Abenteuerspielplatz")
    val park = poi("node/3", "park", -1.0, "Stadtpark")
    val cafe = poi("node/4", "cafe", 0.8, "Café Sonne", "Mo-Su 08:00-18:00")
    val kiosk = poi("node/5", "cafe", 0.6, "Kiosk am Ring", "24/7")
    val restaurant = poi("node/6", "restaurant", -1.2, "Trattoria Roma", "Mo-Su 11:00-22:00")
    val iceCream = poi("node/7", "ice_cream", 0.9, "Eiscafé Venezia", "Mo-Su 12:00-20:00")

    val allPois = listOf(playgroundNear, playgroundFar, park, cafe, kiosk, restaurant, iceCream)

    /** Every fixture POI favorited — the common case for the ranking scenario tests. */
    fun favoriteAll(pois: List<Poi> = allPois): Map<String, Verdict> =
        pois.associate { it.id to favoriteVerdict(it) }

    fun favoriteVerdict(poi: Poi): Verdict = Verdict(
        placeId = poi.id,
        value = Verdict.VALUE_FAVORITE,
        verdictedAt = 0L,
        snapshotName = poi.name,
        snapshotLat = poi.lat,
        snapshotLon = poi.lon,
        snapshotCategory = poi.category
    )

    // July 2026: the 4th is a Saturday, the 7th a Tuesday.
    fun saturdayAt(hour: Int): LocalDateTime = LocalDateTime.of(2026, 7, 4, hour, 0)
    fun tuesdayAt(hour: Int): LocalDateTime = LocalDateTime.of(2026, 7, 7, hour, 0)

    /** 12 forecast hours from [now]; [hourCode]/[precip] indexed by hours ahead. */
    private fun weather(
        now: LocalDateTime,
        currentCode: Int,
        tempC: Double,
        hourCode: (Int) -> Int = { currentCode },
        precip: (Int) -> Int = { 5 }
    ) = WeatherSnapshot(
        fetchedAtMillis = 0,
        currentTempC = tempC,
        currentWeatherCode = currentCode,
        isDay = true,
        hourly = (0 until 12).map { i ->
            HourForecast(now.plusHours(i.toLong()), tempC, precip(i), hourCode(i))
        }
    )

    fun sunny(now: LocalDateTime, tempC: Double = 21.0) = weather(now, currentCode = 0, tempC = tempC)

    fun coldClear(now: LocalDateTime) = weather(now, currentCode = 0, tempC = 3.0)

    fun rainingNow(now: LocalDateTime) =
        weather(now, currentCode = 61, tempC = 14.0, precip = { 90 })

    /** Dry right now, rain arriving [inHours] from now for the rest of the day. */
    fun rainComing(now: LocalDateTime, inHours: Int) = weather(
        now, currentCode = 2, tempC = 17.0,
        hourCode = { i -> if (i >= inHours) 61 else 2 },
        precip = { i -> if (i >= inHours) 85 else 10 }
    )
}
