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
        // Look up duplicates against both the current 5-min bucket AND the previous
        // bucket. Two notifications 11:59:30 and 12:00:30 fall into different bare
        // buckets — checking the previous bucket too gives us a ~10-minute window
        // for boundary catches without paying the cost of a true sliding window.
        // The candidate stores the current-bucket fingerprint so the dedupe key
        // remains stable for subsequent lookups.
        val fingerprints = computeFingerprints(rawEvent, parseResult)
        val current = fingerprints.first
        val previous = fingerprints.second

        val duplicateCandidate = database.transactionCandidateDao()
            .getLatestUsableByFingerprint(rawEvent.userId, current)
            ?: previous?.let {
                database.transactionCandidateDao().getLatestUsableByFingerprint(rawEvent.userId, it)
            }
        val duplicateCanonicalTransaction = database.canonicalTransactionDao()
            .getLatestByDedupeFingerprint(rawEvent.userId, current)
            ?: previous?.let {
                database.canonicalTransactionDao().getLatestByDedupeFingerprint(rawEvent.userId, it)
            }
        return DedupeResult(
            fingerprint = current,
            duplicateCandidate = duplicateCandidate,
            duplicateCanonicalTransaction = duplicateCanonicalTransaction,
        )
    }

    private fun computeFingerprints(
        rawEvent: RawCaptureEventEntity,
        parseResult: NotificationParseResult,
    ): Pair<String, String?> {
        val occurredAt = rawEvent.deviceEventTime?.let(Instant::parse) ?: Instant.parse(rawEvent.receivedAt)
        val epochMinute = occurredAt.epochSecond / 60
        val bucketEpochMinute = epochMinute - (epochMinute % BUCKET_MINUTES)
        val current = fingerprintForBucket(bucketEpochMinute, parseResult)
        val previous = fingerprintForBucket(bucketEpochMinute - BUCKET_MINUTES, parseResult)
        return current to previous
    }

    private fun fingerprintForBucket(
        bucketEpochMinute: Long,
        parseResult: NotificationParseResult,
    ): String {
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

    private companion object {
        const val BUCKET_MINUTES = 5L
    }
}
