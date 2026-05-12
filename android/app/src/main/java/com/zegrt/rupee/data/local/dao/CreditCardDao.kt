package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardDao {
    @Query("SELECT * FROM credit_cards WHERE isActive = 1 ORDER BY sortOrder, displayName")
    fun observeActiveCards(): Flow<List<CreditCardEntity>>

    @Query("SELECT COUNT(*) FROM credit_cards WHERE userId = :userId")
    suspend fun countCards(userId: String): Int

    @Query("SELECT * FROM credit_cards WHERE userId = :userId AND isActive = 1")
    suspend fun getActiveCards(userId: String): List<CreditCardEntity>

    @Query("SELECT * FROM credit_cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: String): CreditCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCards(cards: List<CreditCardEntity>)
}
