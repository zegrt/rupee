package com.zegrt.rupee.data.repository

import com.zegrt.rupee.data.local.BaseDatabaseTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Repo-level coverage for Sprint 1 / TRUST-FROM-TXN:
 *
 *   - `setMerchantTrust(merchant, autoCategoryId, trust=true)` inserts a rule
 *     when one doesn't exist; is a no-op when one already does.
 *   - `setMerchantTrust(..., trust=false)` deletes the matching rule when one
 *     exists; is a no-op when none does.
 *   - The matching is case-insensitive against the *cleaned* merchant pattern
 *     (the same form `addMerchantTrustRule` stores) — so toggling for
 *     "swiggy" finds a rule stored as "Swiggy".
 *
 * These run instrumented (via the shared in-memory Room fixture) because
 * the repo method touches the actual SQLite layer and the
 * MerchantNameUtils.clean output. Robolectric would compile but is
 * brittle around Android's String collation behaviour.
 */
@RunWith(AndroidJUnit4::class)
class MerchantTrustRepositoryTest : BaseDatabaseTest() {

    private fun repo() = LocalFinanceRepository(database)

    @Test
    fun setMerchantTrust_trueWithNoExistingRule_insertsOne() = runBlocking {
        val r = repo()
        assertTrue("precondition: no rules", r.getMerchantTrustRulesSnapshot().isEmpty())

        r.setMerchantTrust(merchant = "Swiggy", autoCategoryId = null, trust = true)

        val rules = r.getMerchantTrustRulesSnapshot()
        assertEquals(1, rules.size)
        assertEquals("Swiggy", rules.single().merchantPattern)
    }

    @Test
    fun setMerchantTrust_trueWithExistingRule_isNoOp() = runBlocking {
        val r = repo()
        r.setMerchantTrust(merchant = "Swiggy", autoCategoryId = null, trust = true)
        val firstSnapshot = r.getMerchantTrustRulesSnapshot()
        assertEquals(1, firstSnapshot.size)
        val originalId = firstSnapshot.single().id

        // Second call with the same merchant + trust=true must NOT insert a
        // duplicate row (idempotence is the whole point of setMerchantTrust).
        r.setMerchantTrust(merchant = "Swiggy", autoCategoryId = null, trust = true)

        val secondSnapshot = r.getMerchantTrustRulesSnapshot()
        assertEquals(1, secondSnapshot.size)
        assertEquals(originalId, secondSnapshot.single().id)
    }

    @Test
    fun setMerchantTrust_falseWithExistingRule_deletesIt() = runBlocking {
        val r = repo()
        r.setMerchantTrust(merchant = "Swiggy", autoCategoryId = null, trust = true)
        assertEquals(1, r.getMerchantTrustRulesSnapshot().size)

        r.setMerchantTrust(merchant = "Swiggy", autoCategoryId = null, trust = false)

        assertTrue(
            "expected the rule to be deleted",
            r.getMerchantTrustRulesSnapshot().isEmpty(),
        )
    }

    @Test
    fun setMerchantTrust_falseWithNoRule_isNoOp() = runBlocking {
        val r = repo()
        // Should not throw and should not somehow create a "deleted" rule.
        r.setMerchantTrust(merchant = "Swiggy", autoCategoryId = null, trust = false)
        assertTrue(r.getMerchantTrustRulesSnapshot().isEmpty())
    }

    @Test
    fun setMerchantTrust_caseInsensitiveMatch_doesNotDuplicate() = runBlocking {
        val r = repo()
        r.setMerchantTrust(merchant = "Swiggy", autoCategoryId = null, trust = true)

        // Toggling with a different-cased version of the same merchant must
        // match the existing rule, not create a parallel one. This is the
        // failure mode that would surface if MerchantNameUtils.clean changed
        // capitalisation behaviour underneath us.
        r.setMerchantTrust(merchant = "swiggy", autoCategoryId = null, trust = true)
        assertEquals(1, r.getMerchantTrustRulesSnapshot().size)

        // And turning it off via the differently-cased name still works.
        r.setMerchantTrust(merchant = "SWIGGY", autoCategoryId = null, trust = false)
        assertTrue(r.getMerchantTrustRulesSnapshot().isEmpty())
    }

    @Test
    fun setMerchantTrust_blankMerchant_isNoOp() = runBlocking {
        val r = repo()
        r.setMerchantTrust(merchant = "   ", autoCategoryId = null, trust = true)
        r.setMerchantTrust(merchant = "", autoCategoryId = null, trust = true)
        assertTrue(r.getMerchantTrustRulesSnapshot().isEmpty())
    }
}
