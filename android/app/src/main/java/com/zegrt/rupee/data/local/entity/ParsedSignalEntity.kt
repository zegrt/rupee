package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ParsedTransactionKind {
    SPEND,
    REFUND,
    BILL_DUE,
    STATEMENT,
    PAYMENT,
    EMI,
    RECURRING_CANDIDATE,
    UNKNOWN,
}

@Entity(
    tableName = "parsed_signals",
    indices = [
        Index("userId"),
        Index("rawCaptureEventId"),
        Index("providerHint"),
        Index("transactionKind"),
        Index("eventOccurredAt"),
    ],
)
data class ParsedSignalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val rawCaptureEventId: String,
    val parserKey: String,
    val parserVersion: String,
    val providerHint: String? = null,
    val transactionKind: ParsedTransactionKind,
    val amountMinor: Long? = null,
    val currencyCode: String? = null,
    val merchantRaw: String? = null,
    val sourceAccountHint: String? = null,
    val sourceCardHint: String? = null,
    val maskedDigits: String? = null,
    val mode: Mode? = null,
    val eventOccurredAt: String? = null,
    val parseConfidence: Double,
    val structuredJson: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

