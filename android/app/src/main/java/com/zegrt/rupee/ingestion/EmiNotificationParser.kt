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
        return title.contains("emi") || body.contains("emi")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val merchant = extractEmiName(body)
            ?: NotificationParsingUtils.extractMerchant(body, fallbackRegexes)

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
            parseConfidence = if (amountMinor != null && merchant != null) 0.72 else 0.50,
            fromEntityType = AccountType.BANK,
            fromEntityHint = null,
            toEntityName = merchant,
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
