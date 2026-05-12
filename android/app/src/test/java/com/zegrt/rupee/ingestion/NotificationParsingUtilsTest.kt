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
}
