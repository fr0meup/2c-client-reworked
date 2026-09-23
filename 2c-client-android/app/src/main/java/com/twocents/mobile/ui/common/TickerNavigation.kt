package com.twocents.mobile.ui.common

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Route ticker taps to the shell's retained overlay stack, outside selectable text. */
internal object TickerNavigation {
    private val channel = Channel<String>(Channel.BUFFERED)
    val requests = channel.receiveAsFlow()

    fun open(ticker: String) {
        if (ticker.isNotBlank()) channel.trySend(ticker)
    }

    fun clear() {
        while (channel.tryReceive().isSuccess) { /* Discard requests from the previous account. */ }
    }
}
