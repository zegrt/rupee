package com.zegrt.rupee.ingestion

import androidx.room.withTransaction
import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.InboxReasonCode
import com.zegrt.rupee.data.local.entity.ParsedSignalEntity
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import java.time.Instant
import java.util.UUID

class NotificationSignalNormalizer(
    private val database: RupeeDatabase,
    private val parserRegistry: NotificationParserRegistry = NotificationParserRegistry.default(),
    private val decisionEngine: NotificationDecisionEngine = NotificationDecisionEngine(),
    private val dedupeEngine: NotificationDedupeEngine = NotificationDedupeEngine(database),
) {
    suspend fun normalize(rawEvent: RawCaptureEventEntity) {
        database.withTransaction {
            val now = Instant.now().toString()
            val parseResult = parserRegistry.parse(rawEvent)
            val dedupeResult = dedupeEngine.detect(rawEvent, parseResult)
            val baseDecision = decisionEngine.decide(parseResult)
            val decision = if (dedupeResult.isDuplicate) {
                CandidateDecision(
                    confidenceTier = baseDecision.confidenceTier,
                    decisionState = CandidateDecisionState.IGNORED,
                    decisionReason = CandidateDecisionReason.DUPLICATE_IGNORED,
                )
            } else {
                baseDecision
            }
            val parsedSignalId = UUID.randomUUID().toString()
            val candidateId = UUID.randomUUID().toString()

            val parsedSignal = ParsedSignalEntity(
                id = parsedSignalId,
                userId = rawEvent.userId,
                rawCaptureEventId = rawEvent.id,
                parserKey = parseResult.parserKey,
                parserVersion = parseResult.parserVersion,
                providerHint = parseResult.providerHint,
                transactionKind = parseResult.transactionKind,
                amountMinor = parseResult.amountMinor,
                currencyCode = parseResult.currencyCode,
                merchantRaw = parseResult.merchantRaw,
                sourceAccountHint = parseResult.sourceAccountHint,
                sourceCardHint = parseResult.sourceCardHint,
                maskedDigits = parseResult.maskedDigits,
                mode = parseResult.mode,
                eventOccurredAt = rawEvent.deviceEventTime ?: rawEvent.receivedAt,
                parseConfidence = parseResult.parseConfidence,
                structuredJson = null,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )

            database.parsedSignalDao().upsertParsedSignal(parsedSignal)

            val inboxItemId = if (decision.decisionState == CandidateDecisionState.INBOX_PENDING) {
                createInboxItem(
                    userId = rawEvent.userId,
                    transactionCandidateId = candidateId,
                    reasonCode = toInboxReasonCode(decision.decisionReason),
                    now = now,
                )
            } else {
                null
            }

            val canonicalTransactionId = when {
                dedupeResult.duplicateCanonicalTransaction != null -> dedupeResult.duplicateCanonicalTransaction.id
                decision.decisionState == CandidateDecisionState.AUTO_CREATED -> createCanonicalTransaction(
                    rawEvent = rawEvent,
                    parseResult = parseResult,
                    confidenceTier = decision.confidenceTier,
                    dedupeFingerprint = dedupeResult.fingerprint,
                    now = now,
                )
                else -> null
            }

            val candidate = TransactionCandidateEntity(
                id = candidateId,
                userId = rawEvent.userId,
                parsedSignalId = parsedSignalId,
                candidateType = parseResult.candidateType,
                amountMinor = parseResult.amountMinor,
                currencyCode = parseResult.currencyCode,
                fromEntityType = parseResult.fromEntityType,
                fromEntityHint = parseResult.fromEntityHint,
                toEntityName = parseResult.toEntityName,
                mode = parseResult.mode,
                occurredAt = rawEvent.deviceEventTime ?: rawEvent.receivedAt,
                candidateFingerprint = dedupeResult.fingerprint,
                confidenceTier = decision.confidenceTier,
                decisionState = decision.decisionState,
                decisionReason = decision.decisionReason,
                duplicateOfCandidateId = dedupeResult.duplicateCandidate?.id,
                linkedInboxItemId = inboxItemId,
                linkedCanonicalTransactionId = canonicalTransactionId ?: dedupeResult.duplicateCandidate?.linkedCanonicalTransactionId,
                normalizationVersion = "v1",
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )

            database.transactionCandidateDao().upsertTransactionCandidate(candidate)
        }
    }

    private suspend fun createInboxItem(
        userId: String,
        transactionCandidateId: String,
        reasonCode: InboxReasonCode,
        now: String,
    ): String {
        val inboxItem = InboxItemEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            transactionCandidateId = transactionCandidateId,
            reasonCode = reasonCode,
            decisionState = InboxDecisionState.PENDING,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )
        database.inboxItemDao().upsertInboxItem(inboxItem)
        return inboxItem.id
    }

    private suspend fun createCanonicalTransaction(
        rawEvent: RawCaptureEventEntity,
        parseResult: NotificationParseResult,
        confidenceTier: ConfidenceTier?,
        dedupeFingerprint: String,
        now: String,
    ): String {
        val canonicalTransaction = CanonicalTransactionEntity(
            id = UUID.randomUUID().toString(),
            userId = rawEvent.userId,
            type = CanonicalTransactionType.EXPENSE,
            status = CanonicalTransactionStatus.CONFIRMED,
            amountMinor = parseResult.amountMinor ?: 0L,
            currencyCode = parseResult.currencyCode ?: "INR",
            merchantName = parseResult.toEntityName ?: parseResult.merchantRaw,
            mode = parseResult.mode,
            occurredAt = rawEvent.deviceEventTime ?: rawEvent.receivedAt,
            sourceSummary = parseResult.providerHint,
            createdBy = "notification_auto",
            confidenceTier = confidenceTier,
            dedupeFingerprint = dedupeFingerprint,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )
        database.canonicalTransactionDao().upsertTransactions(listOf(canonicalTransaction))
        return canonicalTransaction.id
    }

    private fun toInboxReasonCode(reason: CandidateDecisionReason): InboxReasonCode {
        return when (reason) {
            CandidateDecisionReason.HIGH_CONFIDENCE_SPEND ->
                InboxReasonCode.MEDIUM_CONFIDENCE
            CandidateDecisionReason.MEDIUM_CONFIDENCE_REVIEW ->
                InboxReasonCode.MEDIUM_CONFIDENCE
            CandidateDecisionReason.LOW_CONFIDENCE_IGNORE ->
                InboxReasonCode.AMBIGUOUS_KIND
            CandidateDecisionReason.NON_SPEND_REVIEW ->
                InboxReasonCode.AMBIGUOUS_KIND
            CandidateDecisionReason.MISSING_AMOUNT ->
                InboxReasonCode.AMBIGUOUS_KIND
            CandidateDecisionReason.DUPLICATE_IGNORED ->
                InboxReasonCode.POSSIBLE_DUPLICATE_CONFLICT
        }
    }
}
