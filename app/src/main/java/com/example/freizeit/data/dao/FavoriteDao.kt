package com.example.freizeit.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.freizeit.data.entity.Favorite
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert
    suspend fun insert(favorite: Favorite): Long

    @Update
    suspend fun update(favorite: Favorite)

    @Delete
    suspend fun delete(favorite: Favorite)

    @Query("SELECT * FROM favorite ORDER BY name")
    fun observeAll(): Flow<List<Favorite>>

    @Query("SELECT * FROM favorite")
    suspend fun getAll(): List<Favorite>

    /** Used by backup restore, which replaces the whole table wholesale. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<Favorite>)

    @Query("DELETE FROM favorite")
    suspend fun deleteAll()
}
