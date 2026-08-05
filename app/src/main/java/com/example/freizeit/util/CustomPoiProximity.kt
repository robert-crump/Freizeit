package com.example.freizeit.util

import com.example.freizeit.data.entity.Poi

/**
 * Pre-save duplicate check for the add-custom-POI flow (issue #45): warns when a place of the
 * same category already sits very close to the pin, so the user can confirm it's genuinely a
 * new place rather than, say, a GPS-jittered second drop of the café they just added. Checked
 * against [com.example.freizeit.ui.map.MapUiState.allPois], which already merges `poi` and
 * `custom_poi` (via [com.example.freizeit.data.entity.toPoi]) — one list, one check, no separate
 * custom-vs-OSM branch needed here.
 */
object CustomPoiProximity {

    /** "Very close" for this check: tight enough that two different same-category places on the
     *  same street usually don't trigger it, generous enough to catch drop-pin/GPS jitter against
     *  the exact same place (the example in issue #45 is "~15m away"). */
    const val WARNING_THRESHOLD_METERS = 40.0

    /** The nearest same-category existing place within [thresholdMeters], if any. */
    fun findNearbyMatch(
        lat: Double,
        lon: Double,
        category: String,
        existing: List<Poi>,
        thresholdMeters: Double = WARNING_THRESHOLD_METERS
    ): Poi? =
        existing
            .asSequence()
            .filter { it.category == category }
            .map { it to GeoDistance.metersBetween(lat, lon, it.lat, it.lon) }
            .filter { it.second <= thresholdMeters }
            .minByOrNull { it.second }
            ?.first
}
