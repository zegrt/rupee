package com.zegrt.rupee.diagnostics

import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.TransactionCandidateType
import com.zegrt.rupee.ingestion.IngestionResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the dump-line JSON shape. We pin (a) each [IngestionResult]
 * variant maps to the right `kind` discriminator, (b) Ingested outcomes carry
 * the parser key + decision + every id pointer downstream tooling needs, and
 * (c) null-valued fields appear as JSON `null` (not omitted) so the schema is
 * rigid. JSON is asserted as substrings since `org.json.JSONObject` is an
 * Android stub at JVM-test time.
 */
class NotificationDumperJsonTest {

    @Test
    fun `filtered outcome maps to kind=filtered with reason and no rawEventId`() {
        val json = outcomeJsonOf(
            IngestionResult.Filtered(IngestionResult.FilterReason.GROUP_SUMMARY)
        )
        assertContains(json, "\"kind\":\"filtered\"")
        assertContains(json, "\"reason\":\"GROUP_SUMMARY\"")
        // Filtered outcomes have no rawEventId — listener rejected before any DB writes.
        assertFalse("filtered outcome should not carry rawEventId", json.contains("\"rawEventId\""))
    }

    @Test
    fun `gate-rejected outcome carries kind, raw event id, reason, matched`() {
        val json = outcomeJsonOf(
            IngestionResult.GateRejected(
                rawEventId = "raw-1",
                gateReason = "NOT_TRANSACTIONAL",
                matched = "T&C",
            )
        )
        assertContains(json, "\"kind\":\"gate_rejected\"")
        assertContains(json, "\"rawEventId\":\"raw-1\"")
        assertContains(json, "\"gateReason\":\"NOT_TRANSACTIONAL\"")
        assertContains(json, "\"matched\":\"T&C\"")
    }

    @Test
    fun `ingested outcome carries parser, decision, and id pointers`() {
        val json = outcomeJsonOf(
            IngestionResult.Ingested(
                rawEventId = "raw-9",
                parsedSignalId = "sig-9",
                candidateId = "cand-9",
                parserKey = "notification_kotak",
                parserVersion = "v2",
                providerHint = "kotak",
                transactionKind = ParsedTransactionKind.SPEND,
                candidateType = TransactionCandidateType.SPEND,
                amountMinor = 12900,
                currencyCode = "INR",
                merchantRaw = null,
                toEntityName = null,
                mode = Mode.UPI,
                maskedDigits = "4129",
                parseConfidence = 0.75,
                networkReferenceId = null,
                networkReferenceType = null,
                dueDateIso = null,
                confidenceTier = ConfidenceTier.MEDIUM,
                decisionState = CandidateDecisionState.INBOX_PENDING,
                decisionReason = CandidateDecisionReason.MEDIUM_CONFIDENCE_REVIEW,
                trustRuleMatched = false,
                dedupedAgainstCandidateId = null,
                dedupedAgainstCanonicalTxnId = null,
                inboxItemId = "inbox-9",
                canonicalTransactionId = null,
                billDueAppliedToCardId = null,
            )
        )
        assertContains(json, "\"kind\":\"ingested\"")
        assertContains(json, "\"rawEventId\":\"raw-9\"")
        assertContains(json, "\"parserKey\":\"notification_kotak\"")
        assertContains(json, "\"transactionKind\":\"SPEND\"")
        assertContains(json, "\"decisionState\":\"INBOX_PENDING\"")
        assertContains(json, "\"amountMinor\":12900")
        assertContains(json, "\"parseConfidence\":0.75")
        assertContains(json, "\"maskedDigits\":\"4129\"")
        assertContains(json, "\"inboxItemId\":\"inbox-9\"")
        // Nullable fields stay null (not omitted) so jq consumers don't have to
        // special-case missing keys.
        assertContains(json, "\"merchantRaw\":null")
        assertContains(json, "\"canonicalTransactionId\":null")
        assertContains(json, "\"billDueAppliedToCardId\":null")
    }

