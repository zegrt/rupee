package com.zegrt.rupee.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.BucketEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeTransactionRow(
    val headline: String,
    val subline: String,
    val amountLabel: String,
)

data class HomeCandidateRow(
    val headline: String,
    val subline: String,
    val decisionLabel: String,
)

data class HomeUiState(
    val userName: String = "Rupee",
    val accountCount: Int = 0,
    val cardCount: Int = 0,
    val categoryCount: Int = 0,
    val bucketCount: Int = 0,
    val recentTransactionCount: Int = 0,
    val pendingInboxCount: Int = 0,
    val recentCandidateCount: Int = 0,
    val recentTransactions: List<HomeTransactionRow> = emptyList(),
    val recentCandidates: List<HomeCandidateRow> = emptyList(),
    val isSeeding: Boolean = true,
)

class HomeViewModel(
    private val repository: LocalFinanceRepository,
) : ViewModel() {
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        currency = java.util.Currency.getInstance("INR")
    }
    private val isSeeding = MutableStateFlow(true)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeUser(),
        repository.observeAccounts(),
        repository.observeCards(),
        repository.observeCategories(),
        repository.observeBuckets(),
        repository.observeRecentTransactions(),
        repository.observeRecentTransactionCandidates(),
        repository.observePendingInboxItems(),
        isSeeding,
    ) { values ->
        val user = values[0] as UserEntity?
        val accounts = values[1] as List<AccountEntity>
        val cards = values[2] as List<CreditCardEntity>
        val categories = values[3] as List<CategoryEntity>
        val buckets = values[4] as List<BucketEntity>
        val transactions = values[5] as List<CanonicalTransactionEntity>
        val candidates = values[6] as List<TransactionCandidateEntity>
        val inboxItems = values[7] as List<InboxItemEntity>
        val seeding = values[8] as Boolean

        HomeUiState(
            userName = user?.displayName ?: "Rupee",
            accountCount = accounts.size,
            cardCount = cards.size,
            categoryCount = categories.size,
            bucketCount = buckets.size,
            recentTransactionCount = transactions.size,
            pendingInboxCount = inboxItems.size,
            recentCandidateCount = candidates.size,
            recentTransactions = transactions.take(5).map { transaction ->
                HomeTransactionRow(
                    headline = transaction.merchantName ?: "Unnamed transaction",
                    subline = listOfNotNull(
                        transaction.mode?.name?.replace('_', ' '),
                        transaction.sourceSummary,
                    ).joinToString(" • ").ifBlank { transaction.occurredAt },
                    amountLabel = formatMinor(transaction.amountMinor),
                )
            },
            recentCandidates = candidates.take(5).map { candidate ->
                HomeCandidateRow(
                    headline = candidate.toEntityName ?: "Unresolved candidate",
                    subline = listOfNotNull(
                        candidate.mode?.name?.replace('_', ' '),
                        candidate.amountMinor?.let(::formatMinor),
                    ).joinToString(" • ").ifBlank { candidate.occurredAt ?: "No event time" },
                    decisionLabel = "${candidate.decisionState.name} / ${candidate.decisionReason.name}",
                )
            },
            isSeeding = seeding,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            repository.ensureBaseData()
            isSeeding.value = false
        }
    }

    private fun formatMinor(value: Long): String = currencyFormatter.format(value / 100.0)
}

class HomeViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return HomeViewModel(repository) as T
    }
}
