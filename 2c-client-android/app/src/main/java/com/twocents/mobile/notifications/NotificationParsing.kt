package com.twocents.mobile.notifications

import org.json.JSONArray
import org.json.JSONObject

/** Pure wire-format normalization shared by polling and push ingestion. */
internal fun parseNotifications(array: JSONArray?): List<AppNotification> = buildList {
    if (array == null) return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        normalizeNotification(item)?.let(::add)
    }
}

internal fun normalizeNotification(
    raw: JSONObject,
    fallbackType: String? = null,
    fallbackMessage: String? = null,
): AppNotification? {
    val candidates = listOfNotNull(
        raw,
        raw.optJSONObject("notification"),
        raw.optJSONObject("payload"),
        raw.optJSONObject("data"),
    )
    val source = candidates.firstOrNull { objectValue ->
        objectValue.firstString("uuid", "notification_uuid", "notificationUuid", "notification_id", "id") != null
    } ?: return null
    val uuid = source.firstString("uuid", "notification_uuid", "notificationUuid", "notification_id", "id")
        ?: return null
    val metaObject = source.optJSONObject("notification_meta")
        ?: source.optJSONObject("notificationMeta")
        ?: source.optJSONObject("meta")
        ?: source
    val meta = buildMap {
        listOf(source, metaObject).forEach { objectValue ->
            val keys = objectValue.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = objectValue.opt(key)
                if (value is String && value.isNotBlank()) put(key, value)
                if (value is Number) put(key, value.toString())
            }
        }
    }
    return AppNotification(
        uuid = uuid,
        createdAt = source.firstString("created_at", "createdAt") ?: java.time.Instant.now().toString(),
        userUuid = source.firstString("user_uuid", "userUuid") ?: "",
        type = normalizeType(source.firstString("type", "notification_type") ?: fallbackType),
        message = source.firstString("message", "body") ?: fallbackMessage.orEmpty(),
        readAt = source.firstString("read_at", "readAt"),
        meta = meta,
    )
}

internal fun notificationFromPush(payload: PushPayload, userUuid: String): AppNotification? {
    val raw = JSONObject()
    payload.data.forEach { (key, value) ->
        val nested = if (key in setOf("data", "payload", "notification")) runCatching { JSONObject(value) }.getOrNull() else null
        raw.put(key, nested ?: value)
    }
    val type = raw.firstString("type", "event", "action").orEmpty().lowercase()
    val source = raw.optJSONObject("data") ?: raw.optJSONObject("payload") ?: raw.optJSONObject("notification") ?: raw
    return normalizeNotification(source, type, payload.body)?.let { it.copy(userUuid = it.userUuid.ifBlank { userUuid }) }
}

private fun normalizeType(value: String?): String = when (val type = value.orEmpty().lowercase()) {
    "follow" -> "followed"
    "vote" -> "post_voted"
    in NOTIFICATION_TYPES -> type
    else -> "generic"
}

internal fun JSONObject.firstString(vararg keys: String): String? {
    for (key in keys) {
        val value = opt(key)
        if (value is String && value.isNotBlank() && value != "null") return value
        if (value is Number) return value.toString()
    }
    return null
}

private val NOTIFICATION_TYPES = setOf(
    "post_voted",
    "comment_voted",
    "post_replied",
    "comment_replied",
    "room_reply",
    "pick_post",
    "pick_resolved",
    "trending_post",
    "poll_voted",
    "followed",
    "followed_by",
    "generic",
    "balance_updated",
)

internal val LIVE_NOTIFICATION_TYPES = NOTIFICATION_TYPES + setOf(
    "notification",
    "notifications",
    "notif",
    "notification_created",
    "notificationcreated",
    "new_notification",
    "push_notification",
    "follow",
    "vote",
)
