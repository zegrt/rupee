package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationParserCanParseTest {
    private val cred = CredNotificationParser()
    private val icici = IciciNotificationParser()
    private val gpay = GPayNotificationParser()
    private val phonepe = PhonePeNotificationParser()
    private val paytm = PaytmNotificationParser()
    private val genericUpi = GenericUpiNotificationParser()
    private val emi = EmiNotificationParser()
    private val atm = AtmNotificationParser()
    private val registry = NotificationParserRegistry.default()

    @Test
    fun `gpay parser matches its own package`() {
        val event = event(pkg = "com.google.android.apps.nbu.paisa.user", body = "anything")
        assertEquals(true, gpay.canParse(event))
    }

    @Test
    fun `gpay parser matches body that mentions Google Pay`() {
        val event = event(pkg = "com.zegrt.rupee", body = "₹245 paid via Google Pay")
        assertEquals(true, gpay.canParse(event))
    }

    @Test
    fun `gpay parser does NOT match plain UPI bodies any more`() {
        // Real PhonePe / Paytm / debug-mock bodies that just say "UPI" should fall
        // through to GenericUpi so the provider hint is honest.
        val event = event(pkg = "com.zegrt.rupee", body = "You paid ₹245.00 to Swiggy using UPI")
        assertEquals(false, gpay.canParse(event))
    }

    @Test
    fun `generic upi parser matches paid via UPI bodies regardless of package`() {
        val event = event(pkg = "com.zegrt.rupee", body = "You paid ₹245.00 to Swiggy using UPI")
        assertEquals(true, genericUpi.canParse(event))
    }

    @Test
    fun `generic upi parser ignores non-UPI bodies`() {
        val event = event(body = "Rs.3800.00 has been spent at BIGBASKET on ICICI Credit Card xx5678")
        assertEquals(false, genericUpi.canParse(event))
    }

    @Test
    fun `cred parser matches CRED card-spend bodies via brand`() {
        val event = event(
            pkg = "com.example.test",
            body = "₹1,499 spent on HDFC Credit Card via CRED at Zomato",
        )
        assertEquals(true, cred.canParse(event))
    }

    @Test
    fun `cred parser matches its own package even without brand in body`() {
        val event = event(
            pkg = "com.dreamplug.androidapp",
            body = "₹1,499 spent on HDFC Credit Card xx1234 at Zomato",
        )
        assertEquals(true, cred.canParse(event))
    }

    @Test
    fun `cred parser does NOT match ICICI body that mentions Credit Card`() {
        // Regression: the previous matcher used body.contains("cred") which
        // accidentally fired on the substring of "Credit Card".
        val event = event(
            pkg = "com.example.test",
            body = "Rs.3800.00 has been spent at MERCHANT on ICICI Credit Card xx5678",
        )
        assertEquals(false, cred.canParse(event))
    }

    @Test
    fun `icici parser matches ICICI card-spend bodies`() {
        val event = event(body = "Rs.3800.00 has been spent at BIGBASKET on ICICI Credit Card xx5678 on 07-MAY-26")
        assertEquals(true, icici.canParse(event))
    }

    @Test
    fun `registry routes the Swiggy UPI body to the generic-upi parser, not GPay`() {
        val event = event(pkg = "com.zegrt.rupee", body = "You paid ₹245.00 to Swiggy using UPI")
        val result = registry.parse(event)
        assertEquals("notification_upi_generic", result.parserKey)
        assertEquals("upi", result.providerHint)
    }

    @Test
    fun `registry still routes a real Google Pay body to GPay`() {
        val event = event(
            pkg = "com.google.android.apps.nbu.paisa.user",
            body = "Paid ₹245 to Swiggy using Google Pay",
        )
        val result = registry.parse(event)
        assertEquals("notification_gpay", result.parserKey)
        assertEquals("gpay", result.providerHint)
    }

    @Test
    fun `registry routes ICICI body to ICICI even when body mentions UPI elsewhere`() {
        // Defensive: a future ICICI alert that happens to also include "UPI" should
        // still go to the bank parser, not the generic UPI fallback.
        val event = event(body = "Rs.3800.00 has been spent at MERCHANT on ICICI Credit Card xx5678 (UPI)")
        val result = registry.parse(event)
        assertEquals("notification_icici", result.parserKey)
    }

    // ── PhonePe ───────────────────────────────────────────────────────────────

    @Test
    fun `phonepe parser matches its own package`() {
        val event = event(pkg = "com.phonepe.app", body = "anything")
        assertEquals(true, phonepe.canParse(event))
    }

    @Test
    fun `phonepe parser matches body containing PhonePe brand`() {
        val event = event(body = "₹500.00 sent to Swiggy via PhonePe")
        assertEquals(true, phonepe.canParse(event))
    }

    @Test
    fun `phonepe parser does NOT match a plain UPI body without PhonePe mention`() {
        val event = event(body = "You paid ₹245.00 to Swiggy using UPI")
        assertEquals(false, phonepe.canParse(event))
    }

    @Test
    fun `registry routes PhonePe body to phonepe parser`() {
        val event = event(pkg = "com.phonepe.app", body = "₹500 sent to Zomato via PhonePe")
        val result = registry.parse(event)
        assertEquals("notification_phonepe", result.parserKey)
        assertEquals("phonepe", result.providerHint)
    }

    @Test
    fun `registry routes PhonePe body-only (no package) to phonepe parser`() {
        val event = event(body = "₹500 sent to Zomato via PhonePe")
        val result = registry.parse(event)
        assertEquals("notification_phonepe", result.parserKey)
    }

    // ── Paytm ─────────────────────────────────────────────────────────────────

    @Test
    fun `paytm parser matches its own package`() {
        val event = event(pkg = "net.one97.paytm", body = "anything")
        assertEquals(true, paytm.canParse(event))
    }

    @Test
    fun `paytm parser matches body containing Paytm brand`() {
        val event = event(body = "₹500 paid to Swiggy via Paytm UPI")
        assertEquals(true, paytm.canParse(event))
    }

    @Test
    fun `paytm parser does NOT match a plain UPI body without Paytm mention`() {
        val event = event(body = "You paid ₹245.00 to Swiggy using UPI")
        assertEquals(false, paytm.canParse(event))
    }

    @Test
    fun `registry routes Paytm body to paytm parser`() {
        val event = event(pkg = "net.one97.paytm", body = "₹500 paid to Zomato via Paytm")
        val result = registry.parse(event)
        assertEquals("notification_paytm", result.parserKey)
        assertEquals("paytm", result.providerHint)
    }

    @Test
    fun `registry does not send Paytm body to GenericUpi`() {
        val event = event(body = "₹500 paid to Swiggy via Paytm UPI. UPI Ref: 123456")
        val result = registry.parse(event)
        assertEquals("notification_paytm", result.parserKey)
    }

    // ── EMI ───────────────────────────────────────────────────────────────────

    @Test
    fun `emi parser matches a real debit body`() {
        val event = event(body = "EMI of Rs.4,999 has been debited for iPhone 15 loan")
        assertEquals(true, emi.canParse(event))
    }

    @Test
    fun `emi parser matches a due-reminder body`() {
        val event = event(title = "EMI Reminder", body = "Your EMI of Rs.4,999 is due on 15-May-26")
        assertEquals(true, emi.canParse(event))
    }

    @Test
    fun `emi parser rejects marketing copy mentioning EMI`() {
        val event = event(body = "EMI options available on your next purchase")
        assertEquals(false, emi.canParse(event))
    }

    @Test
    fun `emi parser rejects body with EMI as substring of unrelated word`() {
        // "remind" / "demi" etc. used to fire the old loose check; word-boundary covers it.
        val event = event(body = "Reminder to update your KYC by Friday")
        assertEquals(false, emi.canParse(event))
    }

    // ── ATM (M3a) precedence ─────────────────────────────────────────────────
    //
    // The bug we're solving: ATM bodies posted by the ICICI iMobile app
    // (or any bank app) used to route through the bank's parser as
    // SPEND, inflating monthly spend totals by the withdrawn amount.
    // After M3a the ATM parser claims the body first and routes it as
    // CASH_WITHDRAWAL → CASH_ADJUSTMENT canonical type, excluded from
    // spend.

    @Test
    fun `atm parser claims hdfc cash wdl body`() {
        val event = event(body = "ATM Cash Wdl Rs.5,000 from A/c XX1234 at HDFC Bank ATM Delhi")
        assertEquals(true, atm.canParse(event))
    }

    @Test
    fun `atm parser claims icici cash withdrawal body`() {
        val event = event(
            pkg = "com.csam.icici.bank.imobile",
            body = "Cash withdrawal of Rs.10,000 from your ICICI Bank A/c XX5678 via ATM",
        )
        assertEquals(true, atm.canParse(event))
    }

    @Test
    fun `atm parser rejects non-ATM bodies`() {
        val event = event(body = "Rs.500 spent at Swiggy via UPI")
        assertEquals(false, atm.canParse(event))
    }

    @Test
    fun `registry routes ATM body to ATM parser even when posted by a bank app`() {
        // Load-bearing for the M3a registry-order guarantee. If anyone
        // ever shuffles the registry so AtmNotificationParser sits after
        // a bank parser, the result will be SPEND from that bank parser
        // instead of CASH_WITHDRAWAL — this test catches it.
        val result = registry.parse(
            event(
                pkg = "com.csam.icici.bank.imobile",
                body = "ATM Cash Wdl Rs.5,000 from A/c XX1234 on 12-May-26",
            )
        )
        assertEquals("notification_atm", result.parserKey)
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
        receivedAt = "2026-05-08T00:00:00Z",
        deviceEventTime = null,
        hashFingerprint = "test-fingerprint",
        ingestionStatus = RawCaptureIngestionStatus.CAPTURED,
        createdAt = "2026-05-08T00:00:00Z",
        updatedAt = "2026-05-08T00:00:00Z",
        syncStatus = SyncStatus.LOCAL_ONLY,
    )
}
