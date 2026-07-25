package com.example.freizeit.util

import com.example.freizeit.data.entity.Poi

/**
 * Play Services caps active geofences at 100 per app (issue #29). When there are more favorites
 * than [limit], only the closest ones to [lat]/[lon] (the last significant-location-change fix)
 * get a geofence; the rest wait for a future re-rank pass where they might become closest instead.
 * At or under [limit], every favorite is returned unchanged (order preserved) — no ranking needed.
 */
fun selectClosestFavorites(favorites: List<Poi>, lat: Double, lon: Double, limit: Int): List<Poi> {
    if (favorites.size <= limit) return favorites
    return favorites.sortedBy { GeoDistance.metersBetween(lat, lon, it.lat, it.lon) }.take(limit)
}
