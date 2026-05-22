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

    @Test
    fun `accept GPay UPI Autopay native phrasing (GATE-AUTOPAY-VOCAB)`() {
        // The 2026-05-22 Nothing-A015 dump showed every native GPay autopay
        // (Spotify, Netflix, Hotstar, SIP, utility recurrence) being
        // rejected as NO_TRANSACTIONAL_VERB. None of the existing
        // auto-debit verbs catch the noun-form GPay phrasing.
        val body = "Payment to SPOTIFY INDIA PVT LTD was successful. Payment for Autopay of ₹199 to SPOTIFY INDIA PVT LTD was successful."
        assertAccept(body)
    }

    @Test
    fun `reject CRED Cash credit-line withdrawal promo (CRED-PROMO-BODY-GATE)`() {
        // The 2026-05-22 Nothing-A015 dump captured this body. Before the
        // negative-keyword + Indian-numbering fixes, this cleared the gate
        // via `withdraw ` (added for ATM bodies) and the amount regex
        // truncated ₹2,80,000 → ₹2, writing a ₹2.00 SPEND to Inbox with no
        // merchant. The gate must now reject on "available for you" /
        // "withdraw any amount" / "cred cash" / "start your first emi".
        val body = "₹2,80,000 available for you — withdraw any amount from your CRED cash account before May 31st and start your first EMI in July"
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject Truecaller spam-flagged Kotak loan offer from dump`() {
        // Entry #332 of the v0.13.3 dump.
        val body = "🚨 Spam · Dear Cyril, pre-approved Rs.65,000 Kotak Personal Loan is unlocked & can be disbursed instantly. Tap: https://1.kotak.bank.in/KOTAKB/XfhZe8 T&C"
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject clickbait instantly-credited promo`() {
        // Hypothetical clickbait the user flagged. Without the "instantly credited"
        // negative this would pass the gate (has "credited" positive verb) and
        // route to Inbox as a 0.55 MEDIUM income candidate.
        val body = "Get ₹10,000 instantly credited to your account when you apply for our personal loan"
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject SBI Life policy promo from dump`() {
        // Entry #184 — has "₹2CR Cover at ₹915/month" but no money verb.
        val body = "Get ₹2CR Cover at ₹915/month. No GST adds more Savings to your Policy"
        val decision = TransactionalGate.evaluate(body)
        assertTrue(decision is TransactionalGate.Decision.Reject)
    }

    @Test
    fun `reject Uber Parcel upto-rupees promo from dump`() {
        // Entry #277 — "upto ₹50" (no space) is the gap that "up to ₹" missed.
        val body = "Get upto ₹50 off on Parcel. Send items securely across town anytime with live tracking and pin verification"
        assertRejectsOnPromo(body)
    }

    @Test
    fun `reject MakeMyTrip vacation sale from dump`() {
        // Entry #377.
        val body = "Vacation ka Occasion Sale ☀️\nHurry and book your summer travel before the best deals of the season run out ⏳ Enjoy up to ₹500 OFF* on bus tickets, code: MMTVACATION. T&Cs apply."
        assertRejectsOnPromo(body)
    }

    // ── Real bodies that the gate previously false-rejected ───────────────────

    @Test
    fun `accept ICICI Gmail-forwarded transaction-of phrasing`() {
        // Entries #371/#372 of the v0.13.3 dump — Gmail showing a real ICICI
        // alert. Before adding "transaction of" / "used for a transaction" to
        // positive verbs, the gate false-rejected this as no-verb.
        val body = "Transaction alert for your ICICI Bank Credit Card\nDear Customer, Your ICICI Bank Credit Card XX3001 has been used for a transaction of INR 451.00 on May 13, 2026 at 12:49:59."
        assertAccept(body)
    }

    // ── M2 vocabulary gaps closed in phase 3a ──────────────────────────────────
    //
    // Each block below corresponds to a phrase added to POSITIVE_VERBS.
    // Before the fix, the gate false-rejected these as NO_TRANSACTIONAL_VERB
    // even though they describe real money movements.

    @Test
    fun `accept SIP installment debit`() {
        val body = "SIP of Rs.5,000 has been debited from your account towards HDFC Mutual Fund."
        assertAccept(body)
    }

    @Test
    fun `accept SIP installment without explicit debit verb`() {
        // The "debited" above would already pass — pin the no-other-verb shape
        // too, since some bank apps phrase it as just "SIP installment of …"
        val body = "SIP installment of Rs.5,000 processed for HDFC Top 100 Fund."
        assertAccept(body)
    }

    @Test
    fun `accept NACH mandate debit`() {
        val body = "Rs.12,500 has been processed via NACH mandate for LIC Premium."
        assertAccept(body)
    }

    @Test
    fun `accept ECS debit`() {
        val body = "ECS debit of Rs.3,200 has been processed from your account for HDFC home loan."
        assertAccept(body)
    }

    @Test
    fun `accept standing instruction executed`() {
        // "standing instruction" without any other verb still describes a
        // money movement — auto-pay execution alert.
        val body = "Standing instruction of Rs.999 has been executed for Netflix subscription."
        assertAccept(body)
    }

    @Test
    fun `accept refund of body`() {
        // "Refunded" is in POSITIVE_VERBS but the slight variant "Refund of"
        // would slip through without the dedicated phrase.
        val body = "Refund of Rs.499 from Swiggy has been processed to your account."
        assertAccept(body)
    }

    @Test
    fun `accept chargeback body`() {
        val body = "Chargeback of Rs.1,200 for transaction #ABC123 has been issued to your card."
        assertAccept(body)
    }

    @Test
    fun `accept reversal body`() {
        val body = "Reversal of Rs.500 has been initiated for your failed transaction."
        assertAccept(body)
    }

    @Test
    fun `accept reversed body`() {
        // Past-tense variant. Auto-debit failures often phrase it as "Rs.X
        // reversed to A/c YY1234".
        val body = "Rs.500 reversed to A/c XX1234 — original transaction declined."
        assertAccept(body)
    }

    // ── v0.14.1 — title-only debit shapes from the 2026-05-19 dump ────────────
    //
    // Bank apps on newer Android skins put the transaction in the TITLE and
    // leave the body for CTA copy. The combined-body the gate sees has the
    // transaction on the first line, but the verb shape ("sent FROM XX1234")
    // is different from the older "sent to / via" forms. Without these the
    // gate rejects real debits as NO_TRANSACTIONAL_VERB.

    @Test
    fun `accept Kotak811 native title-only debit`() {
        // From the v0.14.0 Nothing-A015 dump (2026-05-19) — title-only debit
        // notification. Combined body is title + body separated by newline.
        val body = "₹14.00 sent from XX4129\nLow balance! Add funds for seamless payment"
        assertAccept(body)
    }

    @Test
    fun `accept generic debit-from body`() {
        val body = "Rs.500 debit from A/c XX1234 on 19-May-26"
        assertAccept(body)
    }

    @Test
    fun `accept credit-to body`() {
        val body = "Rs.2,500 credit to A/c XX1234 — salary deposit"
        assertAccept(body)
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
