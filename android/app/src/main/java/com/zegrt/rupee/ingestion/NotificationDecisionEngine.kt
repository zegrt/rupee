package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

data class CandidateDecision(
    val confidenceTier: ConfidenceTier?,
    val decisionState: CandidateDecisionState,
    val decisionReason: CandidateDecisionReason,
)

class NotificationDecisionEngine {
    fun decide(parseResult: NotificationParseResult): CandidateDecision {
        if (parseResult.amountMinor == null) {
            return CandidateDecision(
                confidenceTier = ConfidenceTier.LOW,
                decisionState = CandidateDecisionState.IGNORED,
                decisionReason = CandidateDecisionReason.MISSING_AMOUNT,
            )
        }

        val confidenceTier = when {
            parseResult.parseConfidence >= HIGH_CONFIDENCE -> ConfidenceTier.HIGH
            parseResult.parseConfidence >= MEDIUM_CONFIDENCE -> ConfidenceTier.MEDIUM
            else -> ConfidenceTier.LOW
        }

        val isSpendLike = parseResult.transactionKind == ParsedTransactionKind.SPEND &&
            parseResult.candidateType == TransactionCandidateType.SPEND
        // EMI debits ("Rs 5000 debited as EMI for HDFC home loan") behave like high-
        // confidence spend events — they impact monthly spend and benefit from auto-
        // creation. The user still confirms via Inbox if confidence is medium.
        val isEmiDebit = parseResult.transactionKind == ParsedTransactionKind.EMI &&
            parseResult.candidateType == TransactionCandidateType.EMI_DUE
        // Income (salary, P2P-received, interest credit) follows the same routing
        // ladder as spend so confirmed inflows show up immediately on the home
        // dashboard. The reasoning is HIGH_CONFIDENCE_SPEND-shaped — we reuse
        // the decision-reason enum rather than add a SPEND/INCOME split, since
        // downstream code branches on transactionKind anyway.
        val isIncome = parseResult.transactionKind == ParsedTransactionKind.INCOME &&
            parseResult.candidateType == TransactionCandidateType.INCOME

        if (isSpendLike || isEmiDebit || isIncome) {
            return when (confidenceTier) {
                ConfidenceTier.HIGH -> CandidateDecision(
                    confidenceTier = confidenceTier,
                    decisionState = CandidateDecisionState.AUTO_CREATED,
                    decisionReason = CandidateDecisionReason.HIGH_CONFIDENCE_SPEND,
                )
                ConfidenceTier.MEDIUM -> CandidateDecision(
                    confidenceTier = confidenceTier,
                    decisionState = CandidateDecisionState.INBOX_PENDING,
                    decisionReason = CandidateDecisionReason.MEDIUM_CONFIDENCE_REVIEW,
                )
                ConfidenceTier.LOW -> CandidateDecision(
                    confidenceTier = confidenceTier,
                    decisionState = CandidateDecisionState.IGNORED,
                    decisionReason = CandidateDecisionReason.LOW_CONFIDENCE_IGNORE,
                )
            }
        }

        return CandidateDecision(
            confidenceTier = confidenceTier,
            decisionState = CandidateDecisionState.INBOX_PENDING,
            decisionReason = CandidateDecisionReason.NON_SPEND_REVIEW,
        )
    }

    companion object {
        private const val HIGH_CONFIDENCE = 0.85
        private const val MEDIUM_CONFIDENCE = 0.6
    }
}
