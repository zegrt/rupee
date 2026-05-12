package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.RecurringPatternEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringPatternDao {
    @Query(
        """
        SELECT * FROM recurring_patterns
        WHERE userId = :userId
          AND isDismissed = 0
        ORDER BY isConfirmed DESC, nextExpectedAt
        """
    )
    fun observePatterns(userId: String): Flow<List<RecurringPatternEntity>>

    @Query("SELECT * FROM recurring_patterns WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecurringPatternEntity?

    @Query("SELECT * FROM recurring_patterns WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<RecurringPatternEntity>

    @Query("DELETE FROM recurring_patterns WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        DELETE FROM recurring_patterns
        WHERE userId = :userId
          AND sourceType = 'auto'
          AND isConfirmed = 0
          AND isDismissed = 0
        """
    )
    suspend fun deleteUnconfirmedAuto(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPattern(pattern: RecurringPatternEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPatterns(patterns: List<RecurringPatternEntity>)
}
