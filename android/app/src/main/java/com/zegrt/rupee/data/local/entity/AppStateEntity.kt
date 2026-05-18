package com.zegrt.rupee.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tiny key-value table for app-scoped persistent state that doesn't deserve
 * its own table. Currently used by debounce-timestamp persistence (recurring
 * pattern refresh, ingestion-row prune) so a cold start doesn't re-fire
 * within-debounce-window work. Add new keys here when something else needs
 * to survive process death without warranting a SharedPreferences / DataStore
 * dependency.
 *
 * Schema rationale: TEXT key + TEXT value keeps deserialization trivial; the
 * two current consumers store epoch-millis longs as decimal strings via
 * `Long.toString()` / `String.toLong()`. If a future key needs structured
 * data, JSON-encode and document the shape at the call site.
 */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: String,
)
