package com.zegrt.rupee

import android.app.Application
import androidx.room.Room
import com.zegrt.rupee.data.local.RupeeDatabase
import com.zegrt.rupee.data.repository.LocalFinanceRepository

class RupeeApplication : Application() {
    val database: RupeeDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            RupeeDatabase::class.java,
            "rupee.db",
        ).fallbackToDestructiveMigration(false).build()
    }

    val localFinanceRepository: LocalFinanceRepository by lazy {
        LocalFinanceRepository(database)
    }
}
