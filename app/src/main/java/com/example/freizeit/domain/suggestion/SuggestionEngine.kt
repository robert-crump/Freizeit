package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.opening.OpeningHours
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.roundToInt

/** Everything the engine needs to know about the outing being planned. */
data class SuggestionContext(
    val now: LocalDateTime,
    val location: LatLon?,
    val weather: WeatherSnapshot?,
    /** Seed for the small novelty jitter; same seed = same ranking. */
    val noveltySeed: Long = now.toLocalDate().toEpochDay(),
    /** Verdicts keyed by place id — only a "favorite" verdict makes a place a candidate. */
    val verdicts: Map<String, Verdict> = emptyMap()
) {
    val nowMillis: Long = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

data class Suggestion(
    val poi: Poi,
    val score: Double,
    val distanceMeters: Double?,
    val travelMinutes: Int?,
    val openStatus: OpenStatus,
    /** Human-readable score facts (travel time, weather fit), joined with " · " on the card. */
    val reasons: List<String>
)

/**
 * The transparent ranking behind the favorites-only suggestion deck (issue
 * #17 redesign). Pure and deterministic: same POIs + same [SuggestionContext]
 * always produce the same output, so every rule here is asserted by unit
 * tests and scenario fixtures.
 *
 * Candidate pool: only places with a "favorite" verdict — this screen is a
 * swipeable deck over your own favorites, not a general recommender.
 *
 * Hard filters on that pool: known-closed places (issue #1 hybrid decision —
 * unknown hours never filter) and outdoor places when rain is due within the
 * outing window.
 *
 * Soft score on survivors: distance decay + weather-fit bonus + confirmed-open
 * bonus + a small daily novelty jitter.
 */
object SuggestionEngine {

    /** Straight-line → road detour fudge, then ~15 km/h family biking pace. */
    private const val DETOUR_FACTOR = 1.3
    private const val BIKE_METERS_PER_MINUTE = 250.0

    private val OUTDOOR_CATEGORIES = setOf("playground", "park")

    /** Fixed window for "will it rain during this outing" now that there's no time-budget input. */
    private const val OUTING_WINDOW_HOURS = 3

    /** Extra edge for a favorite that's also genuinely close by, on top of the distance decay above. */
    private const val PROXIMITY_BONUS = 15.0
    private const val PROXIMITY_DECAY_MINUTES = 25.0

    /** All favorited places that survive the hard filters, best first — the whole swipe deck. */
    fun rankAll(pois: List<Poi>, context: SuggestionContext): List<Suggestion> =
        pois.mapNotNull { evaluate(it, context) }.sortedByDescending { it.score }

    /** Null = not a favorite, or hard-filtered. */
    private fun evaluate(poi: Poi, context: SuggestionContext): Suggestion? {
        if (context.verdicts[poi.id]?.value != Verdict.VALUE_FAVORITE) return null

        val openStatus = OpeningHours.statusAt(poi.openingHours, context.now)
        if (openStatus == OpenStatus.CLOSED) return null

        val weather = context.weather
        val outdoor = poi.category in OUTDOOR_CATEGORIES
        if (outdoor && weather != null && weather.badWeatherWithin(context.now, OUTING_WINDOW_HOURS)) {
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
        }

        val reasons = mutableListOf<String>()
        var score = 0.0

        if (openStatus == OpenStatus.OPEN) {
            score += 6.0
        }

        if (travelMinutes != null) {
            score += 40.0 * exp(-travelMinutes / 25.0)
            score += PROXIMITY_BONUS * exp(-travelMinutes / PROXIMITY_DECAY_MINUTES)
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
                    else "dry for the next ${dryAhead.coerceAtLeast(OUTING_WINDOW_HOURS)} h"
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

        return Suggestion(poi, score, distanceMeters, travelMinutes, openStatus, reasons)
    }

    /** Deterministic 0..8 point jitter so the same day always ranks the same. */
    private fun noveltyJitter(seed: Long, poiId: String): Double {
        var h = seed
        for (c in poiId) h = h * 31 + c.code
        return ((h % 9 + 9) % 9).toDouble()
    }
}
