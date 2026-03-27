package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class GPayNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val packageName = rawEvent.sourceAppPackage.orEmpty()
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()

        return packageName.contains("google.android.apps.nbu.paisa") ||
            title.contains("gpay") ||
            body.contains("upi") && body.contains("google pay")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val amountMinor = NotificationParsingUtils.extractAmountMinor(rawEvent.body)
        val merchant = NotificationParsingUtils.extractMerchant(rawEvent.body, merchantRegexes)

        return NotificationParseResult(
            parserKey = "notification_gpay",
            parserVersion = "v1",
            providerHint = "gpay",
            transactionKind = if (amountMinor != null) ParsedTransactionKind.SPEND else ParsedTransactionKind.UNKNOWN,
            candidateType = if (amountMinor != null) TransactionCandidateType.SPEND else TransactionCandidateType.UNKNOWN,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = Mode.UPI,
            parseConfidence = if (amountMinor != null && merchant != null) 0.8 else 0.6,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "gpay",
            toEntityName = merchant,
        )
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:to|paid to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
