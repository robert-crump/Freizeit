package com.example.freizeit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.PoiOverride
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PoiOverrideDaoTest {

    private lateinit var db: FreizeitDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FreizeitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `setOverride stores a row and replaces it in place`() = runTest {
        db.poiOverrideDao().setOverride("node/1", PoiOverride("node/1", name = "First"))
        db.poiOverrideDao().setOverride("node/1", PoiOverride("node/1", city = "Aachen"))

        assertEquals(listOf(PoiOverride("node/1", city = "Aachen")), db.poiOverrideDao().getAll())
    }

    @Test
    fun `setOverride with null or an empty override deletes the row`() = runTest {
        db.poiOverrideDao().setOverride("node/1", PoiOverride("node/1", name = "A"))
        db.poiOverrideDao().setOverride("node/1", null)
        assertNull(db.poiOverrideDao().getById("node/1"))

        db.poiOverrideDao().setOverride("node/2", PoiOverride("node/2", name = "B"))
        db.poiOverrideDao().setOverride("node/2", PoiOverride("node/2"))
        assertNull(db.poiOverrideDao().getById("node/2"))
    }

    @Test
    fun `hide keeps existing edits`() = runTest {
        db.poiOverrideDao().upsert(PoiOverride("node/1", name = "Our Park"))
        db.poiOverrideDao().hide("node/1")
        db.poiOverrideDao().hide("node/2")

        assertEquals(PoiOverride("node/1", name = "Our Park", hidden = true), db.poiOverrideDao().getById("node/1"))
        assertEquals(PoiOverride("node/2", hidden = true), db.poiOverrideDao().getById("node/2"))
    }
}
