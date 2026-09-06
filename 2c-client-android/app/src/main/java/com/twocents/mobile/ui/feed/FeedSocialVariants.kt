package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.common.AppHaptics
import java.net.URI
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Gold = Color(0xFFC8A44D)
private val Emerald = Color(0xFF34D399)
private val Rose = Color(0xFFF43F5E)
private val CardSurface = Color.White.copy(alpha = 0.02f)
private val CardBorder = Color.White.copy(alpha = 0.08f)
private val noFontPaddingStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
internal fun FeedPicksCard(
    post: FeedPost,
    userVote: String?,
    result: FeedPicksResult?,
    onVote: (String) -> Unit,
    ensureResults: () -> Unit,
) {
    LaunchedEffect(post.uuid) { ensureResults() }
    val picks = result ?: FeedPicksResult()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp))) {
            Box(
                Modifier.weight(picks.yesPercent.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Emerald.copy(alpha = 0.35f)).padding(start = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("${picks.yesPercent}% Yes", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.weight(picks.noPercent.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Rose.copy(alpha = 0.35f)).padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("${picks.noPercent}% No", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            post.meta.resolutionDeadline?.let {
                Text("Resolves: ${formatDeadline(it)}", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }
            if (picks.resolved) Text("Resolved: ${picks.correctAnswer?.uppercase()}", color = if (picks.correctAnswer == "yes") Emerald else Rose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (!picks.resolved) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickButton("yes", userVote, Modifier.weight(1f), onVote)
                PickButton("no", userVote, Modifier.weight(1f), onVote)
            }
        }
    }
}

@Composable
private fun PickButton(vote: String, selected: String?, modifier: Modifier, onVote: (String) -> Unit) {
    val color = if (vote == "yes") Emerald else Rose
    val active = selected == vote
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (active) 0.25f else 0.08f))
            .border(1.dp, color.copy(alpha = if (active) 1f else 0.3f), RoundedCornerShape(10.dp))
            .clickable(enabled = selected == null) { onVote(vote) },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (active) "✓ Voted ${vote.replaceFirstChar { it.uppercase() }}" else "Vote ${vote.replaceFirstChar { it.uppercase() }}", color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun FeedQuoteCard(quote: FeedPost, onClick: (() -> Unit)? = null) {
    val content = remember(quote.uuid, quote.text, quote.meta.giphyUrl) { prepareFeedPostContent(quote) }
    var expanded by remember(quote.uuid) { mutableStateOf(false) }
    val displayedText = if (expanded || !content.isLong) content.visibleText else content.visibleText.take(400).trimEnd()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FeedNetworthPill(quote, compact = true)
            Text("·", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
            Text(feedTimeAgo(quote.createdAt), color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp)
            if (quote.topic.isNotBlank()) {
                Text("·", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
                Text("$/${quote.topic.lowercase()}", color = Gold, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        if (quote.title.isNotBlank()) Text(quote.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        if (displayedText.isNotBlank()) FeedPostText(displayedText, compact = true, modifier = Modifier.padding(top = 4.dp))
        if (content.isLong) {
            Text(
                if (expanded) "Show less" else "Show more",
                color = Gold,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded },
            )
        }
        val videoUrl = quote.meta.videoUrl
        if (!videoUrl.isNullOrBlank()) {
            FeedVideoPlayer(videoUrl, compact = true)
        } else if (quote.meta.images.isNotEmpty()) {
            FeedPostMedia(quote.meta.images, compact = true)
        }
    }
}

@Composable
internal fun FeedLinkCard(url: String) {
    val context = LocalContext.current
    val host = runCatching { URI(url).host }.getOrNull().orEmpty().ifBlank { url }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Gold.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Link, null, tint = Gold, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(host, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(url, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.OpenInNew, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
    }
}


