package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionCandidateType {
    SPEND,
    INCOME,
    CASH_WITHDRAWAL,
    CARD_DUE,
    EMI_DUE,
    TRANSFER,
    UNKNOWN,
}

enum class CandidateDecisionState {
    AUTO_CREATED,
    INBOX_PENDING,
    USER_CONFIRMED,
    IGNORED,
}

enum class CandidateDecisionReason {
    HIGH_CONFIDENCE_SPEND,
    MEDIUM_CONFIDENCE_REVIEW,
    LOW_CONFIDENCE_IGNORE,
    NON_SPEND_REVIEW,
    MISSING_AMOUNT,
    DUPLICATE_IGNORED,
    MERCHANT_TRUSTED,
    NOT_TRANSACTIONAL,
}

@Entity(
    tableName = "transaction_candidates",
    indices = [
        Index("userId"),
        Index("parsedSignalId"),
        Index("candidateType"),
        Index("decisionState"),
        Index("occurredAt"),
        Index("candidateFingerprint"),
        // Required by Room when a foreign-key column isn't already indexed.
        // linkedCanonicalTransactionId / duplicateOfCandidateId fall into
        // this bucket — neither is queried directly today but Room enforces
        // the index so future joins are cheap and to keep cascades fast.
        Index("linkedCanonicalTransactionId"),
        Index("duplicateOfCandidateId"),
    ],
    // CASCADE on parsedSignalId: a candidate is a 1:1 derivative of its
    // parsed signal — when the signal is pruned the candidate must go too.
    //
    // SET NULL on linkedCanonicalTransactionId: keep the candidate row
    // (audit trail of "we saw this notification") even if the user later
    // deletes the canonical transaction. Just clear the dangling pointer.
    //
    // SET NULL on duplicateOfCandidateId: self-reference for dedupe. If
    // the original candidate is deleted, downstream "I was a dupe of X"
    // pointers should null out rather than cascade-delete (that'd remove
    // the trail of how the dedupe engine decided things).
    foreignKeys = [
        ForeignKey(
            entity = ParsedSignalEntity::class,
            parentColumns = ["id"],
            childColumns = ["parsedSignalId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CanonicalTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedCanonicalTransactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TransactionCandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["duplicateOfCandidateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TransactionCandidateEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val parsedSignalId: String,
    val candidateType: TransactionCandidateType,
    val amountMinor: Long? = null,
    val currencyCode: String? = null,
    val fromEntityType: AccountType? = null,
    val fromEntityHint: String? = null,
    val toEntityName: String? = null,
    val mode: Mode? = null,
    val occurredAt: String? = null,
    val candidateFingerprint: String? = null,
    val confidenceTier: ConfidenceTier? = null,
    val decisionState: CandidateDecisionState,
    val decisionReason: CandidateDecisionReason,
    val duplicateOfCandidateId: String? = null,
    val linkedInboxItemId: String? = null,
    val linkedCanonicalTransactionId: String? = null,
    val normalizationVersion: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)
