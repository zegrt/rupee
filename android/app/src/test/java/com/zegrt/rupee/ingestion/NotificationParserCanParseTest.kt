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
    private val genericUpi = GenericUpiNotificationParser()
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
