package com.twocents.mobile.ui.messages

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** API aliases are normalized here before room data reaches Compose state. */
internal fun RoomSummary.otherMember(authUuid: String) = members.firstOrNull { it.uuid != authUuid }
internal fun parseRooms(root: JSONObject?): List<RoomSummary> = root?.optJSONArray("rooms").objects().mapNotNull { item ->
    val uuid = item.string("uuid", "room_uuid", "roomUuid") ?: return@mapNotNull null
    val stats = item.optJSONObject("stats")
    val gradients = (item.optJSONArray("gradients") ?: item.optJSONArray("gradient")).strings().mapNotNull(::parseColor)
    RoomSummary(
        uuid = uuid,
        name = item.string("name", "room_name").orEmpty(),
        description = item.string("description").orEmpty(),
        roomType = item.string("room_type", "roomType").orEmpty(),
        roomCode = item.string("room_code", "roomCode"),
        isPrivate = item.optBoolean("is_private", item.optBoolean("isPrivate")),
        gradients = gradients,
        unread = item.int("missedMessages", "missed_messages", "unread_count", "unreadCount"),
        memberCount = item.int("memberCount", "member_count", "members_count").takeIf { it > 0 } ?: item.optJSONArray("members")?.length().orZero(),
        lastMessage = stats?.string("lastMessage", "last_message", "message").orEmpty().ifBlank {
            mediaPreview(stats) ?: mediaPreview(item) ?: ""
        },
        lastMessageAt = stats?.string("lastMessageTimestamp", "last_message_timestamp", "last_message_at", "created_at").orEmpty(),
        members = item.optJSONArray("members").objects().map { member ->
            RoomMember(
                uuid = member.string("user_uuid", "userUuid", "uuid").orEmpty(),
                balance = member.double("balance", "networth", "net_worth"),
                subscriptionType = member.int("subscription_type", "subscriptionType").coerceAtLeast(0),
                role = member.string("role"), alias = member.string("alias", "display_name"), username = member.string("username"),
                online = member.optBoolean("is_online", member.optBoolean("isOnline")),
                gender = member.string("gender"),
                age = member.int("age"),
                arena = member.string("arena"),
                leftAt = member.string("left_at", "leftAt"),
            )
        },
        totalMessages = stats?.int("totalMessages", "total_messages") ?: 0,
        requirements = item.optJSONArray("requirements").objects().map { requirement ->
            RoomRequirement(
                met = requirement.optBoolean("met", true),
                label = requirement.string("humanReadableRequirement", "human_readable_requirement", "label").orEmpty(),
            )
        },
    )
}.orEmpty()

private fun JSONArray?.objects() = if (this == null) emptyList() else buildList { for (i in 0 until length()) optJSONObject(i)?.let(::add) }
private fun JSONArray?.strings() = if (this == null) emptyList() else buildList { for (i in 0 until length()) optString(i).takeIf(String::isNotBlank)?.let(::add) }
internal fun JSONObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() && it != "null" } }
private fun JSONObject.int(vararg keys: String): Int = keys.firstNotNullOfOrNull { key -> if (has(key) && !isNull(key)) optInt(key) else null } ?: 0
private fun JSONObject.double(vararg keys: String): Double = keys
    .firstNotNullOfOrNull { key -> if (has(key) && !isNull(key)) optDouble(key) else null }
    ?.takeIf(Double::isFinite) ?: 0.0
private fun Int?.orZero() = this ?: 0
private fun parseColor(raw: String): Color? = runCatching {
    val hex = raw.removePrefix("#")
    Color(((if (hex.length == 6) "FF" else "") + hex).toLong(16))
}.getOrNull()
private fun mediaPreview(root: JSONObject?, depth: Int = 0): String? {
    if (root == null || depth > 3) return null
    val keys = root.keys().asSequence().toList()
    keys.forEach { key ->
        val value = root.opt(key)
        val lowerKey = key.lowercase()
        val raw = value as? String
        if (!raw.isNullOrBlank()) {
            if (Regex("\\.(?:gif|gifv)(?:[?#].*)?$", RegexOption.IGNORE_CASE).containsMatchIn(raw) || lowerKey.contains("giphy")) return "GIF"
            if (Regex("\\.(?:mp4|mov|webm|m4v)(?:[?#].*)?$", RegexOption.IGNORE_CASE).containsMatchIn(raw) || lowerKey.contains("video")) return "Video"
            if (Regex("\\.(?:png|jpe?g|webp|apng|avif|bmp|heic|heif)(?:[?#].*)?$", RegexOption.IGNORE_CASE).containsMatchIn(raw) || lowerKey.contains("image")) return "Image"
        }
        if (value is JSONObject) mediaPreview(value, depth + 1)?.let { return it }
        if (value is JSONArray) for (index in 0 until value.length()) {
            (value.opt(index) as? JSONObject)?.let { mediaPreview(it, depth + 1) }?.let { return it }
        }
    }
    return null
}
private val RoomPreviewMedia = Regex("https?://\\S+?\\.(?:gif|gifv|webp|png|jpe?g|apng|avif|bmp|heic|heif)(?:[?#]\\S*)?", RegexOption.IGNORE_CASE)
internal fun cleanPreview(raw: String): String {
    val cleaned = raw.replace(Regex("[\\u200B-\\u200F\\u2060-\\u206F\\uFEFF]"), "").trim()
    return if (RoomPreviewMedia.containsMatchIn(cleaned)) cleaned.replace(RoomPreviewMedia, "").trim().ifBlank { "Media" }
    else cleaned.ifBlank { "Media" }
}
