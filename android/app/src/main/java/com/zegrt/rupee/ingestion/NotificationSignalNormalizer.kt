package com.zegrt.rupee.ingestion

import androidx.room.withTransaction
import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.CreditCardEntity
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
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class NotificationSignalNormalizer(
    private val database: RupeeDatabase,
    private val parserRegistry: NotificationParserRegistry = NotificationParserRegistry.default(),
    private val decisionEngine: NotificationDecisionEngine = NotificationDecisionEngine(),
    private val dedupeEngine: NotificationDedupeEngine = NotificationDedupeEngine(database),
) {
    /**
     * Single-transaction entry point: persists the raw event AND runs normalization
     * atomically, then flips `ingestionStatus` to PARSED inside the same transaction.
     * Returns the persisted event id, or null if a duplicate raw fingerprint already
     * exists (OnConflict.IGNORE on the unique hashFingerprint index).
     *
     * Callers (listener service, debug ingest) use this instead of separate
     * insert + normalize calls — if the coroutine is cancelled mid-flow,
     * either both happen or neither, so we don't leave orphan raw events.
     */
    suspend fun ingestNotification(
        userId: String,
        packageName: String?,
        title: String?,
        body: String,
        postedAtMillis: Long?,
    ): String? {
        return database.withTransaction {
            val now = Instant.now().toString()
            val fingerprint = sha256(
                listOf(
                    RawCaptureSourceType.NOTIFICATION.name,
                    packageName.orEmpty(),
                    title.orEmpty(),
                    body,
                    postedAtMillis?.toString().orEmpty(),
                ).joinToString("|"),
            )
            val rawEvent = RawCaptureEventEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                sourceType = RawCaptureSourceType.NOTIFICATION,
                sourceAppPackage = packageName,
                title = title,
                body = body,
                receivedAt = now,
                deviceEventTime = postedAtMillis?.let { Instant.ofEpochMilli(it).toString() },
                hashFingerprint = fingerprint,
                ingestionStatus = RawCaptureIngestionStatus.CAPTURED,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )
            val inserted = database.rawCaptureEventDao().insertRawCaptureEvent(rawEvent)
            if (inserted == -1L) return@withTransaction null
            normalizeLocked(rawEvent)
            database.rawCaptureEventDao().updateIngestionStatus(
                id = rawEvent.id,
                status = RawCaptureIngestionStatus.PARSED.name,
                updatedAt = Instant.now().toString(),
            )
            rawEvent.id
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    suspend fun normalize(rawEvent: RawCaptureEventEntity) {
        database.withTransaction {
            normalizeLocked(rawEvent)
        }
    }

    private suspend fun normalizeLocked(rawEvent: RawCaptureEventEntity) {
        run {
            val now = Instant.now().toString()
            // Enrich the parser's result with a network reference if the body
            // had one. Done here instead of in each parser so all 8 parsers
            // get this for free and the upcoming JSON rule engine inherits it.
            val parseResult = parserRegistry.parse(rawEvent).let { result ->
                if (result.networkReferenceId != null) result
                else NotificationParsingUtils.extractNetworkReference(rawEvent.body)
                    ?.let { ref ->
                        result.copy(
                            networkReferenceId = ref.id,
                            networkReferenceType = ref.type,
                        )
                    }
                    ?: result
            }
            val dedupeResult = dedupeEngine.detect(rawEvent, parseResult)
            val baseDecision = decisionEngine.decide(parseResult)
            val trustRule = if (parseResult.amountMinor != null && !dedupeResult.isDuplicate) {
                // Indexed lookup against the cleaned merchant form. Try toEntityName
                // first (parser-cleaned) then merchantRaw cleaned at read time. Avoids
                // scanning every rule on every notification.
                val primary = parseResult.toEntityName
                    ?.let(MerchantNameUtils::clean)?.takeIf { it != "Unnamed" }
                val fallback = parseResult.merchantRaw
                    ?.let(MerchantNameUtils::clean)?.takeIf { it != "Unnamed" }
                val dao = database.merchantTrustRuleDao()
                primary?.let { dao.findByCleanedPattern(rawEvent.userId, it) }
                    ?: fallback?.takeIf { it != primary }?.let { dao.findByCleanedPattern(rawEvent.userId, it) }
            } else null
            val decision = when {
                dedupeResult.isDuplicate -> CandidateDecision(
                    confidenceTier = baseDecision.confidenceTier,
                    decisionState = CandidateDecisionState.IGNORED,
                    decisionReason = CandidateDecisionReason.DUPLICATE_IGNORED,
                )
                trustRule != null -> CandidateDecision(
                    confidenceTier = ConfidenceTier.HIGH,
                    decisionState = CandidateDecisionState.AUTO_CREATED,
                    decisionReason = CandidateDecisionReason.MERCHANT_TRUSTED,
                )
                else -> baseDecision
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
                networkReferenceId = parseResult.networkReferenceId,
                networkReferenceType = parseResult.networkReferenceType,
                patternUid = null,
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
                    status = if (trustRule != null) CanonicalTransactionStatus.CONFIRMED
                    else CanonicalTransactionStatus.SUGGESTED,
                    overrideCategoryId = trustRule?.autoCategoryId,
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

            // BILL_DUE side-effect: write the parsed amount/date into the matching
            // credit_card row so DuesAlertManager and the Home dashboard have data
            // to surface. Only fires for high-confidence card-due candidates with
            // an extractable due date.
            if (parseResult.transactionKind == ParsedTransactionKind.BILL_DUE &&
                decision.confidenceTier == ConfidenceTier.HIGH &&
                parseResult.amountMinor != null &&
                parseResult.dueDateIso != null
            ) {
                applyBillDueToCard(rawEvent.userId, parseResult, now)
            }
        }
    }

    private suspend fun applyBillDueToCard(
        userId: String,
        parseResult: NotificationParseResult,
        now: String,
    ) {
        val cards = database.creditCardDao().getActiveCards(userId)
        if (cards.isEmpty()) return
        val match = findMatchingCard(cards, parseResult) ?: return
        database.creditCardDao().upsertCards(
            listOf(
                match.copy(
                    statementDueAmountMinor = parseResult.amountMinor,
                    statementDueDate = parseResult.dueDateIso,
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun findMatchingCard(
        cards: List<CreditCardEntity>,
        parseResult: NotificationParseResult,
    ): CreditCardEntity? {
        // 1. Last-4 digit match is the only fully unambiguous signal — use it
        //    whenever the parser captured the masked digits.
        val digits = parseResult.maskedDigits
        if (!digits.isNullOrBlank()) {
            cards.firstOrNull { it.maskedIdentifier?.takeLast(4) == digits }?.let { return it }
        }
        // 2. Provider hint match — only when there's a single candidate. If the
        //    user has multiple cards from the same provider (two ICICI cards),
        //    refuse to guess and let the user set the due date manually instead.
        val hint = parseResult.sourceCardHint?.lowercase()
        if (!hint.isNullOrBlank() && hint != "cred_card") {
            val matches = cards.filter { card ->
                card.providerName?.lowercase()?.contains(hint) == true ||
                    card.displayName.lowercase().contains(hint)
            }
            if (matches.size == 1) return matches.single()
        }
        // 3. Fall back to the single active card if there's exactly one — common
        // in current builds where users typically add one card during onboarding.
        return cards.singleOrNull()
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
        status: CanonicalTransactionStatus = CanonicalTransactionStatus.SUGGESTED,
        overrideCategoryId: String? = null,
    ): String {
        val canonicalTransaction = CanonicalTransactionEntity(
            id = UUID.randomUUID().toString(),
            userId = rawEvent.userId,
            type = CanonicalTransactionType.EXPENSE,
            status = status,
            amountMinor = parseResult.amountMinor ?: 0L,
            currencyCode = parseResult.currencyCode ?: "INR",
            merchantName = parseResult.toEntityName ?: parseResult.merchantRaw,
            categoryId = overrideCategoryId,
            mode = parseResult.mode,
            occurredAt = rawEvent.deviceEventTime ?: rawEvent.receivedAt,
            sourceSummary = if (status == CanonicalTransactionStatus.CONFIRMED)
                "${parseResult.providerHint ?: "notification"} • trusted merchant"
            else parseResult.providerHint,
            createdBy = if (status == CanonicalTransactionStatus.CONFIRMED) "trust_rule" else "notification_auto",
            confidenceTier = confidenceTier,
            dedupeFingerprint = dedupeFingerprint,
            networkReferenceId = parseResult.networkReferenceId,
            networkReferenceType = parseResult.networkReferenceType,
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
            CandidateDecisionReason.MERCHANT_TRUSTED ->
                InboxReasonCode.MEDIUM_CONFIDENCE
        }
    }
}
