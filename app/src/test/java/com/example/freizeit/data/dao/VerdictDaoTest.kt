package com.example.freizeit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.entity.toPoi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VerdictDaoTest {

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
    fun `setVerdict creates a verdict snapshotting the poi`() = runTest {
        db.verdictDao().setVerdict(poi, Verdict.VALUE_FAVORITE)

        val stored = db.verdictDao().getByPlaceId("node/1")
        assertEquals(Verdict.VALUE_FAVORITE, stored?.value)
        assertEquals("Café Sonne", stored?.snapshotName)
        assertEquals("cafe", stored?.snapshotCategory)
    }

    @Test
    fun `setVerdict again changes the value in place`() = runTest {
        db.verdictDao().setVerdict(poi, Verdict.VALUE_FAVORITE)
        db.verdictDao().setVerdict(poi, "other")

        assertEquals("other", db.verdictDao().getByPlaceId("node/1")?.value)
        assertEquals(1, db.verdictDao().observeAll().first().size)
    }

    @Test
    fun `setVerdict with null clears the verdict`() = runTest {
        db.verdictDao().setVerdict(poi, Verdict.VALUE_FAVORITE)
        db.verdictDao().setVerdict(poi, null)

        assertNull(db.verdictDao().getByPlaceId("node/1"))
        assertEquals(0, db.verdictDao().observeAll().first().size)
    }

    /** #48: a custom POI (#45) reaches setVerdict as a plain Poi via CustomPoi.toPoi(), so it
     *  gets a Verdict row exactly like an OSM one — no separate code path needed. */
    @Test
    fun `setVerdict works identically for a custom poi projected via toPoi`() = runTest {
        val customPoi = CustomPoi(
            id = "custom/1",
            category = "cafe",
            lat = 50.91,
            lon = 6.91,
            name = "Our Café"
        )

        db.verdictDao().setVerdict(customPoi.toPoi(), Verdict.VALUE_FAVORITE)

        val stored = db.verdictDao().getByPlaceId("custom/1")
        assertEquals(Verdict.VALUE_FAVORITE, stored?.value)
        assertEquals("Our Café", stored?.snapshotName)
        assertEquals("cafe", stored?.snapshotCategory)
    }

    /** #49: reimport-time merge re-keys a custom POI's verdict onto the matched OSM place. */
    @Test
    fun `rekey moves a verdict from the old placeId to the new one`() = runTest {
        db.verdictDao().setVerdict(poi.copy(id = "custom/1"), Verdict.VALUE_FAVORITE)

        db.verdictDao().rekey("custom/1", "node/2")

        assertNull(db.verdictDao().getByPlaceId("custom/1"))
        assertEquals(Verdict.VALUE_FAVORITE, db.verdictDao().getByPlaceId("node/2")?.value)
    }

    @Test
    fun `rekey onto a placeId that already has a verdict keeps the old id's data`() = runTest {
        db.verdictDao().setVerdict(poi.copy(id = "custom/1", name = "Ours"), Verdict.VALUE_FAVORITE)
        db.verdictDao().setVerdict(poi.copy(id = "node/2", name = "Theirs"), "other")

        db.verdictDao().rekey("custom/1", "node/2")

        val merged = db.verdictDao().getByPlaceId("node/2")
        assertEquals(Verdict.VALUE_FAVORITE, merged?.value)
        assertEquals("Ours", merged?.snapshotName)
        assertEquals(1, db.verdictDao().observeAll().first().size)
    }

    @Test
    fun `rekey is a no-op when the old placeId has no verdict`() = runTest {
        db.verdictDao().rekey("custom/never-verdicted", "node/2")

        assertNull(db.verdictDao().getByPlaceId("node/2"))
        assertEquals(0, db.verdictDao().observeAll().first().size)
    }
}
