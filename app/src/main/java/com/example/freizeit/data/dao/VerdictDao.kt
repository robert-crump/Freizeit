package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import kotlinx.coroutines.flow.Flow

@Dao
interface VerdictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(verdict: Verdict)

    @Query("DELETE FROM verdict WHERE placeId = :placeId")
    suspend fun delete(placeId: String)

    @Query("SELECT * FROM verdict WHERE placeId = :placeId")
    suspend fun getByPlaceId(placeId: String): Verdict?

    @Query("SELECT * FROM verdict")
    fun observeAll(): Flow<List<Verdict>>

    @Query("SELECT * FROM verdict")
    suspend fun getAll(): List<Verdict>

    /** Used by backup restore, which replaces the whole table wholesale. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(verdicts: List<Verdict>)

    @Query("DELETE FROM verdict")
    suspend fun deleteAll()
}

/** One tap sets/changes a verdict; passing null clears it. Shared by Home and Explore. */
suspend fun VerdictDao.setVerdict(poi: Poi, value: String?) {
    if (value == null) {
        delete(poi.id)
    } else {
        upsert(
            Verdict(
                placeId = poi.id,
                value = value,
                verdictedAt = System.currentTimeMillis(),
                snapshotName = poi.name,
                snapshotLat = poi.lat,
                snapshotLon = poi.lon,
                snapshotCategory = poi.category
            )
        )
    }
}
