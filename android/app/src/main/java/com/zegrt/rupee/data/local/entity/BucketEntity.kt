package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "buckets",
    indices = [
        Index("userId"),
        Index(value = ["userId", "name"], unique = true),
    ],
)
data class BucketEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val description: String? = null,
    val colorKey: String? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

