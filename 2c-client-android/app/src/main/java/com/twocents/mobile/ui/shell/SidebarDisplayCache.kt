package com.twocents.mobile.ui.shell

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.twocents.mobile.notifications.LocalActivitySummary
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.messages.RoomMember
import com.twocents.mobile.ui.messages.RoomSummary
import org.json.JSONArray
import org.json.JSONObject

internal data class SidebarActivityItem(
    val label: String,
    val preview: String,
    val postUuid: String,
    val commentUuid: String?,
    val createdAt: String,
)

internal data class SidebarCachedData(
    val followers: Int = 0,
    val following: Int = 0,
    val elo: Int = 1500,
    val joined: String? = null,
    val activity: LocalActivitySummary = LocalActivitySummary(),
    val recentActivity: List<SidebarActivityItem> = emptyList(),
    val leaderboardPosition: Int = 0,
    val leaderboardTotal: Int = 0,
    val profile: ComposeAuthorProfile? = null,
    val alias: String? = null,
    val recentRooms: List<RoomSummary> = emptyList(),
)

internal object SidebarDisplayCache {
    private val lock = Any()

    fun read(context: Context, userUuid: String): SidebarCachedData = synchronized(lock) {
        val raw = preferences(context, userUuid).getString("snapshot", null) ?: return@synchronized SidebarCachedData()
        runCatching { decode(JSONObject(raw)) }.getOrDefault(SidebarCachedData())
    }

    fun update(context: Context, userUuid: String, transform: (SidebarCachedData) -> SidebarCachedData) = synchronized(lock) {
        val next = transform(read(context, userUuid))
        preferences(context, userUuid).edit().putString("snapshot", encode(next).toString()).apply()
    }

    private fun preferences(context: Context, userUuid: String) =
        context.applicationContext.getSharedPreferences("twocents-sidebar-display-$userUuid", Context.MODE_PRIVATE)

    // This cache is display-only. Network responses remain authoritative and overwrite
    // the snapshot whenever the sidebar refresh completes.
    private fun encode(value: SidebarCachedData) = JSONObject()
        .put("followers", value.followers).put("following", value.following)
        .put("elo", value.elo).put("joined", value.joined ?: JSONObject.NULL)
        .put("leaderboardPosition", value.leaderboardPosition).put("leaderboardTotal", value.leaderboardTotal)
        .put("profile", value.profile?.let { profile -> JSONObject()
            .put("uuid", profile.uuid).put("balance", profile.balance).put("subscriptionType", profile.subscriptionType)
            .put("role", profile.role ?: JSONObject.NULL).put("gender", profile.gender ?: JSONObject.NULL)
            .put("age", profile.age ?: JSONObject.NULL).put("arena", profile.arena ?: JSONObject.NULL)
        } ?: JSONObject.NULL)
        .put("alias", value.alias ?: JSONObject.NULL)
        .put("recentRooms", JSONArray().apply {
            value.recentRooms.take(3).forEach { room ->
                put(JSONObject().put("uuid", room.uuid).put("name", room.name).put("description", room.description)
                    .put("roomType", room.roomType).put("roomCode", room.roomCode ?: JSONObject.NULL)
                    .put("isPrivate", room.isPrivate).put("unread", room.unread).put("memberCount", room.memberCount)
                    .put("lastMessage", room.lastMessage).put("lastMessageAt", room.lastMessageAt)
                    .put("totalMessages", room.totalMessages).put("members", JSONArray().apply {
                        room.members.forEach { member ->
                            put(JSONObject().put("uuid", member.uuid).put("balance", member.balance)
                                .put("subscriptionType", member.subscriptionType).put("role", member.role ?: JSONObject.NULL)
                                .put("alias", member.alias ?: JSONObject.NULL).put("username", member.username ?: JSONObject.NULL)
                                .put("online", member.online).put("gender", member.gender ?: JSONObject.NULL)
                                .put("age", member.age).put("arena", member.arena ?: JSONObject.NULL))
                        }
                    }))
            }
        })
        .put("activity", JSONObject()
            .put("unread", value.activity.unread).put("replies", value.activity.replies).put("messages", value.activity.messages)
            .put("todayUpvotes", value.activity.todayUpvotes).put("weekUpvotes", value.activity.weekUpvotes)
            .put("todayReplies", value.activity.todayReplies).put("weekReplies", value.activity.weekReplies)
            .put("todayFollowers", value.activity.todayFollowers).put("weekFollowers", value.activity.weekFollowers))
        .put("recentActivity", JSONArray().apply {
            value.recentActivity.forEach { item ->
                put(JSONObject().put("label", item.label).put("preview", item.preview).put("postUuid", item.postUuid)
                    .put("commentUuid", item.commentUuid ?: JSONObject.NULL).put("createdAt", item.createdAt))
            }
        })

