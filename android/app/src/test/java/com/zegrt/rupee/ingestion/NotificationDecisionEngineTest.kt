package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.TransactionCandidateType
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDecisionEngineTest {

    private val engine = NotificationDecisionEngine()

    @Test
    fun `missing amount routes to IGNORED with MISSING_AMOUNT reason`() {
        val decision = engine.decide(spend(amount = null, confidence = 0.9))
        assertEquals(CandidateDecisionState.IGNORED, decision.decisionState)
        assertEquals(CandidateDecisionReason.MISSING_AMOUNT, decision.decisionReason)
    }

    @Test
    fun `high-confidence SPEND auto-creates`() {
        val decision = engine.decide(spend(amount = 50000, confidence = 0.9))
        assertEquals(CandidateDecisionState.AUTO_CREATED, decision.decisionState)
        assertEquals(ConfidenceTier.HIGH, decision.confidenceTier)
        assertEquals(CandidateDecisionReason.HIGH_CONFIDENCE_SPEND, decision.decisionReason)
    }

    @Test
    fun `medium-confidence SPEND goes to inbox`() {
        val decision = engine.decide(spend(amount = 50000, confidence = 0.7))
        assertEquals(CandidateDecisionState.INBOX_PENDING, decision.decisionState)
        assertEquals(ConfidenceTier.MEDIUM, decision.confidenceTier)
    }

    @Test
    fun `low-confidence SPEND is IGNORED`() {
        val decision = engine.decide(spend(amount = 50000, confidence = 0.4))
        assertEquals(CandidateDecisionState.IGNORED, decision.decisionState)
        assertEquals(ConfidenceTier.LOW, decision.confidenceTier)
        assertEquals(CandidateDecisionReason.LOW_CONFIDENCE_IGNORE, decision.decisionReason)
    }

    @Test
    fun `high-confidence EMI_DUE auto-creates same as SPEND`() {
        val decision = engine.decide(emiDue(amount = 500000, confidence = 0.9))
        assertEquals(CandidateDecisionState.AUTO_CREATED, decision.decisionState)
        assertEquals(ConfidenceTier.HIGH, decision.confidenceTier)
        assertEquals(CandidateDecisionReason.HIGH_CONFIDENCE_SPEND, decision.decisionReason)
    }

    @Test
    fun `medium-confidence EMI_DUE goes to inbox`() {
        val decision = engine.decide(emiDue(amount = 500000, confidence = 0.7))
        assertEquals(CandidateDecisionState.INBOX_PENDING, decision.decisionState)
    }

    @Test
    fun `card BILL_DUE routes to inbox as non-spend review`() {
        val decision = engine.decide(billDue(amount = 100000, confidence = 0.9))
        assertEquals(CandidateDecisionState.INBOX_PENDING, decision.decisionState)
        assertEquals(CandidateDecisionReason.NON_SPEND_REVIEW, decision.decisionReason)
    }

    @Test
    fun `boundary confidence at HIGH threshold is treated as HIGH`() {
        val decision = engine.decide(spend(amount = 1000, confidence = 0.85))
        assertEquals(ConfidenceTier.HIGH, decision.confidenceTier)
    }

    @Test
    fun `boundary confidence at MEDIUM threshold is treated as MEDIUM`() {
        val decision = engine.decide(spend(amount = 1000, confidence = 0.6))
        assertEquals(ConfidenceTier.MEDIUM, decision.confidenceTier)
    }

    private fun spend(amount: Long?, confidence: Double) = NotificationParseResult(
        parserKey = "test",
        parserVersion = "v1",
        transactionKind = ParsedTransactionKind.SPEND,
        candidateType = TransactionCandidateType.SPEND,
        amountMinor = amount,
        currencyCode = if (amount != null) "INR" else null,
        mode = Mode.UPI,
        parseConfidence = confidence,
    )

    private fun emiDue(amount: Long?, confidence: Double) = NotificationParseResult(
        parserKey = "test",
        parserVersion = "v1",
        transactionKind = ParsedTransactionKind.EMI,
        candidateType = TransactionCandidateType.EMI_DUE,
        amountMinor = amount,
        currencyCode = if (amount != null) "INR" else null,
        mode = Mode.BANK_TRANSFER,
        parseConfidence = confidence,
    )

    private fun billDue(amount: Long?, confidence: Double) = NotificationParseResult(
        parserKey = "test",
        parserVersion = "v1",
        transactionKind = ParsedTransactionKind.BILL_DUE,
        candidateType = TransactionCandidateType.CARD_DUE,
        amountMinor = amount,
        currencyCode = if (amount != null) "INR" else null,
        mode = Mode.CREDIT_CARD,
        parseConfidence = confidence,
    )
}
