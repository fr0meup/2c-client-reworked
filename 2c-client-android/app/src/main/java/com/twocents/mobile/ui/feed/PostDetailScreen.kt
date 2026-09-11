package com.twocents.mobile.ui.feed

import com.twocents.mobile.ui.common.PullToRefreshContainer
import com.twocents.mobile.ui.common.rememberPullToRefreshState

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager

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

/** Coordinates post detail state, comment ordering, targeted scroll, and composer visibility. */
@Composable
internal fun PostDetailScreen(
    seedPost: FeedPost,
    sourceController: FeedController,
    auth: AuthState,
    api: RpcApi,
    onBack: () -> Unit,
    targetCommentUuid: String? = null,
    onOpenPost: ((FeedPost) -> Unit)? = null,
    onQuotePost: ((FeedPost) -> Unit)? = null,
    onOpenMessages: (() -> Unit)? = null,
    onNotificationsFallback: (suspend () -> Unit)? = null,
    navigationEnabled: Boolean = true,
) {
    BackHandler(enabled = navigationEnabled, onBack = onBack)
    val controller = remember(seedPost.uuid, api, auth) { PostDetailController(api, auth, seedPost) }
    val state = controller.state
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var sort by remember { mutableStateOf(CommentSort.Top) }
    var replyTarget by remember { mutableStateOf<PostComment?>(null) }
    var highlightedCommentUuid by remember(targetCommentUuid) { mutableStateOf(targetCommentUuid) }
    var targetCommentHandled by remember(targetCommentUuid) { mutableStateOf(false) }
    val orderedRows = remember(state.commentOrderGeneration, sort) { flattenPostComments(state.comments, sort) }
    val currentComments = remember(state.comments) { state.comments.associateBy(PostComment::uuid) }
    val rows = orderedRows.map { row -> row.copy(comment = currentComments[row.comment.uuid] ?: row.comment) }
    val sourceState = sourceController.state
    val sourcePost = sourceState.posts.firstOrNull { it.uuid == seedPost.uuid }
    val sourceVote = sourceState.postVotes[seedPost.uuid]
    val post = if (sourcePost != null && sourceVote != null && sourceVote != state.postVote) {
        sourcePost
    } else {
        state.post ?: sourcePost ?: seedPost
    }

    LaunchedEffect(controller) { controller.load() }
    LaunchedEffect(state.post) {
        // Comment creation refreshes the post from the server. Propagate that exact
        // snapshot to the originating feed before this detail screen is dismissed.
        state.post?.let(sourceController::reconcilePostSnapshot)
    }
    LaunchedEffect(targetCommentUuid, state.loadingComments, rows) {
        if (targetCommentHandled || state.loadingComments) return@LaunchedEffect
        val target = targetCommentUuid ?: return@LaunchedEffect
        val rowIndex = rows.indexOfFirst { it.comment.uuid == target }
        if (rowIndex < 0) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        val contextOffset = with(density) { 112.dp.roundToPx() }
        listState.animateScrollToItem(rowIndex + 2, scrollOffset = -contextOffset)
        targetCommentHandled = true
        highlightedCommentUuid = target
        kotlinx.coroutines.delay(2_400)
        highlightedCommentUuid = null
    }
    LaunchedEffect(replyTarget?.uuid) {
        val target = replyTarget ?: return@LaunchedEffect
        val rowIndex = rows.indexOfFirst { it.comment.uuid == target.uuid }
        if (rowIndex < 0) return@LaunchedEffect
        // Let the IME begin resizing first, then keep the author pill and as much
        // of the replied-to comment as possible above the composer.
        kotlinx.coroutines.delay(360)
        listState.animateScrollToItem(rowIndex + 2, scrollOffset = -with(density) { 88.dp.roundToPx() })
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Background)
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, size.height - 0.5.dp.toPx()),
                        end = Offset(size.width, size.height - 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.width(32.dp).height(28.dp).clickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(
                "slop",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
            PostOptionsButton(
                post = post,
                authUuid = auth.userUuid,
                controller = sourceController,
                onQuotePost = onQuotePost,
                onDeleted = onBack,
                onOpenMessages = onOpenMessages,
                buttonSize = 32.dp,
                iconSize = 20.dp,
                iconTint = Color.White,
            )
        }

        PullToRefreshContainer(
            state = rememberPullToRefreshState(),
            enabled = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
            onRefresh = {
                val success = controller.refresh()
                if (success) sourceController.refreshResultsInBackground(
                    listOfNotNull(controller.state.post),
                    pollVotes = if (controller.state.pollVote != null) setOf(seedPost.uuid) else emptySet(),
                    likertVotes = if (controller.state.likertVote != null) setOf(seedPost.uuid) else emptySet(),
                )
                com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { onNotificationsFallback?.invoke() }
                success
            },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "detail-post", contentType = "post") {
                    FeedPostCard(
                        post = post,
                        authUuid = auth.userUuid,
                        currentVote = sourceState.postVotes[post.uuid] ?: state.postVote,
                        alias = sourceState.aliases[post.authorUuid] ?: post.author.alias,
                        pollVote = sourceState.pollVotes[post.uuid] ?: state.pollVote,
                        likertVote = sourceState.likertVotes[post.uuid] ?: state.likertVote,
                        pickVote = sourceState.pickVotes[post.uuid] ?: state.pickVote,
                        pollResults = sourceState.pollResults[post.uuid],
                        likertResults = sourceState.likertResults[post.uuid],
                        picksResult = sourceState.picksResults[post.uuid],
                        controller = sourceController,
                        showMenu = false,
                        detailMode = true,
                        onOpenPost = onOpenPost,
                        onQuotePost = onQuotePost,
                        onOpenMessages = onOpenMessages,
                    )
                }
                item(key = "comments-header", contentType = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Comments (${state.comments.size})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        )
                        CommentSortDropdown(value = sort, onChange = { sort = it })
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    Spacer(Modifier.height(8.dp))
                }

                if (state.loadingComments && state.comments.isEmpty()) {
                    items(4, key = { "comment-skeleton-$it" }) { CommentSkeleton() }
                } else if (rows.isEmpty()) {
                    item(key = "comments-empty") {
                        Text(
                            "Be the first to share your twocents",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp, horizontal = 20.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    items(rows, key = { it.comment.uuid }, contentType = { "comment-${it.depth.coerceAtMost(5)}" }) { row ->
                        PostCommentRow(
                            row = row,
                            vote = state.commentVotes[row.comment.uuid] ?: 0,
                            alias = state.aliases[row.comment.authorUuid] ?: row.comment.author.alias,
                            onReply = { if (row.depth < 5) replyTarget = row.comment },
                            onVote = { direction -> com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { controller.toggleCommentVote(row.comment.uuid, direction) } },
                            highlighted = row.comment.uuid == highlightedCommentUuid,
                            ownComment = row.comment.authorUuid == auth.userUuid,
                            onDelete = { com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { controller.deleteComment(row.comment.uuid) } },
                        )
                    }
                }
                // Keep a small scroll target for short threads without leaving a
                // large blank panel above the IME while replying.
                item(key = "comment-bottom-space") { Spacer(Modifier.height(if (replyTarget != null) 44.dp else 8.dp)) }
            }
        }

        CommentComposer(
            controller = controller,
            aliases = state.aliases,
            api = api,
            auth = auth,
            replyTarget = replyTarget,
            onCancelReply = { replyTarget = null },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
