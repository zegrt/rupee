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

/**
 * Pure fingerprint computation extracted from [NotificationDedupeEngine] so it can be
 * unit-tested without a Room database. Buckets the event time into 5-minute windows;
 * boundary cases (an event at 11:59:30 vs 12:00:30) are handled by the engine
 * checking both the current bucket and the previous one.
 */
internal object DedupeFingerprint {
    const val BUCKET_MINUTES = 5L

    fun compute(rawEvent: RawCaptureEventEntity, parseResult: NotificationParseResult): Pair<String, String> {
        val occurredAt = rawEvent.deviceEventTime?.let(Instant::parse) ?: Instant.parse(rawEvent.receivedAt)
        val epochMinute = occurredAt.epochSecond / 60
        val bucketEpochMinute = epochMinute - (epochMinute % BUCKET_MINUTES)
        return forBucket(bucketEpochMinute, parseResult) to
            forBucket(bucketEpochMinute - BUCKET_MINUTES, parseResult)
    }

    fun forBucket(bucketEpochMinute: Long, parseResult: NotificationParseResult): String {
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

class NotificationDedupeEngine(
    private val database: RupeeDatabase,
) {
    suspend fun detect(
        rawEvent: RawCaptureEventEntity,
        parseResult: NotificationParseResult,
    ): DedupeResult {
        val (current, previous) = DedupeFingerprint.compute(rawEvent, parseResult)

        val duplicateCandidate = database.transactionCandidateDao()
            .getLatestUsableByFingerprint(rawEvent.userId, current)
            ?: database.transactionCandidateDao().getLatestUsableByFingerprint(rawEvent.userId, previous)
        val duplicateCanonicalTransaction = database.canonicalTransactionDao()
            .getLatestByDedupeFingerprint(rawEvent.userId, current)
            ?: database.canonicalTransactionDao().getLatestByDedupeFingerprint(rawEvent.userId, previous)
        return DedupeResult(
            fingerprint = current,
            duplicateCandidate = duplicateCandidate,
            duplicateCanonicalTransaction = duplicateCanonicalTransaction,
        )
    }
}
