package com.twocents.mobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.notifications.NotificationIcons
import com.twocents.mobile.notifications.LocalActivitySummary
import com.twocents.mobile.notifications.NotificationHistoryStore
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.feed.*
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.messages.RoomSummary
import com.twocents.mobile.ui.messages.RoomMember
import com.twocents.mobile.ui.messages.otherMember
import com.twocents.mobile.ui.messages.OfflineModeStore
import com.twocents.mobile.ui.messages.cleanPreview
import com.twocents.mobile.ui.profile.FollowingSheet
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.kotlin.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SidebarGold = Color(0xFFC8A44D)
private val SidebarShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
private val NoFontPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
@Composable
internal fun ProfileStat(icon: ImageVector, value: Int, label: String, onClick: (() -> Unit)? = null) = Row(
    modifier = Modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    Icon(icon, null, tint = SidebarGold.copy(alpha = .75f), modifier = Modifier.size(15.5.dp))
    Text(NumberFormat.getNumberInstance(Locale.US).format(value), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, style = NoFontPadding)
    Text(label, color = Color.White.copy(alpha = .45f), fontSize = 10.5.sp, maxLines = 1, softWrap = false, style = NoFontPadding)
    if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .22f), modifier = Modifier.size(11.dp))
}

@Composable internal fun StatDivider() = Box(
    // Keep the divider centered between the preceding chevron and the next icon.
    Modifier.padding(start = 4.dp, end = 12.dp).width(1.dp).height(10.dp)
        .background(Color.White.copy(alpha = .08f)),
)

@Composable
internal fun LeaderboardCard(position: Int, total: Int, onOpen: () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(onClick = onOpen)
        .background(Brush.verticalGradient(listOf(Color(0xFF15130E), Color(0xFF0E0E0B))))
        .border(.5.dp, Color.White.copy(alpha = .075f), RoundedCornerShape(17.dp)).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.sidebar_leaderboard_selected), null, Modifier.size(42.dp), contentScale = ContentScale.Fit)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (position > 0 && total > 0) {
                Text(
                    "#${NumberFormat.getNumberInstance(Locale.US).format(position)} of ${NumberFormat.getNumberInstance(Locale.US).format(total)}",
                    color = SidebarGold.copy(alpha = .88f), fontSize = 18.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, style = NoFontPadding,
                )
            } else {
                Text("Loading your position…", color = Color.White.copy(alpha = .34f), fontSize = 13.sp, style = NoFontPadding)
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun ActivityStatsCard(
    summary: LocalActivitySummary,
    recent: List<SidebarActivityItem>,
    onOpen: (String, String?) -> Unit,
    onOpenProfile: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF17140D), Color(0xFF0E0E0B))))
            .border(.5.dp, SidebarGold.copy(alpha = .14f), RoundedCornerShape(17.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable(onClick = onOpenProfile).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(painterResource(R.drawable.nav_feed_selected), null, Modifier.size(39.dp), contentScale = ContentScale.Fit)
            Text("ACTIVITY", color = Color.White.copy(alpha = .88f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, style = NoFontPadding)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.size(20.dp))
        }
        if (recent.isEmpty()) {
            Text("No recent posts or comments", color = Color.White.copy(alpha = .3f), fontSize = 10.5.sp, style = NoFontPadding)
        } else recent.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable { onOpen(item.postUuid, item.commentUuid) }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(if (item.commentUuid == null) SidebarGold else Color.White.copy(alpha = .35f)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(item.preview.ifBlank { "View ${item.label.lowercase()}" }, color = Color.White.copy(alpha = .7f), fontSize = 10.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = NoFontPadding)
                    Text(item.label, color = Color.White.copy(alpha = .28f), fontSize = 8.8.sp, style = NoFontPadding)
                }
                Text(feedTimeAgo(item.createdAt), color = Color.White.copy(alpha = .25f), fontSize = 8.8.sp, style = NoFontPadding)
            }
        }
        Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .075f)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("STATS", color = SidebarGold.copy(alpha = .75f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, style = NoFontPadding)
            Text("  (BETA)", color = Color.White.copy(alpha = .25f), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, style = NoFontPadding)
            Spacer(Modifier.weight(1f))
            Text("TODAY", color = Color.White.copy(alpha = .25f), fontSize = 8.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, style = NoFontPadding)
            Text("THIS WEEK", color = Color.White.copy(alpha = .25f), fontSize = 7.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, maxLines = 1, style = NoFontPadding)
        }
        ActivityRow("Upvotes", summary.todayUpvotes, summary.weekUpvotes)
        ActivityRow("Replies", summary.todayReplies, summary.weekReplies)
        ActivityRow("Followers", summary.todayFollowers, summary.weekFollowers)
    }
}

