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
import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.diagnostics.CrashReporter
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.onboarding.OnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RupeeApplication : Application() {
    val database: RupeeDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            RupeeDatabase::class.java,
            "rupee.db",
        )
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
            .build()
    }

    val localFinanceRepository: LocalFinanceRepository by lazy {
        LocalFinanceRepository(database)
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // Ingestion-table prune. Debounced inside the repository to once per
        // 24h, so cheap to fire on every cold start. Launched fire-and-forget
        // on an app-scoped SupervisorJob so a failure doesn't take down the
        // app; logged on success so the count shows up in logcat for
        // diagnostics.
        appScope.launch {
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
