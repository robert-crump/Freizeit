package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Visit
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {

    @Insert
    suspend fun insert(visit: Visit)

    @Insert
    suspend fun insertAll(visits: List<Visit>)

    @Query("SELECT * FROM visit ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<Visit>>

    @Query("SELECT * FROM visit ORDER BY visitedAt DESC")
    suspend fun getAll(): List<Visit>

    @Query("DELETE FROM visit WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Shared across [Visit.SOURCE_MANUAL]/[Visit.SOURCE_NOTIFICATION] — one cooldown clock per place. */
    @Query("SELECT MAX(visitedAt) FROM visit WHERE placeId = :placeId")
    suspend fun lastVisitedAt(placeId: String): Long?
}

/** Logs a check-in, snapshotting the poi as it is right now. */
suspend fun VisitDao.checkIn(poi: Poi, source: String = Visit.SOURCE_MANUAL) {
    insert(
        Visit(
            placeId = poi.id,
            visitedAt = System.currentTimeMillis(),
            source = source,
            snapshotName = poi.name,
            snapshotLat = poi.lat,
            snapshotLon = poi.lon,
            snapshotCategory = poi.category
        )
    )
}
