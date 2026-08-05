package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A user-added place (issue #45's "manual POI adding" epic), created by dropping a pin on the
 * Map screen rather than coming from a `.pbf` extract. Mirrors [Poi]'s fields (same category
 * vocabulary — [com.example.freizeit.ui.common.CATEGORY_ORDER] — same optional address/opening-
 * hours shape) but is deliberately a separate table, not a row in `poi`: a reimport wholesale
 * REPLACEs `poi` (see [com.example.freizeit.data.dao.PoiDao.upsertAll]), which would silently
 * wipe user-authored data — same rationale as [PoiCustomName].
 *
 * [id] uses its own `custom/<uuid>` scheme (see [newCustomPoiId]), independent of and never
 * colliding with OSM's `node/`, `way/`, `relation/` ids, so a [toPoi] projection can be merged
 * into the same id space as OSM [Poi]s (used for Verdict/Visit keying, map markers, search) with
 * no risk of two different places sharing an id. [name] and [category] are required at creation
 * (enforced by the add-POI form, not this table) — unlike [Poi], where both are legitimately
 * absent for some OSM data.
 */
@Entity(tableName = "custom_poi")
data class CustomPoi(
    @PrimaryKey val id: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val name: String,
    val openingHours: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val postcode: String? = null,
    val city: String? = null
)

/** Prefix marking an id as belonging to [CustomPoi] rather than an OSM [Poi] — lets later code
 *  (e.g. #47's edit/delete-only-for-custom-POIs) tell the two apart without a DB round trip. */
const val CUSTOM_POI_ID_PREFIX = "custom/"

fun newCustomPoiId(): String = CUSTOM_POI_ID_PREFIX + UUID.randomUUID()

fun isCustomPoiId(id: String): Boolean = id.startsWith(CUSTOM_POI_ID_PREFIX)

/**
 * Projects this custom place into the shape the rest of the app already renders, filters, and
 * searches ([Poi]) — merged into [com.example.freizeit.ui.map.MapViewModel]'s POI list alongside
 * OSM places rather than requiring every consumer (map markers, category chips, search) to learn
 * a second POI type. Not persisted anywhere as a `Poi` row; [missingFromOsm] is always false since
 * that flag only means something for the OSM reimport cycle this table is exempt from.
 */
fun CustomPoi.toPoi(): Poi = Poi(
    id = id,
    category = category,
    lat = lat,
    lon = lon,
    name = name,
    openingHours = openingHours,
    street = street,
    housenumber = housenumber,
    postcode = postcode,
    city = city,
    missingFromOsm = false
)
