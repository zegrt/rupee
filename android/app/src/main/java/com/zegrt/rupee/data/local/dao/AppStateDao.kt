package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.AppStateEntity

@Dao
interface AppStateDao {
    /**
     * Returns the raw stored string, or null if the key has never been set.
     * Callers parse the value with whatever shape they wrote.
     */
    @Query("SELECT value FROM app_state WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AppStateEntity)
}
