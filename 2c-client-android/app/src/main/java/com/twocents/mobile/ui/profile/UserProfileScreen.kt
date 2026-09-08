package com.twocents.mobile.ui.profile

import com.twocents.mobile.ui.common.PullToRefreshContainer
import com.twocents.mobile.ui.common.PullToRefreshState

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
import com.twocents.mobile.ui.settings.InteractionPreferences
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

/** Coordinates profile tabs and paging while feature components own their layouts. */
@Composable
fun UserProfileContent(
    auth: AuthState,
    api: RpcApi,
    targetUuid: String,
    scrollToTopRequest: Int,
    refreshState: PullToRefreshState,
    feedController: FeedController,
    onProfileLoaded: (ComposeAuthorProfile) -> Unit,
    onOpenPost: (FeedPost) -> Unit,
    onOpenCommentPost: (String, String) -> Unit,
    onOpenProfile: (String, ComposeAuthorProfile) -> Unit,
    onQuotePost: (FeedPost) -> Unit,
    onOpenMessages: () -> Unit,
    onNotificationsFallback: suspend () -> Unit,
    navigationActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val controller = remember(api, auth, targetUuid) { ProfileController(api, auth, targetUuid) }
    val state = controller.state
    val feedState = feedController.state
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var tab by remember(targetUuid) { mutableStateOf(ProfileTab.Posts) }
    var graphGestureActive by remember(targetUuid) { mutableStateOf(false) }
    var followingOpen by remember(targetUuid) { mutableStateOf(false) }
    var followersOpen by remember(targetUuid) { mutableStateOf(false) }
    var peopleListIndex by remember(targetUuid) { mutableIntStateOf(0) }
    var peopleListOffset by remember(targetUuid) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val density = LocalDensity.current
    LaunchedEffect(controller) { controller.load() }
    LaunchedEffect(feedController) { feedController.ensureAliases() }
    LaunchedEffect(targetUuid, scrollToTopRequest) { listState.scrollToItem(0) }
    LaunchedEffect(listState, tab, state.posts.size, state.comments.size, state.votedPosts.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { last ->
                val count = when (tab) {
                    ProfileTab.Posts -> state.posts.size
                    ProfileTab.Comments -> state.comments.size
                    ProfileTab.Votes -> state.votedPosts.size
                }
                last >= count.coerceAtLeast(1) - 2 && controller.hasMore(tab)
            }
            .distinctUntilChanged()
            .collect { shouldLoad -> if (shouldLoad) controller.loadMore(tab) }
    }
    LaunchedEffect(tab, state.posts, state.comments, state.votedPosts) {
        if (!InteractionPreferences.automaticMediaAllowed(context)) return@LaunchedEffect
        val size = with(density) { 380.dp.roundToPx() }
        val urls = when (tab) {
            ProfileTab.Posts -> state.posts.flatMap(FeedPost::profileWarmableMediaUrls)
            ProfileTab.Comments -> state.comments.flatMap { it.mediaUrls }
            ProfileTab.Votes -> state.votedPosts.flatMap(FeedPost::profileWarmableMediaUrls)
        }.distinct().take(6)
        // Queue the active tab's first screen immediately. Coil still owns request
        // coalescing and both cache layers, so this removes reveal latency without
        // duplicating downloads or retaining decoded bitmaps in profile state.
        urls.forEach { url ->
            context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).size(size, size)
                    .memoryCacheKey(url).diskCacheKey(url).build(),
            )
            delay(12)
        }
    }
    LaunchedEffect(tab, state.posts, state.votedPosts) {
        if (!InteractionPreferences.automaticMediaAllowed(context)) return@LaunchedEffect
        val posts = if (tab == ProfileTab.Votes) state.votedPosts else if (tab == ProfileTab.Posts) state.posts else emptyList()
        posts.mapNotNull { it.meta.videoUrl }.distinct().take(4).forEach { url ->
            VideoPreviewRepository.prepare(context.applicationContext, url)
            delay(15)
        }
    }
    LaunchedEffect(state.user) {
        state.user?.let { user ->
            onProfileLoaded(
                ComposeAuthorProfile(
                    uuid = user.uuid,
                    balance = user.balance,
                    subscriptionType = user.subscriptionType,
                    role = user.role,
                    gender = user.gender,
                    age = user.age,
                    arena = user.arena,
                ),
            )
        }
    }

    if (state.loading && state.user == null) {
        ProfileSkeleton(modifier)
        return
    }
    if (state.user == null) {
        ProfileUnavailable(ProfileAvailability.blockedByMe[targetUuid] == true, modifier)
        return
    }

    PullToRefreshContainer(
        state = refreshState,
        enabled = !graphGestureActive && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
        onRefresh = { val ok = controller.load(force = true); onNotificationsFallback(); ok },
        modifier = modifier.fillMaxSize(),
        indicatorTopOffset = 4.dp,
    ) {
        LazyColumn(state = listState, userScrollEnabled = !graphGestureActive, modifier = Modifier.fillMaxSize().background(Background), contentPadding = PaddingValues(bottom = 18.dp)) {
            item("profile-identity") {
                ProfileIdentityCard(
                    state = state,
                    isOwn = targetUuid == auth.userUuid,
                    isFollowing = targetUuid in feedController.state.aliases,
                    alias = feedController.state.aliases[targetUuid],
                    onToggleFollow = { aliasValue -> scope.launch { feedController.toggleFollowing(targetUuid, aliasValue) } },
                    onGraphGestureActive = { graphGestureActive = it },
                    onOpenFollowers = if (targetUuid == auth.userUuid) ({ followersOpen = true }) else null,
                    onOpenFollowing = if (targetUuid == auth.userUuid) ({ followingOpen = true }) else null,
                )
            }
            item("profile-tabs") { ProfileTabs(tab, targetUuid == auth.userUuid) { next -> tab = next } }
            val posts = when (tab) { ProfileTab.Posts -> state.posts; ProfileTab.Votes -> state.votedPosts; ProfileTab.Comments -> emptyList() }
            if (tab == ProfileTab.Comments) {
                if (state.comments.isEmpty()) item("profile-empty-comments") { ProfileEmpty("No comments yet") }
                else items(state.comments, key = { it.uuid }, contentType = { "profile-comment" }) { comment ->
                    ProfileCommentCard(
                        comment = comment,
                        currentVote = state.commentVotes[comment.uuid] ?: 0,
                        alias = feedController.state.aliases[comment.authorUuid] ?: comment.author.alias,
                        onOpenPost = onOpenCommentPost,
                        onVote = { direction -> scope.launch { controller.toggleCommentVote(comment, direction) } },
                    )
                }
            } else if (posts.isEmpty()) {
                item("profile-empty-${tab.name}") { ProfileEmpty("No ${tab.label.lowercase()} yet") }
            } else {
                items(posts, key = { it.uuid }, contentType = { it.recycleType }) { post ->
                    FeedPostCard(
                        post = post,
                        authorNavigationEnabled = post.authorUuid != targetUuid,
                        authUuid = auth.userUuid,
                        currentVote = state.votes[post.uuid] ?: 0,
                        alias = feedController.state.aliases[post.authorUuid] ?: post.author.alias,
                        pollVote = state.polls[post.uuid],
                        likertVote = state.likerts[post.uuid],
                        pickVote = state.picks[post.uuid],
                        pollResults = feedState.pollResults[post.uuid],
                        likertResults = feedState.likertResults[post.uuid],
                        picksResult = feedState.picksResults[post.uuid],
                        controller = feedController,
                        onOpenPost = onOpenPost,
                        onQuotePost = onQuotePost,
                        onOpenMessages = onOpenMessages,
                        onVoteOverride = { direction -> scope.launch { controller.togglePostVote(post, direction) } },
                    )
                }
            }
            val activeCount = when (tab) {
                ProfileTab.Posts -> state.posts.size
                ProfileTab.Comments -> state.comments.size
                ProfileTab.Votes -> state.votedPosts.size
            }
            if (activeCount > 0 && state.isLoadingMore) {
                item("profile-loading-${tab.name}") {
                    Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = ProfileGold, strokeWidth = 2.dp)
                    }
                }
            } else if (activeCount > 0 && !controller.hasMore(tab)) {
                item("profile-end-${tab.name}") {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text("You've reached the end", color = Color.White.copy(alpha = .34f), fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
    if (followingOpen) {
        FollowingSheet(
            visible = navigationActive,
            auth = auth,
            api = api,
            onDismiss = { followingOpen = false },
            initialListIndex = peopleListIndex,
            initialListOffset = peopleListOffset,
            onOpenProfile = { uuid, profile, index, offset ->
                peopleListIndex = index
                peopleListOffset = offset
                onOpenProfile(uuid, profile)
            },
        )
    }
    if (followersOpen) {
        FollowersSheet(
            visible = navigationActive,
            auth = auth,
            api = api,
            onDismiss = { followersOpen = false },
            initialListIndex = peopleListIndex,
            initialListOffset = peopleListOffset,
            onOpenProfile = { uuid, profile, index, offset ->
                peopleListIndex = index
                peopleListOffset = offset
                onOpenProfile(uuid, profile)
            },
        )
    }
}

private fun FeedPost.profileWarmableMediaUrls(): List<String> = buildList {
    addAll(meta.images.take(2))
    meta.giphyUrl?.let(::add)
    meta.categoryIconUrl?.let(::add)
    meta.receiptImageUrl?.let(::add)
    meta.quotePost?.meta?.images?.firstOrNull()?.let(::add)
    meta.quotePost?.meta?.giphyUrl?.let(::add)
}
