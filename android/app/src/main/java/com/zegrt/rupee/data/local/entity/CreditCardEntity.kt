package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credit_cards",
    indices = [
        Index("userId"),
        Index("providerName"),
        Index("statementDueDate"),
        Index("isActive"),
    ],
)
data class CreditCardEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val displayName: String,
    val providerName: String? = null,
    val maskedIdentifier: String? = null,
    val network: String? = null,
    val creditLimitMinor: Long? = null,
    val statementDueAmountMinor: Long? = null,
    val statementDueDate: String? = null,
    val currentOutstandingMinor: Long? = null,
    val availableLimitMinor: Long? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

