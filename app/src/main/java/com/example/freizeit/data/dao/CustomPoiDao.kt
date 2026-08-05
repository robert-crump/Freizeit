package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freizeit.data.entity.CustomPoi
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPoiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(poi: CustomPoi)

    /** Used by backup restore, which replaces the whole table wholesale. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pois: List<CustomPoi>)

    @Query("DELETE FROM custom_poi WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM custom_poi")
    fun observeAll(): Flow<List<CustomPoi>>

    @Query("SELECT * FROM custom_poi")
    suspend fun getAll(): List<CustomPoi>

    @Query("SELECT * FROM custom_poi WHERE id = :id")
    suspend fun getById(id: String): CustomPoi?

    @Query("DELETE FROM custom_poi")
    suspend fun deleteAll()
}
