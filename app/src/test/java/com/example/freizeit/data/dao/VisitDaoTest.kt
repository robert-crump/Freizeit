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
}
