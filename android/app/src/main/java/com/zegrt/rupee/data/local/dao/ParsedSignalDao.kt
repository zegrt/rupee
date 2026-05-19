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

    /**
     * Splits parsed_signal volume by gate-rejected vs ingested-by-parser
     * for the ingestion health card. `parser_key = 'gate_rejected'` is the
     * sentinel writeGateRejectedSignal sets; everything else is a real
     * parser hit.
     */
    @Query(
        """
        SELECT
            CASE WHEN parserKey = 'gate_rejected' THEN 'gate_rejected' ELSE 'ingested' END AS bucket,
            COUNT(*) AS count
        FROM parsed_signals
        WHERE createdAt >= :fromIso
        GROUP BY bucket
        """
    )
    fun observeBucketCountsSince(fromIso: String): Flow<List<ParserBucketCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParsedSignal(signal: ParsedSignalEntity)
}

data class ParserBucketCount(val bucket: String, val count: Int)

