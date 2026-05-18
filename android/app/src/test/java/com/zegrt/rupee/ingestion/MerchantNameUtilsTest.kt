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
    fun `clean does not clip merchant names containing abbreviations with periods`() {
        // Regression for an earlier v0.10.1 build that included ". " as a tail and would
        // turn "St. Patrick's Restaurant" into "St".
        assertEquals("St. Patrick's Restaurant", MerchantNameUtils.clean("St. Patrick's Restaurant"))
        assertEquals(
            "St. Patrick's Restaurant",
            MerchantNameUtils.clean("St. Patrick's Restaurant using UPI"),
        )
    }

    @Test
    fun `matchesPattern handles both inputs null or blank`() {
        assertEquals(false, MerchantNameUtils.matchesPattern(null, "Swiggy"))
        assertEquals(false, MerchantNameUtils.matchesPattern("Swiggy", ""))
        assertEquals(false, MerchantNameUtils.matchesPattern("   ", "Swiggy"))
    }

    // ── M3b: fuel-brand normalisation ────────────────────────────────────────
    //
    // Petrol-station bodies vary across cities/outlets — "IOC OUTLET MUMBAI
    // 12345", "HPCL DELHI PETROL PUMP", etc. Without normalisation, a user
    // who sets "always trust IOC = Fuel" only gets the rule to fire on
    // the exact merchant they categorised. With normalisation, every
    // outlet of that brand maps to the same key.

    @Test
    fun `clean normalises IOC outlet body to bare IOC`() {
        assertEquals("IOC", MerchantNameUtils.clean("IOC OUTLET MUMBAI 12345"))
    }

    @Test
    fun `clean normalises Indian Oil to IOC`() {
        // Some bodies spell out the full company name.
        assertEquals("IOC", MerchantNameUtils.clean("INDIAN OIL CORP MUMBAI"))
    }

    @Test
    fun `clean normalises HPCL with location to bare HPCL`() {
        assertEquals("HPCL", MerchantNameUtils.clean("HPCL DELHI PETROL PUMP"))
    }

    @Test
    fun `clean normalises Hindustan Petroleum to HPCL`() {
        assertEquals("HPCL", MerchantNameUtils.clean("HINDUSTAN PETROLEUM BANGALORE"))
    }

    @Test
    fun `clean normalises BPCL with outlet code to BPCL`() {
        assertEquals("BPCL", MerchantNameUtils.clean("BPCL OUTLET 9876 PUNE"))
    }

    @Test
    fun `clean normalises Shell petrol stations to Shell`() {
        assertEquals("Shell", MerchantNameUtils.clean("SHELL FUEL STATION GURGAON"))
    }

    @Test
    fun `clean normalises Nayara petrol to Nayara`() {
        assertEquals("Nayara", MerchantNameUtils.clean("NAYARA ENERGY MUMBAI 4567"))
    }

    @Test
    fun `clean does NOT mistake BIOCON pharma for IOC`() {
        // Regression: "BIOCON" contains "IOC" as a substring. The padded
        // whole-word matcher must avoid this collision; if it ever
        // regresses to a bare substring check, the pharma company gets
        // mislabelled as a petrol station.
        assertEquals("BIOCON LIMITED", MerchantNameUtils.clean("BIOCON LIMITED"))
    }

    @Test
    fun `clean preserves non-fuel merchants untouched`() {
        // Counter-sanity: random merchants don't collide with the fuel
        // whitelist. Pin a small set to catch a future overly-broad rule.
        assertEquals("Swiggy", MerchantNameUtils.clean("Swiggy"))
        assertEquals("Amazon", MerchantNameUtils.clean("Amazon"))
        assertEquals("HDFC Bank", MerchantNameUtils.clean("HDFC Bank"))
    }

    @Test
    fun `normalizeFuelBrand returns null for non-fuel inputs`() {
        // Direct test of the helper — useful for triage if a future change
        // makes a brand match too aggressively.
        assertEquals(null, MerchantNameUtils.normalizeFuelBrand("Zomato"))
        assertEquals(null, MerchantNameUtils.normalizeFuelBrand("BIOCON LIMITED"))
    }
}
