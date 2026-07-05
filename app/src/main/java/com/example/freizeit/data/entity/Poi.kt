package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimal seed entity so the schema exists from day one.
 * The POI import (issue #3) will evolve this to match the extraction format.
 */
@Entity(tableName = "poi")
data class Poi(
    @PrimaryKey val osmId: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String
)
