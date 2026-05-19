package com.zegrt.rupee.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

/**
 * Base for instrumented Room tests.
 *
 * Builds a fresh in-memory [RupeeDatabase] before every test and closes it
 * after. The DB is built with `setQueryExecutor(Runnable::run)` and
 * `setTransactionExecutor(Runnable::run)` so suspend DAO calls inside
 * `runBlocking` execute on the same thread — without it, transactions
 * deadlock waiting on a background dispatcher that the test runner doesn't
 * provide.
 *
 * Why instrumented and not Robolectric: the regressions T1 is built to
 * catch are SQLite-specific (the H4 FK ordering bug raised
 * `SQLiteConstraintException` because Android's real SQLite enforces FKs
 * at INSERT time). Robolectric's SQLite shim has known quirks around FK
 * enforcement and pragma handling. Instrumented tests run on a real
 * device or emulator's SQLite — same engine as production.
 */
abstract class BaseDatabaseTest {

    protected lateinit var database: RupeeDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RupeeDatabase::class.java)
            // Run queries on the same thread the test is calling from so
            // `runBlocking { dao.suspendCall() }` doesn't park forever
            // waiting on an executor.
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            // Allow main-thread queries — instrumented test threads aren't
            // the actual main thread, but Room's main-thread check trips
            // anyway.
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }
}
