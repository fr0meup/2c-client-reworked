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
import androidx.compose.foundation.lazy.LazyRow
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
import com.twocents.mobile.ui.feed.cacheMediaRatio
import com.twocents.mobile.ui.feed.cachedMediaRatio
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
import kotlin.math.abs

private val ChatNoPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))



@Composable
internal fun ChatMessageRow(
    message: ChatMessage,
    mine: Boolean,
    alias: String?,
    reactions: List<ChatReaction>,
    authUuid: String,
    showAuthor: Boolean,
    showTime: Boolean,
    showDate: Boolean,
    replyTarget: ChatMessage?,
    highlighted: Boolean,
    onReply: () -> Unit,
    onOpenReply: (String) -> Unit,
    onLongPress: () -> Unit,
    onReaction: (String) -> Unit,
) {
    val view = LocalView.current
    var swipeOffset by remember(message.uuid) { mutableFloatStateOf(0f) }
    var lightboxMedia by remember(message.uuid) { mutableStateOf<String?>(null) }
    val swipeScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val replyThreshold = with(density) { 42.dp.toPx() }
    val maxSwipe = with(density) { 68.dp.toPx() }
    fun settleSwipe(triggerReply: Boolean) {
        if (triggerReply) onReply()
        val start = swipeOffset
        swipeScope.launch {
            animate(start, 0f, animationSpec = tween(170)) { value, _ -> swipeOffset = value }
        }
    }
    val highlightCover by animateColorAsState(
        if (highlighted) Gold.copy(alpha = .20f) else Color.Transparent,
        animationSpec = tween(if (highlighted) 150 else 650),
        label = "message-highlight-cover",
    )
    Column(Modifier.fillMaxWidth().background(highlightCover)) {
        if (showDate) ChatDateDivider(message.createdAt)
        Column(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            Column(Modifier.widthIn(max = 286.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (!mine && showAuthor) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)) {
                        val authorSeed = ComposeAuthorProfile(message.authorUuid, message.author.balance, message.author.subscriptionType, message.author.role)
                        Box(Modifier.clip(CircleShape).clickable { AppHaptics.navigate(view); ProfileNavigationBus.open(authorSeed) }) {
                            ComposeNetworthPill(ComposeAuthorProfile(message.authorUuid, message.author.balance, message.author.subscriptionType, message.author.role), message.authorUuid, compact = true)
                        }
                        alias?.let { Text(it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp).clickable { AppHaptics.navigate(view); ProfileNavigationBus.open(authorSeed) }, style = ChatNoPadding) }
                    }
                }
                if (message.deleted) {
                    Text("Message deleted", color = Color.White.copy(alpha = .2f), fontSize = 11.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(8.dp))
                } else {
                    val inlineMedia = message.mediaUrl ?: DirectChatMedia.find(message.text)?.value
                    val visibleText = if (inlineMedia == null) message.text else message.text.replace(inlineMedia, "").trim()
                    val replyToUuid = message.replyToUuid
                    val rawReply = message.replyText ?: replyTarget?.text.orEmpty()
                    val replyMedia = replyTarget?.mediaUrl ?: DirectChatMedia.find(rawReply)?.value
                    val replyText = if (replyMedia == null) rawReply.trim() else rawReply.replace(replyMedia, "").trim()
                    val bubbleShape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                    var mediaRatio by remember(inlineMedia) { mutableFloatStateOf(inlineMedia?.let(::cachedMediaRatio) ?: 1f) }
                    Box {
                        Icon(
                            Icons.Outlined.Reply, null, tint = Gold,
                            modifier = Modifier.align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                                .padding(if (mine) PaddingValues(end = 8.dp) else PaddingValues(start = 8.dp)).size(18.dp)
                                .graphicsLayer { alpha = (abs(swipeOffset) / replyThreshold).coerceIn(0f, 1f) },
                        )
                        Column(
                            Modifier.offset { IntOffset(swipeOffset.roundToInt(), 0) }
                                .clip(bubbleShape)
                                 .background(if (mine) Gold else Color.White.copy(alpha = .06f))
                                 .then(
                                     if (highlighted) Modifier.border(2.dp, Gold, RoundedCornerShape(18.dp))
                                     else if (!mine) Modifier.border(1.dp, Color.White.copy(alpha = .07f), bubbleShape)
                                     else Modifier,
                                 )
                                .pointerInput(message.uuid) {
                                    detectTapGestures(onLongPress = { AppHaptics.open(view); onLongPress() })
                                }
                                .pointerInput(message.uuid) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { settleSwipe(if (mine) swipeOffset <= -replyThreshold else swipeOffset >= replyThreshold) },
                                        onDragCancel = { settleSwipe(false) },
                                    ) { change, dragAmount ->
                                        val next = if (mine) {
                                            (swipeOffset + dragAmount).coerceIn(-maxSwipe, 0f)
                                        } else {
                                            (swipeOffset + dragAmount).coerceIn(0f, maxSwipe)
                                        }
                                        if (next != swipeOffset) change.consume()
                                        swipeOffset = next
                                    }
                                }
                                .padding(if (inlineMedia == null) 9.dp else 2.dp),
                        ) {
                            if (replyToUuid != null && (replyText.isNotBlank() || replyMedia != null)) {
                                ChatReplyPreview(
                                    replied = replyText.ifBlank { "Image" },
                                    hasMedia = replyMedia != null,
                                    target = replyTarget,
                                    mine = mine,
                                    enabled = true,
                                    onClick = { AppHaptics.navigate(view); onOpenReply(replyToUuid) },
                                )
                            }
                            if (visibleText.isNotBlank()) LinkifiedText(
                                text = visibleText,
                                color = if (mine) Color(0xFF0F0E0A) else Color.White.copy(alpha = .92f),
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                fontWeight = if (mine) FontWeight.Medium else FontWeight.Normal,
                                linkColor = if (mine) Color(0xFF0F0E0A) else Gold,
                                modifier = if (inlineMedia != null) Modifier.padding(horizontal = 10.dp, vertical = 7.dp) else Modifier.padding(horizontal = 4.dp),
                            )
                            inlineMedia?.let { media ->
                                AsyncImage(
                                    model = media,
                                    contentDescription = "Open image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .padding(top = if (visibleText.isBlank()) 0.dp else 2.dp)
                                        .width(278.dp)
                                        .aspectRatio(mediaRatio.coerceAtLeast(.08f))
                                        .clip(RoundedCornerShape(16.dp))
                                        .pointerInput(media) {
                                            detectTapGestures(
                                                onTap = { AppHaptics.open(view); lightboxMedia = media },
                                                onLongPress = { AppHaptics.open(view); onLongPress() },
                                            )
                                        },
                                    onSuccess = { result ->
                                        val image = result.result.image
                                        if (image.width > 0 && image.height > 0) {
                                            mediaRatio = image.width.toFloat() / image.height.toFloat()
                                            cacheMediaRatio(media, mediaRatio)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                if (reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        reactions.groupBy(ChatReaction::text).forEach { (emoji, rows) ->
                            val mineReacted = rows.any { it.authorUuid == authUuid }
                            Row(
                                Modifier.clip(CircleShape).background(if (mineReacted) Gold.copy(alpha = .15f) else Color.White.copy(alpha = .05f))
                                    .border(1.dp, if (mineReacted) Gold.copy(alpha = .4f) else Color.White.copy(alpha = .08f), CircleShape)
                                    .clickable { AppHaptics.toggle(view); onReaction(emoji) }.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(emoji, fontSize = 11.5.sp, style = ChatNoPadding)
                                if (rows.size > 1) Text(rows.size.toString(), color = if (mineReacted) Gold else Color.White.copy(alpha = .5f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, style = ChatNoPadding)
                            }
                        }
                    }
                }
                when {
                    message.optimistic -> Text("Sending…", color = Color.White.copy(alpha = .35f), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp), style = ChatNoPadding)
                    message.justSent -> Text("Sent", color = Color(0xFF34D399).copy(alpha = .7f), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp), style = ChatNoPadding)
                    showTime -> Text(compactTimeAgo(message.createdAt), color = Color.White.copy(alpha = .25f), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp), style = ChatNoPadding)
                }
            }
        }
    }
    lightboxMedia?.let { media ->
        ImageLightbox(images = listOf(media), initialIndex = 0, originRect = null) { lightboxMedia = null }
    }
}

