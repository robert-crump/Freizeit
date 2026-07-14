package com.example.freizeit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
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
}
