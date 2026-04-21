package com.example.freizeit.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY name ASC")
    fun getAllActivities(): LiveData<List<Activity>>
    
    @Query("SELECT * FROM activities WHERE category = :category ORDER BY name ASC")
    fun getActivitiesByCategory(category: ActivityCategory): LiveData<List<Activity>>
    
    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getActivityById(id: Long): Activity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: Activity): Long
    
    @Update
    suspend fun update(activity: Activity)
    
    @Delete
    suspend fun delete(activity: Activity)
    
    @Query("DELETE FROM activities")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM activities")
    suspend fun getAllActivitiesList(): List<Activity>
}
