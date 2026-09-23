package com.twocents.mobile.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.kotlin.R
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

// Apple/Android outlines from Font Awesome Free 6 Brands (CC BY 4.0):
// https://fontawesome.com/license/free. The official APK bundles this font;
// keeping only two paths avoids loading the whole font for tiny header icons.
private const val ApplePlatformPath = "M18.24 12.64Q18.19 9.94 20.69 8.46Q19.27 6.5 16.52 6.25Q15.68 6.21 14.85 6.45Q14.01 6.7 13.38 6.94Q12.59 7.24 12.2 7.29Q11.66 7.24 10.82 6.89Q9.64 6.4 8.42 6.3Q6.25 6.35 4.58 7.97Q2.87 9.59 2.77 12.88Q2.77 14.8 3.46 16.86Q3.8 17.79 4.58 19.27Q5.37 20.69 6.45 21.82Q7.53 22.95 8.76 23Q9.54 22.95 10.33 22.61Q11.21 22.17 12.44 22.12Q13.62 22.17 14.46 22.56Q15.24 22.95 16.22 23Q17.4 22.9 18.43 21.87Q19.46 20.79 20.2 19.46Q20.94 18.09 21.23 17.16Q19.02 15.93 18.58 14.41Q18.14 12.93 18.24 12.64ZM15.44 4.58Q16.37 3.36 16.57 2.38Q16.71 1.44 16.62 1Q15.73 1.05 14.8 1.54Q13.92 2.03 13.28 2.72Q11.95 4.19 12.05 6.25Q13.96 6.3 15.44 4.58Z"
private const val AndroidPlatformPath = "M17.08 15.07Q16.24 15 16.16 14.16Q16.24 13.32 17.08 13.24Q17.88 13.32 18 14.16Q17.88 15 17.08 15.07ZM6.92 15.07Q6.12 15 6 14.16Q6.12 13.32 6.92 13.24Q7.76 13.32 7.84 14.16Q7.76 15 6.92 15.07ZM17.39 9.54 19.22 6.37Q19.33 6.18 19.26 5.98Q19.14 5.79 18.91 5.79Q18.68 5.79 18.57 5.98L16.7 9.23Q14.44 8.2 12 8.2Q9.56 8.2 7.3 9.23L5.43 5.98Q5.32 5.79 5.09 5.79Q4.86 5.79 4.74 5.98Q4.67 6.18 4.78 6.37L6.61 9.54Q4.21 10.87 2.76 13.13Q1.27 15.34 1 18.21H23Q22.73 15.34 21.24 13.13Q19.79 10.87 17.39 9.54Z"

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
    expandableTimestamp: Boolean = false,
) {
    var timestampExpanded by androidx.compose.runtime.saveable.rememberSaveable(post.uuid) { mutableStateOf(false) }
    val fullTimestamp = remember(post.createdAt) { formatExactJoined(post.createdAt) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedNetworthPill(post, compact = true, navigationEnabled = authorNavigationEnabled)
            if (expandableTimestamp) Text(
                if (timestampExpanded) fullTimestamp else feedTimeAgo(post.createdAt),
                color = Color.White.copy(alpha = .4f), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = (if (timestampExpanded) Modifier.weight(1f) else Modifier)
                    .clickable { timestampExpanded = !timestampExpanded }.padding(vertical = 5.dp),
            ) else HeaderMetaText(feedTimeAgo(post.createdAt))
            // Expanded time uses the metadata slot rather than pushing the pill
            // or menu off-screen. Collapsing restores the original header exactly.
            if (!expandableTimestamp || !timestampExpanded) {
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
    // These glyphs are local: the old Next.js asset URLs expire on web deploys.
    when (platform?.lowercase(Locale.ROOT)) {
        "ios" -> FeedBrandPlatformIcon(ApplePlatformPath, "iOS")
        "android" -> FeedBrandPlatformIcon(AndroidPlatformPath, "Android")
        else -> FeedWebIcon(Modifier.size(13.dp))
    }
}

@Composable
private fun FeedBrandPlatformIcon(pathData: String, label: String) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(Modifier.size(14.dp).semantics { contentDescription = label }) {
        withTransform({ scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) }) {
            drawPath(path, Color(0xFFA6AFB1))
        }
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
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val hasAlias = !alias.isNullOrBlank() && !alias.equals("null", ignoreCase = true)
    val hasGender = !user.gender.isNullOrBlank()
    val hasAge = user.age?.let { it > 0 } == true
    val hasArena = !user.arena.isNullOrBlank()
    val hasElo = elo != null && elo > 0
    val hasJoined = !joined.isNullOrBlank()
    var joinedExpanded by remember(joinedExact) { mutableStateOf(false) }
    if (!hasAlias && !hasGender && !hasAge && !hasArena && !hasElo && !hasJoined && leadingContent == null) {
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
            // Optional slot is used only by comments; other pills are unchanged.
            if (leadingContent != null) {
                leadingContent()
                Spacer(Modifier.width(5.dp))
                hasPrevious = false
            }
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
                    Image(painterResource(R.drawable.twocents_location), contentDescription = null,
                        modifier = Modifier.size(if (compact) 16.dp else 18.dp), alpha = .8f)
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
