package com.example.freizeit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row record of the last successful POI import.
 * Per-category counts are queried live from the poi table instead of stored.
 */
@Entity(tableName = "import_info")
data class ImportInfo(
    @PrimaryKey val id: Int = 1,
    val importedAt: Long,
    val fileGeneratedAt: String?
)
