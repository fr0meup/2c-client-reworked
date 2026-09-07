package com.twocents.mobile.ui.profile

import com.twocents.mobile.notifications.AppNotification
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import java.time.Instant

/** Notification evidence supplements discovery; only a scan can discover an unfollow. */
internal fun FollowersScanSnapshot.withFollowNotifications(
    userUuid: String,
    notifications: List<AppNotification>,
): FollowersScanSnapshot {
    val candidates = candidates.toMutableSet() // UUIDs remain case-sensitive.
    val followers = followers.associateByTo(linkedMapOf()) { it.profile.uuid }
    val events = followEvents.toMutableMap()
    notifications.sortedBy { it.createdAt }.forEach { notification ->
        if (notification.type !in setOf("followed", "followed_by")) return@forEach
        if (notification.userUuid.isNotBlank() && notification.userUuid != userUuid) return@forEach
        val uuid = notification.actorUuid?.takeUnless { it == userUuid } ?: return@forEach
        candidates += uuid
        val time = runCatching { Instant.parse(notification.createdAt).toEpochMilli() }.getOrNull() ?: return@forEach
        // Persist event watermarks through backups so polling/reimport cannot replay an
        // old follow after hasMe has already confirmed that person no longer follows.
        if (time <= (events[uuid] ?: 0L)) return@forEach
        events[uuid] = time
        if (time <= completedAt) return@forEach
        val existing = followers[uuid]
        val profile = existing?.profile ?: ComposeAuthorProfile(uuid, subscriptionType = 0)
        followers[uuid] = FollowerEntry(existing?.alias, profile.copy(
            balance = notification.actorBalance ?: profile.balance,
            subscriptionType = notification.meta["follower_subscription_type"]?.toIntOrNull()
                ?: profile.subscriptionType,
        ))
    }
    return copy(candidates = candidates.toList(), followers = followers.values.toList(), followEvents = events)
}
