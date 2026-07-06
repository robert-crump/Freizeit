package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row record of an unresolved "Go" tap from a Home card. Snapshots
 * name/coords/category like [Verdict] does, so the next-open banner and the
 * verdict it may produce don't depend on the poi row still existing.
 */
@Entity(tableName = "pending_visit")
data class PendingVisit(
    @PrimaryKey val id: Int = 1,
    val placeId: String,
    val snapshotName: String?,
    val snapshotCategory: String,
    val snapshotLat: Double,
    val snapshotLon: Double,
    val wentAt: Long
)
