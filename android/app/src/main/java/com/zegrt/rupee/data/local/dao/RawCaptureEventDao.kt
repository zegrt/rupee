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

    @Query("UPDATE raw_capture_events SET ingestionStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateIngestionStatus(id: String, status: String, updatedAt: String)

    @Query("DELETE FROM raw_capture_events")
    suspend fun deleteAll()

    /**
     * Time-based prune. Deletes raw events older than [cutoffIso] (ISO-8601
     * timestamp). H4's FK cascades sweep up the downstream parsed_signals →
     * transaction_candidates → inbox_items chain automatically, but the
     * SET_NULL clauses on `linkedCanonicalTransactionId` mean any
     * user-confirmed canonical transaction survives — just with the dangling
     * pointer cleared. Returns the row count deleted (useful for telemetry
     * and tests).
     */
    @Query("DELETE FROM raw_capture_events WHERE receivedAt < :cutoffIso")
    suspend fun deleteOlderThan(cutoffIso: String): Int
}

