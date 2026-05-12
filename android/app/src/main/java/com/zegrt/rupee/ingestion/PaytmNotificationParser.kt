package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class PaytmNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val pkg = rawEvent.sourceAppPackage.orEmpty().lowercase()
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()
        return pkg.contains("one97") || pkg.contains("paytm") ||
            title.contains("paytm") ||
            body.contains("paytm")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = NotificationParsingUtils.extractMerchant(body, merchantRegexes)

        // Drop "received" — too generic, fires on routine spend bodies.
        val isRefund = NotificationParsingUtils.containsAny(lower, listOf("refund", "credited", "cashback"))
        val isWallet = lower.contains("paytm wallet") || lower.contains("wallet balance")
        val transactionKind = when {
            isRefund && amountMinor != null -> ParsedTransactionKind.REFUND
            amountMinor != null -> ParsedTransactionKind.SPEND
            else -> ParsedTransactionKind.UNKNOWN
        }
        val candidateType = when (transactionKind) {
            ParsedTransactionKind.SPEND -> TransactionCandidateType.SPEND
            else -> TransactionCandidateType.UNKNOWN
        }
        val mode = when {
            isWallet -> Mode.WALLET
            lower.contains("upi") -> Mode.UPI
            else -> Mode.UPI
        }

        return NotificationParseResult(
            parserKey = "notification_paytm",
            parserVersion = "v1",
            providerHint = "paytm",
            transactionKind = transactionKind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = mode,
            parseConfidence = if (amountMinor != null && merchant != null) 0.76 else 0.52,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "paytm",
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
        )
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:paid to|payment to|to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
