package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

/**
 * Audit record for what the ingestion pipeline did with a single notification.
 *
 * Threaded out of [NotificationSignalNormalizer.ingestNotification] and consumed
 * by [com.zegrt.rupee.diagnostics.NotificationDumper] (debug-only). The point
 * is to make the dump self-describing: every line carries both the raw bank
 * text AND what Rupee decided to do with it (which parser fired, what was
 * extracted, where the resulting candidate landed). Without this, a dump only
 * answers "what does the bank send?" — with it, the dump also answers "did
 * Rupee handle it right?" and "where did this end up?".
 *
 * The reason ladder mirrors the listener's decision flow:
 *  - [Outcome.Filtered] — listener-level reject (group summary, self-package,
 *    empty body, raw fingerprint duplicate). No DB writes happened.
 *  - [Outcome.GateRejected] — TransactionalGate threw it out as marketing/OTP/etc.
 *    A bookkeeping `parsed_signal` row is written but no candidate/Inbox row.
 *  - [Outcome.Ingested] — normalized end-to-end. Carries the full parse output,
 *    decision, and pointers to whatever rows were created (candidate, inbox,
 *    canonical txn, card update). The exact set of non-null id fields tells you
 *    where this notification landed.
 *
 * Persistence note: this object never hits the DB. It's an in-memory return
 * value scoped to a single ingestion call. Capturing it durably is the
 * dumper's job (debug builds only).
 */
sealed interface IngestionResult {

    /**
     * Listener rejected the notification before normalization started.
     * No DB writes attempted; [rawEventId] is null because nothing was persisted.
     */
    data class Filtered(val reason: FilterReason) : IngestionResult

    /**
     * Normalization ran but TransactionalGate rejected the body as non-
     * transactional. A `parsed_signal` row was written for auditability.
     */
    data class GateRejected(
        val rawEventId: String,
        val gateReason: String,
        val matched: String,
    ) : IngestionResult

    /**
     * Full ingest path. At least the parsed_signal + candidate rows were
     * written; [inboxItemId] / [canonicalTransactionId] are non-null when
     * the decision routed there. Both can be null for a candidate that was
     * dedupe-killed mid-ingest (sibling notification arrived seconds earlier).
     */
    data class Ingested(
        val rawEventId: String,
        val parsedSignalId: String,
        val candidateId: String,
        val parserKey: String,
        val parserVersion: String,
        val providerHint: String?,
        val transactionKind: ParsedTransactionKind,
        val candidateType: TransactionCandidateType,
        val amountMinor: Long?,
        val currencyCode: String?,
        val merchantRaw: String?,
        val toEntityName: String?,
        val mode: Mode?,
        val maskedDigits: String?,
        val parseConfidence: Double,
        val networkReferenceId: String?,
        val networkReferenceType: String?,
        val dueDateIso: String?,
        val confidenceTier: ConfidenceTier?,
        val decisionState: CandidateDecisionState,
        val decisionReason: CandidateDecisionReason,
        // Non-null when the matching merchant-trust rule fired and the
        // candidate was AUTO_CREATED with status = CONFIRMED.
        val trustRuleMatched: Boolean,
        // The candidate.id of an earlier candidate that the dedupe engine
        // matched against; the current candidate's linkedCanonicalTransactionId
        // points at the same canonical row when this is non-null.
        val dedupedAgainstCandidateId: String?,
        // The canonical_transaction.id of an earlier auto-created txn that
        // dedupe matched against. Either this or [dedupedAgainstCandidateId]
        // (or both) being non-null means this raw event added no new state
        // beyond its parsed_signal + candidate audit rows.
        val dedupedAgainstCanonicalTxnId: String?,
        val inboxItemId: String?,
        val canonicalTransactionId: String?,
        // Set when a BILL_DUE candidate updated a credit_card row's due
        // fields. Read this to diagnose "the upcoming-dues card should have
        // updated but didn't" symptoms.
        val billDueAppliedToCardId: String?,
    ) : IngestionResult

    enum class FilterReason {
        // Notification listener saw an Android-managed grouped summary that
        // duplicates child content — we skip these to avoid double-counting.
        GROUP_SUMMARY,
        // We posted the notification ourselves (debug mock) without the magic
        // extra flag — drop to avoid feedback loops.
        SELF_PACKAGE,
        // Extractor pulled nothing usable out of the notification extras.
        EMPTY_BODY,
        // The raw fingerprint already exists in raw_capture_events; this is
        // the same notification re-posted by Android (often happens when a
        // notification updates in place).
        RAW_DUPLICATE,
        // Catch-all for exceptions surfaced from inside ingestNotification.
        // The dumper records the raw text but no DB state changed.
        INGEST_FAILED,
        // Normalizer wasn't available (cold start / process-death edge).
        NORMALIZER_UNAVAILABLE,
    }
}
