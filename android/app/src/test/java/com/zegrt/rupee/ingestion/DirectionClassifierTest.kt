package com.zegrt.rupee.ingestion

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bug surfaced from a real Pixel notification: when a friend received money
 * via GPay (phrased "<sender> sent you ₹10"), no credit verb in the original
 * vocabulary matched, the classifier returned UNKNOWN, and parsers' else branch
 * defaulted to SPEND. The friend's transactions tab showed an "expense" they
 * had actually received. Adding receiver-side phrases to the credit verb list
 * is the structural fix; this suite locks the routing in.
 */
class DirectionClassifierTest {

    @Test
    fun `gpay receiver phrasing 'sent you' classifies as IN`() {
        val direction = NotificationParsingUtils.classifyDirection("Cyril sent you ₹10")
        assertEquals(NotificationParsingUtils.MoneyDirection.IN, direction)
    }

    @Test
    fun `phonepe receiver 'you have received' classifies as IN`() {
        val direction = NotificationParsingUtils.classifyDirection(
            "You have received ₹500 from John via PhonePe"
        )
        assertEquals(NotificationParsingUtils.MoneyDirection.IN, direction)
    }

    @Test
    fun `paytm receiver 'paid you' classifies as IN`() {
        val direction = NotificationParsingUtils.classifyDirection(
            "Alice paid you ₹250 via Paytm UPI"
        )
        assertEquals(NotificationParsingUtils.MoneyDirection.IN, direction)
    }

    @Test
    fun `transferred to you classifies as IN`() {
        val direction = NotificationParsingUtils.classifyDirection(
            "₹1000 transferred to you by John Doe"
        )
        assertEquals(NotificationParsingUtils.MoneyDirection.IN, direction)
    }

    @Test
    fun `realistic spend body with 'paid to' still classifies as OUT`() {
        // Defensive: receiver-side credit phrases must not shadow real spend
        // bodies. The classic phrasing "Paid ₹245 to Swiggy" has 'paid' as a
        // debit verb and no receiver-shape ("paid you") phrase.
        val direction = NotificationParsingUtils.classifyDirection(
            "Paid ₹245 to Swiggy using Google Pay"
        )
        assertEquals(NotificationParsingUtils.MoneyDirection.OUT, direction)
    }

    @Test
    fun `receiver phrase wins when generic debit verb is also in body`() {
        // The actual bug: "Alice paid you ₹250" matches both 'paid you' (credit)
        // and bare 'paid' (debit). Strong receiver phrases short-circuit to IN
        // so this body classifies correctly instead of cancelling to UNKNOWN.
        val direction = NotificationParsingUtils.classifyDirection(
            "Alice paid you ₹250 via Paytm UPI"
        )
        assertEquals(NotificationParsingUtils.MoneyDirection.IN, direction)
    }

    @Test
    fun `existing 'credited' still resolves to IN`() {
        // Regression: adding new credit verbs shouldn't break the old ones.
        val direction = NotificationParsingUtils.classifyDirection(
            "Rs.500 credited to A/c XX1234"
        )
        assertEquals(NotificationParsingUtils.MoneyDirection.IN, direction)
    }

    @Test
    fun `body with both credit and debit verbs returns UNKNOWN`() {
        // E.g. "₹500 debited and ₹500 credited" — let the user adjudicate.
        val direction = NotificationParsingUtils.classifyDirection(
            "Rs.500 debited and Rs.500 credited to your account"
        )
        assertEquals(NotificationParsingUtils.MoneyDirection.UNKNOWN, direction)
    }
}
