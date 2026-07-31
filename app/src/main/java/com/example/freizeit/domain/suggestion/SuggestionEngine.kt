package com.example.freizeit.domain.suggestion

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.opening.OpeningHours
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LastVisit
import com.example.freizeit.util.LatLon
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Everything the engine needs to know about the outing being planned. */
data class SuggestionContext(
    val now: LocalDateTime,
    val location: LatLon?,
    val weather: WeatherSnapshot?,
    /** Seed for the small novelty jitter; same seed = same ranking. */
    val noveltySeed: Long = now.toLocalDate().toEpochDay(),
    /** Verdicts keyed by place id — a "favorite" or "want to go" verdict makes a place a candidate. */
    val verdicts: Map<String, Verdict> = emptyMap(),
    /** Check-in timestamps (epoch millis) per place id, full history — [SuggestionEngine]
     *  applies the trailing-90-day window itself rather than receiving pre-filtered counts. */
    val visits: Map<String, List<Long>> = emptyMap()
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
    val reasons: List<String>,
    /** Card-level cautions (currently closed, imminent rain) that no longer remove a
     *  favorite/want-to-go place from the deck — they're surfaced here instead so the
     *  user can still decide. */
    val warnings: List<String> = emptyList(),
    /** [Verdict.VALUE_FAVORITE] or [Verdict.VALUE_WANT_TO_GO] — which bucket this card
     *  came from, so the UI can show the matching icon/action (#31). */
    val verdictValue: String,
    /** Human-readable recency of the most recent check-in (any source, no 90-day window,
     *  unlike the frequency scoring below), or null if never visited. Display only. */
    val lastVisit: String? = null
)

/**
 * The transparent ranking behind the merged favorites/want-to-go suggestion deck
 * (issue #17 redesign, extended by #31). Pure and deterministic: same POIs + same
 * [SuggestionContext] always produce the same output, so every rule here is
 * asserted by unit tests and scenario fixtures.
 *
 * Candidate pool: only places with a "favorite" or "want to go" verdict — this screen
 * is a swipeable deck over your own favorites and want-to-go places, not a general
 * recommender. Both verdict types compete on equal footing; neither gets a scoring
 * advantage over the other (#31).
 *
 * The only hard filter left on that pool is the verdict check above. Known-closed
 * places (issue #1 hybrid decision — unknown hours never count as closed) and outdoor
 * places facing imminent rain no longer get excluded — they stay in the deck with a
 * [Suggestion.warnings] entry instead, so the user decides rather than the engine.
 *
 * Soft score on survivors: distance decay + weather-fit bonus + confirmed-open
 * bonus + a small daily novelty jitter.
 */
object SuggestionEngine {

    /** Straight-line → road detour fudge, then ~15 km/h family biking pace. */
    private const val DETOUR_FACTOR = 1.3
    private const val BIKE_METERS_PER_MINUTE = 250.0

    private val OUTDOOR_CATEGORIES = setOf("playground", "park")

    /** Above this precipitation probability (%) for the current hour, an outdoor favorite
     *  gets a rain warning instead of being silently dropped from the deck. */
    private const val RAIN_WARNING_THRESHOLD_PERCENT = 60

    /** Extra edge for a favorite that's also genuinely close by, on top of the distance decay above. */
    private const val PROXIMITY_BONUS = 15.0
    private const val PROXIMITY_DECAY_MINUTES = 25.0

    /** Lookback for the visit-frequency term below — fixed, not user-configurable (issue #27). */
    private const val VISIT_FREQUENCY_WINDOW_DAYS = 90L
    private const val VISIT_FREQUENCY_WEIGHT = 10.0

    /** All favorited places that survive the hard filters, best first — the whole swipe deck. */
    fun rankAll(pois: List<Poi>, context: SuggestionContext): List<Suggestion> =
        pois.mapNotNull { evaluate(it, context) }.sortedByDescending { it.score }

    /**
     * Favorites no farther than [radiusMeters] from [location] (issue #21) — checked as a
     * separate step before [rankAll] rather than folded into [evaluate], so a caller can tell
     * "nothing is within range" apart from "the deck is empty for some other reason".
     *
     * [location] null means no real fix is available yet (permission pending, no GPS lock) —
     * filtering against a guess would be worse than not filtering, so every favorite passes.
     */
    fun withinRadius(pois: List<Poi>, location: LatLon?, radiusMeters: Double): List<Poi> {
        if (location == null) return pois
        return pois.filter {
            GeoDistance.metersBetween(location.lat, location.lon, it.lat, it.lon) <= radiusMeters
        }
    }

    /** Null = neither a favorite nor a want-to-go place. */
    private fun evaluate(poi: Poi, context: SuggestionContext): Suggestion? {
        val verdictValue = context.verdicts[poi.id]?.value ?: return null
        if (verdictValue != Verdict.VALUE_FAVORITE && verdictValue != Verdict.VALUE_WANT_TO_GO) return null

        val openStatus = OpeningHours.statusAt(poi.openingHours, context.now)
        val weather = context.weather
        val outdoor = poi.category in OUTDOOR_CATEGORIES

        // Current-hour rain check only (not a lookahead window) — rounds `now` to the
        // nearest full hour and reads that single hour's forecast probability.
        val rainProbabilityNow = weather?.precipitationProbabilityNear(context.now)
        val rainWarning = rainProbabilityNow?.takeIf { outdoor && it > RAIN_WARNING_THRESHOLD_PERCENT }

        val warnings = buildList {
            if (openStatus == OpenStatus.CLOSED) add("Warning: Currently closed")
            if (rainWarning != null) add("Warning: $rainWarning% rain probability")
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
                outdoor && rainWarning == null -> {
                    score += 15.0
                    reasons += if (dryAhead >= 10) "dry all day"
                    else "dry for the next ${dryAhead.coerceAtLeast(1)} h"
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

        val windowStartMillis = context.nowMillis - VISIT_FREQUENCY_WINDOW_DAYS * 24 * 60 * 60 * 1000L
        val recentVisits = context.visits[poi.id]?.count { it >= windowStartMillis } ?: 0
        if (recentVisits > 0) {
            score += VISIT_FREQUENCY_WEIGHT * ln(1.0 + recentVisits)
        }
        val lastVisit = LastVisit.format(context.visits[poi.id]?.maxOrNull(), context.now)

        score += noveltyJitter(context.noveltySeed, poi.id)

        return Suggestion(poi, score, distanceMeters, travelMinutes, openStatus, reasons, warnings, verdictValue, lastVisit)
    }

    /** Deterministic 0..8 point jitter so the same day always ranks the same. */
    private fun noveltyJitter(seed: Long, poiId: String): Double {
        var h = seed
        for (c in poiId) h = h * 31 + c.code
        return ((h % 9 + 9) % 9).toDouble()
    }
}
