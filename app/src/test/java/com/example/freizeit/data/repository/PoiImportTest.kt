package com.example.freizeit.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.PoiParseException
import com.example.freizeit.data.entity.Verdict
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PoiImportTest {

    private lateinit var context: Context
    private lateinit var db: FreizeitDatabase
    private lateinit var repository: PoiRepository
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FreizeitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PoiRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
        tempFiles.forEach { it.delete() }
    }

    private fun fileWith(content: String): Uri {
        val file = File.createTempFile("pois", ".json", context.cacheDir)
        file.writeText(content)
        tempFiles += file
        return Uri.fromFile(file)
    }

    private fun poiJson(vararg pois: String) = """
        {
          "generated": "2026-07-05T17:08:17+00:00",
          "pois": [${pois.joinToString(",")}]
        }
    """

    private fun poi(id: String, category: String, name: String = "Place $id") =
        """{"id": "$id", "category": "$category", "lat": 50.5, "lon": 6.5, "name": "$name"}"""

    @Test
    fun `valid file imports all categories`() = runTest {
        val count = repository.importFrom(
            fileWith(
                poiJson(
                    poi("node/1", "playground"),
                    poi("node/2", "park"),
                    poi("node/3", "cafe"),
                    poi("node/4", "restaurant"),
                    poi("node/5", "ice_cream")
                )
            )
        )

        assertEquals(5, count)
        assertEquals(5, db.poiDao().count())
        val counts = db.poiDao().categoryCounts().first().associate { it.category to it.count }
        assertEquals(
            mapOf("cafe" to 1, "ice_cream" to 1, "park" to 1, "playground" to 1, "restaurant" to 1),
            counts
        )
        assertNotNull(db.importInfoDao().observe().first())
    }

    @Test
    fun `new coarse categories from the widened extractor import alongside the original five`() = runTest {
        val count = repository.importFrom(
            fileWith(
                poiJson(
                    poi("node/1", "playground"),
                    poi("node/2", "shop"),
                    poi("node/3", "tourism"),
                    poi("node/4", "leisure_other"),
                    poi("node/5", "office"),
                    poi("node/6", "craft"),
                    poi("node/7", "historic")
                )
            )
        )

        assertEquals(7, count)
        assertEquals(7, db.poiDao().count())
        val counts = db.poiDao().categoryCounts().first().associate { it.category to it.count }
        assertEquals(
            mapOf(
                "playground" to 1, "shop" to 1, "tourism" to 1, "leisure_other" to 1,
                "office" to 1, "craft" to 1, "historic" to 1
            ),
            counts
        )
    }

    @Test
    fun `verdicted new-category place missing from new file is kept and flagged, same as the original five`() = runTest {
        repository.importFrom(fileWith(poiJson(poi("node/1", "shop"), poi("node/2", "cafe"))))
        db.verdictDao().upsert(
            Verdict(
                placeId = "node/1",
                value = Verdict.VALUE_FAVORITE,
                verdictedAt = System.currentTimeMillis(),
                snapshotName = "Place node/1",
                snapshotLat = 50.5,
                snapshotLon = 6.5,
                snapshotCategory = "shop"
            )
        )

        repository.importFrom(fileWith(poiJson(poi("node/2", "cafe"))))

        val kept = db.poiDao().getById("node/1")
        assertNotNull(kept)
        assertTrue(kept!!.missingFromOsm)
    }

    @Test
    fun `re-importing the same file is idempotent`() = runTest {
        val uri = fileWith(poiJson(poi("node/1", "cafe"), poi("way/2", "park")))

        repository.importFrom(uri)
        repository.importFrom(uri)

        assertEquals(2, db.poiDao().count())
        assertEquals(0, db.poiDao().missingCount().first())
    }

    @Test
    fun `unverdicted place missing from new file is dropped`() = runTest {
        repository.importFrom(fileWith(poiJson(poi("node/1", "cafe"), poi("node/2", "park"))))
        repository.importFrom(fileWith(poiJson(poi("node/1", "cafe"))))

        assertEquals(1, db.poiDao().count())
        assertNull(db.poiDao().getById("node/2"))
    }

    @Test
    fun `verdicted place missing from new file is kept and flagged`() = runTest {
        repository.importFrom(fileWith(poiJson(poi("node/1", "cafe"), poi("node/2", "park"))))
        db.verdictDao().upsert(
            Verdict(
                placeId = "node/2",
                value = Verdict.VALUE_FAVORITE,
                verdictedAt = System.currentTimeMillis(),
                snapshotName = "Place node/2",
                snapshotLat = 50.5,
                snapshotLon = 6.5,
                snapshotCategory = "park"
            )
        )

        repository.importFrom(fileWith(poiJson(poi("node/1", "cafe"))))

        val kept = db.poiDao().getById("node/2")
        assertNotNull(kept)
        assertTrue(kept!!.missingFromOsm)
        assertEquals(1, db.poiDao().missingCount().first())

        // A later re-import that still lacks the place keeps it flagged.
        repository.importFrom(fileWith(poiJson(poi("node/1", "cafe"))))
        assertTrue(db.poiDao().getById("node/2")!!.missingFromOsm)

        // If the place returns to OSM, the flag clears.
        repository.importFrom(fileWith(poiJson(poi("node/1", "cafe"), poi("node/2", "park"))))
        assertEquals(false, db.poiDao().getById("node/2")!!.missingFromOsm)
    }

    @Test
    fun `malformed file throws and leaves the database untouched`() = runTest {
        repository.importFrom(fileWith(poiJson(poi("node/1", "cafe"))))
        val infoBefore = db.importInfoDao().observe().first()

        assertThrows(PoiParseException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.importFrom(fileWith("""{"pois": [{"id": "node/2"}]}"""))
            }
        }

        assertEquals(1, db.poiDao().count())
        assertNotNull(db.poiDao().getById("node/1"))
        assertEquals(false, db.poiDao().getById("node/1")!!.missingFromOsm)
        assertEquals(infoBefore, db.importInfoDao().observe().first())
    }

    @Test
    fun `file that is not JSON throws and leaves the database empty`() = runTest {
        assertThrows(PoiParseException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.importFrom(fileWith("definitely not json"))
            }
        }
        assertEquals(0, db.poiDao().count())
        assertNull(db.importInfoDao().observe().first())
    }
}
