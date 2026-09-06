package com.twocents.mobile.core.realtime

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Thin socket factory; feature controllers retain reconnect, parsing, and lifecycle policy. */
internal class RealtimeClient(private val client: OkHttpClient) {
    fun open(
        request: Request,
        onOpen: (WebSocket, Response) -> Unit,
        onText: (WebSocket, String) -> Unit,
        onClosed: (WebSocket, Int, String) -> Unit,
        onFailure: (WebSocket, Throwable, Response?) -> Unit,
    ): WebSocket = client.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = onOpen(webSocket, response)
        override fun onMessage(webSocket: WebSocket, text: String) = onText(webSocket, text)
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onClosed(webSocket, code, reason)
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onFailure(webSocket, t, response)
    })
}
