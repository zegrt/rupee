package com.zegrt.rupee

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.room.Room
import com.zegrt.rupee.budget.BudgetAlertManager
import com.zegrt.rupee.budget.DuesAlertManager
import com.zegrt.rupee.data.local.MIGRATION_5_6
import com.zegrt.rupee.data.local.MIGRATION_6_7
import com.zegrt.rupee.data.local.MIGRATION_7_8
import com.zegrt.rupee.data.local.MIGRATION_8_9
import com.zegrt.rupee.data.local.MIGRATION_9_10
import com.zegrt.rupee.data.local.MIGRATION_10_11
import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.diagnostics.CrashReporter
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.onboarding.OnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RupeeApplication : Application() {
    // App-lifetime coroutine scope for fire-and-forget background work
    // (debounce-timestamp persistence, ingestion-row pruning, anything else
    // that should outlive a viewModelScope but die with the process).
    // Declared before localFinanceRepository so the lazy block below
    // references an already-initialized field — Kotlin initialises class
    // fields in declaration order.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: RupeeDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            RupeeDatabase::class.java,
            "rupee.db",
        )
            .addMigrations(
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            )
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
            .build()
    }

    val localFinanceRepository: LocalFinanceRepository by lazy {
        // appScope is the app-lifetime SupervisorJob declared below; pass it
        // through so the repository's fire-and-forget persistence writes
        // ride structured concurrency tied to process lifetime instead of
        // falling back to its constructor-default SupervisorJob.
        LocalFinanceRepository(database, persistScope = appScope)
    }

    val onboardingPreferences: OnboardingPreferences by lazy {
        OnboardingPreferences(applicationContext)
    }

    val budgetAlertManager: BudgetAlertManager by lazy {
        BudgetAlertManager(applicationContext)
    }

    val duesAlertManager: DuesAlertManager by lazy {
        DuesAlertManager(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                DEBUG_CHANNEL_ID,
                "Rupee debug",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Mock notifications used by Rupee's debug tools" },
        )
        budgetAlertManager.registerChannel()
        duesAlertManager.registerChannel()

        // Hydrate debounce timestamps from app_state BEFORE the prune
        // launches — otherwise the in-memory AtomicLongs are still 0 and
        // the prune runs even if one fired half an hour ago. Sequential
        // launch (hydrate finishes, then prune fires) is the simplest
        // guarantee; both are I/O work and run off-main so the few-ms
        // delay doesn't matter for app startup.
        appScope.launch {
            runCatching { localFinanceRepository.hydrateDebounceState() }
                .onFailure { Log.w("RupeeApp", "Hydrating debounce state failed; debounces start at 0", it) }
            runCatching { localFinanceRepository.pruneStaleIngestionRows() }
                .onSuccess { deleted ->
                    if (deleted > 0) Log.i("RupeeApp", "Pruned $deleted stale raw events (+ FK-cascaded children)")
                }
                .onFailure { Log.w("RupeeApp", "Pruning stale ingestion rows failed", it) }
        }
    }

    companion object {
        const val DEBUG_CHANNEL_ID = "rupee_debug"
        const val DEBUG_MOCK_EXTRA = "rupee_debug_mock"
    }
}
