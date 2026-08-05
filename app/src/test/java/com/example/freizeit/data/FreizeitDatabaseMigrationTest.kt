package com.example.freizeit.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises [FreizeitDatabase.MIGRATION_6_7]'s raw SQL directly against a v6-shaped database —
 * this project doesn't export Room schemas ([FreizeitDatabase]'s `exportSchema = false`), so
 * there's no schema JSON for `androidx.room.testing.MigrationTestHelper` to migrate between;
 * a hand-built [SupportSQLiteDatabase] plus the `Migration` object itself covers the same ground.
 */
@RunWith(RobolectricTestRunner::class)
class FreizeitDatabaseMigrationTest {

    @Test
    fun `MIGRATION_6_7 creates custom_poi without touching poi`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val dbName = "migration-6-7-test.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE poi (id TEXT PRIMARY KEY NOT NULL, category TEXT NOT NULL)")
                        db.execSQL("INSERT INTO poi (id, category) VALUES ('node/1', 'cafe')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // Not exercised here — the version-6 schema above is created fresh each run.
                    }
                })
                .build()
        )

        try {
            val db = helper.writableDatabase // runs onCreate, landing at version 6

            FreizeitDatabase.MIGRATION_6_7.migrate(db)

            db.query("SELECT * FROM custom_poi").use { cursor ->
                assertEquals(0, cursor.count)
                assertTrue(
                    listOf("id", "category", "lat", "lon", "name", "openingHours", "street", "housenumber", "postcode", "city")
                        .all { it in cursor.columnNames }
                )
            }
            db.query("SELECT * FROM poi").use { cursor ->
                assertEquals(1, cursor.count)
            }

            // Insertable with just the required columns — name/category/lat/lon are NOT NULL,
            // the rest are nullable, matching CustomPoi's own required-vs-optional fields.
            db.execSQL(
                "INSERT INTO custom_poi (id, category, lat, lon, name) VALUES ('custom/1', 'cafe', 50.9, 6.9, 'Our Café')"
            )
            db.query("SELECT * FROM custom_poi").use { cursor ->
                assertEquals(1, cursor.count)
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }
}
