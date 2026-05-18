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

/**
 * v9 — add foreign-key constraints with CASCADE / SET NULL behaviours on the
 * ingestion chain. SQLite doesn't support ADD CONSTRAINT, so each affected
 * table is rebuilt: create a `_new` table with the FK declarations, copy
 * data over, drop the old, rename `_new`. Indices are recreated to match
 * Room's expected names.
 *
 * The ingestion-chain shape we're encoding:
 *
 *     raw_capture_events
 *         ↓ (CASCADE)
 *     parsed_signals
 *         ↓ (CASCADE)
 *     transaction_candidates ─── (SET NULL) ──> canonical_transactions
 *         ↓ (CASCADE)
 *     inbox_items ────────────── (SET NULL) ──> canonical_transactions
 *
 * Why CASCADE for the parent chain: a parsed_signal has no meaning without
 * its raw_capture_event; a candidate has no meaning without its signal; an
 * inbox row has no meaning without its candidate. Deleting upstream sweeps
 * everything cleanly.
 *
 * Why SET NULL for the canonical-transaction back-references: the user can
 * delete a canonical txn from the transactions list — the inbox row that
 * spawned it (or the candidate) is an audit record and should outlive
 * the canonical txn, just with the dangling pointer cleared.
 *
 * Pre-cleanup before the rebuild: existing rows may already have dangling
 * references (e.g. a parsed_signal pointing at a raw_capture_event that
 * the debug "wipe raw capture" already deleted). The CREATE statement with
 * FK constraints will reject those at INSERT time; defuse by deleting /
 * NULL-ing them up front.
 *
 * Foreign-key enforcement during the migration itself is disabled via
 * `PRAGMA foreign_keys = OFF` for the duration of the table swap — Room
 * will re-enable it after the migration completes. Otherwise the
 * intermediate state (parsed_signals_new exists but candidates point at
 * old parsed_signals ids) would violate constraints.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Defer FK enforcement until all rebuilds finish. Room turns FKs
        // back on after the migration block exits.
        db.execSQL("PRAGMA foreign_keys = OFF")

        // ── 1. Pre-clean orphan references ──────────────────────────────
        //
        // CASCADE paths: if the parent is missing today, just delete the
        // orphan child — it would have been cascade-deleted anyway.
        db.execSQL(
            "DELETE FROM `parsed_signals` WHERE `rawCaptureEventId` NOT IN " +
                "(SELECT `id` FROM `raw_capture_events`)"
        )
        db.execSQL(
            "DELETE FROM `transaction_candidates` WHERE `parsedSignalId` NOT IN " +
                "(SELECT `id` FROM `parsed_signals`)"
        )
        db.execSQL(
            "DELETE FROM `inbox_items` WHERE `transactionCandidateId` NOT IN " +
                "(SELECT `id` FROM `transaction_candidates`)"
        )
        // SET NULL paths: null out dangling pointers so the new FK accepts
        // the row.
        db.execSQL(
            "UPDATE `transaction_candidates` SET `linkedCanonicalTransactionId` = NULL " +
                "WHERE `linkedCanonicalTransactionId` IS NOT NULL " +
                "AND `linkedCanonicalTransactionId` NOT IN (SELECT `id` FROM `canonical_transactions`)"
        )
        db.execSQL(
            "UPDATE `transaction_candidates` SET `duplicateOfCandidateId` = NULL " +
                "WHERE `duplicateOfCandidateId` IS NOT NULL " +
                "AND `duplicateOfCandidateId` NOT IN (SELECT `id` FROM `transaction_candidates`)"
        )
        db.execSQL(
            "UPDATE `inbox_items` SET `linkedCanonicalTransactionId` = NULL " +
                "WHERE `linkedCanonicalTransactionId` IS NOT NULL " +
                "AND `linkedCanonicalTransactionId` NOT IN (SELECT `id` FROM `canonical_transactions`)"
        )

        // ── 2. Rebuild parsed_signals with FK on rawCaptureEventId ──────
        //
        // CREATE statement copied verbatim from
        // schemas/.../RupeeDatabase/9.json so the post-migration schema
        // fingerprint matches what Room expects. If you change FK clauses
        // in the entity, regenerate 9.json and update both places.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `parsed_signals_new` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `rawCaptureEventId` TEXT NOT NULL,
                `parserKey` TEXT NOT NULL,
                `parserVersion` TEXT NOT NULL,
                `providerHint` TEXT,
                `transactionKind` TEXT NOT NULL,
                `amountMinor` INTEGER,
                `currencyCode` TEXT,
                `merchantRaw` TEXT,
                `sourceAccountHint` TEXT,
                `sourceCardHint` TEXT,
                `maskedDigits` TEXT,
                `mode` TEXT,
                `eventOccurredAt` TEXT,
                `networkReferenceId` TEXT,
                `networkReferenceType` TEXT,
                `patternUid` INTEGER,
                `parseConfidence` REAL NOT NULL,
                `structuredJson` TEXT,
                `createdAt` TEXT NOT NULL,
                `updatedAt` TEXT NOT NULL,
                `syncStatus` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`rawCaptureEventId`) REFERENCES `raw_capture_events`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `parsed_signals_new` (
                `id`, `userId`, `rawCaptureEventId`, `parserKey`, `parserVersion`,
                `providerHint`, `transactionKind`, `amountMinor`, `currencyCode`,
                `merchantRaw`, `sourceAccountHint`, `sourceCardHint`, `maskedDigits`,
                `mode`, `eventOccurredAt`, `networkReferenceId`, `networkReferenceType`,
                `patternUid`, `parseConfidence`, `structuredJson`, `createdAt`,
                `updatedAt`, `syncStatus`
            )
            SELECT
                `id`, `userId`, `rawCaptureEventId`, `parserKey`, `parserVersion`,
                `providerHint`, `transactionKind`, `amountMinor`, `currencyCode`,
                `merchantRaw`, `sourceAccountHint`, `sourceCardHint`, `maskedDigits`,
                `mode`, `eventOccurredAt`, `networkReferenceId`, `networkReferenceType`,
                `patternUid`, `parseConfidence`, `structuredJson`, `createdAt`,
                `updatedAt`, `syncStatus`
            FROM `parsed_signals`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `parsed_signals`")
        db.execSQL("ALTER TABLE `parsed_signals_new` RENAME TO `parsed_signals`")
        // Recreate all indices Room expects for this entity.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parsed_signals_userId` ON `parsed_signals` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parsed_signals_rawCaptureEventId` ON `parsed_signals` (`rawCaptureEventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parsed_signals_providerHint` ON `parsed_signals` (`providerHint`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parsed_signals_transactionKind` ON `parsed_signals` (`transactionKind`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parsed_signals_eventOccurredAt` ON `parsed_signals` (`eventOccurredAt`)")

        // ── 3. Rebuild transaction_candidates with three FKs ────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transaction_candidates_new` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `parsedSignalId` TEXT NOT NULL,
                `candidateType` TEXT NOT NULL,
                `amountMinor` INTEGER,
                `currencyCode` TEXT,
                `fromEntityType` TEXT,
                `fromEntityHint` TEXT,
                `toEntityName` TEXT,
                `mode` TEXT,
                `occurredAt` TEXT,
                `candidateFingerprint` TEXT,
                `confidenceTier` TEXT,
                `decisionState` TEXT NOT NULL,
                `decisionReason` TEXT NOT NULL,
                `duplicateOfCandidateId` TEXT,
                `linkedInboxItemId` TEXT,
                `linkedCanonicalTransactionId` TEXT,
                `normalizationVersion` TEXT NOT NULL,
                `createdAt` TEXT NOT NULL,
                `updatedAt` TEXT NOT NULL,
                `syncStatus` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`parsedSignalId`) REFERENCES `parsed_signals`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`linkedCanonicalTransactionId`) REFERENCES `canonical_transactions`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`duplicateOfCandidateId`) REFERENCES `transaction_candidates`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `transaction_candidates_new` (
                `id`, `userId`, `parsedSignalId`, `candidateType`, `amountMinor`,
                `currencyCode`, `fromEntityType`, `fromEntityHint`, `toEntityName`,
                `mode`, `occurredAt`, `candidateFingerprint`, `confidenceTier`,
                `decisionState`, `decisionReason`, `duplicateOfCandidateId`,
                `linkedInboxItemId`, `linkedCanonicalTransactionId`,
                `normalizationVersion`, `createdAt`, `updatedAt`, `syncStatus`
            )
            SELECT
                `id`, `userId`, `parsedSignalId`, `candidateType`, `amountMinor`,
                `currencyCode`, `fromEntityType`, `fromEntityHint`, `toEntityName`,
                `mode`, `occurredAt`, `candidateFingerprint`, `confidenceTier`,
                `decisionState`, `decisionReason`, `duplicateOfCandidateId`,
                `linkedInboxItemId`, `linkedCanonicalTransactionId`,
                `normalizationVersion`, `createdAt`, `updatedAt`, `syncStatus`
            FROM `transaction_candidates`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `transaction_candidates`")
        db.execSQL("ALTER TABLE `transaction_candidates_new` RENAME TO `transaction_candidates`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_candidates_userId` ON `transaction_candidates` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_candidates_parsedSignalId` ON `transaction_candidates` (`parsedSignalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_candidates_candidateType` ON `transaction_candidates` (`candidateType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_candidates_decisionState` ON `transaction_candidates` (`decisionState`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_candidates_occurredAt` ON `transaction_candidates` (`occurredAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_candidates_candidateFingerprint` ON `transaction_candidates` (`candidateFingerprint`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_candidates_linkedCanonicalTransactionId` ON `transaction_candidates` (`linkedCanonicalTransactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_candidates_duplicateOfCandidateId` ON `transaction_candidates` (`duplicateOfCandidateId`)")

        // ── 4. Rebuild inbox_items with two FKs ─────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inbox_items_new` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `transactionCandidateId` TEXT NOT NULL,
                `reasonCode` TEXT NOT NULL,
                `decisionState` TEXT NOT NULL,
                `linkedCanonicalTransactionId` TEXT,
                `createdAt` TEXT NOT NULL,
                `resolvedAt` TEXT,
                `updatedAt` TEXT NOT NULL,
                `syncStatus` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`transactionCandidateId`) REFERENCES `transaction_candidates`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`linkedCanonicalTransactionId`) REFERENCES `canonical_transactions`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `inbox_items_new` (
                `id`, `userId`, `transactionCandidateId`, `reasonCode`,
                `decisionState`, `linkedCanonicalTransactionId`, `createdAt`,
                `resolvedAt`, `updatedAt`, `syncStatus`
            )
            SELECT
                `id`, `userId`, `transactionCandidateId`, `reasonCode`,
                `decisionState`, `linkedCanonicalTransactionId`, `createdAt`,
                `resolvedAt`, `updatedAt`, `syncStatus`
            FROM `inbox_items`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `inbox_items`")
        db.execSQL("ALTER TABLE `inbox_items_new` RENAME TO `inbox_items`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_items_userId` ON `inbox_items` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_items_decisionState` ON `inbox_items` (`decisionState`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_items_transactionCandidateId` ON `inbox_items` (`transactionCandidateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_items_createdAt` ON `inbox_items` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_items_linkedCanonicalTransactionId` ON `inbox_items` (`linkedCanonicalTransactionId`)")

        // FK enforcement is re-enabled by Room on migration exit.
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

/**
 * v10 — explicit `mergedFromExistingCanonicalId` column on inbox_items.
 *
 * Replaces the brittle DumpOutcomeDao CASE expression that derived the merge
 * state by comparing `linkedCanonicalTransactionId` against a synthesised
 * `'txn-' || tc.id` string. Now `confirmInboxItemMergedWith` writes the
 * existing canonical id into this column directly, and the DAO selects it.
 *
 * Pre-v10 rows get NULL (the column is nullable with no default backfill —
 * we don't know which historical confirms were merges without re-running
 * the heuristic, and the dump-outcome snapshot is for current-state
 * triage, not historical analysis).
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `inbox_items` ADD COLUMN `mergedFromExistingCanonicalId` TEXT DEFAULT NULL"
        )
    }
}
