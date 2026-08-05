package com.example.freizeit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.freizeit.data.dao.CustomPoiDao
import com.example.freizeit.data.dao.ImportInfoDao
import com.example.freizeit.data.dao.PoiCustomNameDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.VerdictDao
import com.example.freizeit.data.dao.VisitDao
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.ImportInfo
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.PoiCustomName
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.entity.Visit

@Database(
    entities = [
        Poi::class, Verdict::class, ImportInfo::class, PoiCustomName::class,
        Visit::class, CustomPoi::class
    ],
    version = 7,
    exportSchema = false
)
abstract class FreizeitDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao
    abstract fun verdictDao(): VerdictDao
    abstract fun importInfoDao(): ImportInfoDao
    abstract fun poiCustomNameDao(): PoiCustomNameDao
    abstract fun visitDao(): VisitDao
    abstract fun customPoiDao(): CustomPoiDao

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

        /**
         * Retires favoriting-as-a-separate-place: verdicts alone drive it now
         * (up and love both collapse into "favorite"), so `favorite` drops
         * and its data isn't migrated — see the family-favorites rewrite.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `poi_custom_name` (
                        `placeId` TEXT NOT NULL,
                        `customName` TEXT NOT NULL,
                        PRIMARY KEY(`placeId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("UPDATE verdict SET value = 'favorite' WHERE value IN ('up', 'love')")
                db.execSQL("DROP TABLE IF EXISTS `favorite`")
            }
        }

        /** Adds visit (issue #23), the manual check-in log; existing tables are untouched. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `visit` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `placeId` TEXT NOT NULL,
                        `visitedAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `snapshotName` TEXT,
                        `snapshotLat` REAL NOT NULL,
                        `snapshotLon` REAL NOT NULL,
                        `snapshotCategory` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Drops pending_visit — the "Go" feature (and its 2h-later visit-confirmation banner)
         *  is retired; visits are recorded only via Check-in now. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `pending_visit`")
            }
        }

        /** Adds custom_poi (issue #45), user-added places from the Map screen's pin-drop flow;
         *  existing tables — including poi, which a `.pbf` reimport still owns exclusively — are
         *  untouched. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_poi` (
                        `id` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL,
                        `name` TEXT NOT NULL,
                        `openingHours` TEXT,
                        `street` TEXT,
                        `housenumber` TEXT,
                        `postcode` TEXT,
                        `city` TEXT,
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
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7
                )
                .build()
    }
}
