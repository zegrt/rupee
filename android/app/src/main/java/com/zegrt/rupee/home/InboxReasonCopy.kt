package com.zegrt.rupee.home

import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.InboxReasonCode
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity

/**
 * Derives the short reason chip shown beneath every Inbox row.
 *
 * Centralised + pure so `HomeViewModelTest` (and future UI tests) can pin
 * the mapping without touching Compose. The previous derivation was a
 * one-liner inside `buildReviewRows` that just rendered the enum name
 * (`MEDIUM_CONFIDENCE` → "MEDIUM CONFIDENCE") — readable but uninformative.
 *
 * Spec: PRD §15 ("every inferred transaction should be explainable").
 * Backlog INBOX-WHY-COPY.
 *
 * The expanded copy reads off the *candidate* state, not the parsed signal,
 * because the existing `@Relation` already hydrates the candidate alongside
 * the inbox row. Joining `parsed_signals` for `parserKey` / `maskedDigits`
 * would either need a nested @Relation (rewires every consumer of
 * `InboxItemWithCandidate.candidate`) or a schema denorm — both heavier
 * than this Sprint 2 polish item is sized for. "MASKED DIGITS MISSING" /
 * "NEW SENDER" are explicitly deferred; what we *can* tell from the
 * candidate is its merchant, amount, confidence tier, and direction.
 */
internal object InboxReasonCopy {
    /**
     * Pick the most specific reason that applies. Ordering matters — the
     * inbox row's `reasonCode` records why the decision engine routed it
     * here (e.g. duplicate-suspected), but the *content* of the candidate
     * often gives the user a more actionable explanation ("no merchant
     * extracted" beats "medium confidence" because the latter doesn't
     * tell them what to look at).
     */
    fun forInbox(
        reasonCode: InboxReasonCode,
        candidate: TransactionCandidateEntity?,
    ): String {
        // The enum-derived reasons that carry their own meaning win first —
        // a duplicate-conflict is the user's decision to make regardless of
        // the candidate's amount/merchant shape.
        when (reasonCode) {
            InboxReasonCode.POSSIBLE_DUPLICATE_CONFLICT -> return "DUPLICATE SUSPECTED"
            InboxReasonCode.MISSING_ACCOUNT_MAPPING -> return "ACCOUNT UNKNOWN"
            InboxReasonCode.MISSING_CATEGORY -> return "NEEDS CATEGORY"
            InboxReasonCode.AMBIGUOUS_MERCHANT -> return "MERCHANT UNCLEAR"
            InboxReasonCode.AMBIGUOUS_KIND -> return "DIRECTION UNCLEAR"
            InboxReasonCode.MEDIUM_CONFIDENCE -> Unit
        }

        // MEDIUM_CONFIDENCE is the catch-all the decision engine emits when
        // a parse made it through but didn't clear the HIGH threshold. Look
        // at what's actually present on the candidate to say something
        // useful instead of just "MEDIUM CONFIDENCE".
        if (candidate == null) return "LOW CONFIDENCE"

        // LOW tier overrides everything else — even if the row has merchant
        // and amount, a LOW score is the headline.
        if (candidate.confidenceTier == ConfidenceTier.LOW) return "LOW CONFIDENCE"

        val merchantMissing = candidate.toEntityName.isNullOrBlank()
        val amountPresent = candidate.amountMinor != null

        return when {
            merchantMissing && amountPresent -> "AMOUNT ONLY"
            merchantMissing && !amountPresent -> "NO MERCHANT"
            else -> "REVIEW SUGGESTED"
        }
    }
}
