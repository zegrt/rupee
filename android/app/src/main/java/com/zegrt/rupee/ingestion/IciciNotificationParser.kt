package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class IciciNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val packageName = rawEvent.sourceAppPackage.orEmpty().lowercase()
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()

        return packageName.contains("icici") ||
            title.contains("icici") ||
            body.contains("icici")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = NotificationParsingUtils.extractMerchant(body, merchantRegexes)
        val maskedDigits = NotificationParsingUtils.extractMaskedDigits(body)
        val isDue = NotificationParsingUtils.containsAny(
            text = lower,
            needles = listOf("payment due", "total due", "minimum due", "statement due", "due amount"),
        )
        val isSpend = amountMinor != null && NotificationParsingUtils.containsAny(
            text = lower,
            needles = listOf("spent", "purchase", "debited", "used at", "transaction"),
        )

        val transactionKind = when {
            isDue -> ParsedTransactionKind.BILL_DUE
            isSpend -> ParsedTransactionKind.SPEND
            else -> ParsedTransactionKind.UNKNOWN
        }

        val candidateType = when (transactionKind) {
            ParsedTransactionKind.BILL_DUE -> TransactionCandidateType.CARD_DUE
            ParsedTransactionKind.SPEND -> TransactionCandidateType.SPEND
            else -> TransactionCandidateType.UNKNOWN
        }

        return NotificationParseResult(
            parserKey = "notification_icici",
            parserVersion = "v1",
            providerHint = "icici",
            transactionKind = transactionKind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            sourceCardHint = "icici",
            maskedDigits = maskedDigits,
            mode = when {
                lower.contains("upi") -> Mode.UPI
                lower.contains("debit card") -> Mode.DEBIT_CARD
                lower.contains("credit card") || maskedDigits != null -> Mode.CREDIT_CARD
                else -> null
            },
            parseConfidence = confidenceFor(transactionKind, amountMinor, maskedDigits, merchant),
            fromEntityHint = "icici",
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
        )
    }

    private fun confidenceFor(
        transactionKind: ParsedTransactionKind,
        amountMinor: Long?,
        maskedDigits: String?,
        merchant: String?,
    ): Double {
        return when {
            transactionKind == ParsedTransactionKind.BILL_DUE && amountMinor != null && maskedDigits != null -> 0.9
            transactionKind == ParsedTransactionKind.SPEND && amountMinor != null && merchant != null -> 0.79
            transactionKind != ParsedTransactionKind.UNKNOWN && amountMinor != null -> 0.68
            else -> 0.28
        }
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:at|on|to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
