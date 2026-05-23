package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionCandidateDao {
    @Query("SELECT * FROM transaction_candidates ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentTransactionCandidates(limit: Int = 50): Flow<List<TransactionCandidateEntity>>

    @Query("SELECT * FROM transaction_candidates WHERE id = :id LIMIT 1")
    suspend fun getTransactionCandidateById(id: String): TransactionCandidateEntity?

    /**
     * "Have we seen this fingerprint recently?" — used by the dedupe engine.
     * Includes IGNORED rows on purpose: when three identical low-confidence
     * mirrors arrive within seconds (Truecaller's SMS bridge typically
     * fires the same body 2-3× a few seconds apart), all three would
     * otherwise write distinct IGNORED rows because each one's lookup
     * filtered out the prior IGNORED tombstones. See
     * docs/ingestion-pipeline-research-2026-05-19.md §3 for the dump-traced
     * root cause.
     */
    @Query(
        """
        SELECT * FROM transaction_candidates
        WHERE userId = :userId
          AND candidateFingerprint = :fingerprint
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestByFingerprint(
        userId: String,
        fingerprint: String,
    ): TransactionCandidateEntity?

    /**
     * Non-tombstone variant. Used wherever we want "a candidate we'd link
     * a confirm / merge against" — IGNORED rows are excluded because
     * they're decisions we've already made.
     */
    @Query(
        """
        SELECT * FROM transaction_candidates
        WHERE userId = :userId
          AND candidateFingerprint = :fingerprint
          AND decisionState != 'IGNORED'
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestUsableByFingerprint(
        userId: String,
        fingerprint: String,
    ): TransactionCandidateEntity?

    /**
     * @Upsert generates `INSERT ... ON CONFLICT(id) DO UPDATE SET ...` —
     * an in-place update on conflict, not a delete-then-insert.
     *
     * `@Insert(onConflict = REPLACE)` was the old shape; Room rendered that
     * as `INSERT OR REPLACE`. SQLite's REPLACE conflict resolution **deletes
     * the conflicting row first**, then inserts a new one. Because
     * `inbox_items.transactionCandidateId` has `onDelete = CASCADE`, every
     * REPLACE on a candidate row cascade-deleted any inbox row pointing at
     * it. `NotificationSignalNormalizer.normalizeLocked` re-upserts the
     * candidate after writing the inbox row (to backfill
     * `linkedInboxItemId`), so every INBOX_PENDING-bound notification had
     * its inbox row silently destroyed at write time. The v0.15.0-alpha.1
     * stage-roll surfaced this — user saw zero items in Inbox even though
     * `IngestionResult.Ingested` was returned. Same cascade also affected
     * other re-upsert sites (confirmInboxItemMergedWith, etc.) and the
     * `duplicateOfCandidateId` SET NULL on dedupe chains.
     *
     * Authoritative reference for the SQLite REPLACE-cascade interaction:
     * https://dexterslog.com/posts/insert-on-conflict-replace-with-on-delete-cascade-in-sqlite/
     */
    @Upsert
    suspend fun upsertTransactionCandidate(candidate: TransactionCandidateEntity)
}
