package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_bucket_assignments",
    indices = [
        Index("canonicalTransactionId"),
        Index("bucketId"),
        Index(value = ["canonicalTransactionId", "bucketId"], unique = true),
    ],
)
data class TransactionBucketAssignmentEntity(
    @PrimaryKey val id: String,
    val canonicalTransactionId: String,
    val bucketId: String,
    val assignmentSource: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

