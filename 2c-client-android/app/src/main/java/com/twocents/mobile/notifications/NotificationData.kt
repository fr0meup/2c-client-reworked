package com.twocents.mobile.notifications

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.core.realtime.RealtimeClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.twocents.mobile.ui.messages.OfflineModeStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class NotificationFilter(val label: String) {
    All("All"),
    Unread("Unread"),
    Replies("Replies"),
}

@Immutable
data class AppNotification(
    val uuid: String,
    val createdAt: String,
    val userUuid: String,
    val type: String,
    val message: String,
    val readAt: String?,
    val meta: Map<String, String>,
) {
    val postUuid: String?
        get() = listOf("post_uuid", "postUuid", "content_uuid", "contentUuid")
            .firstNotNullOfOrNull { meta[it]?.takeIf(String::isNotBlank) }
    val commentUuid: String?
        get() = listOf("comment_uuid", "commentUuid", "reply_uuid", "replyUuid")
            .firstNotNullOfOrNull { meta[it]?.takeIf(String::isNotBlank) }
    val roomUuid: String?
        get() = listOf("room_uuid", "roomUuid")
            .firstNotNullOfOrNull { meta[it]?.takeIf(String::isNotBlank) }
    val messageUuid: String?
        get() = listOf("message_uuid", "messageUuid")
            .firstNotNullOfOrNull { meta[it]?.takeIf(String::isNotBlank) }
    val actorUuid: String?
        get() = listOf(
            "voter_uuid", "follower_uuid", "replier_uuid", "actor_uuid", "author_uuid", "user_uuid",
            "from_user_uuid", "sender_uuid", "comment_author_uuid", "post_author_uuid",
            "voterUuid", "followerUuid", "replierUuid", "actorUuid", "authorUuid", "fromUserUuid",
        ).firstNotNullOfOrNull { key ->
            // Recipient fields must not hide a later, valid sender field.
            meta[key]?.trim()?.takeUnless { it.isEmpty() || it == "null" || it == userUuid }
        }
    val actorBalance: Double?
        get() = listOf(
            "actor_balance", "replier_balance", "voter_balance", "follower_balance", "author_balance",
            "sender_balance", "from_user_balance", "balance", "networth", "net_worth",
            "actorBalance", "replierBalance", "voterBalance", "followerBalance",
        ).firstNotNullOfOrNull { meta[it]?.toDoubleOrNull() }
    val actorSubscriptionType: Int
        get() = listOf("actor_subscription_type", "replier_subscription_type", "follower_subscription_type", "subscription_type", "subscriptionType").firstNotNullOfOrNull { meta[it]?.toIntOrNull() } ?: 0
}

@Immutable
data class NotificationUiState(
    val notifications: List<AppNotification> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val unreadCount: Int get() = notifications.count {
        it.readAt == null && NotificationPreferences.cachedEnabled(notificationCategory(it.type, it.roomUuid), push = false)
    }
    val replyCount: Int get() = notifications.count {
        it.readAt == null && NotificationPreferences.cachedEnabled(notificationCategory(it.type, it.roomUuid), push = false) &&
            (it.type == "post_replied" || it.type == "comment_replied" || it.type == "room_reply")
    }
}

data class PushPayload(
    val data: Map<String, String>,
    val title: String? = null,
    val body: String? = null,
)

object NotificationEventBus {
    private val mutableEvents = MutableSharedFlow<PushPayload>(extraBufferCapacity = 32)
    val events = mutableEvents.asSharedFlow()

    fun publish(payload: PushPayload) {
        mutableEvents.tryEmit(payload)
    }
}

