package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BudgetType {
    MONTHLY_TOTAL,
    CATEGORY,
    BUCKET,
}

@Entity(
    tableName = "budgets",
    indices = [
        Index("userId"),
        Index("budgetType"),
        Index(value = ["periodStart", "periodEnd"]),
        Index("isActive"),
    ],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val budgetType: BudgetType,
    val targetRefId: String? = null,
    val limitMinor: Long,
    val currencyCode: String,
    val periodStart: String,
    val periodEnd: String,
    val alertThresholdPercent: Double,
    val isActive: Boolean = true,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

