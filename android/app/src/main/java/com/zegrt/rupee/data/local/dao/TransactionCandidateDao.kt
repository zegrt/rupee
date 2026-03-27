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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactionCandidate(candidate: TransactionCandidateEntity)
}

