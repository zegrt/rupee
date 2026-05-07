package com.zegrt.rupee.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.BucketEntity
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

data class HomeRecentRow(
    val id: String,
    val merchant: String,
    val subline: String,
    val amountLabel: String,
    val isSuggested: Boolean,
)

data class HomeDashboard(
    val greeting: String = "Hello",
    val monthLabel: String = "",
    val hasMonthlyBudget: Boolean = false,
    val monthlyBudgetLabel: String = "",
    val monthlySpentLabel: String = "",
    val monthlyRemainingLabel: String = "",
    val monthlyProgress: Float = 0f,
    val isOverBudget: Boolean = false,
    val isNearLimit: Boolean = false,
    val weeklySpentLabel: String = "",
    val weekRangeLabel: String = "",
    val pendingInboxCount: Int = 0,
    val recentTransactions: List<HomeRecentRow> = emptyList(),
    val hasAnyTransactions: Boolean = false,
)

data class HomeUiState(
    val userName: String = "Rupee",
    val accountCount: Int = 0,
    val cardCount: Int = 0,
    val categoryCount: Int = 0,
    val bucketCount: Int = 0,
    val recentTransactionCount: Int = 0,
    val pendingInboxCount: Int = 0,
    val selectedTab: HomeTab = HomeTab.HOME,
    val selectedInboxItemId: String? = null,
    val selectedTransactionId: String? = null,
    val inboxItems: List<HomeInboxRow> = emptyList(),
    val recentTransactions: List<HomeTransactionRow> = emptyList(),
    val dashboard: HomeDashboard = HomeDashboard(),
    val isSeeding: Boolean = true,
)

