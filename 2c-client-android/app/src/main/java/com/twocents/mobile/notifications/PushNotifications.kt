package com.twocents.mobile.notifications

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.twocents.mobile.AuthState
import com.twocents.mobile.MainActivity
import com.twocents.mobile.RpcApi
import com.twocents.mobile.SecureAuthStore
import com.twocents.mobile.kotlin.R
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject

object PushNotificationManager {
    private const val PREFS = "twocents_push"
    private const val TOKEN = "fcm_token"
    private const val REGISTERED = "registered_fingerprint"
    private const val REGISTERED_AT = "registered_at"
    private const val REGISTRATION_TTL_MS = 24 * 60 * 60 * 1_000L
    private const val PERMISSION_REQUEST = 2402
    const val DEFAULT_CHANNEL = "default"
    const val ACTIVITY_CHANNEL = "activity"
    const val MESSAGE_CHANNEL = "messages"
    private val registrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(
        context: Context,
        api: RpcApi,
        auth: AuthState,
    ) {
        createChannels(context)
        requestPermission(context)
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isBlank()) return@addOnSuccessListener
                Log.i("TwoCentsFCM", "FCM token acquired (${token.length} chars)")
                saveToken(context, token)
                registrationScope.launch { syncToken(context.applicationContext, api, auth, token) }
            }
            .addOnFailureListener { error -> Log.e("TwoCentsFCM", "FCM token acquisition failed", error) }
    }

    suspend fun syncStoredToken(context: Context, api: RpcApi, auth: AuthState) {
        val token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN, null)
            ?: return
        syncToken(context, api, auth, token)
    }

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(TOKEN, token)
            .apply()
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                DEFAULT_CHANNEL,
                "General notifications",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "General 2C notifications"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lightColor = 0xFFC8A44D.toInt()
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ACTIVITY_CHANNEL,
                "Activity",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Votes, replies, follows, and account activity"; setShowBadge(true) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Room and direct messages"; setShowBadge(true) },
        )
    }

    private fun requestPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        context.findActivity()?.let { activity ->
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST,
            )
        }
    }

    private suspend fun syncToken(
        context: Context,
        api: RpcApi,
        auth: AuthState,
        token: String,
    ) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fingerprint = "${auth.userUuid}:$token"
        val registrationIsFresh = preferences.getString(REGISTERED, null) == fingerprint &&
            System.currentTimeMillis() - preferences.getLong(REGISTERED_AT, 0L) < REGISTRATION_TTL_MS
        if (registrationIsFresh) return
        repeat(3) { attempt ->
            val result = runCatching {
                api.call(
                    "/v1/users/update/fcm_token",
                    JSONObject().put("fcm_token", token),
                    auth,
                )
            }
            if (result.isSuccess) {
                preferences.edit()
                    .putString(REGISTERED, fingerprint)
                    .putLong(REGISTERED_AT, System.currentTimeMillis())
                    .apply()
                Log.i("TwoCentsFCM", "FCM token registered with backend")
                return
            }
            Log.w("TwoCentsFCM", "FCM backend sync attempt ${attempt + 1} failed", result.exceptionOrNull())
            if (attempt < 2) delay(if (attempt == 0) 1_500L else 3_000L)
        }
    }
}

data class NotificationNavigationRequest(
    val postUuid: String? = null,
    val commentUuid: String? = null,
    val roomUuid: String? = null,
    val messageUuid: String? = null,
)

object NotificationNavigationBus {
    private val requestChannel = Channel<NotificationNavigationRequest>(Channel.BUFFERED)
    val requests = requestChannel.receiveAsFlow()

    fun openNotifications(
        postUuid: String? = null,
        commentUuid: String? = null,
        roomUuid: String? = null,
        messageUuid: String? = null,
    ) {
        requestChannel.trySend(NotificationNavigationRequest(postUuid, commentUuid, roomUuid, messageUuid))
    }
}

class TwoCentsMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushNotificationManager.saveToken(applicationContext, token)
        CoroutineScope(Dispatchers.IO).launch {
            val auth = SecureAuthStore(applicationContext).load() ?: return@launch
            PushNotificationManager.syncStoredToken(applicationContext, RpcApi(), auth)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val flattened = flattenPushData(message.data)
        val payload = PushPayload(
            data = flattened,
            title = message.notification?.title ?: flattened["title"],
            body = message.notification?.body ?: flattened["body"] ?: flattened["message"],
        )
        NotificationEventBus.publish(payload)
        CoroutineScope(Dispatchers.IO).launch {
            val auth = SecureAuthStore(applicationContext).load() ?: return@launch
            notificationFromPush(payload, auth.userUuid)?.let {
                NotificationHistoryStore(applicationContext, auth.userUuid).upsert(it)
            }
            runCatching {
                RpcApi().call(
                    "/v2/users/get",
                    JSONObject().put("user_uuid", auth.userUuid).put("posts_limit", 0)
                        .put("comments_limit", 0).put("voted_posts_limit", 0),
                    auth,
                ) as? JSONObject
            }.getOrNull()?.let { profile ->
                NotificationHistoryStore(applicationContext, auth.userUuid)
                    .also { store ->
                        store.reconcileTotalUpvotes(profile.optInt("totalUpvotes"))
                        store.reconcileFollowerCount(profile.optInt("aliasesReceived"))
                    }
            }
        }
        showNotification(payload)
    }

    private fun showNotification(payload: PushPayload) {
        PushNotificationManager.createChannels(applicationContext)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val roomUuid = payload.data["room_uuid"] ?: payload.data["roomUuid"]
        val eventType = payload.data["type"].orEmpty()
        val explicitRoomType = payload.data["room_type"] ?: payload.data["roomType"].orEmpty()
        if (!NotificationPreferences.enabled(this, notificationCategory(eventType, roomUuid, explicitRoomType), push = true)) return
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("open_notifications", true)
            putExtra("post_uuid", payload.data["post_uuid"] ?: payload.data["postUuid"])
            putExtra("comment_uuid", payload.data["comment_uuid"] ?: payload.data["commentUuid"])
            putExtra("room_uuid", payload.data["room_uuid"] ?: payload.data["roomUuid"])
            putExtra("message_uuid", payload.data["message_uuid"] ?: payload.data["messageUuid"])
        }
        val notificationId = nextNotificationId.incrementAndGet()
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = payload.title ?: "2c"
        val body = (payload.body ?: payload.data["message"] ?: "You have new activity")
            .replace("\r\n", "\n")
            .replace(Regex("\\n(?:[ \\t]*\\n)+"), "\n")
            .trim()
        val directMessage = roomUuid != null && (
            explicitRoomType.contains("dm", ignoreCase = true) || LauncherBadge.isKnownDirectMessage(this, roomUuid)
        )
        // Update the shared count before building the notification so launchers
        // driven by Notification.number and provider-driven launchers agree.
        if (eventType.contains("reply", ignoreCase = true) || roomUuid == null) LauncherBadge.incrementNotifications(this)
        if (directMessage) LauncherBadge.incrementDirectMessages(this)
        val channel = if (
            payload.data["room_uuid"] != null ||
            payload.data["roomUuid"] != null
        ) PushNotificationManager.MESSAGE_CHANNEL else payload.data["channel_id"]
            ?.takeIf { it in setOf(PushNotificationManager.DEFAULT_CHANNEL, PushNotificationManager.ACTIVITY_CHANNEL) }
            ?: PushNotificationManager.ACTIVITY_CHANNEL
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(0xFFC8A44D.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(LauncherBadge.currentTotal(this))
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .build()
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private companion object {
        val nextNotificationId = AtomicInteger(2_000)
    }
}

private fun flattenPushData(source: Map<String, String>): Map<String, String> = buildMap {
    putAll(source)
    listOf("data", "payload", "notification").forEach { key ->
        val nested = source[key]?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
        nested.keys().forEach { nestedKey ->
            val value = nested.opt(nestedKey)
            if (value != null && value != JSONObject.NULL) putIfAbsent(nestedKey, value.toString())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
