package com.example.freizeit.data.repository

import com.example.freizeit.data.dao.CustomPoiDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.isCustomPoiId
import com.example.freizeit.data.entity.toPoi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * A place can live in either `poi` (OSM-sourced) or `custom_poi` (user-added, #45). The Map
 * screen's own ViewModel already folds both into one list for its consumers; everything else that
 * still reads `poiDao` directly — geofence registration/lookup, the check-in flow — needs the same
 * fold applied so custom POIs get full parity with OSM ones (#48).
 */

/** Every currently favorited place, OSM or custom. */
fun observeAllFavorites(poiDao: PoiDao, customPoiDao: CustomPoiDao): Flow<List<Poi>> =
    combine(poiDao.observeFavorites(), customPoiDao.observeFavorites()) { pois, customPois ->
        pois + customPois.map { it.toPoi() }
    }

suspend fun allFavoritesOnce(poiDao: PoiDao, customPoiDao: CustomPoiDao): List<Poi> =
    observeAllFavorites(poiDao, customPoiDao).first()

/** Looks a place up by id regardless of which table it lives in — routes on [isCustomPoiId]
 *  rather than trying `poiDao` first, since a custom id is by construction never present there. */
suspend fun findPoiById(poiDao: PoiDao, customPoiDao: CustomPoiDao, id: String): Poi? =
    if (isCustomPoiId(id)) customPoiDao.getById(id)?.toPoi() else poiDao.getById(id)
