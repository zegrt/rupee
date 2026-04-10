package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxItemDao {
    @Query("SELECT * FROM inbox_items WHERE decisionState = :state ORDER BY createdAt DESC LIMIT :limit")
    fun observeInboxItems(
        state: InboxDecisionState = InboxDecisionState.PENDING,
        limit: Int = 50,
    ): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items WHERE id = :id LIMIT 1")
    suspend fun getInboxItemById(id: String): InboxItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInboxItem(item: InboxItemEntity)
}
