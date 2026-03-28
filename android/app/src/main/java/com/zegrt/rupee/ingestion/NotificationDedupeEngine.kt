package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

data class DedupeResult(
    val fingerprint: String,
    val duplicateCandidate: TransactionCandidateEntity? = null,
    val duplicateCanonicalTransaction: CanonicalTransactionEntity? = null,
) {
    val isDuplicate: Boolean
        get() = duplicateCandidate != null || duplicateCanonicalTransaction != null
}

class NotificationDedupeEngine(
    private val database: RupeeDatabase,
) {
    suspend fun detect(
        rawEvent: RawCaptureEventEntity,
        parseResult: NotificationParseResult,
    ): DedupeResult {
        val fingerprint = computeFingerprint(rawEvent, parseResult)
        val duplicateCandidate = database.transactionCandidateDao().getLatestUsableByFingerprint(
            userId = rawEvent.userId,
            fingerprint = fingerprint,
        )
        val duplicateCanonicalTransaction = database.canonicalTransactionDao().getLatestByDedupeFingerprint(
            userId = rawEvent.userId,
            fingerprint = fingerprint,
        )
        return DedupeResult(
            fingerprint = fingerprint,
            duplicateCandidate = duplicateCandidate,
            duplicateCanonicalTransaction = duplicateCanonicalTransaction,
        )
    }

    private fun computeFingerprint(
        rawEvent: RawCaptureEventEntity,
        parseResult: NotificationParseResult,
    ): String {
        val occurredAt = rawEvent.deviceEventTime?.let(Instant::parse) ?: Instant.parse(rawEvent.receivedAt)
        val epochMinute = occurredAt.epochSecond / 60
        val bucketEpochMinute = epochMinute - (epochMinute % 5)
        val timeBucket = Instant.ofEpochSecond(bucketEpochMinute * 60).truncatedTo(ChronoUnit.MINUTES).toString()
        val normalizedCounterparty = (parseResult.toEntityName ?: parseResult.merchantRaw)
            ?.lowercase()
            ?.replace(Regex("""[^a-z0-9]+"""), "")
            .orEmpty()

        return sha256(
            listOf(
                parseResult.candidateType.name,
                parseResult.amountMinor?.toString().orEmpty(),
                parseResult.currencyCode.orEmpty(),
                parseResult.mode?.name.orEmpty(),
                parseResult.maskedDigits.orEmpty(),
                normalizedCounterparty,
                timeBucket,
            ).joinToString("|"),
        )
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
