package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DedupeFingerprintTest {

    private val sameParse = parse(amount = 12500, merchant = "Swiggy", mode = Mode.UPI)

    @Test
    fun `identical events within the same 5-min bucket produce the same fingerprint`() {
        val a = DedupeFingerprint.compute(event("2026-05-09T10:31:30Z"), sameParse).first
        val b = DedupeFingerprint.compute(event("2026-05-09T10:34:59Z"), sameParse).first
        assertEquals(a, b)
    }

    @Test
    fun `events in adjacent buckets produce different current fingerprints`() {
        val a = DedupeFingerprint.compute(event("2026-05-09T10:34:59Z"), sameParse).first
        val b = DedupeFingerprint.compute(event("2026-05-09T10:35:00Z"), sameParse).first
        assertNotEquals(a, b)
    }

    @Test
    fun `boundary catch — previous-bucket fallback reaches across the 5-min boundary`() {
        // Event A lands at 10:34 (bucket 10:30-10:35). Event B lands at 10:35 (bucket
        // 10:35-10:40). Their current fingerprints differ — but B's PREVIOUS bucket
        // fingerprint should equal A's current.
        val a = DedupeFingerprint.compute(event("2026-05-09T10:34:30Z"), sameParse).first
        val b = DedupeFingerprint.compute(event("2026-05-09T10:35:30Z"), sameParse)
        assertEquals(a, b.second)
        assertNotEquals(a, b.first)
    }

    @Test
    fun `different amounts produce different fingerprints`() {
        val a = DedupeFingerprint.compute(event("2026-05-09T10:31:30Z"), sameParse).first
        val b = DedupeFingerprint.compute(
            event("2026-05-09T10:31:30Z"),
            parse(amount = 12600, merchant = "Swiggy", mode = Mode.UPI),
        ).first
        assertNotEquals(a, b)
    }

    @Test
    fun `merchant whitespace and punctuation are normalized away`() {
        val a = DedupeFingerprint.compute(
            event("2026-05-09T10:31:30Z"),
            parse(amount = 12500, merchant = "Swiggy", mode = Mode.UPI),
        ).first
        val b = DedupeFingerprint.compute(
            event("2026-05-09T10:31:30Z"),
            parse(amount = 12500, merchant = "  swiggy  ", mode = Mode.UPI),
        ).first
        assertEquals(a, b)
    }

    @Test
    fun `different mode produces different fingerprint`() {
        val a = DedupeFingerprint.compute(
            event("2026-05-09T10:31:30Z"),
            parse(amount = 12500, merchant = "Swiggy", mode = Mode.UPI),
        ).first
        val b = DedupeFingerprint.compute(
            event("2026-05-09T10:31:30Z"),
            parse(amount = 12500, merchant = "Swiggy", mode = Mode.CREDIT_CARD),
        ).first
        assertNotEquals(a, b)
    }

    @Test
    fun `null device time falls back to receivedAt`() {
        val a = DedupeFingerprint.compute(
            event(receivedAt = "2026-05-09T10:31:30Z", deviceEventTime = null),
            sameParse,
        ).first
        val b = DedupeFingerprint.compute(
            event(receivedAt = "2026-05-09T10:31:30Z"),
            sameParse,
        ).first
        assertEquals(a, b)
    }

    private fun event(
        receivedAt: String = "2026-05-09T00:00:00Z",
        deviceEventTime: String? = receivedAt,
    ): RawCaptureEventEntity = RawCaptureEventEntity(
        id = "test",
        userId = "user-1",
        sourceType = RawCaptureSourceType.NOTIFICATION,
        sourceAppPackage = "com.example",
        title = null,
        body = "test",
        receivedAt = receivedAt,
        deviceEventTime = deviceEventTime,
        hashFingerprint = "test",
        ingestionStatus = RawCaptureIngestionStatus.CAPTURED,
        createdAt = receivedAt,
        updatedAt = receivedAt,
        syncStatus = SyncStatus.LOCAL_ONLY,
    )

    private fun parse(amount: Long?, merchant: String?, mode: Mode?) = NotificationParseResult(
        parserKey = "test",
        parserVersion = "v1",
        transactionKind = ParsedTransactionKind.SPEND,
        candidateType = TransactionCandidateType.SPEND,
        amountMinor = amount,
        currencyCode = if (amount != null) "INR" else null,
        merchantRaw = merchant,
        toEntityName = merchant,
        mode = mode,
        parseConfidence = 0.9,
    )
}
