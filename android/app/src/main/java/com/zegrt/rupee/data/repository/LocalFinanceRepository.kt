package com.zegrt.rupee.data.repository

import com.zegrt.rupee.data.local.RupeeDatabase
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
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.MerchantTrustRuleEntity
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import com.zegrt.rupee.ingestion.NotificationSignalNormalizer
import com.zegrt.rupee.ingestion.RawCaptureWriter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
) {
    companion object {
        private const val USER_ID = "local-user"
        private const val DEFAULT_MONTHLY_BUDGET_MINOR = 4_000_000L
    }

    fun observeUser(): Flow<UserEntity?> = database.userDao().observeUser()

    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeActiveAccounts()

    fun observeCards(): Flow<List<CreditCardEntity>> = database.creditCardDao().observeActiveCards()

    fun observeCategories(): Flow<List<CategoryEntity>> = database.categoryDao().observeActiveCategories()

    fun observeBuckets(): Flow<List<BucketEntity>> = database.bucketDao().observeBuckets()

    fun observeRecentTransactions(limit: Int = 20): Flow<List<CanonicalTransactionEntity>> =
        database.canonicalTransactionDao().observeRecentTransactions(USER_ID, limit)

    fun observeRecentTransactionCandidates(limit: Int = 20): Flow<List<TransactionCandidateEntity>> =
        database.transactionCandidateDao().observeRecentTransactionCandidates(limit)

    fun observePendingInboxItems(limit: Int = 20): Flow<List<InboxItemEntity>> =
        database.inboxItemDao().observeInboxItems(
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
        val pattern = merchantPattern.trim()
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
        val inboxItem = database.inboxItemDao().getInboxItemById(inboxItemId) ?: return
        val candidate = database.transactionCandidateDao()
            .getTransactionCandidateById(inboxItem.transactionCandidateId) ?: return

        val existingCanonical = candidate.linkedCanonicalTransactionId?.let { linkedCanonicalId ->
            database.canonicalTransactionDao().getTransactionById(linkedCanonicalId)
        }

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
        if (addTrustRule && resolvedMerchant != null) {
            addMerchantTrustRule(resolvedMerchant, resolvedCategory)
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
    ) {
        val now = Instant.now().toString()
        val transaction = database.canonicalTransactionDao().getTransactionById(transactionId) ?: return
        database.canonicalTransactionDao().upsertTransactions(
            listOf(
                transaction.copy(
                    merchantName = merchantName.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    categoryId = if (applyCategory) categoryId else transaction.categoryId,
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
    ) {
        val now = Instant.now().toString()
        val txnId = "txn-manual-${UUID.randomUUID()}"
        database.canonicalTransactionDao().upsertTransactions(
            listOf(
                CanonicalTransactionEntity(
                    id = txnId,
                    userId = USER_ID,
                    type = CanonicalTransactionType.EXPENSE,
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

    suspend fun resetAllData() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        ensureBaseData()
    }

    suspend fun debugIngestNotification(
        packageName: String,
        title: String?,
        body: String,
    ) {
        val writer = RawCaptureWriter(database)
        val normalizer = NotificationSignalNormalizer(database)
        val rawEvent = writer.storeNotificationEvent(
            packageName = packageName,
            title = title,
            body = body,
            postedAtMillis = System.currentTimeMillis(),
        ) ?: return
        normalizer.normalize(rawEvent)
    }

    private fun TransactionCandidateEntity.toCanonicalType(): CanonicalTransactionType = when (candidateType) {
        com.zegrt.rupee.data.local.entity.TransactionCandidateType.TRANSFER -> CanonicalTransactionType.TRANSFER
        com.zegrt.rupee.data.local.entity.TransactionCandidateType.CASH_WITHDRAWAL -> CanonicalTransactionType.CASH_ADJUSTMENT
        else -> CanonicalTransactionType.EXPENSE
    }
}
