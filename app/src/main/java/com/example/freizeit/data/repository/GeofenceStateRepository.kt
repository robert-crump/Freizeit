package com.example.freizeit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Runtime state for issue #28's geofencing, not a user-facing setting — deliberately separate
 * from [SettingsRepository]. Tracks which favorites the device is currently confirmed to be
 * dwelling at (per DWELL/EXIT broadcasts — a mere ENTER doesn't count, since passing by
 * shouldn't prompt a check-in) so the receiver can re-derive "closest of the currently-dwelling
 * ones" on every transition without needing a live location fix of its own.
 */
class GeofenceStateRepository(private val dataStore: DataStore<Preferences>) {

    suspend fun getDwellingPlaceIds(): Set<String> =
        dataStore.data.first()[DWELLING_PLACE_IDS_KEY] ?: emptySet()

    suspend fun setDwellingPlaceIds(placeIds: Set<String>) {
        dataStore.edit { it[DWELLING_PLACE_IDS_KEY] = placeIds }
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

    /**
     * Which favorite ids currently have a live Play Services geofence registered.
     * [GeofenceSyncManager.register] diffs against this rather than blindly removing and
     * re-adding everything, so an unrelated favorites change doesn't reset the loitering clock
     * on a geofence the user happens to be mid-dwell at.
     */
    suspend fun getRegisteredFavoriteIds(): Set<String> =
        dataStore.data.first()[REGISTERED_FAVORITE_IDS_KEY] ?: emptySet()

    suspend fun setRegisteredFavoriteIds(placeIds: Set<String>) {
        dataStore.edit { it[REGISTERED_FAVORITE_IDS_KEY] = placeIds }
    }

    /**
     * Which favorite the single outstanding check-in notification (if any) is for — lets
     * [GeofenceSyncManager.register] tell whether a favorite losing its geofence (e.g.
     * un-favorited mid-dwell) is the one currently on screen, so it can cancel that notification
     * immediately instead of leaving a stale prompt up until the next transition (issue #42).
     */
    suspend fun getActiveNotificationPlaceId(): String? =
        dataStore.data.first()[ACTIVE_NOTIFICATION_PLACE_ID_KEY]

    suspend fun setActiveNotificationPlaceId(placeId: String?) {
        dataStore.edit {
            if (placeId == null) it.remove(ACTIVE_NOTIFICATION_PLACE_ID_KEY) else it[ACTIVE_NOTIFICATION_PLACE_ID_KEY] = placeId
        }
    }

    private companion object {
        val DWELLING_PLACE_IDS_KEY = stringSetPreferencesKey("geofence_dwelling_place_ids")
        val SELECTED_FAVORITE_IDS_KEY = stringSetPreferencesKey("geofence_selected_favorite_ids")
        val REGISTERED_FAVORITE_IDS_KEY = stringSetPreferencesKey("geofence_registered_favorite_ids")
        val ACTIVE_NOTIFICATION_PLACE_ID_KEY = stringPreferencesKey("geofence_active_notification_place_id")
    }
}
