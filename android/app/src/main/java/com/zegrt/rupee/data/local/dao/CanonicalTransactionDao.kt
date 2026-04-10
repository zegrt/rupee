package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CanonicalTransactionDao {
    @Query("SELECT * FROM canonical_transactions ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int = 20): Flow<List<CanonicalTransactionEntity>>

    @Query("SELECT * FROM canonical_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): CanonicalTransactionEntity?

    @Query(
        """
        SELECT * FROM canonical_transactions
        WHERE userId = :userId
          AND dedupeFingerprint = :fingerprint
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestByDedupeFingerprint(
        userId: String,
        fingerprint: String,
    ): CanonicalTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<CanonicalTransactionEntity>)
}
