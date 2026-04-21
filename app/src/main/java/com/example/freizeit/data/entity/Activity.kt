package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.freizeit.util.distanceBetween

@Entity(tableName = "activities")
data class Activity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: ActivityCategory,
    val latitude: Double,
    val longitude: Double,
    val isIndoor: Boolean,
    val address: String? = null,

    val enableWalk: Boolean = true,
    val enableBike: Boolean = true,
    val enableTransit: Boolean = true,
    val enableCar: Boolean = true
) {
    fun getDistanceTo(latitude: Double, longitude: Double): Double {
        return distanceBetween(this.latitude, this.longitude, latitude, longitude)
    }

    fun getDuration(distanceKm: Double, transportMode: TransportMode): Int {
        val speedKmh = when (transportMode) {
            TransportMode.WALK -> 5.0
            TransportMode.BIKE -> if (distanceKm < 15) 20.0 else 25.0
            TransportMode.TRANSIT -> if (distanceKm < 15) 20.0 else 80.0
            TransportMode.CAR -> if (distanceKm < 15) 25.0 else 100.0
        }

        val hours = distanceKm / speedKmh
        return (hours * 60).toInt()
    }

    fun isTransportEnabled(mode: TransportMode): Boolean {
        return when (mode) {
            TransportMode.WALK -> enableWalk
            TransportMode.BIKE -> enableBike
            TransportMode.TRANSIT -> enableTransit
            TransportMode.CAR -> enableCar
        }
    }
}

enum class ActivityCategory {
    WALK,
    CAFE,
    RESTAURANT,
    PLAYGROUND,
    PARK,
    JOGGING,
    CYCLING,
    ICE_CREAM
}

enum class TransportMode {
    WALK,
    BIKE,
    TRANSIT,
    CAR
}
