package com.zegrt.rupee.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.dao.CategorySpend
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryBudgetRow(
    val categoryId: String,
    val name: String,
    val limitRupees: String,
    val draftRupees: String,
    val spentLabel: String,
    val limitLabel: String?,
    val progress: Float,
    val isOverLimit: Boolean,
    val hasLimit: Boolean,
    val isSaving: Boolean,
)

data class MonthlyBudgetState(
    val limitRupees: String,
    val draftRupees: String,
    val limitLabel: String,
    val spentLabel: String,
    val remainingLabel: String,
    val progress: Float,
    val isOverLimit: Boolean,
    val isNearLimit: Boolean,
    val hasLimit: Boolean,
    val isSaving: Boolean,
)

data class BudgetsUiState(
    val monthLabel: String = "",
    val monthly: MonthlyBudgetState = MonthlyBudgetState(
        limitRupees = "",
        draftRupees = "",
        limitLabel = "Not set",
        spentLabel = "₹0 spent",
        remainingLabel = "",
        progress = 0f,
        isOverLimit = false,
        isNearLimit = false,
        hasLimit = false,
        isSaving = false,
    ),
    val categoryRows: List<CategoryBudgetRow> = emptyList(),
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val today = MutableStateFlow(clock())
    private val categoryDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val savingCategories = MutableStateFlow<Set<String>>(emptySet())
    private val monthlyDraft = MutableStateFlow<String?>(null)
    private val savingMonthly = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val moneyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("INR")
    }

    private val monthlyBudgetFlow = today.flatMapLatest { repository.observeMonthlyTotalBudget(it) }
    private val monthlySpendFlow = today.flatMapLatest { date ->
        val month = YearMonth.from(date)
        repository.observeSpentInPeriod(
            fromIso = month.atDay(1).toString(),
            untilIso = month.plusMonths(1).atDay(1).toString(),
        )
    }
    private val categoryBudgetsFlow = today.flatMapLatest { repository.observeCategoryBudgets(it) }
    private val categorySpendFlow = today.flatMapLatest { date ->
        val month = YearMonth.from(date)
        repository.observeSpentByCategory(
            fromIso = month.atDay(1).toString(),
            untilIso = month.plusMonths(1).atDay(1).toString(),
        )
    }

    val uiState: StateFlow<BudgetsUiState> = combine(
        combine(
            today,
            monthlyBudgetFlow,
            monthlySpendFlow,
            monthlyDraft,
            savingMonthly,
        ) { date, budget, spent, draft, saving ->
            arrayOf<Any?>(date, budget, spent, draft, saving)
        },
        combine(
            repository.observeCategories(),
            categoryBudgetsFlow,
            categorySpendFlow,
            categoryDrafts,
            savingCategories,
        ) { cats, budgets, spends, drafts, saving ->
            arrayOf<Any?>(cats, budgets, spends, drafts, saving)
        },
        message,
    ) { monthly, categories, msg ->
        @Suppress("UNCHECKED_CAST")
        toUiState(
            date = monthly[0] as LocalDate,
            monthlyBudget = monthly[1] as BudgetEntity?,
            monthlySpent = monthly[2] as Long,
            monthlyDraftValue = monthly[3] as String?,
            savingMonthlyValue = monthly[4] as Boolean,
            categories = categories[0] as List<CategoryEntity>,
            categoryBudgets = categories[1] as List<BudgetEntity>,
            categorySpends = categories[2] as List<CategorySpend>,
            categoryDraftsValue = categories[3] as Map<String, String>,
            savingCategoriesValue = categories[4] as Set<String>,
            messageValue = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetsUiState(),
    )

    fun updateMonthlyDraft(value: String) {
        monthlyDraft.value = value.filter { it.isDigit() }
    }

    fun saveMonthly() {
        val raw = monthlyDraft.value ?: return
        val rupees = raw.toLongOrNull() ?: return
        if (rupees <= 0L) return
        savingMonthly.value = true
        viewModelScope.launch {
            repository.setMonthlyBudgetLimit(rupees * 100)
            monthlyDraft.value = null
            savingMonthly.value = false
            message.value = "Monthly budget saved"
        }
    }

    fun updateCategoryDraft(categoryId: String, value: String) {
        categoryDrafts.value = categoryDrafts.value + (categoryId to value.filter { it.isDigit() })
    }

    fun saveCategoryLimit(categoryId: String) {
        val raw = categoryDrafts.value[categoryId] ?: return
        val rupees = raw.toLongOrNull() ?: 0L
        savingCategories.value = savingCategories.value + categoryId
        viewModelScope.launch {
            repository.setCategoryBudgetLimit(categoryId, rupees * 100)
            categoryDrafts.value = categoryDrafts.value - categoryId
            savingCategories.value = savingCategories.value - categoryId
            message.value = if (rupees == 0L) "Limit cleared" else "Saved"
        }
    }

    fun clearMessage() { message.value = null }

    private fun toUiState(
        date: LocalDate,
        monthlyBudget: BudgetEntity?,
        monthlySpent: Long,
        monthlyDraftValue: String?,
        savingMonthlyValue: Boolean,
        categories: List<CategoryEntity>,
        categoryBudgets: List<BudgetEntity>,
        categorySpends: List<CategorySpend>,
        categoryDraftsValue: Map<String, String>,
        savingCategoriesValue: Set<String>,
        messageValue: String?,
    ): BudgetsUiState {
        val month = YearMonth.from(date)
        val limit = monthlyBudget?.limitMinor ?: 0L
        val limitRupees = if (limit > 0L) (limit / 100).toString() else ""
        val remaining = (limit - monthlySpent).coerceAtLeast(0L)
        val progress = if (limit > 0L)
            (monthlySpent.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f)
        else 0f
        val threshold = monthlyBudget?.alertThresholdPercent?.toFloat() ?: 0.8f
        val isOver = limit > 0L && monthlySpent > limit
        val isNear = !isOver && limit > 0L && progress >= threshold

        val budgetByCat = categoryBudgets.mapNotNull { b -> b.targetRefId?.let { it to b } }.toMap()
        val spentByCat = categorySpends.associate { it.categoryId to it.amountMinor }
        val rows = categories.map { cat ->
            val limitMinor = budgetByCat[cat.id]?.limitMinor ?: 0L
            val spent = spentByCat[cat.id] ?: 0L
            val limitRup = if (limitMinor > 0L) (limitMinor / 100).toString() else ""
            val catProgress = if (limitMinor > 0L)
                (spent.toDouble() / limitMinor.toDouble()).toFloat().coerceIn(0f, 1f)
            else 0f
            CategoryBudgetRow(
                categoryId = cat.id,
                name = cat.name,
                limitRupees = limitRup,
                draftRupees = categoryDraftsValue[cat.id] ?: limitRup,
                spentLabel = "${moneyFormatter.format(spent / 100.0)} spent",
                limitLabel = if (limitMinor > 0L) "of ${moneyFormatter.format(limitMinor / 100.0)}" else null,
                progress = catProgress,
                isOverLimit = limitMinor > 0L && spent > limitMinor,
                hasLimit = limitMinor > 0L,
                isSaving = cat.id in savingCategoriesValue,
            )
        }

        return BudgetsUiState(
            monthLabel = "${month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
            monthly = MonthlyBudgetState(
                limitRupees = limitRupees,
                draftRupees = monthlyDraftValue ?: limitRupees,
                limitLabel = if (limit > 0L) moneyFormatter.format(limit / 100.0) else "Not set",
                spentLabel = "${moneyFormatter.format(monthlySpent / 100.0)} spent",
                remainingLabel = if (limit > 0L) "${moneyFormatter.format(remaining / 100.0)} left" else "",
                progress = progress,
                isOverLimit = isOver,
                isNearLimit = isNear,
                hasLimit = limit > 0L,
                isSaving = savingMonthlyValue,
            ),
            categoryRows = rows,
            message = messageValue,
        )
    }
}

class BudgetsViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BudgetsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return BudgetsViewModel(repository) as T
    }
}
