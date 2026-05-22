package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

/**
 * Catches ATM cash-withdrawal notifications across Indian banks. Sits ahead of
 * the bank-specific parsers in the registry so an "ATM Cash Wdl Rs.5000 from
 * A/c XX1234" body from the ICICI iMobile app routes here (CASH_WITHDRAWAL)
 * instead of through IciciNotificationParser (SPEND).
 *
 * The distinction matters: a CASH_WITHDRAWAL flows through
 * [com.zegrt.rupee.data.repository.LocalFinanceRepository.toCanonicalType] as
 * `CASH_ADJUSTMENT`, which is filtered out of monthly spend totals. Treating
 * the withdrawal as a regular spend would double-count whatever the user
 * later actually spends with that cash.
 *
 * Decision routing: candidateType = CASH_WITHDRAWAL doesn't match the
 * decision engine's isSpendLike check, so HIGH confidence still lands the
 * candidate in Inbox via NON_SPEND_REVIEW. That's deliberate — the user
 * sees "you withdrew Rs.5000" in Inbox and can confirm before it disappears
 * from spend totals. Auto-creating would silently hide the event.
 *
 * Body shapes covered (sampled from HDFC/ICICI/SBI/Axis ATM alerts):
 *   - "ATM Cash Wdl Rs.5,000 from A/c XX1234 on 12-May-26 at HDFC Bank ATM Delhi"
 *   - "Rs.5000 withdrawn from A/c XX1234 at ATM"
 *   - "Cash withdrawal of Rs.10000 from your ICICI Bank A/c XX5678 via ATM"
 *   - "SBI: Rs.2000 has been withdrawn from XX9999 at ATM/POS"
 */
class AtmNotificationParser : NotificationParser {

    override fun canParse(rawEvent: RawCaptureEventEntity): Boolean {
        val title = rawEvent.title.orEmpty().lowercase()
        val body = rawEvent.body.lowercase()
        val hay = "$title\n$body"
        // Require an ATM signal AND a money signal. "atm" alone catches
        // marketing ("Try our new ATM card" — which the gate would reject
        // anyway, but defensive). The money signal piggybacks on the verb
        // list since these bodies almost always say "withdrawn"/"wdl"/
        // "cash" alongside the amount.
        val hasAtmSignal = ATM_TOKENS.any { it in hay }
        if (!hasAtmSignal) return false
        val hasMoneySignal = MONEY_VERBS.any { it in hay }
        return hasMoneySignal
    }

    override fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        val body = rawEvent.body
        val lower = body.lowercase()
        val amountMinor = NotificationParsingUtils.extractAmountMinor(body)
        val maskedDigits = NotificationParsingUtils.extractMaskedDigits(body)
        val location = extractLocation(body)

        // S1.3 (rest) — migrated from the prior 3-tier `when`. ATM has
        // three signals (amount, masked digits, location); weights are
        // amount=3, digits=2, location=1. Tier landings preserved:
        //   amount + digits          = 5 = 0.85 (HIGH, was 0.85 — exact)
        //   amount + digits + locn   = 6 = 0.90 (HIGH, was 0.85 — within tier)
        //   amount + locn            = 4 = 0.78 (MEDIUM, was 0.65 — within tier)
        //   amount alone             = 3 = 0.62 (MEDIUM, was 0.65 — close)
        //   nothing                  = 0 = 0.30 (LOW, was 0.40 — within tier)
        val confidence = EvidenceTally()
            .addIf(amountMinor != null, "amount", 3)
            .addIf(maskedDigits != null, "maskedDigits", 2)
            .addIf(!location.isNullOrBlank(), "location", 1)
            .score()

        // Merchant is synthesised — ATM withdrawals don't have a payee in
        // the conventional sense. Surface the location when present so the
        // user can later add a merchant trust rule on "ATM Cash · Delhi"
        // for category prediction. The "Unnamed" fallback that
        // MerchantNameUtils.clean would otherwise emit is the wrong shape
        // here.
        val merchantClean = if (location.isNullOrBlank()) "ATM Cash" else "ATM Cash · $location"

        // Kind still SPEND so dump-replay can recognise the body as a
        // money-out event without learning a new kind discriminator.
        // candidateType is the carrier for the CASH_WITHDRAWAL semantic;
        // the downstream toCanonicalType mapping looks at candidateType.
        val kind = if (amountMinor == null) ParsedTransactionKind.UNKNOWN else ParsedTransactionKind.SPEND
        val candidateType = if (amountMinor == null) {
            TransactionCandidateType.UNKNOWN
        } else {
            TransactionCandidateType.CASH_WITHDRAWAL
        }

        return NotificationParseResult(
            parserKey = "notification_atm",
            parserVersion = "v2",
            providerHint = "atm",
            transactionKind = kind,
            candidateType = candidateType,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchantClean,
            maskedDigits = maskedDigits,
            mode = Mode.ATM,
            parseConfidence = confidence,
            fromEntityType = AccountType.BANK,
            fromEntityHint = rawEvent.sourceAppPackage,
            toEntityName = merchantClean,
        )
    }

    /**
     * Best-effort ATM location extraction. Bank notifications often include
     * the branch / city after the verb: "withdrawn at HDFC Bank ATM Delhi",
     * "Rs.X withdrawn from A/c XX1234 at HDFC ATM Andheri East". Pulls
     * tokens after "ATM" up to ~30 chars; returns null if nothing useful.
     *
     * Intentionally lenient — false positives are fine because the location
     * is decorative (shows up in the merchant display), not load-bearing
     * for trust rules.
     */
    private fun extractLocation(body: String): String? {
        for (regex in locationRegexes) {
            val match = regex.find(body) ?: continue
            val raw = match.groupValues[1].trim()
                .replace(Regex("[^A-Za-z0-9 ,.&'_-]"), "")
                .trim(',', '.', ' ', '-')
            if (raw.length in 2..30) return raw
        }
        return null
    }

    companion object {
        // Substrings that indicate an ATM-shaped body. "wdl" (HDFC's
        // abbreviation), "withdraw" (general), "atm" (explicit). One must
        // match for canParse to claim the body.
        private val ATM_TOKENS = listOf(
            "atm",
            "cash wdl",
            "cash withdrawal",
            "withdrawn from",
            "withdrew",
        )

        // Money verb on top of the ATM signal — keeps marketing pushes out
        // ("Apply for a new ATM card") even though TransactionalGate
        // already catches most of those.
        private val MONEY_VERBS = listOf(
            "withdrawn",
            "withdrew",
            "wdl",
            "cash withdrawal",
            "debited",
            "rs.",
            "rs ",
            "inr",
            "₹",
        )

        private val locationRegexes = listOf(
            // "...at HDFC Bank ATM Delhi" → "HDFC Bank ATM Delhi"
            // "...at HDFC ATM Andheri East" → "HDFC ATM Andheri East"
            Regex("""\bat\s+([A-Za-z0-9 .,&'_-]{2,40})""", RegexOption.IGNORE_CASE),
            // "...from HDFC Bank ATM, Delhi" → "HDFC Bank ATM, Delhi"
            Regex("""\bfrom\s+([A-Za-z0-9 .,&'_-]{2,40}\s*atm[A-Za-z0-9 .,&'_-]{0,30})""", RegexOption.IGNORE_CASE),
        )
    }
}
