package com.zegrt.rupee.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "Rupee",
    val accountCount: Int = 0,
    val cardCount: Int = 0,
    val categoryCount: Int = 0,
    val bucketCount: Int = 0,
    val recentTransactionCount: Int = 0,
    val isSeeding: Boolean = true,
)

class HomeViewModel(
    private val repository: LocalFinanceRepository,
) : ViewModel() {
    private val isSeeding = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeUser(),
        repository.observeAccounts(),
        repository.observeCards(),
        repository.observeCategories(),
        repository.observeBuckets(),
        repository.observeRecentTransactions(),
        isSeeding,
    ) { values ->
        val user = values[0] as? com.zegrt.rupee.data.local.entity.UserEntity
        val accounts = values[1] as List<*>
        val cards = values[2] as List<*>
        val categories = values[3] as List<*>
        val buckets = values[4] as List<*>
        val transactions = values[5] as List<*>
        val seeding = values[6] as Boolean

        HomeUiState(
            userName = user?.displayName ?: "Rupee",
            accountCount = accounts.size,
            cardCount = cards.size,
            categoryCount = categories.size,
            bucketCount = buckets.size,
            recentTransactionCount = transactions.size,
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
