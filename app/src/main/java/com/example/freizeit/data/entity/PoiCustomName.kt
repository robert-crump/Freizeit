package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-given name for a POI, overriding the OSM [Poi.name] (e.g. for OSM's
 * many unnamed playgrounds). Deliberately a separate table, not a column on
 * [Poi]: re-importing a `.pbf` extract REPLACEs the whole poi row (see
 * [com.example.freizeit.data.dao.PoiDao.upsertAll]), which would silently
 * wipe a column-based custom name. Same reasoning as [Verdict].
 */
@Entity(tableName = "poi_custom_name")
data class PoiCustomName(
    @PrimaryKey val placeId: String,
    val customName: String
)
