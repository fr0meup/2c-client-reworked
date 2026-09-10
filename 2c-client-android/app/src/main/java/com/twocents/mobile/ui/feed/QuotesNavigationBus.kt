package com.twocents.mobile.ui.feed

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Quotes are retained navigation entries, not dialogs owned by recycled cards. */
internal object QuotesNavigationBus {
    data class Request(val post: FeedPost, val controller: FeedController)
    private val channel = Channel<Request>(Channel.BUFFERED)
    val requests = channel.receiveAsFlow()
    fun open(post: FeedPost, controller: FeedController) { channel.trySend(Request(post, controller)) }
}
