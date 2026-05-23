package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.BaseDatabaseTest
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Instrumented regression coverage for the v0.14.0 H4 ingestion bug.
 *
 * The bug: H4 added a CASCADE foreign key from `inbox_items.transactionCandidateId`
 * to `transaction_candidates.id`. `NotificationSignalNormalizer.normalizeLocked`
 * was writing the inbox row BEFORE the candidate row. Every INBOX_PENDING-
 * bound notification hit `SQLiteConstraintException` and got swallowed by
 * the listener's `runCatching` as `INGEST_FAILED`. The bug lived in
 * production for a full day before being caught by a user dump.
 *
 * v0.14.1 reversed the write order. This test class makes sure that fix
 * stays fixed — every MEDIUM-confidence notification routes through inbox
 * creation, and the candidate row + inbox row + back-pointer must all be
 * coherent at the end of `ingestNotification`.
 *
 * Lives under androidTest because the bug was Android-SQLite-specific
 * (Robolectric's SQLite shim doesn't enforce FKs identically). Run via
 * `./gradlew :android:app:connectedDebugAndroidTest` against an emulator
 * or attached device.
 */
@RunWith(AndroidJUnit4::class)
class IngestionPipelineTest : BaseDatabaseTest() {

    private fun normalizer() = NotificationSignalNormalizer(database)

    @Test
    fun ingestNotification_inboxPendingPath_writesBothInboxAndCandidate() = runBlocking {
        // A Kotak debit. Kotak parsers structurally emit `merchantRaw = null`
        // (the payee never appears in the bank push — only in the Kotak app),
        // so the EvidenceTally scores amount=3 + digits=1 = 4 points = 0.78
        // (MEDIUM tier) → INBOX_PENDING. This is the path that exercises the
        // candidate-then-inbox-then-candidate-backfill write sequence — the
        // path the v0.14.0 H4 FK regression broke and the v0.14.1 hotfix
        // ostensibly fixed.
        //
        // Previously this test used a CRED card spend body, but Sprint 2's
        // S1.3 (rest) parser migration promoted CRED with amount+merchant+
        // digits to HIGH tier, so it no longer routes through INBOX_PENDING
        // at all. Kotak's structural lack of a merchant keeps it in MEDIUM.
        // ingestNotification expects the already-combined body (title +
        // bigText + textLines), which the production NotificationListenerService
        // assembles via NotificationExtractor before calling. Concat here so
        // amount + verb both reach the parser.
        val outcome = normalizer().ingestNotification(
            userId = "local-user",
            packageName = "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
            title = "₹1,593.77 received via UPI",
            body = "₹1,593.77 received via UPI\nAmount credited to XX4129. Check out details.",
            postedAtMillis = System.currentTimeMillis(),
        )

        assertTrue(
            "Expected Ingested outcome but got $outcome — likely the H4 FK regression returned",
            outcome is IngestionResult.Ingested,
        )
        val ingested = outcome as IngestionResult.Ingested
        assertEquals(CandidateDecisionState.INBOX_PENDING, ingested.decisionState)
        assertNotNull(ingested.inboxItemId)

        // Both rows must exist at the DB level.
        val candidate = database.transactionCandidateDao()
            .getTransactionCandidateById(ingested.candidateId)
        assertNotNull("candidate row missing post-ingest", candidate)
        // Back-pointer wired: candidate.linkedInboxItemId points at the inbox row.
        assertEquals(ingested.inboxItemId, candidate!!.linkedInboxItemId)

        // This is THE assertion the cascade-delete bug breaks. Pre-fix, the
        // second candidate upsert (to backfill linkedInboxItemId) used Room's
        // @Insert(onConflict = REPLACE), which generates INSERT OR REPLACE.
        // SQLite REPLACE deletes the conflicting candidate row first; because
        // inbox_items.transactionCandidateId has onDelete = CASCADE, the
        // inbox row we just wrote gets cascade-deleted. The candidate is
        // re-inserted, but the inbox row is gone forever. The user sees an
        // empty Inbox even though `normalizeLocked` returned Ingested with
        // an inboxItemId.
        //
        // Reference: https://dexterslog.com/posts/insert-on-conflict-replace-with-on-delete-cascade-in-sqlite/
        val inbox = database.inboxItemDao()
            .observeInboxItems(userId = "local-user", state = InboxDecisionState.PENDING)
            .first()
        assertEquals(
            "inbox_items row survived candidate-backfill — cascade-delete bug fixed",
            1,
            inbox.size,
        )
        assertEquals(ingested.inboxItemId, inbox[0].id)
        assertEquals(ingested.candidateId, inbox[0].transactionCandidateId)
    }