class HomeViewModel(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = java.util.Currency.getInstance("INR")
    }

    private val isSeeding = MutableStateFlow(true)
    private val selectedTab = MutableStateFlow(HomeTab.HOME)
    private val selectedInboxItemId = MutableStateFlow<String?>(null)
    private val selectedTransactionId = MutableStateFlow<String?>(null)
    private val inboxMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionNotesDrafts = MutableStateFlow<Map<String, String>>(emptyMap())

    private val today: LocalDate get() = clock()
    private val month: YearMonth get() = YearMonth.from(today)
    private val monthStart: LocalDate get() = month.atDay(1)
    private val nextMonthStart: LocalDate get() = month.plusMonths(1).atDay(1)
    private val weekStart: LocalDate get() = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val nextWeekStart: LocalDate get() = weekStart.plusDays(7)

    private val monthlySpend: Flow<Long> = repository.observeSpentInPeriod(
        fromIso = monthStart.toString(),
        untilIso = nextMonthStart.toString(),
    )
    private val weeklySpend: Flow<Long> = repository.observeSpentInPeriod(
        fromIso = weekStart.toString(),
        untilIso = nextWeekStart.toString(),
    )
    private val monthlyBudget: Flow<BudgetEntity?> = repository.observeMonthlyTotalBudget(today)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<HomeUiState> = combine(
        listOf(
            repository.observeUser(),
            repository.observeAccounts(),
            repository.observeCards(),
            repository.observeCategories(),
            repository.observeBuckets(),
            repository.observeRecentTransactions(),
            repository.observeRecentTransactionCandidates(),
            repository.observePendingInboxItems(),
            monthlyBudget,
            monthlySpend,
            weeklySpend,
            isSeeding,
            selectedTab,
            selectedInboxItemId,
            selectedTransactionId,
            inboxMerchantDrafts,
            transactionMerchantDrafts,
            transactionNotesDrafts,
        ),
    ) { values ->
        val user = values[0] as UserEntity?
        val accounts = values[1] as List<AccountEntity>
        val cards = values[2] as List<CreditCardEntity>
        val categories = values[3] as List<CategoryEntity>
        val buckets = values[4] as List<BucketEntity>
        val transactions = values[5] as List<CanonicalTransactionEntity>
        val candidates = values[6] as List<TransactionCandidateEntity>
        val inboxItems = values[7] as List<InboxItemEntity>
        val budget = values[8] as BudgetEntity?
        val spentMonth = values[9] as Long
        val spentWeek = values[10] as Long
        val seeding = values[11] as Boolean
        val tab = values[12] as HomeTab
        val activeInboxItemId = values[13] as String?
        val activeTransactionId = values[14] as String?
        val inboxDrafts = values[15] as Map<String, String>
        val transactionMerchantDraftMap = values[16] as Map<String, String>
        val transactionNotesDraftMap = values[17] as Map<String, String>

        val candidatesById = candidates.associateBy { it.id }
        val inboxRows = inboxItems.map { inboxItem ->
            val candidate = candidatesById[inboxItem.transactionCandidateId]
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

        val dashboard = buildDashboard(
            user = user,
            transactions = transactions,
            inboxItems = inboxItems,
            budget = budget,
            spentMonth = spentMonth,
            spentWeek = spentWeek,
        )

        HomeUiState(
            userName = user?.displayName ?: "Rupee",
            accountCount = accounts.size,
            cardCount = cards.size,
            categoryCount = categories.size,
            bucketCount = buckets.size,
            recentTransactionCount = transactions.size,
            pendingInboxCount = inboxItems.size,
            selectedTab = tab,
            selectedInboxItemId = activeInboxItemId ?: inboxRows.firstOrNull()?.id,
            selectedTransactionId = activeTransactionId ?: transactionRows.firstOrNull()?.id,
            inboxItems = inboxRows,
            recentTransactions = transactionRows,
            dashboard = dashboard,
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

    private fun buildDashboard(
        user: UserEntity?,
        transactions: List<CanonicalTransactionEntity>,
        inboxItems: List<InboxItemEntity>,
        budget: BudgetEntity?,
        spentMonth: Long,
        spentWeek: Long,
    ): HomeDashboard {
        val limit = budget?.limitMinor ?: 0L
        val remaining = (limit - spentMonth).coerceAtLeast(0L)
        val progress = if (limit > 0L) {
            (spentMonth.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f)
        } else 0f
        val isOver = limit > 0L && spentMonth > limit
        val isNear = !isOver && limit > 0L && progress >= (budget?.alertThresholdPercent?.toFloat() ?: 0.8f)

        val recents = transactions.take(5).map { txn ->
            HomeRecentRow(
                id = txn.id,
                merchant = txn.merchantName ?: "Unnamed",
                subline = listOfNotNull(
                    txn.mode?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() },
                    txn.sourceSummary,
                ).joinToString(" • ").ifBlank { txn.occurredAt.take(10) },
                amountLabel = formatMinor(txn.amountMinor),
                isSuggested = txn.status.name == "SUGGESTED",
            )
        }

        return HomeDashboard(
            greeting = greetingFor(user?.displayName),
            monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year,
            hasMonthlyBudget = budget != null,
            monthlyBudgetLabel = if (budget != null) formatMinor(limit) else "Set a monthly budget",
            monthlySpentLabel = formatMinor(spentMonth),
            monthlyRemainingLabel = formatMinor(remaining),
            monthlyProgress = progress,
            isOverBudget = isOver,
            isNearLimit = isNear,
            weeklySpentLabel = formatMinor(spentWeek),
            weekRangeLabel = "${weekStart.dayOfMonth} ${weekStart.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} – ${nextWeekStart.minusDays(1).dayOfMonth} ${nextWeekStart.minusDays(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}",
            pendingInboxCount = inboxItems.size,
            recentTransactions = recents,
            hasAnyTransactions = transactions.isNotEmpty(),
        )
    }

    private fun greetingFor(name: String?): String {
        val hour = java.time.LocalTime.now().hour
        val timeOfDay = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        return if (!name.isNullOrBlank()) "$timeOfDay, $name" else timeOfDay
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
