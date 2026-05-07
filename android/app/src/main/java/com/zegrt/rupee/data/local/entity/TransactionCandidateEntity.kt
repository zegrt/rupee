package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionCandidateType {
    SPEND,
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
