package com.example.freizeit.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.freizeit.data.AppDatabase
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory
import com.example.freizeit.data.entity.FavoriteLocation
import com.example.freizeit.data.repository.ActivityRepository
import com.example.freizeit.data.repository.FavoriteLocationRepository
import com.example.freizeit.util.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val activityRepository: ActivityRepository
    private val favoriteLocationRepository: FavoriteLocationRepository
    private val locationManager: LocationManager
    private val geocodingService: GeocodingService
    
    val allActivities: LiveData<List<Activity>>
    val allFavoriteLocations: LiveData<List<FavoriteLocation>>
    
    private val _currentLocation = MutableLiveData<Pair<Double, Double>>()
    val currentLocation: LiveData<Pair<Double, Double>> = _currentLocation

    private val _currentLocationName = MutableLiveData<String>("Determining location\u2026")
    val currentLocationName: LiveData<String> = _currentLocationName

    
    private val _filterOptions = MutableLiveData<FilterOptions>(FilterOptions())
    val filterOptions: LiveData<FilterOptions> = _filterOptions
    
    private val _selectedFavoriteLocation = MutableLiveData<FavoriteLocation?>()
    val selectedFavoriteLocation: LiveData<FavoriteLocation?> = _selectedFavoriteLocation
    
    init {
        val database = AppDatabase.getDatabase(application)
        activityRepository = ActivityRepository(database.activityDao())
        favoriteLocationRepository = FavoriteLocationRepository(database.favoriteLocationDao())
        locationManager = LocationManager(application)
        geocodingService = GeocodingService()
        
        allActivities = activityRepository.allActivities
        allFavoriteLocations = favoriteLocationRepository.allFavoriteLocations
    }
    
    fun updateCurrentLocation() {
        viewModelScope.launch {
            val location = locationManager.getCurrentLocation()
            if (location != null) {
                _currentLocation.value = Pair(location.latitude, location.longitude)
                
                // Check if a favorite location is nearby (within 300m)
                val favoriteLocations = favoriteLocationRepository.getAllFavoriteLocationsList()
                val nearbyFavorite = favoriteLocations.firstOrNull { favorite ->
                    favorite.getDistanceTo(location.latitude, location.longitude) < 0.3
                }

                if (nearbyFavorite != null) {
                    _currentLocationName.value = nearbyFavorite.name
                    _selectedFavoriteLocation.value = nearbyFavorite
                } else {
                    // Reverse geocode to get city name
                    val city = geocodingService.reverseGeocode(location.latitude, location.longitude)
                    _currentLocationName.value = city ?: "Current location"
                    _selectedFavoriteLocation.value = null
                }
            }
        }
    }
    
    fun selectFavoriteLocation(favoriteLocation: FavoriteLocation?) {
        _selectedFavoriteLocation.value = favoriteLocation
        if (favoriteLocation != null) {
            _currentLocation.value = Pair(favoriteLocation.latitude, favoriteLocation.longitude)
            _currentLocationName.value = favoriteLocation.name
        } else {
            updateCurrentLocation()
        }
    }
    
    fun updateFilterOptions(newOptions: FilterOptions) {
        _filterOptions.value = newOptions
    }
    
    fun getActivitiesByCategory(category: ActivityCategory): LiveData<List<Activity>> {
        return activityRepository.getActivitiesByCategory(category)
    }
    
    fun insertActivity(activity: Activity) {
        viewModelScope.launch {
            activityRepository.insert(activity)
        }
    }
    
    fun updateActivity(activity: Activity) {
        viewModelScope.launch {
            activityRepository.update(activity)
        }
    }
    
    fun deleteActivity(activity: Activity) {
        viewModelScope.launch {
            activityRepository.delete(activity)
        }
    }
    
    suspend fun getActivityById(id: Long): Activity? {
        return activityRepository.getActivityById(id)
    }
    
    fun hasLocationPermission(): Boolean {
        return locationManager.hasLocationPermission()
    }
}
