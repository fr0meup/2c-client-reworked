package com.twocents.mobile.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.toUserDisplay
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin

private val PostGold = Color(0xFFC8A44D)
private val PostEmerald = Color(0xFF34D399)
private val PostRose = Color(0xFFF43F5E)
private val ActionGray = Color(0xFF8E8B85)

private const val IOS_ICON_URL = "https://www.twocents.money/_next/image?url=%2F_next%2Fstatic%2Fmedia%2Fapple.0xxwgeqy4kw1g.png&w=32&q=75&dpl=dpl_5ovAARAu8zMP9MtrCL9RTcRsDq7b"
private const val ANDROID_ICON_URL = "https://www.twocents.money/_next/image?url=%2F_next%2Fstatic%2Fmedia%2Fandroid.0ujtbb1oilk8l.png&w=32&q=75&dpl=dpl_5ovAARAu8zMP9MtrCL9RTcRsDq7b"

@Composable
internal fun FeedPostHeader(
    post: FeedPost,
    showMenu: Boolean,
    authUuid: String,
    controller: FeedController,
    onQuotePost: ((FeedPost) -> Unit)?,
    onOpenMessages: (() -> Unit)?,
    onDeleted: (() -> Unit)?,
    authorNavigationEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedNetworthPill(post, compact = true, navigationEnabled = authorNavigationEnabled)
            HeaderMetaText(feedTimeAgo(post.createdAt))
            HeaderDot()
            FeedPlatformIcon(post.meta.platform)
            if (post.topic.isNotBlank()) {
                HeaderDot()
                Text(
                    text = "$/${post.topic.lowercase()}",
                    color = PostGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            HeaderDot()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.5.dp)) {
                FeedEyeIcon(Modifier.size(12.dp))
                Text(post.viewCount.toString(), color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (showMenu) {
            PostOptionsButton(
                post = post,
                authUuid = authUuid,
                controller = controller,
                onQuotePost = onQuotePost,
                onDeleted = onDeleted,
                onOpenMessages = onOpenMessages,
                buttonSize = 44.dp,
                buttonOffsetX = 3.dp,
                adaptiveMiddleOffset = true,
            )
        }
    }
}

@Composable
private fun FeedPlatformIcon(platform: String?) {
    val context = LocalContext.current
    val url = when (platform) {
        "ios" -> IOS_ICON_URL
        "android" -> ANDROID_ICON_URL
        else -> null
    }
    if (url != null) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).memoryCacheKey("platform-$platform").build(),
            contentDescription = platform,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(13.5.dp),
        )
    } else {
        FeedWebIcon(Modifier.size(13.dp))
    }
}

@Composable private fun HeaderMetaText(value: String) = Text(value, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
@Composable private fun HeaderDot() = Text("·", color = Color.White.copy(alpha = 0.25f), fontSize = 12.sp)

@Composable
internal fun FeedPostActions(post: FeedPost, currentVote: Int, alias: String?, onVote: (Int) -> Unit) {
    val view = LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FeedUserMetaPill(post, alias, Modifier.weight(1f))
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, PostGold.copy(alpha = 0.22f), CircleShape)
                .padding(horizontal = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 8.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.5.dp),
            ) {
                FeedCommentIcon(Modifier.size(15.dp), ActionGray)
                ActionText(post.commentCount.toString(), Color.White.copy(alpha = 0.75f))
            }
            Box(Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.12f)))
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoteArrow(up = true, active = currentVote == 1, modifier = Modifier.fillMaxHeight().width(24.dp).clip(CircleShape).clickable { AppHaptics.toggle(view); onVote(1) })
                ActionText(
                    post.upvoteCount.toString(),
                    when (currentVote) { 1 -> PostEmerald; -1 -> PostRose; else -> Color.White.copy(alpha = 0.85f) },
                )
                VoteArrow(up = false, active = currentVote == -1, modifier = Modifier.fillMaxHeight().width(24.dp).clip(CircleShape).clickable { AppHaptics.toggle(view); onVote(-1) })
            }
        }
    }
}

@Composable
internal fun FeedUserMetaPill(
    post: FeedPost,
    alias: String?,
    modifier: Modifier,
    compact: Boolean = false,
    fillWidth: Boolean = true,
    elo: Int? = null,
    joined: String? = null,
    joinedExact: String? = null,
) = UserMetaPill(
    user = post.toUserDisplay(alias, elo, joinedExact),
    alias = alias,
    modifier = modifier,
    compact = compact,
    fillWidth = fillWidth,
    elo = elo,
    joined = joined,
    joinedExact = joinedExact,
)

