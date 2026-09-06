package com.twocents.mobile.notifications

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import me.leolin.shortcutbadger.ShortcutBadger

/**
 * Keeps the launcher count independent from screen lifetimes. Notifications and
 * direct messages update separate buckets because public-room unread messages
 * intentionally do not contribute to the app icon badge.
 */
internal object LauncherBadge {
    private const val Store = "twocents-launcher-badge"
    private const val Notifications = "notifications"
    private const val DirectMessages = "direct-messages"
    private const val KnownDirectMessages = "known-direct-messages"

    fun setNotifications(context: Context, count: Int) = update(context, Notifications, count)
    fun setDirectMessages(context: Context, count: Int) = update(context, DirectMessages, count)

    fun setKnownDirectMessages(context: Context, roomUuids: Set<String>) {
        context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
            .edit().putStringSet(KnownDirectMessages, roomUuids).apply()
    }

    fun isKnownDirectMessage(context: Context, roomUuid: String): Boolean =
        roomUuid in context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
            .getStringSet(KnownDirectMessages, emptySet()).orEmpty()

    fun incrementNotifications(context: Context) = increment(context, Notifications)
    fun incrementDirectMessages(context: Context) = increment(context, DirectMessages)

    fun currentTotal(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
        return (prefs.getInt(Notifications, 0) + prefs.getInt(DirectMessages, 0)).coerceIn(0, 999)
    }

    private fun increment(context: Context, key: String) {
        val prefs = context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
        update(context, key, prefs.getInt(key, 0) + 1)
    }

    private fun update(context: Context, key: String, count: Int) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(Store, Context.MODE_PRIVATE)
        prefs.edit().putInt(key, count.coerceAtLeast(0)).apply()
        val total = currentTotal(app)
        // ShortcutBadger chooses the correct implementation for supported
        // launchers across manufacturers. OEM providers are fallbacks only.
        runCatching {
            if (total == 0) ShortcutBadger.removeCount(app) else ShortcutBadger.applyCount(app, total)
        }.onFailure { Log.d("LauncherBadge", "Cross-device badge update unavailable", it) }
        // Some ColorOS launchers report success to the compatibility library but
        // update only through their provider. This supplements—not replaces—the
        // universal path above and is never used on other manufacturers.
        if (Build.MANUFACTURER.equals("OPPO", ignoreCase = true)) {
            runCatching {
                app.contentResolver.call(
                    Uri.parse("content://com.android.badge/badge"),
                    "setAppBadgeCount",
                    null,
                    Bundle().apply { putInt("app_badge_count", total) },
                )
            }.onFailure { Log.d("LauncherBadge", "ColorOS badge fallback unavailable", it) }
        }
    }
}
