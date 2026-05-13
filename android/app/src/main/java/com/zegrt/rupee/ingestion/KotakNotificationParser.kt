package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

/**
 * Kotak Mahindra (main bank app) and Kotak811 (digital bank) post transaction
 * notifications shaped like:
 *
 *   title: "₹10.00 sent via UPI"        ← amount lives here
 *   text/big: "Amount debited from XX4129. Check out details."
 *                                       ← masked digits live here; merchant
 *                                       does not (it's only in the Kotak app)
 *
 * No payee field is ever included in the bank-side notification, so we emit
 * merchantRaw = null and trust the user to fill it in via Inbox. Confidence
 * is medium (0.75) so the candidate routes to Inbox rather than auto-creating
 * with a missing merchant.
 *
 * Why a dedicated parser vs. just leaning on GenericUpi: this lets us report
 * `providerHint = "kotak"` (useful for future cross-stream chaining when a
 * Truecaller mirror of the same SMS arrives) and confidently set mode = UPI
 * regardless of body phrasing.
 */
class KotakNotificationParser : NotificationParser {

    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val pkg = rawEvent.sourceAppPackage.orEmpty().lowercase()
        val isKotakPkg = "kotak" in pkg || pkg.startsWith("com.msf.kbank")
        if (!isKotakPkg) return false
        // Kotak's app sends both real txn alerts AND marketing pushes (e.g.
        // "Just ₹2,500/month → ₹64,415 with Kotak Recurring Deposit. T&C").
        // Since we always emit merchantRaw=null, a marketing match becomes a
        // null-merchant MEDIUM-confidence candidate → "Unnamed" Inbox row.
        // Require at least one transactional verb in the combined body to fire.
        val body = rawEvent.body.lowercase()
        return TRANSACTIONAL_VERBS.any { it in body }
    }

    private companion object {
        private val TRANSACTIONAL_VERBS = listOf(
            "sent via",
            "debited",
            "credited",
            "paid",
            "received",
            "deducted",
            "auto-debit",
            "withdrawn",
            "spent",
        )
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val amountMinor = NotificationParsingUtils.extractAmountMinor(rawEvent.body)
        val maskedDigits = NotificationParsingUtils.extractMaskedDigits(rawEvent.body)
        val isUpi = "upi" in rawEvent.body.lowercase()
        val direction = NotificationParsingUtils.classifyDirection(rawEvent.body)
        val isIncome = direction == NotificationParsingUtils.MoneyDirection.IN

        val confidence = when {
            amountMinor != null && maskedDigits != null -> 0.75
            amountMinor != null -> 0.65
            else -> 0.4
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
            parserKey = "notification_kotak",
            parserVersion = "v2",
            providerHint = "kotak",
            transactionKind = kind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = null,
            maskedDigits = maskedDigits,
            mode = if (isUpi) Mode.UPI else Mode.BANK_TRANSFER,
            parseConfidence = confidence,
            fromEntityType = AccountType.BANK,
            fromEntityHint = "kotak",
            toEntityName = null,
        )
    }
}
