package com.example.freizeit.util

import android.util.Log
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.FavoriteLocation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class DatabaseExport(
    val activities: List<Activity>,
    val favoriteLocations: List<FavoriteLocation>
)

object DatabaseExportImport {
    private const val TAG = "DatabaseExportImport"
    private val gson = Gson()

    fun exportToJson(
        activities: List<Activity>,
        favoriteLocations: List<FavoriteLocation>
    ): String {
        val export = DatabaseExport(activities, favoriteLocations)
        return gson.toJson(export)
    }

    fun importFromJson(json: String): DatabaseExport? {
        return try {
            val type = object : TypeToken<DatabaseExport>() {}.type
            gson.fromJson<DatabaseExport>(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            null
        }
    }
}
