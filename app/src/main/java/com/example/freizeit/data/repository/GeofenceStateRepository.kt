package com.example.freizeit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Runtime state for issue #28's geofencing, not a user-facing setting — deliberately separate
 * from [SettingsRepository]. Tracks which favorites the device is currently physically inside
 * (per ENTER/EXIT broadcasts) so the receiver can re-derive "closest of the currently-inside
 * ones" on every transition without needing a live location fix of its own.
 */
class GeofenceStateRepository(private val dataStore: DataStore<Preferences>) {

    suspend fun getInsidePlaceIds(): Set<String> =
        dataStore.data.first()[INSIDE_PLACE_IDS_KEY] ?: emptySet()

    suspend fun setInsidePlaceIds(placeIds: Set<String>) {
        dataStore.edit { it[INSIDE_PLACE_IDS_KEY] = placeIds }
    }

    /**
     * The closest-100 favorite selection from the last re-rank pass (issue #29) — empty until the
     * first significant-location-change fix arrives. [GeofenceSyncManager.sync] replays this set
     * as-is on every favorites/toggle/app-open change rather than recomputing it, which is what
     * makes un-favoriting a place lazy: it drops out of this set (and thus its geofence) only at
     * the next re-rank, not immediately.
     */
    suspend fun getSelectedFavoriteIds(): Set<String> =
        dataStore.data.first()[SELECTED_FAVORITE_IDS_KEY] ?: emptySet()

    suspend fun setSelectedFavoriteIds(placeIds: Set<String>) {
        dataStore.edit { it[SELECTED_FAVORITE_IDS_KEY] = placeIds }
    }

    private companion object {
        val INSIDE_PLACE_IDS_KEY = stringSetPreferencesKey("geofence_inside_place_ids")
        val SELECTED_FAVORITE_IDS_KEY = stringSetPreferencesKey("geofence_selected_favorite_ids")
    }
}
