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

        // Amount + masked digits is enough signal to surface an Inbox candidate
        // even when no merchant is in the body (Kotak-style "Amount debited
        // from XX4129. Check out details." has no payee — that lives in the
        // bank app). 0.62 clears the MEDIUM threshold so it stops getting
        // silently dropped as LOW.
        val confidence = when {
            amountMinor != null && merchant != null -> 0.62
            amountMinor != null && maskedDigits != null -> 0.62
            amountMinor != null -> 0.55
            else -> 0.15
        }

        return NotificationParseResult(
            parserKey = "notification_generic",
            parserVersion = "v2",
            providerHint = rawEvent.sourceAppPackage,
            transactionKind = if (amountMinor != null) ParsedTransactionKind.SPEND else ParsedTransactionKind.UNKNOWN,
            candidateType = if (amountMinor != null) TransactionCandidateType.SPEND else TransactionCandidateType.UNKNOWN,
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

    private fun extractAmountMinor(body: String): Long? {
        val regex = Regex("""(?:rs\.?|inr|₹)\s*([0-9]+(?:[.,][0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        val match = regex.find(body) ?: return null
        val normalized = match.groupValues[1].replace(",", "")
        return normalized.toDoubleOrNull()?.times(100)?.toLong()
    }

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