    @Test
    fun `ingested outcome surfaces trust-rule match flag and canonical txn id`() {
        val json = outcomeJsonOf(
            baseIngested().copy(
                trustRuleMatched = true,
                confidenceTier = ConfidenceTier.HIGH,
                decisionState = CandidateDecisionState.AUTO_CREATED,
                decisionReason = CandidateDecisionReason.MERCHANT_TRUSTED,
                canonicalTransactionId = "txn-7",
            )
        )
        assertContains(json, "\"trustRuleMatched\":true")
        assertContains(json, "\"decisionReason\":\"MERCHANT_TRUSTED\"")
        assertContains(json, "\"canonicalTransactionId\":\"txn-7\"")
    }

    @Test
    fun `ingested outcome surfaces dedupe targets when this is a duplicate`() {
        val json = outcomeJsonOf(
            baseIngested().copy(
                dedupedAgainstCandidateId = "prior-cand-3",
                dedupedAgainstCanonicalTxnId = "prior-txn-3",
                decisionState = CandidateDecisionState.IGNORED,
                decisionReason = CandidateDecisionReason.DUPLICATE_IGNORED,
            )
        )
        assertContains(json, "\"dedupedAgainstCandidateId\":\"prior-cand-3\"")
        assertContains(json, "\"dedupedAgainstCanonicalTxnId\":\"prior-txn-3\"")
        assertContains(json, "\"decisionReason\":\"DUPLICATE_IGNORED\"")
    }

    @Test
    fun `ingested outcome surfaces BILL_DUE side-effect card id`() {
        val json = outcomeJsonOf(
            baseIngested().copy(
                transactionKind = ParsedTransactionKind.BILL_DUE,
                candidateType = TransactionCandidateType.CARD_DUE,
                billDueAppliedToCardId = "card-icici-1",
                dueDateIso = "2026-05-30",
            )
        )
        assertContains(json, "\"billDueAppliedToCardId\":\"card-icici-1\"")
        assertContains(json, "\"dueDateIso\":\"2026-05-30\"")
        assertContains(json, "\"transactionKind\":\"BILL_DUE\"")
    }

    private fun outcomeJsonOf(outcome: IngestionResult): String =
        NotificationDumper::class.java
            .getDeclaredMethod("outcomeJson", IngestionResult::class.java)
            .apply { isAccessible = true }
            .invoke(NotificationDumper, outcome) as String

    private fun assertContains(haystack: String, needle: String) {
        assertTrue(
            "expected JSON to contain: $needle\nactual: $haystack",
            haystack.contains(needle),
        )
    }

    private fun baseIngested(): IngestionResult.Ingested = IngestionResult.Ingested(
        rawEventId = "raw",
        parsedSignalId = "sig",
        candidateId = "cand",
        parserKey = "notification_generic",
        parserVersion = "v3",
        providerHint = null,
        transactionKind = ParsedTransactionKind.SPEND,
        candidateType = TransactionCandidateType.SPEND,
        amountMinor = 50000,
        currencyCode = "INR",
        merchantRaw = "Swiggy",
        toEntityName = "Swiggy",
        mode = Mode.UPI,
        maskedDigits = null,
        parseConfidence = 0.7,
        networkReferenceId = null,
        networkReferenceType = null,
        dueDateIso = null,
        confidenceTier = ConfidenceTier.MEDIUM,
        decisionState = CandidateDecisionState.INBOX_PENDING,
        decisionReason = CandidateDecisionReason.MEDIUM_CONFIDENCE_REVIEW,
        trustRuleMatched = false,
        dedupedAgainstCandidateId = null,
        dedupedAgainstCanonicalTxnId = null,
        inboxItemId = "inbox",
        canonicalTransactionId = null,
        billDueAppliedToCardId = null,
    )
}
