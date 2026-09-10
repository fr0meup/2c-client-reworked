package com.twocents.mobile.ui.messages

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.core.realtime.RealtimeClient
import com.twocents.mobile.core.format.parseApiInstant
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.json.JSONArray

/** Owns room I/O and optimistic state; the screen only renders this state. */
@Stable
internal class RoomChatController(
    private val api: RpcApi,
    private val auth: AuthState,
    private val room: RoomSummary,
    private val appContext: Context,
) {
    var state by mutableStateOf(RoomChatState())
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val socketClient = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    private val realtimeClient = RealtimeClient(socketClient)
    private var socket: WebSocket? = null
    private var socketWanted = false
    private var reconnectJob: Job? = null
    private var reconnectDelayMs = 2_000L
    private var typingActive = false
    private var typingStopJob: Job? = null
    private val typingExpiryJobs = mutableMapOf<String, Job>()

    suspend fun load(): Boolean {
        state = state.copy(loading = state.messages.isEmpty(), error = null)
        runCatching {
            val root = api.call(
                "/v1/rooms/getMessages",
                JSONObject().put("roomUuid", room.uuid).put("offset", 0).put("limit", 100),
                auth,
            ) as? JSONObject ?: error("Messages response was invalid")
            val messages = root.optJSONArray("messages").chatMessages()
                .sortedByDescending { parseApiInstant(it.createdAt)?.toEpochMilli() ?: 0L }
            val reactions = root.optJSONArray("reactions").chatReactions().groupBy(ChatReaction::messageUuid)
            state = state.copy(messages = messages, reactions = reactions, loading = false)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            state = state.copy(loading = false, error = error.message ?: "Couldn't load messages")
        }
        return state.error == null
    }

    fun connect() {
        if (OfflineModeStore.isEnabled(appContext, auth.userUuid)) {
            socketWanted = false
            state = state.copy(connected = false)
            return
        }
        socketWanted = true
        if (socket != null) return
        val token = URLEncoder.encode(auth.token, StandardCharsets.UTF_8.toString())
        socket = realtimeClient.open(
            request = Request.Builder().url("wss://ds3y2js2k0.execute-api.us-east-2.amazonaws.com/ws/?token=$token").build(),
            onOpen = { webSocket, _ ->
                reconnectDelayMs = 2_000L
                scope.launch { state = state.copy(connected = true) }
                webSocket.send(JSONObject().put("action", "joinRoom").put("roomUuid", room.uuid).toString())
            },
            onText = { _, text ->
                val raw = runCatching { JSONObject(text) }.getOrNull() ?: return@open
                scope.launch { applySocketEvent(raw) }
            },
            onClosed = { webSocket, _, _ ->
                if (socket === webSocket) socket = null
                scope.launch { state = state.copy(connected = false) }
                scheduleReconnect()
            },
            onFailure = { webSocket, _, _ ->
                if (socket === webSocket) socket = null
                scope.launch { state = state.copy(connected = false) }
                scheduleReconnect()
            },
        )
    }

    suspend fun send(text: String, reply: ChatMessage?, localImage: Uri?, remoteMedia: String?, context: Context): Boolean {
        if ((text.isBlank() && localImage == null && remoteMedia.isNullOrBlank()) || state.sending) return false
        val optimisticUuid = "opt-${System.nanoTime()}"
        val pendingMediaUrl = localImage?.toString() ?: remoteMedia
        // Add the pending row before uploading. The composer can clear immediately,
        // while its attachment remains visible in the conversation until the server
        // returns the durable message (the same behavior as pending text messages).
        val optimistic = ChatMessage(
            uuid = optimisticUuid,
            createdAt = Instant.now().toString(),
            roomUuid = room.uuid,
            authorUuid = auth.userUuid,
            text = text.trim(),
            replyToUuid = reply?.uuid,
            replyText = reply?.text,
            author = ChatAuthor(),
            mediaUrl = pendingMediaUrl,
            deleted = false,
            optimistic = true,
        )
        state = state.copy(sending = true, messages = listOf(optimistic) + state.messages)
        var imageUrl: String? = remoteMedia
        return runCatching {
            if (localImage != null) imageUrl = uploadImage(localImage, context)
            if (localImage == null && !remoteMedia.isNullOrBlank() && !remoteMedia.contains("api.twocents.money/ugc/")) {
                val (reportedType, bytes) = api.downloadBinary(remoteMedia)
                // The picker represents animated GIFs. Preserve that MIME type
                // for the upload contract even when a CDN reports octet-stream.
                val contentType = if (remoteMedia.substringBefore('?').endsWith(".gif", true)) "image/gif" else reportedType
                val upload = api.call(
                    "/v1/media/uploadImage",
                    JSONObject().put("contentType", contentType).put("size", bytes.size),
                    auth,
                ) as? JSONObject ?: error("GIF upload failed")
                api.putBytes(upload.getString("presignedURL"), bytes, contentType)
                imageUrl = upload.getString("publicURL")
            }
            // A socket enqueue isn't delivery confirmation and its connection is
            // closed on navigation. REST lets every submitted send finish and
            // report the server-confirmed outcome even after leaving the room.
                val result = api.call(
                    "/v1/rooms/sendMessageRest",
                    JSONObject().put("roomUuid", room.uuid).put("text", text.trim())
                        .put("replyToMessageUuid", reply?.uuid.orEmpty()).apply { imageUrl?.let { put("imageUrl", it) } },
                    auth,
                ) as? JSONObject ?: error("Message response was invalid")
                val confirmed = result.optJSONObject("message")?.toChatMessage()
                    ?: error("Sent message was missing from the response")
                replaceOptimistic(optimisticUuid, confirmed)
                imageUrl?.let { media ->
                    RoomMediaPreviewStore.put(appContext, room.uuid, if (media.substringBefore('?').endsWith(".gif", true)) "GIF" else "Image", confirmed.createdAt)
                }
            state = state.copy(sending = false)
            true
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            state = state.copy(messages = state.messages.filterNot { it.uuid == optimisticUuid }, sending = false, error = "Message failed to send")
            false
        }
    }

    fun toggleReaction(messageUuid: String, emoji: String) {
        if (OfflineModeStore.isEnabled(appContext, auth.userUuid)) return
        val current = state.reactions[messageUuid].orEmpty()
        val mine = current.firstOrNull { it.authorUuid == auth.userUuid && it.text == emoji }
        val action = if (mine == null) "addReaction" else "removeReaction"
        val next = if (mine == null) current + ChatReaction("opt-rx-${System.nanoTime()}", auth.userUuid, emoji, messageUuid)
        else current - mine
        state = state.copy(reactions = state.reactions + (messageUuid to next))
        socket?.send(JSONObject().put("action", action).put("messageUuid", messageUuid).put("text", emoji).toString())
    }

    fun updateTyping(hasText: Boolean) {
        if (OfflineModeStore.isEnabled(appContext, auth.userUuid)) return
        typingStopJob?.cancel()
        typingStopJob = null
        if (!hasText) {
            scope.launch { stopTypingNow() }
            return
        }
        if (!typingActive) {
            typingActive = true
            scope.launch {
                runCatching { api.call("/v2/rooms/typing/started", JSONObject().put("roomUuid", room.uuid), auth) }
                    .onFailure { typingActive = false }
            }
        }
        typingStopJob = scope.launch {
            delay(1_450)
            stopTypingNow()
        }
    }

    private suspend fun stopTypingNow() {
        if (!typingActive) return
        typingActive = false
        runCatching { api.call("/v2/rooms/typing/stopped", JSONObject().put("roomUuid", room.uuid), auth) }
    }

    suspend fun leave(): Boolean = runCatching {
        api.call("/v1/rooms/leaveRoom", JSONObject().put("roomUuid", room.uuid), auth)
        true
    }.getOrDefault(false)

    fun dispose() {
        socketWanted = false
        typingStopJob?.cancel()
        if (typingActive) {
            typingActive = false
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { api.call("/v2/rooms/typing/stopped", JSONObject().put("roomUuid", room.uuid), auth) }
            }
        }
        typingExpiryJobs.values.forEach(Job::cancel)
        typingExpiryJobs.clear()
        reconnectJob?.cancel()
        socket?.close(1000, "Room closed")
        socket = null
        socketClient.dispatcher.executorService.shutdown()
        scope.cancel()
    }

    private fun scheduleReconnect() {
        if (!socketWanted || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 1.5).toLong().coerceAtMost(30_000L)
            connect()
        }
    }

    private suspend fun uploadImage(uri: Uri, context: Context): String {
        val resolver = context.contentResolver
        val contentType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 }
            ?: resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }?.takeIf { it > 0 } ?: error("Selected image size is unavailable")
        val upload = api.call("/v1/media/uploadImage", JSONObject().put("contentType", contentType).put("size", size), auth) as? JSONObject
            ?: error("Image upload failed")
        api.putBinary(upload.getString("presignedURL"), resolver, uri, contentType, size)
        return upload.getString("publicURL")
    }

    private fun applySocketEvent(raw: JSONObject) {
        val type = raw.optString("type", raw.optString("action")).lowercase()
        val payload = raw.optJSONObject("data") ?: raw.optJSONObject("payload") ?: raw.optJSONObject("message") ?: raw
        when (type) {
            "message", "new_message", "message_created", "messagecreated", "room_message", "chat_message" -> {
                val messageJson = payload.optJSONObject("message") ?: payload
                val incoming = messageJson.toChatMessage() ?: return
                if (incoming.roomUuid != room.uuid) return
                // The server echo has the durable UUID; replace a matching optimistic
                // row instead of appending a duplicate copy of the sender's message.
                val matchedOptimistic = state.messages.any { current -> current.optimistic && current.authorUuid == incoming.authorUuid && current.text == incoming.text }
                val displayedIncoming = if (matchedOptimistic) incoming.copy(justSent = true) else incoming
                val withoutDuplicate = state.messages.filterNot { current ->
                    current.uuid == incoming.uuid || (current.optimistic && current.authorUuid == incoming.authorUuid && current.text == incoming.text)
                }
                state = state.copy(messages = (listOf(displayedIncoming) + withoutDuplicate).take(200))
                incoming.mediaUrl?.let { media ->
                    RoomMediaPreviewStore.put(appContext, room.uuid, if (media.substringBefore('?').endsWith(".gif", true)) "GIF" else "Image", incoming.createdAt)
                }
                if (matchedOptimistic) clearSentLabelLater(incoming.uuid)
            }
            "reaction" -> payload.toChatReaction()?.let { reaction ->
                val current = state.reactions[reaction.messageUuid].orEmpty().filterNot { it.authorUuid == reaction.authorUuid && it.text == reaction.text }
                state = state.copy(reactions = state.reactions + (reaction.messageUuid to (current + reaction)))
            }
            "reactionremoved" -> payload.toChatReaction()?.let { reaction ->
                val next = state.reactions[reaction.messageUuid].orEmpty().filterNot { it.authorUuid == reaction.authorUuid && it.text == reaction.text }
                state = state.copy(reactions = state.reactions + (reaction.messageUuid to next))
            }
            "typingstarted" -> {
                val authorUuid = payload.chatString("authorUuid", "author_uuid") ?: return
                if (payload.chatString("roomUuid", "room_uuid") != room.uuid || authorUuid == auth.userUuid) return
                typingExpiryJobs.remove(authorUuid)?.cancel()
                state = state.copy(typingAuthors = state.typingAuthors + authorUuid)
                typingExpiryJobs[authorUuid] = scope.launch {
                    delay(7_000)
                    state = state.copy(typingAuthors = state.typingAuthors - authorUuid)
                    typingExpiryJobs.remove(authorUuid)
                }
            }
            "typingstopped" -> {
                val authorUuid = payload.chatString("authorUuid", "author_uuid") ?: return
                typingExpiryJobs.remove(authorUuid)?.cancel()
                state = state.copy(typingAuthors = state.typingAuthors - authorUuid)
            }
        }
    }

    private fun replaceOptimistic(uuid: String, message: ChatMessage) {
        state = state.copy(messages = listOf(message.copy(justSent = true)) + state.messages.filterNot { it.uuid == uuid || it.uuid == message.uuid })
        clearSentLabelLater(message.uuid)
    }

    private fun clearSentLabelLater(uuid: String) {
        scope.launch {
            delay(1_350)
            state = state.copy(messages = state.messages.map { if (it.uuid == uuid) it.copy(justSent = false) else it })
        }
    }
}

