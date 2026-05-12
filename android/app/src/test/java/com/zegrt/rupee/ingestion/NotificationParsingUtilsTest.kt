package com.zegrt.rupee.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParsingUtilsTest {

    @Test
    fun `extracts due date in d-MMM-yyyy form`() {
        assertEquals(
            "2026-05-15",
            NotificationParsingUtils.extractDueDateIso("Total due Rs. 5000 by 15 May 2026", 2026),
        )
    }

    @Test
    fun `extracts due date in d-MMM-yy form`() {
        assertEquals(
            "2026-06-12",
            NotificationParsingUtils.extractDueDateIso("Min due Rs 1500 due on 12-Jun-26", 2026),
        )
    }

    @Test
    fun `extracts due date in numeric d-MM-yyyy form`() {
        assertEquals(
            "2026-05-15",
            NotificationParsingUtils.extractDueDateIso("Payment due 15/05/2026", 2026),
        )
    }

    @Test
    fun `handles 'September' as 'sept' abbreviation`() {
        assertEquals(
            "2026-09-04",
            NotificationParsingUtils.extractDueDateIso("Bill due on 4 Sept 2026", 2026),
        )
    }

    @Test
    fun `missing year defaults to current year`() {
        assertEquals(
            "2026-05-15",
            NotificationParsingUtils.extractDueDateIso("Due 15 May", 2026),
        )
    }

    @Test
    fun `returns null when there's no due phrase`() {
        assertNull(
            NotificationParsingUtils.extractDueDateIso("Spent Rs 100 at Swiggy on 5 May 2026", 2026),
        )
    }

    @Test
    fun `returns null when day is out of range`() {
        // LocalDate.of throws for invalid days; our runCatching swallows it.
        assertNull(
            NotificationParsingUtils.extractDueDateIso("Bill due 32 May 2026", 2026),
        )
    }

    // ── Network reference ────────────────────────────────────────────────────

    @Test
    fun `network ref extracts labelled UPI reference`() {
        val ref = NotificationParsingUtils.extractNetworkReference(
            "Paid ₹500 to Swiggy via UPI. UPI Ref 412356789012",
        )
        assertEquals("412356789012", ref?.id)
        assertEquals("UPI", ref?.type)
    }

    @Test
    fun `network ref extracts IMPS reference`() {
        val ref = NotificationParsingUtils.extractNetworkReference(
            "Rs 10,000 transferred via IMPS Ref No 412567890123",
        )
        assertEquals("412567890123", ref?.id)
        assertEquals("IMPS", ref?.type)
    }

    @Test
    fun `network ref extracts RRN for card transactions`() {
        val ref = NotificationParsingUtils.extractNetworkReference(
            "Rs 1234 spent on HDFC Card xx1234. RRN 412567890123",
        )
        assertEquals("412567890123", ref?.id)
        assertEquals("CARD_AUTH", ref?.type)
    }

    @Test
    fun `network ref falls back to generic Ref number`() {
        val ref = NotificationParsingUtils.extractNetworkReference(
            "Payment received. Ref No 412567890123",
        )
        assertEquals("412567890123", ref?.id)
        assertEquals("UPI", ref?.type)
    }

    @Test
    fun `network ref returns null when no reference is present`() {
        assertNull(
            NotificationParsingUtils.extractNetworkReference("Rs 500 spent at MERCHANT"),
        )
    }
}
