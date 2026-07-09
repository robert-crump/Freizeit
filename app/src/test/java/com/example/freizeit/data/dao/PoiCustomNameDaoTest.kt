package com.example.freizeit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
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
class PoiCustomNameDaoTest {

    private lateinit var db: FreizeitDatabase

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
    fun `setCustomName creates an entry`() = runTest {
        db.poiCustomNameDao().setCustomName("node/1", "Our Playground")

        assertEquals("Our Playground", db.poiCustomNameDao().getAll().first().customName)
    }

    @Test
    fun `setCustomName again replaces the value in place`() = runTest {
        db.poiCustomNameDao().setCustomName("node/1", "First Name")
        db.poiCustomNameDao().setCustomName("node/1", "Second Name")

        assertEquals("Second Name", db.poiCustomNameDao().getAll().first().customName)
        assertEquals(1, db.poiCustomNameDao().observeAll().first().size)
    }

    @Test
    fun `setCustomName with null clears the entry`() = runTest {
        db.poiCustomNameDao().setCustomName("node/1", "A Name")
        db.poiCustomNameDao().setCustomName("node/1", null)

        assertEquals(0, db.poiCustomNameDao().observeAll().first().size)
    }

    @Test
    fun `setCustomName with a blank string clears the entry`() = runTest {
        db.poiCustomNameDao().setCustomName("node/1", "A Name")
        db.poiCustomNameDao().setCustomName("node/1", "   ")

        assertEquals(0, db.poiCustomNameDao().observeAll().first().size)
    }

    @Test
    fun `no entry means no custom name`() = runTest {
        assertNull(db.poiCustomNameDao().getAll().firstOrNull())
    }
}
