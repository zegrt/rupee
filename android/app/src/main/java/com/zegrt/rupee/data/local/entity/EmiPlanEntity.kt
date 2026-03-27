package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "emi_plans",
    indices = [
        Index("userId"),
        Index("nextDueAt"),
        Index("isConfirmed"),
    ],
)
data class EmiPlanEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val linkedAccountId: String? = null,
    val linkedCreditCardId: String? = null,
    val monthlyAmountMinor: Long,
    val remainingTenureMonths: Int? = null,
    val nextDueAt: String? = null,
    val totalOutstandingMinor: Long? = null,
    val sourceType: String,
    val isConfirmed: Boolean = false,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

