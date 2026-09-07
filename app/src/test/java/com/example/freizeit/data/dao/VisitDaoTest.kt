package com.example.freizeit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Visit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisitDaoTest {

    private lateinit var db: FreizeitDatabase

    private val poi = Poi(id = "node/1", category = "cafe", lat = 50.9, lon = 6.9, name = "Café Sonne")

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FreizeitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `checkIn logs a visit snapshotting the poi with source manual`() = runTest {
        db.visitDao().checkIn(poi)

        val stored = db.visitDao().getAll()
        assertEquals(1, stored.size)
        assertEquals("node/1", stored[0].placeId)
        assertEquals(Visit.SOURCE_MANUAL, stored[0].source)
        assertEquals("Café Sonne", stored[0].snapshotName)
        assertEquals("cafe", stored[0].snapshotCategory)
    }

    @Test
    fun `checkIn appends rather than replacing prior visits to the same place`() = runTest {
        db.visitDao().checkIn(poi)
        db.visitDao().checkIn(poi)

        assertEquals(2, db.visitDao().observeAll().first().size)
    }

    @Test
    fun `observeAll orders most recent visit first`() = runTest {
        db.visitDao().insert(
            Visit(
                placeId = "node/1",
                visitedAt = 1_000L,
                source = Visit.SOURCE_MANUAL,
                snapshotName = "Older",
                snapshotLat = 50.9,
                snapshotLon = 6.9,
                snapshotCategory = "cafe"
            )
        )
        db.visitDao().insert(
            Visit(
                placeId = "node/1",
                visitedAt = 2_000L,
                source = Visit.SOURCE_MANUAL,
                snapshotName = "Newer",
                snapshotLat = 50.9,
                snapshotLon = 6.9,
                snapshotCategory = "cafe"
            )
        )

        val all = db.visitDao().observeAll().first()
        assertEquals("Newer", all[0].snapshotName)
        assertEquals("Older", all[1].snapshotName)
    }

    @Test
    fun `lastVisitedAt returns the most recent visit regardless of source`() = runTest {
        db.visitDao().checkIn(poi, source = Visit.SOURCE_MANUAL)
        db.visitDao().checkIn(poi.copy(id = "node/1"), source = Visit.SOURCE_NOTIFICATION)

        assertEquals(db.visitDao().getAll().maxOf { it.visitedAt }, db.visitDao().lastVisitedAt("node/1"))
    }

    @Test
    fun `lastVisitedAt is null for a place with no visits`() = runTest {
        assertEquals(null, db.visitDao().lastVisitedAt("node/never-visited"))
    }

    @Test
    fun `checkIn stores a caller-supplied visitedAt instead of stamping now`() = runTest {
        db.visitDao().checkIn(poi, visitedAt = 5_000L)

        assertEquals(5_000L, db.visitDao().getAll().single().visitedAt)
    }

    @Test
    fun `checkIn returns the new visit's id, usable to delete it again`() = runTest {
        val id = db.visitDao().checkIn(poi)

        db.visitDao().deleteByIds(listOf(id))

        assertEquals(0, db.visitDao().getAll().size)
    }

    @Test
    fun `deleteByPlaceId removes every visit logged against that place`() = runTest {
        db.visitDao().checkIn(poi)
        db.visitDao().checkIn(poi)
        val otherPoi = poi.copy(id = "node/other")
        db.visitDao().checkIn(otherPoi)

        db.visitDao().deleteByPlaceId("node/1")

        val remaining = db.visitDao().getAll()
        assertEquals(1, remaining.size)
        assertEquals("node/other", remaining[0].placeId)
    }

    @Test
    fun `deleteByPlaceId is a no-op for a place with no visits`() = runTest {
        db.visitDao().checkIn(poi)

        db.visitDao().deleteByPlaceId("node/never-visited")

        assertEquals(1, db.visitDao().getAll().size)
    }

    /** #49: reimport-time merge re-keys every visit logged against a custom POI onto the
     *  matched OSM place, rather than deleting them the way #47's outright delete does. */
    @Test
    fun `rekey moves every visit from the old placeId to the new one`() = runTest {
        db.visitDao().checkIn(poi.copy(id = "custom/1"))
        db.visitDao().checkIn(poi.copy(id = "custom/1"))
        db.visitDao().checkIn(poi.copy(id = "node/other"))

        db.visitDao().rekey("custom/1", "node/2")

        val all = db.visitDao().getAll()
        assertEquals(2, all.count { it.placeId == "node/2" })
        assertEquals(0, all.count { it.placeId == "custom/1" })
        assertEquals(1, all.count { it.placeId == "node/other" })
    }

    @Test
    fun `rekey onto a placeId that already has visits keeps both sets, all under the new id`() = runTest {
        db.visitDao().checkIn(poi.copy(id = "custom/1"))
        db.visitDao().checkIn(poi.copy(id = "node/2"))

        db.visitDao().rekey("custom/1", "node/2")

        assertEquals(2, db.visitDao().getAll().count { it.placeId == "node/2" })
    }

    @Test
    fun `rekey is a no-op when the old placeId has no visits`() = runTest {
        db.visitDao().checkIn(poi.copy(id = "node/other"))

        db.visitDao().rekey("custom/never-visited", "node/2")

        assertEquals(1, db.visitDao().getAll().size)
        assertEquals(0, db.visitDao().getAll().count { it.placeId == "node/2" })
    }
}
