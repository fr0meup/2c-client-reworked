package com.twocents.mobile.ui.profile

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.feed.*
import com.twocents.mobile.ui.shell.ProfileSkeleton
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.notifications.NotificationIcons
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.toUserDisplay
import com.twocents.mobile.ui.feed.UserMetaPill
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ProfileGold = Color(0xFFC8A44D)
private val ProfileSurface = Color.White.copy(alpha = .025f)
private val ProfileBorder = Color.White.copy(alpha = .07f)
private val NoProfilePadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
internal fun ProfileCommentCard(comment: ProfileComment, currentVote: Int, alias: String?, onOpenPost: (String, String) -> Unit, onVote: (Int) -> Unit) {
    val view = LocalView.current
    Column(
        Modifier.fillMaxWidth().clickable { AppHaptics.navigate(view); onOpenPost(comment.postUuid, comment.uuid) }.padding(start = 13.dp, top = 13.dp, end = 13.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        comment.postTitle?.takeIf(String::isNotBlank)?.let { title ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White.copy(alpha = .45f), modifier = Modifier.size(13.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White.copy(alpha = .85f), fontWeight = FontWeight.Bold)) { append("Commented on: ") }
                        withStyle(SpanStyle(color = Color.White.copy(alpha = .45f), fontWeight = FontWeight.Normal)) { append(title) }
                    },
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = NoProfilePadding,
                )
            }
        }
        if (comment.deleted) {
            Text("[deleted]", color = Color.White.copy(alpha = .3f), fontSize = 15.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic, style = NoProfilePadding)
        } else {
            val visibleText = prepareCommentText(comment.text)
            val tweetUrl = commentTweetUrl(comment.text)
            if (visibleText.isNotBlank()) ExpandableCommentBody(visibleText, comment.uuid)
            tweetUrl?.let { TweetEmbedCard(it) }
            CommentImageAttachments(comment.mediaUrls)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    UserMetaPill(comment.author.toUserDisplay(comment.authorUuid, alias), alias, Modifier, compact = true, fillWidth = false, elo = null)
                }
                Text(feedTimeAgo(comment.createdAt), color = Color.White.copy(alpha = .4f), fontSize = 12.sp, maxLines = 1, softWrap = false, style = NoProfilePadding)
            }
            Row(
                Modifier.height(34.dp).clip(CircleShape).background(Color.White.copy(alpha = .05f)).border(1.dp, ProfileGold.copy(alpha = .22f), CircleShape).padding(horizontal = 3.5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.fillMaxHeight().width(30.dp).clip(CircleShape).clickable { AppHaptics.toggle(view); onVote(1) }, contentAlignment = Alignment.Center) {
                    FeedVoteIcon(true, Modifier.size(13.dp), if (currentVote == 1) Color(0xFF34D399) else Color(0xFF8E8B85))
                }
                Text(comment.upvotes.toString(), color = when (currentVote) { 1 -> Color(0xFF34D399); -1 -> Color(0xFFF43F5E); else -> Color.White.copy(alpha = .75f) }, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 14.dp), textAlign = TextAlign.Center, style = NoProfilePadding)
                Box(Modifier.fillMaxHeight().width(30.dp).clip(CircleShape).clickable { AppHaptics.toggle(view); onVote(-1) }, contentAlignment = Alignment.Center) {
                    FeedVoteIcon(false, Modifier.size(13.dp), if (currentVote == -1) Color(0xFFF43F5E) else Color(0xFF8E8B85))
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
}

internal val profileMediaUrl = Regex("https?://\\S+?(?:gif|png|jpe?g|webp)(?:\\?\\S*)?", RegexOption.IGNORE_CASE)

@Composable internal fun ProfileEmpty(message: String) = Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(150.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .02f)).border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text(message, color = Color.White.copy(alpha = .4f), fontSize = 13.sp) }
