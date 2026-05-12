package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType {
    BANK,
    CASH,
}

@Entity(
    tableName = "accounts",
    indices = [
        Index("userId"),
        Index("accountType"),
        Index("providerName"),
        Index("isActive"),
    ],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val accountType: AccountType,
    val displayName: String,
    val providerName: String? = null,
    val maskedIdentifier: String? = null,
    val currencyCode: String,
    val openingBalanceMinor: Long? = null,
    val currentBalanceMinor: Long? = null,
    // When true, transactions on this account are skipped in spend totals.
    // For wallets/transit cards where the inflow was already counted at the
    // source bank — toggling this prevents double-counting. Per Axio §5.9.
    val excludeFromExpenseTotals: Boolean = false,
    val excludeFromIncomeTotals: Boolean = false,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

