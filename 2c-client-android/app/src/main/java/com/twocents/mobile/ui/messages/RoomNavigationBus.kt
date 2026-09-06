package com.twocents.mobile.ui.messages

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class RoomNavigationRequest(val roomUuid: String, val targetMessageUuid: String? = null)

object RoomNavigationBus {
    private val channel = Channel<RoomNavigationRequest>(Channel.BUFFERED)
    val requests = channel.receiveAsFlow()

    fun open(roomUuid: String, targetMessageUuid: String? = null) {
        if (roomUuid.isNotBlank()) channel.trySend(RoomNavigationRequest(roomUuid, targetMessageUuid))
    }
}
