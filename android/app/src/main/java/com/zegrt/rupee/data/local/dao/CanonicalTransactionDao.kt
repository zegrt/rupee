package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CanonicalTransactionDao {
    @Query(
        """
        SELECT * FROM canonical_transactions
        WHERE userId = :userId
        ORDER BY occurredAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentTransactions(
        userId: String,
        limit: Int = 20,
    ): Flow<List<CanonicalTransactionEntity>>

    @Query("SELECT * FROM canonical_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): CanonicalTransactionEntity?

    // Spend totals join accounts and credit_cards so per-account / per-card
    // "exclude from expense totals" toggles take effect. Transactions without
    // an accountId / creditCardId (cash, unattributed) still count — the
    // IS NULL OR = 0 form covers that.
    @Query(
        """
        SELECT COALESCE(SUM(t.amountMinor), 0)
        FROM canonical_transactions AS t
        LEFT JOIN accounts AS a ON t.accountId = a.id
        LEFT JOIN credit_cards AS cc ON t.creditCardId = cc.id
        WHERE t.userId = :userId
          AND t.type = 'EXPENSE'
          AND t.status != 'IGNORED'
          AND t.isHiddenFromBudget = 0
          AND (a.excludeFromExpenseTotals IS NULL OR a.excludeFromExpenseTotals = 0)
          AND (cc.excludeFromExpenseTotals IS NULL OR cc.excludeFromExpenseTotals = 0)
          AND t.occurredAt >= :fromIso
          AND t.occurredAt < :untilIso
        """
    )
    fun observeSpentInPeriod(
        userId: String,
        fromIso: String,
        untilIso: String,
    ): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(t.amountMinor), 0)
        FROM canonical_transactions AS t
        LEFT JOIN accounts AS a ON t.accountId = a.id
        LEFT JOIN credit_cards AS cc ON t.creditCardId = cc.id
        WHERE t.userId = :userId
          AND t.type = 'EXPENSE'
          AND t.status != 'IGNORED'
          AND t.isHiddenFromBudget = 0
          AND (a.excludeFromExpenseTotals IS NULL OR a.excludeFromExpenseTotals = 0)
          AND (cc.excludeFromExpenseTotals IS NULL OR cc.excludeFromExpenseTotals = 0)
          AND t.occurredAt >= :fromIso
          AND t.occurredAt < :untilIso
        """
    )
    suspend fun getSpentInPeriod(
        userId: String,
        fromIso: String,
        untilIso: String,
    ): Long

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

    @Query(
        """
        SELECT * FROM canonical_transactions
        WHERE userId = :userId
          AND status = 'SUGGESTED'
        ORDER BY occurredAt DESC
        LIMIT :limit
        """
    )
    fun observeSuggestedTransactions(
        userId: String,
        limit: Int = 50,
    ): Flow<List<CanonicalTransactionEntity>>

    @Query(
        """
        SELECT * FROM canonical_transactions
        WHERE userId = :userId
          AND status != 'IGNORED'
          AND occurredAt >= :fromIso
          AND occurredAt < :untilIso
        ORDER BY occurredAt DESC
        """
    )
    fun observeTransactionsInPeriod(
        userId: String,
        fromIso: String,
        untilIso: String,
    ): Flow<List<CanonicalTransactionEntity>>

    @Query(
        """
        SELECT * FROM canonical_transactions
        WHERE userId = :userId
          AND status != 'IGNORED'
          AND occurredAt >= :fromIso
          AND occurredAt < :untilIso
        ORDER BY occurredAt DESC
        """
    )
    suspend fun getTransactionsInPeriod(
        userId: String,
        fromIso: String,
        untilIso: String,
    ): List<CanonicalTransactionEntity>

    @Query(
        """
        SELECT t.categoryId AS categoryId, COALESCE(SUM(t.amountMinor), 0) AS amountMinor
        FROM canonical_transactions AS t
        LEFT JOIN accounts AS a ON t.accountId = a.id
        LEFT JOIN credit_cards AS cc ON t.creditCardId = cc.id
        WHERE t.userId = :userId
          AND t.type = 'EXPENSE'
          AND t.status != 'IGNORED'
          AND t.isHiddenFromBudget = 0
          AND (a.excludeFromExpenseTotals IS NULL OR a.excludeFromExpenseTotals = 0)
          AND (cc.excludeFromExpenseTotals IS NULL OR cc.excludeFromExpenseTotals = 0)
          AND t.occurredAt >= :fromIso
          AND t.occurredAt < :untilIso
          AND t.categoryId IS NOT NULL
        GROUP BY t.categoryId
        """
    )
    fun observeSpentByCategoryInPeriod(
        userId: String,
        fromIso: String,
        untilIso: String,
    ): Flow<List<CategorySpend>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<CanonicalTransactionEntity>)
}