@Composable
private fun ChatReplyPreview(
    replied: String,
    hasMedia: Boolean,
    target: ChatMessage?,
    mine: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 5.dp, top = 5.dp, end = 5.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (mine) Color.Black.copy(alpha = .11f) else Color.White.copy(alpha = .035f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 6.dp, top = 5.dp, end = 7.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.width(2.5.dp).height(25.dp).clip(CircleShape).background(if (mine) Color(0xFF0F0E0A).copy(alpha = .48f) else Gold.copy(alpha = .72f)))
        target?.let {
            ComposeNetworthPill(
                ComposeAuthorProfile(it.authorUuid, it.author.balance, it.author.subscriptionType, it.author.role),
                it.authorUuid,
                compact = true,
            )
        }
        if (hasMedia) Icon(Icons.Outlined.Image, null, tint = if (mine) Color(0xFF0F0E0A).copy(alpha = .58f) else Gold.copy(alpha = .72f), modifier = Modifier.size(13.dp))
        Text(
            replied,
            color = if (mine) Color(0xFF0F0E0A).copy(alpha = .68f) else Color.White.copy(alpha = .58f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = ChatNoPadding,
        )
    }
}

@Composable
internal fun MessageActionsOverlay(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReaction: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var moreEmoji by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableIntStateOf(0) }
    val basic = listOf("❤️", "😂", "👍", "👎", "🔥", "😭")
    val categories by produceState(initialValue = emptyList<EmojiCategory>(), moreEmoji) {
        if (moreEmoji) value = withContext(Dispatchers.IO) { EmojiCatalog.categories(context.applicationContext) }
    }
    Box(modifier.background(Color.Black.copy(alpha = .32f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF141410))
                    .border(1.dp, Color.White.copy(alpha = .11f), RoundedCornerShape(22.dp)).clickable(onClick = {})
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("React to message", color = Color.White.copy(alpha = .78f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    basic.forEach { emoji -> MessageActionCircle(onClick = { onReaction(emoji) }) { Text(emoji, fontSize = 18.sp) } }
                    MessageActionCircle(onClick = { moreEmoji = !moreEmoji }) { Icon(Icons.Outlined.MoreHoriz, "More reactions", tint = Gold, modifier = Modifier.size(19.dp)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MessageLabeledAction(Modifier.weight(1f), Icons.Outlined.Reply, "Reply", onReply)
                    MessageLabeledAction(Modifier.weight(1f), Icons.Outlined.ContentCopy, "Copy text") {
                        clipboard.setText(AnnotatedString(message.text.ifBlank { message.mediaUrl.orEmpty() }))
                        onDismiss()
                    }
                }
                if (moreEmoji) {
                    if (categories.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            itemsIndexed(categories) { index, category ->
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(if (index == selectedCategory) Gold.copy(alpha = .16f) else Color.Transparent)
                                        .clickable { selectedCategory = index },
                                    contentAlignment = Alignment.Center,
                                ) { Text(category.icon, fontSize = 17.sp) }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Color.White.copy(alpha = .075f)))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(categories[selectedCategory.coerceIn(0, categories.lastIndex)].emojis, key = { it }) { emoji ->
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).clickable { onReaction(emoji) }, contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun MessageLabeledAction(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier.height(38.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .05f))
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(11.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = .72f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = .76f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MessageActionCircle(onClick: () -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .055f))
            .border(1.dp, Color.White.copy(alpha = .08f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable private fun ChatDateDivider(raw: String) = Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = .07f)))
    Text(exactLocalDate(raw), color = Color.White.copy(alpha = .35f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, style = ChatNoPadding)
    Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = .07f)))
}

internal data class TypingPerson(val uuid: String, val nickname: String?, val balance: Double, val subscriptionType: Int, val role: String?)

@Composable
internal fun RoomTypingIndicator(people: List<TypingPerson>) {
    var dots by remember(people) { mutableIntStateOf(1) }
    LaunchedEffect(people) {
        while (true) {
            delay(260)
            dots = if (dots == 3) 1 else dots + 1
        }
    }
    Row(
        Modifier.fillMaxWidth().height(24.dp).background(Background).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val first = people.first()
        val identity = first.nickname?.takeIf(String::isNotBlank)
            ?: "$" + java.text.NumberFormat.getIntegerInstance().format(first.balance)
        Text(
            identity,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 130.dp),
            style = ChatNoPadding,
        )
        Text(
            (if (people.size == 1) "is typing" else "+${people.size - 1} typing") + ".".repeat(dots),
            color = Color.White.copy(alpha = .46f), fontSize = 11.sp, fontStyle = FontStyle.Italic,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = ChatNoPadding,
        )
    }
}
