package com.zegrt.rupee.data.local.dao

import com.zegrt.rupee.data.local.BaseDatabaseTest
import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.InboxReasonCode
import com.zegrt.rupee.data.local.entity.ParsedSignalEntity
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * End-to-end coverage for [DumpOutcomeDao.getDumpOutcomeSnapshot] — the
 * joined query that backs Phase 2a of the notification-dump export.
 *
 * Three shapes covered:
 *  - Confirm-fresh inbox row → snapshot has candidate + inbox + canonical,
 *    `mergedIntoExistingTxnId` is null
 *  - Merge-into-existing inbox row → `mergedIntoExistingTxnId` matches the
 *    pre-existing canonical id (this is the v0.14.0 M1 column, replacing the
 *    magic-string CASE the original dao used)
 *  - Bare candidate (no inbox, no canonical) → snapshot has the candidate
 *    fields and nulls everywhere else, no NPE
 */
@RunWith(AndroidJUnit4::class)
class DumpOutcomeDaoTest : BaseDatabaseTest() {

    private val nowIso = "2026-05-19T10:00:00Z"

    @Test
    fun snapshot_confirmFreshInboxRow_populatesAllFields() = runBlocking {
        // Seed the full chain: raw → parsed → candidate → inbox + canonical.
        seedRawEvent(id = "raw-1")
        seedParsedSignal(id = "sig-1", rawId = "raw-1")
        seedCandidate(id = "cand-1", signalId = "sig-1", linkedCanonicalId = "txn-cand-1")
        seedInbox(
            id = "inbox-1",
            candidateId = "cand-1",
            linkedCanonicalId = "txn-cand-1",
            mergedFromExisting = null, // confirm-fresh
        )
        seedCanonical(id = "txn-cand-1", merchant = "Swiggy", amount = 24500L)

        val snapshots = database.dumpOutcomeDao()
            .getDumpOutcomeSnapshot(listOf("raw-1"))

        assertEquals(1, snapshots.size)
        val s = snapshots[0]
        assertEquals("cand-1", s.candidateId)
        assertEquals("inbox-1", s.inboxItemId)
        assertEquals("txn-cand-1", s.canonicalTxnId)
        assertEquals("Swiggy", s.canonicalMerchantName)
        assertEquals(24500L, s.canonicalAmountMinor)
        // The whole point of M1: confirm-fresh leaves mergedIntoExistingTxnId
        // null. v0.14.0's CASE-expression form would have computed this from
        // the synthetic id naming convention; v0.14.0 onward reads the
        // explicit inbox_items.mergedFromExistingCanonicalId column.
        assertNull(s.mergedIntoExistingTxnId)
    }

    @Test
    fun snapshot_mergeIntoExistingRow_setsMergedFromColumn() = runBlocking {
        // Seed: same chain, but the inbox row was merged into a pre-existing
        // canonical txn (not the "txn-<candidateId>" synthetic id).
        seedRawEvent(id = "raw-2")
        seedParsedSignal(id = "sig-2", rawId = "raw-2")
        seedCandidate(id = "cand-2", signalId = "sig-2", linkedCanonicalId = "preexisting-42")
        seedInbox(
            id = "inbox-2",
            candidateId = "cand-2",
            linkedCanonicalId = "preexisting-42",
            mergedFromExisting = "preexisting-42",
        )
        seedCanonical(id = "preexisting-42", merchant = "Coffee shop (user-edited)", amount = 50000L)

        val s = database.dumpOutcomeDao()
            .getDumpOutcomeSnapshot(listOf("raw-2")).single()

        assertEquals("preexisting-42", s.canonicalTxnId)
        assertEquals("preexisting-42", s.mergedIntoExistingTxnId)
        assertEquals("Coffee shop (user-edited)", s.canonicalMerchantName)
    }

