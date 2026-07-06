package com.example.freizeit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.PendingVisit
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
class PendingVisitDaoTest {

    private lateinit var db: FreizeitDatabase

    private fun visit(placeId: String, wentAt: Long = 1_000L) = PendingVisit(
        placeId = placeId,
        snapshotName = "Café Sonne",
        snapshotCategory = "cafe",
        snapshotLat = 50.9,
        snapshotLon = 6.9,
        wentAt = wentAt
    )

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
    fun `set stores the visit`() = runTest {
        db.pendingVisitDao().set(visit("node/1"))
        assertEquals("node/1", db.pendingVisitDao().observe().first()?.placeId)
    }

    @Test
    fun `a later Go tap overwrites the earlier one — only the latest matters`() = runTest {
        db.pendingVisitDao().set(visit("node/1"))
        db.pendingVisitDao().set(visit("node/2"))

        assertEquals("node/2", db.pendingVisitDao().observe().first()?.placeId)
    }

    @Test
    fun `clear removes the pending visit`() = runTest {
        db.pendingVisitDao().set(visit("node/1"))
        db.pendingVisitDao().clear()

        assertNull(db.pendingVisitDao().observe().first())
    }
}
