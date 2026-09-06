package com.twocents.mobile.ui.common

import android.net.Uri
import com.twocents.mobile.notifications.NotificationNavigationBus
import com.twocents.mobile.ui.messages.RoomNavigationBus
import com.twocents.mobile.ui.profile.ProfileNavigationBus

/** Converts first-party web URLs into native destinations without a browser round-trip. */
object AppLinkRouter {
    fun open(raw: String): Boolean {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        val host = uri.host?.removePrefix("www.")?.lowercase() ?: return false
        if (host != "twocents.money" && host != "twocents.com") return false
        val parts = uri.pathSegments
        return when (parts.firstOrNull()?.lowercase()) {
            "user" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let { ProfileNavigationBus.open(it); true } ?: false
            "post" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let {
                NotificationNavigationBus.openNotifications(postUuid = it, commentUuid = uri.getQueryParameter("comment")); true
            } ?: false
            "rooms", "room" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let { RoomNavigationBus.open(it); true } ?: false
            else -> false
        }
    }
}
