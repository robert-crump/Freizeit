package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freizeit.data.entity.Poi
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String, val count: Int)

@Dao
interface PoiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pois: List<Poi>)

    /** Import step 1: assume every known place vanished until the file says otherwise. */
    @Query("UPDATE poi SET missingFromOsm = 1")
    suspend fun markAllMissing()

    /** Import step 3: vanished places without a verdict are dropped; verdicted ones stay flagged. */
    @Query("DELETE FROM poi WHERE missingFromOsm = 1 AND id NOT IN (SELECT placeId FROM verdict)")
    suspend fun deleteUnverdictedMissing()

    @Query("SELECT category, COUNT(*) as count FROM poi GROUP BY category ORDER BY category")
    fun categoryCounts(): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM poi WHERE missingFromOsm = 1")
    fun missingCount(): Flow<Int>

    @Query("SELECT * FROM poi WHERE id = :id")
    suspend fun getById(id: String): Poi?

    @Query("SELECT COUNT(*) FROM poi")
    suspend fun count(): Int
}
