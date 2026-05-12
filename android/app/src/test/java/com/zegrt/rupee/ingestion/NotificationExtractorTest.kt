package com.zegrt.rupee.ingestion

import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the [NotificationExtractor.extract] core. The Android-facing
 * [NotificationExtractor.fromNotification] wraps this with a Bundle adapter; the
 * adapter is trivial and tested separately via instrumentation when ready.
 */
class NotificationExtractorTest {

    @Test
    fun `BigTextStyle - body in EXTRA_BIG_TEXT is captured`() {
        // ICICI-style: title is brand-only, body in EXTRA_BIG_TEXT
        val result = NotificationExtractor.extract(
            extras = mapOf(
                NotificationCompat.EXTRA_TITLE to "ICICI Bank",
                NotificationCompat.EXTRA_TEXT to "",
                NotificationCompat.EXTRA_BIG_TEXT to "Rs 1,234.00 spent on ICICI Bank Card XX1234 at AMAZON on 12-May-26",
            ),
        )
        assertTrue(
            "combinedBody must contain the bigText",
            result.combinedBody.contains("Rs 1,234.00"),
        )
        assertTrue(result.combinedBody.contains("AMAZON"))
        assertEquals("ICICI Bank", result.title)
        assertEquals("Rs 1,234.00 spent on ICICI Bank Card XX1234 at AMAZON on 12-May-26", result.bigText)
    }

    @Test
    fun `Kotak-style - amount in title only is included in combinedBody`() {
        // Real-world Kotak811 shape: amount only in EXTRA_TITLE
        val result = NotificationExtractor.extract(
            extras = mapOf(
                NotificationCompat.EXTRA_TITLE to "₹10.00 sent via UPI",
                NotificationCompat.EXTRA_BIG_TEXT to "Amount debited from XX4129. Check out details.",
            ),
        )
        assertTrue(result.combinedBody.contains("₹10.00"))
        assertTrue(result.combinedBody.contains("XX4129"))
    }

    @Test
    fun `InboxStyle - EXTRA_TEXT_LINES array is captured`() {
        val result = NotificationExtractor.extract(
            extras = mapOf(
                NotificationCompat.EXTRA_TITLE to "Slice statement",
                NotificationCompat.EXTRA_TEXT_LINES to arrayOf<CharSequence>(
                    "Statement of ₹4,500 generated",
                    "Due on 25 May 2026",
                    "Min due ₹450",
                ),
            ),
        )
        assertEquals(3, result.textLines.size)
        assertTrue(result.combinedBody.contains("Statement of ₹4,500"))
        assertTrue(result.combinedBody.contains("Min due ₹450"))
    }

    @Test
    fun `Group summary flag is exposed`() {
        val result = NotificationExtractor.extract(
            extras = mapOf(NotificationCompat.EXTRA_TITLE to "3 new alerts"),
            flags = ExtractedNotification.FLAG_GROUP_SUMMARY,
        )
        assertTrue(result.isGroupSummary)
    }

    @Test
    fun `non-group notification reports isGroupSummary=false`() {
        val result = NotificationExtractor.extract(
            extras = mapOf(NotificationCompat.EXTRA_TITLE to "Transaction Alert"),
            flags = 0,
        )
        assertFalse(result.isGroupSummary)
    }

    @Test
    fun `duplicate strings across fields are deduplicated in combinedBody`() {
        // Many banks set EXTRA_TEXT and EXTRA_BIG_TEXT to the same string
        val same = "Rs 500 debited from A/c XX1234"
        val result = NotificationExtractor.extract(
            extras = mapOf(
                NotificationCompat.EXTRA_TITLE to "Bank Alert",
                NotificationCompat.EXTRA_TEXT to same,
                NotificationCompat.EXTRA_BIG_TEXT to same,
            ),
        )
        // The body string should not contain the same phrase twice.
        val occurrences = result.combinedBody.split(same).size - 1
        assertEquals(1, occurrences)
    }

    @Test
    fun `ticker text is folded into combinedBody`() {
        val result = NotificationExtractor.extract(
            extras = mapOf(NotificationCompat.EXTRA_TITLE to "Bank Alert"),
            ticker = "HDFC: Rs.500 debited",
        )
        assertTrue(result.combinedBody.contains("HDFC: Rs.500 debited"))
    }

    @Test
    fun `MessagingStyle messages are folded into combinedBody`() {
        val result = NotificationExtractor.extract(
            extras = mapOf(NotificationCompat.EXTRA_TITLE to "Bank Chat"),
            messages = listOf("Your ₹2,000 EMI is due tomorrow", "Tap to pay"),
        )
        assertTrue(result.combinedBody.contains("Your ₹2,000 EMI is due tomorrow"))
        assertTrue(result.combinedBody.contains("Tap to pay"))
    }

    @Test
    fun `action labels are folded into combinedBody`() {
        val result = NotificationExtractor.extract(
            extras = mapOf(
                NotificationCompat.EXTRA_TITLE to "ICICI Alert",
                NotificationCompat.EXTRA_BIG_TEXT to "Rs 500 spent",
            ),
            actionLabels = listOf("Dispute", "Mark as paid"),
        )
        assertTrue(result.combinedBody.contains("Dispute"))
        assertTrue(result.combinedBody.contains("Mark as paid"))
    }

    @Test
    fun `empty extras produces empty combinedBody`() {
        val result = NotificationExtractor.extract(extras = emptyMap())
        assertEquals("", result.combinedBody)
    }

    @Test
    fun `blank string values are skipped`() {
        val result = NotificationExtractor.extract(
            extras = mapOf(
                NotificationCompat.EXTRA_TITLE to "",
                NotificationCompat.EXTRA_TEXT to "  ",
                NotificationCompat.EXTRA_BIG_TEXT to "Real content",
            ),
        )
        assertEquals("Real content", result.combinedBody)
    }

    @Test
    fun `unknown extras key with CharSequence value still gets captured`() {
        // Future-proof: if a bank invents an "android.someBankExtra" key with
        // a CharSequence payload, the sweep should still pull it.
        val result = NotificationExtractor.extract(
            extras = mapOf(
                NotificationCompat.EXTRA_TITLE to "Bank Alert",
                "android.someBankExtra" to "Rs 750 paid to MERCHANT_X",
            ),
        )
        assertTrue(result.combinedBody.contains("Rs 750 paid to MERCHANT_X"))
    }
}
