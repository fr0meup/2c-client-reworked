package com.twocents.mobile.ui.profile

import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Routes author-pill taps without coupling reusable post and comment components to the shell. */
object ProfileNavigationBus {
    data class Request(val userUuid: String, val seedProfile: ComposeAuthorProfile? = null)

    private val requestChannel = Channel<Request>(Channel.BUFFERED)
    val requests = requestChannel.receiveAsFlow()
    internal var followPromptUuid by mutableStateOf<String?>(null)
        private set

    fun open(userUuid: String) {
        if (userUuid.isNotBlank()) requestChannel.trySend(Request(userUuid))
    }

    fun open(profile: ComposeAuthorProfile) {
        if (profile.uuid.isNotBlank()) requestChannel.trySend(Request(profile.uuid, profile))
    }

    fun openForFollow(userUuid: String) {
        if (userUuid.isBlank()) return
        followPromptUuid = userUuid
        requestChannel.trySend(Request(userUuid))
    }

    internal fun consumeFollowPrompt(userUuid: String) {
        if (followPromptUuid == userUuid) followPromptUuid = null
    }
}
