package com.example.freizeit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.CustomPoi
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
class CustomPoiDaoTest {

    private lateinit var db: FreizeitDatabase

    private fun customPoi(id: String, name: String = "Our Place", category: String = "cafe") = CustomPoi(
        id = id,
        category = category,
        lat = 50.9,
        lon = 6.9,
        name = name
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
    fun `upsert creates a new custom POI`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/1"))

        val all = db.customPoiDao().getAll()
        assertEquals(1, all.size)
        assertEquals("Our Place", all.single().name)
    }

    @Test
    fun `upsert with the same id replaces the row in place`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/1", name = "First Name"))
        db.customPoiDao().upsert(customPoi("custom/1", name = "Second Name"))

        val all = db.customPoiDao().getAll()
        assertEquals(1, all.size)
        assertEquals("Second Name", all.single().name)
    }

    @Test
    fun `getById returns null for an unknown id`() = runTest {
        assertNull(db.customPoiDao().getById("custom/unknown"))
    }

    @Test
    fun `getById finds a stored custom POI`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/1"))

        assertEquals("custom/1", db.customPoiDao().getById("custom/1")?.id)
    }

    @Test
    fun `delete removes a single custom POI`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/1"))
        db.customPoiDao().upsert(customPoi("custom/2"))

        db.customPoiDao().delete("custom/1")

        assertEquals(listOf("custom/2"), db.customPoiDao().getAll().map { it.id })
    }

    @Test
    fun `upsertAll replaces matching ids and adds new ones`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/1", name = "Stale"))

        db.customPoiDao().upsertAll(
            listOf(customPoi("custom/1", name = "Fresh"), customPoi("custom/2", name = "New"))
        )

        val byId = db.customPoiDao().getAll().associateBy { it.id }
        assertEquals("Fresh", byId["custom/1"]?.name)
        assertEquals("New", byId["custom/2"]?.name)
    }

    @Test
    fun `deleteAll clears every custom POI`() = runTest {
        db.customPoiDao().upsert(customPoi("custom/1"))
        db.customPoiDao().upsert(customPoi("custom/2"))

        db.customPoiDao().deleteAll()

        assertEquals(0, db.customPoiDao().observeAll().first().size)
    }

    @Test
    fun `observeAll reflects inserts`() = runTest {
        assertEquals(0, db.customPoiDao().observeAll().first().size)

        db.customPoiDao().upsert(customPoi("custom/1"))

        assertEquals(1, db.customPoiDao().observeAll().first().size)
    }
}