@Composable
internal fun UserMetaPill(
    user: com.twocents.mobile.ui.common.UserDisplayModel,
    alias: String?,
    modifier: Modifier,
    compact: Boolean = false,
    fillWidth: Boolean = true,
    elo: Int? = null,
    joined: String? = null,
    joinedExact: String? = null,
) {
    val hasAlias = !alias.isNullOrBlank() && !alias.equals("null", ignoreCase = true)
    val hasGender = !user.gender.isNullOrBlank()
    val hasAge = user.age?.let { it > 0 } == true
    val hasArena = !user.arena.isNullOrBlank()
    val hasElo = elo != null && elo > 0
    val hasJoined = !joined.isNullOrBlank()
    var joinedExpanded by remember(joinedExact) { mutableStateOf(false) }
    if (!hasAlias && !hasGender && !hasAge && !hasArena && !hasElo && !hasJoined) {
        Spacer(modifier)
        return
    }
    Box(
        modifier = modifier
            .height(if (compact) 30.dp else 34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (compact) Color.White.copy(alpha = 0.08f) else PostGold.copy(alpha = 0.22f), CircleShape),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 3.dp, vertical = 1.dp)
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .fillMaxHeight()
                .clipToBounds()
                .horizontalScroll(rememberScrollState())
                .padding(start = 3.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var hasPrevious = false
            if (hasElo) {
                val eloColor = when {
                    elo >= 1700 -> Color(0xFFDAB232)
                    elo >= 1500 -> Color(0xFFC0C0D2)
                    else -> Color(0xFFCD7F32)
                }
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .height(if (compact) 24.dp else 27.dp)
                        .clip(CircleShape)
                        .background(eloColor.copy(alpha = .18f))
                        .border(1.dp, eloColor.copy(alpha = .35f), CircleShape)
                        .padding(horizontal = if (compact) 6.dp else 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        RoundedEloStar(eloColor, if (compact) 9.dp else 10.dp)
                        Text(
                            elo.toString(),
                            color = eloColor,
                            fontSize = if (compact) 10.5.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        )
                    }
                }
                hasPrevious = true
            }
            if (hasAlias) {
                Text(
                    text = alias.orEmpty(),
                    color = PostGold,
                    fontSize = if (compact) 12.sp else 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    modifier = Modifier.padding(start = if (hasPrevious) if (compact) 5.dp else 6.dp else 2.dp, end = if (compact) 6.dp else 8.dp),
                )
                hasPrevious = true
            }
            if (hasGender) {
                if (hasPrevious) MetaSeparator(compact)
                Box(Modifier.padding(start = if (hasPrevious) if (compact) 5.dp else 7.dp else 2.dp, end = if (compact) 5.dp else 7.dp), contentAlignment = Alignment.Center) {
                    FeedGenderIcon(user.gender.orEmpty(), if (compact) 12.5.dp else 14.5.dp)
                }
                hasPrevious = true
            }
            if (hasAge) {
                if (hasPrevious) MetaSeparator(compact)
                Text(
                    user.age.toString(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = if (compact) 12.sp else 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    modifier = Modifier.padding(start = if (hasPrevious) if (compact) 6.dp else 8.dp else 2.dp, end = if (compact) 6.dp else 8.dp),
                )
                hasPrevious = true
            }
            if (hasArena) {
                if (hasPrevious) MetaSeparator(compact)
                Row(
                    modifier = Modifier.padding(start = if (hasPrevious) if (compact) 6.dp else 8.dp else 2.dp, end = if (compact) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(LOCATION_ICON_URL).memoryCacheKey("feed-location-icon").build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                        alpha = 0.8f,
                    )
                    Text(
                        user.arena.orEmpty(),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = if (compact) 12.sp else 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    )
                }
                hasPrevious = true
            }
            if (hasJoined) {
                if (hasPrevious) MetaSeparator(compact)
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(if (!joinedExact.isNullOrBlank()) Modifier.clickable { joinedExpanded = !joinedExpanded } else Modifier)
                        .padding(start = if (hasPrevious) if (compact) 6.dp else 8.dp else 2.dp, end = if (compact) 7.dp else 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                ) {
                    Text(
                        "JOINED",
                        color = Color.White.copy(alpha = .3f),
                        fontSize = if (compact) 9.sp else 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .5.sp,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    )
                    Text(
                        if (joinedExpanded) formatExactJoined(joinedExact.orEmpty()) else joined.orEmpty(),
                        color = Color.White.copy(alpha = .8f),
                        fontSize = if (compact) 11.5.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    )
                }
            }
        }
    }
}

private fun formatExactJoined(raw: String): String = runCatching {
    val instant = runCatching { Instant.parse(raw) }.getOrElse { OffsetDateTime.parse(raw).toInstant() }
    DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm", Locale.getDefault()).format(instant.atZone(ZoneId.systemDefault()))
}.getOrDefault(raw)

@Composable
private fun RoundedEloStar(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outer = this.size.minDimension * .46f
        val inner = outer * .47f
        val path = Path()
        repeat(10) { index ->
            val radius = if (index % 2 == 0) outer else inner
            val angle = -Math.PI / 2 + index * Math.PI / 5
            val point = Offset(
                center.x + (cos(angle) * radius).toFloat(),
                center.y + (sin(angle) * radius).toFloat(),
            )
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color, style = Fill)
        drawPath(path, color, style = Stroke(width = 1.35.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
private fun MetaSeparator(compact: Boolean = false) {
    Box(Modifier.width(1.dp).height(if (compact) 14.dp else 16.dp).background(Color.White.copy(alpha = 0.1f)))
}

@Composable
private fun ActionText(value: String, color: Color) {
    Text(
        text = value,
        color = color,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    )
}

@Composable
private fun VoteArrow(up: Boolean, active: Boolean, modifier: Modifier) {
    val color = if (active) if (up) PostEmerald else PostRose else ActionGray
    FeedVoteIcon(up, modifier.padding(horizontal = 5.dp, vertical = 10.dp), color)
}

internal fun feedTimeAgo(raw: String): String {
    if (raw.isBlank()) return ""
    val past = runCatching { Instant.parse(raw) }.getOrElse {
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull() ?: return ""
    }
    val seconds = floor((Instant.now().epochSecond - past.epochSecond).coerceAtLeast(1).toDouble()).toLong()
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 2_592_000 -> "${seconds / 86_400}d"
        seconds < 31_536_000 -> "${seconds / 2_592_000}mo"
        else -> "${seconds / 31_536_000}y"
    }
}


private const val LOCATION_ICON_URL = "https://www.twocents.money/_next/image?url=%2F_next%2Fstatic%2Fmedia%2Flocation-icon.432s1sddmkeug.png&w=48&q=75&dpl=dpl_5ovAARAu8zMP9MtrCL9RTcRsDq7b"

