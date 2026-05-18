package com.zegrt.rupee.data.repository

import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.dao.CategorySpend
import com.zegrt.rupee.data.local.dao.DumpOutcomeSnapshot
import com.zegrt.rupee.data.local.dao.InboxItemWithCandidate
import com.zegrt.rupee.data.local.entity.TransactionCandidateType
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.BucketEntity
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.BudgetType
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import com.zegrt.rupee.data.local.entity.EmiPlanEntity
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.MerchantTrustRuleEntity
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.RecurringPatternEntity
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import com.zegrt.rupee.ingestion.MerchantNameUtils
import com.zegrt.rupee.ingestion.NotificationSignalNormalizer
import com.zegrt.rupee.recurring.RecurringDetectionEngine
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingSetupInput(
    val bankAccountName: String,
    val bankProviderName: String,
    val creditCardName: String,
    val creditCardProviderName: String,
    val cashAccountEnabled: Boolean,
    val cashBalanceMinor: Long?,
)

class LocalFinanceRepository(
    private val database: RupeeDatabase,
    private val recurringDetector: RecurringDetectionEngine = RecurringDetectionEngine(),
    // Fire-and-forget coroutine scope for background persistence writes
    // (debounce timestamps and similar). RupeeApplication passes its
    // app-lifetime SupervisorJob; tests / debug callers that construct
    // the repository directly get an isolated SupervisorJob default
    // that won't leak across test runs. Avoids the GlobalScope opt-in
    // that an earlier draft of M4 used.
    private val persistScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    // Hydrated from `app_state` at startup via [hydrateDebounceState]; written
    // back to `app_state` on every set via [persistDebounceTimestamp]. Without
    // the persistence layer these reset to 0 on every cold start, forcing
    // a 120-day scan / 90-day prune to fire on the next foreground tick
    // even when one ran an hour ago.
    private val lastRecurringRefreshMs = AtomicLong(0L)
    private val lastIngestionPruneMs = AtomicLong(0L)

    companion object {
        private const val USER_ID = "local-user"
        private const val DEFAULT_MONTHLY_BUDGET_MINOR = 4_000_000L
        private const val RECURRING_REFRESH_DEBOUNCE_MS = 30 * 60 * 1000L

        // Keys for the app_state K/V table. Stored as decimal-string-encoded
        // epoch-millis longs.
        private const val KEY_LAST_RECURRING_REFRESH_MS = "last_recurring_refresh_ms"
        private const val KEY_LAST_INGESTION_PRUNE_MS = "last_ingestion_prune_ms"

        // Pruning runs at most once per 24 hours. Cheap-enough to call on
        // every cold start without nagging the disk; spaced enough that a
        // user bouncing the app several times in a session doesn't trigger
        // it.
        private const val INGESTION_PRUNE_DEBOUNCE_MS = 24 * 60 * 60 * 1000L

        // Default retention window for raw_capture_events. Beyond 90 days
        // the gate-rejected telemetry has diminishing value (parsers evolve
        // faster than that) and dump-replay against ancient bodies is
        // rarely useful. User-confirmed canonical transactions are NOT
        // affected by this prune — H4's FK cascade clears the pipeline rows
        // but SET NULL preserves the canonical txn with its dangling
        // pointer cleared.
        private const val DEFAULT_RAW_EVENT_RETENTION_DAYS = 90L
    }

    fun observeUser(): Flow<UserEntity?> = database.userDao().observeUser()

    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeActiveAccounts()

    fun observeCards(): Flow<List<CreditCardEntity>> = database.creditCardDao().observeActiveCards()

    suspend fun setAccountExcludeFromExpenseTotals(accountId: String, exclude: Boolean) {
        val account = database.accountDao().getAccountById(accountId) ?: return
        val now = Instant.now().toString()
        database.accountDao().upsertAccounts(
            listOf(account.copy(excludeFromExpenseTotals = exclude, updatedAt = now)),
        )
    }

    suspend fun setCardExcludeFromExpenseTotals(cardId: String, exclude: Boolean) {
        val card = database.creditCardDao().getCardById(cardId) ?: return
        val now = Instant.now().toString()
        database.creditCardDao().upsertCards(
            listOf(card.copy(excludeFromExpenseTotals = exclude, updatedAt = now)),
        )
    }

    fun observeEmiPlans(): Flow<List<EmiPlanEntity>> = database.emiPlanDao().observePlans(USER_ID)

    fun observeRecurringPatterns(): Flow<List<RecurringPatternEntity>> =
        database.recurringPatternDao().observePatterns(USER_ID)

    suspend fun confirmRecurringPattern(id: String) {
        val now = Instant.now().toString()
        val pattern = database.recurringPatternDao().getById(id) ?: return
        database.recurringPatternDao().upsertPattern(
            pattern.copy(isConfirmed = true, isDismissed = false, updatedAt = now),
        )
    }

    suspend fun dismissRecurringPattern(id: String) {
        val now = Instant.now().toString()
        val pattern = database.recurringPatternDao().getById(id) ?: return
        database.recurringPatternDao().upsertPattern(
            pattern.copy(isDismissed = true, updatedAt = now),
        )
    }

    suspend fun removeRecurringPattern(id: String) {
        database.recurringPatternDao().deleteById(id)
    }

    /**
     * Re-run auto-detection over the last 120 days. Wipes prior unconfirmed auto-suggestions
     * and rewrites them; preserves user-confirmed and user-dismissed rows so the user's
     * decisions stick across runs.
     *
     * When [force] is false (default), the 30-min debounce skips redundant work from
     * lifecycle hooks. User-triggered refreshes pass force=true so the action isn't
     * silently swallowed when the user taps "Refresh now".
     */
    suspend fun refreshRecurringPatterns(
        today: java.time.LocalDate = java.time.LocalDate.now(),
        force: Boolean = false,
    ) {
        val currentMs = System.currentTimeMillis()
        if (!force && currentMs - lastRecurringRefreshMs.get() < RECURRING_REFRESH_DEBOUNCE_MS) return
        lastRecurringRefreshMs.set(currentMs)
        persistDebounceTimestamp(KEY_LAST_RECURRING_REFRESH_MS, currentMs)

        val lookbackStart = today.minusDays(120).toString()
        val nowIso = today.plusDays(1).toString()
        val txns = database.canonicalTransactionDao()
            .getTransactionsInPeriod(USER_ID, lookbackStart, nowIso)
        val detected = recurringDetector.detect(txns, today)
        val existing = database.recurringPatternDao().getAllForUser(USER_ID)
        val byMerchant = existing.associateBy { it.merchantPattern.lowercase() }
        val now = Instant.now().toString()

        database.recurringPatternDao().deleteUnconfirmedAuto(USER_ID)

        val rows = detected.mapNotNull { pattern ->
            val key = pattern.merchantPattern.lowercase()
            val prior = byMerchant[key]
            // If user already dismissed this merchant's auto suggestion, leave it dismissed.
            if (prior != null && prior.isDismissed && prior.sourceType == "auto") return@mapNotNull null
            // If user confirmed it earlier, just refresh next-expected and amount but keep state.
            if (prior != null && prior.isConfirmed) {
                return@mapNotNull prior.copy(
                    expectedAmountMinor = pattern.expectedAmountMinor,
                    intervalDays = pattern.intervalDays,
                    occurrenceCount = pattern.occurrenceCount,
                    lastSeenAt = pattern.lastSeenAt,
                    nextExpectedAt = pattern.nextExpectedAt,
                    updatedAt = now,
                )
            }
            RecurringPatternEntity(
                id = "rec-${java.util.UUID.randomUUID()}",
                userId = USER_ID,
                merchantPattern = pattern.merchantPattern,
                expectedAmountMinor = pattern.expectedAmountMinor,
                intervalDays = pattern.intervalDays,
                occurrenceCount = pattern.occurrenceCount,
                lastSeenAt = pattern.lastSeenAt,
                nextExpectedAt = pattern.nextExpectedAt,
                isConfirmed = false,
                isDismissed = false,
                sourceType = "auto",
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )
        }
        if (rows.isNotEmpty()) {
            database.recurringPatternDao().upsertPatterns(rows)
        }
    }

    suspend fun setCreditCardDue(
        cardId: String,
        statementDueAmountMinor: Long?,
        statementDueDateIso: String?,
    ) {
        val card = database.creditCardDao().getCardById(cardId) ?: return
        val now = Instant.now().toString()
        database.creditCardDao().upsertCards(
            listOf(
                card.copy(
                    statementDueAmountMinor = statementDueAmountMinor,
                    statementDueDate = statementDueDateIso,
                    updatedAt = now,
                ),
            ),
        )
    }

    suspend fun addEmiPlan(
        name: String,
        monthlyAmountMinor: Long,
        remainingTenureMonths: Int? = null,
        nextDueAt: String? = null,
        linkedCreditCardId: String? = null,
        notes: String? = null,
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank() || monthlyAmountMinor <= 0L) return
        val now = Instant.now().toString()
        database.emiPlanDao().upsertPlan(
            EmiPlanEntity(
                id = "emi-${UUID.randomUUID()}",
                userId = USER_ID,
                name = cleanName,
                linkedCreditCardId = linkedCreditCardId,
                monthlyAmountMinor = monthlyAmountMinor,
                remainingTenureMonths = remainingTenureMonths,
                nextDueAt = nextDueAt,
                totalOutstandingMinor = remainingTenureMonths?.let { it.toLong() * monthlyAmountMinor },
                sourceType = "manual",
                isConfirmed = true,
                notes = notes?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY,
            ),
        )
    }

    suspend fun removeEmiPlan(id: String) {
        database.emiPlanDao().deletePlan(id)
    }

    fun observeCategories(): Flow<List<CategoryEntity>> = database.categoryDao().observeActiveCategories()

    fun observeBuckets(): Flow<List<BucketEntity>> = database.bucketDao().observeBuckets()

    /**
     * Recent canonical transactions for the Home screen. Date-windowed
     * (default 30 days back from [now]) rather than row-windowed because a
     * row cap silently hides any back-dated manual entry or any
     * notification with an old `deviceEventTime`. See gravedigging audit
     * H2 and PR 7.
     *
     * [limit] caps memory inside the window — generous default (200) since
     * the Home UI takes only 20 from the head. If the window has more rows
     * than [limit], the oldest get dropped, but at least the user sees a
     * coherent "last 30 days" slice rather than an arbitrary truncation.
     */
    fun observeRecentTransactions(
        now: java.time.LocalDate = java.time.LocalDate.now(),
        windowDays: Long = 30,
        limit: Int = 200,
    ): Flow<List<CanonicalTransactionEntity>> =
        database.canonicalTransactionDao().observeRecentTransactionsSince(
            userId = USER_ID,
            fromIso = now.minusDays(windowDays).toString(),
            limit = limit,
        )

    fun observeRecentTransactionCandidates(limit: Int = 20): Flow<List<TransactionCandidateEntity>> =
        database.transactionCandidateDao().observeRecentTransactionCandidates(limit)

    fun observePendingInboxItems(limit: Int = 20): Flow<List<InboxItemEntity>> =
        database.inboxItemDao().observeInboxItems(
            userId = USER_ID,
            state = InboxDecisionState.PENDING,
            limit = limit,
        )

    // Preferred entry point for the Home dashboard's inbox rendering. Joins
    // each pending inbox row to its candidate at the DB so the UI never has
    // to do an in-memory lookup against a separate, windowed candidate flow
    // (which was the source of the "husk row" bug — pending inbox items
    // rendered with no merchant / amount once the candidate window had
    // rotated past them). See docs/gravedigging-2026-05-18.md.
    fun observePendingInboxItemsWithCandidates(limit: Int = 20): Flow<List<InboxItemWithCandidate>> =
        database.inboxItemDao().observeInboxItemsWithCandidates(
            userId = USER_ID,
            state = InboxDecisionState.PENDING,
            limit = limit,
        )

    fun observeSuggestedTransactions(limit: Int = 50): Flow<List<CanonicalTransactionEntity>> =
        database.canonicalTransactionDao().observeSuggestedTransactions(USER_ID, limit)

    fun observeMonthlyTotalBudget(today: LocalDate = LocalDate.now()): Flow<BudgetEntity?> =
        database.budgetDao().observeMonthlyTotalBudgetForDate(
            userId = USER_ID,
            date = today.toString(),
        )

    fun observeSpentInPeriod(fromIso: String, untilIso: String): Flow<Long> =
        database.canonicalTransactionDao().observeSpentInPeriod(
            userId = USER_ID,
            fromIso = fromIso,
            untilIso = untilIso,
        )

    fun observeReceivedInPeriod(fromIso: String, untilIso: String): Flow<Long> =
        database.canonicalTransactionDao().observeReceivedInPeriod(
            userId = USER_ID,
            fromIso = fromIso,
            untilIso = untilIso,
        )

    fun observeTransactionsInPeriod(fromIso: String, untilIso: String): Flow<List<CanonicalTransactionEntity>> =
        database.canonicalTransactionDao().observeTransactionsInPeriod(
            userId = USER_ID,
            fromIso = fromIso,
            untilIso = untilIso,
        )

    suspend fun ensureBaseData() {
        if (database.userDao().countUsers() > 0) return

        val now = Instant.now().toString()
        val userId = USER_ID

        database.userDao().upsertUser(
            UserEntity(
                id = userId,
                displayName = "Cyril",
                defaultCurrencyCode = "INR",
                countryCode = "IN",
                timezone = "Asia/Kolkata",
                weekStartDay = 1,
                monthStartDay = 1,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY,
            ),
        )

        database.categoryDao().upsertCategories(
            listOf(
                "Food",
                "Groceries",
                "Shopping",
                "Travel",
                "Bills",
                "Family",
                "Health",
                "Entertainment",
                "Subscriptions",
                "EMIs",
                "Cash",
            ).mapIndexed { index, name ->
                CategoryEntity(
                    id = "category-${name.lowercase()}",
                    userId = userId,
                    name = name,
                    isDefault = true,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                )
            },
        )

        database.bucketDao().upsertBuckets(
            listOf(
                "Wants",
                "Subscriptions",
                "Food Out",
                "Family",
                "EMIs",
                "Travel",
                "Essentials",
            ).mapIndexed { index, name ->
                BucketEntity(
                    id = "bucket-${name.lowercase().replace(" ", "-")}",
                    userId = userId,
                    name = name,
                    isDefault = true,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                )
            },
        )

        ensureMonthlyBudgetForToday()
    }

    suspend fun ensureMonthlyBudgetForToday(today: LocalDate = LocalDate.now()) {
        val isoDate = today.toString()
        val existing = database.budgetDao().getMonthlyTotalBudgetForDate(USER_ID, isoDate)
        if (existing != null) return

        val carryForwardLimit = database.budgetDao()
            .getLatestMonthlyTotalBudget(USER_ID)
            ?.limitMinor
            ?: DEFAULT_MONTHLY_BUDGET_MINOR

        val now = Instant.now().toString()
        val month = YearMonth.from(today)
        database.budgetDao().upsertBudgets(
            listOf(
                BudgetEntity(
                    id = "budget-monthly-total-$month",
                    userId = USER_ID,
                    budgetType = BudgetType.MONTHLY_TOTAL,
                    limitMinor = carryForwardLimit,
                    currencyCode = "INR",
                    periodStart = month.atDay(1).toString(),
                    periodEnd = month.atEndOfMonth().toString(),
                    alertThresholdPercent = 0.8,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            ),
        )
    }

    suspend fun completeInitialSetup(input: OnboardingSetupInput) {
        ensureBaseData()

        val now = Instant.now().toString()
        val userId = USER_ID

        if (input.bankAccountName.isNotBlank()) {
            val existingBanks = database.accountDao().countAccountsByType(userId, AccountType.BANK)
            database.accountDao().upsertAccounts(
                listOf(
                    AccountEntity(
                        id = "account-bank-${existingBanks + 1}",
                        userId = userId,
                        accountType = AccountType.BANK,
                        displayName = input.bankAccountName.trim(),
                        providerName = input.bankProviderName.trim().ifBlank { null },
                        currencyCode = "INR",
                        sortOrder = existingBanks,
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                    ),
                ),
            )
        }

        if (input.creditCardName.isNotBlank()) {
            val existingCards = database.creditCardDao().countCards(userId)
            database.creditCardDao().upsertCards(
                listOf(
                    CreditCardEntity(
                        id = "card-${existingCards + 1}",
                        userId = userId,
                        displayName = input.creditCardName.trim(),
                        providerName = input.creditCardProviderName.trim().ifBlank { null },
                        sortOrder = existingCards,
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                    ),
                ),
            )
        }

        if (input.cashAccountEnabled) {
            val existingCash = database.accountDao().countAccountsByType(userId, AccountType.CASH)
            if (existingCash == 0) {
                database.accountDao().upsertAccounts(
                    listOf(
                        AccountEntity(
                            id = "account-cash",
                            userId = userId,
                            accountType = AccountType.CASH,
                            displayName = "Cash on hand",
                            currencyCode = "INR",
                            currentBalanceMinor = input.cashBalanceMinor,
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = SyncStatus.LOCAL_ONLY,
                        ),
                    ),
                )
            }
        }
    }

    fun observeMerchantTrustRules(): Flow<List<MerchantTrustRuleEntity>> =
        database.merchantTrustRuleDao().observeRules(USER_ID)

    suspend fun addMerchantTrustRule(merchantPattern: String, autoCategoryId: String? = null) {
        // Store the cleaned merchant form so future ingestion — which matches against the
        // cleaned form too — actually fires. Storing raw "Swiggy using UPI" would never
        // match clean("Swiggy using UPI") = "Swiggy".
        val pattern = MerchantNameUtils.clean(merchantPattern).takeIf { it != "Unnamed" }
            ?: merchantPattern.trim()
        if (pattern.isBlank()) return
        val now = Instant.now().toString()
        database.merchantTrustRuleDao().upsertRule(
            MerchantTrustRuleEntity(
                id = "trust-${UUID.randomUUID()}",
                userId = USER_ID,
                merchantPattern = pattern,
                autoCategoryId = autoCategoryId,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY,
            ),
        )
    }

    suspend fun removeMerchantTrustRule(id: String) {
        database.merchantTrustRuleDao().deleteRule(id)
    }

    suspend fun getMerchantTrustRulesSnapshot(): List<MerchantTrustRuleEntity> =
        database.merchantTrustRuleDao().getRulesForUser(USER_ID)

    suspend fun confirmInboxItem(
        inboxItemId: String,
        merchantNameOverride: String? = null,
        amountMinorOverride: Long? = null,
        categoryIdOverride: String? = null,
        addTrustRule: Boolean = false,
    ) {
        val now = Instant.now().toString()
        var resolvedMerchantForTrust: String? = null
        var resolvedCategoryForTrust: String? = null

        database.withTransaction {
            val inboxItem = database.inboxItemDao().getInboxItemById(inboxItemId) ?: return@withTransaction
            val candidate = database.transactionCandidateDao()
                .getTransactionCandidateById(inboxItem.transactionCandidateId) ?: return@withTransaction

            val existingCanonical = candidate.linkedCanonicalTransactionId?.let { linkedCanonicalId ->
                database.canonicalTransactionDao().getTransactionById(linkedCanonicalId)
            }

            // "txn-<candidateId>" is just a deterministic id derived from the
            // candidate so a confirm-then-edit-then-re-confirm flow lands on
            // the same canonical row instead of creating duplicates. Used to
            // be load-bearing for DumpOutcomeDao's merge detection, but that
            // dependency moved to an explicit inbox_items column in v10 —
            // see InboxItemEntity.mergedFromExistingCanonicalId.
            val canonicalId = existingCanonical?.id ?: "txn-${candidate.id}"
            val resolvedMerchant = merchantNameOverride?.trim()?.ifBlank { null }
                ?: existingCanonical?.merchantName ?: candidate.toEntityName
            val resolvedAmount = amountMinorOverride
                ?: candidate.amountMinor
                ?: existingCanonical?.amountMinor
                ?: 0L
            val resolvedCategory = categoryIdOverride ?: existingCanonical?.categoryId

            val canonical = (existingCanonical ?: CanonicalTransactionEntity(
                id = canonicalId,
                userId = USER_ID,
                type = candidate.toCanonicalType(),
                status = CanonicalTransactionStatus.CONFIRMED,
                amountMinor = resolvedAmount,
                currencyCode = candidate.currencyCode ?: "INR",
                merchantName = resolvedMerchant,
                categoryId = resolvedCategory,
                mode = candidate.mode ?: Mode.OTHER,
                occurredAt = candidate.occurredAt ?: inboxItem.createdAt,
                sourceSummary = "Confirmed from Inbox review",
                createdBy = "user_review",
                confidenceTier = candidate.confidenceTier ?: ConfidenceTier.MEDIUM,
                dedupeFingerprint = candidate.candidateFingerprint,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )).copy(
                merchantName = resolvedMerchant,
                amountMinor = resolvedAmount,
                categoryId = resolvedCategory,
                currencyCode = candidate.currencyCode ?: existingCanonical?.currencyCode ?: "INR",
                mode = candidate.mode ?: existingCanonical?.mode ?: Mode.OTHER,
                occurredAt = candidate.occurredAt ?: existingCanonical?.occurredAt ?: inboxItem.createdAt,
                status = CanonicalTransactionStatus.CONFIRMED,
                updatedAt = now,
            )

            database.canonicalTransactionDao().upsertTransactions(listOf(canonical))
            database.transactionCandidateDao().upsertTransactionCandidate(
                candidate.copy(
                    decisionState = CandidateDecisionState.USER_CONFIRMED,
                    linkedCanonicalTransactionId = canonical.id,
                    updatedAt = now,
                ),
            )
            database.inboxItemDao().upsertInboxItem(
                inboxItem.copy(
                    decisionState = InboxDecisionState.CONFIRMED,
                    linkedCanonicalTransactionId = canonical.id,
                    resolvedAt = now,
                    updatedAt = now,
                ),
            )

            resolvedMerchantForTrust = resolvedMerchant
            resolvedCategoryForTrust = resolvedCategory
        }

        if (addTrustRule) {
            resolvedMerchantForTrust?.let { addMerchantTrustRule(it, resolvedCategoryForTrust) }
        }
    }

    suspend fun confirmInboxItemMergedWith(inboxItemId: String, existingTransactionId: String) {
        val now = Instant.now().toString()
        database.withTransaction {
            val inboxItem = database.inboxItemDao().getInboxItemById(inboxItemId) ?: return@withTransaction
            val candidate = database.transactionCandidateDao()
                .getTransactionCandidateById(inboxItem.transactionCandidateId) ?: return@withTransaction
            database.inboxItemDao().upsertInboxItem(
                inboxItem.copy(
                    decisionState = InboxDecisionState.CONFIRMED,
                    linkedCanonicalTransactionId = existingTransactionId,
                    // Distinguishes a merge from a fresh confirm.
                    // DumpOutcomeDao surfaces this directly instead of
                    // reverse-engineering the merge state from id naming.
                    mergedFromExistingCanonicalId = existingTransactionId,
                    resolvedAt = now,
                    updatedAt = now,
                ),
            )
            database.transactionCandidateDao().upsertTransactionCandidate(
                candidate.copy(
                    decisionState = CandidateDecisionState.USER_CONFIRMED,
                    linkedCanonicalTransactionId = existingTransactionId,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun confirmSuggestedTransaction(
        transactionId: String,
        merchantNameOverride: String? = null,
        amountMinorOverride: Long? = null,
        categoryIdOverride: String? = null,
        addTrustRule: Boolean = false,
    ) {
        val now = Instant.now().toString()
        val txn = database.canonicalTransactionDao().getTransactionById(transactionId) ?: return
        val resolvedMerchant = merchantNameOverride?.trim()?.ifBlank { null } ?: txn.merchantName
        val resolvedAmount = amountMinorOverride ?: txn.amountMinor
        val resolvedCategory = categoryIdOverride ?: txn.categoryId
        database.canonicalTransactionDao().upsertTransactions(
            listOf(
                txn.copy(
                    status = CanonicalTransactionStatus.CONFIRMED,
                    merchantName = resolvedMerchant,
                    amountMinor = resolvedAmount,
                    categoryId = resolvedCategory,
                    updatedAt = now,
                ),
            ),
        )
        if (addTrustRule && resolvedMerchant != null) {
            addMerchantTrustRule(resolvedMerchant, resolvedCategory)
        }
    }

    suspend fun dismissSuggestedTransaction(transactionId: String) {
        deleteTransaction(transactionId)
    }

    suspend fun deleteTransaction(transactionId: String) {
        val now = Instant.now().toString()
        val txn = database.canonicalTransactionDao().getTransactionById(transactionId) ?: return
        database.canonicalTransactionDao().upsertTransactions(
            listOf(txn.copy(status = CanonicalTransactionStatus.IGNORED, updatedAt = now)),
        )
    }

    suspend fun dismissInboxItem(inboxItemId: String) {
        val now = Instant.now().toString()
        val inboxItem = database.inboxItemDao().getInboxItemById(inboxItemId) ?: return
        val candidate = database.transactionCandidateDao()
            .getTransactionCandidateById(inboxItem.transactionCandidateId) ?: return

        database.transactionCandidateDao().upsertTransactionCandidate(
            candidate.copy(
                decisionState = CandidateDecisionState.IGNORED,
                updatedAt = now,
            ),
        )
        database.inboxItemDao().upsertInboxItem(
            inboxItem.copy(
                decisionState = InboxDecisionState.DISMISSED,
                resolvedAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateTransactionDetails(
        transactionId: String,
        merchantName: String,
        notes: String,
        categoryId: String? = null,
        applyCategory: Boolean = false,
        // When non-null, flip EXPENSE ↔ INCOME. Used by the Transactions tab edit
        // form to correct mis-classified P2P inflows ("John sent you ₹10" landing
        // as EXPENSE) without forcing the user to delete + re-create manually.
        type: CanonicalTransactionType? = null,
    ) {
        val now = Instant.now().toString()
        val transaction = database.canonicalTransactionDao().getTransactionById(transactionId) ?: return
        database.canonicalTransactionDao().upsertTransactions(
            listOf(
                transaction.copy(
                    merchantName = merchantName.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    categoryId = if (applyCategory) categoryId else transaction.categoryId,
                    type = type ?: transaction.type,
                    updatedAt = now,
                ),
            ),
        )
    }

    suspend fun createManualTransaction(
        merchantName: String,
        amountMinor: Long,
        mode: Mode,
        categoryId: String?,
        notes: String?,
        occurredAt: Instant = Instant.now(),
        type: CanonicalTransactionType = CanonicalTransactionType.EXPENSE,
    ) {
        val now = Instant.now().toString()
        val txnId = "txn-manual-${UUID.randomUUID()}"
        database.canonicalTransactionDao().upsertTransactions(
            listOf(
                CanonicalTransactionEntity(
                    id = txnId,
                    userId = USER_ID,
                    type = type,
                    status = CanonicalTransactionStatus.CONFIRMED,
                    amountMinor = amountMinor,
                    currencyCode = "INR",
                    merchantName = merchantName.trim().ifBlank { null },
                    categoryId = categoryId,
                    mode = mode,
                    notes = notes?.trim()?.ifBlank { null },
                    occurredAt = occurredAt.toString(),
                    sourceSummary = "Manual entry",
                    createdBy = "manual",
                    confidenceTier = ConfidenceTier.HIGH,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            ),
        )
    }

    suspend fun updateUserDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val user = database.userDao().getUserOnce(USER_ID) ?: return
        val now = Instant.now().toString()
        database.userDao().upsertUser(user.copy(displayName = trimmed, updatedAt = now))
    }

    suspend fun setMonthlyBudgetLimit(limitMinor: Long, today: LocalDate = LocalDate.now()) {
        if (limitMinor <= 0L) return
        ensureMonthlyBudgetForToday(today)
        val isoDate = today.toString()
        val existing = database.budgetDao().getMonthlyTotalBudgetForDate(USER_ID, isoDate) ?: return
        val now = Instant.now().toString()
        database.budgetDao().upsertBudgets(
            listOf(existing.copy(limitMinor = limitMinor, updatedAt = now)),
        )
    }

    fun observeCategoryBudgets(today: LocalDate = LocalDate.now()): Flow<List<BudgetEntity>> =
        database.budgetDao().observeCategoryBudgetsForDate(USER_ID, today.toString())

    fun observeSpentByCategory(fromIso: String, untilIso: String): Flow<List<CategorySpend>> =
        database.canonicalTransactionDao().observeSpentByCategoryInPeriod(
            userId = USER_ID,
            fromIso = fromIso,
            untilIso = untilIso,
        )

    suspend fun setCategoryBudgetLimit(
        categoryId: String,
        limitMinor: Long,
        today: LocalDate = LocalDate.now(),
    ) {
        if (categoryId.isBlank()) return
        val month = YearMonth.from(today)
        val isoDate = today.toString()
        val existing = database.budgetDao().getCategoryBudgetForDate(USER_ID, categoryId, isoDate)
        val now = Instant.now().toString()
        if (limitMinor <= 0L) {
            // Clear by deactivating any existing active row.
            if (existing != null) {
                database.budgetDao().upsertBudgets(
                    listOf(existing.copy(isActive = false, updatedAt = now)),
                )
            }
            return
        }
        val budget = existing?.copy(
            limitMinor = limitMinor,
            isActive = true,
            updatedAt = now,
        ) ?: BudgetEntity(
            id = "budget-cat-$categoryId-$month",
            userId = USER_ID,
            budgetType = BudgetType.CATEGORY,
            targetRefId = categoryId,
            limitMinor = limitMinor,
            currencyCode = "INR",
            periodStart = month.atDay(1).toString(),
            periodEnd = month.atEndOfMonth().toString(),
            alertThresholdPercent = 0.8,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )
        database.budgetDao().upsertBudgets(listOf(budget))
    }

    suspend fun getBudgetSnapshot(today: LocalDate = LocalDate.now()) =
        database.budgetDao().getMonthlyTotalBudgetForDate(USER_ID, today.toString())

    suspend fun getSpentSnapshot(today: LocalDate = LocalDate.now()): Long {
        val month = YearMonth.from(today)
        return database.canonicalTransactionDao().getSpentInPeriod(
            userId = USER_ID,
            fromIso = month.atDay(1).toString(),
            untilIso = month.plusMonths(1).atDay(1).toString(),
        )
    }

    suspend fun getCreditCardsSnapshot(): List<CreditCardEntity> = withContext(Dispatchers.IO) {
        database.creditCardDao().observeActiveCards().first()
    }

    suspend fun getEmiPlansSnapshot(): List<EmiPlanEntity> = withContext(Dispatchers.IO) {
        database.emiPlanDao().observePlans(USER_ID).first()
    }

    suspend fun getRecurringPatternsSnapshot(): List<RecurringPatternEntity> = withContext(Dispatchers.IO) {
        database.recurringPatternDao().observePatterns(USER_ID).first()
    }

    // Phase 2a of dump enrichment. Caller (DebugViewModel) passes the
    // rawEventId list parsed out of the live dump file; we join across
    // parsed_signals → transaction_candidates → inbox_items → canonical_transactions
    // to produce a "current state at export time" snapshot per id.
    // Chunked because SQLite's older parameter cap is 999 — dumps top out at
    // ~2.5k entries today; 500 leaves headroom for the rest of the WHERE clause.
    suspend fun getDumpOutcomeSnapshot(
        rawEventIds: List<String>,
    ): List<DumpOutcomeSnapshot> = withContext(Dispatchers.IO) {
        if (rawEventIds.isEmpty()) return@withContext emptyList()
        rawEventIds.chunked(500).flatMap { chunk ->
            database.dumpOutcomeDao().getDumpOutcomeSnapshot(chunk)
        }
    }

    /**
     * Read the persisted debounce timestamps from `app_state` and seed the
     * in-memory AtomicLongs. RupeeApplication calls this once on cold start
     * before anything that could trigger a refresh/prune fires. If the
     * persisted values are absent (fresh install, pre-v11 user), the
     * AtomicLongs stay at 0 and the next refresh/prune runs immediately —
     * desired behaviour, just no debounce on first ever run.
     */
    suspend fun hydrateDebounceState() = withContext(Dispatchers.IO) {
        val dao = database.appStateDao()
        dao.get(KEY_LAST_RECURRING_REFRESH_MS)?.toLongOrNull()?.let(lastRecurringRefreshMs::set)
        dao.get(KEY_LAST_INGESTION_PRUNE_MS)?.toLongOrNull()?.let(lastIngestionPruneMs::set)
    }

    /**
     * Write a debounce timestamp through to `app_state`. Fire-and-forget on
     * a separate coroutine so the caller (refreshRecurringPatterns,
     * pruneStaleIngestionRows) doesn't have to wait for the disk write.
     * The in-memory AtomicLong is the source of truth during the process
     * lifetime; the DB write is the cold-start hand-off.
     */
    private fun persistDebounceTimestamp(key: String, valueMs: Long) {
        val nowIso = Instant.now().toString()
        // Fire-and-forget write — caller doesn't wait on it. Failure to
        // persist costs at most one extra refresh/prune next cold start.
        // Dispatched on persistScope (app-lifetime SupervisorJob in
        // production; an isolated SupervisorJob in tests / debug callers).
        persistScope.launch {
            runCatching {
                database.appStateDao().upsert(
                    com.zegrt.rupee.data.local.entity.AppStateEntity(
                        key = key,
                        value = valueMs.toString(),
                        updatedAt = nowIso,
                    ),
                )
            }
        }
    }

    /**
     * Delete raw_capture_events older than [retentionDays] days. FK cascade
     * sweeps up parsed_signals → transaction_candidates → inbox_items;
     * canonical_transactions are preserved by the SET NULL FKs added in H4.
     *
     * Debounced to once per 24 hours so a user bouncing the app doesn't
     * thrash the disk. Pass [force] = true to bypass the debounce (used by
     * tests and explicit debug actions). Returns the count of raw events
     * deleted, useful for diagnostics.
     */
    suspend fun pruneStaleIngestionRows(
        now: Instant = Instant.now(),
        retentionDays: Long = DEFAULT_RAW_EVENT_RETENTION_DAYS,
        force: Boolean = false,
    ): Int {
        val currentMs = now.toEpochMilli()
        if (!force && currentMs - lastIngestionPruneMs.get() < INGESTION_PRUNE_DEBOUNCE_MS) {
            return 0
        }
        lastIngestionPruneMs.set(currentMs)
        persistDebounceTimestamp(KEY_LAST_INGESTION_PRUNE_MS, currentMs)

        val cutoffIso = now.minusSeconds(retentionDays * 24 * 60 * 60).toString()
        return withContext(Dispatchers.IO) {
            database.rawCaptureEventDao().deleteOlderThan(cutoffIso)
        }
    }

    suspend fun wipeRawCaptureData() {
        // Drop only the raw notification bodies, not the derived transactions / candidates.
        // Future ingestion will rebuild fingerprints from new events.
        withContext(Dispatchers.IO) {
            database.rawCaptureEventDao().deleteAll()
        }
    }

    suspend fun resetAllData() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        ensureBaseData()
    }

    suspend fun debugIngestNotification(
        packageName: String,
        title: String?,
        body: String,
    ) {
        NotificationSignalNormalizer(database).ingestNotification(
            userId = USER_ID,
            packageName = packageName,
            title = title,
            body = body,
            postedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun TransactionCandidateEntity.toCanonicalType(): CanonicalTransactionType = when (candidateType) {
        TransactionCandidateType.TRANSFER -> CanonicalTransactionType.TRANSFER
        TransactionCandidateType.CASH_WITHDRAWAL -> CanonicalTransactionType.CASH_ADJUSTMENT
        TransactionCandidateType.INCOME -> CanonicalTransactionType.INCOME
        else -> CanonicalTransactionType.EXPENSE
    }
}
