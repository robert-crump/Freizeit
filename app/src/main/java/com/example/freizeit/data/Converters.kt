package com.example.freizeit.data

import androidx.room.TypeConverter
import com.example.freizeit.data.entity.ActivityCategory

class Converters {
    @TypeConverter
    fun fromActivityCategory(category: ActivityCategory): String {
        return category.name
    }
    
    @TypeConverter
    fun toActivityCategory(value: String): ActivityCategory {
        return ActivityCategory.valueOf(value)
    }
}
