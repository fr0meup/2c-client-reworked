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

internal fun parseProfile(root: JSONObject): ProfileState {
    val userJson = root.optJSONObject("user") ?: error("Profile user missing")
    val user = ProfileUser(userJson.optString("uuid"), userJson.optString("created_at"), userJson.optInt("disabled"), userJson.optDouble("balance"), userJson.nullableString("bio"), userJson.nullableInt("age"), userJson.nullableString("gender"), userJson.nullableString("arena"), userJson.optInt("subscription_type", 1), userJson.nullableString("role"), userJson.optInt("elo_rating"))
    val votes = mutableMapOf<String, Int>(); val polls = mutableMapOf<String, Int>(); val likerts = mutableMapOf<String, Int>(); val picks = mutableMapOf<String, String>()
    fun parsePostGroup(group: JSONObject?): List<FeedPost> {
        group?.optJSONArray("votes").objects().forEach { votes[it.optString("content_uuid")] = it.optInt("vote_type") }
        group?.optJSONArray("polls").objects().forEach { polls[it.optString("post_uuid")] = it.optInt("option") }
        group?.optJSONArray("likertVotes").objects().forEach { likerts[it.optString("post_uuid")] = it.optInt("option") }
        group?.optJSONArray("pickVotes").objects().forEach { picks[it.optString("post_uuid")] = it.optString("vote") }
        return group?.optJSONArray("posts").objects().mapNotNull(::parseFeedPost).orEmpty()
    }
    val recentComments = root.optJSONObject("recentComments")
    val titles = recentComments?.optJSONObject("postTitles")
    val commentVotes = mutableMapOf<String, Int>()
    recentComments?.optJSONArray("votes").objects().forEach { vote ->
        val uuid = vote.optString("content_uuid").ifBlank { vote.optString("comment_uuid") }
        if (uuid.isNotBlank()) commentVotes[uuid] = vote.optInt("vote_type")
    }
    val comments = recentComments?.optJSONArray("comments").objects().mapNotNull { item ->
        val uuid = item.optString("uuid").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val authorMeta = item.optJSONObject("author_meta")
        val commentMeta = item.optJSONObject("comment_meta")
        val postUuid = item.optString("post_uuid")
        val text = item.optString("text")
        val media = buildList {
            listOf("giphy_url", "image_url", "imageUrl", "src").forEach { key -> commentMeta?.nullableString(key)?.let { add(normalizeMediaUrl(it)) } }
            profileMediaUrl.findAll(text).map { normalizeMediaUrl(it.value.trimEnd('.', ',', ')')) }.forEach(::add)
        }.distinct()
        ProfileComment(
            uuid = uuid,
            postUuid = postUuid,
            createdAt = item.optString("created_at"),
            text = text,
            upvotes = item.optInt("upvote_count"),
            author = FeedAuthor(
                balance = authorMeta?.optDouble("balance") ?: 0.0,
                subscriptionType = authorMeta?.optInt("subscription_type", 1) ?: 1,
                age = authorMeta?.nullableInt("age"),
                gender = authorMeta?.nullableString("gender"),
                arena = authorMeta?.nullableString("arena"),
                role = authorMeta?.nullableString("role"),
                eloRating = authorMeta?.optDouble("elo_rating"),
                alias = authorMeta?.nullableString("alias") ?: authorMeta?.nullableString("display_name"),
            ),
            authorUuid = item.optString("author_uuid"),
            postTitle = titles?.optString(postUuid)?.takeIf(String::isNotBlank),
            deleted = !item.isNull("deleted_at"),
            mediaUrls = media,
        )
    }.orEmpty()
    val pagination = root.optJSONObject("pagination")
    return ProfileState(
        loading = false, user = user,
        history = root.optJSONArray("balanceHistory").objects().map { BalancePoint(it.optDouble("balance"), it.optString("date")) },
        followers = root.optInt("aliasesReceived"), following = root.optInt("aliasesGiven"), totalUpvotes = root.optInt("totalUpvotes"),
        posts = parsePostGroup(root.optJSONObject("recentPosts")), comments = comments,
        votedPosts = parsePostGroup(root.optJSONObject("votedPosts")), pickPosts = parsePostGroup(root.optJSONObject("pickPostsVotes")),
        votes = votes, polls = polls, likerts = likerts, picks = picks, commentVotes = commentVotes,
        hasMorePosts = pagination?.optBoolean("hasMorePosts") == true,
        hasMoreComments = pagination?.optBoolean("hasMoreComments") == true,
        hasMoreVotedPosts = pagination?.optBoolean("hasMoreVotedPosts") == true,
        hasMorePickVotes = pagination?.optBoolean("hasMorePickVotes") == true,
        nextPostCursor = pagination?.nullableString("nextPostCursor"),
        nextCommentCursor = pagination?.nullableString("nextCommentCursor"),
        nextVotedPostCursor = pagination?.nullableString("nextVotedPostCursor"),
        nextPickVoteCursor = pagination?.nullableString("nextPickVoteCursor"),
    )
}

internal fun mergeProfilePage(base: ProfileState, page: ProfileState): ProfileState = base.copy(
    posts = (base.posts + page.posts).distinctBy(FeedPost::uuid),
    comments = (base.comments + page.comments).distinctBy(ProfileComment::uuid),
    votedPosts = (base.votedPosts + page.votedPosts).distinctBy(FeedPost::uuid),
    pickPosts = (base.pickPosts + page.pickPosts).distinctBy(FeedPost::uuid),
    votes = base.votes + page.votes,
    polls = base.polls + page.polls,
    likerts = base.likerts + page.likerts,
    picks = base.picks + page.picks,
    commentVotes = base.commentVotes + page.commentVotes,
    hasMorePosts = page.hasMorePosts,
    hasMoreComments = page.hasMoreComments,
    hasMoreVotedPosts = page.hasMoreVotedPosts,
    hasMorePickVotes = page.hasMorePickVotes,
    nextPostCursor = page.nextPostCursor,
    nextCommentCursor = page.nextCommentCursor,
    nextVotedPostCursor = page.nextVotedPostCursor,
    nextPickVoteCursor = page.nextPickVoteCursor,
    error = null,
)

internal fun joinedAgo(raw: String): String = runCatching {
    val instant = runCatching { Instant.parse(raw) }.getOrElse { OffsetDateTime.parse(raw).toInstant() }
    val days = ((System.currentTimeMillis() - instant.toEpochMilli()) / 86_400_000L).coerceAtLeast(1)
    when { days < 30 -> "${days}d"; days < 365 -> "${days / 30}mo"; else -> "${days / 365}y" }
}.getOrDefault("")
private fun formatCompact(value: Double): String = when { abs(value) >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000).replace(".0M", "M"); abs(value) >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000).replace(".0K", "K"); else -> value.roundToInt().toString() }
internal fun formatNumber(value: Double) = java.text.NumberFormat.getNumberInstance(Locale.US).format(value)
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else buildList { for (index in 0 until length()) optJSONObject(index)?.let(::add) }
private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
private fun JSONObject.nullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)

