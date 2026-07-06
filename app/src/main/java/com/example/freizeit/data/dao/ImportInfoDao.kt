package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freizeit.data.entity.ImportInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(info: ImportInfo)

    @Query("SELECT * FROM import_info WHERE id = 1")
    fun observe(): Flow<ImportInfo?>
}
