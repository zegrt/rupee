package com.zegrt.rupee.data.repository

import com.zegrt.rupee.data.local.BaseDatabaseTest
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.SyncStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Coverage for Sprint 1 / EXCL-FROM-SPEND:
 *
 *   - `setAccountExcludeFromExpenseTotals` flips the row's
 *     `excludeFromExpenseTotals` and bumps `updatedAt`. (Already shipped;
 *     pinned here as a baseline against future refactors.)
 *   - `setAccountExcludeFromIncomeTotals` is new — same shape but writes
 *     the income flag. No production test until this file existed.
 *   - Both setters no-op silently when the account ID doesn't exist
 *     (matches the existing pattern in `setAccountExcludeFromExpenseTotals`).
 *
 * Lives under androidTest so we exercise the real DAO + SQLite-backed
 * `accounts` row update.
 */
@RunWith(AndroidJUnit4::class)
class AccountExclusionRepositoryTest : BaseDatabaseTest() {

    private fun repo() = LocalFinanceRepository(database)

    private fun seedAccount(id: String = "account-1"): AccountEntity {
        val now = Instant.now().toString()
        val account = AccountEntity(
            id = id,
            userId = "local-user",
            accountType = AccountType.BANK,
            displayName = "ICICI Savings",
            providerName = "ICICI",
            currencyCode = "INR",
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )
        runBlocking { database.accountDao().upsertAccounts(listOf(account)) }
        return account
    }

    @Test
    fun setAccountExcludeFromExpenseTotals_flipsTheFlag() = runBlocking {
        val seeded = seedAccount()
        val r = repo()
        assertTrue("seed has the default flag off", !seeded.excludeFromExpenseTotals)

        r.setAccountExcludeFromExpenseTotals(seeded.id, exclude = true)

        val after = database.accountDao().getAccountById(seeded.id)
        assertNotNull(after)
        assertEquals(true, after!!.excludeFromExpenseTotals)
        // Other flag must be untouched.
        assertEquals(false, after.excludeFromIncomeTotals)
    }

    @Test
    fun setAccountExcludeFromIncomeTotals_flipsTheFlag() = runBlocking {
        val seeded = seedAccount()
        val r = repo()
        assertTrue("seed has the default flag off", !seeded.excludeFromIncomeTotals)

        r.setAccountExcludeFromIncomeTotals(seeded.id, exclude = true)

        val after = database.accountDao().getAccountById(seeded.id)
        assertNotNull(after)
        assertEquals(true, after!!.excludeFromIncomeTotals)
        // Expense flag must be untouched — the two are independent toggles.
        assertEquals(false, after.excludeFromExpenseTotals)
    }

    @Test
    fun setAccountExclude_unknownAccountId_isNoOp() = runBlocking {
        val r = repo()
        // No seed; no rows in the accounts table.
        r.setAccountExcludeFromExpenseTotals("account-does-not-exist", exclude = true)
        r.setAccountExcludeFromIncomeTotals("account-does-not-exist", exclude = true)
        // No crash, no row magically appears.
        val all = database.accountDao().getAccountById("account-does-not-exist")
        assertEquals(null, all)
    }

    @Test
    fun setBothToggles_independently() = runBlocking {
        val seeded = seedAccount()
        val r = repo()
        r.setAccountExcludeFromExpenseTotals(seeded.id, exclude = true)
        r.setAccountExcludeFromIncomeTotals(seeded.id, exclude = true)

        val after = database.accountDao().getAccountById(seeded.id)!!
        assertEquals(true, after.excludeFromExpenseTotals)
        assertEquals(true, after.excludeFromIncomeTotals)

        // Turn one back off; the other stays.
        r.setAccountExcludeFromExpenseTotals(seeded.id, exclude = false)
        val again = database.accountDao().getAccountById(seeded.id)!!
        assertEquals(false, again.excludeFromExpenseTotals)
        assertEquals(true, again.excludeFromIncomeTotals)
    }
}
