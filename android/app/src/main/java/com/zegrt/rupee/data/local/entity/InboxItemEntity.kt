package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class InboxReasonCode {
    MEDIUM_CONFIDENCE,
    POSSIBLE_DUPLICATE_CONFLICT,
    MISSING_ACCOUNT_MAPPING,
    MISSING_CATEGORY,
    AMBIGUOUS_MERCHANT,
    AMBIGUOUS_KIND,
}

enum class InboxDecisionState {
    PENDING,
    CONFIRMED,
    EDITED,
    DISMISSED,
    MERGED,
}

@Entity(
    tableName = "inbox_items",
    indices = [
        Index("userId"),
        Index("decisionState"),
        Index("transactionCandidateId"),
        Index("createdAt"),
    ],
)
data class InboxItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val transactionCandidateId: String,
    val reasonCode: InboxReasonCode,
    val decisionState: InboxDecisionState,
    val linkedCanonicalTransactionId: String? = null,
    val createdAt: String,
    val resolvedAt: String? = null,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

