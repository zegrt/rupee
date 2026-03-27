package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.AccountType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY sortOrder, displayName")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts WHERE userId = :userId AND accountType = :accountType")
    suspend fun countAccountsByType(userId: String, accountType: AccountType): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<AccountEntity>)
}
