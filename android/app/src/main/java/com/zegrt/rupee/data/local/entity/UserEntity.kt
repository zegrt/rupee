package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
    ],
)
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String? = null,
    val displayName: String? = null,
    val defaultCurrencyCode: String,
    val countryCode: String,
    val timezone: String,
    val weekStartDay: Int,
    val monthStartDay: Int = 1,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus,
)

