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
    // UPI/IMPS/NEFT/RTGS reference. Captured by parsers so two signals from the
    // same charge across different streams (HDFC SMS + CRED notification) can
    // be correlated later via the chaining engine. Null when the body had no
    // recognisable reference token. (Axio §3.4.)
    val networkReferenceId: String? = null,
    val networkReferenceType: String? = null,
    // Stable identifier for the matched rule, reserved for the upcoming
    // JSON rule engine. Null until rules ship.
    val patternUid: Long? = null,
    val parseConfidence: Double,
    val structuredJson: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

