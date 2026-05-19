package com.zegrt.rupee.data.local.dao

/**
 * Rolled-up view of the ingestion funnel over a recent window — see
 * `LocalFinanceRepository.observeIngestionHealth` and the Debug-screen
 * health card.
 *
 *   totalReceived
 *     ├── parsedCount        (ingestionStatus = PARSED)
 *     │     ├── gateRejectedCount   (parsed_signals.parserKey = 'gate_rejected')
 *     │     └── ingestedCount       (real parser hit)
 *     ├── failedCount        (ingestionStatus = FAILED — an exception fired)
 *     └── inFlightCount      (ingestionStatus = CAPTURED — still in-progress
 *                             at query time, almost always 0)
 *
 * parsedCount ≈ gateRejectedCount + ingestedCount, modulo a transient
 * mismatch when a notification has just committed its raw row but
 * parsed_signals hasn't caught up yet. Don't depend on exact equality.
 */
data class IngestionHealth(
    val totalReceived: Int,
    val parsedCount: Int,
    val failedCount: Int,
    val inFlightCount: Int,
    val gateRejectedCount: Int,
    val ingestedCount: Int,
    val windowDays: Long,
) {
    val successRate: Float =
        if (totalReceived == 0) 0f else ingestedCount.toFloat() / totalReceived

    val failureRate: Float =
        if (totalReceived == 0) 0f else failedCount.toFloat() / totalReceived

    /**
     * Whether the failure count is non-zero — the Debug card paints loud
     * red when this is true. A single FAILED row means an exception fired
     * silently and needs investigation, which is the whole point of T3.
     */
    val hasFailures: Boolean = failedCount > 0
}
