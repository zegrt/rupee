package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class PaytmNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val pkg = rawEvent.sourceAppPackage.orEmpty().lowercase()
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()
        return pkg.contains("one97") || pkg.contains("paytm") ||
            title.contains("paytm") ||
            body.contains("paytm")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = NotificationParsingUtils.extractMerchant(body, merchantRegexes)

        val direction = NotificationParsingUtils.classifyDirection(body)
        val isRefund = "refund" in lower || "cashback" in lower
        val isWallet = lower.contains("paytm wallet") || lower.contains("wallet balance")
        val transactionKind = when {
            amountMinor == null -> ParsedTransactionKind.UNKNOWN
            isRefund -> ParsedTransactionKind.REFUND
            direction == NotificationParsingUtils.MoneyDirection.IN -> ParsedTransactionKind.INCOME
            else -> ParsedTransactionKind.SPEND
        }
        val candidateType = when (transactionKind) {
            ParsedTransactionKind.SPEND -> TransactionCandidateType.SPEND
            // REFUND and cashback are real money back in the user's account —
            // route them like INCOME end-to-end. Without this branch the kind
            // drops to UNKNOWN and the canonical-txn writer defaults to
            // EXPENSE, double-counting the original spend instead of
            // cancelling it.
            ParsedTransactionKind.INCOME, ParsedTransactionKind.REFUND -> TransactionCandidateType.INCOME
            else -> TransactionCandidateType.UNKNOWN
        }
        val mode = when {
            isWallet -> Mode.WALLET
            lower.contains("upi") -> Mode.UPI
            else -> Mode.UPI
        }

        // S1.3 (rest) — migrated from the prior `if (amount + merchant) 0.76
        // else 0.52` ternary. Weights tuned to preserve tier landings:
        // amount+merchant stays MEDIUM (was 0.76 → now 0.78), amount-only or
        // merchant-only stays LOW (was 0.52 → now 0.55).
        val confidence = EvidenceTally()
            .addIf(amountMinor != null, "amount", 2)
            .addIf(merchant != null, "merchant", 2)
            .score()

        return NotificationParseResult(
            parserKey = "notification_paytm",
            parserVersion = "v3",
            providerHint = "paytm",
            transactionKind = transactionKind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = mode,
            parseConfidence = confidence,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "paytm",
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
        )
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:paid to|payment to|to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
