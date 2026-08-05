package com.example.freizeit.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.BackupParseException
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.PoiCustomName
import com.example.freizeit.data.entity.Verdict
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
    private lateinit var repository: BackupRepository
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FreizeitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(context, db)
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

    private fun customName(placeId: String, name: String) = PoiCustomName(placeId = placeId, customName = name)

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
    fun `round trip preserves verdicts and custom names, including cooldown-relevant fields`() = runTest {
        db.verdictDao().upsert(verdict("node/1", value = Verdict.VALUE_FAVORITE, verdictedAt = 12_345L))
        db.verdictDao().upsert(verdict("node/2", value = "other", verdictedAt = 67_890L))
        db.poiCustomNameDao().upsert(customName("node/3", "Home playground"))
        db.poiCustomNameDao().upsert(customName("node/4", "Oma's park"))

        val uri = newFileUri()
        val exported = repository.exportTo(uri)
        assertEquals(4, exported)

        // Simulate wiping app data: fresh in-memory database.
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, FreizeitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(context, db)

        val imported = repository.importFrom(uri)
        assertEquals(4, imported)

        val verdicts = db.verdictDao().getAll().associateBy { it.placeId }
        assertEquals(Verdict.VALUE_FAVORITE, verdicts["node/1"]?.value)
        assertEquals(12_345L, verdicts["node/1"]?.verdictedAt)
        assertEquals("other", verdicts["node/2"]?.value)

        val customNames = db.poiCustomNameDao().getAll()
        assertEquals(setOf("Home playground", "Oma's park"), customNames.map { it.customName }.toSet())
    }

    @Test
    fun `round trip preserves custom POIs, including optional address fields`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/1", "Our Garden"))
        db.customPoiDao().upsert(customPoi("custom/2", "Secret Playground", category = "playground").copy(street = null))

        val uri = newFileUri()
        val exported = repository.exportTo(uri)
        assertEquals(2, exported)

        db.close()
        db = Room.inMemoryDatabaseBuilder(context, FreizeitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(context, db)

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

    @Test
    fun `import replaces existing verdicts and custom names wholesale`() = runTest {
        db.verdictDao().upsert(verdict("node/stale"))
        db.poiCustomNameDao().upsert(customName("node/stale", "Stale"))

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
        assertEquals(listOf("Home"), db.poiCustomNameDao().getAll().map { it.customName })
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
        db.poiCustomNameDao().upsert(customName("node/1", "Home"))
        val uri = fileWith("definitely not json")

        assertThrows(BackupParseException::class.java) {
            kotlinx.coroutines.runBlocking { repository.importFrom(uri) }
        }

        assertEquals(listOf("Home"), db.poiCustomNameDao().getAll().map { it.customName })
    }
}
