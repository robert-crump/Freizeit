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

    /** Used by #49's reimport-time merge: re-keys a custom POI's verdict onto the OSM `poi.id`
     *  it was just matched to, instead of deleting it outright the way [delete] does for #47's
     *  unconditional delete. `OR REPLACE` matters only in the rare case [newId] already carries
     *  its own verdict (an OSM place independently favorited before the merge) — SQLite drops
     *  that pre-existing row rather than failing on the now-duplicate placeId primary key, and
     *  the custom POI's verdict wins. A no-op (0 rows) if [oldId] has no verdict at all. */
    @Query("UPDATE OR REPLACE verdict SET placeId = :newId WHERE placeId = :oldId")
    suspend fun rekey(oldId: String, newId: String)
}

/** One tap sets/changes a verdict; passing null clears it. Shared by Home and Map. */
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