    @Test
    fun snapshot_bareCandidate_returnsNullDownstreamFields() = runBlocking {
        // No inbox row, no canonical row — a parsed_signal + candidate pair
        // that the decision engine routed to IGNORED. Snapshot still returns
        // a row (the LEFT JOINs cover this) with the candidate fields set
        // and everything downstream null.
        seedRawEvent(id = "raw-3")
        seedParsedSignal(id = "sig-3", rawId = "raw-3")
        seedCandidate(id = "cand-3", signalId = "sig-3", linkedCanonicalId = null)

        val s = database.dumpOutcomeDao()
            .getDumpOutcomeSnapshot(listOf("raw-3")).single()

        assertEquals("cand-3", s.candidateId)
        assertNull(s.inboxItemId)
        assertNull(s.canonicalTxnId)
        assertNull(s.mergedIntoExistingTxnId)
    }

    // ── fixture helpers ──────────────────────────────────────────────────────

    private suspend fun seedRawEvent(id: String) {
        database.rawCaptureEventDao().insertRawCaptureEvent(
            RawCaptureEventEntity(
                id = id, userId = "local-user",
                sourceType = RawCaptureSourceType.NOTIFICATION,
                sourceAppPackage = "com.example.test",
                title = null, body = "test body",
                receivedAt = nowIso, deviceEventTime = nowIso,
                hashFingerprint = "fp-$id",
                ingestionStatus = RawCaptureIngestionStatus.PARSED,
                createdAt = nowIso, updatedAt = nowIso,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )
        )
    }

    private suspend fun seedParsedSignal(id: String, rawId: String) {
        database.parsedSignalDao().upsertParsedSignal(
            ParsedSignalEntity(
                id = id, userId = "local-user",
                rawCaptureEventId = rawId,
                parserKey = "notification_test", parserVersion = "v1",
                providerHint = "test",
                transactionKind = ParsedTransactionKind.SPEND,
                amountMinor = 24500, currencyCode = "INR",
                merchantRaw = "Swiggy",
                parseConfidence = 0.78,
                createdAt = nowIso, updatedAt = nowIso,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )
        )
    }

    private suspend fun seedCandidate(
        id: String,
        signalId: String,
        linkedCanonicalId: String?,
    ) {
        database.transactionCandidateDao().upsertTransactionCandidate(
            TransactionCandidateEntity(
                id = id, userId = "local-user",
                parsedSignalId = signalId,
                candidateType = TransactionCandidateType.SPEND,
                amountMinor = 24500, currencyCode = "INR",
                linkedCanonicalTransactionId = linkedCanonicalId,
                decisionState = CandidateDecisionState.INBOX_PENDING,
                decisionReason = CandidateDecisionReason.MEDIUM_CONFIDENCE_REVIEW,
                normalizationVersion = "v1",
                createdAt = nowIso, updatedAt = nowIso,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )
        )
    }

    private suspend fun seedInbox(
        id: String,
        candidateId: String,
        linkedCanonicalId: String?,
        mergedFromExisting: String?,
    ) {
        database.inboxItemDao().upsertInboxItem(
            InboxItemEntity(
                id = id, userId = "local-user",
                transactionCandidateId = candidateId,
                reasonCode = InboxReasonCode.MEDIUM_CONFIDENCE,
                decisionState = InboxDecisionState.CONFIRMED,
                linkedCanonicalTransactionId = linkedCanonicalId,
                mergedFromExistingCanonicalId = mergedFromExisting,
                createdAt = nowIso, resolvedAt = nowIso,
                updatedAt = nowIso,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )
        )
    }

    private suspend fun seedCanonical(id: String, merchant: String, amount: Long) {
        database.canonicalTransactionDao().upsertTransactions(
            listOf(
                CanonicalTransactionEntity(
                    id = id, userId = "local-user",
                    type = CanonicalTransactionType.EXPENSE,
                    status = CanonicalTransactionStatus.CONFIRMED,
                    amountMinor = amount, currencyCode = "INR",
                    merchantName = merchant,
                    occurredAt = nowIso,
                    createdBy = "test",
                    createdAt = nowIso, updatedAt = nowIso,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                )
            )
        )
    }
}