@Stable
class NotificationController(
    private val api: RpcApi,
    private val auth: AuthState,
    context: Context,
) {
    var state by mutableStateOf(NotificationUiState())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val socketClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
    private val realtimeClient = RealtimeClient(socketClient)
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconciliationJob: Job? = null
    private var statsReconciliationJob: Job? = null
    private var socketWanted = false
    private var reconnectDelayMs = 2_000L
    private var loadedAt = 0L
    private val appContext = context.applicationContext
    private val history = NotificationHistoryStore(context, auth.userUuid)
    private val locallyReadAt = linkedMapOf<String, String>()

    init {
        NotificationCategory.entries.forEach { category ->
            NotificationPreferences.enabled(appContext, category, push = false)
        }
        scope.launch {
            NotificationEventBus.events.collect { payload ->
                applyLivePayload(payload)
            }
        }
    }

    suspend fun load(force: Boolean = false): Boolean {
        if (!force && loadedAt > 0L && System.currentTimeMillis() - loadedAt < 30_000L) return true
        val hadRows = state.notifications.isNotEmpty()
        state = state.copy(
            isLoading = !hadRows,
            isRefreshing = hadRows,
            error = null,
        )
        runCatching {
            val root = api.call("/v1/notifications/get", JSONObject(), auth) as? JSONObject
                ?: error("Notifications response was invalid")
            parseNotifications(root.optJSONArray("notifications"))
        }.onSuccess { notifications ->
            loadedAt = System.currentTimeMillis()
            val previous = state.notifications.associateBy { it.uuid }
            val merged = notifications.map { item ->
                // Read-state refreshes can omit actor metadata already received by push.
                item.copy(
                    meta = previous[item.uuid]?.meta.orEmpty() + item.meta,
                    readAt = locallyReadAt[item.uuid] ?: item.readAt,
                )
            }
            history.reconcile(merged)
            state = NotificationUiState(notifications = merged)
            publishLauncherBadge()
        }.onFailure { error ->
            if (error is CancellationException) throw error
            state = state.copy(
                isLoading = false,
                isRefreshing = false,
                error = error.message ?: "Unable to load notifications",
            )
        }
        reconcileUserStats()
        return state.error == null
    }

    suspend fun markRead(uuid: String) {
        val target = state.notifications.firstOrNull { it.uuid == uuid } ?: return
        if (target.readAt != null) return
        val markedAt = java.time.Instant.now().toString()
        locallyReadAt[uuid] = markedAt
        state = state.copy(
            notifications = state.notifications.map { item ->
                if (item.uuid == uuid) item.copy(readAt = markedAt) else item
            },
        )
        history.markRead(setOf(uuid), markedAt)
        publishLauncherBadge()
        runCatching {
            api.call(
                "/v1/notifications/read",
                JSONObject().put("notification_uuid", uuid),
                auth,
            )
        }.onFailure {
            if (locallyReadAt[uuid] == markedAt) locallyReadAt.remove(uuid)
            state = state.copy(
                notifications = state.notifications.map { item ->
                    if (item.uuid == uuid) item.copy(readAt = target.readAt) else item
                },
            )
            publishLauncherBadge()
        }
    }

    suspend fun markAllRead(filter: NotificationFilter = NotificationFilter.All) {
        val unread = state.notifications.filter { notification ->
            notification.readAt == null &&
                NotificationPreferences.cachedEnabled(notificationCategory(notification.type, notification.roomUuid), push = false) && when (filter) {
                NotificationFilter.All, NotificationFilter.Unread -> true
                NotificationFilter.Replies -> notification.type in setOf("post_replied", "comment_replied", "room_reply")
            }
        }
        if (unread.isEmpty()) return
        val unreadIds = unread.mapTo(hashSetOf()) { it.uuid }
        val markedAt = java.time.Instant.now().toString()
        unreadIds.forEach { locallyReadAt[it] = markedAt }
        state = state.copy(
            notifications = state.notifications.map { item ->
                if (item.uuid in unreadIds) item.copy(readAt = markedAt) else item
            },
        )
        history.markRead(unreadIds, markedAt)
        publishLauncherBadge()
        unread.forEach { notification ->
            runCatching {
                api.call(
                    "/v1/notifications/read",
                    JSONObject().put("notification_uuid", notification.uuid),
                    auth,
                )
            }
        }
    }

    fun startRealtime() {
        socketWanted = true
        if (OfflineModeStore.isEnabled(appContext, auth.userUuid)) return
        if (socket != null) return
        connectSocket()
    }

    fun setAppearOffline(enabled: Boolean) {
        if (enabled) {
            reconnectJob?.cancel()
            reconnectJob = null
            socket?.close(1000, "Appear offline enabled")
            socket = null
        } else if (socketWanted) {
            reconnectDelayMs = 2_000L
            connectSocket()
        }
    }

    fun stopRealtime() {
        socketWanted = false
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "App backgrounded")
        socket = null
    }

    fun dispose() {
        stopRealtime()
        reconciliationJob?.cancel()
        statsReconciliationJob?.cancel()
        socketClient.dispatcher.executorService.shutdown()
        scope.cancel()
    }

    private fun connectSocket() {
        if (!socketWanted || socket != null) return
        // Appear-offline suppresses the live channel only. Explicit refreshes still
        // use the API, which keeps badges and history recoverable.
        if (OfflineModeStore.isEnabled(appContext, auth.userUuid)) return
        val encodedToken = URLEncoder.encode(auth.token, StandardCharsets.UTF_8.toString())
        val request = Request.Builder()
            .url("wss://ds3y2js2k0.execute-api.us-east-2.amazonaws.com/ws/?token=$encodedToken")
            .build()
        socket = realtimeClient.open(
            request = request,
            onOpen = { _, _ -> reconnectDelayMs = 2_000L },
            onText = { _, text ->
                val raw = runCatching { JSONObject(text) }.getOrNull() ?: return@open
                scope.launch { applySocketPayload(raw) }
            },
            onClosed = { webSocket, _, _ ->
                if (socket === webSocket) socket = null
                scheduleReconnect()
            },
            onFailure = { webSocket, _, _ ->
                if (socket === webSocket) socket = null
                scheduleReconnect()
            },
        )
    }

    private fun scheduleReconnect() {
        if (!socketWanted || reconnectJob?.isActive == true) return
        if (OfflineModeStore.isEnabled(appContext, auth.userUuid)) return
        reconnectJob = scope.launch {
            // One reconnect job owns the backoff, preventing simultaneous socket
            // failures from multiplying active connections.
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 1.5).toLong().coerceAtMost(30_000L)
            connectSocket()
        }
    }

    private fun applySocketPayload(raw: JSONObject) {
        val eventType = raw.firstString("type", "action", "event").orEmpty().lowercase()
        if (eventType !in LIVE_NOTIFICATION_TYPES) return
        val nested = raw.optJSONObject("data")
            ?: raw.optJSONObject("payload")
            ?: raw.optJSONObject("notification")
            ?: raw
        val incoming = normalizeNotification(nested, fallbackType = eventType)
        // Some websocket events are only invalidations. Re-fetch when the payload
        // lacks a complete notification instead of inventing a partial card.
        if (incoming != null) upsert(incoming) else scheduleReconciliation()
    }

    private fun applyLivePayload(payload: PushPayload) {
        val raw = JSONObject()
        payload.data.forEach { (key, value) ->
            if (key == "data" || key == "payload" || key == "notification") {
                val nested = runCatching { JSONObject(value) }.getOrNull()
                raw.put(key, nested ?: value)
            } else {
                raw.put(key, value)
            }
        }
        val eventType = raw.firstString("type", "event", "action").orEmpty().lowercase()
        val nested = raw.optJSONObject("data")
            ?: raw.optJSONObject("payload")
            ?: raw.optJSONObject("notification")
            ?: raw
        val incoming = normalizeNotification(
            nested,
            fallbackType = eventType,
            fallbackMessage = payload.body,
        )
        if (incoming != null) upsert(incoming) else scheduleReconciliation()
    }

    private fun upsert(incoming: AppNotification) {
        val current = state.notifications
        val index = current.indexOfFirst { it.uuid == incoming.uuid }
        val next = if (index < 0) {
            listOf(incoming) + current
        } else {
            current.toMutableList().apply {
                val previous = this[index]
                this[index] = incoming.copy(readAt = previous.readAt ?: incoming.readAt)
            }
        }
        state = state.copy(notifications = next.take(500), isLoading = false)
        publishLauncherBadge()
        history.upsert(incoming)
        scheduleStatsReconciliation()
    }

    private fun publishLauncherBadge() {
        LauncherBadge.setNotifications(appContext, state.unreadCount)
    }

    private fun scheduleReconciliation() {
        if (reconciliationJob?.isActive == true) return
        reconciliationJob = scope.launch {
            delay(500)
            load(force = true)
        }
    }

    private fun scheduleStatsReconciliation() {
        statsReconciliationJob?.cancel()
        statsReconciliationJob = scope.launch {
            delay(450)
            reconcileUserStats()
        }
    }

    private suspend fun reconcileUserStats() {
        runCatching {
            api.call(
                "/v2/users/get",
                JSONObject().put("user_uuid", auth.userUuid).put("posts_limit", 0)
                    .put("comments_limit", 0).put("voted_posts_limit", 0),
                auth,
            ) as? JSONObject
        }.getOrNull()?.let {
            history.reconcileTotalUpvotes(it.optInt("totalUpvotes"))
            history.reconcileFollowerCount(it.optInt("aliasesReceived"))
        }
    }
}
