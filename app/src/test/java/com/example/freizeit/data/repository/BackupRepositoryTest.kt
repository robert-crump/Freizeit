package com.example.freizeit.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.BackupParseException
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.PoiOverride
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.entity.Visit
import kotlinx.coroutines.flow.first
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: FreizeitDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: BackupRepository
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        freshInstall()
    }

    /** A fresh database and settings store — what a reinstall (or wiped app data) looks like. */
    private fun freshInstall() {
        if (::db.isInitialized) db.close()
        db = Room.inMemoryDatabaseBuilder(context, FreizeitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settingsFile = File.createTempFile("settings", ".preferences_pb", context.cacheDir)
        settingsFile.delete()
        tempFiles += settingsFile
        settings = SettingsRepository(PreferenceDataStoreFactory.create(produceFile = { settingsFile }))
        repository = BackupRepository(context, db, settings)
    }

    @After
    fun tearDown() {
        db.close()
        tempFiles.forEach { it.delete() }
    }

    private fun newFileUri(): Uri {
        val file = File.createTempFile("backup", ".json", context.cacheDir)
        tempFiles += file
        return Uri.fromFile(file)
    }

    private fun fileWith(content: String): Uri {
        val file = File.createTempFile("backup", ".json", context.cacheDir)
        file.writeText(content)
        tempFiles += file
        return Uri.fromFile(file)
    }

    private fun verdict(placeId: String, value: String = Verdict.VALUE_FAVORITE, verdictedAt: Long = 1_000L) = Verdict(
        placeId = placeId,
        value = value,
        verdictedAt = verdictedAt,
        snapshotName = "Place $placeId",
        snapshotLat = 50.9,
        snapshotLon = 6.9,
        snapshotCategory = "cafe"
    )

    private fun nameOverride(placeId: String, name: String) = PoiOverride(placeId = placeId, name = name)

    private fun customPoi(id: String, name: String = "Our Place", category: String = "cafe") = CustomPoi(
        id = id,
        category = category,
        lat = 50.9,
        lon = 6.9,
        name = name,
        street = "Beispielstraße",
        housenumber = "1",
        postcode = "52062",
        city = "Aachen"
    )

    @Test
    fun `round trip preserves verdicts and overrides, including cooldown-relevant fields`() = runTest {
        db.verdictDao().upsert(verdict("node/1", value = Verdict.VALUE_FAVORITE, verdictedAt = 12_345L))
        db.verdictDao().upsert(verdict("node/2", value = "other", verdictedAt = 67_890L))
        db.poiOverrideDao().upsert(nameOverride("node/3", "Home playground"))
        db.poiOverrideDao().upsert(
            PoiOverride("node/4", name = "Oma's park", category = "playground", city = "Würselen", hidden = true)
        )

        val uri = newFileUri()
        val exported = repository.exportTo(uri)
        assertEquals(4, exported)

        freshInstall()

        val imported = repository.importFrom(uri)
        assertEquals(4, imported)

        val verdicts = db.verdictDao().getAll().associateBy { it.placeId }
        assertEquals(Verdict.VALUE_FAVORITE, verdicts["node/1"]?.value)
        assertEquals(12_345L, verdicts["node/1"]?.verdictedAt)
        assertEquals("other", verdicts["node/2"]?.value)

        val overrides = db.poiOverrideDao().getAll().associateBy { it.placeId }
        assertEquals(nameOverride("node/3", "Home playground"), overrides["node/3"])
        assertEquals(
            PoiOverride("node/4", name = "Oma's park", category = "playground", city = "Würselen", hidden = true),
            overrides["node/4"]
        )
    }

    @Test
    fun `round trip preserves custom POIs, including optional address fields`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/1", "Our Garden"))
        db.customPoiDao().upsert(customPoi("custom/2", "Secret Playground", category = "playground").copy(street = null))

        val uri = newFileUri()
        val exported = repository.exportTo(uri)
        assertEquals(2, exported)

        freshInstall()

        val imported = repository.importFrom(uri)
        assertEquals(2, imported)

        val customPois = db.customPoiDao().getAll().associateBy { it.id }
        assertEquals("Our Garden", customPois["custom/1"]?.name)
        assertEquals("cafe", customPois["custom/1"]?.category)
        assertEquals("Beispielstraße", customPois["custom/1"]?.street)
        assertEquals("Aachen", customPois["custom/1"]?.city)
        assertEquals("Secret Playground", customPois["custom/2"]?.name)
        assertEquals("playground", customPois["custom/2"]?.category)
        assertEquals(null, customPois["custom/2"]?.street)
    }

    private fun visit(placeId: String, visitedAt: Long, source: String = Visit.SOURCE_MANUAL) = Visit(
        placeId = placeId,
        visitedAt = visitedAt,
        source = source,
        snapshotName = "Place $placeId",
        snapshotLat = 50.9,
        snapshotLon = 6.9,
        snapshotCategory = "playground"
    )

    @Test
    fun `round trip restores verdicts, overrides, custom places, visits and settings after a wipe`() = runTest {
        db.verdictDao().upsert(verdict("node/1", value = Verdict.VALUE_WANT_TO_GO))
        db.poiOverrideDao().upsert(nameOverride("node/2", "Home playground"))
        db.customPoiDao().upsert(customPoi("custom/1"))
        db.visitDao().insert(visit("node/1", visitedAt = 1_000L))
        db.visitDao().insert(visit("custom/1", visitedAt = 2_000L, source = Visit.SOURCE_NOTIFICATION))
        settings.setSuggestionRadiusKm(25)
        settings.setNotifyFavorites(true)
        settings.setNotifyWantToGo(true)
        settings.setThemeMode(ThemeMode.DARK)

        val uri = newFileUri()
        assertEquals(5, repository.exportTo(uri))

        freshInstall()
        assertEquals(5, repository.importFrom(uri))

        assertEquals(listOf(Verdict.VALUE_WANT_TO_GO), db.verdictDao().getAll().map { it.value })
        assertEquals(listOf(nameOverride("node/2", "Home playground")), db.poiOverrideDao().getAll())
        assertEquals(listOf("custom/1"), db.customPoiDao().getAll().map { it.id })
        val visits = db.visitDao().getAll()
        assertEquals(listOf("custom/1", "node/1"), visits.map { it.placeId })
        assertEquals(listOf(2_000L, 1_000L), visits.map { it.visitedAt })
        assertEquals(Visit.SOURCE_NOTIFICATION, visits.first().source)
        assertEquals("Place node/1", visits.last().snapshotName)
        assertEquals(
            UserSettings(suggestionRadiusKm = 25, notifyFavorites = true, notifyWantToGo = true, themeMode = ThemeMode.DARK),
            settings.snapshot()
        )
    }

    @Test
    fun `import replaces the existing history wholesale`() = runTest {
        db.visitDao().insert(visit("node/stale", visitedAt = 1L))

        val uri = fileWith(
            """
            {
              "exportedAt": 1,
              "visits": [
                {"placeId": "node/1", "visitedAt": 500, "source": "manual", "snapshotName": "Park",
                 "snapshotLat": 50.9, "snapshotLon": 6.9, "snapshotCategory": "park"}
              ]
            }
            """.trimIndent()
        )

        repository.importFrom(uri)

        assertEquals(listOf("node/1"), db.visitDao().getAll().map { it.placeId })
    }

    @Test
    fun `an old backup without visits or settings clears the history and resets settings to defaults`() = runTest {
        db.visitDao().insert(visit("node/stale", visitedAt = 1L))
        settings.setSuggestionRadiusKm(80)
        settings.setNotifyFavorites(true)
        settings.setThemeMode(ThemeMode.LIGHT)

        val uri = fileWith(
            """
            {
              "exportedAt": 1,
              "verdicts": [],
              "customNames": [{"placeId": "node/2", "customName": "Home"}]
            }
            """.trimIndent()
        )

        assertEquals(1, repository.importFrom(uri))

        assertEquals(0, db.visitDao().getAll().size)
        assertEquals(listOf(nameOverride("node/2", "Home")), db.poiOverrideDao().getAll())
        assertEquals(UserSettings(), settings.snapshot())
    }

    @Test
    fun `odd settings values fall back to their defaults`() = runTest {
        val uri = fileWith(
            """
            {
              "exportedAt": 1,
              "settings": {"suggestionRadiusKm": 0, "notifyFavorites": "yes", "themeMode": "SEPIA",
                           "notifyWantToGo": true}
            }
            """.trimIndent()
        )

        repository.importFrom(uri)

        assertEquals(UserSettings(notifyWantToGo = true), settings.snapshot())
        assertEquals(false, settings.notifyFavorites.first())
    }

    @Test
    fun `import replaces existing verdicts and overrides wholesale, reading old custom names as name overrides`() = runTest {
        db.verdictDao().upsert(verdict("node/stale"))
        db.poiOverrideDao().upsert(nameOverride("node/stale", "Stale"))

        val uri = fileWith(
            """
            {
              "exportedAt": 1,
              "verdicts": [
                {"placeId": "node/1", "value": "favorite", "verdictedAt": 100, "snapshotName": "Place",
                 "snapshotLat": 50.9, "snapshotLon": 6.9, "snapshotCategory": "cafe"}
              ],
              "customNames": [
                {"placeId": "node/2", "customName": "Home"}
              ]
            }
            """.trimIndent()
        )

        repository.importFrom(uri)

        assertEquals(listOf("node/1"), db.verdictDao().getAll().map { it.placeId })
        assertEquals(listOf(nameOverride("node/2", "Home")), db.poiOverrideDao().getAll())
    }

    @Test
    fun `import replaces existing custom POIs wholesale`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/stale", "Stale"))

        val uri = fileWith(
            """
            {
              "exportedAt": 1,
              "customPois": [
                {"id": "custom/1", "category": "cafe", "lat": 50.9, "lon": 6.9, "name": "Fresh"}
              ]
            }
            """.trimIndent()
        )

        repository.importFrom(uri)

        assertEquals(listOf("Fresh"), db.customPoiDao().getAll().map { it.name })
    }

    @Test
    fun `an old backup file without a customPois section imports cleanly`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/stale", "Stale"))

        val uri = fileWith(
            """
            {
              "exportedAt": 1,
              "verdicts": [],
              "customNames": []
            }
            """.trimIndent()
        )

        repository.importFrom(uri)

        assertEquals(0, db.customPoiDao().getAll().size)
    }

    @Test
    fun `malformed file throws and leaves the database untouched`() = runTest {
        db.verdictDao().upsert(verdict("node/1"))
        val uri = fileWith("""{"verdicts": [{"placeId": "node/2"}]}""")

        assertThrows(BackupParseException::class.java) {
            kotlinx.coroutines.runBlocking { repository.importFrom(uri) }
        }

        assertEquals(listOf("node/1"), db.verdictDao().getAll().map { it.placeId })
    }

    @Test
    fun `file that is not JSON throws and leaves the database untouched`() = runTest {
        db.poiOverrideDao().upsert(nameOverride("node/1", "Home"))
        val uri = fileWith("definitely not json")

        assertThrows(BackupParseException::class.java) {
            kotlinx.coroutines.runBlocking { repository.importFrom(uri) }
        }

        assertEquals(listOf("Home"), db.poiOverrideDao().getAll().map { it.name })
    }
}
