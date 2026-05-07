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
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

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

        val thisMonth = YearMonth.now()
        database.budgetDao().upsertBudgets(
            listOf(
                BudgetEntity(
                    id = "budget-monthly-total-${thisMonth}",
                    userId = userId,
                    budgetType = BudgetType.MONTHLY_TOTAL,
                    limitMinor = 4_000_000,
                    currencyCode = "INR",
                    periodStart = thisMonth.atDay(1).toString(),
                    periodEnd = thisMonth.atEndOfMonth().toString(),
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

    suspend fun confirmInboxItem(
        inboxItemId: String,
        merchantNameOverride: String? = null,
    ) {
        val now = Instant.now().toString()
        val inboxItem = database.inboxItemDao().getInboxItemById(inboxItemId) ?: return
        val candidate = database.transactionCandidateDao()
            .getTransactionCandidateById(inboxItem.transactionCandidateId) ?: return

        val existingCanonical = candidate.linkedCanonicalTransactionId?.let { linkedCanonicalId ->
            database.canonicalTransactionDao().getTransactionById(linkedCanonicalId)
        }

        val canonicalId = existingCanonical?.id ?: "txn-${candidate.id}"
        val canonical = (existingCanonical ?: CanonicalTransactionEntity(
            id = canonicalId,
            userId = USER_ID,
            type = candidate.toCanonicalType(),
            status = CanonicalTransactionStatus.CONFIRMED,
            amountMinor = candidate.amountMinor ?: 0L,
            currencyCode = candidate.currencyCode ?: "INR",
            merchantName = candidate.toEntityName,
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
            merchantName = merchantNameOverride?.trim()?.ifBlank { null } ?: existingCanonical?.merchantName ?: candidate.toEntityName,
            amountMinor = candidate.amountMinor ?: existingCanonical?.amountMinor ?: 0L,
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
    ) {
        val now = Instant.now().toString()
        val transaction = database.canonicalTransactionDao().getTransactionById(transactionId) ?: return
        database.canonicalTransactionDao().upsertTransactions(
            listOf(
                transaction.copy(
                    merchantName = merchantName.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun TransactionCandidateEntity.toCanonicalType(): CanonicalTransactionType = when (candidateType) {
        com.zegrt.rupee.data.local.entity.TransactionCandidateType.TRANSFER -> CanonicalTransactionType.TRANSFER
        com.zegrt.rupee.data.local.entity.TransactionCandidateType.CASH_WITHDRAWAL -> CanonicalTransactionType.CASH_ADJUSTMENT
        else -> CanonicalTransactionType.EXPENSE
    }
}
