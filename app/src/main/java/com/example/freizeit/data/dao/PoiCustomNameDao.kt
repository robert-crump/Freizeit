package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freizeit.data.entity.PoiCustomName
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiCustomNameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PoiCustomName)

    @Query("DELETE FROM poi_custom_name WHERE placeId = :placeId")
    suspend fun delete(placeId: String)

    @Query("SELECT * FROM poi_custom_name")
    fun observeAll(): Flow<List<PoiCustomName>>

    @Query("SELECT * FROM poi_custom_name")
    suspend fun getAll(): List<PoiCustomName>

    /** Used by backup restore, which replaces the whole table wholesale. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<PoiCustomName>)

    @Query("DELETE FROM poi_custom_name")
    suspend fun deleteAll()
}

/** Blank clears the custom name (deletes the row); non-blank upserts it, trimmed. */
suspend fun PoiCustomNameDao.setCustomName(placeId: String, customName: String?) {
    val trimmed = customName?.trim()
    if (trimmed.isNullOrEmpty()) {
        delete(placeId)
    } else {
        upsert(PoiCustomName(placeId = placeId, customName = trimmed))
    }
}
