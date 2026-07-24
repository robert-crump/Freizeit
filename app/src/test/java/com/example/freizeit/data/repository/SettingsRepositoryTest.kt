package com.example.freizeit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
}
