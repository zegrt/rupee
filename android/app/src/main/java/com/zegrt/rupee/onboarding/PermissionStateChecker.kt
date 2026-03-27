package com.zegrt.rupee.onboarding

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object PermissionStateChecker {
    fun hasNotificationAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false

        val expectedPackage = context.packageName
        return flat.split(':').any { value ->
            ComponentName.unflattenFromString(value)?.packageName == expectedPackage
        }
    }
}
