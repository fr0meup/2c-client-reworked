package com.twocents.mobile.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twocents.mobile.ui.feed.FeedPostCard
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.FlatPostComment
import com.twocents.mobile.ui.feed.PostCommentRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val Gold = Color(0xFFC8A44D)
private val Surface = Color(0xFF141410)

/** Native ticker destination; hidden behind a pushed post without losing list or chart state. */
@Composable
internal fun TickerSheet(
    symbol: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenPost: (FeedPost?, FeedController?, String, String?) -> Unit,
) {
    val data = LocalTickerData.current
    val context = LocalContext.current
    val motion = remember(symbol) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var drag by remember(symbol) { mutableFloatStateOf(0f) }
    var closing by remember(symbol) { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 65.dp.toPx() }
    val listState = rememberLazyListState()
    val feed = remember(data, symbol) { data?.let { TickerFeed(it, it.feedController(context)) } }
    var period by remember(symbol) { mutableStateOf(TickerPeriod.Day) }
    var chartRetry by remember(symbol) { mutableIntStateOf(0) }
    var chart by remember(symbol, period) { mutableStateOf(emptyList<TickerPoint>()) }
    var chartLoading by remember(symbol, period) { mutableStateOf(true) }
    var chartError by remember(symbol, period) { mutableStateOf<String?>(null) }
    var details by remember(symbol) { mutableStateOf<TickerDetails?>(null) }
    var price by remember(symbol) { mutableStateOf<TickerPrice?>(null) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch { motion.animateTo(0f, tween(220)); onDismiss() }
    }
    fun openPost(postUuid: String, commentUuid: String? = null) {
        val knownPost = feed?.posts?.state?.posts?.firstOrNull { it.uuid == postUuid }
        onOpenPost(knownPost, feed?.posts, postUuid, commentUuid)
    }

    LaunchedEffect(symbol) { motion.animateTo(1f, tween(220)) }
    LaunchedEffect(data, symbol) {
        if (data == null) return@LaunchedEffect
        launch { try { details = data.details(symbol) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Price and feed remain usable. */ } }
        launch { try { price = data.price(symbol) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Chart can still show historic prices. */ } }
    }
    LaunchedEffect(data, symbol, period, chartRetry) {
        if (data == null) return@LaunchedEffect
        chartLoading = true
        chartError = null
        try { chart = data.chart(symbol, period) }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { chartError = friendlyError(failure, "Chart unavailable") }
        finally { chartLoading = false }
    }
    LaunchedEffect(feed, symbol) { feed?.select(symbol, TickerTab.Posts, TickerSort.Top) }
    LaunchedEffect(feed) { feed?.posts?.ensureAliases() }
    LaunchedEffect(feed, listState) {
        if (feed == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged().collect { (last, total) ->
                if (last != null && last >= total - 3) feed.loadMore(symbol)
            }
    }

    // Keeping the composable alive retains LazyListState and its loaded pages;
    // only the dialog window is removed while a nested destination is on top.
    if (!visible) return
    Dialog(onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        EdgeToEdgeDialogWindow(navigationBarColor = android.graphics.Color.TRANSPARENT)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f * motion.value))
            .clickable(onClick = ::close)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.94f)
                .graphicsLayer { translationY = (1f - motion.value) * (size.height + 40.dp.toPx()) + drag }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Surface)
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clickable(onClick = {})) {
                Box(Modifier.fillMaxWidth().height(19.dp).appBottomSheetDragHandle(
                    onDrag = { drag = (drag + it).coerceAtLeast(0f) },
                    onDragEnd = { if (drag > threshold) close() else drag = 0f },
                    onDragCancel = { drag = 0f },
                ), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(38.dp).height(4.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = .2f)))
                }
                TickerCompanyHeader(symbol, details, onClose = ::close)
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 10.dp)) {
                    item(key = "overview") {
                        TickerOverview(symbol, details, price, chart, period, chartLoading, chartError,
                            onPeriod = { period = it }, onRetryChart = { chartRetry++ })
                    }
                    item(key = "filters") {
                        if (feed != null) TickerFeedFilters(feed,
                            onTab = { next -> scope.launch { listState.scrollToItem(1); feed.select(symbol, next, feed.sort) } },
                            onSort = { next -> scope.launch { listState.scrollToItem(1); feed.select(symbol, feed.tab, next) } })
                    }
                    if (feed?.tab == TickerTab.Posts) {
                        val posts = feed.posts.state.posts
                        items(posts, key = { "post:${it.uuid}" }) { post ->
                            FeedPostCard(
                                post = post, authUuid = data?.authUuid.orEmpty(),
                                currentVote = feed.posts.state.postVotes[post.uuid] ?: 0,
                                alias = feed.posts.state.aliases[post.authorUuid] ?: post.author.alias,
                                pollVote = feed.posts.state.pollVotes[post.uuid],
                                likertVote = feed.posts.state.likertVotes[post.uuid],
                                pickVote = feed.posts.state.pickVotes[post.uuid],
                                pollResults = feed.posts.state.pollResults[post.uuid],
                                likertResults = feed.posts.state.likertResults[post.uuid],
                                picksResult = feed.posts.state.picksResults[post.uuid],
                                controller = feed.posts,
                                onOpenPost = { openPost(it.uuid) },
                                onDeleted = { feed.posts.state = feed.posts.state.copy(posts = feed.posts.state.posts.filterNot { row -> row.uuid == post.uuid }) },
                                parentScrolling = { listState.isScrollInProgress },
                            )
                        }
                    } else if (feed != null) {
                        items(feed.comments, key = { "comment:${it.uuid}" }) { comment ->
                            Column {
                            val title = feed.postTitles[comment.postUuid].orEmpty()
                            val topic = feed.postTopics[comment.postUuid].orEmpty()
                            if (title.isNotBlank() || topic.isNotBlank()) {
                                Text(if (title.isNotBlank()) title else "\$${topic}",
                                    modifier = Modifier.fillMaxWidth().clickable { openPost(comment.postUuid) }
                                        .padding(start = 13.dp, end = 13.dp, top = 11.dp),
                                    color = Gold.copy(alpha = .78f), fontSize = 11.sp, maxLines = 1)
                            }
                            PostCommentRow(
                                row = FlatPostComment(comment, 0, true, emptyList()),
                                vote = feed.commentVotes[comment.uuid] ?: 0,
                                alias = feed.posts.state.aliases[comment.authorUuid] ?: comment.author.alias,
                                onReply = { openPost(comment.postUuid, comment.uuid) },
                                onVote = { direction -> scope.launch { feed.toggleCommentVote(comment, direction) } },
                                highlighted = false,
                                ownComment = comment.authorUuid == data?.authUuid,
                                onDelete = { scope.launch { feed.deleteComment(comment) } },
                            )
                            }
                        }
                    }
                    item(key = "status") {
                        TickerFeedStatus(feed, onRetry = { if (feed != null) scope.launch {
                            if (feed.hasMore) feed.loadMore(symbol) else feed.select(symbol, feed.tab, feed.sort)
                        } })
                    }
                }
            }
        }
    }
}

@Composable
private fun TickerFeedStatus(feed: TickerFeed?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 21.dp), contentAlignment = Alignment.Center) {
        when {
            feed == null -> Text("Sign in to see ticker activity", color = Color.White.copy(alpha = .48f), fontSize = 12.sp)
            feed.loading -> CircularProgressIndicator(Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
            feed.error != null -> Text("${feed.error} · Tap to retry", Modifier.clickable(onClick = onRetry),
                color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
            feed.tab == TickerTab.Posts && feed.posts.state.posts.isEmpty() -> Text("No posts mention this ticker yet", color = Color.White.copy(alpha = .48f), fontSize = 12.sp)
            feed.tab == TickerTab.Replies && feed.comments.isEmpty() -> Text("No replies mention this ticker yet", color = Color.White.copy(alpha = .48f), fontSize = 12.sp)
        }
    }
}
