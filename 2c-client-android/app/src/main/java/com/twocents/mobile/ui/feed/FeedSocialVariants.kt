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
    PicksCardContent(post, userVote, result, onVote)
}

@Composable
internal fun FeedQuoteCard(quote: FeedPost, onClick: (() -> Unit)? = null) {
    val content = remember(quote.uuid, quote.text, quote.meta.giphyUrl) { prepareFeedPostContent(quote) }
    val tweetUrl = quote.meta.tweetUrl ?: commentTweetUrl(quote.meta.link.orEmpty()) ?: commentTweetUrl(quote.text)
    val quoteText = if (tweetUrl != null) stripEmbeddedTweetLink(content.visibleText, tweetUrl) else content.visibleText
    var expanded by remember(quote.uuid) { mutableStateOf(false) }
    val displayedText = if (expanded || quoteText.length <= 400) quoteText else quoteText.take(400).trimEnd()
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
        tweetUrl?.let { TweetEmbedCard(it) }
        com.twocents.mobile.ui.common.LinkPreviewCards(quote.text, quote.meta.link)
        if (!videoUrl.isNullOrBlank()) {
            FeedVideoPlayer(videoUrl, compact = true)
        } else if (quote.meta.images.isNotEmpty()) {
            // Scale each carousel image to the quote's width, preserving its ratio
            // and full content. Leave single-image quotes on their existing path.
            FeedPostMedia(quote.meta.images, compact = true, preserveFullImage = quote.meta.images.size > 1)
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
