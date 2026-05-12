package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class EmiNotificationParser : NotificationParser {

    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()
        // "emi" alone is too loose — fires on marketing copy like "EMI options available"
        // or "no EMI due this month". Require it to be paired with a transaction signal
        // so we only claim bodies that are actually about a charge or upcoming due.
        val hasEmiWord = " emi" in " $title" || " emi" in " $body" ||
            title.startsWith("emi") || body.startsWith("emi")
        if (!hasEmiWord) return false
        val hasTransactionSignal = TRANSACTION_SIGNALS.any { it in title || it in body }
        return hasTransactionSignal
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = extractEmiName(body)
            ?: NotificationParsingUtils.extractMerchant(body, fallbackRegexes)
        // "debited", "deducted", "auto-debit" are unambiguous debit verbs — when all of
        // (amount, merchant, debit verb) are present we treat the parse as high-
        // confidence so the decision engine auto-creates the SUGGESTED txn.
        val hasDebitVerb = DEBIT_VERBS.any { it in lower }

        val confidence = when {
            amountMinor != null && merchant != null && hasDebitVerb -> 0.88
            amountMinor != null && merchant != null -> 0.72
            else -> 0.50
        }

        return NotificationParseResult(
            parserKey = "notification_emi",
            parserVersion = "v1",
            providerHint = null,
            transactionKind = ParsedTransactionKind.EMI,
            candidateType = TransactionCandidateType.EMI_DUE,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = Mode.BANK_TRANSFER,
            parseConfidence = confidence,
            fromEntityType = AccountType.BANK,
            fromEntityHint = null,
            toEntityName = MerchantNameUtils.cleanForEntity(merchant),
            dueDateIso = NotificationParsingUtils.extractDueDateIso(body),
        )
    }

    private fun extractEmiName(body: String): String? {
        for (regex in emiNameRegexes) {
            val m = regex.find(body) ?: continue
            val name = m.groupValues[1].trim()
            if (name.length >= 3) return name
        }
        return null
    }

    companion object {
        private val TRANSACTION_SIGNALS = listOf(
            "debited", "deducted", "paid", "auto-debit", "auto debit",
            "due", "scheduled", "charged", "instalment", "installment",
        )
        private val DEBIT_VERBS = listOf(
            "debited", "deducted", "auto-debit", "auto debit",
        )

        private val emiNameRegexes = listOf(
            Regex("""emi\s+of\s+[₹Rs.,0-9]+\s+for\s+([A-Za-z0-9 .&'_-]{3,50})""", RegexOption.IGNORE_CASE),
            Regex("""(?:emi|instalment)\s+(?:for|towards)\s+([A-Za-z0-9 .&'_-]{3,50})""", RegexOption.IGNORE_CASE),
            Regex("""(?:towards|for)\s+([A-Za-z0-9 .&'_-]{3,50})\s+emi""", RegexOption.IGNORE_CASE),
        )
        private val fallbackRegexes = listOf(
            Regex("""(?:for|towards)\s+([A-Za-z0-9 .&'_-]{3,50})""", RegexOption.IGNORE_CASE),
        )
    }
}
