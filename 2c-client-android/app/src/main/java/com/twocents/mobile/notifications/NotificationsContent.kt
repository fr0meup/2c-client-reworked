package com.twocents.mobile.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import coil3.compose.AsyncImage
import com.twocents.mobile.ui.shell.NotificationsListSkeleton
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.AppLoadState

@Composable
fun NotificationsContent(
    controller: NotificationController,
    filter: NotificationFilter,
    listState: LazyListState,
    onOpenNotification: (AppNotification) -> Unit,
    bottomContentPadding: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val state = controller.state
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller) { controller.load(force = true) }
    val categoryVisible = state.notifications.filter {
        NotificationPreferences.cachedEnabled(notificationCategory(it.type, it.roomUuid), push = false)
    }
    val visible = when (filter) {
        NotificationFilter.All -> categoryVisible
        NotificationFilter.Unread -> categoryVisible.filter { it.readAt == null }
        NotificationFilter.Replies -> categoryVisible.filter {
            it.type == "post_replied" || it.type == "comment_replied" || it.type == "room_reply"
        }
    }

    when {
        state.isLoading && state.notifications.isEmpty() -> NotificationsListSkeleton(modifier)
        visible.isEmpty() && state.error != null -> AppLoadState("Couldn't load notifications", state.error, modifier.background(Background))
        visible.isEmpty() -> NotificationEmptyState(filter = filter, error = null, modifier = modifier)
        else -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().background(Background),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it.uuid }, contentType = { "notification" }) { notification ->
                NotificationRow(
                    notification = notification,
                    onClick = {
                        AppHaptics.navigate(view)
                        scope.launch { controller.markRead(notification.uuid) }
                        onOpenNotification(notification)
                    },
                    onMarkRead = { scope.launch { controller.markRead(notification.uuid) } },
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val view = LocalView.current
    val unread = notification.readAt == null
    val parsed = parseNotificationMessage(notification.type, notification.message)
    val preview = parsed.preview?.let(::cleanNotificationPreview)?.takeIf(String::isNotBlank)
    val mediaUrl = NotificationMediaUrl.find(notification.message)?.value
    val actionLabel = parsed.action ?: notificationActionLabel(notification.type, parsed.isDownvote)
    val actorUuid = notification.actorUuid
    val actorBalance = notification.actorBalance
    val actorLabel = parsed.actor?.takeIf(String::isNotBlank)
        ?: actorBalance?.let(::formatNotificationBalance)
    val timestamp = timeAgo(notification.createdAt)
    val hasPreview = preview != null || mediaUrl != null
    val border = if (unread) Gold.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f)
    val surface = if (unread) Gold.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.02f)
    val actorInteraction = remember { MutableInteractionSource() }
    val actorPressed by actorInteraction.collectIsPressedAsState()
    var actorHighlighted by remember { mutableStateOf(false) }
    LaunchedEffect(actorPressed) {
        if (actorPressed) actorHighlighted = true else if (actorHighlighted) {
            kotlinx.coroutines.delay(650)
            actorHighlighted = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        if (unread) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 12.dp)
                    .size(width = 2.5.dp, height = 40.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(Gold.copy(alpha = 0.8f)),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onMarkRead),
            ) {
                NotificationIcon(type = notification.type, downvote = parsed.isDownvote, unread = unread)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (hasPreview) 5.dp else 0.dp),
            ) {
                if (!actorLabel.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = actorLabel,
                            color = if (actorHighlighted) Gold else if (unread) Color.White else Color.White.copy(alpha = .75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 156.dp)
                                .clip(CircleShape)
                                .clickable(
                                    enabled = actorUuid != null,
                                    interactionSource = actorInteraction,
                                    indication = null,
                                ) {
                                    AppHaptics.navigate(view)
                                    actorUuid?.let { uuid ->
                                        ProfileNavigationBus.open(
                                            ComposeAuthorProfile(uuid, actorBalance ?: 0.0, notification.actorSubscriptionType),
                                        )
                                    }
                                }
                                .padding(horizontal = 1.dp, vertical = 2.dp),
                            style = NoFontPadding,
                        )
                        val actionText = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White.copy(alpha = if (unread) .52f else .34f), fontSize = 12.5.sp)) { append(actionLabel) }
                        }
                        if (preview == null) AlwaysTimestampText(actionText, timestamp, Modifier.weight(1f), NoFontPadding, maxLines = 1)
                        else Text(actionText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = NoFontPadding)
                    }
                    preview?.let { preview ->
                        AlwaysTimestampText(
                            content = AnnotatedString(preview), timestamp = timestamp,
                            color = Color.White.copy(alpha = if (unread) 0.65f else 0.35f),
                            fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 2, style = NoFontPadding,
                        )
                    }
                } else {
                        AlwaysTimestampText(
                            content = buildAnnotatedString {
                                if (actionLabel.isNotBlank()) {
                                    withStyle(
                                        SpanStyle(
                                            color = Color.White.copy(alpha = if (unread) 0.58f else 0.40f),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp,
                                        ),
                                    ) {
                                        append(actionLabel)
                                        if (preview != null) append(' ')
                                    }
                                }
                                preview?.let { body ->
                                    withStyle(SpanStyle(color = Color.White.copy(alpha = if (unread) 0.65f else 0.35f))) { append(body) }
                                }
                            },
                            timestamp = timestamp,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            style = NoFontPadding,
                        )
                }
            }
            mediaUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

