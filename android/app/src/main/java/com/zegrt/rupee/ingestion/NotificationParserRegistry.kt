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
            return NotificationParserRegistry(
                parsers = listOf(
                    CredNotificationParser(),
                    IciciNotificationParser(),
                    GPayNotificationParser(),
                    PhonePeNotificationParser(),
                    PaytmNotificationParser(),
                    GenericUpiNotificationParser(),
                    GenericNotificationParser(),
                ),
            )
        }
    }
}
