package com.twocents.mobile.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

internal data class LocalActivitySummary(
    val unread: Int = 0,
    val replies: Int = 0,
    val messages: Int = 0,
    val todayUpvotes: Int = 0,
    val weekUpvotes: Int = 0,
    val todayReplies: Int = 0,
    val weekReplies: Int = 0,
    val todayFollowers: Int = 0,
    val weekFollowers: Int = 0,
)

/** Durable, bounded notification ledger used for client-side activity history. */
internal class NotificationHistoryStore(context: Context, private val userUuid: String) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = "history-$userUuid"

    fun reconcile(incoming: List<AppNotification>) = synchronized(Lock) {
        val merged = readUnlocked().associateByTo(linkedMapOf()) { it.uuid }
        incoming.forEach { remote ->
            val local = merged[remote.uuid]
            merged[remote.uuid] = remote.copy(
                userUuid = remote.userUuid.ifBlank { userUuid },
                readAt = remote.readAt ?: local?.readAt,
            )
        }
        writeUnlocked(merged.values)
    }

    fun upsert(notification: AppNotification) = reconcile(listOf(notification))

    fun markRead(uuids: Set<String>, readAt: String) = synchronized(Lock) {
        writeUnlocked(readUnlocked().map { if (it.uuid in uuids) it.copy(readAt = readAt) else it })
    }

    fun reconcileTotalUpvotes(authoritativeTotal: Int, nowMs: Long = System.currentTimeMillis()) = synchronized(Lock) {
        reconcileCounter("upvote", authoritativeTotal, nowMs)
    }

    fun reconcileFollowerCount(authoritativeTotal: Int, nowMs: Long = System.currentTimeMillis()) = synchronized(Lock) {
        reconcileCounter("follower", authoritativeTotal, nowMs)
    }

    private fun reconcileCounter(name: String, authoritativeTotal: Int, nowMs: Long) {
        val totalKey = "$name-total-$userUuid"
        if (!prefs.contains(totalKey)) {
            prefs.edit().putInt(totalKey, authoritativeTotal).apply()
            return
        }
        val previous = prefs.getInt(totalKey, authoritativeTotal)
        val delta = authoritativeTotal - previous
        if (delta == 0) return
        val eventsKey = "$name-deltas-$userUuid"
        val events = runCatching { JSONArray(prefs.getString(eventsKey, "[]")) }.getOrElse { JSONArray() }
        events.put(JSONObject().put("at", nowMs).put("delta", delta).put("total", authoritativeTotal))
        val retained = JSONArray()
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            if (event.optLong("at") >= nowMs - RetentionMs) retained.put(event)
        }
        prefs.edit().putInt(totalKey, authoritativeTotal).putString(eventsKey, retained.toString()).apply()
    }

    fun summary(nowMs: Long = System.currentTimeMillis()): LocalActivitySummary = synchronized(Lock) {
        val rows = readUnlocked()
        // "Today" is the user's calendar day, not a rolling 24-hour window.
        val dayAgo = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // "This week" starts Monday at 00:00 in the user's local timezone.
        val weekAgo = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
            .toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun AppNotification.time() = parseTime(createdAt)
        fun List<AppNotification>.countSince(cutoff: Long, types: Set<String>) = count { it.type in types && it.time() >= cutoff }
        fun netSince(name: String, cutoff: Long): Int {
            val deltas = runCatching { JSONArray(prefs.getString("$name-deltas-$userUuid", "[]")) }.getOrElse { JSONArray() }
            var total = 0
            for (index in 0 until deltas.length()) {
                val event = deltas.optJSONObject(index) ?: continue
                if (event.optLong("at") >= cutoff) total += event.optInt("delta")
            }
            return total
        }
        LocalActivitySummary(
            unread = rows.count { it.readAt == null },
            replies = rows.count { it.readAt == null && it.type in ReplyTypes },
            messages = rows.count { it.readAt == null && it.type == "room_reply" },
            todayUpvotes = netSince("upvote", dayAgo),
            weekUpvotes = netSince("upvote", weekAgo),
            todayReplies = rows.countSince(dayAgo, ReplyTypes),
            weekReplies = rows.countSince(weekAgo, ReplyTypes),
            todayFollowers = netSince("follower", dayAgo),
            weekFollowers = netSince("follower", weekAgo),
        )
    }

    /** Portable source data for the sidebar's locally reconstructed statistics. */
    fun exportJson(): JSONObject = synchronized(Lock) {
        JSONObject()
            .put("history", prefs.getString(key, "[]"))
            .put("upvoteTotal", prefs.getInt("upvote-total-$userUuid", 0))
            .put("upvoteDeltas", prefs.getString("upvote-deltas-$userUuid", "[]"))
            .put("followerTotal", prefs.getInt("follower-total-$userUuid", 0))
            .put("followerDeltas", prefs.getString("follower-deltas-$userUuid", "[]"))
    }

    fun importJson(root: JSONObject) = synchronized(Lock) {
        prefs.edit()
            .putString(key, root.optString("history", "[]"))
            .putInt("upvote-total-$userUuid", root.optInt("upvoteTotal"))
            .putString("upvote-deltas-$userUuid", root.optString("upvoteDeltas", "[]"))
            .putInt("follower-total-$userUuid", root.optInt("followerTotal"))
            .putString("follower-deltas-$userUuid", root.optString("followerDeltas", "[]"))
            .apply()
    }

    fun clear() = synchronized(Lock) {
        prefs.edit()
            .remove(key)
            .remove("upvote-total-$userUuid")
            .remove("upvote-deltas-$userUuid")
            .remove("follower-total-$userUuid")
            .remove("follower-deltas-$userUuid")
            .apply()
    }

    private fun readUnlocked(): List<AppNotification> {
        val array = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val uuid = item.optString("uuid").takeIf(String::isNotBlank) ?: continue
                val metaObject = item.optJSONObject("meta") ?: JSONObject()
                val meta = buildMap {
                    metaObject.keys().forEach { metaKey -> put(metaKey, metaObject.optString(metaKey)) }
                }
                add(AppNotification(uuid, item.optString("createdAt"), item.optString("userUuid"), item.optString("type"), item.optString("message"), item.optString("readAt").takeIf { it.isNotBlank() }, meta))
            }
        }
    }

    private fun writeUnlocked(rows: Collection<AppNotification>) {
        val cutoff = System.currentTimeMillis() - RetentionMs
        val retained = rows.asSequence().filter { parseTime(it.createdAt) >= cutoff }
            .sortedByDescending { parseTime(it.createdAt) }.take(MaxRows).toList()
        val array = JSONArray()
        retained.forEach { row ->
            array.put(JSONObject().put("uuid", row.uuid).put("createdAt", row.createdAt).put("userUuid", row.userUuid)
                .put("type", row.type).put("message", row.message).put("readAt", row.readAt ?: "")
                .put("meta", JSONObject(row.meta)))
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        val Lock = Any()
        const val PREFS = "twocents-notification-history-v1"
        const val MaxRows = 2_500
        const val RetentionMs = 180L * 24L * 60L * 60L * 1000L
        val ReplyTypes = setOf("post_replied", "comment_replied", "room_reply")
        fun parseTime(raw: String): Long = runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrDefault(0L)
    }
}
