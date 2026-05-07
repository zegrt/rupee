package com.zegrt.rupee.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.ingestion.NotificationParserRegistry
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SampleNotification(
    val label: String,
    val packageName: String,
    val title: String?,
    val body: String,
)

object DebugSamples {
    val gpay = SampleNotification(
        label = "GPay ₹245 to Swiggy",
        packageName = "com.google.android.apps.nbu.paisa.user",
        title = "₹245.00 paid",
        body = "You paid ₹245.00 to Swiggy using UPI",
    )
    val cred = SampleNotification(
        label = "CRED ₹1,499 Zomato",
        packageName = "com.dreamplug.androidapp",
        title = "Spent on HDFC card",
        body = "₹1,499 spent on HDFC Credit Card xx1234 at Zomato on 06 May",
    )
    val icici = SampleNotification(
        label = "ICICI ₹3,800 BigBasket",
        packageName = "com.csam.icici.bank.imobile",
        title = "Transaction alert",
        body = "Rs.3800.00 has been spent at BIGBASKET on ICICI Credit Card xx5678 on 07-MAY-26",
    )

    val all: List<SampleNotification> = listOf(gpay, cred, icici)
}

data class DebugUiState(
    val isResetting: Boolean = false,
    val sampleSent: String? = null,
    val parseInputTitle: String = "",
    val parseInputBody: String = "",
    val parseOutput: String? = null,
    val message: String? = null,
)

class DebugViewModel(
    private val repository: LocalFinanceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    private val parserRegistry = NotificationParserRegistry.default()

    fun resetAllData() {
        _uiState.value = _uiState.value.copy(isResetting = true)
        viewModelScope.launch {
            repository.resetAllData()
            _uiState.value = _uiState.value.copy(
                isResetting = false,
                message = "App data reset",
            )
        }
    }

    fun sendSample(sample: SampleNotification) {
        viewModelScope.launch {
            repository.debugIngestNotification(
                packageName = sample.packageName,
                title = sample.title,
                body = sample.body,
            )
            _uiState.value = _uiState.value.copy(
                sampleSent = sample.label,
                message = "Pushed: ${sample.label}",
            )
        }
    }

    fun updateParseTitle(value: String) {
        _uiState.value = _uiState.value.copy(parseInputTitle = value)
    }

    fun updateParseBody(value: String) {
        _uiState.value = _uiState.value.copy(parseInputBody = value)
    }

    fun runParseTest(packageName: String = "com.example.test") {
        val state = _uiState.value
        val now = Instant.now().toString()
        val raw = RawCaptureEventEntity(
            id = "debug-parse-${System.nanoTime()}",
            userId = "local-user",
            sourceType = RawCaptureSourceType.NOTIFICATION,
            sourceAppPackage = packageName,
            title = state.parseInputTitle.ifBlank { null },
            body = state.parseInputBody,
            receivedAt = now,
            deviceEventTime = now,
            hashFingerprint = "debug-parse-${System.nanoTime()}",
            ingestionStatus = com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus.CAPTURED,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )
        val result = parserRegistry.parse(raw)
        val pretty = buildString {
            appendLine("parserKey: ${result.parserKey}")
            appendLine("providerHint: ${result.providerHint ?: "—"}")
            appendLine("transactionKind: ${result.transactionKind}")
            appendLine("candidateType: ${result.candidateType}")
            appendLine("amountMinor: ${result.amountMinor ?: "—"}")
            appendLine("currency: ${result.currencyCode ?: "—"}")
            appendLine("merchantRaw: ${result.merchantRaw ?: "—"}")
            appendLine("toEntityName: ${result.toEntityName ?: "—"}")
            appendLine("mode: ${result.mode ?: "—"}")
            appendLine("maskedDigits: ${result.maskedDigits ?: "—"}")
            append("parseConfidence: ${result.parseConfidence}")
        }
        _uiState.value = state.copy(parseOutput = pretty)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

class DebugViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DebugViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return DebugViewModel(repository) as T
    }
}
