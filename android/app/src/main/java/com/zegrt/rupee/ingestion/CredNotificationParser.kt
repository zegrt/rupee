package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class CredNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val packageName = rawEvent.sourceAppPackage.orEmpty().lowercase()
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()

        return packageName.contains("cred") ||
            title.contains("cred") ||
            body.contains("cred")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = NotificationParsingUtils.extractMerchant(body, merchantRegexes)
        val maskedDigits = NotificationParsingUtils.extractMaskedDigits(body)

        val transactionKind = when {
            isCardDue(lower) -> ParsedTransactionKind.BILL_DUE
            isCardPayment(lower) -> ParsedTransactionKind.PAYMENT
            isCardSpend(lower, amountMinor) -> ParsedTransactionKind.SPEND
            else -> ParsedTransactionKind.UNKNOWN
        }

        val candidateType = when (transactionKind) {
            ParsedTransactionKind.SPEND -> TransactionCandidateType.SPEND
            ParsedTransactionKind.BILL_DUE -> TransactionCandidateType.CARD_DUE
            ParsedTransactionKind.PAYMENT -> TransactionCandidateType.TRANSFER
            else -> TransactionCandidateType.UNKNOWN
        }

        return NotificationParseResult(
            parserKey = "notification_cred",
            parserVersion = "v1",
            providerHint = "cred",
            transactionKind = transactionKind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            sourceCardHint = when {
                lower.contains("icici") -> "icici"
                lower.contains("sbi") -> "sbi"
                lower.contains("kotak") -> "kotak"
                else -> "cred_card"
            },
            maskedDigits = maskedDigits,
            mode = Mode.CREDIT_CARD,
            parseConfidence = confidenceFor(transactionKind, amountMinor, maskedDigits, merchant),
            fromEntityHint = "cred",
            toEntityName = merchant,
        )
    }

    private fun isCardDue(lower: String): Boolean {
        return NotificationParsingUtils.containsAny(
            text = lower,
            needles = listOf(
                "due amount",
                "bill due",
                "payment due",
                "statement due",
                "minimum due",
                "total due",
            ),
        )
    }

    private fun isCardPayment(lower: String): Boolean {
        return NotificationParsingUtils.containsAny(
            text = lower,
            needles = listOf(
                "bill paid",
                "payment received",
                "card payment",
                "paid your",
                "payment successful",
            ),
        )
    }

    private fun isCardSpend(lower: String, amountMinor: Long?): Boolean {
        if (amountMinor == null) return false
        return NotificationParsingUtils.containsAny(
            text = lower,
            needles = listOf(
                "spent",
                "purchase",
                "transaction",
                "charged",
                "debited",
                "used at",
            ),
        )
    }

    private fun confidenceFor(
        transactionKind: ParsedTransactionKind,
        amountMinor: Long?,
        maskedDigits: String?,
        merchant: String?,
    ): Double {
        return when {
            transactionKind == ParsedTransactionKind.SPEND && amountMinor != null && merchant != null -> 0.88
            transactionKind == ParsedTransactionKind.BILL_DUE && amountMinor != null && maskedDigits != null -> 0.91
            transactionKind == ParsedTransactionKind.PAYMENT && amountMinor != null && maskedDigits != null -> 0.86
            transactionKind != ParsedTransactionKind.UNKNOWN && amountMinor != null -> 0.76
            else -> 0.35
        }
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:at|on|to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
