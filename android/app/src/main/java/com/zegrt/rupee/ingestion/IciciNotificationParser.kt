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
        // ICICI is a bank — accounts receive money too. Lean on the central direction
        // classifier so credits ("Rs.10 credited to A/c XX1234", "received from …")
        // route to INCOME instead of falling to UNKNOWN/SPEND. Any future bank parser
        // (SBI, HDFC, Axis, …) should do the same — see NotificationParsingUtils.classifyDirection.
        val direction = NotificationParsingUtils.classifyDirection(body)
        val isIncome = amountMinor != null && direction == NotificationParsingUtils.MoneyDirection.IN
        val isSpend = amountMinor != null && !isIncome && NotificationParsingUtils.containsAny(
            text = lower,
            needles = listOf("spent", "purchase", "debited", "used at", "transaction"),
        )

        val transactionKind = when {
            isDue -> ParsedTransactionKind.BILL_DUE
            isIncome -> ParsedTransactionKind.INCOME
            isSpend -> ParsedTransactionKind.SPEND
            else -> ParsedTransactionKind.UNKNOWN
        }

        val candidateType = when (transactionKind) {
            ParsedTransactionKind.BILL_DUE -> TransactionCandidateType.CARD_DUE
            ParsedTransactionKind.INCOME -> TransactionCandidateType.INCOME
            ParsedTransactionKind.SPEND -> TransactionCandidateType.SPEND
            else -> TransactionCandidateType.UNKNOWN
        }

        return NotificationParseResult(
            parserKey = "notification_icici",
            parserVersion = "v2",
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
            dueDateIso = if (transactionKind == ParsedTransactionKind.BILL_DUE)
                NotificationParsingUtils.extractDueDateIso(body) else null,
        )
    }

    private fun confidenceFor(
        transactionKind: ParsedTransactionKind,
        amountMinor: Long?,
        maskedDigits: String?,
        merchant: String?,
    ): Double {
        // S1.3 (rest) — migrated from the per-kind 5-tier `when`. Same
        // kind-as-evidence pattern as CredNotificationParser. Weights:
        // amount=1, merchant=2, maskedDigits=2, known-kind=2.
        //
        // Tier landings preserved vs the old per-kind floors:
        //   BILL_DUE + amount + digits   + known = 5 → 0.85 (was 0.90, HIGH)
        //   BILL_DUE + amount + digits + merchant + known = 7 → 0.90
        //   SPEND   + amount + merchant + known  = 5 → 0.85 (was 0.79, MEDIUM→HIGH)
        //   INCOME  + amount + digits + known    = 5 → 0.85 (was 0.82, MEDIUM→HIGH)
        //   any known + amount alone             = 3 → 0.62 (was 0.68, MEDIUM)
        //   UNKNOWN  + amount                    = 1 → 0.30 (was 0.28, LOW)
        //
        // Two tier promotions: ICICI SPEND with amount+merchant and ICICI
        // INCOME with amount+digits both move from MEDIUM (Inbox) to HIGH
        // (auto-create as SUGGESTED). This matches the corresponding CRED
        // kinds — symmetry between the two card-issuer parsers. Both bodies
        // had been routing to Inbox at 0.79/0.82 (just below the 0.85 HIGH
        // threshold); promoting them brings the tier in line with the CRED
        // analogue and the rest of the bank parsers (PhonePe at 0.78 stays
        // MEDIUM because it's not card-specific).
        val isKnownKind = transactionKind != ParsedTransactionKind.UNKNOWN
        return EvidenceTally()
            .addIf(amountMinor != null, "amount", 1)
            .addIf(merchant != null, "merchant", 2)
            .addIf(maskedDigits != null, "maskedDigits", 2)
            .addIf(isKnownKind, "knownKind", 2)
            .score()
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:at|on|to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
