package com.zegrt.rupee

import android.app.Application
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
}
