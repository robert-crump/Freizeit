package com.example.freizeit.util

import kotlin.math.roundToInt

/**
 * Straight-line distance -> rough travel-time estimate. No routing engine (roads, traffic,
 * one-way streets are ignored) — same detour-factor fudge for both modes, just a different
 * assumed average speed (issue #41).
 */
object TravelDuration {

    /** Straight-line -> road detour fudge, shared by both modes. */
    private const val DETOUR_FACTOR = 1.3

    private const val BIKE_METERS_PER_MINUTE = 250.0 // ~15 km/h family biking pace
    private const val CAR_METERS_PER_MINUTE = 70_000.0 / 60.0 // 70 km/h: country-road/autobahn average

    fun bikeMinutes(distanceMeters: Double): Int =
        (distanceMeters * DETOUR_FACTOR / BIKE_METERS_PER_MINUTE).roundToInt().coerceAtLeast(1)

    fun carMinutes(distanceMeters: Double): Int =
        (distanceMeters * DETOUR_FACTOR / CAR_METERS_PER_MINUTE).roundToInt().coerceAtLeast(1)
}
