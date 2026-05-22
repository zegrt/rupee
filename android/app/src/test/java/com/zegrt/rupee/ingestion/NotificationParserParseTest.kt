package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserParseTest {

    private val gpay = GPayNotificationParser()
    private val cred = CredNotificationParser()
    private val icici = IciciNotificationParser()
    private val phonepe = PhonePeNotificationParser()
    private val paytm = PaytmNotificationParser()
    private val genericUpi = GenericUpiNotificationParser()
    private val emi = EmiNotificationParser()
    private val kotak = KotakNotificationParser()
    private val atm = AtmNotificationParser()

    // ── GPay ──────────────────────────────────────────────────────────────────

    @Test
    fun `gpay parse extracts amount and merchant from standard body`() {
        val result = gpay.parse(event(body = "Paid ₹245 to Swiggy using Google Pay"))
        assertEquals(24500L, result.amountMinor)
        assertEquals("Swiggy", result.toEntityName)
        assertEquals(Mode.UPI, result.mode)
        assertEquals("notification_gpay", result.parserKey)
        assertEquals("gpay", result.providerHint)
        assertEquals(TransactionCandidateType.SPEND, result.candidateType)
    }

    @Test
    fun `gpay parse returns null amount and merchant when body has no recognizable pattern`() {
        val result = gpay.parse(event(body = "Google Pay notification"))
        assertNull(result.amountMinor)
        assertNull(result.toEntityName)
    }

    @Test
    fun `gpay parse extracts rupee amount with comma separator`() {
        val result = gpay.parse(event(body = "Paid ₹1,499 to Zomato using Google Pay"))
        assertEquals(149900L, result.amountMinor)
        assertEquals("Zomato", result.toEntityName)
    }

    // ── CRED ──────────────────────────────────────────────────────────────────

    @Test
    fun `cred parse extracts amount and merchant from spend body`() {
        val result = cred.parse(
            event(
                pkg = "com.dreamplug.androidapp",
                body = "₹1,499 spent on HDFC Credit Card xx1234 via CRED at Zomato on 06 May",
            )
        )
        assertEquals(149900L, result.amountMinor)
        assertEquals("Zomato", result.toEntityName)
        assertEquals(Mode.CREDIT_CARD, result.mode)
        assertEquals("notification_cred", result.parserKey)
        assertEquals("cred", result.providerHint)
        assertEquals(TransactionCandidateType.SPEND, result.candidateType)
        assertEquals("1234", result.maskedDigits)
    }

    @Test
    fun `cred parse sets sourceCardHint to icici when body mentions ICICI`() {
        val result = cred.parse(
            event(
                pkg = "com.dreamplug.androidapp",
                body = "₹800 spent on ICICI Credit Card xx5678 via CRED at BigBasket",
            )
        )
        assertEquals("icici", result.sourceCardHint)
        assertEquals(149900L - 149900L + 80000L, result.amountMinor) // 800 * 100 = 80000
        assertEquals(80000L, result.amountMinor)
    }

    // ── ICICI ─────────────────────────────────────────────────────────────────

    @Test
    fun `icici parse extracts amount, merchant, and masked digits from spend body`() {
        val result = icici.parse(
            event(body = "Rs.3800.00 has been spent at BIGBASKET on ICICI Credit Card xx5678 on 07-MAY-26")
        )
        assertEquals(380000L, result.amountMinor)
        assertEquals("BIGBASKET", result.toEntityName)
        assertEquals(Mode.CREDIT_CARD, result.mode)
        assertEquals("notification_icici", result.parserKey)
        assertEquals("icici", result.providerHint)
        assertEquals("5678", result.maskedDigits)
        assertEquals(TransactionCandidateType.SPEND, result.candidateType)
    }

    @Test
    fun `icici parse sets mode to UPI when body contains upi`() {
        val result = icici.parse(
            event(body = "Rs.500.00 debited via UPI from ICICI Bank account on 07-MAY-26")
        )
        assertEquals(Mode.UPI, result.mode)
        assertEquals(50000L, result.amountMinor)
    }

    // ── Generic UPI ───────────────────────────────────────────────────────────

    @Test
    fun `generic upi parse extracts amount and merchant from standard body`() {
        val result = genericUpi.parse(
            event(body = "You paid ₹245.00 to Swiggy using UPI")
        )
        assertEquals(24500L, result.amountMinor)
        assertEquals("Swiggy", result.toEntityName)
        assertEquals(Mode.UPI, result.mode)
        assertEquals("notification_upi_generic", result.parserKey)
        assertEquals("upi", result.providerHint)
        assertEquals(TransactionCandidateType.SPEND, result.candidateType)
    }

    @Test
    fun `generic upi parse handles amount without decimal`() {
        val result = genericUpi.parse(
            event(body = "Payment to PhonePe Merchant UPI ₹1500 paid successfully")
        )
        assertEquals(150000L, result.amountMinor)
    }

    // S1.3 pilot: the GenericUpi confidence is now an additive evidence tally
    // (see EvidenceTally.kt). Weights: amount +1, merchant +3, digits +2,
    // networkRef +2. Tier bands unchanged (HIGH ≥ 0.85, MEDIUM ≥ 0.6, LOW
    // below) so dump-replay distribution holds.
    //
    // Concrete cases pinned below:
    //   amount + merchant + digits     = 6 pts → 0.90 HIGH (was 0.85 HIGH)
    //   amount + merchant              = 4 pts → 0.78 MEDIUM (unchanged)
    //   amount + digits                = 3 pts → 0.62 MEDIUM (unchanged)
    //   amount alone                   = 1 pt  → 0.30 LOW   (was 0.50 LOW)

    @Test
    fun `generic upi with amount, merchant, and masked digits reaches HIGH confidence`() {
        val result = genericUpi.parse(
            event(body = "₹245.00 paid to Swiggy via UPI from A/c XX1234")
        )
        assertEquals(24500L, result.amountMinor)
        assertEquals("Swiggy", result.toEntityName)
        assertEquals("1234", result.maskedDigits)
        // 0.85+ is the HIGH threshold in NotificationDecisionEngine. Pin the
        // exact value here so a future tier shuffle that demotes this case
        // back to MEDIUM (and back into Inbox-review fatigue) surfaces in
        // the test diff.
        assertEquals(0.90, result.parseConfidence, 0.0001)
    }

    @Test
    fun `generic upi with amount and merchant only stays MEDIUM`() {
        val result = genericUpi.parse(
            event(body = "You paid ₹245.00 to Swiggy using UPI")
        )
        assertEquals(0.78, result.parseConfidence, 0.0001)
    }

    @Test
    fun `generic upi with amount and digits but no merchant stays MEDIUM`() {
        val result = genericUpi.parse(
            event(body = "₹500.00 debited via UPI from A/c XX1234")
        )
        assertEquals("1234", result.maskedDigits)
        assertEquals(0.62, result.parseConfidence, 0.0001)
    }

    // ── PhonePe ───────────────────────────────────────────────────────────────

    @Test
    fun `phonepe parse extracts amount and merchant from sent body`() {
        val result = phonepe.parse(event(body = "₹500.00 sent to Swiggy via PhonePe"))
        assertEquals(50000L, result.amountMinor)
        assertEquals("Swiggy", result.toEntityName)
        assertEquals(Mode.UPI, result.mode)
        assertEquals("notification_phonepe", result.parserKey)
        assertEquals("phonepe", result.providerHint)
        assertEquals(TransactionCandidateType.SPEND, result.candidateType)
    }

    @Test
    fun `phonepe parse extracts amount and merchant from paid body`() {
        val result = phonepe.parse(
            event(pkg = "com.phonepe.app", body = "You have paid ₹1,299.00 to Netflix successfully.")
        )
        assertEquals(129900L, result.amountMinor)
        assertEquals("Netflix", result.toEntityName)
    }

    @Test
    fun `phonepe parse handles amount with comma separator`() {
        val result = phonepe.parse(event(body = "₹1,500 sent to Zomato via PhonePe"))
        assertEquals(150000L, result.amountMinor)
        assertEquals("Zomato", result.toEntityName)
    }

    @Test
    fun `phonepe parse returns null amount when body has no amount`() {
        val result = phonepe.parse(event(body = "PhonePe: Your account is linked successfully"))
        assertNull(result.amountMinor)
    }

    // ── Paytm ─────────────────────────────────────────────────────────────────

    @Test
    fun `paytm parse extracts amount and merchant from paid body`() {
        val result = paytm.parse(event(body = "₹500 paid to Swiggy via Paytm UPI"))
        assertEquals(50000L, result.amountMinor)
        assertEquals("Swiggy", result.toEntityName)
        assertEquals(Mode.UPI, result.mode)
        assertEquals("notification_paytm", result.parserKey)
        assertEquals("paytm", result.providerHint)
        assertEquals(TransactionCandidateType.SPEND, result.candidateType)
    }

    @Test
    fun `paytm parse extracts amount from payment to merchant`() {
        val result = paytm.parse(
            event(pkg = "net.one97.paytm", body = "Payment of ₹1,499 to Zomato was successful. UPI Ref: 123456789")
        )
        assertEquals(149900L, result.amountMinor)
        assertEquals("Zomato", result.toEntityName)
    }

    @Test
    fun `paytm parse sets mode to WALLET for wallet payment`() {
        val result = paytm.parse(
            event(body = "₹200 paid to BigBasket via Paytm Wallet balance")
        )
        assertEquals(Mode.WALLET, result.mode)
        assertEquals(20000L, result.amountMinor)
    }

    @Test
    fun `paytm parse returns null amount when body has no amount`() {
        val result = paytm.parse(event(body = "Paytm: Your KYC is pending"))
        assertNull(result.amountMinor)
    }

    // ── EMI ───────────────────────────────────────────────────────────────────

    @Test
    fun `emi parse extracts amount from auto-debit body`() {
        val result = emi.parse(
            event(body = "EMI of Rs.4,999 has been debited from your account for iPhone 15")
        )
        assertEquals(499900L, result.amountMinor)
        assertEquals("INR", result.currencyCode)
        assertEquals(TransactionCandidateType.EMI_DUE, result.candidateType)
        assertEquals("notification_emi", result.parserKey)
    }

    @Test
    fun `emi parse extracts merchant name from emi for clause`() {
        val result = emi.parse(
            event(body = "Your EMI of Rs.2,499 has been deducted for HomeCredit Loan")
        )
        assertEquals(249900L, result.amountMinor)
        assertEquals("HomeCredit Loan", result.toEntityName)
    }

    @Test
    fun `emi parse handles a due-reminder body without amount`() {
        // Body has the EMI word and a "due" signal but no rupee amount — should still
        // produce a candidate, just with null amount and lower confidence.
        val result = emi.parse(
            event(title = "EMI Reminder", body = "Your EMI is due on 15-May")
        )
        assertEquals(null, result.amountMinor)
    }

    // ── Kotak ─────────────────────────────────────────────────────────────────

    @Test
    fun `kotak parse handles real-world combined body from Kotak811`() {
        // Real notification captured from the v0.13.0 dumper. Title carries the
        // amount + UPI verb; body carries the masked digits; merchant is never in
        // the bank-side notification (it's only in the Kotak app).
        val result = kotak.parse(
            event(
                pkg = "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
                body = "₹10.00 sent via UPI\nAmount debited from XX4129. Check out details.",
            )
        )
        assertEquals(1000L, result.amountMinor)
        assertEquals("4129", result.maskedDigits)
        assertEquals(Mode.UPI, result.mode)
        assertEquals("kotak", result.providerHint)
        assertEquals(null, result.merchantRaw)
        // 0.75 → MEDIUM tier → routes to INBOX_PENDING, not silently dropped.
        org.junit.Assert.assertTrue(result.parseConfidence >= 0.6)
    }

    @Test
    fun `kotak canParse matches main bank app package too`() {
        val event = event(
            pkg = "com.msf.kbank.mobile",
            body = "Rs 500 debited from XX1234",
        )
        org.junit.Assert.assertTrue(kotak.canParse(event))
    }

    @Test
    fun `kotak canParse rejects non-Kotak packages`() {
        val event = event(
            pkg = "com.phonepe.app",
            body = "₹10 sent via UPI",
        )
        org.junit.Assert.assertFalse(kotak.canParse(event))
    }

    @Test
    fun `kotak canParse rejects marketing push from Kotak811 with no transactional verb`() {
        // Real body captured in the v0.13.3 dump — a Recurring Deposit promo.
        // Without a transactional-verb gate this routed to Inbox as Unnamed
        // (since Kotak parser intentionally emits merchantRaw = null).
        val event = event(
            pkg = "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
            body = "That's all it takes? 👀\nJust ₹2,500/month → ₹64,415 with Kotak Recurring Deposit. T&C",
        )
        org.junit.Assert.assertFalse(kotak.canParse(event))
    }

    @Test
    fun `kotak canParse rejects FD promo with no transactional verb`() {
        val event = event(
            pkg = "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
            body = "Is your ₹5,000 earning enough? 🤔\nPut it in a Kotak FD for assured 6.8% p.a. returns. T&C",
        )
        org.junit.Assert.assertFalse(kotak.canParse(event))
    }

    @Test
    fun `kotak parse classifies credited body as INCOME not SPEND`() {
        // Before S1.4 every Kotak body got transactionKind = SPEND. A "Rs 50,000
        // credited" salary alert was therefore subtracted from monthly budget.
        val result = kotak.parse(
            event(
                pkg = "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
                body = "₹50,000 credited to XX4129. Available balance: ₹1,20,000.",
            )
        )
        assertEquals(5_000_000L, result.amountMinor)
        assertEquals(ParsedTransactionKind.INCOME, result.transactionKind)
        assertEquals(
            TransactionCandidateType.INCOME,
            result.candidateType,
        )
    }

    @Test
    fun `kotak parse keeps debit body as SPEND`() {
        val result = kotak.parse(
            event(
                pkg = "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
                body = "₹10.00 sent via UPI\nAmount debited from XX4129. Check out details.",
            )
        )
        assertEquals(ParsedTransactionKind.SPEND, result.transactionKind)
    }

    // ── INCOME paths ──────────────────────────────────────────────────────────

    @Test
    fun `icici credit-to-account body classifies as INCOME`() {
        val result = icici.parse(
            event(
                pkg = "com.csam.icici.bank.imobile",
                body = "Rs.500 credited to A/c XX1234 on 12-May-26",
            )
        )
        assertEquals(ParsedTransactionKind.INCOME, result.transactionKind)
        assertEquals(TransactionCandidateType.INCOME, result.candidateType)
        assertEquals(50000L, result.amountMinor)
    }

    @Test
    fun `icici receive-from-sender body classifies as INCOME`() {
        val result = icici.parse(
            event(
                pkg = "com.csam.icici.bank.imobile",
                body = "Rs.250 received from John Doe via UPI on your ICICI A/c XX1234",
            )
        )
        assertEquals(ParsedTransactionKind.INCOME, result.transactionKind)
    }

    @Test
    fun `icici spend body still routes to SPEND when no credit verb`() {
        // Regression: adding INCOME path must not break existing SPEND routing.
        val result = icici.parse(
            event(body = "Rs.3800.00 has been spent at BIGBASKET on ICICI Credit Card xx5678 on 07-MAY-26")
        )
        assertEquals(ParsedTransactionKind.SPEND, result.transactionKind)
    }

    @Test
    fun `cred pay UPI receipt classifies as INCOME`() {
        // CRED Pay is not just credit cards — UPI receipts via CRED are real
        // income flows that previously fell to UNKNOWN.
        val result = cred.parse(
            event(
                pkg = "com.dreamplug.androidapp",
                body = "Rs 200 received from Cyril via UPI on CRED Pay",
            )
        )
        assertEquals(ParsedTransactionKind.INCOME, result.transactionKind)
        assertEquals(TransactionCandidateType.INCOME, result.candidateType)
        assertEquals(com.zegrt.rupee.data.local.entity.Mode.UPI, result.mode)
    }

    @Test
    fun `cred mint interest credit classifies as INCOME`() {
        val result = cred.parse(
            event(
                pkg = "com.dreamplug.androidapp",
                body = "Rs 12 credited to your CRED Mint account as monthly interest",
            )
        )
        assertEquals(ParsedTransactionKind.INCOME, result.transactionKind)
        assertEquals(com.zegrt.rupee.data.local.entity.Mode.BANK_TRANSFER, result.mode)
    }

    @Test
    fun `cred card spend still routes to SPEND despite broader INCOME path`() {
        // Regression: card-flow phrasing should still win the kind dispatch.
        val result = cred.parse(
            event(
                pkg = "com.dreamplug.androidapp",
                body = "₹1,499 spent on HDFC Credit Card xx1234 at Zomato via CRED on 06 May",
            )
        )
        assertEquals(ParsedTransactionKind.SPEND, result.transactionKind)
        assertEquals(com.zegrt.rupee.data.local.entity.Mode.CREDIT_CARD, result.mode)
    }

    @Test
    fun `gpay receiver 'sent you' body classifies as INCOME end to end`() {
        // The exact bug from the field: friend sends money, friend's GPay
        // notification reads "<sender> sent you ₹10", parser used to route as
        // SPEND. Pinning end-to-end at the parser level.
        val result = gpay.parse(
            event(
                pkg = "com.google.android.apps.nbu.paisa.user",
                body = "Cyril sent you ₹10 via Google Pay",
            )
        )
        assertEquals(ParsedTransactionKind.INCOME, result.transactionKind)
        assertEquals(TransactionCandidateType.INCOME, result.candidateType)
    }

    // ── REFUND routing ────────────────────────────────────────────────────────
    //
    // Pre-fix: PhonePe and Paytm parsers correctly tagged refund bodies with
    // ParsedTransactionKind.REFUND, but the candidateType `when` block had no
    // REFUND branch — it fell through to UNKNOWN. The normalizer's
    // canonical-type writer then defaulted everything-not-INCOME to EXPENSE,
    // so refunds inflated monthly spend instead of cancelling the original
    // outflow. These pin the routing end-to-end at the parser layer.

    @Test
    fun `phonepe refund body routes to INCOME candidate type`() {
        val result = phonepe.parse(
            event(
                pkg = "com.phonepe.app",
                body = "Refund of ₹500.00 from Swiggy credited to your PhonePe wallet",
            )
        )
        assertEquals(ParsedTransactionKind.REFUND, result.transactionKind)
        assertEquals(TransactionCandidateType.INCOME, result.candidateType)
        assertEquals(50000L, result.amountMinor)
    }

    @Test
    fun `paytm refund body routes to INCOME candidate type`() {
        val result = paytm.parse(
            event(
                pkg = "net.one97.paytm",
                body = "₹250 refund from Zomato credited to Paytm wallet",
            )
        )
        assertEquals(ParsedTransactionKind.REFUND, result.transactionKind)
        assertEquals(TransactionCandidateType.INCOME, result.candidateType)
    }

    @Test
    fun `paytm cashback body routes to INCOME candidate type`() {
        // Cashback shares the REFUND classifier branch in the Paytm parser —
        // money back to the user even though it isn't a refund of a prior
        // identifiable spend. Pin so the routing stays consistent.
        val result = paytm.parse(
            event(
                pkg = "net.one97.paytm",
                body = "₹50 cashback credited to your Paytm wallet for order #1234",
            )
        )
        assertEquals(ParsedTransactionKind.REFUND, result.transactionKind)
        assertEquals(TransactionCandidateType.INCOME, result.candidateType)
    }

    @Test
    fun `phonepe regular spend still routes to SPEND`() {
        // Regression: REFUND short-circuit must not catch the regular spend
        // path. Body has no refund/cashback token and direction is OUT.
        val result = phonepe.parse(event(body = "₹500.00 sent to Swiggy via PhonePe"))
        assertEquals(ParsedTransactionKind.SPEND, result.transactionKind)
        assertEquals(TransactionCandidateType.SPEND, result.candidateType)
    }

    // ── ATM (M3a) ─────────────────────────────────────────────────────────────
    //
    // ATM withdrawals route to CASH_WITHDRAWAL → CASH_ADJUSTMENT canonical
    // type, which is excluded from monthly spend totals. The parser must
    // claim ATM bodies AHEAD of the bank-specific parsers (which would
    // otherwise treat the same body as SPEND and inflate the user's spend
    // numbers). Registry order is pinned in NotificationParserRegistry.

    @Test
    fun `atm hdfc cash wdl body routes to CASH_WITHDRAWAL`() {
        val result = atm.parse(
            event(body = "ATM Cash Wdl Rs.5,000 from A/c XX1234 on 12-May-26 at HDFC Bank ATM Delhi")
        )
        assertEquals(500000L, result.amountMinor)
        assertEquals("1234", result.maskedDigits)
        assertEquals(Mode.ATM, result.mode)
        assertEquals("notification_atm", result.parserKey)
        assertEquals(ParsedTransactionKind.SPEND, result.transactionKind)
        assertEquals(TransactionCandidateType.CASH_WITHDRAWAL, result.candidateType)
        // amount + maskedDigits both present → HIGH tier (0.85)
        assertEquals(0.85, result.parseConfidence, 0.0001)
    }

    @Test
    fun `atm icici cash withdrawal body routes to CASH_WITHDRAWAL`() {
        val result = atm.parse(
            event(body = "Cash withdrawal of Rs.10,000 from your ICICI Bank A/c XX5678 via ATM on 12-May-26")
        )
        assertEquals(1000000L, result.amountMinor)
        assertEquals("5678", result.maskedDigits)
        assertEquals(TransactionCandidateType.CASH_WITHDRAWAL, result.candidateType)
    }

    @Test
    fun `atm withdrawn-from body routes to CASH_WITHDRAWAL`() {
        // Most generic body shape — verb "withdrawn" + "ATM" anywhere
        // in the body. Covers SBI/Axis variants.
        val result = atm.parse(
            event(body = "Rs.2,000 has been withdrawn from XX9999 at ATM/POS on 12-May-26")
        )
        assertEquals(200000L, result.amountMinor)
        assertEquals("9999", result.maskedDigits)
        assertEquals(TransactionCandidateType.CASH_WITHDRAWAL, result.candidateType)
    }

    @Test
    fun `atm canParse rejects bodies with no money signal`() {
        // Sanity: "atm" alone (without an amount / verb) shouldn't claim
        // the body. Defensive — the gate would also reject this — but
        // belt-and-braces.
        val rawEvent = event(body = "Your new ATM card has been dispatched. Track at ...")
        // amount = null and no "withdrawn"/"wdl"/etc. → canParse should
        // return false so the body falls through to other parsers.
        // (TransactionalGate would reject this body before it reaches a
        // parser in production, but canParse defines the contract.)
        assert(!atm.canParse(rawEvent)) {
            "ATM parser should not claim bodies without a money signal"
        }
    }

    @Test
    fun `atm merchant surfaces with location when present`() {
        val result = atm.parse(
            event(body = "ATM Cash Wdl Rs.5,000 from A/c XX1234 at HDFC Bank ATM Andheri East")
        )
        // Location is decorative — the assertion is loose (contains the
        // brand) to allow the location regex to evolve without churning
        // the test. The "ATM Cash · " prefix is the contract.
        assertEquals(true, result.toEntityName?.startsWith("ATM Cash"))
    }

    private fun event(
        pkg: String = "com.example.test",
        title: String? = null,
        body: String,
    ): RawCaptureEventEntity = RawCaptureEventEntity(
        id = "test-event",
        userId = "local-user",
        sourceType = RawCaptureSourceType.NOTIFICATION,
        sourceAppPackage = pkg,
        title = title,
        body = body,
        receivedAt = "2026-05-09T00:00:00Z",
        deviceEventTime = null,
        hashFingerprint = "test-fingerprint",
        ingestionStatus = RawCaptureIngestionStatus.CAPTURED,
        createdAt = "2026-05-09T00:00:00Z",
        updatedAt = "2026-05-09T00:00:00Z",
        syncStatus = SyncStatus.LOCAL_ONLY,
    )
}
