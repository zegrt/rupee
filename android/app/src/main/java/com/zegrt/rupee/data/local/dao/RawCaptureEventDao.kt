package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawCaptureEventDao {
    @Query("SELECT * FROM raw_capture_events ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRecentRawEvents(limit: Int = 50): Flow<List<RawCaptureEventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRawCaptureEvent(event: RawCaptureEventEntity): Long

    @Query("DELETE FROM raw_capture_events")
    suspend fun deleteAll()
}

