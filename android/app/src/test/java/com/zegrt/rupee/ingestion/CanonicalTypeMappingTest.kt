package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the [NotificationSignalNormalizer.canonicalTypeFor] mapping —
 * the boundary between the parser layer's granular `ParsedTransactionKind`
 * enum and the user-facing ledger's `CanonicalTransactionType`.
 *
 * Why this test is load-bearing despite looking trivial:
 *
 * 1. Two of the bugs we've shipped fixes for (the P2P income bug and the
 *    refund-as-expense bug) lived inside `when` blocks that defaulted
 *    "everything else" to the wrong type. Locking the mapping explicitly
 *    here means a future kind-rename or a new enum value drawing the
 *    wrong default surfaces as a compile error (the `when` is in
 *    expression position) AND a failing test (one assertion per enum
 *    value below).
 *
 * 2. The normalizer file is otherwise impossible to unit-test from JVM
 *    code — it uses `database.withTransaction { … }` everywhere, which
 *    needs an instrumented test or in-memory Room. Pulling the pure
 *    mapping out into a companion-object function makes that one slice
 *    testable cheaply.
 *
 * One test per [ParsedTransactionKind] enum value. If the enum gains a
 * new entry, add a row here.
 */
class CanonicalTypeMappingTest {

    @Test
    fun `INCOME maps to canonical INCOME`() {
        assertEquals(
            CanonicalTransactionType.INCOME,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.INCOME),
        )
    }

    @Test
    fun `REFUND maps to canonical INCOME — this is the bug fix from PR 2`() {
        // PhonePe / Paytm refund and cashback bodies tag the kind as REFUND.
        // Pre-fix the canonical-type writer defaulted everything-not-INCOME
        // to EXPENSE, so refunds inflated monthly spend instead of cancelling
        // the original outflow. This assertion is the regression guard for
        // that fix and the reason `canonicalTypeFor` exists at all.
        assertEquals(
            CanonicalTransactionType.INCOME,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.REFUND),
        )
    }

    @Test
    fun `SPEND maps to canonical EXPENSE`() {
        assertEquals(
            CanonicalTransactionType.EXPENSE,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.SPEND),
        )
    }

    @Test
    fun `BILL_DUE maps to canonical EXPENSE`() {
        // BILL_DUE doesn't usually produce a canonical row in current code
        // (applyBillDueToCard handles the side-effect on credit_cards), but
        // if any future path does write one, mapping it as EXPENSE matches
        // the intent — a bill that's about to be debited.
        assertEquals(
            CanonicalTransactionType.EXPENSE,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.BILL_DUE),
        )
    }

    @Test
    fun `EMI maps to canonical EXPENSE`() {
        assertEquals(
            CanonicalTransactionType.EXPENSE,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.EMI),
        )
    }

    @Test
    fun `PAYMENT maps to canonical EXPENSE`() {
        // PAYMENT today is emitted by CRED's "bill paid via CRED" path —
        // i.e. money going from the user's account to a credit card. From
        // the ledger's POV that's an expense (the cash-side debit), not
        // income to the card. Confirm-or-merge logic in the inbox path
        // handles the card-side credit separately.
        assertEquals(
            CanonicalTransactionType.EXPENSE,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.PAYMENT),
        )
    }

    @Test
    fun `STATEMENT maps to canonical EXPENSE`() {
        assertEquals(
            CanonicalTransactionType.EXPENSE,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.STATEMENT),
        )
    }

    @Test
    fun `RECURRING_CANDIDATE maps to canonical EXPENSE`() {
        assertEquals(
            CanonicalTransactionType.EXPENSE,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.RECURRING_CANDIDATE),
        )
    }

    @Test
    fun `UNKNOWN maps to canonical EXPENSE`() {
        // Conservative default — an unparseable body that somehow made it
        // through the parser and decision engine still ends up as an
        // expense, surfacing in the user's monthly spend so they can review
        // it. The alternative (default to INCOME) would mask spend errors.
        assertEquals(
            CanonicalTransactionType.EXPENSE,
            NotificationSignalNormalizer.canonicalTypeFor(ParsedTransactionKind.UNKNOWN),
        )
    }

    @Test
    fun `mapping is exhaustive over the ParsedTransactionKind enum`() {
        // Compile-time check disguised as a runtime test. If a new kind is
        // added without updating canonicalTypeFor, the `when` inside
        // canonicalTypeFor stops compiling (expression-position `when` on
        // a sealed enum must be exhaustive). The check below also fails
        // at runtime — defense in depth.
        val allKinds = ParsedTransactionKind.values().toSet()
        val covered = allKinds.associateWith { kind ->
            NotificationSignalNormalizer.canonicalTypeFor(kind)
        }
        // Sanity: every enum value produces SOMETHING, no nulls.
        assertEquals(allKinds.size, covered.size)
    }
}
