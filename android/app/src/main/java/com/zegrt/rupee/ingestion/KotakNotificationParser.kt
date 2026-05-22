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
        return isKotakPkg
        // Previously this also required one of a local TRANSACTIONAL_VERBS list
        // (`sent via`, `debited`, etc.) to be present in the body. That list was
        // redundant with `TransactionalGate.POSITIVE_VERBS` — by the time a body
        // reaches the parser the gate has already filtered any verb-free
        // marketing push. Worse, the local list had drifted: v0.14.1 added
        // `sent from` to the central gate for Kotak811's title-only shape
        // (`₹3.00 sent from XX4129`) but the parser-level list was not
        // updated, so the 2026-05-22 Nothing-A015 dump showed a real Kotak
        // ₹3 debit clearing the gate, then falling through this parser into
        // GenericNotificationParser — losing the kotak provider hint and the
        // forced Mode.UPI.
        //
        // The other parsers that look superficially similar use verb matching
        // for routing/disambiguation (real ATM vs "Try our new ATM card",
        // real EMI vs "EMI options available", real UPI vs "Your UPI ID is")
        // — those stay. Kotak's was just transactionality, which the gate
        // already enforces.
        //
        // Dropped 2026-05-22 (KOTAK-VERB-DRIFT) from the Nothing-A015 dump
        // audit.
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
