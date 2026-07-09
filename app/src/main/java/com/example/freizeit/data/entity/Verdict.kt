package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A family verdict on a place. Verdict UI and ranking effects land in #6;
 * the table exists now because the import's keep+flag rule depends on it.
 *
 * Deliberately NOT a foreign key to [Poi]: a verdict must outlive the poi row
 * it refers to (that is the whole point of the keep+flag rule), so it
 * snapshots name/coords/category at verdict time.
 */
@Entity(tableName = "verdict")
data class Verdict(
    @PrimaryKey val placeId: String,
    val value: String,
    val verdictedAt: Long,
    val snapshotName: String?,
    val snapshotLat: Double,
    val snapshotLon: Double,
    val snapshotCategory: String
) {
    companion object {
        const val VALUE_DOWN = "down"
        const val VALUE_FAVORITE = "favorite"
    }
}
