package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
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
    DISMISSED,
}

@Entity(
    tableName = "inbox_items",
    indices = [
        Index("userId"),
        Index("decisionState"),
        Index("transactionCandidateId"),
        Index("createdAt"),
        // Required by Room for the SET_NULL foreign key below.
        Index("linkedCanonicalTransactionId"),
    ],
    // CASCADE on transactionCandidateId: an inbox item is a presentation of
    // a candidate; deleting the candidate (via the parsed_signal pruning
    // chain) must take its inbox row with it.
    //
    // SET NULL on linkedCanonicalTransactionId: a confirmed/merged inbox
    // row points at a canonical txn the user can later delete. The audit
    // trail (decisionState = CONFIRMED, resolvedAt, etc.) should outlive
    // the canonical row — just null the pointer.
    foreignKeys = [
        ForeignKey(
            entity = TransactionCandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionCandidateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CanonicalTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedCanonicalTransactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
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