    @Test
    fun ingestNotification_autoCreatedPath_writesCanonicalNotInbox() = runBlocking {
        // Auto-created path doesn't go through inbox. This test exists as a
        // counter-sanity to make sure the v0.14.1 reorder didn't accidentally
        // create inbox rows for AUTO_CREATED candidates.
        //
        // Need a merchant trust rule first so the candidate routes to
        // AUTO_CREATED via the trust-rule branch. Without that, this body
        // would land at MEDIUM and go to inbox.
        val now = java.time.Instant.now().toString()
        database.merchantTrustRuleDao().upsertRule(
            com.zegrt.rupee.data.local.entity.MerchantTrustRuleEntity(
                id = "rule-1",
                userId = "local-user",
                merchantPattern = "Zomato",
                autoCategoryId = null,
                createdAt = now,
                updatedAt = now,
                syncStatus = com.zegrt.rupee.data.local.entity.SyncStatus.LOCAL_ONLY,
            )
        )

        val outcome = normalizer().ingestNotification(
            userId = "local-user",
            packageName = "com.dreamplug.androidapp",
            title = "Spent on HDFC card",
            body = "₹1,499 spent on HDFC Credit Card xx1234 at Zomato via CRED on 06 May",
            postedAtMillis = System.currentTimeMillis(),
        )

        assertTrue(outcome is IngestionResult.Ingested)
        val ingested = outcome as IngestionResult.Ingested
        assertEquals(CandidateDecisionState.AUTO_CREATED, ingested.decisionState)
        assertNotNull(ingested.canonicalTransactionId)
        assertEquals(
            "AUTO_CREATED path should not create an inbox row",
            null,
            ingested.inboxItemId,
        )

        // Defensive: candidate exists, no inbox rows, one canonical txn.
        val candidate = database.transactionCandidateDao()
            .getTransactionCandidateById(ingested.candidateId)
        assertNotNull(candidate)
        assertEquals(null, candidate!!.linkedInboxItemId)

        val pendingInbox = database.inboxItemDao()
            .observeInboxItems(userId = "local-user", state = InboxDecisionState.PENDING)
            .first()
        assertTrue(
            "AUTO_CREATED path produced an unexpected inbox row: $pendingInbox",
            pendingInbox.isEmpty(),
        )
    }

    @Test
    fun ingestNotification_dedupePath_doesNotCrashOnSecondMirror() = runBlocking {
        // Companion regression: M2 fix (split the dedupe DAO so the lookup
        // includes IGNORED rows) — confirm two identical mirrors don't both
        // crash on the FK path and don't both write distinct candidates.
        val body = "₹14.00 sent from XX4129\nLow balance! Add funds for seamless payment"
        val pkg = "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge"
        val firstTime = System.currentTimeMillis()
        val secondTime = firstTime + 1500 // within the 5-min bucket

        val first = normalizer().ingestNotification(
            userId = "local-user", packageName = pkg, title = "₹14.00 sent from XX4129",
            body = body, postedAtMillis = firstTime,
        )
        val second = normalizer().ingestNotification(
            userId = "local-user", packageName = pkg, title = "₹14.00 sent from XX4129",
            body = body, postedAtMillis = secondTime,
        )

        // Neither call should have hit INGEST_FAILED.
        assertFalse("first ingest hit INGEST_FAILED — likely H4 regression", first.isIngestFailed())
        assertFalse("second ingest hit INGEST_FAILED — likely H4 regression", second.isIngestFailed())
    }

    private fun IngestionResult.isIngestFailed(): Boolean =
        this is IngestionResult.Filtered &&
            this.reason == IngestionResult.FilterReason.INGEST_FAILED
}
