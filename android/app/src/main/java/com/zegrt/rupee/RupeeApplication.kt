package com.zegrt.rupee

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
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
            // Pre-release builds can reset local state while the schema is still moving quickly.
            .fallbackToDestructiveMigration(true)
            .build()
    }

    val localFinanceRepository: LocalFinanceRepository by lazy {
        LocalFinanceRepository(database)
    }

    val onboardingPreferences: OnboardingPreferences by lazy {
        OnboardingPreferences(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                DEBUG_CHANNEL_ID,
                "Rupee debug",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Mock notifications used by Rupee's debug tools" },
        )
    }

    companion object {
        const val DEBUG_CHANNEL_ID = "rupee_debug"
        const val DEBUG_MOCK_EXTRA = "rupee_debug_mock"
    }
}
