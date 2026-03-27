package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index("userId"),
        Index("isArchived"),
        Index(value = ["userId", "name"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val iconKey: String? = null,
    val colorKey: String? = null,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

