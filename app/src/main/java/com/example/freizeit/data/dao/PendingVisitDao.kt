package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freizeit.data.entity.PendingVisit
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingVisitDao {

    /** Overwrites any previous unresolved visit — only the latest "Go" matters. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(visit: PendingVisit)

    @Query("SELECT * FROM pending_visit WHERE id = 1")
    fun observe(): Flow<PendingVisit?>

    @Query("DELETE FROM pending_visit WHERE id = 1")
    suspend fun clear()
}
