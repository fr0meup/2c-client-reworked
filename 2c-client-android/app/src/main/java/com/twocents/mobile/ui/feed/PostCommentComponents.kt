package com.twocents.mobile.ui.feed
import com.twocents.mobile.ui.common.AppDropdown
import com.twocents.mobile.ui.common.toUserDisplay

import android.content.Context

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.toUserDisplay
import com.twocents.mobile.core.platform.ShareActions
import com.twocents.mobile.ui.common.MentionSuggestions
import com.twocents.mobile.ui.common.mentionContext
import com.twocents.mobile.ui.common.mentionMarkup
import com.twocents.mobile.ui.compose.GifPickerSheet
import com.twocents.mobile.ui.common.LinkifiedText
import kotlinx.coroutines.launch

private val DetailSurface = Color(0xFF141410)
private val DetailGold = Color(0xFFC8A44D)
private val DetailEmerald = Color(0xFF34D399)
private val DetailRose = Color(0xFFF43F5E)

@Composable
internal fun PostCommentRow(
    row: FlatPostComment,
    vote: Int,
    alias: String?,
    onReply: () -> Unit,
    onVote: (Int) -> Unit,
    highlighted: Boolean,
    ownComment: Boolean,
    onDelete: () -> Unit,
) {
    val view = LocalView.current
    val visualDepth = row.depth.coerceAtMost(5)
    val canReply = row.depth < 5
    val comment = row.comment
    val tweetUrl = remember(comment.text) { commentTweetUrl(comment.text) }
    val displayAuthor = remember(comment, alias) { comment.author.toUserDisplay(comment.authorUuid, alias) }
    val highlightColor by animateColorAsState(
        targetValue = if (highlighted) DetailGold.copy(alpha = 0.13f) else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(if (highlighted) 180 else 700),
        label = "comment-highlight",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(highlightColor)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset((12 + visualDepth * 16).dp.toPx(), size.height - 0.5.dp.toPx()),
                    end = Offset(size.width - 13.dp.toPx(), size.height - 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
                if (visualDepth <= 0) return@drawBehind
                val lane = 16.dp.toPx()
                val baseX = 12.dp.toPx()
                val branchEndY = minOf(size.height * 0.48f, 22.5.dp.toPx())
                val branchStartY = (branchEndY - 10.dp.toPx()).coerceAtLeast(0f)
                val color = DetailGold.copy(alpha = 0.35f)
                val stroke = 2.dp.toPx()
                for (level in 0 until visualDepth - 1) {
                    // Index zero is the top-level comment's sibling state.
                    // Top-level comments have no connector lane, so reply
                    // continuation lanes begin at ancestor index one.
                    val hiddenDepth = (row.depth - visualDepth).coerceAtLeast(0)
                    val continuationIndex = hiddenDepth + level + 1
                    if (row.ancestorContinuations.getOrNull(continuationIndex) == true) {
                        val x = baseX + lane * level
                        drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke, StrokeCap.Butt)
                    }
                }
                val x = baseX + lane * (visualDepth - 1)
                val ownConnector = Path().apply {
                    moveTo(x, 0f)
                    lineTo(x, if (row.isLast) branchStartY else size.height)
                    if (!row.isLast) moveTo(x, branchStartY)
                    cubicTo(
                        x,
                        branchStartY + 6.dp.toPx(),
                        x + 4.dp.toPx(),
                        branchEndY,
                        x + 10.dp.toPx(),
                        branchEndY,
                    )
                }
                // One path per lane avoids double-painted/brighter pixels at
                // the vertical-to-curve join.
                drawPath(ownConnector, color, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
            .padding(start = (12 + visualDepth * 16).dp, end = 13.dp, top = 9.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UserNetworthPill(displayAuthor, compact = true)
                Text(feedTimeAgo(comment.createdAt), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            if (!comment.deleted) CommentOptionsButton(comment, ownComment, onDelete)
        }
        val body = comment.visibleText()
        if (comment.deleted) {
            Text("[deleted]", color = Color.White.copy(alpha = 0.3f), fontSize = 13.5.sp, fontStyle = FontStyle.Italic)
        } else if (body.isNotBlank()) {
            ExpandableCommentBody(body, comment.uuid)
        }
        tweetUrl?.let { TweetEmbedCard(it) }
        CommentImageAttachments(comment.mediaUrls)
        if (!comment.deleted) com.twocents.mobile.ui.common.LinkPreviewCards(comment.text)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                UserMetaPill(comment.author.toUserDisplay(comment.authorUuid, alias), alias, Modifier, compact = true, fillWidth = false)
            }
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, DetailGold.copy(alpha = 0.22f), CircleShape)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canReply) {
                    Box(Modifier.fillMaxHeight().width(30.dp).clip(CircleShape).clickable { AppHaptics.open(view); onReply() }, contentAlignment = Alignment.Center) {
                        FeedCommentIcon(Modifier.size(14.dp), Color(0xFF8E8B85))
                    }
                    Box(Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.12f)))
                }
                CommentVoteArrow(true, vote == 1, Modifier.fillMaxHeight().width(25.dp).clip(CircleShape).clickable { AppHaptics.toggle(view); onVote(1) })
                Text(
                    comment.upvoteCount.toString(),
                    color = when (vote) { 1 -> DetailEmerald; -1 -> DetailRose; else -> Color.White.copy(alpha = 0.8f) },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
                CommentVoteArrow(false, vote == -1, Modifier.fillMaxHeight().width(25.dp).clip(CircleShape).clickable { AppHaptics.toggle(view); onVote(-1) })
            }
        }
    }
}

