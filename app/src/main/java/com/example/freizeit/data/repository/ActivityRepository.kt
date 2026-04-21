package com.example.freizeit.data.repository

import androidx.lifecycle.LiveData
import com.example.freizeit.data.dao.ActivityDao
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory

class ActivityRepository(private val activityDao: ActivityDao) {
    
    val allActivities: LiveData<List<Activity>> = activityDao.getAllActivities()
    
    fun getActivitiesByCategory(category: ActivityCategory): LiveData<List<Activity>> {
        return activityDao.getActivitiesByCategory(category)
    }
    
    suspend fun getActivityById(id: Long): Activity? {
        return activityDao.getActivityById(id)
    }
    
    suspend fun insert(activity: Activity): Long {
        return activityDao.insert(activity)
    }
    
    suspend fun update(activity: Activity) {
        activityDao.update(activity)
    }
    
    suspend fun delete(activity: Activity) {
        activityDao.delete(activity)
    }
    
    suspend fun deleteAll() {
        activityDao.deleteAll()
    }
    
    suspend fun getAllActivitiesList(): List<Activity> {
        return activityDao.getAllActivitiesList()
    }
}
