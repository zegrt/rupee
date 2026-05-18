package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Phase 2a of dump enrichment: snapshot of what the user ultimately did with
 * each notification, joined at export time. Complements the dump's per-line
 * `outcome{}` block (which is frozen at ingest time) with the *current* state
 * of every row the notification produced.
 *
 * One row per matched rawEventId. The join starts FROM parsed_signals, so
 * raw events that never produced a parsed signal — filtered (listener-level
 * skip) and gate-rejected — are absent from the result. The dump's per-line
 * `outcome{}` block already records those decisions at ingest time, so
 * pairing the two files by rawEventId still answers "why didn't this fire".
 * LEFT JOINs from candidate onward so a parsed signal that never reached
 * Inbox or canonical (auto-ignored, awaiting review) still surfaces.
 */
data class DumpOutcomeSnapshot(
    val rawEventId: String,
    // From parsed_signals — what we extracted. Mostly redundant with the dump
    // line's `outcome{}` block today, but re-parses (planned for the JSON rule
    // engine) would diverge, and the snapshot is the source of truth then.
    val parserKey: String?,
    val parserVersion: String?,
    val transactionKind: String?,
    // From transaction_candidates — current decision state.
    val candidateId: String?,
    val candidateDecisionState: String?,
    val candidateDecisionReason: String?,
    // From inbox_items — what the user did, if anything.
    val inboxItemId: String?,
    val inboxDecisionState: String?,
    val inboxResolvedAt: String?,
    val inboxLinkedCanonicalTxnId: String?,
    // From canonical_transactions — the user-visible row's current state.
    // Joined via COALESCE(inbox.linkedCanonicalTransactionId,
    //                     candidate.linkedCanonicalTransactionId) so that
    // both confirm-from-inbox and auto-create paths surface the same row.
    val canonicalTxnId: String?,
    val canonicalStatus: String?,
    val canonicalType: String?,
    val canonicalAmountMinor: Long?,
    val canonicalMerchantName: String?,
    val canonicalCategoryId: String?,
    val canonicalNotes: String?,
    // Non-null when the user picked "merge with existing" in Inbox. As of
    // v10 this column maps directly to inbox_items.mergedFromExistingCanonicalId,
    // which `confirmInboxItemMergedWith` writes at merge time. The old
    // reverse-engineered CASE expression (comparing
    // linkedCanonicalTransactionId against a synthetic "txn-<candidateId>")
    // is gone — no more silent breakage if the candidate-id format changes.
    val mergedIntoExistingTxnId: String?,
)

@Dao
interface DumpOutcomeDao {

    /**
     * One pass, returns one row per matched rawEventId. Chunking is the
     * repository's responsibility — SQLite's parameter cap (999 on older
     * builds) means callers must split large id lists.
     *
     * Column aliases are explicit because Room maps result columns by name
     * onto the POJO constructor parameter names. Don't rename the data
     * class fields without updating the SELECT list — KSP catches column
     * mismatches at build time.
     */
    @Query(
        """
        SELECT
            ps.rawCaptureEventId         AS rawEventId,
            ps.parserKey                 AS parserKey,
            ps.parserVersion             AS parserVersion,
            ps.transactionKind           AS transactionKind,
            tc.id                        AS candidateId,
            tc.decisionState             AS candidateDecisionState,
            tc.decisionReason            AS candidateDecisionReason,
            ii.id                        AS inboxItemId,
            ii.decisionState             AS inboxDecisionState,
            ii.resolvedAt                AS inboxResolvedAt,
            ii.linkedCanonicalTransactionId AS inboxLinkedCanonicalTxnId,
            ct.id                        AS canonicalTxnId,
            ct.status                    AS canonicalStatus,
            ct.type                      AS canonicalType,
            ct.amountMinor               AS canonicalAmountMinor,
            ct.merchantName              AS canonicalMerchantName,
            ct.categoryId                AS canonicalCategoryId,
            ct.notes                     AS canonicalNotes,
            ii.mergedFromExistingCanonicalId AS mergedIntoExistingTxnId
        FROM parsed_signals AS ps
        LEFT JOIN transaction_candidates AS tc
            ON tc.parsedSignalId = ps.id
        LEFT JOIN inbox_items AS ii
            ON ii.transactionCandidateId = tc.id
        LEFT JOIN canonical_transactions AS ct
            ON ct.id = COALESCE(ii.linkedCanonicalTransactionId, tc.linkedCanonicalTransactionId)
        WHERE ps.rawCaptureEventId IN (:rawEventIds)
        """
    )
    suspend fun getDumpOutcomeSnapshot(rawEventIds: List<String>): List<DumpOutcomeSnapshot>
}
