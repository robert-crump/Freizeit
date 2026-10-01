package com.example.freizeit.data.repository

import com.example.freizeit.data.dao.CustomPoiDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.PoiOverrideDao
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.applyOverrides
import com.example.freizeit.data.entity.isCustomPoiId
import com.example.freizeit.data.entity.toPoi
import com.example.freizeit.data.entity.withOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * A place can live in either `poi` (OSM-sourced) or `custom_poi` (user-added, #45), and the
 * user's edits to it live in `poi_override` (#73). Everything that shows or acts on places reads
 * them through these helpers, so custom POIs get full parity with OSM ones (#48) and every screen
 * sees the same effective values (override → OSM), with hidden places left out.
 */

/** Every place, OSM and custom, with overrides applied and hidden ones dropped. */
fun observeAllPlaces(poiDao: PoiDao, customPoiDao: CustomPoiDao, overrideDao: PoiOverrideDao): Flow<List<Poi>> =
    combine(poiDao.observeAll(), customPoiDao.observeAll(), overrideDao.observeAll()) { pois, customPois, overrides ->
        applyOverrides(pois + customPois.map { it.toPoi() }, overrides.associateBy { it.placeId })
    }

/** Every currently favorited place, OSM or custom. */
fun observeAllFavorites(poiDao: PoiDao, customPoiDao: CustomPoiDao, overrideDao: PoiOverrideDao): Flow<List<Poi>> =
    combine(poiDao.observeFavorites(), customPoiDao.observeFavorites(), overrideDao.observeAll()) { pois, customPois, overrides ->
        applyOverrides(pois + customPois.map { it.toPoi() }, overrides.associateBy { it.placeId })
    }

suspend fun allFavoritesOnce(poiDao: PoiDao, customPoiDao: CustomPoiDao, overrideDao: PoiOverrideDao): List<Poi> =
    observeAllFavorites(poiDao, customPoiDao, overrideDao).first()

/** Every place holding one of [values] as its verdict, OSM or custom — Home's deck pool and the
 *  widget's (#57). */
fun observeAllByVerdictValues(
    poiDao: PoiDao,
    customPoiDao: CustomPoiDao,
    overrideDao: PoiOverrideDao,
    values: List<String>
): Flow<List<Poi>> =
    combine(
        poiDao.observeByVerdictValues(values),
        customPoiDao.observeByVerdictValues(values),
        overrideDao.observeAll()
    ) { pois, customPois, overrides ->
        applyOverrides(pois + customPois.map { it.toPoi() }, overrides.associateBy { it.placeId })
    }

/** The places check-in notifications watch (#74): every place whose verdict one of the enabled
 *  switches in [targets] covers, so a place with either verdict appears once. Empty when both
 *  switches are off. */
fun observeNotificationPlaces(
    poiDao: PoiDao,
    customPoiDao: CustomPoiDao,
    overrideDao: PoiOverrideDao,
    targets: NotificationTargets
): Flow<List<Poi>> =
    if (targets.any) {
        observeAllByVerdictValues(poiDao, customPoiDao, overrideDao, targets.verdictValues)
    } else {
        flowOf(emptyList())
    }

suspend fun notificationPlacesOnce(
    poiDao: PoiDao,
    customPoiDao: CustomPoiDao,
    overrideDao: PoiOverrideDao,
    targets: NotificationTargets
): List<Poi> = observeNotificationPlaces(poiDao, customPoiDao, overrideDao, targets).first()

/** The place as stored, without overrides — what the edit form compares against. Routes on
 *  [isCustomPoiId] rather than trying `poiDao` first, since a custom id is by construction never
 *  present there. */
suspend fun findRawPoiById(poiDao: PoiDao, customPoiDao: CustomPoiDao, id: String): Poi? =
    if (isCustomPoiId(id)) customPoiDao.getById(id)?.toPoi() else poiDao.getById(id)

/** The effective place regardless of which table it lives in; null if it's gone or hidden. */
suspend fun findPoiById(poiDao: PoiDao, customPoiDao: CustomPoiDao, overrideDao: PoiOverrideDao, id: String): Poi? {
    val poi = findRawPoiById(poiDao, customPoiDao, id) ?: return null
    val override = overrideDao.getById(id)
    return if (override?.hidden == true) null else poi.withOverride(override)
}
