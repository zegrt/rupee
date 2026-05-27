package com.zegrt.rupee.recurring

import android.util.Log
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.ingestion.MerchantNameUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class DetectedPattern(
    val merchantPattern: String,
    val expectedAmountMinor: Long,
    val intervalDays: Int,
    val occurrenceCount: Int,
    val lastSeenAt: String,
    val nextExpectedAt: String,
)

/**
 * Cheap, deterministic recurring-spend detector. Looks at the last 120 days of confirmed
 * (non-IGNORED, non-SUGGESTED) expenses, groups by cleaned merchant name, and reports
 * groups that look monthly-ish: ≥ 3 occurrences, median spacing in [20, 35] days,
 * amounts within ±20% of the median.
 *
 * Suggestions are written with isConfirmed = false; the user confirms or dismisses.
 */
class RecurringDetectionEngine(
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun detect(
        transactions: List<CanonicalTransactionEntity>,
        today: LocalDate = LocalDate.now(zone),
    ): List<DetectedPattern> {
        val horizon = today.minusDays(LOOKBACK_DAYS.toLong())
        val candidates = transactions
            .asSequence()
            // Pattern detection should only reinforce on transactions the user has
            // confirmed (directly, or via a trust rule that wrote CONFIRMED at ingest).
            // Including SUGGESTED would let unconfirmed auto-captures feed themselves
            // into recurring suggestions and risk a self-reinforcing loop.
            .filter { it.status == CanonicalTransactionStatus.CONFIRMED }
            .filter { it.merchantName != null }
            .mapNotNull { txn ->
                val date = parseDate(txn.occurredAt) ?: return@mapNotNull null
                if (date.isBefore(horizon)) return@mapNotNull null
                val cleaned = MerchantNameUtils.clean(txn.merchantName).takeIf { it != "Unnamed" }
                    ?: return@mapNotNull null
                Triple(cleaned, date, txn.amountMinor)
            }
            .toList()

        return candidates
            .groupBy { it.first.lowercase() }
            .mapNotNull { (_, hits) ->
                val sorted = hits.sortedBy { it.second }
                if (sorted.size < MIN_OCCURRENCES) return@mapNotNull null

                val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.second, b.second).toInt() }
                val medianGap = median(gaps) ?: return@mapNotNull null
                if (medianGap !in MIN_INTERVAL_DAYS..MAX_INTERVAL_DAYS) return@mapNotNull null

                val medianAmount = median(sorted.map { it.third }) ?: return@mapNotNull null
                val amountSpreadOk = sorted.all { (_, _, amount) ->
                    val deviation = abs(amount - medianAmount).toDouble() / medianAmount.toDouble()
                    deviation <= MAX_AMOUNT_DRIFT
                }
                if (!amountSpreadOk) return@mapNotNull null

                val last = sorted.last()
                val merchantPretty = sorted.first().first
                val nextExpected = last.second.plusDays(medianGap.toLong())
                DetectedPattern(
                    merchantPattern = merchantPretty,
                    expectedAmountMinor = medianAmount,
                    intervalDays = medianGap,
                    occurrenceCount = sorted.size,
                    lastSeenAt = last.second.toString(),
                    nextExpectedAt = nextExpected.toString(),
                )
            }
    }

    private fun parseDate(iso: String): LocalDate? = try {
        Instant.parse(iso).atZone(zone).toLocalDate()
    } catch (_: Exception) {
        // DATE-PARSE-LOGGING (Sprint 4). If we fall through here AND the
        // shorter LocalDate parse also fails, the row drops from recurring
        // detection silently. Surface to logcat so a tester who's missing
        // a recurring subscription has something to bisect against.
        runCatching { LocalDate.parse(iso.take(10)) }
            .onFailure { t -> Log.w("Rupee", "RecurringDetectionEngine.parseDate failed for iso=$iso", t) }
            .getOrNull()
    }

    private fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    @JvmName("medianLong")
    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private companion object {
        const val LOOKBACK_DAYS = 120
        const val MIN_OCCURRENCES = 3
        const val MIN_INTERVAL_DAYS = 20
        const val MAX_INTERVAL_DAYS = 35
        const val MAX_AMOUNT_DRIFT = 0.20
    }
}
