package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

/**
 * Catches UPI payment notifications that aren't attributed to a known UPI app
 * (PhonePe, Paytm, BHIM, banking apps with their own UPI flows, mocks posted
 * from our own debug surface). The phrasing varies but always pairs an amount
 * with a "paid" verb and "UPI". Stays after the GPay parser so a real Google
 * Pay alert still gets the gpay provider hint.
 */
class GenericUpiNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()
        val mentionsUpi = body.contains("upi") || title.contains("upi")
        if (!mentionsUpi) return false
        // Bank/wallet apps phrase UPI debits with multiple verbs:
        //  - "paid"     (Google Pay, Paytm, generic UPI receipts)
        //  - "sent"     (Kotak811, Fi, Jupiter — "₹X sent via UPI")
        //  - "debited"  (banks confirming an account debit on a UPI flow)
        // Without "sent"/"debited" we drop Kotak-style notifications entirely.
        val hay = "$title\n$body"
        val mentionsPayment =
            hay.contains("paid") ||
                hay.contains("payment to") ||
                hay.contains("paying") ||
                hay.contains("sent") ||
                hay.contains("debited") ||
                hay.contains("received") ||
                hay.contains("credited")
        return mentionsPayment
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val amountMinor = NotificationParsingUtils.extractAmountMinor(rawEvent.body)
        val merchant = NotificationParsingUtils.extractMerchant(rawEvent.body, merchantRegexes)
        val maskedDigits = NotificationParsingUtils.extractMaskedDigits(rawEvent.body)
        val networkRef = NotificationParsingUtils.extractNetworkReference(rawEvent.body)
        val direction = NotificationParsingUtils.classifyDirection(rawEvent.body)
        val isIncome = direction == NotificationParsingUtils.MoneyDirection.IN

        // S1.3 pilot — additive evidence tally (see EvidenceTally.kt) instead
        // of the prior per-case `when` ladder. Weights:
        //   amount     +1   (floor; without it nothing else matters)
        //   merchant   +3   (regex captured a payee — strongest single signal;
        //                    the "paid to X" / "to X" pattern means we
        //                    parsed a directional verb, not a generic balance
        //                    push)
        //   digits     +2   (account ending in XXXX — names a specific source)
        //   networkRef +2   (UPI / NEFT reference id — unique to a real txn)
        //
        // The tally maps to the same HIGH (≥0.85) / MEDIUM (≥0.6) bands
        // `NotificationDecisionEngine` already reads. Dump-replay against the
        // current corpus shows no tier reshuffling at these weights — only
        // the underlying number changes:
        //   amount+merchant+digits      = 6 → 0.90  (was 0.85, still HIGH)
        //   amount+merchant+digits+ref  = 8 → 0.90  (caps at HIGH)
        //   amount+merchant             = 4 → 0.78  (unchanged)
        //   amount+digits               = 3 → 0.62  (unchanged)
        //   amount only                 = 1 → 0.30  (was 0.50, still LOW)
        //
        // Mode is intentionally NOT in the tally — every body that reaches
        // this parser is Mode.UPI by construction, so it's not additive
        // evidence.
        val confidence = EvidenceTally()
            .addIf(amountMinor != null, "amount", 1)
            .addIf(merchant != null, "merchant", 3)
            .addIf(maskedDigits != null, "maskedDigits", 2)
            .addIf(networkRef != null, "networkRef", 2)
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
            parserKey = "notification_upi_generic",
            parserVersion = "v4",
            providerHint = "upi",
            transactionKind = kind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            maskedDigits = maskedDigits,
            mode = Mode.UPI,
            parseConfidence = confidence,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "upi",
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
        )
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:to|paid to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
