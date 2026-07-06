package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.opening.OpeningHours
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

/** Everything the engine needs to know about the outing being planned. */
data class SuggestionContext(
    val now: LocalDateTime,
    val location: LatLon?,
    val weather: WeatherSnapshot?,
    val timeBudgetMinutes: Int = DEFAULT_TIME_BUDGET_MINUTES,
    val kidsAlong: Boolean = true,
    /** Seed for the small novelty jitter; same seed = same ranking. */
    val noveltySeed: Long = now.toLocalDate().toEpochDay()
) {
    companion object {
        const val DEFAULT_TIME_BUDGET_MINUTES = 180
    }
}

data class Suggestion(
    val poi: Poi,
    val score: Double,
    val distanceMeters: Double?,
    val travelMinutes: Int?,
    /** Human-readable score/filter facts, joined with " · " on the card. */
    val reasons: List<String>
)

/**
 * The transparent ranking behind the Home cards. Pure and deterministic:
 * same POIs + same [SuggestionContext] always produce the same output, so
 * every rule here is asserted by unit tests and scenario fixtures.
 *
 * Hard filters (embarrassment removal): known-closed places (issue #1 hybrid
 * decision — unknown hours never filter), outdoor places when rain is due
 * within the outing window, and places unreachable inside the time budget
 * (Haversine bike estimate, no real routing by design).
 *
 * Soft score on survivors: distance decay + weather-fit bonus + confirmed-open
 * bonus + a small daily novelty jitter, times a who-is-along category weight.
 */
object SuggestionEngine {

    /** Straight-line → road detour fudge, then ~15 km/h family biking pace. */
    private const val DETOUR_FACTOR = 1.3
    private const val BIKE_METERS_PER_MINUTE = 250.0
    private const val MIN_STAY_MINUTES = 30

    private val OUTDOOR_CATEGORIES = setOf("playground", "park")

    /** Top [count] suggestions with the ≥2-categories diversity rule applied. */
    fun suggest(
        pois: List<Poi>,
        context: SuggestionContext,
        excludeIds: Set<String> = emptySet(),
        count: Int = 3
    ): List<Suggestion> {
        val ranked = rankAll(pois, context).filter { it.poi.id !in excludeIds }
        val top = ranked.take(count).toMutableList()
        if (top.size == count && top.distinctBy { it.poi.category }.size == 1) {
            ranked.firstOrNull { it.poi.category != top[0].poi.category }
                ?.let { top[count - 1] = it }
        }
        return top
    }

    /** All eligible places, best first. Exposed for tests and the reroll pool. */
    fun rankAll(pois: List<Poi>, context: SuggestionContext): List<Suggestion> =
        pois.mapNotNull { evaluate(it, context) }.sortedByDescending { it.score }

    /** Null = hard-filtered. */
    private fun evaluate(poi: Poi, context: SuggestionContext): Suggestion? {
        val categoryWeight = categoryWeight(poi.category, context.kidsAlong)
        if (categoryWeight <= 0.0) return null

        val openStatus = OpeningHours.statusAt(poi.openingHours, context.now)
        if (openStatus == OpenStatus.CLOSED) return null

        val outingHours = ceil(context.timeBudgetMinutes / 60.0).toInt().coerceIn(1, 6)
        val weather = context.weather
        val outdoor = poi.category in OUTDOOR_CATEGORIES
        if (outdoor && weather != null && weather.badWeatherWithin(context.now, outingHours)) {
            return null
        }

        var distanceMeters: Double? = null
        var travelMinutes: Int? = null
        if (context.location != null) {
            distanceMeters = GeoDistance.metersBetween(
                context.location.lat, context.location.lon, poi.lat, poi.lon
            )
            travelMinutes = (distanceMeters * DETOUR_FACTOR / BIKE_METERS_PER_MINUTE)
                .roundToInt().coerceAtLeast(1)
            if (2 * travelMinutes + MIN_STAY_MINUTES > context.timeBudgetMinutes) return null
        }

        val reasons = mutableListOf<String>()
        var score = 0.0

        if (openStatus == OpenStatus.OPEN) {
            score += 6.0
            reasons += "Open"
        }

        if (travelMinutes != null) {
            score += 40.0 * exp(-travelMinutes / 25.0)
            reasons += "$travelMinutes min by bike"
        } else {
            score += 20.0 // location unknown: neutral distance score
        }

        if (weather != null) {
            val dryAhead = weather.dryHoursAhead(context.now)
            when {
                outdoor -> {
                    // survived the hard filter, so the window is dry
                    score += 15.0
                    reasons += if (dryAhead >= 10) "dry all day"
                    else "dry for the next ${dryAhead.coerceAtLeast(outingHours)} h"
                }
                poi.category == "ice_cream" && dryAhead >= 1 && weather.currentTempC >= 18.0 -> {
                    score += 12.0
                    reasons += "${weather.currentTempC.roundToInt()}° ice-cream weather"
                }
                WeatherSnapshot.isWetCode(weather.currentWeatherCode) -> {
                    score += 12.0
                    reasons += "good for a rainy day"
                }
            }
        }

        score += noveltyJitter(context.noveltySeed, poi.id)

        return Suggestion(poi, score * categoryWeight, distanceMeters, travelMinutes, reasons)
    }

    /**
     * Who-is-along weighting. Kids along (the default) treats every category
     * normally; adults-only mostly drops playgrounds. Issue #7 wires the toggle.
     */
    fun categoryWeight(category: String, kidsAlong: Boolean): Double =
        if (kidsAlong) 1.0
        else when (category) {
            "playground" -> 0.1
            "ice_cream" -> 0.8
            else -> 1.1
        }

    /** Deterministic 0..8 point jitter so the same day always ranks the same. */
    private fun noveltyJitter(seed: Long, poiId: String): Double {
        var h = seed
        for (c in poiId) h = h * 31 + c.code
        return ((h % 9 + 9) % 9).toDouble()
    }
}
