package com.zegrt.rupee.data.repository

import com.zegrt.rupee.data.local.BaseDatabaseTest
import com.zegrt.rupee.data.local.entity.AccountType
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Coverage for Sprint 0 / SEED-SERVICE:
 *
 *  - `completeInitialSetup` issues UUID-derived IDs for account/card rows
 *    instead of sequential `account-bank-${n+1}` strings.
 *  - The monthly budget seeded by `ensureMonthlyBudgetForToday` is for the
 *    *current* month, not a hardcoded period.
 *  - The cash-account guard still prevents duplicates on re-run.
 */
@RunWith(AndroidJUnit4::class)
class InitialSetupTest : BaseDatabaseTest() {

    private fun repo() = LocalFinanceRepository(database)

    @Test
    fun completeInitialSetup_seedsBudgetForCurrentMonth() = runBlocking {
        val r = repo()
        r.completeInitialSetup(
            OnboardingSetupInput(
                bankAccountName = "ICICI Savings",
                bankProviderName = "ICICI",
                creditCardName = "HDFC Millennia",
                creditCardProviderName = "HDFC",
                cashAccountEnabled = true,
                cashBalanceMinor = 50000,
            ),
        )

        val currentMonth = YearMonth.now()
        val budget = database.budgetDao()
            .getMonthlyTotalBudgetForDate("local-user", java.time.LocalDate.now().toString())
        assertNotNull("expected a current-month budget to be seeded", budget)
        assertEquals(currentMonth.atDay(1).toString(), budget!!.periodStart)
        assertEquals(currentMonth.atEndOfMonth().toString(), budget.periodEnd)
    }

    @Test
    fun completeInitialSetup_idsAreUuidsNotSequential() = runBlocking {
        val r = repo()
        r.completeInitialSetup(
            OnboardingSetupInput(
                bankAccountName = "ICICI Savings",
                bankProviderName = "ICICI",
                creditCardName = "HDFC Millennia",
                creditCardProviderName = "HDFC",
                cashAccountEnabled = true,
                cashBalanceMinor = 0,
            ),
        )

        val accounts = database.accountDao().observeActiveAccounts().first()
        val cards = database.creditCardDao().observeActiveCards().first()
        assertEquals(2, accounts.size)
        assertEquals(1, cards.size)
        // None should match the old hardcoded patterns; UUID-derived IDs are
        // ~30+ chars (account-<uuid>).
        accounts.forEach {
            assertTrue("expected UUID-style ID, got ${it.id}", it.id.length > 20)
            assertNotEquals("account-bank-1", it.id)
            assertNotEquals("account-cash", it.id)
        }
        cards.forEach {
            assertTrue("expected UUID-style ID, got ${it.id}", it.id.length > 20)
            assertNotEquals("card-1", it.id)
        }
    }

    @Test
    fun completeInitialSetup_secondCallDoesNotDuplicateCashAccount() = runBlocking {
        val r = repo()
        val input = OnboardingSetupInput(
            bankAccountName = "",
            bankProviderName = "",
            creditCardName = "",
            creditCardProviderName = "",
            cashAccountEnabled = true,
            cashBalanceMinor = 0,
        )
        r.completeInitialSetup(input)
        r.completeInitialSetup(input)

        val cashAccounts = database.accountDao().observeActiveAccounts().first()
            .filter { it.accountType == AccountType.CASH }
        assertEquals("cash account should be deduped across re-runs", 1, cashAccounts.size)
    }
}
