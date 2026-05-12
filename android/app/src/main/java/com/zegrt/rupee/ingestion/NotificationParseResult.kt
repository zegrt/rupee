package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

data class NotificationParseResult(
    val parserKey: String,
    val parserVersion: String,
    val providerHint: String? = null,
    val transactionKind: ParsedTransactionKind,
    val candidateType: TransactionCandidateType,
    val amountMinor: Long? = null,
    val currencyCode: String? = null,
    val merchantRaw: String? = null,
    val sourceAccountHint: String? = null,
    val sourceCardHint: String? = null,
    val maskedDigits: String? = null,
    val mode: Mode? = null,
    val parseConfidence: Double,
    val fromEntityType: AccountType? = null,
    val fromEntityHint: String? = null,
    val toEntityName: String? = null,
    // ISO-8601 date (yyyy-MM-dd) extracted from the body for BILL_DUE / EMI_DUE candidates.
    // Null if no due date phrase was matched.
    val dueDateIso: String? = null,
    // UPI/IMPS/NEFT/RTGS/RRN reference extracted from the body. Captured here
    // for cross-stream dedupe later; flows into ParsedSignalEntity and the
    // canonical txn. Null when no recognisable token was found.
    val networkReferenceId: String? = null,
    val networkReferenceType: String? = null,
)

