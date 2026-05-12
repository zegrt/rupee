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

/**
 * v8 — Sprint 1 additive columns. No table renames or drops, so the migration
 * is purely ALTER TABLE … ADD COLUMN. All new columns are nullable / default
 * false so existing rows remain valid.
 *
 *  - `networkReferenceId` + `networkReferenceType` on `parsed_signals` and
 *    `canonical_transactions` (Axio §3.4) — captured by parsers to enable
 *    later cross-stream chaining dedupe.
 *  - `patternUid` on `parsed_signals` — reserved for the JSON rule engine.
 *  - `excludeFromExpenseTotals` / `excludeFromIncomeTotals` on `accounts` and
 *    `credit_cards` (Axio §5.9) — per-account opt-out from spend/income
 *    rollups so wallets don't double-count.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `parsed_signals` ADD COLUMN `networkReferenceId` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `parsed_signals` ADD COLUMN `networkReferenceType` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `parsed_signals` ADD COLUMN `patternUid` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `canonical_transactions` ADD COLUMN `networkReferenceId` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `canonical_transactions` ADD COLUMN `networkReferenceType` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `excludeFromExpenseTotals` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `excludeFromIncomeTotals` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `credit_cards` ADD COLUMN `excludeFromExpenseTotals` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `credit_cards` ADD COLUMN `excludeFromIncomeTotals` INTEGER NOT NULL DEFAULT 0")
    }
}
