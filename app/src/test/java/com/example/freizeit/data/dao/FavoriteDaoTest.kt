package com.example.freizeit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.Favorite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest {

    private lateinit var db: FreizeitDatabase

    private fun favorite(name: String, lat: Double = 50.9, lon: Double = 6.9) =
        Favorite(name = name, address = "$name street", lat = lat, lon = lon)

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
    fun `insert then observeAll returns it with an assigned id`() = runTest {
        db.favoriteDao().insert(favorite("Home"))

        val all = db.favoriteDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Home", all[0].name)
        assertTrue(all[0].id != 0L)
    }

    @Test
    fun `update changes name, address and coordinates`() = runTest {
        val id = db.favoriteDao().insert(favorite("Home"))
        val stored = db.favoriteDao().getAll().first { it.id == id }

        db.favoriteDao().update(stored.copy(name = "Oma's", lat = 51.0, lon = 7.0))

        val updated = db.favoriteDao().getAll().first { it.id == id }
        assertEquals("Oma's", updated.name)
        assertEquals(51.0, updated.lat, 0.0)
    }

    @Test
    fun `delete removes the favorite`() = runTest {
        val id = db.favoriteDao().insert(favorite("Home"))
        val stored = db.favoriteDao().getAll().first { it.id == id }

        db.favoriteDao().delete(stored)

        assertTrue(db.favoriteDao().getAll().isEmpty())
    }

    @Test
    fun `insertAll and deleteAll support wholesale replace for backup restore`() = runTest {
        db.favoriteDao().insert(favorite("Stale"))

        db.favoriteDao().deleteAll()
        db.favoriteDao().insertAll(listOf(favorite("Home"), favorite("Oma's")))

        val all = db.favoriteDao().getAll()
        assertEquals(setOf("Home", "Oma's"), all.map { it.name }.toSet())
    }
}
