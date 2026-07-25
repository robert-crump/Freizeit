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

    private companion object {
        val INSIDE_PLACE_IDS_KEY = stringSetPreferencesKey("geofence_inside_place_ids")
    }
}
