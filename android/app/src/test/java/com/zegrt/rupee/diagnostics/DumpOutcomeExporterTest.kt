package com.zegrt.rupee.diagnostics

import com.zegrt.rupee.data.local.dao.DumpOutcomeSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2a regression set. Two surfaces under test, both JVM-side:
 *
 *  1. `parseRawEventIdsFromDump` — the cheap regex used at share time to pull
 *     raw event ids out of `dumps.jsonl` for the joined snapshot query. Must
 *     skip `Filtered` lines (no rawEventId), preserve order, and de-duplicate.
 *
 *  2. `snapshotJson` — the per-row JSONL emitter for the outcomes file. Pins
 *     the field set and the null-vs-string-encoding contract so replay tooling
 *     can rely on a fixed schema. Substring assertions because we don't ship
 *     a JVM JSON parser into the test harness.
 *
 * In-memory Room coverage of the actual join is deferred — see
 * docs/dump-enrichment-followups.md.
 */
class DumpOutcomeExporterTest {

    // ── parseRawEventIdsFromDump ──────────────────────────────────────────────

    @Test
    fun `parseRawEventIdsFromDump pulls ids from ingested and gate-rejected lines`() {
        val dump = tempFile().apply {
            writeText(
                listOf(
                    """{"pkg":"a","outcome":{"kind":"filtered","reason":"GROUP_SUMMARY"}}""",
                    """{"pkg":"b","outcome":{"kind":"gate_rejected","rawEventId":"raw-1","gateReason":"NOT_TRANSACTIONAL"}}""",
                    """{"pkg":"c","outcome":{"kind":"ingested","rawEventId":"raw-2","parsedSignalId":"sig-2"}}""",
                ).joinToString("\n")
            )
        }

        val ids = NotificationDumper.parseRawEventIdsFromDump(dump)

        assertEquals(listOf("raw-1", "raw-2"), ids)
    }

    @Test
    fun `parseRawEventIdsFromDump deduplicates while preserving first-seen order`() {
        // Defensive — a single raw event shouldn't appear twice in practice
        // (each ingest writes one line), but format drift is cheap to guard.
        val dump = tempFile().apply {
            writeText(
                listOf(
                    """{"outcome":{"kind":"ingested","rawEventId":"raw-b"}}""",
                    """{"outcome":{"kind":"ingested","rawEventId":"raw-a"}}""",
                    """{"outcome":{"kind":"ingested","rawEventId":"raw-b"}}""",
                ).joinToString("\n")
            )
        }

        val ids = NotificationDumper.parseRawEventIdsFromDump(dump)

        assertEquals(listOf("raw-b", "raw-a"), ids)
    }

