package com.example.freizeit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.freizeit.data.dao.ActivityDao
import com.example.freizeit.data.dao.FavoriteLocationDao
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.FavoriteLocation
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Activity::class, FavoriteLocation::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun favoriteLocationDao(): FavoriteLocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "freizeit_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Migration von Version 1 zu 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Füge die neuen Transport Felder hinzu (Default: alle aktiviert)
                database.execSQL(
                    "ALTER TABLE activities ADD COLUMN enableWalk INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "ALTER TABLE activities ADD COLUMN enableBike INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "ALTER TABLE activities ADD COLUMN enableTransit INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "ALTER TABLE activities ADD COLUMN enableCar INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        // Migration von Version 2 zu 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Füge das address Feld hinzu (nullable)
                database.execSQL(
                    "ALTER TABLE activities ADD COLUMN address TEXT"
                )
            }
        }
    }
}