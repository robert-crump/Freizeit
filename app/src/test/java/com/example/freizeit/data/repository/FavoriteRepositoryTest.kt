package com.example.freizeit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.util.LatLon
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteRepositoryTest {

    private lateinit var db: FreizeitDatabase
    private var resolved: LatLon? = LatLon(50.9, 6.9)

    private val repository by lazy {
        FavoriteRepository(db.favoriteDao()) { resolved }
    }

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
    fun `add resolves the address and stores coordinates`() = runTest {
        val favorite = repository.add("Home", "Bahnhofstraße 1, Aachen")

        assertEquals(50.9, favorite.lat, 0.0)
        assertEquals(6.9, favorite.lon, 0.0)
        assertEquals(1, db.favoriteDao().getAll().size)
    }

    @Test
    fun `add throws and stores nothing when the address does not resolve`() = runTest {
        resolved = null

        assertThrows(FavoriteResolveException::class.java) {
            kotlinx.coroutines.runBlocking { repository.add("Nowhere", "not a real address") }
        }
        assertEquals(0, db.favoriteDao().getAll().size)
    }

    @Test
    fun `update re-resolves the address and overwrites name and coordinates`() = runTest {
        val favorite = repository.add("Home", "Bahnhofstraße 1, Aachen")
        resolved = LatLon(51.0, 7.0)

        val updated = repository.update(favorite, "Oma's", "new address")

        assertEquals("Oma's", updated.name)
        assertEquals(51.0, updated.lat, 0.0)
        assertEquals(favorite.id, updated.id)
    }
}
