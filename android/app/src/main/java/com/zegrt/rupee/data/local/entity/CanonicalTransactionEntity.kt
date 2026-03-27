package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CanonicalTransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    CASH_ADJUSTMENT,
}

enum class CanonicalTransactionStatus {
    CONFIRMED,
    SUGGESTED,
    IGNORED,
}

enum class ConfidenceTier {
    HIGH,
    MEDIUM,
    LOW,
}

enum class Mode {
    UPI,
    CREDIT_CARD,
    DEBIT_CARD,
    BANK_TRANSFER,
    CASH,
    ATM,
    OTHER,
}

@Entity(
    tableName = "canonical_transactions",
    indices = [
        Index("userId"),
        Index("occurredAt"),
        Index("categoryId"),
        Index("accountId"),
        Index("creditCardId"),
        Index("type"),
        Index("status"),
        Index("similarHistoryKey"),
    ],
)
data class CanonicalTransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: CanonicalTransactionType,
    val status: CanonicalTransactionStatus,
    val amountMinor: Long,
    val currencyCode: String,
    val accountId: String? = null,
    val creditCardId: String? = null,
    val cashAccountId: String? = null,
    val merchantName: String? = null,
    val categoryId: String? = null,
    val mode: Mode? = null,
    val notes: String? = null,
    val occurredAt: String,
    val sourceSummary: String? = null,
    val createdBy: String,
    val confidenceTier: ConfidenceTier? = null,
    val similarHistoryKey: String? = null,
    val isHiddenFromBudget: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

