package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity

class NotificationParserRegistry(
    private val parsers: List<NotificationParser>,
) {
    fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
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
