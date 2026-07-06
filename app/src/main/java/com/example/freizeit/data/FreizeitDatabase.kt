package com.example.freizeit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.freizeit.data.dao.ImportInfoDao
import com.example.freizeit.data.dao.PendingVisitDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.VerdictDao
import com.example.freizeit.data.entity.ImportInfo
import com.example.freizeit.data.entity.PendingVisit
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict

@Database(
    entities = [Poi::class, Verdict::class, ImportInfo::class, PendingVisit::class],
    version = 2,
    exportSchema = false
)
abstract class FreizeitDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao
    abstract fun verdictDao(): VerdictDao
    abstract fun importInfoDao(): ImportInfoDao
    abstract fun pendingVisitDao(): PendingVisitDao

    companion object {
        /** Adds pending_visit (issue #6); poi/verdict/import_info data on real devices is untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_visit` (
                        `id` INTEGER NOT NULL,
                        `placeId` TEXT NOT NULL,
                        `snapshotName` TEXT,
                        `snapshotCategory` TEXT NOT NULL,
                        `snapshotLat` REAL NOT NULL,
                        `snapshotLon` REAL NOT NULL,
                        `wentAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun build(context: Context): FreizeitDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FreizeitDatabase::class.java,
                "freizeit.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
