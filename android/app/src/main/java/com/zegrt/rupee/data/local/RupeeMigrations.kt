package com.zegrt.rupee.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `merchant_trust_rules` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `merchantPattern` TEXT NOT NULL,
                `autoCategoryId` TEXT,
                `createdAt` TEXT NOT NULL,
                `updatedAt` TEXT NOT NULL,
                `syncStatus` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_merchant_trust_rules_userId` ON `merchant_trust_rules` (`userId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_merchant_trust_rules_userId_merchantPattern` ON `merchant_trust_rules` (`userId`, `merchantPattern`)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_patterns` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `merchantPattern` TEXT NOT NULL,
                `expectedAmountMinor` INTEGER NOT NULL,
                `intervalDays` INTEGER NOT NULL,
                `occurrenceCount` INTEGER NOT NULL,
                `lastSeenAt` TEXT NOT NULL,
                `nextExpectedAt` TEXT NOT NULL,
                `isConfirmed` INTEGER NOT NULL,
                `isDismissed` INTEGER NOT NULL,
                `sourceType` TEXT NOT NULL,
                `notes` TEXT,
                `createdAt` TEXT NOT NULL,
                `updatedAt` TEXT NOT NULL,
                `syncStatus` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_patterns_userId` ON `recurring_patterns` (`userId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_patterns_merchantPattern` ON `recurring_patterns` (`merchantPattern`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_patterns_nextExpectedAt` ON `recurring_patterns` (`nextExpectedAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_patterns_isConfirmed` ON `recurring_patterns` (`isConfirmed`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_patterns_isDismissed` ON `recurring_patterns` (`isDismissed`)"
        )
    }
}
