package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedSignalEntity
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateType
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class NotificationSignalNormalizer(
    private val database: RupeeDatabase,
) {
    suspend fun normalize(rawEvent: RawCaptureEventEntity) {
        val now = Instant.now().toString()
        val amountMinor = extractAmountMinor(rawEvent.body)
        val providerHint = rawEvent.sourceAppPackage
        val merchant = extractMerchant(rawEvent.body)
        val mode = inferMode(rawEvent.body)
        val parsedSignalId = UUID.randomUUID().toString()

        val parsedSignal = ParsedSignalEntity(
            id = parsedSignalId,
            userId = rawEvent.userId,
            rawCaptureEventId = rawEvent.id,
            parserKey = "notification_generic",
            parserVersion = "v1",
            providerHint = providerHint,
            transactionKind = if (amountMinor != null) ParsedTransactionKind.SPEND else ParsedTransactionKind.UNKNOWN,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            merchantRaw = merchant,
            mode = mode,
            eventOccurredAt = rawEvent.deviceEventTime ?: rawEvent.receivedAt,
            parseConfidence = if (amountMinor != null) 0.55 else 0.15,
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
            candidateType = if (amountMinor != null) TransactionCandidateType.SPEND else TransactionCandidateType.UNKNOWN,
            amountMinor = amountMinor,
            currencyCode = if (amountMinor != null) "INR" else null,
            fromEntityType = AccountType.BANK,
            fromEntityHint = providerHint,
            toEntityName = merchant,
            mode = mode,
            occurredAt = rawEvent.deviceEventTime ?: rawEvent.receivedAt,
            candidateFingerprint = sha256(
                listOf(
                    providerHint.orEmpty(),
                    merchant.orEmpty(),
                    amountMinor?.toString().orEmpty(),
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

    private fun extractAmountMinor(body: String): Long? {
        val regex = Regex("""(?:rs\.?|inr|₹)\s*([0-9]+(?:[.,][0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        val match = regex.find(body) ?: return null
        val normalized = match.groupValues[1].replace(",", "")
        return normalized.toDoubleOrNull()?.times(100)?.toLong()
    }

    private fun extractMerchant(body: String): String? {
        val regex = Regex("""(?:to|at)\s+([A-Za-z0-9 .&_-]{2,40})""", RegexOption.IGNORE_CASE)
        return regex.find(body)?.groupValues?.get(1)?.trim()
    }

    private fun inferMode(body: String): Mode? {
        val lower = body.lowercase()
        return when {
            "upi" in lower -> Mode.UPI
            "debit card" in lower -> Mode.DEBIT_CARD
            "credit card" in lower || "card" in lower -> Mode.CREDIT_CARD
            "atm" in lower -> Mode.ATM
            else -> null
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
