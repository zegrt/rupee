package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Auto-confirmation rule for a recurring merchant. When a parsed signal's
 * merchant name matches `merchantPattern` (case-insensitive equality on the
 * cleaned merchant string), the ingestion pipeline elevates the candidate
 * straight to a CONFIRMED canonical transaction — bypassing the Inbox and
 * the SUGGESTED review state. Optional `autoCategoryId` is also applied.
 */
@Entity(
    tableName = "merchant_trust_rules",
    indices = [
        Index("userId"),
        Index(value = ["userId", "merchantPattern"], unique = true),
    ],
)
data class MerchantTrustRuleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val merchantPattern: String,
    val autoCategoryId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)
