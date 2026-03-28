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

        if (parseResult.transactionKind == ParsedTransactionKind.SPEND &&
            parseResult.candidateType == TransactionCandidateType.SPEND
        ) {
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
