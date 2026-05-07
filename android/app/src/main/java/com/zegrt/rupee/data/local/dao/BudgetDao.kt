package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY periodStart DESC")
    fun observeActiveBudgets(): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE userId = :userId
          AND isActive = 1
          AND budgetType = 'MONTHLY_TOTAL'
          AND periodStart <= :date
          AND periodEnd >= :date
        ORDER BY periodStart DESC
        LIMIT 1
        """
    )
    fun observeMonthlyTotalBudgetForDate(
        userId: String,
        date: String,
    ): Flow<BudgetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgets(budgets: List<BudgetEntity>)
}

