package com.example.freizeit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.freizeit.data.dao.FavoriteDao
import com.example.freizeit.data.dao.ImportInfoDao
import com.example.freizeit.data.dao.PendingVisitDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.VerdictDao
import com.example.freizeit.data.entity.Favorite
import com.example.freizeit.data.entity.ImportInfo
import com.example.freizeit.data.entity.PendingVisit
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict

@Database(
    entities = [Poi::class, Verdict::class, ImportInfo::class, PendingVisit::class, Favorite::class],
    version = 3,
    exportSchema = false
)
abstract class FreizeitDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao
    abstract fun verdictDao(): VerdictDao
    abstract fun importInfoDao(): ImportInfoDao
    abstract fun pendingVisitDao(): PendingVisitDao
    abstract fun favoriteDao(): FavoriteDao

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

        /** Adds favorite (issue #8); existing tables on real devices are untouched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `address` TEXT,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
