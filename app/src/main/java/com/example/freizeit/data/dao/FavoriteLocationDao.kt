package com.example.freizeit.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.freizeit.data.entity.FavoriteLocation

@Dao
interface FavoriteLocationDao {
    @Query("SELECT * FROM favorite_locations ORDER BY name ASC")
    fun getAllFavoriteLocations(): LiveData<List<FavoriteLocation>>
    
    @Query("SELECT * FROM favorite_locations WHERE id = :id")
    suspend fun getFavoriteLocationById(id: Long): FavoriteLocation?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: FavoriteLocation): Long
    
    @Update
    suspend fun update(location: FavoriteLocation)
    
    @Delete
    suspend fun delete(location: FavoriteLocation)
    
    @Query("DELETE FROM favorite_locations")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM favorite_locations")
    suspend fun getAllFavoriteLocationsList(): List<FavoriteLocation>
}
