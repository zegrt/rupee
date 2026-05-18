package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Phase 2a of dump enrichment: snapshot of what the user ultimately did with
 * each notification, joined at export time. Complements the dump's per-line
 * `outcome{}` block (which is frozen at ingest time) with the *current* state
 * of every row the notification produced.
 *
 * One row per rawEventId. LEFT JOINs throughout so a notification that was
 * filtered or gate-rejected (no parsed signal) still produces a row with
 * nulls beyond `rawEventId` — useful for triaging "why didn't this fire".
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
    // Non-null when the inbox row resolved to a canonical txn that wasn't
    // freshly created from this candidate — i.e. the user picked "merge with
    // existing". Detected by comparing inbox.linkedCanonicalTransactionId
    // against the synthetic "txn-<candidateId>" id used by
    // confirmInboxItem. If they differ, it was a merge.
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
     * class fields without updating the SELECT list — the schema test in
     * `dumps/` exercises the full join end-to-end.
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
            CASE
                WHEN ii.linkedCanonicalTransactionId IS NOT NULL
                 AND ii.linkedCanonicalTransactionId <> ('txn-' || tc.id)
                THEN ii.linkedCanonicalTransactionId
                ELSE NULL
            END                          AS mergedIntoExistingTxnId
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
