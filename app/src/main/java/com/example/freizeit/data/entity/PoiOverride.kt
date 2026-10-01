package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's edits to a place (#73), generalizing the old name-only `poi_custom_name`. Every
 * value column is nullable: null means "use the OSM value". Deliberately a separate table, not
 * columns on [Poi]: re-importing a `.pbf` extract REPLACEs the whole poi row (see
 * [com.example.freizeit.data.dao.PoiDao.upsertAll]), which would silently wipe column-based
 * edits. Same reasoning as [Verdict]. Keyed by the OSM id, so edits survive re-imports.
 *
 * [hidden] removes the place from the map, search, suggestions and geofences (see
 * [applyOverrides]); its visits stay in the check-in history.
 */
@Entity(tableName = "poi_override")
data class PoiOverride(
    @PrimaryKey val placeId: String,
    val name: String? = null,
    val category: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    val openingHours: String? = null,
    val hidden: Boolean = false
) {
    /** True when this row changes nothing and can be deleted instead of stored. */
    val isEmpty: Boolean
        get() = !hidden &&
            listOf(name, category, street, housenumber, postcode, city, openingHours).all { it == null }
}

/** This place with [override]'s non-null values in place of its own — the "effective" place
 *  every screen shows. [override] null returns the place unchanged. */
fun Poi.withOverride(override: PoiOverride?): Poi =
    if (override == null) {
        this
    } else {
        copy(
            name = override.name ?: name,
            category = override.category ?: category,
            street = override.street ?: street,
            housenumber = override.housenumber ?: housenumber,
            postcode = override.postcode ?: postcode,
            city = override.city ?: city,
            openingHours = override.openingHours ?: openingHours
        )
    }

/** Effective places (see [withOverride]), with hidden ones dropped. */
fun applyOverrides(pois: List<Poi>, overrides: Map<String, PoiOverride>): List<Poi> =
    if (overrides.isEmpty()) {
        pois
    } else {
        pois.mapNotNull { poi ->
            val override = overrides[poi.id]
            if (override?.hidden == true) null else poi.withOverride(override)
        }
    }

/**
 * The override an OSM place's edit form saves (#73). Each field is trimmed; a blank field or one
 * equal to the OSM value is stored as null, so it keeps following OSM. Returns null when nothing
 * differs from [osm], meaning the row should be deleted.
 */
fun buildPoiOverride(
    osm: Poi,
    name: String,
    category: String,
    street: String,
    housenumber: String,
    postcode: String,
    city: String,
    openingHours: String
): PoiOverride? {
    fun diff(value: String, osmValue: String?): String? =
        value.trim().ifBlank { null }?.takeIf { it != osmValue }
    val override = PoiOverride(
        placeId = osm.id,
        name = diff(name, osm.name),
        category = diff(category, osm.category),
        street = diff(street, osm.street),
        housenumber = diff(housenumber, osm.housenumber),
        postcode = diff(postcode, osm.postcode),
        city = diff(city, osm.city),
        openingHours = diff(openingHours, osm.openingHours)
    )
    return override.takeUnless { it.isEmpty }
}
