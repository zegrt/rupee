package com.zegrt.rupee.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputsTest {

    // -------------------------------------------------------------------------
    // Indian-grouping formatter
    // -------------------------------------------------------------------------

    @Test
    fun `empty input formats to empty`() {
        assertEquals("", formatIndianGrouping(""))
    }

    @Test
    fun `three digits or fewer pass through unchanged`() {
        assertEquals("0", formatIndianGrouping("0"))
        assertEquals("12", formatIndianGrouping("12"))
        assertEquals("100", formatIndianGrouping("100"))
    }

    @Test
    fun `four digits get one comma at the conventional 3-from-right position`() {
        assertEquals("1,000", formatIndianGrouping("1000"))
        assertEquals("9,999", formatIndianGrouping("9999"))
    }

    @Test
    fun `lakh-scale numbers use Indian grouping not Western`() {
        // Western would be "100,000"; Indian is "1,00,000".
        assertEquals("1,00,000", formatIndianGrouping("100000"))
        assertEquals("2,80,000", formatIndianGrouping("280000"))
        assertEquals("12,34,567", formatIndianGrouping("1234567"))
    }

    @Test
    fun `crore-scale numbers nest the 2-digit groups`() {
        // 1 crore = 1,00,00,000.
        assertEquals("1,00,00,000", formatIndianGrouping("10000000"))
        assertEquals("12,34,56,789", formatIndianGrouping("123456789"))
    }

    @Test
    fun `decimal portion is preserved verbatim after grouping`() {
        assertEquals("1,000.5", formatIndianGrouping("1000.5"))
        assertEquals("1,00,000.99", formatIndianGrouping("100000.99"))
        // Trailing dot is preserved so the user can keep typing.
        assertEquals("1,000.", formatIndianGrouping("1000."))
    }

    // -------------------------------------------------------------------------
    // Currency input sanitiser
    // -------------------------------------------------------------------------

    @Test
    fun `sanitise strips non-digit non-dot characters`() {
        assertEquals("1234", sanitizeCurrencyInput("1a2b3c4", allowPaise = true))
        assertEquals("1234", sanitizeCurrencyInput("Rs. 1,234", allowPaise = true))
    }

    @Test
    fun `sanitise allows only one decimal point`() {
        // First dot is kept, second is dropped.
        assertEquals("12.34", sanitizeCurrencyInput("12.34", allowPaise = true))
        assertEquals("12.34", sanitizeCurrencyInput("12.3.4", allowPaise = true))
    }

    @Test
    fun `sanitise caps paise at two digits`() {
        assertEquals("12.34", sanitizeCurrencyInput("12.3456", allowPaise = true))
        assertEquals("12.99", sanitizeCurrencyInput("12.9999", allowPaise = true))
    }

    @Test
    fun `sanitise preserves trailing dot for in-flight typing`() {
        assertEquals("12.", sanitizeCurrencyInput("12.", allowPaise = true))
    }

    @Test
    fun `sanitise with paise disabled strips all dots`() {
        assertEquals("1234", sanitizeCurrencyInput("12.34", allowPaise = false))
        assertEquals("1234", sanitizeCurrencyInput("12...34", allowPaise = false))
    }

    // -------------------------------------------------------------------------
    // ISO date validator
    // -------------------------------------------------------------------------

    @Test
    fun `iso date validator accepts a well-formed date`() {
        assertTrue(isValidIsoDate("2026-05-23"))
    }

    @Test
    fun `iso date validator rejects nonsense`() {
        assertFalse(isValidIsoDate("tomorrow"))
        assertFalse(isValidIsoDate("12/05/2026"))
        assertFalse(isValidIsoDate("2026-13-01")) // month 13
        assertFalse(isValidIsoDate("2026-05-32")) // day 32
    }

    @Test
    fun `iso date validator handles empty per emptyOk flag`() {
        assertFalse(isValidIsoDate("", emptyOk = false))
        assertTrue(isValidIsoDate("", emptyOk = true))
    }
}
