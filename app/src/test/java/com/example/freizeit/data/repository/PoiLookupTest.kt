package com.example.freizeit.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.entity.CustomPoi
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

/**
 * Covers the merge helpers geofence registration/lookup (#48) relies on to give `custom_poi`
 * (#45) rows the same treatment as OSM `poi` rows.
 */
@RunWith(RobolectricTestRunner::class)
class PoiLookupTest {

    private lateinit var db: FreizeitDatabase

    private val osmPoi = Poi(id = "node/1", category = "cafe", lat = 50.9, lon = 6.9, name = "OSM Café")
    private val customPoi = CustomPoi(
        id = "custom/1",
        category = "cafe",
        lat = 50.91,
        lon = 6.91,
        name = "Our Café"
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

    private fun favorite(placeId: String) = Verdict(
        placeId = placeId,
        value = Verdict.VALUE_FAVORITE,
        verdictedAt = 0L,
        snapshotName = null,
        snapshotLat = 0.0,
        snapshotLon = 0.0,
        snapshotCategory = "cafe"
    )

    @Test
    fun `findPoiById resolves an OSM poi`() = runTest {
        db.poiDao().upsertAll(listOf(osmPoi))

        val found = findPoiById(db.poiDao(), db.customPoiDao(), "node/1")

        assertEquals("OSM Café", found?.name)
    }

    @Test
    fun `findPoiById resolves a custom poi`() = runTest {
        db.customPoiDao().upsert(customPoi)

        val found = findPoiById(db.poiDao(), db.customPoiDao(), "custom/1")

        assertEquals("Our Café", found?.name)
    }

    @Test
    fun `findPoiById returns null for an unknown id in either table`() = runTest {
        assertNull(findPoiById(db.poiDao(), db.customPoiDao(), "node/missing"))
        assertNull(findPoiById(db.poiDao(), db.customPoiDao(), "custom/missing"))
    }

    @Test
    fun `observeAllFavorites merges favorited OSM and custom pois`() = runTest {
        db.poiDao().upsertAll(listOf(osmPoi))
        db.customPoiDao().upsert(customPoi)
        db.verdictDao().upsert(favorite("node/1"))
        db.verdictDao().upsert(favorite("custom/1"))

        val favorites = observeAllFavorites(db.poiDao(), db.customPoiDao()).first()

        assertEquals(setOf("node/1", "custom/1"), favorites.map { it.id }.toSet())
    }

    @Test
    fun `observeAllFavorites excludes un-favorited custom pois`() = runTest {
        db.customPoiDao().upsert(customPoi)

        val favorites = observeAllFavorites(db.poiDao(), db.customPoiDao()).first()

        assertEquals(emptyList<Poi>(), favorites)
    }

    @Test
    fun `allFavoritesOnce returns the same merged result as a one-shot read`() = runTest {
        db.customPoiDao().upsert(customPoi)
        db.verdictDao().upsert(favorite("custom/1"))

        val favorites = allFavoritesOnce(db.poiDao(), db.customPoiDao())

        assertEquals(listOf("custom/1"), favorites.map { it.id })
    }
}
