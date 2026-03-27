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

@Entity(
    tableName = "transaction_candidates",
    indices = [
        Index("userId"),
        Index("parsedSignalId"),
        Index("candidateType"),
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
    val normalizationVersion: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

