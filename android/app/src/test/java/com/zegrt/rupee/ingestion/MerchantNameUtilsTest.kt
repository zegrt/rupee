package com.zegrt.rupee.ingestion

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantNameUtilsTest {

    @Test
    fun `clean trims using-UPI tail from GPay-style merchant`() {
        assertEquals("Swiggy", MerchantNameUtils.clean("Swiggy using UPI"))
    }

    @Test
    fun `clean trims trailing on-date from ICICI-style merchant`() {
        assertEquals(
            "BIGBASKET",
            MerchantNameUtils.clean("BIGBASKET on ICICI Credit Card xx5678 on 07-MAY-26"),
        )
    }

    @Test
    fun `clean prefers segment after last 'at' for CRED-style bodies`() {
        assertEquals(
            "Zomato",
            MerchantNameUtils.clean("HDFC Credit Card xx1234 at Zomato on 06 May"),
        )
    }

    @Test
    fun `clean returns Unnamed for null and blank`() {
        assertEquals("Unnamed", MerchantNameUtils.clean(null))
        assertEquals("Unnamed", MerchantNameUtils.clean(""))
        assertEquals("Unnamed", MerchantNameUtils.clean("   "))
    }

    @Test
    fun `matchesPattern is case-insensitive on the cleaned form`() {
        assertEquals(true, MerchantNameUtils.matchesPattern("Swiggy using UPI", "swiggy"))
        assertEquals(true, MerchantNameUtils.matchesPattern("SWIGGY using UPI", "Swiggy"))
        assertEquals(true, MerchantNameUtils.matchesPattern("HDFC Credit Card xx1234 at Zomato on 06 May", "Zomato"))
    }

    @Test
    fun `matchesPattern rejects different merchants`() {
        assertEquals(false, MerchantNameUtils.matchesPattern("Swiggy using UPI", "Zomato"))
        assertEquals(false, MerchantNameUtils.matchesPattern(null, "Swiggy"))
        assertEquals(false, MerchantNameUtils.matchesPattern("Swiggy", ""))
    }

    @Test
    fun `matchesPattern is exact on the cleaned merchant - no substring match`() {
        // "Big Bazaar" should NOT trigger a rule for "Big" — keeps the rule narrow.
        assertEquals(false, MerchantNameUtils.matchesPattern("Big Bazaar using UPI", "Big"))
    }

    @Test
    fun `trust rule round-trip - pattern derived from clean of raw matches future raw`() {
        // Regression: when the user toggles "Always trust" without editing the merchant,
        // the rule must be stored in cleaned form so the next ingestion of the same raw
        // body actually matches. Storing raw "Swiggy using UPI" would never fire.
        val raw = "Swiggy using UPI"
        val storedPattern = MerchantNameUtils.clean(raw)
        assertEquals("Swiggy", storedPattern)
        assertEquals(true, MerchantNameUtils.matchesPattern(raw, storedPattern))
        assertEquals(true, MerchantNameUtils.matchesPattern("SWIGGY using UPI", storedPattern))
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `clean handles degenerate ' at ' with nothing after it`() {
        // " at " at end → segment after is empty → falls back to Unnamed
        assertEquals("Unnamed", MerchantNameUtils.clean("HDFC Credit Card xx1234 at "))
    }

    @Test
    fun `clean handles bare ' at ' string`() {
        assertEquals("Unnamed", MerchantNameUtils.clean(" at "))
    }

    @Test
    fun `clean handles merchant that is only whitespace after tail strip`() {
        assertEquals("Unnamed", MerchantNameUtils.clean("  using UPI  "))
    }

    @Test
    fun `clean does not strip when ' at ' appears inside merchant name`() {
        // "Pay at Café" — the merchant IS "Café", segment after " at " is correct
        assertEquals("Café", MerchantNameUtils.clean("Pay at Café"))
    }

    @Test
    fun `clean strips all recognised tail variants`() {
        assertEquals("Netflix", MerchantNameUtils.clean("Netflix via HDFC"))
        assertEquals("Netflix", MerchantNameUtils.clean("Netflix through Axis"))
        assertEquals("Netflix", MerchantNameUtils.clean("Netflix on ICICI Credit Card xx1234"))
    }

    @Test
    fun `clean picks the earliest tail when multiple are present`() {
        // "Swiggy using UPI on 09-May" — " using " comes first at index 6
        assertEquals("Swiggy", MerchantNameUtils.clean("Swiggy using UPI on 09-May"))
    }

    @Test
    fun `clean full ICICI HDFC-style body via CRED`() {
        assertEquals(
            "Zomato",
            MerchantNameUtils.clean("HDFC Credit Card xx1234 at Zomato on 06 May"),
        )
    }

    @Test
    fun `matchesPattern handles both inputs null or blank`() {
        assertEquals(false, MerchantNameUtils.matchesPattern(null, "Swiggy"))
        assertEquals(false, MerchantNameUtils.matchesPattern("Swiggy", ""))
        assertEquals(false, MerchantNameUtils.matchesPattern("   ", "Swiggy"))
    }
}
