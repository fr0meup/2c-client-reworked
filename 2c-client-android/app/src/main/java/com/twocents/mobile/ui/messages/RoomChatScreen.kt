package com.twocents.mobile.ui.messages

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import coil3.compose.AsyncImage
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.core.format.compactTimeAgo
import com.twocents.mobile.core.format.exactLocalDate
import com.twocents.mobile.core.format.formatCompactCount
import com.twocents.mobile.core.format.parseApiInstant
import com.twocents.mobile.notifications.NotificationIcons
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.compose.GifPickerSheet
import com.twocents.mobile.ui.feed.ImageLightbox
import com.twocents.mobile.ui.common.PullToRefreshContainer
import com.twocents.mobile.ui.common.RefreshProgressBar
import com.twocents.mobile.ui.common.rememberPullToRefreshState
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.common.LinkifiedText
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val ChatNoPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))



/**
 * Binds the room controller to viewport state, unread anchoring, and composer UI.
 * Message rows, composer controls, and transport are deliberately split out.
 */
@Composable
fun RoomChatScreen(
    room: RoomSummary,
    auth: AuthState,
    api: RpcApi,
    aliases: Map<String, String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetMessageUuid: String? = null,
    navigationEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val controller = remember(room.uuid, auth, api) { RoomChatController(api, auth, room, context.applicationContext) }
    val state = controller.state
    val scope = rememberCoroutineScope()
    // Messages are newest-first in a reverse-layout list. Starting at the oldest
    // unread row lets the initial viewport be established before content is shown,
    // instead of visibly jumping there after the network response is rendered.
    val openingUnreadCount = remember(room.uuid) { room.unread.coerceAtLeast(0) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = openingUnreadCount,
    )
    val refreshState = rememberPullToRefreshState()
    var input by remember(room.uuid) { mutableStateOf("") }
    var reply by remember(room.uuid) { mutableStateOf<ChatMessage?>(null) }
    var selectedImage by remember(room.uuid) { mutableStateOf<Uri?>(null) }
    var selectedGif by remember(room.uuid) { mutableStateOf<String?>(null) }
    var gifPickerOpen by remember(room.uuid) { mutableStateOf(false) }
    var infoOpen by remember(room.uuid) { mutableStateOf(false) }
    var reopenInfoAfterProfile by remember(room.uuid) { mutableStateOf(false) }
    var actionMessage by remember(room.uuid) { mutableStateOf<ChatMessage?>(null) }
    var highlightedMessageUuid by remember(room.uuid) { mutableStateOf<String?>(null) }
    var initialUnreadViewportReady by remember(room.uuid) { mutableStateOf(openingUnreadCount == 0) }
    var previousNewestMessageUuid by remember(room.uuid) { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selectedImage = it }

    BackHandler(enabled = navigationEnabled, onBack = onBack)
    LaunchedEffect(controller) { controller.load(); controller.connect() }
    LaunchedEffect(navigationEnabled) {
        if (navigationEnabled && reopenInfoAfterProfile) {
            reopenInfoAfterProfile = false
            infoOpen = true
        }
    }
    DisposableEffect(controller) { onDispose(controller::dispose) }
    LaunchedEffect(state.loading, state.messages.size, initialUnreadViewportReady) {
        if (!state.loading && !initialUnreadViewportReady) {
            // The divider belongs to the first older/read row (index == unread
            // count), so target that row rather than the last unread message.
            // This keeps both the boundary label and first unread message visible.
            val targetIndex = openingUnreadCount.coerceAtMost(state.messages.size).coerceAtLeast(0)
            if (state.messages.isNotEmpty()) {
                listState.scrollToItem(targetIndex)
                withFrameNanos { }
                val layout = listState.layoutInfo
                val target = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                if (target != null) {
                    // Leave a small glimpse of the preceding message above the
                    // unread boundary while placing the first unread row near top.
                    val marginPx = with(density) { 10.dp.roundToPx() }
                    val viewportHeight = layout.viewportEndOffset - layout.viewportStartOffset
                    val offsetFromBottom = (viewportHeight - target.size - marginPx).coerceAtLeast(0)
                    listState.scrollToItem(targetIndex, scrollOffset = -offsetFromBottom)
                }
            }
            initialUnreadViewportReady = true
        }
    }
    LaunchedEffect(state.messages.firstOrNull()?.uuid) {
        val newest = state.messages.firstOrNull()?.uuid
        val previous = previousNewestMessageUuid
        if (newest != null && previous != null && newest != previous && initialUnreadViewportReady) {
            val visibleKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
            val wasAtBottom = (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) ||
                visibleKey == previous
            if (wasAtBottom) listState.requestScrollToItem(0)
        }
        previousNewestMessageUuid = newest
    }
    suspend fun jumpToMessage(uuid: String) {
        val index = state.messages.indexOfFirst { it.uuid == uuid }
        if (index < 0) return
        listState.animateScrollToItem(index, scrollOffset = -120)
        highlightedMessageUuid = uuid
        delay(1_150)
        if (highlightedMessageUuid == uuid) highlightedMessageUuid = null
    }
    LaunchedEffect(targetMessageUuid, state.loading, state.messages.size, initialUnreadViewportReady) {
        if (!targetMessageUuid.isNullOrBlank() && !state.loading && initialUnreadViewportReady) jumpToMessage(targetMessageUuid)
    }
    val typingPeople = state.typingAuthors.map { uuid ->
        val messageAuthor = state.messages.firstOrNull { it.authorUuid == uuid }
        val member = room.members.firstOrNull { it.uuid == uuid }
        TypingPerson(
            uuid = uuid,
            nickname = aliases[uuid] ?: member?.alias ?: member?.username ?: messageAuthor?.author?.nickname,
            balance = member?.balance ?: messageAuthor?.author?.balance ?: 0.0,
            subscriptionType = member?.subscriptionType ?: messageAuthor?.author?.subscriptionType ?: 0,
            role = member?.role ?: messageAuthor?.author?.role,
        )
    }

    Box(modifier.fillMaxSize().background(Background)) {
      Column(Modifier.fillMaxSize()) {
        RoomChatTopBar(
            room, auth.userUuid, aliases, state.connected, onBack,
            onInfo = { infoOpen = true },
            onLeave = { scope.launch { if (controller.leave()) onBack() } },
        )
        val atBottom = state.messages.isEmpty() ||
            (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
        PullToRefreshContainer(
            state = refreshState,
            enabled = !state.loading && atBottom,
            onRefresh = { controller.load() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            fromBottom = true,
        ) {
            when {
                state.loading -> RoomChatSkeleton(Modifier.fillMaxSize())
                state.messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("No messages yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Send a message to start the conversation!", color = Color.White.copy(alpha = .4f), fontSize = 13.sp)
                    }
                }
                else -> Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (initialUnreadViewportReady) 1f else 0f },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                      itemsIndexed(state.messages, key = { _, item -> item.uuid }) { index, message ->
                        val unreadCount = room.unread.coerceAtMost(state.messages.size)
                        if (unreadCount in 1 until state.messages.size && index == unreadCount) {
                            ChatUnreadDivider(unreadCount)
                        }
                        val older = state.messages.getOrNull(index + 1)
                        val newer = state.messages.getOrNull(index - 1)
                        val showAuthor = older == null || older.authorUuid != message.authorUuid || !sameChatDay(older.createdAt, message.createdAt)
                        val showTime = newer == null || newer.authorUuid != message.authorUuid || chatMinutesBetween(newer.createdAt, message.createdAt) > 5
                        ChatMessageRow(
                            message = message,
                            mine = message.authorUuid == auth.userUuid,
                            alias = aliases[message.authorUuid] ?: message.author.nickname,
                            reactions = state.reactions[message.uuid].orEmpty(),
                            authUuid = auth.userUuid,
                            showAuthor = showAuthor,
                            showTime = showTime,
                            showDate = older == null || !sameChatDay(older.createdAt, message.createdAt),
                            replyTarget = message.replyToUuid?.let { target -> state.messages.firstOrNull { it.uuid == target } },
                            highlighted = highlightedMessageUuid == message.uuid,
                            onReply = { reply = message },
                            onOpenReply = { target -> scope.launch { jumpToMessage(target) } },
                            onLongPress = { actionMessage = message },
                            onReaction = { emoji -> controller.toggleReaction(message.uuid, emoji) },
                        )
                      }
                      if (room.unread >= state.messages.size && state.messages.isNotEmpty()) {
                          item("all-messages-unread-divider") { ChatUnreadDivider(state.messages.size) }
                      }
                    }
                    if (!initialUnreadViewportReady) RoomChatSkeleton(Modifier.fillMaxSize())
                }
            }
        }
        if (typingPeople.isNotEmpty()) RoomTypingIndicator(typingPeople)
        RefreshProgressBar(active = refreshState.isRefreshing)
        RoomComposer(
            input = input,
            onInput = { value -> input = value; controller.updateTyping(value.isNotBlank()) },
            reply = reply,
            onClearReply = { reply = null },
            selectedImage = selectedImage,
            onClearImage = { selectedImage = null },
            onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            selectedGif = selectedGif,
            onClearGif = { selectedGif = null },
            onPickGif = { gifPickerOpen = true },
            sending = state.sending,
            onSend = {
                val sentText = input
                val sentReply = reply
                val sentImage = selectedImage
                val sentGif = selectedGif
                if (sentText.isBlank() && sentImage == null && sentGif == null) return@RoomComposer
                val keepNewestVisible = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= with(density) { 12.dp.roundToPx() }
                controller.updateTyping(false)
                input = ""; reply = null; selectedImage = null; selectedGif = null
                com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch {
                    val send = async { controller.send(sentText, sentReply, sentImage, sentGif, context.applicationContext) }
                    if (keepNewestVisible) {
                        scope.launch {
                            withFrameNanos { }
                            listState.requestScrollToItem(0)
                        }
                    }
                    if (!send.await()) {
                        input = sentText; reply = sentReply; selectedImage = sentImage; selectedGif = sentGif
                        com.twocents.mobile.ui.common.AppToast.error("Message failed to send. Check your connection and try again.")
                    } else com.twocents.mobile.ui.common.AppToast.success("Message sent")
                }
            },
        )
      }
      actionMessage?.let { message ->
          MessageActionsOverlay(
              message = message,
              modifier = Modifier.fillMaxSize().zIndex(10f),
              onDismiss = { actionMessage = null },
              onReply = { reply = message; actionMessage = null },
              onReaction = { emoji -> controller.toggleReaction(message.uuid, emoji); actionMessage = null },
          )
      }
    }
    if (gifPickerOpen) {
        GifPickerSheet(
            onDismiss = { gifPickerOpen = false },
            onSelect = { url -> selectedGif = url; selectedImage = null; gifPickerOpen = false },
        )
    }
    if (infoOpen) {
        RoomInfoSheet(
            room = room,
            auth = auth,
            api = api,
            aliases = aliases,
            onDismiss = { infoOpen = false },
            onLeave = { scope.launch { if (controller.leave()) { infoOpen = false; onBack() } } },
            onOpenProfile = { profile ->
                reopenInfoAfterProfile = true
                infoOpen = false
                ProfileNavigationBus.open(profile)
            },
        )
    }
}
