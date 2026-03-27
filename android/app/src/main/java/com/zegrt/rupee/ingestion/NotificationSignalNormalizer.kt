package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.ParsedSignalEntity
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class NotificationSignalNormalizer(
    private val database: RupeeDatabase,
    private val parserRegistry: NotificationParserRegistry = NotificationParserRegistry.default(),
) {
    suspend fun normalize(rawEvent: RawCaptureEventEntity) {
        val now = Instant.now().toString()
        val parseResult = parserRegistry.parse(rawEvent)
        val parsedSignalId = UUID.randomUUID().toString()

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

        val candidate = TransactionCandidateEntity(
            id = UUID.randomUUID().toString(),
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
            candidateFingerprint = sha256(
                listOf(
                    parseResult.providerHint.orEmpty(),
                    parseResult.toEntityName.orEmpty(),
                    parseResult.amountMinor?.toString().orEmpty(),
                    rawEvent.deviceEventTime.orEmpty(),
                ).joinToString("|"),
            ),
            normalizationVersion = "v1",
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )

        database.transactionCandidateDao().upsertTransactionCandidate(candidate)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}

