package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RawCaptureSourceType {
    NOTIFICATION,
    SMS,
    MANUAL,
    CSV,
}

enum class RawCaptureIngestionStatus {
    CAPTURED,
    FILTERED,
    PARSED,
    FAILED,
}

@Entity(
    tableName = "raw_capture_events",
    indices = [
        Index("userId"),
        Index("sourceType"),
        Index("sourceAppPackage"),
        Index("senderAddress"),
        Index("receivedAt"),
        Index(value = ["hashFingerprint"], unique = true),
    ],
)
data class RawCaptureEventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sourceType: RawCaptureSourceType,
    val sourceAppPackage: String? = null,
    val senderAddress: String? = null,
    val title: String? = null,
    val body: String,
    val receivedAt: String,
    val deviceEventTime: String? = null,
    val hashFingerprint: String,
    val ingestionStatus: RawCaptureIngestionStatus,
    val metadataJson: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

