package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freizeit.data.entity.PoiOverride
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PoiOverride)

    @Query("DELETE FROM poi_override WHERE placeId = :placeId")
    suspend fun delete(placeId: String)

    @Query("SELECT * FROM poi_override WHERE placeId = :placeId")
    suspend fun getById(placeId: String): PoiOverride?

    @Query("SELECT * FROM poi_override")
    fun observeAll(): Flow<List<PoiOverride>>

    @Query("SELECT * FROM poi_override")
    suspend fun getAll(): List<PoiOverride>

    /** Used by backup restore, which replaces the whole table wholesale. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<PoiOverride>)

    @Query("DELETE FROM poi_override")
    suspend fun deleteAll()
}

/** Stores [override] for [placeId], or deletes the row when it's null/changes nothing. */
suspend fun PoiOverrideDao.setOverride(placeId: String, override: PoiOverride?) {
    if (override == null || override.isEmpty) delete(placeId) else upsert(override.copy(placeId = placeId))
}

/** Hides [placeId] (#73), keeping any edits it already had. */
suspend fun PoiOverrideDao.hide(placeId: String) {
    upsert((getById(placeId) ?: PoiOverride(placeId)).copy(hidden = true))
}
