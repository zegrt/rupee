package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactionCandidate(candidate: TransactionCandidateEntity)
}
