package com.zegrt.rupee

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import com.zegrt.rupee.budget.BudgetAlertManager
import com.zegrt.rupee.budget.DuesAlertManager
import com.zegrt.rupee.data.local.MIGRATION_5_6
import com.zegrt.rupee.data.local.MIGRATION_6_7
import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.onboarding.OnboardingPreferences

class RupeeApplication : Application() {
    val database: RupeeDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            RupeeDatabase::class.java,
            "rupee.db",
        )
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
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

    override fun onCreate() {
        super.onCreate()
        com.zegrt.rupee.diagnostics.CrashReporter.install(this)
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
    }

    companion object {
        const val DEBUG_CHANNEL_ID = "rupee_debug"
        const val DEBUG_MOCK_EXTRA = "rupee_debug_mock"
    }
}
