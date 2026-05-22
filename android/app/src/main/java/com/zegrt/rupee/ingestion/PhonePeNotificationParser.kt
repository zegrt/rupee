package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class PhonePeNotificationParser : NotificationParser {
    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val pkg = rawEvent.sourceAppPackage.orEmpty().lowercase()
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()
        return pkg.contains("phonepe") ||
            title.contains("phonepe") || title.contains("phone pe") ||
            body.contains("phonepe") || body.contains("phone pe") ||
            body.contains("via phonepe")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = NotificationParsingUtils.extractMerchant(body, merchantRegexes)
        val direction = NotificationParsingUtils.classifyDirection(body)
        val isRefund = "refund" in lower
        val transactionKind = when {
            amountMinor == null -> ParsedTransactionKind.UNKNOWN
            isRefund -> ParsedTransactionKind.REFUND
            direction == NotificationParsingUtils.MoneyDirection.IN -> ParsedTransactionKind.INCOME
            else -> ParsedTransactionKind.SPEND
        }
        val candidateType = when (transactionKind) {
            ParsedTransactionKind.SPEND -> TransactionCandidateType.SPEND
            // REFUND is real money back in the user's account — route it like
            // INCOME end-to-end. Without this branch the kind drops to UNKNOWN
            // and the canonical-txn writer defaults to EXPENSE, double-counting
            // the original spend instead of cancelling it.
            ParsedTransactionKind.INCOME, ParsedTransactionKind.REFUND -> TransactionCandidateType.INCOME
            else -> TransactionCandidateType.UNKNOWN
        }

        // S1.3 (rest) — migrated from the prior `if (amount + merchant) 0.78
        // else 0.55` ternary. amount=2 + merchant=2 lands exactly on 0.78 for
        // the two-signal case and exactly on 0.55 for the one-signal case;
        // tier preservation is bit-for-bit.
        val confidence = EvidenceTally()
            .addIf(amountMinor != null, "amount", 2)
            .addIf(merchant != null, "merchant", 2)
            .score()

        return NotificationParseResult(
            parserKey = "notification_phonepe",
            parserVersion = "v3",
            providerHint = "phonepe",
            transactionKind = transactionKind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = Mode.UPI,
            parseConfidence = confidence,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "phonepe",
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
        )
    }

    companion object {
        private val merchantRegexes = listOf(
            Regex("""(?:sent to|paid to|payment to|to)\s+([A-Za-z0-9 .&'_-]{2,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
