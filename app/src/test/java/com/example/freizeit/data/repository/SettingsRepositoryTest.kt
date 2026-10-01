package com.example.freizeit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.util.TravelLimits
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Backed by a temp-file DataStore instead of Robolectric/[android.content.Context] — DataStore's
 * own factory only needs a plain [File], so this stays a fast, deterministic JVM test.
 */
class SettingsRepositoryTest {

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        file = File.createTempFile("settings", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        repository = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `defaults to 40 km before anything is ever set`() = runTest {
        assertEquals(40, repository.suggestionRadiusKm.first())
    }

    @Test
    fun `set radius round trips`() = runTest {
        repository.setSuggestionRadiusKm(75)
        assertEquals(75, repository.suggestionRadiusKm.first())
    }

    @Test
    fun `a non-positive value is coerced up to 1 - never disable the filter by accident`() = runTest {
        repository.setSuggestionRadiusKm(0)
        assertEquals(1, repository.suggestionRadiusKm.first())

        repository.setSuggestionRadiusKm(-5)
        assertEquals(1, repository.suggestionRadiusKm.first())
    }

    @Test
    fun `both notification switches default to off before anything is ever set`() = runTest {
        assertEquals(NotificationTargets(favorites = false, wantToGo = false), repository.notificationTargets.first())
    }

    @Test
    fun `notification switches round trip independently`() = runTest {
        repository.setNotifyWantToGo(true)
        assertEquals(NotificationTargets(favorites = false, wantToGo = true), repository.notificationTargets.first())

        repository.setNotifyFavorites(true)
        repository.setNotifyWantToGo(false)
        assertEquals(NotificationTargets(favorites = true, wantToGo = false), repository.notificationTargets.first())
    }

    @Test
    fun `an enabled pre-74 auto check-in migrates to Favorites on, Want to go off`() = runTest {
        dataStore.edit { it[booleanPreferencesKey("auto_checkin_enabled")] = true }

        assertEquals(true, repository.notifyFavorites.first())
        assertEquals(false, repository.notifyWantToGo.first())
    }

    @Test
    fun `turning Favorites off wins over an old enabled auto check-in`() = runTest {
        dataStore.edit { it[booleanPreferencesKey("auto_checkin_enabled")] = true }

        repository.setNotifyFavorites(false)

        assertEquals(false, repository.notifyFavorites.first())
    }

    @Test
    fun `notification targets map to the verdict values that get geofences`() {
        assertEquals(emptyList<String>(), NotificationTargets(favorites = false, wantToGo = false).verdictValues)
        assertEquals(listOf(Verdict.VALUE_FAVORITE), NotificationTargets(favorites = true, wantToGo = false).verdictValues)
        assertEquals(listOf(Verdict.VALUE_WANT_TO_GO), NotificationTargets(favorites = false, wantToGo = true).verdictValues)
        assertEquals(
            listOf(Verdict.VALUE_FAVORITE, Verdict.VALUE_WANT_TO_GO),
            NotificationTargets(favorites = true, wantToGo = true).verdictValues
        )
    }

    @Test
    fun `restore replaces every setting`() = runTest {
        repository.setSuggestionRadiusKm(75)
        val restored = UserSettings(
            suggestionRadiusKm = 15, notifyFavorites = false, notifyWantToGo = true, themeMode = ThemeMode.DARK,
            travelLimits = TravelLimits(greenMax = 10, orangeMax = 15)
        )

        repository.restore(restored)

        assertEquals(restored, repository.snapshot())
    }

    @Test
    fun `theme follows the system before anything is ever set`() = runTest {
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun `theme mode round trips`() = runTest {
        ThemeMode.entries.forEach { mode ->
            repository.setThemeMode(mode)
            assertEquals(mode, repository.themeMode.first())
        }
    }

    @Test
    fun `travel limits default to short 20, still OK 30`() = runTest {
        assertEquals(TravelLimits(greenMax = 20, orangeMax = 30), repository.travelLimits.first())
    }

    @Test
    fun `travel limits are stored sanitized`() = runTest {
        repository.setTravelLimits(TravelLimits(greenMax = 15, orangeMax = 10))

        assertEquals(TravelLimits(greenMax = 15, orangeMax = 15), repository.travelLimits.first())
    }
}