@Composable
private fun AlwaysTimestampText(
    content: AnnotatedString,
    timestamp: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int,
) {
    val measurer = rememberTextMeasurer()
    val resolvedStyle = style.merge(TextStyle(color = color, fontSize = fontSize, lineHeight = lineHeight))
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val displayed = remember(content, timestamp, widthPx, resolvedStyle, maxLines) {
            fun candidate(prefixLength: Int, truncated: Boolean) = buildAnnotatedString {
                var end = prefixLength.coerceIn(0, content.length)
                while (end > 0 && content.text[end - 1].isWhitespace()) end--
                append(content.subSequence(0, end))
                if (truncated) append('…')
                withStyle(SpanStyle(color = Color.White.copy(alpha = .35f), fontSize = 11.sp)) { append(" · $timestamp") }
            }
            fun fits(value: AnnotatedString): Boolean = !measurer.measure(
                text = value,
                style = resolvedStyle,
                maxLines = maxLines,
                overflow = TextOverflow.Clip,
                constraints = Constraints(maxWidth = widthPx),
            ).hasVisualOverflow

            val full = candidate(content.length, false)
            if (fits(full)) full else {
                var low = 0
                var high = content.length
                while (low < high) {
                    val middle = (low + high + 1) / 2
                    if (fits(candidate(middle, true))) low = middle else high = middle - 1
                }
                candidate(low, true)
            }
        }
        Text(displayed, modifier = Modifier.fillMaxWidth(), style = resolvedStyle, maxLines = maxLines, overflow = TextOverflow.Clip)
    }
}

private fun formatNotificationBalance(value: Double): String =
    "$" + java.text.NumberFormat.getIntegerInstance().format(value)

private fun cleanNotificationPreview(raw: String): String = raw
    .replace(Regex("[\\u200B-\\u200F\\u2060-\\u206F\\uFEFF\\u3164]"), "")
    .replace(Regex("https?://\\S+\\.(?:gif|png|jpe?g|webp|mp4|mov)(?:\\?\\S*)?", RegexOption.IGNORE_CASE), "")
    .replace("\r\n", "\n")
    .replace(Regex("\\n(?:[ \\t]*\\n)+"), "\n")
    .trim()

private val NotificationMediaUrl = Regex(
    "https?://\\S+?\\.(?:gif|gifv|webp|png|jpe?g|apng|avif|bmp|heic|heif)(?:[?#]\\S*)?",
    RegexOption.IGNORE_CASE,
)

@Composable
private fun NotificationIcon(type: String, downvote: Boolean, unread: Boolean) {
    val icon: ImageVector = when (type) {
        "post_voted", "comment_voted" -> if (downvote) NotificationIcons.ArrowBigDown else NotificationIcons.ArrowBigUp
        "post_replied", "comment_replied", "room_reply" -> NotificationIcons.MessageSquareText
        "poll_voted" -> NotificationIcons.BarChart3
        "followed" -> NotificationIcons.UserPlus
        "pick_resolved" -> NotificationIcons.CheckCircle2
        "pick_post" -> NotificationIcons.Target
        "trending_post", "balance_updated" -> NotificationIcons.TrendingUp
        else -> NotificationIcons.Bell
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (unread) Gold.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (unread) Gold else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NotificationEmptyState(
    filter: NotificationFilter,
    error: String?,
    modifier: Modifier,
) {
    val title = when {
        error != null -> "Couldn't load notifications"
        filter == NotificationFilter.Unread -> "You're all caught up"
        filter == NotificationFilter.Replies -> "No replies yet"
        else -> "No notifications yet"
    }
    val subtitle = when {
        error != null -> error
        filter == NotificationFilter.Unread -> "No unread notifications to show."
        filter == NotificationFilter.Replies -> "Replies to your posts and comments will appear here."
        else -> "Votes, replies, follows, and account activity will appear here."
    }
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
        }
    }
}
