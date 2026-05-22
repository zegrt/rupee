package com.zegrt.rupee.home

import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.InboxReasonCode
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxReasonCopyTest {

    @Test
    fun `duplicate suspected wins over candidate state`() {
        val candidate = candidate(toEntityName = "Swiggy", amountMinor = 25000L, tier = ConfidenceTier.MEDIUM)
        val label = InboxReasonCopy.forInbox(
            reasonCode = InboxReasonCode.POSSIBLE_DUPLICATE_CONFLICT,
            candidate = candidate,
        )
        assertEquals("DUPLICATE SUSPECTED", label)
    }

    @Test
    fun `missing category surfaces its own reason`() {
        val label = InboxReasonCopy.forInbox(
            reasonCode = InboxReasonCode.MISSING_CATEGORY,
            candidate = candidate(),
        )
        assertEquals("NEEDS CATEGORY", label)
    }

    @Test
    fun `ambiguous merchant and direction get distinct copy`() {
        assertEquals(
            "MERCHANT UNCLEAR",
            InboxReasonCopy.forInbox(InboxReasonCode.AMBIGUOUS_MERCHANT, candidate()),
        )
        assertEquals(
            "DIRECTION UNCLEAR",
            InboxReasonCopy.forInbox(InboxReasonCode.AMBIGUOUS_KIND, candidate()),
        )
    }

    @Test
    fun `medium-confidence candidate with amount but no merchant reads amount-only`() {
        val candidate = candidate(toEntityName = null, amountMinor = 25000L, tier = ConfidenceTier.MEDIUM)
        val label = InboxReasonCopy.forInbox(InboxReasonCode.MEDIUM_CONFIDENCE, candidate)
        assertEquals("AMOUNT ONLY", label)
    }

    @Test
    fun `medium-confidence candidate with blank merchant string still counts as missing`() {
        val candidate = candidate(toEntityName = "   ", amountMinor = 25000L, tier = ConfidenceTier.MEDIUM)
        val label = InboxReasonCopy.forInbox(InboxReasonCode.MEDIUM_CONFIDENCE, candidate)
        assertEquals("AMOUNT ONLY", label)
    }

    @Test
    fun `medium-confidence candidate with no merchant and no amount reads no-merchant`() {
        val candidate = candidate(toEntityName = null, amountMinor = null, tier = ConfidenceTier.MEDIUM)
        val label = InboxReasonCopy.forInbox(InboxReasonCode.MEDIUM_CONFIDENCE, candidate)
        assertEquals("NO MERCHANT", label)
    }

    @Test
    fun `low-confidence tier overrides merchant + amount presence`() {
        val candidate = candidate(toEntityName = "Swiggy", amountMinor = 25000L, tier = ConfidenceTier.LOW)
        val label = InboxReasonCopy.forInbox(InboxReasonCode.MEDIUM_CONFIDENCE, candidate)
        assertEquals("LOW CONFIDENCE", label)
    }

    @Test
    fun `medium-confidence with full candidate falls back to review-suggested`() {
        val candidate = candidate(toEntityName = "Swiggy", amountMinor = 25000L, tier = ConfidenceTier.MEDIUM)
        val label = InboxReasonCopy.forInbox(InboxReasonCode.MEDIUM_CONFIDENCE, candidate)
        assertEquals("REVIEW SUGGESTED", label)
    }

    @Test
    fun `null candidate degrades to low-confidence rather than crashing`() {
        val label = InboxReasonCopy.forInbox(InboxReasonCode.MEDIUM_CONFIDENCE, candidate = null)
        assertEquals("LOW CONFIDENCE", label)
    }

    private fun candidate(
        toEntityName: String? = "Swiggy",
        amountMinor: Long? = 25000L,
        tier: ConfidenceTier? = ConfidenceTier.MEDIUM,
    ): TransactionCandidateEntity = TransactionCandidateEntity(
        id = "cand-1",
        userId = "u-1",
        parsedSignalId = "sig-1",
        candidateType = TransactionCandidateType.SPEND,
        amountMinor = amountMinor,
        toEntityName = toEntityName,
        confidenceTier = tier,
        decisionState = CandidateDecisionState.INBOX_PENDING,
        decisionReason = CandidateDecisionReason.MEDIUM_CONFIDENCE_REVIEW,
        normalizationVersion = "v1",
        createdAt = "2026-05-22T00:00:00Z",
        updatedAt = "2026-05-22T00:00:00Z",
        syncStatus = SyncStatus.LOCAL_ONLY,
    )
}
