package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class CredNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val packageName = rawEvent.sourceAppPackage.orEmpty().lowercase()
        val title = rawEvent.title.orEmpty()
        val body = rawEvent.body
        // Word-boundary match so "Credit Card" in an ICICI/HDFC body does not get
        // mis-routed here. CRED's actual package is com.dreamplug.androidapp.
        val brandRegex = Regex("""\bCRED\b""", RegexOption.IGNORE_CASE)
        return packageName.contains("dreamplug") ||
            packageName == "com.cred" ||
            brandRegex.containsMatchIn(title) ||
            brandRegex.containsMatchIn(body)
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = NotificationParsingUtils.extractMerchant(body, merchantRegexes)
        val maskedDigits = NotificationParsingUtils.extractMaskedDigits(body)
        val direction = NotificationParsingUtils.classifyDirection(body)
        // CRED is broader than credit cards now: CRED Pay (UPI), Mint (interest
        // credits), Cash (loan disbursals), cashback to bank account. All of those
        // are real inflows. Lean on classifyDirection so they route to INCOME, but
        // keep the card-specific kinds (BILL_DUE, PAYMENT-of-bill) ordered first
        // since their phrasing is specific and shouldn't be overridden by a stray
        // "credited" keyword.
        val isIncome = amountMinor != null &&
            direction == NotificationParsingUtils.MoneyDirection.IN

        // CRED-ICICI-AUDIT (2026-05-22): isIncome runs *before* isCardSpend
        // so a credit-side notification (refund / reversal / chargeback /
        // statement credit / EMI-conversion reversal) doesn't get
        // miscategorised as SPEND. isCardSpend matches generic verbs
        // ("transaction", "debited", "charged") that also appear in refund
        // / reversal bodies — e.g. "Refund transaction of Rs.500 credited
        // to your HDFC card via CRED". Without this reorder the body's
        // "transaction" keyword wins over the direction classifier and the
        // user's spend total inflates by the refund amount.
        // Mirrors the ladder structure IciciNotificationParser already uses.
        val transactionKind = when {
            isCardDue(lower) -> ParsedTransactionKind.BILL_DUE
            isCardPayment(lower) -> ParsedTransactionKind.PAYMENT
            isIncome -> ParsedTransactionKind.INCOME
            isCardSpend(lower, amountMinor) -> ParsedTransactionKind.SPEND
            else -> ParsedTransactionKind.UNKNOWN
        }

        val candidateType = when (transactionKind) {
            ParsedTransactionKind.SPEND -> TransactionCandidateType.SPEND
            ParsedTransactionKind.BILL_DUE -> TransactionCandidateType.CARD_DUE
            ParsedTransactionKind.PAYMENT -> TransactionCandidateType.TRANSFER
            ParsedTransactionKind.INCOME -> TransactionCandidateType.INCOME
            else -> TransactionCandidateType.UNKNOWN
        }

        // Mode depends on the flow, not the parser. CRED Pay UPI receipts and CRED
        // Pay UPI spends both say "via UPI" in the body — honour that. Card-specific
        // kinds (BILL_DUE, PAYMENT-of-bill, SPEND-on-card) stay CREDIT_CARD.
        val mode = when {
            "upi" in lower -> Mode.UPI
            transactionKind == ParsedTransactionKind.BILL_DUE ||
                transactionKind == ParsedTransactionKind.PAYMENT ||
                transactionKind == ParsedTransactionKind.SPEND -> Mode.CREDIT_CARD
            transactionKind == ParsedTransactionKind.INCOME -> Mode.BANK_TRANSFER
            else -> Mode.CREDIT_CARD
        }

        return NotificationParseResult(
            parserKey = "notification_cred",
            parserVersion = "v2",
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
            mode = mode,
            parseConfidence = confidenceFor(transactionKind, amountMinor, maskedDigits, merchant),
            fromEntityHint = "cred",
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
            dueDateIso = if (transactionKind == ParsedTransactionKind.BILL_DUE)
                NotificationParsingUtils.extractDueDateIso(body) else null,
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
            transactionKind == ParsedTransactionKind.INCOME && amountMinor != null -> 0.82
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
