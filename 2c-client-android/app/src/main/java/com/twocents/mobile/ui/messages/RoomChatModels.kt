package com.twocents.mobile.ui.messages

import androidx.compose.runtime.Immutable

@Immutable
internal data class ChatAuthor(
    val balance: Double = 0.0,
    val subscriptionType: Int = 1,
    val role: String? = null,
    val nickname: String? = null,
)

@Immutable
internal data class ChatMessage(
    val uuid: String,
    val createdAt: String,
    val roomUuid: String,
    val authorUuid: String,
    val text: String,
    val replyToUuid: String?,
    val replyText: String?,
    val author: ChatAuthor,
    val mediaUrl: String?,
    val deleted: Boolean,
    val optimistic: Boolean = false,
    val justSent: Boolean = false,
)

@Immutable
internal data class ChatReaction(
    val uuid: String,
    val authorUuid: String,
    val text: String,
    val messageUuid: String,
)

@Immutable
internal data class RoomChatState(
    val messages: List<ChatMessage> = emptyList(),
    val reactions: Map<String, List<ChatReaction>> = emptyMap(),
    val loading: Boolean = true,
    val sending: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val typingAuthors: Set<String> = emptySet(),
)
