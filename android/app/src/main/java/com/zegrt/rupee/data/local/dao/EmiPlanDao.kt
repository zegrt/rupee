package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.EmiPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmiPlanDao {
    @Query("SELECT * FROM emi_plans WHERE userId = :userId ORDER BY nextDueAt IS NULL, nextDueAt, name")
    fun observePlans(userId: String): Flow<List<EmiPlanEntity>>

    @Query("DELETE FROM emi_plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: EmiPlanEntity)
}
