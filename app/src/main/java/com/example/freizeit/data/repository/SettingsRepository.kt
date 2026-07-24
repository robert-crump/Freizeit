package com.example.freizeit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The app's first persisted user preference (issue #21): how far a favorite can be
 * before Home's suggestion deck stops considering it.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val suggestionRadiusKm: Flow<Int> = dataStore.data.map { prefs ->
        prefs[RADIUS_KM_KEY]?.coerceAtLeast(1) ?: DEFAULT_RADIUS_KM
    }

    suspend fun setSuggestionRadiusKm(radiusKm: Int) {
        dataStore.edit { it[RADIUS_KM_KEY] = radiusKm.coerceAtLeast(1) }
    }

    companion object {
        const val DEFAULT_RADIUS_KM = 40
        private val RADIUS_KM_KEY = intPreferencesKey("suggestion_radius_km")
    }
}