    private fun decode(root: JSONObject): SidebarCachedData {
        val summary = root.optJSONObject("activity") ?: JSONObject()
        val rows = root.optJSONArray("recentActivity")
        val profile = root.optJSONObject("profile")?.let { item ->
            ComposeAuthorProfile(
                uuid = item.optString("uuid"), balance = item.optDouble("balance"),
                subscriptionType = item.optInt("subscriptionType", 1),
                role = item.optString("role").takeIf(String::isNotBlank),
                gender = item.optString("gender").takeIf(String::isNotBlank),
                age = item.optInt("age").takeIf { item.has("age") && !item.isNull("age") },
                arena = item.optString("arena").takeIf(String::isNotBlank),
            )
        }
        val roomRows = root.optJSONArray("recentRooms")
        return SidebarCachedData(
            followers = root.optInt("followers"), following = root.optInt("following"),
            elo = root.optInt("elo", 1500), joined = root.optString("joined").takeIf(String::isNotBlank),
            activity = LocalActivitySummary(
                unread = summary.optInt("unread"), replies = summary.optInt("replies"), messages = summary.optInt("messages"),
                todayUpvotes = summary.optInt("todayUpvotes"), weekUpvotes = summary.optInt("weekUpvotes"),
                todayReplies = summary.optInt("todayReplies"), weekReplies = summary.optInt("weekReplies"),
                todayFollowers = summary.optInt("todayFollowers"), weekFollowers = summary.optInt("weekFollowers"),
            ),
            recentActivity = buildList {
                if (rows != null) for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { item ->
                    val postUuid = item.optString("postUuid").takeIf(String::isNotBlank) ?: return@let
                    add(SidebarActivityItem(item.optString("label"), item.optString("preview"), postUuid,
                        item.optString("commentUuid").takeIf(String::isNotBlank), item.optString("createdAt")))
                }
            },
            leaderboardPosition = root.optInt("leaderboardPosition"),
            leaderboardTotal = root.optInt("leaderboardTotal"),
            profile = profile,
            alias = root.optString("alias").takeIf(String::isNotBlank),
            recentRooms = buildList {
                if (roomRows != null) for (index in 0 until roomRows.length()) roomRows.optJSONObject(index)?.let { room ->
                    val uuid = room.optString("uuid").takeIf(String::isNotBlank) ?: return@let
                    val members = room.optJSONArray("members")
                    add(RoomSummary(
                        uuid = uuid, name = room.optString("name"), description = room.optString("description"),
                        roomType = room.optString("roomType"), roomCode = room.optString("roomCode").takeIf(String::isNotBlank),
                        isPrivate = room.optBoolean("isPrivate"), gradients = listOf(Color.Black, Color.White),
                        unread = room.optInt("unread"), memberCount = room.optInt("memberCount"),
                        lastMessage = room.optString("lastMessage"), lastMessageAt = room.optString("lastMessageAt"),
                        members = buildList {
                            if (members != null) for (memberIndex in 0 until members.length()) members.optJSONObject(memberIndex)?.let { member ->
                                add(RoomMember(
                                    uuid = member.optString("uuid"), balance = member.optDouble("balance"),
                                    subscriptionType = member.optInt("subscriptionType"), role = member.optString("role").takeIf(String::isNotBlank),
                                    alias = member.optString("alias").takeIf(String::isNotBlank), username = member.optString("username").takeIf(String::isNotBlank),
                                    online = member.optBoolean("online"), gender = member.optString("gender").takeIf(String::isNotBlank),
                                    age = member.optInt("age"), arena = member.optString("arena").takeIf(String::isNotBlank),
                                ))
                            }
                        }, totalMessages = room.optInt("totalMessages"),
                    ))
                }
            },
        )
    }
}
