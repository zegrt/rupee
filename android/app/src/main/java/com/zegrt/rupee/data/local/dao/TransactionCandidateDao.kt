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
