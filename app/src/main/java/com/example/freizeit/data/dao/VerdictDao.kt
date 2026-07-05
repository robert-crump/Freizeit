package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freizeit.data.entity.Verdict

@Dao
interface VerdictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(verdict: Verdict)

    @Query("SELECT * FROM verdict WHERE placeId = :placeId")
    suspend fun getByPlaceId(placeId: String): Verdict?
}
