package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class PhonePeNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val pkg = rawEvent.sourceAppPackage.orEmpty().lowercase()
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()
        return pkg.contains("phonepe") ||
            title.contains("phonepe") || title.contains("phone pe") ||
            body.contains("phonepe") || body.contains("phone pe") ||
            body.contains("via phonepe")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = NotificationParsingUtils.extractMerchant(body, merchantRegexes)

        val isRefund = NotificationParsingUtils.containsAny(lower, listOf("refund", "credited", "received"))
        val transactionKind = when {
            isRefund && amountMinor != null -> ParsedTransactionKind.REFUND
            amountMinor != null -> ParsedTransactionKind.SPEND
            else -> ParsedTransactionKind.UNKNOWN
        }
        val candidateType = when (transactionKind) {
            ParsedTransactionKind.SPEND -> TransactionCandidateType.SPEND
            else -> TransactionCandidateType.UNKNOWN
        }

        return NotificationParseResult(
            parserKey = "notification_phonepe",
            parserVersion = "v1",
            providerHint = "phonepe",
            transactionKind = transactionKind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = Mode.UPI,
            parseConfidence = if (amountMinor != null && merchant != null) 0.78 else 0.55,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "phonepe",
            toEntityName = merchant,
        )
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:sent to|paid to|payment to|to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
