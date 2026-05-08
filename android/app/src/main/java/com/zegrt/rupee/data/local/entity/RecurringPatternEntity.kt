package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_patterns",
    indices = [
        Index("userId"),
        Index("merchantPattern"),
        Index("nextExpectedAt"),
        Index("isConfirmed"),
        Index("isDismissed"),
    ],
)
data class RecurringPatternEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val merchantPattern: String,
    val expectedAmountMinor: Long,
    val intervalDays: Int,
    val occurrenceCount: Int,
    val lastSeenAt: String,
    val nextExpectedAt: String,
    val isConfirmed: Boolean = false,
    val isDismissed: Boolean = false,
    val sourceType: String,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)
