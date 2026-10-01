package com.example.freizeit.util

import kotlin.math.roundToInt

enum class TravelMode { WALK, BIKE, CAR }

data class TravelEstimate(val mode: TravelMode, val minutes: Int)

/** How a travel time reads for its mode: fine, a stretch, or too far. */
enum class DurationBand { GREEN, ORANGE, RED }

/** One mode's color limits, both inclusive: up to [greenMax] is green, up to [orangeMax] orange. */
data class BandLimits(val greenMax: Int, val orangeMax: Int) {
    fun bandOf(minutes: Int): DurationBand = when {
        minutes <= greenMax -> DurationBand.GREEN
        minutes <= orangeMax -> DurationBand.ORANGE
        else -> DurationBand.RED
    }

    /** Snapped to [STEP]s within [MIN]..[MAX], orange never below green. */
    fun sanitized(): BandLimits {
        val green = snap(greenMax)
        return BandLimits(green, snap(orangeMax).coerceAtLeast(green))
    }

    companion object {
        const val MIN = 5
        const val MAX = 90
        const val STEP = 5
        private fun snap(minutes: Int): Int = ((minutes + STEP / 2) / STEP * STEP).coerceIn(MIN, MAX)
    }
}

/** The user's travel-time color limits per mode (Settings). */
data class TravelLimits(
    val walk: BandLimits = BandLimits(greenMax = 20, orangeMax = 30),
    val bike: BandLimits = BandLimits(greenMax = 20, orangeMax = 30),
    val car: BandLimits = BandLimits(greenMax = 20, orangeMax = 40)
) {
    fun of(mode: TravelMode): BandLimits = when (mode) {
        TravelMode.WALK -> walk
        TravelMode.BIKE -> bike
        TravelMode.CAR -> car
    }

    fun with(mode: TravelMode, limits: BandLimits): TravelLimits = when (mode) {
        TravelMode.WALK -> copy(walk = limits)
        TravelMode.BIKE -> copy(bike = limits)
        TravelMode.CAR -> copy(car = limits)
    }

    fun bandOf(estimate: TravelEstimate): DurationBand = of(estimate.mode).bandOf(estimate.minutes)
}

/**
 * Straight-line distance -> rough travel-time estimate. No routing engine (roads, traffic,
 * one-way streets are ignored) — same detour-factor fudge for every mode, just a different
 * assumed average speed (issues #41, #75).
 */
object TravelDuration {

    /** Straight-line -> road detour fudge, shared by every mode. */
    private const val DETOUR_FACTOR = 1.3

    private const val WALK_METERS_PER_MINUTE = 4_000.0 / 60.0 // 4 km/h: walking with kids

    /** Bike: a slow start for the first [BIKE_SLOW_METERS] of road (setting off, junctions, short
     *  hops rarely reach cruising pace), family cruising pace after — gradual, so a longer ride
     *  never estimates shorter than a nearer one. */
    private const val BIKE_SLOW_METERS = 3_000.0
    private const val BIKE_SLOW_METERS_PER_MINUTE = 10_000.0 / 60.0 // 10 km/h
    private const val BIKE_CRUISE_METERS_PER_MINUTE = 250.0 // 15 km/h family biking pace

    /** Car: town traffic for the first [CAR_TOWN_METERS] of road, country road/autobahn after. */
    private const val CAR_TOWN_METERS = 10_000.0
    private const val CAR_TOWN_METERS_PER_MINUTE = 30_000.0 / 60.0 // 30 km/h
    private const val CAR_OPEN_METERS_PER_MINUTE = 70_000.0 / 60.0 // 70 km/h

    fun walkMinutes(distanceMeters: Double): Int =
        minutes(distanceMeters * DETOUR_FACTOR / WALK_METERS_PER_MINUTE)

    fun bikeMinutes(distanceMeters: Double): Int {
        val road = distanceMeters * DETOUR_FACTOR
        val slow = road.coerceAtMost(BIKE_SLOW_METERS)
        val cruise = (road - BIKE_SLOW_METERS).coerceAtLeast(0.0)
        return minutes(slow / BIKE_SLOW_METERS_PER_MINUTE + cruise / BIKE_CRUISE_METERS_PER_MINUTE)
    }

    fun carMinutes(distanceMeters: Double): Int {
        val road = distanceMeters * DETOUR_FACTOR
        val town = road.coerceAtMost(CAR_TOWN_METERS)
        val open = (road - CAR_TOWN_METERS).coerceAtLeast(0.0)
        return minutes(town / CAR_TOWN_METERS_PER_MINUTE + open / CAR_OPEN_METERS_PER_MINUTE)
    }

    /** Walk, bike and car, in that order — every mode is always shown, colored by [TravelLimits]. */
    fun estimates(distanceMeters: Double): List<TravelEstimate> = listOf(
        TravelEstimate(TravelMode.WALK, walkMinutes(distanceMeters)),
        TravelEstimate(TravelMode.BIKE, bikeMinutes(distanceMeters)),
        TravelEstimate(TravelMode.CAR, carMinutes(distanceMeters))
    )

    private fun minutes(exact: Double): Int = exact.roundToInt().coerceAtLeast(1)
}
