package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A family-named place (e.g. "Home", "Oma's"). When GPS lands within ~300 m
 * of one, it becomes the distance anchor on Home instead of raw coordinates
 * (carried over from the v1 concept).
 */
@Entity(tableName = "favorite")
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String?,
    val lat: Double,
    val lon: Double
)
