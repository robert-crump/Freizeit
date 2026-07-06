package com.example.freizeit.util

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoDistance {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Haversine great-circle distance in meters. */
    fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** "340 m" below 1 km, "1.2 km" above. */
    fun format(meters: Double): String =
        if (meters < 1000) "${meters.toInt()} m"
        else String.format(Locale.getDefault(), "%.1f km", meters / 1000)
}
