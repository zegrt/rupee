package com.zegrt.rupee.data.repository

import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.BudgetType
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.BucketEntity
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

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

    suspend fun seedDefaultsIfEmpty() {
        if (database.userDao().countUsers() > 0) return

        val now = Instant.now().toString()
        val userId = "local-user"

        database.userDao().upsertUser(
            UserEntity(
                id = userId,
                email = null,
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

        database.accountDao().upsertAccounts(
            listOf(
                AccountEntity(
                    id = "account-cash",
                    userId = userId,
                    accountType = AccountType.CASH,
                    displayName = "Cash on hand",
                    currencyCode = "INR",
                    currentBalanceMinor = 5000,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            ),
        )

        database.creditCardDao().upsertCards(
            listOf(
                CreditCardEntity(
                    id = "card-icici",
                    userId = userId,
                    displayName = "ICICI Card",
                    providerName = "ICICI",
                    maskedIdentifier = "1234",
                    statementDueAmountMinor = 184500,
                    statementDueDate = "2026-04-05",
                    currentOutstandingMinor = 184500,
                    availableLimitMinor = 315500,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            ),
        )

        database.canonicalTransactionDao().upsertTransactions(
            listOf(
                CanonicalTransactionEntity(
                    id = "txn-zomato-1",
                    userId = userId,
                    type = CanonicalTransactionType.EXPENSE,
                    status = CanonicalTransactionStatus.CONFIRMED,
                    amountMinor = 42000,
                    currencyCode = "INR",
                    accountId = "account-cash",
                    merchantName = "Zomato",
                    categoryId = "category-food",
                    mode = Mode.UPI,
                    sourceSummary = "Seed data",
                    createdBy = "seed",
                    confidenceTier = ConfidenceTier.HIGH,
                    occurredAt = "2026-03-27T18:45:00Z",
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
                CanonicalTransactionEntity(
                    id = "txn-emi-1",
                    userId = userId,
                    type = CanonicalTransactionType.EXPENSE,
                    status = CanonicalTransactionStatus.CONFIRMED,
                    amountMinor = 125000,
                    currencyCode = "INR",
                    creditCardId = "card-icici",
                    merchantName = "Laptop EMI",
                    categoryId = "category-emis",
                    mode = Mode.CREDIT_CARD,
                    sourceSummary = "Seed data",
                    createdBy = "seed",
                    confidenceTier = ConfidenceTier.HIGH,
                    occurredAt = "2026-03-26T10:00:00Z",
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            ),
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
}
