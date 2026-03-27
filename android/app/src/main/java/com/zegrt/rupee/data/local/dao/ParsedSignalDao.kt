package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.ParsedSignalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParsedSignalDao {
    @Query("SELECT * FROM parsed_signals ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentParsedSignals(limit: Int = 50): Flow<List<ParsedSignalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParsedSignal(signal: ParsedSignalEntity)
}

