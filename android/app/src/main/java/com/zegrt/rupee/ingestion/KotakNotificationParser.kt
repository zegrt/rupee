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
        // Known Kotak packages — the substring catches Kotak811 ("kotak811..."),
        // the explicit prefixes catch the main Kotak Bank app (com.msf.kbank).
        // Add more variants here as they're observed in real dumps.
        return "kotak" in pkg || pkg.startsWith("com.msf.kbank")
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val amountMinor = NotificationParsingUtils.extractAmountMinor(rawEvent.body)
        val maskedDigits = NotificationParsingUtils.extractMaskedDigits(rawEvent.body)
        val isUpi = "upi" in rawEvent.body.lowercase()

        val confidence = when {
            amountMinor != null && maskedDigits != null -> 0.75
            amountMinor != null -> 0.65
            else -> 0.4
        }

        return NotificationParseResult(
            parserKey = "notification_kotak",
            parserVersion = "v1",
            providerHint = "kotak",
            transactionKind = if (amountMinor != null) ParsedTransactionKind.SPEND else ParsedTransactionKind.UNKNOWN,
            candidateType = if (amountMinor != null) TransactionCandidateType.SPEND else TransactionCandidateType.UNKNOWN,
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
