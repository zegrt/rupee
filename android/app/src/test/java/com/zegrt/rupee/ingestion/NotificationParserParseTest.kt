package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.Mode
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
