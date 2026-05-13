package com.zegrt.rupee.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate calibration test. The Accept/Reject corpus below is drawn directly from
 * `dumps/rupee-notif-dumps-0.13.3-Nothing-A015-20260513-140757.jsonl` plus
 * representative bodies from docs/notification-ingestion-deep-dive.md §3.
 *
 * When a new dump arrives, copy 3-5 bodies that should obviously accept/reject
 * into here and run the test. If the gate fails on a real body, tune the
 * keyword lists in TransactionalGate.kt — that's how we keep the gate honest.
 */
class TransactionalGateTest {

    // ── Real transactions: gate must accept ────────────────────────────────────

    @Test
    fun `accept Kotak811 real UPI debit`() {
        val body = "₹10.00 sent via UPI\nAmount debited from XX4129. Check out details."
        assertAccept(body)
    }

    @Test
    fun `accept ICICI card swipe with full body`() {
        val body = "Rs 1,234.00 spent on ICICI Bank Card XX1234 on 12-May-26 at AMAZON. Avail Limit Rs 45,678."
        assertAccept(body)
    }

    @Test
    fun `accept HDFC bank A C debit`() {
        val body = "Sent Rs.500.00 From HDFC Bank A/C *1234 To MERCHANT On 12/05. Ref 123456."
        assertAccept(body)
    }

    @Test
    fun `accept GPay UPI paid`() {
        val body = "You paid ₹245.00 to Swiggy using UPI"
        assertAccept(body)
    }

    @Test
    fun `accept CRED bill due notification`() {
        val body = "Your ICICI card bill of ₹4,200 is due on 18 May. Pay via CRED."
        assertAccept(body)
    }

    @Test
    fun `accept EMI auto-debit`() {
        val body = "Your EMI of Rs.5,000 has been auto-debited from A/c XX1234."
        assertAccept(body)
    }

    @Test
    fun `accept credit received`() {
        val body = "Rs.1,500 credited to A/c XX1234 from MERCHANT on 12-May-26."
        assertAccept(body)
    }

    // ── Marketing pushes: gate must reject ─────────────────────────────────────

    @Test
    fun `reject Kotak Recurring Deposit promo - the original sin`() {
        // Entry #246 of the v0.13.3 dump. The body that started this whole
        // sprint by routing to Inbox as "Unnamed".
        val body = "That's all it takes? 👀\nJust ₹2,500/month → ₹64,415 with Kotak Recurring Deposit. T&C"
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject Kotak FD promo`() {
        // Entry #435 of the v0.13.3 dump.
        val body = "Is your ₹5,000 earning enough? 🤔\nPut it in a Kotak FD for assured 6.8% p.a. returns. T&C"
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject ICICI upgrade marketing`() {
        val body = "ICICI: Your account is eligible for Gold upgrade. Apply now to unlock premium benefits. T&C apply."
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject Paytm cashback offer`() {
        val body = "Paytm Rewards: Get ₹100 cashback. Limited period offer — claim now!"
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject PhonePe referral`() {
        val body = "PhonePe: Invite friends and earn ₹500. Click here to unlock rewards."
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject Flipkart shopping nudge`() {
        val body = "Add to cart now! Razr Fold From ₹89,999 — limited period offer."
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject OTP body`() {
        val body = "123456 is your OTP. Do not share with anyone. Valid for 5 minutes."
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject UPI collect-request from stranger`() {
        val body = "abc@upi has requested ₹500. Pay only if you know the requestor."
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject pre-approved loan offer`() {
        val body = "You are pre-approved for a personal loan of ₹2,00,000 at 10.5% p.a. Apply today."
        assertRejectsOnPromo(body)
    }

    // ── Bodies with neither verbs nor promo keywords ───────────────────────────

    @Test
    fun `reject empty body`() {
        val decision = TransactionalGate.evaluate("")
        assertEquals(TransactionalGate.RejectReason.EMPTY_BODY, (decision as TransactionalGate.Decision.Reject).reason)
    }

    @Test
    fun `reject non-financial chatter with no verb`() {
        val body = "Hey, are we still on for dinner tonight?"
        val decision = TransactionalGate.evaluate(body)
        assertEquals(
            TransactionalGate.RejectReason.NO_TRANSACTIONAL_VERB,
            (decision as TransactionalGate.Decision.Reject).reason,
        )
    }

    @Test
    fun `reject random app push with amount but no verb or promo word`() {
        // This is the gate doing exactly its job — a body that has an amount
        // but no money verb is not a transaction. Generic parser would have
        // happily extracted it as 0.55 MEDIUM Inbox before.
        val body = "Wallet balance: ₹500.00"
        val decision = TransactionalGate.evaluate(body)
        assertTrue(decision is TransactionalGate.Decision.Reject)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun assertAccept(body: String) {
        val decision = TransactionalGate.evaluate(body)
        assertTrue(
            "expected Accept for: $body but got $decision",
            decision is TransactionalGate.Decision.Accept,
        )
    }

    private fun assertRejectsOnPromo(body: String) {
        val decision = TransactionalGate.evaluate(body)
        assertTrue(
            "expected Reject for: $body but got $decision",
            decision is TransactionalGate.Decision.Reject,
        )
    }
}
