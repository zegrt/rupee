package com.zegrt.rupee.recurring

import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.SyncStatus
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringDetectionEngineTest {

    private val today = LocalDate.of(2026, 5, 9)
    private val engine = RecurringDetectionEngine(zone = ZoneId.of("UTC"))

    @Test
    fun `detects a clean monthly subscription`() {
        val txns = listOf(
            txn("Netflix", "2026-02-05", 64900),
            txn("Netflix", "2026-03-05", 64900),
            txn("Netflix", "2026-04-04", 64900),
        )
        val patterns = engine.detect(txns, today)
        assertEquals(1, patterns.size)
        val p = patterns.single()
        assertEquals("Netflix", p.merchantPattern)
        assertEquals(64900L, p.expectedAmountMinor)
        assertTrue(p.intervalDays in 28..31)
        assertEquals(3, p.occurrenceCount)
    }

    @Test
    fun `ignores groups with fewer than 3 occurrences`() {
        val txns = listOf(
            txn("Netflix", "2026-03-05", 64900),
            txn("Netflix", "2026-04-04", 64900),
        )
        assertEquals(0, engine.detect(txns, today).size)
    }

    @Test
    fun `rejects when amount drift is too large`() {
        // 10000, 30000, 12000 — median 12000, max drift 18000/12000 = 150% > 20%
        val txns = listOf(
            txn("Variable Vendor", "2026-02-05", 10000),
            txn("Variable Vendor", "2026-03-05", 30000),
            txn("Variable Vendor", "2026-04-04", 12000),
        )
        assertEquals(0, engine.detect(txns, today).size)
    }

    @Test
    fun `rejects spacings outside the monthly band`() {
        // ~weekly: 7-day gaps
        val weekly = listOf(
            txn("Coffee", "2026-04-15", 25000),
            txn("Coffee", "2026-04-22", 25000),
            txn("Coffee", "2026-04-29", 25000),
            txn("Coffee", "2026-05-06", 25000),
        )
        assertEquals(0, engine.detect(weekly, today).size)
    }

    @Test
    fun `cleans merchant name before grouping`() {
        // Both bodies clean to "Swiggy" and should group
        val txns = listOf(
            txn("Swiggy using UPI", "2026-02-05", 50000),
            txn("Swiggy", "2026-03-05", 50000),
            txn("HDFC Credit Card xx1234 at Swiggy on 04 Apr", "2026-04-04", 50000),
        )
        val patterns = engine.detect(txns, today)
        assertEquals(1, patterns.size)
        assertEquals("Swiggy", patterns.single().merchantPattern)
    }

    @Test
    fun `excludes IGNORED status`() {
        val txns = listOf(
            txn("Netflix", "2026-02-05", 64900),
            txn("Netflix", "2026-03-05", 64900),
            txn("Netflix", "2026-04-04", 64900, status = CanonicalTransactionStatus.IGNORED),
        )
        // Only 2 valid → not enough.
        assertEquals(0, engine.detect(txns, today).size)
    }

    @Test
    fun `nextExpectedAt is lastSeen plus median gap`() {
        val txns = listOf(
            txn("Spotify", "2026-02-10", 11900),
            txn("Spotify", "2026-03-10", 11900),
            txn("Spotify", "2026-04-09", 11900),
        )
        val p = engine.detect(txns, today).single()
        assertEquals(LocalDate.parse("2026-04-09").plusDays(p.intervalDays.toLong()).toString(), p.nextExpectedAt)
    }

    private fun txn(
        merchant: String,
        dateIso: String,
        amountMinor: Long,
        status: CanonicalTransactionStatus = CanonicalTransactionStatus.CONFIRMED,
    ) = CanonicalTransactionEntity(
        id = "txn-$merchant-$dateIso",
        userId = "user-1",
        type = CanonicalTransactionType.EXPENSE,
        status = status,
        amountMinor = amountMinor,
        currencyCode = "INR",
        merchantName = merchant,
        categoryId = null,
        mode = null,
        occurredAt = "${dateIso}T12:00:00Z",
        sourceSummary = null,
        createdBy = "test",
        confidenceTier = null,
        dedupeFingerprint = null,
        createdAt = "${dateIso}T12:00:00Z",
        updatedAt = "${dateIso}T12:00:00Z",
        syncStatus = SyncStatus.LOCAL_ONLY,
    )
}
