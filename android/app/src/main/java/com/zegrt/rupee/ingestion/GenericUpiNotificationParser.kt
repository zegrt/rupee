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
        val direction = NotificationParsingUtils.classifyDirection(rawEvent.body)
        val isIncome = direction == NotificationParsingUtils.MoneyDirection.IN

        // Confidence ladder is tiered by how many independent corroborating
        // signals the parser extracted. Three signals matter:
        //   - amountMinor    (the money — required for any non-LOW tier)
        //   - merchant       (the payee — distinguishes a real UPI receipt
        //                     from a balance / status push)
        //   - maskedDigits   (the source account — confirms the body actually
        //                     names a specific account, not a generic "sent
        //                     ₹X via UPI" balance push)
        //
        // Two corroborating signals + amount → HIGH (auto-create at 0.85+).
        // One corroborating signal → MEDIUM (Inbox review).
        // Amount only → LOW (ignore — could be anything).
        //
        // The previous static 0.7 cap pinned every fully-extracted UPI debit
        // to MEDIUM, meaning the user got an Inbox review for every routine
        // UPI spend even when merchant + amount + account were all extracted
        // cleanly. Review fatigue → mass-confirms → defeats the Inbox. With
        // the HIGH tier reachable here, routine UPI debits to a known payee
        // from a recognised account auto-create as SUGGESTED on the Home
        // screen.
        //
        // Mode is intentionally NOT a tier discriminator — every body that
        // reaches this parser is Mode.UPI by construction, so it's not
        // additive evidence.
        val confidence = when {
            amountMinor != null && merchant != null && maskedDigits != null -> 0.85
            amountMinor != null && merchant != null -> 0.78
            amountMinor != null && maskedDigits != null -> 0.62
            else -> 0.5
        }

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