@Composable
private fun CommentOptionsButton(comment: PostComment, own: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var expanded by remember(comment.uuid) { mutableStateOf(false) }
    var confirming by remember(comment.uuid) { mutableStateOf(false) }
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).clickable { AppHaptics.open(view); expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.MoreHoriz, "Comment options", tint = Color.White.copy(alpha = .28f), modifier = Modifier.size(15.dp)) }
        AppDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false; confirming = false },
            modifier = Modifier.width(170.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = Color(0xFF141410),
            tonalElevation = 0.dp,
            shadowElevation = 22.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
            verticalTrim = 3.dp,
        ) {
            fun copy(label: String, value: String) {
                ShareActions.copyText(context, label, value)
                expanded = false
            }
            CommentMenuItem(PostMenuIcons.Link2, "Copy link") { copy("2C comment", "https://twocents.money/post/${comment.postUuid}?comment=${comment.uuid}") }
            CommentMenuItem(PostMenuIcons.Copy, "Copy text") { copy("2C comment text", comment.visibleText()) }
            if (own) CommentMenuItem(PostMenuIcons.Trash2, if (confirming) "Are you sure?" else "Delete", destructive = true) {
                if (!confirming) confirming = true else { expanded = false; confirming = false; onDelete() }
            }
        }
    }
}

@Composable
private fun CommentMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (destructive) DetailRose else Color.White.copy(alpha = .8f), fontSize = 13.sp) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = if (destructive) DetailRose else Color.White.copy(alpha = .4f), modifier = Modifier.size(16.dp)) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 11.dp),
        modifier = Modifier.height(38.dp),
    )
}

@Composable
internal fun CommentSortDropdown(value: CommentSort, onChange: (CommentSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        ) {
            Text(
                value.label,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Sort comments",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
        AppDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(140.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = Color(0xFF141410),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            CommentSort.entries.forEach { option ->
                val selected = option == value
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            color = if (selected) DetailGold else Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    onClick = {
                        expanded = false
                        onChange(option)
                    },
                    trailingIcon = {
                        if (selected) Icon(Icons.Rounded.Check, null, tint = DetailGold, modifier = Modifier.size(14.dp))
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(35.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) DetailGold.copy(alpha = 0.14f) else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun CommentVoteArrow(up: Boolean, active: Boolean, modifier: Modifier) {
    val color = if (active) (if (up) DetailEmerald else DetailRose) else Color(0xFF8E8B85)
    FeedVoteIcon(up, modifier.padding(horizontal = 5.dp, vertical = 9.dp), color)
}

@Composable
internal fun CommentSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(88.dp).height(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.055f)))
            Box(Modifier.width(34.dp).height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
        }
        Box(Modifier.fillMaxWidth(0.82f).height(11.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.045f)))
        Box(Modifier.fillMaxWidth(0.58f).height(11.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.fillMaxWidth(0.55f).height(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.045f)))
            Box(Modifier.width(92.dp).height(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.045f)))
        }
    }
}
