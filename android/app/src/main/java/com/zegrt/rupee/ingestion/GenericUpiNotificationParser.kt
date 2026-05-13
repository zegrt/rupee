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

        // Confidence ladder:
        //  - merchant resolved → high enough for AUTO_CREATED (0.7 is medium tier today,
        //    decision engine routes it to Inbox; raising to 0.85+ would auto-create
        //    on stranger bodies — keep at 0.7 for now)
        //  - merchant null but amount + masked digits both present → still a real
        //    transaction signal, just missing the payee. 0.62 puts it in MEDIUM
        //    so Inbox shows it instead of silently ignoring.
        //  - everything else → 0.5 LOW, gets dropped (correct — not enough signal).
        val confidence = when {
            amountMinor != null && merchant != null -> 0.7
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
            parserVersion = "v3",
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
