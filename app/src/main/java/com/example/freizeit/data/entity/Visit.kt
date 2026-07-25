package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A logged check-in at a place, manual or (future) auto-detected. Same
 * denormalized-snapshot rationale as [Verdict]/[PendingVisit]: a visit must
 * outlive the poi row it refers to, since POIs can vanish on re-import.
 */
@Entity(tableName = "visit")
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: String,
    val visitedAt: Long,
    val source: String,
    val snapshotName: String?,
    val snapshotLat: Double,
    val snapshotLon: Double,
    val snapshotCategory: String
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_NOTIFICATION = "notification"
    }
}
