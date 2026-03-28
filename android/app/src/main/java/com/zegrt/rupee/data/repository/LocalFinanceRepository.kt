package com.zegrt.rupee.data.repository

import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.BucketEntity
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.BudgetType
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import java.time.Instant
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
    fun observeUser(): Flow<UserEntity?> = database.userDao().observeUser()

    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeActiveAccounts()

    fun observeCards(): Flow<List<CreditCardEntity>> = database.creditCardDao().observeActiveCards()

    fun observeCategories(): Flow<List<CategoryEntity>> = database.categoryDao().observeActiveCategories()

    fun observeBuckets(): Flow<List<BucketEntity>> = database.bucketDao().observeBuckets()

    fun observeRecentTransactions(limit: Int = 20): Flow<List<CanonicalTransactionEntity>> =
        database.canonicalTransactionDao().observeRecentTransactions(limit)

    fun observeRecentTransactionCandidates(limit: Int = 20): Flow<List<TransactionCandidateEntity>> =
        database.transactionCandidateDao().observeRecentTransactionCandidates(limit)

    fun observePendingInboxItems(limit: Int = 20): Flow<List<InboxItemEntity>> =
        database.inboxItemDao().observeInboxItems(
            state = InboxDecisionState.PENDING,
            limit = limit,
        )

    suspend fun ensureBaseData() {
        if (database.userDao().countUsers() > 0) return

        val now = Instant.now().toString()
        val userId = "local-user"

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

        database.budgetDao().upsertBudgets(
            listOf(
                BudgetEntity(
                    id = "budget-monthly-total",
                    userId = userId,
                    budgetType = BudgetType.MONTHLY_TOTAL,
                    limitMinor = 400000,
                    currencyCode = "INR",
                    periodStart = "2026-03-01",
                    periodEnd = "2026-03-31",
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
        val userId = "local-user"

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
}