@Composable
internal fun RecentRoomsCard(
    rooms: List<RoomSummary>,
    authUuid: String,
    onOpen: (RoomSummary) -> Unit,
    onOpenOverview: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF15130E), Color(0xFF0D0E0C))))
            .border(.5.dp, Color.White.copy(alpha = .075f), RoundedCornerShape(17.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable(onClick = onOpenOverview).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(painterResource(R.drawable.nav_messages_selected), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
            Text("RECENT DMS", color = Color.White.copy(alpha = .86f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .9.sp, style = NoFontPadding)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.size(20.dp))
        }
        if (rooms.isEmpty()) Text("No recent DMs", color = Color.White.copy(alpha = .3f), fontSize = 10.5.sp, style = NoFontPadding)
        else rooms.forEach { room ->
            val member = room.otherMember(authUuid)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable { onOpen(room) }.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                member?.let {
                    ComposeNetworthPill(
                        ComposeAuthorProfile(it.uuid, it.balance, it.subscriptionType, it.role, it.gender, it.age, it.arena),
                        it.uuid,
                        compact = true,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(member?.alias ?: member?.username ?: room.name.ifBlank { "DM" }, color = Color.White.copy(alpha = .74f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = NoFontPadding)
                    val preview = room.lastMessage.takeIf(String::isNotBlank)?.let(::cleanPreview).orEmpty()
                        .replace(Regex("\\[(@[^]]+)]\\s*\\(/user/[0-9a-fA-F-]{32,36}\\)"), "$1")
                    Text(preview.ifBlank { "No messages yet" }, color = Color.White.copy(alpha = .3f), fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = NoFontPadding)
                }
                Text(feedTimeAgo(room.lastMessageAt), color = Color.White.copy(alpha = .25f), fontSize = 8.8.sp, style = NoFontPadding)
            }
        }
    }
}

@Composable
internal fun ActivityRow(label: String, today: Int, week: Int, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = Color.White.copy(alpha = .58f), fontSize = 11.5.sp, modifier = Modifier.weight(1f), style = NoFontPadding)
    Text(today.toString(), color = Color.White.copy(alpha = .9f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, style = NoFontPadding)
    Text(week.toString(), color = SidebarGold.copy(alpha = .85f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, style = NoFontPadding)
}

internal fun sidebarActivityPreview(raw: String, meta: JSONObject?, postType: Int = 0): String {
    val visible = prepareCommentText(raw)
        .replace(Regex("\\[(@[^]]+)]\\s*\\(/user/[0-9a-fA-F-]{32,36}\\)"), "$1")
        .replace(Regex("https?://\\S+?\\.(?:mp4|mov|webm|m4v|gifv)(?:[?#]\\S*)?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[\\u2800\\u3000\\u3164]"), " ")
        .trim()
    if (visible.isNotBlank()) return visible
    val keys = meta?.keys()?.asSequence()?.toList().orEmpty()
    val values = keys.map { key -> meta?.opt(key)?.toString().orEmpty() }
    return when {
        postType == 10 || keys.any { it.contains("video", true) } || values.any { it.contains(Regex("\\.(?:mp4|mov|webm)(?:[?#]|$)", RegexOption.IGNORE_CASE)) } -> "Video"
        keys.any { it.contains("giphy", true) || it.contains("gif", true) } || values.any { it.contains(Regex("\\.(?:gif|gifv)(?:[?#]|$)", RegexOption.IGNORE_CASE)) } -> "GIF"
        postType == 4 || keys.any { it.contains("image", true) } || values.any { it.contains(Regex("\\.(?:png|jpe?g|webp|heic|avif)(?:[?#]|$)", RegexOption.IGNORE_CASE)) } -> "Image"
        else -> "Media"
    }
}

internal fun joinedAgo(raw: String): String? = runCatching {
    val instant = runCatching { Instant.parse(raw) }.getOrElse { OffsetDateTime.parse(raw).toInstant() }
    val then = instant.atZone(ZoneOffset.UTC); val now = Instant.now().atZone(ZoneOffset.UTC)
    val years = ChronoUnit.YEARS.between(then, now)
    val months = ChronoUnit.MONTHS.between(then, now)
    if (years > 0) "${years}y" else if (months > 0) "${months}mo" else "new"
}.getOrNull()
