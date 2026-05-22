package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class GPayNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val packageName = rawEvent.sourceAppPackage.orEmpty()
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()

        if (packageName.contains("google.android.apps.nbu.paisa")) return true
        if (title.contains("gpay") || title.contains("google pay")) return true
        if (body.contains("google pay")) return true
        return false
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val amountMinor = NotificationParsingUtils.extractAmountMinor(rawEvent.body)
        val merchant = NotificationParsingUtils.extractMerchant(rawEvent.body, merchantRegexes)
        val direction = NotificationParsingUtils.classifyDirection(rawEvent.body)
        val isIncome = direction == NotificationParsingUtils.MoneyDirection.IN

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

        // S1.3 (rest) — migrated from the prior `if (amount + merchant) 0.8 else 0.6` ternary.
        // GPay has no masked-digits or network-ref to lean on, so the tally is
        // just (amount, merchant) with weights tuned to keep the existing tier
        // landings: amount+merchant stays MEDIUM (was 0.80 → now 0.78), amount-
        // only stays MEDIUM (was 0.60 → now 0.62), nothing drops to LOW.
        val confidence = EvidenceTally()
            .addIf(amountMinor != null, "amount", 3)
            .addIf(merchant != null, "merchant", 1)
            .score()

        return NotificationParseResult(
            parserKey = "notification_gpay",
            parserVersion = "v3",
            providerHint = "gpay",
            transactionKind = kind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = Mode.UPI,
            parseConfidence = confidence,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "gpay",
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
        )
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:to|paid to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
