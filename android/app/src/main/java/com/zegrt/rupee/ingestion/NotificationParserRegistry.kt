package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity

class NotificationParserRegistry(
    private val parsers: List<NotificationParser>,
) {
    fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
        // First-canParse-wins. Confidence-max routing was considered (research
        // doc §6.4) but creates a real regression for branded UPI parsers
        // (Paytm/PhonePe/GPay tie or lose to GenericUpi on the same body
        // shape, swapping the providerHint). A brand-aware-bonus refactor
        // is the proper fix; deferred from this hotfix.
        val parser = parsers.firstOrNull { it.canParse(rawEvent) } ?: GenericNotificationParser()
        return parser.parse(rawEvent)
    }

    companion object {
        fun default(): NotificationParserRegistry {
            // Order matters — first canParse() wins. The ATM parser sits
            // ahead of the bank-specific parsers so ATM withdrawal bodies
            // route as CASH_WITHDRAWAL (which flattens to CASH_ADJUSTMENT
            // canonical type, kept out of monthly spend totals) instead
            // of as SPEND through IciciNotificationParser etc.
            return NotificationParserRegistry(
                parsers = listOf(
                    AtmNotificationParser(),
                    CredNotificationParser(),
                    IciciNotificationParser(),
                    KotakNotificationParser(),
                    GPayNotificationParser(),
                    PhonePeNotificationParser(),
                    PaytmNotificationParser(),
                    EmiNotificationParser(),
                    GenericUpiNotificationParser(),
                    GenericNotificationParser(),
                ),
            )
        }
    }
}
