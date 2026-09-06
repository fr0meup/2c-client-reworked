package com.twocents.mobile

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class RpcApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val uploadClient = client.newBuilder()
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    suspend fun call(
        method: String,
        params: JSONObject,
        auth: AuthState,
    ): Any? = withContext(Dispatchers.IO) {
        val requestJson = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", auth.userUuid)
            .put("method", method)
            .put("params", params)

        val request = Request.Builder()
            .url(API_URL)
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer " + auth.token)
            .header("Content-Type", "application/json")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw ApiException("Couldn't connect. Check your internet connection and try again.", error)
        }

        response.use { httpResponse ->
            val body = httpResponse.body?.string().orEmpty()
            if (!httpResponse.isSuccessful) {
                throw ApiException("HTTP ${httpResponse.code}: ${body.take(500)}")
            }

            val root = try {
                JSONObject(body)
            } catch (error: Exception) {
                throw ApiException("Invalid API response", error)
            }

            if (root.has("error") && !root.isNull("error")) {
                val rpcError = root.optJSONObject("error")
                val message = rpcError?.optString("message")
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: root.optString("error").trim().takeIf(String::isNotBlank)
                    ?: "The request was rejected"
                throw ApiException(message)
            }

            root.opt("result")
        }
    }

    suspend fun putBinary(
        presignedUrl: String,
        resolver: ContentResolver,
        uri: Uri,
        contentType: String,
        contentLength: Long,
    ) = withContext(Dispatchers.IO) {
        val requestBody = object : RequestBody() {
            override fun contentType() = contentType.toMediaType()

            override fun contentLength() = contentLength

            override fun writeTo(sink: okio.BufferedSink) {
                val input = resolver.openInputStream(uri)
                    ?: throw IOException("Unable to open selected media")
                input.use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(presignedUrl)
            .put(requestBody)
            .header("x-amz-server-side-encryption", "AES256")
            .header("Content-Type", contentType)
            .build()

        val response = uploadClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw ApiException("Media upload failed: HTTP ${it.code}")
        }
    }

    suspend fun downloadBinary(url: String, maxBytes: Long = 25L * 1024L * 1024L): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        response.use {
            if (!it.isSuccessful) throw ApiException("Couldn't download media: HTTP ${it.code}")
            val body = it.body ?: throw ApiException("Downloaded media was empty")
            val declared = body.contentLength()
            if (declared > maxBytes) throw ApiException("Media is too large")
            val bytes = body.bytes()
            if (bytes.isEmpty()) throw ApiException("Downloaded media was empty")
            if (bytes.size.toLong() > maxBytes) throw ApiException("Media is too large")
            val contentType = body.contentType()?.toString()?.substringBefore(';')
                ?.takeIf { type -> type.startsWith("image/") } ?: "image/gif"
            contentType to bytes
        }
    }

    suspend fun putBytes(presignedUrl: String, bytes: ByteArray, contentType: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(presignedUrl)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .header("x-amz-server-side-encryption", "AES256")
            .header("Content-Type", contentType)
            .build()
        uploadClient.newCall(request).execute().use {
            if (!it.isSuccessful) throw ApiException("Media upload failed: HTTP ${it.code}")
        }
    }

    suspend fun bootstrapGroupRoom(roomUuid: String, auth: AuthState) {
        val opened = CompletableDeferred<WebSocket>()
        val socket = client.newWebSocket(
            Request.Builder().url("wss://ds3y2js2k0.execute-api.us-east-2.amazonaws.com/ws/?token=${auth.token}").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(webSocket) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { opened.completeExceptionally(t) }
            },
        )
        try {
            val ready = withTimeout(5_000) { opened.await() }
            ready.send(JSONObject().put("action", "joinRoom").put("roomUuid", roomUuid).toString())
            delay(300)
            ready.send(JSONObject().put("action", "sendMessage").put("roomUuid", roomUuid).put("text", "start").toString())
        } finally {
            socket.close(1000, null)
        }
    }

    suspend fun sendRoomMessage(roomUuid: String, text: String, auth: AuthState) {
        val opened = CompletableDeferred<WebSocket>()
        val socket = client.newWebSocket(
            Request.Builder().url("wss://ds3y2js2k0.execute-api.us-east-2.amazonaws.com/ws/?token=${auth.token}").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(webSocket) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { opened.completeExceptionally(t) }
            },
        )
        try {
            val ready = withTimeout(7_000) { opened.await() }
            ready.send(JSONObject().put("action", "joinRoom").put("roomUuid", roomUuid).toString())
            delay(300)
            ready.send(JSONObject().put("action", "sendMessage").put("roomUuid", roomUuid).put("text", text).toString())
        } finally {
            socket.close(1000, null)
        }
    }

    companion object {
        const val API_URL = "https://api.twocents.money/prod"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
