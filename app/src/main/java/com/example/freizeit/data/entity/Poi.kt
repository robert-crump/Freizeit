package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A place imported from the POI extraction file (tools/poi_extraction).
 *
 * [id] is the OSM "type/id" string (e.g. "node/286560726") — the stable key
 * the import upserts by. [missingFromOsm] is set when a re-import no longer
 * contains this place but a family verdict exists for it (keep+flag rule);
 * unverdicted vanished places are deleted instead.
 */
@Entity(tableName = "poi")
data class Poi(
    @PrimaryKey val id: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val name: String? = null,
    val openingHours: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    val missingFromOsm: Boolean = false
)
