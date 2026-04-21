package com.example.freizeit.util

import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory

fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0
    val deltaLat = Math.toRadians(lat2 - lat1)
    val deltaLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return earthRadius * c
}

data class FilterOptions(
    val categories: Set<ActivityCategory> = ActivityCategory.values().toSet(),
    val maxDistance: Double? = null, // in km, null = egal
    val indoorOutdoor: IndoorOutdoorFilter = IndoorOutdoorFilter.BOTH
)

enum class IndoorOutdoorFilter {
    INDOOR,
    OUTDOOR,
    BOTH
}

object ActivityFilter {
    fun filterActivities(
        activities: List<Activity>,
        filterOptions: FilterOptions,
        userLatitude: Double,
        userLongitude: Double
    ): List<Activity> {
        return activities.filter { activity ->
            // Filter nach Kategorie
            val categoryMatch = filterOptions.categories.contains(activity.category)
            
            // Filter nach Entfernung
            val distanceMatch = if (filterOptions.maxDistance != null) {
                val distance = activity.getDistanceTo(userLatitude, userLongitude)
                distance <= filterOptions.maxDistance
            } else {
                true
            }
            
            // Filter nach Indoor/Outdoor
            val indoorOutdoorMatch = when (filterOptions.indoorOutdoor) {
                IndoorOutdoorFilter.INDOOR -> activity.isIndoor
                IndoorOutdoorFilter.OUTDOOR -> !activity.isIndoor
                IndoorOutdoorFilter.BOTH -> true
            }
            
            categoryMatch && distanceMatch && indoorOutdoorMatch
        }
    }
}
