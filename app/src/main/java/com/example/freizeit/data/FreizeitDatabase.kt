package com.example.freizeit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.freizeit.data.entity.Poi

@Database(
    entities = [Poi::class],
    version = 1,
    exportSchema = false
)
abstract class FreizeitDatabase : RoomDatabase() {

    companion object {
        fun build(context: Context): FreizeitDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FreizeitDatabase::class.java,
                "freizeit.db"
            ).build()
    }
}
