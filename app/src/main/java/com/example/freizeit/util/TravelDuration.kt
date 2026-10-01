package com.example.freizeit.util

import kotlin.math.roundToInt

enum class TravelMode { WALK, BIKE, CAR }

data class TravelEstimate(val mode: TravelMode, val minutes: Int)

/**
 * Straight-line distance -> rough travel-time estimate. No routing engine (roads, traffic,
 * one-way streets are ignored) — same detour-factor fudge for every mode, just a different
 * assumed average speed (issues #41, #75).
 */
object TravelDuration {

    /** Straight-line -> road detour fudge, shared by every mode. */
    private const val DETOUR_FACTOR = 1.3

    private const val WALK_METERS_PER_MINUTE = 4_000.0 / 60.0 // 4 km/h: walking with kids
    private const val BIKE_METERS_PER_MINUTE = 250.0 // ~15 km/h family biking pace

    /** Car: town traffic for the first [CAR_TOWN_METERS] of road, country road/autobahn after. */
    private const val CAR_TOWN_METERS = 10_000.0
    private const val CAR_TOWN_METERS_PER_MINUTE = 30_000.0 / 60.0 // 30 km/h
    private const val CAR_OPEN_METERS_PER_MINUTE = 70_000.0 / 60.0 // 70 km/h

    /** Mode bands (#75): walk shown up to this, bike alone below [BIKE_WITH_CAR_MINUTES],
     *  bike + car up to [BIKE_MAX_MINUTES], car alone beyond. */
    private const val WALK_MAX_MINUTES = 20
    private const val BIKE_WITH_CAR_MINUTES = 25
    private const val BIKE_MAX_MINUTES = 60

    fun walkMinutes(distanceMeters: Double): Int =
        minutes(distanceMeters * DETOUR_FACTOR / WALK_METERS_PER_MINUTE)

    fun bikeMinutes(distanceMeters: Double): Int =
        minutes(distanceMeters * DETOUR_FACTOR / BIKE_METERS_PER_MINUTE)

    fun carMinutes(distanceMeters: Double): Int {
        val road = distanceMeters * DETOUR_FACTOR
        val town = road.coerceAtMost(CAR_TOWN_METERS)
        val open = (road - CAR_TOWN_METERS).coerceAtLeast(0.0)
        return minutes(town / CAR_TOWN_METERS_PER_MINUTE + open / CAR_OPEN_METERS_PER_MINUTE)
    }

    /** The one or two chips to show for [distanceMeters], the most fitting mode first. */
    fun estimates(distanceMeters: Double): List<TravelEstimate> {
        val walk = TravelEstimate(TravelMode.WALK, walkMinutes(distanceMeters))
        val bike = TravelEstimate(TravelMode.BIKE, bikeMinutes(distanceMeters))
        val car = TravelEstimate(TravelMode.CAR, carMinutes(distanceMeters))
        return when {
            walk.minutes <= WALK_MAX_MINUTES -> listOf(walk, bike)
            bike.minutes < BIKE_WITH_CAR_MINUTES -> listOf(bike)
            bike.minutes <= BIKE_MAX_MINUTES -> listOf(bike, car)
            else -> listOf(car)
        }
    }

    private fun minutes(exact: Double): Int = exact.roundToInt().coerceAtLeast(1)
}
