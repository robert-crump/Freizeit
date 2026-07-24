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

    @Query("SELECT * FROM visit ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<Visit>>

    @Query("SELECT * FROM visit ORDER BY visitedAt DESC")
    suspend fun getAll(): List<Visit>
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
