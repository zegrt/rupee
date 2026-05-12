package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.MerchantTrustRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantTrustRuleDao {
    @Query("SELECT * FROM merchant_trust_rules WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeRules(userId: String): Flow<List<MerchantTrustRuleEntity>>

    @Query("SELECT * FROM merchant_trust_rules WHERE userId = :userId")
    suspend fun getRulesForUser(userId: String): List<MerchantTrustRuleEntity>

    // Indexed (userId, merchantPattern UNIQUE) lookup — used by the normalizer per
    // notification, hot path. Pattern must be passed in already-cleaned form so it
    // hits the index directly rather than scanning every rule.
    @Query(
        """
        SELECT * FROM merchant_trust_rules
        WHERE userId = :userId
          AND LOWER(merchantPattern) = LOWER(:cleanedPattern)
        LIMIT 1
        """
    )
    suspend fun findByCleanedPattern(
        userId: String,
        cleanedPattern: String,
    ): MerchantTrustRuleEntity?

    @Query("DELETE FROM merchant_trust_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: MerchantTrustRuleEntity)
}
