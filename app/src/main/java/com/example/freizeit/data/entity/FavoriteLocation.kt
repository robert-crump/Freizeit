package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.freizeit.util.distanceBetween

@Entity(tableName = "favorite_locations")
data class FavoriteLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val street: String,
    val houseNumber: String,
    val zipCode: String,
    val city: String,
    val latitude: Double,
    val longitude: Double
) {
    fun getFullAddress(): String {
        return "$street $houseNumber, $zipCode $city"
    }

    fun getDistanceTo(latitude: Double, longitude: Double): Double {
        return distanceBetween(this.latitude, this.longitude, latitude, longitude)
    }
}