    @Test
    fun `parseRawEventIdsFromDump returns empty for missing file`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "rupee-test-${System.nanoTime()}.jsonl")
        assertFalse(missing.exists())
        assertEquals(emptyList<String>(), NotificationDumper.parseRawEventIdsFromDump(missing))
    }

    @Test
    fun `parseRawEventIdsFromDump returns empty for filtered-only dump`() {
        val dump = tempFile().apply {
            writeText("""{"outcome":{"kind":"filtered","reason":"EMPTY_BODY"}}""")
        }
        assertEquals(emptyList<String>(), NotificationDumper.parseRawEventIdsFromDump(dump))
    }

    // ── snapshotJson ──────────────────────────────────────────────────────────

    @Test
    fun `snapshotJson emits every field with null encoded as JSON null`() {
        val json = snapshotJsonOf(
            DumpOutcomeSnapshot(
                rawEventId = "raw-1",
                parserKey = null,
                parserVersion = null,
                transactionKind = null,
                candidateId = null,
                candidateDecisionState = null,
                candidateDecisionReason = null,
                inboxItemId = null,
                inboxDecisionState = null,
                inboxResolvedAt = null,
                inboxLinkedCanonicalTxnId = null,
                canonicalTxnId = null,
                canonicalStatus = null,
                canonicalType = null,
                canonicalAmountMinor = null,
                canonicalMerchantName = null,
                canonicalCategoryId = null,
                canonicalNotes = null,
                mergedIntoExistingTxnId = null,
            )
        )

        assertContains(json, "\"rawEventId\":\"raw-1\"")
        // Nulls stay as JSON null — not omitted, not "". A filtered/gate-rejected
        // raw event still produces a row; downstream code should see the
        // shape and decide what to display.
        assertContains(json, "\"parserKey\":null")
        assertContains(json, "\"canonicalAmountMinor\":null")
        assertContains(json, "\"mergedIntoExistingTxnId\":null")
    }

    @Test
    fun `snapshotJson encodes a confirmed-fresh outcome with full pointers`() {
        val json = snapshotJsonOf(
            DumpOutcomeSnapshot(
                rawEventId = "raw-9",
                parserKey = "notification_kotak",
                parserVersion = "v2",
                transactionKind = "SPEND",
                candidateId = "cand-9",
                candidateDecisionState = "USER_CONFIRMED",
                candidateDecisionReason = "MEDIUM_CONFIDENCE_REVIEW",
                inboxItemId = "inbox-9",
                inboxDecisionState = "CONFIRMED",
                inboxResolvedAt = "2026-05-15T11:30:00Z",
                inboxLinkedCanonicalTxnId = "txn-cand-9",
                canonicalTxnId = "txn-cand-9",
                canonicalStatus = "CONFIRMED",
                canonicalType = "EXPENSE",
                canonicalAmountMinor = 24500L,
                canonicalMerchantName = "Swiggy",
                canonicalCategoryId = "cat-food",
                canonicalNotes = null,
                mergedIntoExistingTxnId = null, // confirm-fresh, not merge
            )
        )

        assertContains(json, "\"parserKey\":\"notification_kotak\"")
        assertContains(json, "\"candidateDecisionState\":\"USER_CONFIRMED\"")
        assertContains(json, "\"inboxDecisionState\":\"CONFIRMED\"")
        assertContains(json, "\"canonicalStatus\":\"CONFIRMED\"")
        assertContains(json, "\"canonicalType\":\"EXPENSE\"")
        // amount must be bare numeric, not quoted.
        assertContains(json, "\"canonicalAmountMinor\":24500")
        assertContains(json, "\"canonicalMerchantName\":\"Swiggy\"")
        // Fresh-confirm leaves mergedIntoExistingTxnId null — the snapshot's
        // way of distinguishing "user accepted a new row" from "user merged
        // into an existing row".
        assertContains(json, "\"mergedIntoExistingTxnId\":null")
    }

    @Test
    fun `snapshotJson surfaces mergedIntoExistingTxnId when inbox linked to pre-existing row`() {
        val json = snapshotJsonOf(
            DumpOutcomeSnapshot(
                rawEventId = "raw-10",
                parserKey = "notification_gpay",
                parserVersion = "v1",
                transactionKind = "SPEND",
                candidateId = "cand-10",
                candidateDecisionState = "USER_CONFIRMED",
                candidateDecisionReason = "MEDIUM_CONFIDENCE_REVIEW",
                inboxItemId = "inbox-10",
                inboxDecisionState = "CONFIRMED",
                inboxResolvedAt = "2026-05-15T11:35:00Z",
                inboxLinkedCanonicalTxnId = "txn-pre-existing-42",
                canonicalTxnId = "txn-pre-existing-42",
                canonicalStatus = "CONFIRMED",
                canonicalType = "EXPENSE",
                canonicalAmountMinor = 50000L,
                canonicalMerchantName = "Swiggy",
                canonicalCategoryId = "cat-food",
                canonicalNotes = "merged from notification",
                mergedIntoExistingTxnId = "txn-pre-existing-42",
            )
        )

        assertContains(json, "\"mergedIntoExistingTxnId\":\"txn-pre-existing-42\"")
        assertContains(json, "\"canonicalNotes\":\"merged from notification\"")
    }

    @Test
    fun `snapshotJson encodes a filtered-or-gate-rejected row with only rawEventId populated`() {
        // A rawEventId that produced no parsed signal still appears in the
        // dump (gate-rejected path), so the snapshot writer must be happy
        // emitting "rawEventId only, everything else null".
        val json = snapshotJsonOf(
            DumpOutcomeSnapshot(
                rawEventId = "raw-gate",
                parserKey = null,
                parserVersion = null,
                transactionKind = null,
                candidateId = null,
                candidateDecisionState = null,
                candidateDecisionReason = null,
                inboxItemId = null,
                inboxDecisionState = null,
                inboxResolvedAt = null,
                inboxLinkedCanonicalTxnId = null,
                canonicalTxnId = null,
                canonicalStatus = null,
                canonicalType = null,
                canonicalAmountMinor = null,
                canonicalMerchantName = null,
                canonicalCategoryId = null,
                canonicalNotes = null,
                mergedIntoExistingTxnId = null,
            )
        )

        assertContains(json, "\"rawEventId\":\"raw-gate\"")
        assertContains(json, "\"candidateId\":null")
        assertContains(json, "\"canonicalTxnId\":null")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun tempFile(): File =
        File.createTempFile("rupee-dump-test", ".jsonl").apply { deleteOnExit() }

    private fun snapshotJsonOf(snapshot: DumpOutcomeSnapshot): String =
        NotificationDumper::class.java
            .getDeclaredMethod("snapshotJson", DumpOutcomeSnapshot::class.java)
            .apply { isAccessible = true }
            .invoke(NotificationDumper, snapshot) as String

    private fun assertContains(haystack: String, needle: String) {
        assertTrue(
            "expected JSON to contain: $needle\nactual: $haystack",
            haystack.contains(needle),
        )
    }
}
