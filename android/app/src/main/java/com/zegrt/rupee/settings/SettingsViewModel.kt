package com.zegrt.rupee.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.BucketEntity
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.MerchantTrustRuleEntity
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

data class SettingsUiState(
    val displayName: String = "",
    val displayNameDraft: String = "",
    val monthlyBudgetRupees: String = "",
    val monthlyBudgetDraft: String = "",
    val monthlyBudgetLabel: String = "",
    val currencyCode: String = "INR",
    val categories: List<String> = emptyList(),
    val buckets: List<String> = emptyList(),
    val trustRules: List<TrustRuleRow> = emptyList(),
    val notificationGranted: Boolean = false,
    val appVersion: String = "",
    val savingName: Boolean = false,
    val savingBudget: Boolean = false,
    val message: String? = null,
)

data class TrustRuleRow(
    val id: String,
    val merchantPattern: String,
    val categoryLabel: String?,
)

class SettingsViewModel(
    private val repository: LocalFinanceRepository,
    private val appVersion: String = "",
) : ViewModel() {
    private val nameDraft = MutableStateFlow<String?>(null)
    private val budgetDraft = MutableStateFlow<String?>(null)
    private val savingName = MutableStateFlow(false)
    private val savingBudget = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val notificationGranted = MutableStateFlow(false)

    private val budgetFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = java.util.Currency.getInstance("INR")
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            repository.observeUser(),
            repository.observeMonthlyTotalBudget(),
            repository.observeCategories(),
            repository.observeBuckets(),
            repository.observeMerchantTrustRules(),
        ) { user, budget, cats, buckets, rules ->
            arrayOf<Any?>(user, budget, cats, buckets, rules)
        },
        combine(
            nameDraft,
            budgetDraft,
            savingName,
            savingBudget,
            message,
        ) { n, b, sn, sb, m ->
            arrayOf<Any?>(n, b, sn, sb, m)
        },
        // Notification permission flips after the user toggles system settings; this flow
        // belongs in the combine so the Settings card re-renders, not just sampled inside.
        notificationGranted,
    ) { entities, drafts, granted ->
        @Suppress("UNCHECKED_CAST")
        val user = entities[0] as UserEntity?
        @Suppress("UNCHECKED_CAST")
        val budget = entities[1] as BudgetEntity?
        @Suppress("UNCHECKED_CAST")
        val cats = entities[2] as List<CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val buckets = entities[3] as List<BucketEntity>
        @Suppress("UNCHECKED_CAST")
        val rules = entities[4] as List<MerchantTrustRuleEntity>

        val name = user?.displayName ?: ""
        val limitMinor = budget?.limitMinor ?: 0L
        val limitRupees = if (limitMinor > 0L) (limitMinor / 100).toString() else ""
        val categoryLabelById = cats.associate { it.id to it.name }
        val trustRules = rules.map { r ->
            TrustRuleRow(
                id = r.id,
                merchantPattern = r.merchantPattern,
                categoryLabel = r.autoCategoryId?.let { categoryLabelById[it] },
            )
        }

        SettingsUiState(
            displayName = name,
            displayNameDraft = drafts[0] as String? ?: name,
            monthlyBudgetRupees = limitRupees,
            monthlyBudgetDraft = drafts[1] as String? ?: limitRupees,
            monthlyBudgetLabel = if (limitMinor > 0L) budgetFormatter.format(limitMinor / 100.0) else "Not set",
            currencyCode = user?.defaultCurrencyCode ?: "INR",
            categories = cats.map { it.name },
            buckets = buckets.map { it.name },
            trustRules = trustRules,
            notificationGranted = granted,
            appVersion = appVersion,
            savingName = drafts[2] as Boolean,
            savingBudget = drafts[3] as Boolean,
            message = drafts[4] as String?,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun updateNameDraft(value: String) {
        nameDraft.value = value
    }

    fun updateBudgetDraft(value: String) {
        budgetDraft.value = value.filter { it.isDigit() }
    }

    fun saveDisplayName() {
        val current = nameDraft.value ?: return
        if (current.isBlank()) return
        savingName.value = true
        viewModelScope.launch {
            repository.updateUserDisplayName(current)
            nameDraft.value = null
            savingName.value = false
            message.value = "Name saved"
        }
    }

    fun saveMonthlyBudget() {
        val current = budgetDraft.value ?: return
        val rupees = current.toLongOrNull() ?: return
        if (rupees <= 0L) return
        savingBudget.value = true
        viewModelScope.launch {
            repository.setMonthlyBudgetLimit(rupees * 100)
            budgetDraft.value = null
            savingBudget.value = false
            message.value = "Budget saved"
        }
    }

    fun removeTrustRule(id: String) {
        viewModelScope.launch {
            repository.removeMerchantTrustRule(id)
            message.value = "Trust rule removed"
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun setNotificationGranted(granted: Boolean) {
        notificationGranted.value = granted
    }
}

class SettingsViewModelFactory(
    private val repository: LocalFinanceRepository,
    private val appVersion: String = "",
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return SettingsViewModel(repository, appVersion) as T
    }
}
