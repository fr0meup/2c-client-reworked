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
internal fun FeedPollCard(
    post: FeedPost,
    userVote: Int?,
    results: Map<Int, FeedOptionResult>?,
    isOwner: Boolean,
    onVote: (Int) -> Unit,
    ensureResults: () -> Unit,
) {
    LaunchedEffect(post.uuid, userVote) {
        if (userVote != null || isOwner) ensureResults()
    }
    val showResults = userVote != null || isOwner
    val totalVotes = results?.values?.sumOf { it.votes } ?: 0
    val maxVotes = results?.values?.maxOfOrNull { it.votes } ?: 0
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        post.meta.poll.forEachIndexed { index, label ->
            val result = results?.get(index)
            val votes = result?.votes ?: 0
            val percent = if (totalVotes > 0) (votes * 100f / totalVotes).roundToInt() else 0
            val winning = showResults && votes > 0 && votes == maxVotes
            val selected = userVote == index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (winning) Gold.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.025f))
                    .border(
                        1.dp,
                        if (winning) Gold.copy(alpha = 0.25f) else Color.White.copy(alpha = if (showResults) 0.06f else 0.08f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = !showResults) { onVote(index) },
            ) {
                if (showResults && percent > 0) {
                    val fill = if (winning) Gold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f)
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(fill, size = Size(size.width * (percent / 100f), size.height))
                    }
                }
                if (!showResults) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Box(Modifier.size(16.dp).border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape))
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = noFontPaddingStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            if (selected) {
                                Box(
                                    Modifier.size(17.dp).clip(CircleShape).background(Emerald.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center,
                                ) { FeedCheckIcon(Modifier.size(10.dp), Emerald, 3.2f) }
                            }
                            Text(
                                text = label,
                                color = if (winning) Gold else Color.White.copy(alpha = 0.82f),
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = noFontPaddingStyle,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                text = "$percent%",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = noFontPaddingStyle,
                                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(votes.toString(), color = Color.White.copy(alpha = 0.28f), fontSize = 11.sp, style = noFontPaddingStyle)
                            if (votes > 0) {
                                Text(
                                    text = pollNetWorth(result?.averageBalance ?: 0.0),
                                    color = netWorthTierColor(result?.averageBalance ?: 0.0).copy(alpha = if (winning) 1f else 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = noFontPaddingStyle,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showResults && totalVotes > 0) VoteTotal(totalVotes)
    }
}

@Composable
internal fun FeedLikertCard(
    post: FeedPost,
    userVote: Int?,
    results: Map<Int, FeedOptionResult>?,
    isOwner: Boolean,
    onVote: (Int) -> Unit,
    ensureResults: () -> Unit,
) {
    LaunchedEffect(post.uuid, userVote, isOwner) { if (userVote != null || isOwner) ensureResults() }
    val labels = listOf("Strongly Disagree", "Disagree", "Neutral", "Agree", "Strongly Agree")
    val showResults = userVote != null || isOwner
    val total = results?.values?.sumOf { it.votes } ?: 0
    val maxVotes = results?.values?.maxOfOrNull { it.votes } ?: 0
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val result = results?.get(index)
                val votes = result?.votes ?: 0
                val pct = if (total > 0) (votes * 100f / total).roundToInt() else 0
                val winning = showResults && votes > 0 && votes == maxVotes
                val selected = userVote == index
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clickable(enabled = !showResults) { onVote(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .background(
                                    when {
                                        winning -> Gold.copy(alpha = 0.18f)
                                        selected -> Emerald.copy(alpha = 0.14f)
                                        else -> Color.White.copy(alpha = 0.03f)
                                    },
                                )
                                .border(
                                    if (winning || selected) 1.5.dp else 1.dp,
                                    when {
                                        winning -> Gold.copy(alpha = 0.65f)
                                        selected -> Emerald.copy(alpha = 0.55f)
                                        else -> Color.White.copy(alpha = 0.09f)
                                    },
                                    CircleShape,
                                ),
                        )
                        Text(
                            text = if (showResults) votes.toString() else "•",
                            color = when {
                                selected -> Emerald
                                winning -> Gold
                                else -> Color.White.copy(alpha = if (showResults) 0.75f else 0.25f)
                            },
                            fontSize = if (showResults) 14.5.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            style = noFontPaddingStyle,
                        )
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(17.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                                    .border(2.dp, Color(0xFF0A0907), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { FeedCheckIcon(Modifier.size(9.dp), Color.White, 3.5f) }
                        }
                    }
                    Text(
                        text = label,
                        color = when {
                            selected -> Emerald
                            winning -> Gold
                            else -> Color.White.copy(alpha = 0.28f)
                        },
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2,
                        style = noFontPaddingStyle,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (showResults) Text("$pct%", color = Color.White.copy(alpha = 0.22f), fontSize = 9.5.sp, style = noFontPaddingStyle)
                    if (showResults && votes > 0) {
                        Text(
                            likertNetWorth(result?.averageBalance ?: 0.0),
                            color = netWorthTierColor(result?.averageBalance ?: 0.0).copy(alpha = if (winning) 1f else 0.6f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            style = noFontPaddingStyle,
                        )
                    }
                }
            }
        }
        if (showResults && total > 0) {
            Spacer(Modifier.height(14.dp))
            VoteTotal(total)
        }
    }
}

@Composable
private fun VoteTotal(total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        Text(
            text = "${formatNumber(total)} ${if (total == 1) "vote" else "votes"}",
            color = Color.White.copy(alpha = 0.22f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            style = noFontPaddingStyle,
        )
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}


