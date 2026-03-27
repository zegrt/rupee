package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class RawCaptureWriter(
    private val database: RupeeDatabase,
) {
    suspend fun storeNotificationEvent(
        packageName: String?,
        title: String?,
        body: String,
        postedAtMillis: Long?,
    ): RawCaptureEventEntity? {
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

        val event = RawCaptureEventEntity(
            id = UUID.randomUUID().toString(),
            userId = "local-user",
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

        val inserted = database.rawCaptureEventDao().insertRawCaptureEvent(event)
        return if (inserted == -1L) null else event
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
