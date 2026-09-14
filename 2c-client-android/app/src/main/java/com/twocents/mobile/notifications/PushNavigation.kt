package com.twocents.mobile.notifications

import android.content.Intent
import org.json.JSONObject

/** FCM system-tray taps carry raw extras; locally displayed pushes carry our explicit flag. */
internal fun handlePushNavigation(intent: Intent) {
    val extras = intent.extras ?: return
    if (!intent.getBooleanExtra("open_notifications", false) &&
        !extras.containsKey("google.message_id") && !extras.containsKey("gcm.message_id")) return
    val raw = JSONObject()
    extras.keySet().forEach { key -> extras.get(key)?.let { raw.put(key, it) } }
    val fields = mutableMapOf<String, String>()
    fun collect(source: JSONObject, depth: Int) {
        if (depth > 4) return
        source.keys().forEach { key ->
            val value = source.opt(key)
            if (value is String && value.isNotBlank() && value != "null") fields[key] = value
            if (key in setOf("notification", "payload", "data", "notification_meta", "notificationMeta", "meta")) {
                val nested = value as? JSONObject ?: (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
                nested?.let { collect(it, depth + 1) }
            }
        }
    }
    collect(raw, 0)
    fun field(vararg keys: String) = keys.firstNotNullOfOrNull { fields[it] }
    NotificationNavigationBus.openNotifications(
        postUuid = field("post_uuid", "postUuid", "content_uuid", "contentUuid"),
        commentUuid = field("comment_uuid", "commentUuid", "reply_uuid", "replyUuid"),
        roomUuid = field("room_uuid", "roomUuid"),
        messageUuid = field("message_uuid", "messageUuid"),
        userUuid = field("follower_uuid", "followerUuid", "actor_uuid", "actorUuid", "author_uuid", "authorUuid"),
        fromPush = true,
    )
}
