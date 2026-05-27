package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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

    /**
     * @Upsert is defense-in-depth — `parsed_signals` is the CASCADE parent
     * of `transaction_candidates`, so any REPLACE on a parsed_signal would
     * cascade-delete every candidate (and via the chain, every inbox row)
     * pointing at it. Not exploitable today because every call site uses a
     * fresh UUID for the new row, but trivial to exploit accidentally from
     * a future code path that wants to re-write a parsed_signal in place.
     * Third instance of the alpha.2 bug class (TransactionCandidateDao +
     * CanonicalTransactionDao were the first two). Reference:
     * https://dexterslog.com/posts/insert-on-conflict-replace-with-on-delete-cascade-in-sqlite/
     */
    @Upsert
    suspend fun upsertParsedSignal(signal: ParsedSignalEntity)
}

data class ParserBucketCount(val bucket: String, val count: Int)

