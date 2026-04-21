package com.example.freizeit.data.repository

import androidx.lifecycle.LiveData
import com.example.freizeit.data.dao.FavoriteLocationDao
import com.example.freizeit.data.entity.FavoriteLocation

class FavoriteLocationRepository(private val favoriteLocationDao: FavoriteLocationDao) {
    
    val allFavoriteLocations: LiveData<List<FavoriteLocation>> = 
        favoriteLocationDao.getAllFavoriteLocations()
    
    suspend fun getFavoriteLocationById(id: Long): FavoriteLocation? {
        return favoriteLocationDao.getFavoriteLocationById(id)
    }
    
    suspend fun insert(location: FavoriteLocation): Long {
        return favoriteLocationDao.insert(location)
    }
    
    suspend fun update(location: FavoriteLocation) {
        favoriteLocationDao.update(location)
    }
    
    suspend fun delete(location: FavoriteLocation) {
        favoriteLocationDao.delete(location)
    }
    
    suspend fun deleteAll() {
        favoriteLocationDao.deleteAll()
    }
    
    suspend fun getAllFavoriteLocationsList(): List<FavoriteLocation> {
        return favoriteLocationDao.getAllFavoriteLocationsList()
    }
}
