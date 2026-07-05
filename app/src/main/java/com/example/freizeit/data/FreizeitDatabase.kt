package com.example.freizeit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.freizeit.data.dao.ImportInfoDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.VerdictDao
import com.example.freizeit.data.entity.ImportInfo
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict

@Database(
    entities = [Poi::class, Verdict::class, ImportInfo::class],
    version = 1,
    exportSchema = false
)
abstract class FreizeitDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao
    abstract fun verdictDao(): VerdictDao
    abstract fun importInfoDao(): ImportInfoDao

    companion object {
        fun build(context: Context): FreizeitDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FreizeitDatabase::class.java,
                "freizeit.db"
            ).build()
    }
}
