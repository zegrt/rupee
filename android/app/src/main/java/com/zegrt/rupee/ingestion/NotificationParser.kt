package com.zegrt.rupee.ingestion

import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity

interface NotificationParser {
    fun canParse(rawEvent: RawCaptureEventEntity): Boolean
    fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult
}

