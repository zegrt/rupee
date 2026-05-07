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
        val mentionsPayment = body.contains("paid") || body.contains("payment to") ||
            body.contains("paying") || title.contains("paid")
        return mentionsPayment
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val amountMinor = NotificationParsingUtils.extractAmountMinor(rawEvent.body)
        val merchant = NotificationParsingUtils.extractMerchant(rawEvent.body, merchantRegexes)

        return NotificationParseResult(
            parserKey = "notification_upi_generic",
            parserVersion = "v1",
            providerHint = "upi",
            transactionKind = if (amountMinor != null) ParsedTransactionKind.SPEND else ParsedTransactionKind.UNKNOWN,
            candidateType = if (amountMinor != null) TransactionCandidateType.SPEND else TransactionCandidateType.UNKNOWN,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = Mode.UPI,
            parseConfidence = if (amountMinor != null && merchant != null) 0.7 else 0.5,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "upi",
            toEntityName = merchant,
        )
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:to|paid to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
