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

data class CategoryBudgetsUiState(
    val rows: List<CategoryBudgetRow> = emptyList(),
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryBudgetsViewModel(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val today = MutableStateFlow(clock())
    private val drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val saving = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)

    private val moneyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("INR")
    }

    private val budgetsFlow = today.flatMapLatest { date ->
        repository.observeCategoryBudgets(date)
    }
    private val spendFlow = today.flatMapLatest { date ->
        val month = YearMonth.from(date)
        repository.observeSpentByCategory(
            fromIso = month.atDay(1).toString(),
            untilIso = month.plusMonths(1).atDay(1).toString(),
        )
    }

    val uiState: StateFlow<CategoryBudgetsUiState> = combine(
        repository.observeCategories(),
        budgetsFlow,
        spendFlow,
        drafts,
        saving,
    ) { cats, budgets, spends, draftsNow, savingNow ->
        toUiState(cats, budgets, spends, draftsNow, savingNow)
    }.combine(message) { state, msg ->
        state.copy(message = msg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryBudgetsUiState(),
    )

    fun updateDraft(categoryId: String, value: String) {
        drafts.value = drafts.value + (categoryId to value.filter { it.isDigit() })
    }

    fun saveLimit(categoryId: String) {
        val raw = drafts.value[categoryId] ?: return
        val rupees = raw.toLongOrNull() ?: 0L
        saving.value = saving.value + categoryId
        viewModelScope.launch {
            repository.setCategoryBudgetLimit(categoryId, rupees * 100)
            drafts.value = drafts.value - categoryId
            saving.value = saving.value - categoryId
            message.value = if (rupees == 0L) "Limit cleared" else "Saved"
        }
    }

    fun clearMessage() { message.value = null }

    private fun toUiState(
        cats: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        spends: List<CategorySpend>,
        drafts: Map<String, String>,
        saving: Set<String>,
    ): CategoryBudgetsUiState {
        val budgetByCat = budgets.mapNotNull { b -> b.targetRefId?.let { it to b } }.toMap()
        val spentByCat = spends.associate { it.categoryId to it.amountMinor }
        val rows = cats.map { cat ->
            val budget = budgetByCat[cat.id]
            val limitMinor = budget?.limitMinor ?: 0L
            val spent = spentByCat[cat.id] ?: 0L
            val limitRupees = if (limitMinor > 0L) (limitMinor / 100).toString() else ""
            val progress = if (limitMinor > 0L) {
                (spent.toDouble() / limitMinor.toDouble()).toFloat().coerceIn(0f, 1f)
            } else 0f
            CategoryBudgetRow(
                categoryId = cat.id,
                name = cat.name,
                limitRupees = limitRupees,
                draftRupees = drafts[cat.id] ?: limitRupees,
                spentLabel = "${moneyFormatter.format(spent / 100.0)} spent",
                limitLabel = if (limitMinor > 0L) "of ${moneyFormatter.format(limitMinor / 100.0)}" else null,
                progress = progress,
                isOverLimit = limitMinor > 0L && spent > limitMinor,
                hasLimit = limitMinor > 0L,
                isSaving = cat.id in saving,
            )
        }
        return CategoryBudgetsUiState(rows = rows)
    }
}

class CategoryBudgetsViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CategoryBudgetsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return CategoryBudgetsViewModel(repository) as T
    }
}
