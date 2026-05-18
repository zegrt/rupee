package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxItemDao {
    @Query(
        """
        SELECT * FROM inbox_items
        WHERE userId = :userId
          AND decisionState = :state
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun observeInboxItems(
        userId: String,
        state: InboxDecisionState = InboxDecisionState.PENDING,
        limit: Int = 50,
    ): Flow<List<InboxItemEntity>>

    /**
     * Same row set as [observeInboxItems], but each row is hydrated with its
     * underlying `transaction_candidates` row via [InboxItemWithCandidate].
     *
     * `@Transaction` is required (and Room enforces it) because `@Relation`
     * runs an additional query per parent set — without the transaction
     * wrapper, the inbox rows and candidates could be read across two
     * different DB snapshots, opening a hairline race where a freshly-
     * confirmed inbox row points at a candidate that's already mutated.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM inbox_items
        WHERE userId = :userId
          AND decisionState = :state
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun observeInboxItemsWithCandidates(
        userId: String,
        state: InboxDecisionState = InboxDecisionState.PENDING,
        limit: Int = 50,
    ): Flow<List<InboxItemWithCandidate>>

    @Query("SELECT * FROM inbox_items WHERE id = :id LIMIT 1")
    suspend fun getInboxItemById(id: String): InboxItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInboxItem(item: InboxItemEntity)
}
