package com.example.freizeit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.util.TravelLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    /**
     * Check-in notifications near favorites (#74; issue #24's single "auto check-in" switch
     * before). Off by default, only ever flipped on after the user opts in from Settings. An
     * install that had auto check-in on reads as Favorites on until this switch is first written.
     */
    val notifyFavorites: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[NOTIFY_FAVORITES_KEY] ?: prefs[AUTO_CHECKIN_ENABLED_KEY] ?: false
    }

    suspend fun setNotifyFavorites(enabled: Boolean) {
        dataStore.edit { it[NOTIFY_FAVORITES_KEY] = enabled }
    }

    /** Check-in notifications near want-to-go places (#74). Off by default. */
    val notifyWantToGo: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[NOTIFY_WANT_TO_GO_KEY] ?: false
    }

    suspend fun setNotifyWantToGo(enabled: Boolean) {
        dataStore.edit { it[NOTIFY_WANT_TO_GO_KEY] = enabled }
    }

    /** Both notification switches together — what geofence registration keys off. */
    val notificationTargets: Flow<NotificationTargets> =
        combine(notifyFavorites, notifyWantToGo) { favorites, wantToGo ->
            NotificationTargets(favorites, wantToGo)
        }

    /** Follows the system until the user picks Light or Dark; an unknown stored value falls back too. */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE_KEY] } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    /** Travel-time color limits, shared by every mode; defaults until first set. */
    val travelLimits: Flow<TravelLimits> = dataStore.data.map { prefs ->
        val green = prefs[TRAVEL_GREEN_MAX_KEY]
        val orange = prefs[TRAVEL_ORANGE_MAX_KEY]
        if (green == null || orange == null) TravelLimits() else TravelLimits(green, orange).sanitized()
    }

    suspend fun setTravelLimits(limits: TravelLimits) {
        val clean = limits.sanitized()
        dataStore.edit {
            it[TRAVEL_GREEN_MAX_KEY] = clean.greenMax
            it[TRAVEL_ORANGE_MAX_KEY] = clean.orangeMax
        }
    }

    /** Every user setting at once, for the backup file (#74). */
    suspend fun snapshot(): UserSettings = UserSettings(
        suggestionRadiusKm = suggestionRadiusKm.first(),
        notifyFavorites = notifyFavorites.first(),
        notifyWantToGo = notifyWantToGo.first(),
        themeMode = themeMode.first(),
        travelLimits = travelLimits.first()
    )

    /** Replaces every user setting with [settings] in one edit — a backup restore. */
    suspend fun restore(settings: UserSettings) {
        dataStore.edit {
            it[RADIUS_KM_KEY] = settings.suggestionRadiusKm.coerceAtLeast(1)
            it[NOTIFY_FAVORITES_KEY] = settings.notifyFavorites
            it[NOTIFY_WANT_TO_GO_KEY] = settings.notifyWantToGo
            it[THEME_MODE_KEY] = settings.themeMode.name
            val limits = settings.travelLimits.sanitized()
            it[TRAVEL_GREEN_MAX_KEY] = limits.greenMax
            it[TRAVEL_ORANGE_MAX_KEY] = limits.orangeMax
            it.remove(AUTO_CHECKIN_ENABLED_KEY)
        }
    }

    companion object {
        const val DEFAULT_RADIUS_KM = 40
        private val RADIUS_KM_KEY = intPreferencesKey("suggestion_radius_km")
        /** The pre-#74 single switch; only read as [notifyFavorites]' fallback. */
        private val AUTO_CHECKIN_ENABLED_KEY = booleanPreferencesKey("auto_checkin_enabled")
        private val NOTIFY_FAVORITES_KEY = booleanPreferencesKey("notify_favorites")
        private val NOTIFY_WANT_TO_GO_KEY = booleanPreferencesKey("notify_want_to_go")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val TRAVEL_GREEN_MAX_KEY = intPreferencesKey("travel_green_max")
        private val TRAVEL_ORANGE_MAX_KEY = intPreferencesKey("travel_orange_max")
    }
}

/** Which verdicts get check-in notifications (#74). */
data class NotificationTargets(val favorites: Boolean, val wantToGo: Boolean) {
    val any: Boolean get() = favorites || wantToGo

    /** The verdict values whose places get a geofence; empty when both switches are off. */
    val verdictValues: List<String>
        get() = listOfNotNull(
            Verdict.VALUE_FAVORITE.takeIf { favorites },
            Verdict.VALUE_WANT_TO_GO.takeIf { wantToGo }
        )
}

/** The user settings a backup carries (#74). */
data class UserSettings(
    val suggestionRadiusKm: Int = SettingsRepository.DEFAULT_RADIUS_KM,
    val notifyFavorites: Boolean = false,
    val notifyWantToGo: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val travelLimits: TravelLimits = TravelLimits()
)
