package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class GenericNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean = true

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val amountMinor = extractAmountMinor(rawEvent.body)
        val merchant = extractMerchant(rawEvent.body)
        val maskedDigits = NotificationParsingUtils.extractMaskedDigits(rawEvent.body)
        val mode = inferMode(rawEvent.body)
        val direction = NotificationParsingUtils.classifyDirection(rawEvent.body)
        val isIncome = direction == NotificationParsingUtils.MoneyDirection.IN

        // S1.3 (rest) — migrated from the prior 4-tier `when`. The Generic
        // parser is the fallback: weak by design. Weights amount=2,
        // merchant=1, digits=1 preserve every tier landing the previous
        // ladder produced — amount+merchant and amount+digits both score 3
        // points (0.62, exact match) so the central MEDIUM threshold still
        // catches the Kotak-style "Amount debited from XX4129" body. Triple-
        // signal lands at 0.78, slightly inflated within MEDIUM tier (no
        // tier promotion since the Generic parser's 0.78 is still below the
        // HIGH=0.85 threshold).
        val confidence = EvidenceTally()
            .addIf(amountMinor != null, "amount", 2)
            .addIf(merchant != null, "merchant", 1)
            .addIf(maskedDigits != null, "maskedDigits", 1)
            .score()

        val kind = when {
            amountMinor == null -> ParsedTransactionKind.UNKNOWN
            isIncome -> ParsedTransactionKind.INCOME
            else -> ParsedTransactionKind.SPEND
        }
        val candidateType = when {
            amountMinor == null -> TransactionCandidateType.UNKNOWN
            isIncome -> TransactionCandidateType.INCOME
            else -> TransactionCandidateType.SPEND
        }

        return NotificationParseResult(
            parserKey = "notification_generic",
            parserVersion = "v3",
            providerHint = rawEvent.sourceAppPackage,
            transactionKind = kind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            maskedDigits = maskedDigits,
            mode = mode,
            parseConfidence = confidence,
            fromEntityType = AccountType.BANK,
            fromEntityHint = rawEvent.sourceAppPackage,
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
        )
    }

    // Route through the shared util — this used to be a bespoke regex
    // that (a) treated `.` and `,` as interchangeable decimal/grouping
    // separators (so `₹1,593.77` parsed as `₹159.00`) and (b) used
    // `Double * 100 → Long` rounding that can flip a `19.99` body
    // to `1999.99 → 199999L` or `200000L` depending on JVM rounding
    // mode. Both bugs were filed as MONEY-MATH-LEGACY after the
    // alpha.1 Walnut SMS-bridge dump surfaced the comma case.
    // The shared util handles Indian comma-grouping (`1,00,000`,
    // `1,00,00,000`) and uses BigDecimal.movePointRight(2) for
    // bit-exact paise.
    private fun extractAmountMinor(body: String): Long? =
        NotificationParsingUtils.extractAmountMinor(body)

    private fun extractMerchant(body: String): String? {
        val regex = Regex("""(?:to|at)\s+([A-Za-z0-9 .&_-]{2,40})""", RegexOption.IGNORE_CASE)
        return regex.find(body)?.groupValues?.get(1)?.trim()
    }

    private fun inferMode(body: String): Mode? {
        val lower = body.lowercase()
        return when {
            "upi" in lower -> Mode.UPI
            "debit card" in lower -> Mode.DEBIT_CARD
            "credit card" in lower || "card" in lower -> Mode.CREDIT_CARD
            "atm" in lower -> Mode.ATM
            else -> null
        }
    }
}