internal val DirectChatMedia = Regex(
    "https?://[^\\s<>\"'`]+?\\.(?:gif|gifv|webp|png|jpe?g|apng|avif|bmp|heic|heif)(?:[?#][^\\s<>\"'`]*)?",
    RegexOption.IGNORE_CASE,
)

internal fun JSONArray?.chatMessages(): List<ChatMessage> = if (this == null) emptyList() else buildList { for (i in 0 until length()) optJSONObject(i)?.toChatMessage()?.let(::add) }
internal fun JSONArray?.chatReactions(): List<ChatReaction> = if (this == null) emptyList() else buildList { for (i in 0 until length()) optJSONObject(i)?.toChatReaction()?.let(::add) }
internal fun JSONObject.toChatMessage(): ChatMessage? {
    val uuid = chatString("uuid", "message_uuid", "id") ?: return null
    val authorMeta = optJSONObject("author_meta") ?: JSONObject()
    val messageMeta = optJSONObject("message_meta") ?: JSONObject()
    return ChatMessage(
        uuid, chatString("created_at", "createdAt") ?: Instant.now().toString(), chatString("room_uuid", "roomUuid").orEmpty(), chatString("author_uuid", "authorUuid").orEmpty(),
        optString("text"), chatString("reply_to_message_uuid", "replyToMessageUuid"), chatString("replyMessageText", "reply_message_text"),
        ChatAuthor(authorMeta.optDouble("balance"), authorMeta.optInt("subscription_type", 1), authorMeta.chatString("role"), authorMeta.chatString("alias", "username", "display_name")),
        chatString("giphy_url") ?: messageMeta.chatString("giphy_url", "giphy_id", "imageUrl", "image_url", "src"), !isNull("deleted_at"),
    )
}
internal fun JSONObject.toChatReaction(): ChatReaction? {
    val message = chatString("message_uuid", "messageUuid") ?: return null
    return ChatReaction(chatString("uuid", "id") ?: "rx-${hashCode()}", chatString("author_uuid", "authorUuid").orEmpty(), chatString("text").orEmpty(), message)
}
internal fun JSONObject.chatString(vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() && it != "null" } }
internal fun sameChatDay(a: String, b: String): Boolean = parseApiInstant(a)?.atZone(ZoneId.systemDefault())?.toLocalDate() == parseApiInstant(b)?.atZone(ZoneId.systemDefault())?.toLocalDate()
internal fun chatMinutesBetween(a: String, b: String): Long { val x = parseApiInstant(a) ?: return 0; val y = parseApiInstant(b) ?: return 0; return kotlin.math.abs(ChronoUnit.MINUTES.between(x, y)) }
