package com.zegrt.rupee.diagnostics

import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.zegrt.rupee.BuildConfig
import java.io.File
import java.time.Instant

/**
 * Debug-only tool that captures every text-shaped value from every incoming
 * notification as a JSON line. Bootstraps a real-world corpus we can replay
 * against `NotificationExtractor` + parsers when porting them to the JSON
 * rule engine in Sprint 2.
 *
 * Writes to `getExternalFilesDir("notif-dumps")/dumps.jsonl` — one line per
 * notification. The folder is share-exportable so friends/family testing the
 * app can email the file back without ADB.
 *
 * No-op in release builds; the listener guard plus the BuildConfig.DEBUG
 * check here make it impossible to write user notification data to disk in
 * a release APK.
 */
object NotificationDumper {

    private const val DIR_NAME = "notif-dumps"
    private const val FILE_NAME = "dumps.jsonl"
    private const val MAX_BYTES = 2 * 1024 * 1024 // 2 MB rolling

    fun isEnabled(): Boolean = BuildConfig.DEBUG

    fun dump(context: Context, sbn: StatusBarNotification) {
        if (!isEnabled()) return
        runCatching {
            val file = ensureFile(context) ?: return
            val n = sbn.notification
            val json = buildString {
                append('{')
                appendField("when", Instant.now().toString()); append(',')
                appendField("pkg", sbn.packageName); append(',')
                appendField("postTime", sbn.postTime.toString()); append(',')
                appendField("flags", n.flags.toString()); append(',')
                appendField("category", n.category); append(',')
                appendField("tickerText", n.tickerText?.toString()); append(',')
                append("\"actions\":[")
                n.actions?.forEachIndexed { i, action ->
                    if (i > 0) append(',')
                    append('"').append(escape(action.title?.toString())).append('"')
                }
                append("],")
                append("\"extras\":{")
                val extras = n.extras ?: Bundle()
                var first = true
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    val rendered = renderExtraValue(value) ?: continue
                    if (!first) append(',')
                    first = false
                    appendField(key, rendered)
                }
                append('}')
                append('}')
            }
            // Cap the file at MAX_BYTES — drop the oldest half if exceeded.
            if (file.exists() && file.length() > MAX_BYTES) {
                val keep = file.readBytes().takeLast(MAX_BYTES / 2).toByteArray()
                file.writeBytes(keep)
            }
            file.appendText("$json\n")
        }
    }

    fun directory(context: Context): File? =
        context.getExternalFilesDir(DIR_NAME)

    fun fileSize(context: Context): Long =
        directory(context)?.let { File(it, FILE_NAME) }?.takeIf(File::exists)?.length() ?: 0L

    fun clear(context: Context) {
        // Wipe the live dump plus any shared-copy artefacts (rupee-notif-dumps-*.jsonl).
        directory(context)?.listFiles()?.forEach { it.delete() }
    }

    private fun ensureFile(context: Context): File? {
        val dir = directory(context) ?: return null
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    private fun renderExtraValue(value: Any?): String? = when (value) {
        null -> null
        is CharSequence -> value.toString().takeIf(String::isNotBlank)
        is Array<*> -> value
            .mapNotNull { (it as? CharSequence)?.toString()?.takeIf(String::isNotBlank) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" | ")
        is Boolean, is Number -> value.toString()
        // Bitmaps, Parcelables, PendingIntents — skip; they'd blow up the dump
        // and aren't text content anyway.
        else -> null
    }

    private fun StringBuilder.appendField(key: String, value: String?) {
        append('"').append(escape(key)).append("\":")
        if (value == null) {
            append("null")
        } else {
            append('"').append(escape(value)).append('"')
        }
    }

    private fun escape(s: String?): String {
        if (s == null) return ""
        val out = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
            }
        }
        return out.toString()
    }
}
