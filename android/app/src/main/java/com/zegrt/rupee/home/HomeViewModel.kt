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

enum class HomeTab {
    HOME,
    INBOX,
    TRANSACTIONS,
}

data class HomeInboxRow(
    val id: String,
    val headline: String,
    val subline: String,
    val reasonLabel: String,
    val merchantDraft: String,
)

data class HomeTransactionRow(
    val id: String,
    val headline: String,
    val subline: String,
    val amountLabel: String,
    val notes: String,
    val merchantDraft: String,
    val notesDraft: String,
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
    val selectedTab: HomeTab = HomeTab.HOME,
    val selectedInboxItemId: String? = null,
    val selectedTransactionId: String? = null,
    val inboxItems: List<HomeInboxRow> = emptyList(),
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
    private val selectedTab = MutableStateFlow(HomeTab.HOME)
    private val selectedInboxItemId = MutableStateFlow<String?>(null)
    private val selectedTransactionId = MutableStateFlow<String?>(null)
    private val inboxMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionNotesDrafts = MutableStateFlow<Map<String, String>>(emptyMap())

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
        selectedTab,
        selectedInboxItemId,
        selectedTransactionId,
        inboxMerchantDrafts,
        transactionMerchantDrafts,
        transactionNotesDrafts,
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
        val tab = values[9] as HomeTab
        val activeInboxItemId = values[10] as String?
        val activeTransactionId = values[11] as String?
        val inboxDrafts = values[12] as Map<String, String>
        val transactionMerchantDraftMap = values[13] as Map<String, String>
        val transactionNotesDraftMap = values[14] as Map<String, String>

        val candidateById = candidates.associateBy { it.id }
        val inboxRows = inboxItems.map { inboxItem ->
            val candidate = candidateById[inboxItem.transactionCandidateId]
            HomeInboxRow(
                id = inboxItem.id,
                headline = candidate?.toEntityName ?: "Review transaction",
                subline = listOfNotNull(
                    candidate?.mode?.name?.replace('_', ' '),
                    candidate?.amountMinor?.let(::formatMinor),
                    candidate?.occurredAt,
                ).joinToString(" • ").ifBlank { inboxItem.createdAt },
                reasonLabel = inboxItem.reasonCode.name.replace('_', ' '),
                merchantDraft = inboxDrafts[inboxItem.id] ?: candidate?.toEntityName.orEmpty(),
            )
        }
        val transactionRows = transactions.take(20).map { transaction ->
            HomeTransactionRow(
                id = transaction.id,
                headline = transaction.merchantName ?: "Unnamed transaction",
                subline = listOfNotNull(
                    transaction.mode?.name?.replace('_', ' '),
                    transaction.sourceSummary,
                    transaction.occurredAt,
                ).joinToString(" • ").ifBlank { transaction.occurredAt },
                amountLabel = formatMinor(transaction.amountMinor),
                notes = transaction.notes.orEmpty(),
                merchantDraft = transactionMerchantDraftMap[transaction.id] ?: transaction.merchantName.orEmpty(),
                notesDraft = transactionNotesDraftMap[transaction.id] ?: transaction.notes.orEmpty(),
            )
        }

        HomeUiState(
            userName = user?.displayName ?: "Rupee",
            accountCount = accounts.size,
            cardCount = cards.size,
            categoryCount = categories.size,
            bucketCount = buckets.size,
            recentTransactionCount = transactions.size,
            pendingInboxCount = inboxItems.size,
            recentCandidateCount = candidates.size,
            selectedTab = tab,
            selectedInboxItemId = activeInboxItemId ?: inboxRows.firstOrNull()?.id,
            selectedTransactionId = activeTransactionId ?: transactionRows.firstOrNull()?.id,
            inboxItems = inboxRows,
            recentTransactions = transactionRows,
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

    fun selectTab(tab: HomeTab) {
        selectedTab.value = tab
    }

    fun selectInboxItem(id: String) {
        selectedInboxItemId.value = id
    }

    fun updateInboxMerchantDraft(id: String, value: String) {
        inboxMerchantDrafts.value = inboxMerchantDrafts.value + (id to value)
    }

    fun confirmInboxItem(id: String) {
        viewModelScope.launch {
            repository.confirmInboxItem(
                inboxItemId = id,
                merchantNameOverride = inboxMerchantDrafts.value[id],
            )
            inboxMerchantDrafts.value = inboxMerchantDrafts.value - id
            selectedInboxItemId.value = null
        }
    }

    fun dismissInboxItem(id: String) {
        viewModelScope.launch {
            repository.dismissInboxItem(id)
            inboxMerchantDrafts.value = inboxMerchantDrafts.value - id
            selectedInboxItemId.value = null
        }
    }

    fun selectTransaction(id: String) {
        selectedTransactionId.value = id
    }

    fun updateTransactionMerchantDraft(id: String, value: String) {
        transactionMerchantDrafts.value = transactionMerchantDrafts.value + (id to value)
    }

    fun updateTransactionNotesDraft(id: String, value: String) {
        transactionNotesDrafts.value = transactionNotesDrafts.value + (id to value)
    }

    fun saveTransactionEdits(id: String) {
        viewModelScope.launch {
            repository.updateTransactionDetails(
                transactionId = id,
                merchantName = transactionMerchantDrafts.value[id].orEmpty(),
                notes = transactionNotesDrafts.value[id].orEmpty(),
            )
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
