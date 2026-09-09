package com.twocents.mobile.ui.feed
import com.twocents.mobile.ui.common.AppDropdown
import com.twocents.mobile.ui.common.AppLoadState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import coil3.request.ImageRequest
import com.twocents.mobile.ui.settings.InteractionPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun FeedContent(
    controller: FeedController,
    topic: String,
    searchQuery: String,
    advancedFilters: AdvancedSearchFilters? = null,
    advancedHeaderContent: (@Composable () -> Unit)? = null,
    onAdvancedSortChange: (SearchResultSort) -> Unit = {},
    authUuid: String,
    listState: LazyListState,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    chromeScrollConnection: NestedScrollConnection? = null,
    emptyTitle: String = "No posts found",
    emptyMessage: String = "There isn't anything here yet.",
    onOpenPost: ((FeedPost) -> Unit)? = null,
    onQuotePost: ((FeedPost) -> Unit)? = null,
    onOpenMessages: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    val context = LocalContext.current
    val verifiedOnly = VerifiedContentFilterStore.isEnabled(context, authUuid)
    // Filter only the outer post. Embedded quote authors remain visible, and
    // bookmarks intentionally preserve the user's explicitly saved content.
    val visiblePosts = remember(state.posts, verifiedOnly, controller.source) {
        if (!verifiedOnly || controller.source == FeedSource.Bookmarks) state.posts
        else state.posts.filter { it.author.subscriptionType > 0 }
    }

    LaunchedEffect(controller, topic, searchQuery, advancedFilters) {
        if (searchQuery.isNotBlank() && advancedFilters == null) delay(280)
        if (advancedFilters != null) controller.loadAdvanced(advancedFilters)
        else {
            val sourceChanged = controller.load(topic, searchQuery)
            if (sourceChanged) listState.scrollToItem(0)
        }
    }
    LaunchedEffect(controller) { controller.ensureAliases() }

    LaunchedEffect(controller, topic, searchQuery, advancedFilters, listState) {
        snapshotFlow {
            val snapshot = controller.state
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val shouldLoad = snapshot.hasMore && !snapshot.isLoadingMore && snapshot.posts.isNotEmpty() &&
                lastVisible >= snapshot.posts.lastIndex - 4
            (shouldLoad && advancedFilters == null) to snapshot.posts.size
        }
            .distinctUntilChanged()
            .collect { (shouldLoadMore, _) ->
                if (shouldLoadMore) controller.loadMore(topic, searchQuery)
            }
    }

    FeedMediaPrewarmer(posts = visiblePosts, listState = listState)

    when {
        advancedFilters == null && state.isInitialLoading && state.posts.isEmpty() -> Column(modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding)) {
            advancedHeaderContent?.invoke()
            FeedSkeleton(Modifier.weight(1f))
        }
        advancedFilters == null && state.posts.isEmpty() -> Column(modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding)) {
            advancedHeaderContent?.invoke()
            EmptyFeed(error = state.error, emptyTitle = emptyTitle, emptyMessage = emptyMessage, modifier = Modifier.weight(1f))
        }
        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topContentPadding, bottom = bottomContentPadding),
            modifier = modifier
                .fillMaxSize()
                .then(if (chromeScrollConnection != null) Modifier.nestedScroll(chromeScrollConnection) else Modifier),
        ) {
            advancedHeaderContent?.let { header ->
                item(key = "advanced-search-controls", contentType = "advanced-controls") { header() }
            }
            if (advancedFilters != null) {
                item(key = "advanced-results-header", contentType = "advanced-header") {
                    Column {
                        AdvancedResultsHeader(state = state, sort = advancedFilters.sort, onSort = onAdvancedSortChange)
                        Box(Modifier.fillMaxWidth().padding(horizontal = 13.dp).height(1.dp).background(Color.White.copy(alpha = .065f)))
                    }
                }
                if (state.posts.isEmpty()) {
                    item(key = "advanced-empty-state", contentType = "advanced-state") {
                        if (state.advancedSearching) {
                            AdvancedSearchWorking(scanned = state.advancedScanned, matches = state.advancedMatches, modifier = Modifier.fillMaxWidth().height(300.dp))
                        } else {
                            EmptyFeed(error = state.error, emptyTitle = emptyTitle, emptyMessage = emptyMessage, modifier = Modifier.fillMaxWidth().height(280.dp))
                        }
                    }
                }
            }
            if (visiblePosts.isEmpty() && state.posts.isNotEmpty()) {
                item(key = "verified-filter-empty", contentType = "empty") {
                    AppLoadState("No verified posts here", "Turn off Verified accounts only to show the hidden posts.", Modifier.fillMaxWidth().height(260.dp))
                }
            }
            items(
                items = visiblePosts,
                key = { it.uuid },
                contentType = { it.recycleType },
            ) { post ->
                FeedPostCard(
                    post = post,
                    authUuid = authUuid,
                    currentVote = state.postVotes[post.uuid] ?: 0,
                    alias = state.aliases[post.authorUuid] ?: post.author.alias,
                    pollVote = state.pollVotes[post.uuid],
                    likertVote = state.likertVotes[post.uuid],
                    pickVote = state.pickVotes[post.uuid],
                    pollResults = state.pollResults[post.uuid],
                    likertResults = state.likertResults[post.uuid],
                    picksResult = state.picksResults[post.uuid],
                    controller = controller,
                    onOpenPost = onOpenPost,
                    onQuotePost = onQuotePost,
                    onOpenMessages = onOpenMessages,
                    parentScrolling = { listState.isScrollInProgress },
                )
            }
            if (visiblePosts.isEmpty()) {
                item(key = "advanced-scroll-space", contentType = "footer") { Spacer(Modifier.height(80.dp)) }
            } else if (state.isLoadingMore) {
                item(key = "feed-loading-more", contentType = "footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FeedPaginationSpinner()
                    }
                }
            } else if (!state.hasMore) {
                item(key = "feed-end", contentType = "footer") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "You've reached the end",
                            color = Color.White.copy(alpha = 0.34f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                item(key = "feed-bottom-space", contentType = "footer") { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun AdvancedSearchWorking(scanned: Int, matches: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(27.dp), color = Color(0xFFC8A44D), strokeWidth = 2.dp)
        Text("Searching twocents…", color = Color.White.copy(alpha = .62f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text("$scanned scanned · $matches matches", color = Color.White.copy(alpha = .3f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AdvancedResultsHeader(state: FeedUiState, sort: SearchResultSort, onSort: (SearchResultSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.advancedSearching) CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFFC8A44D), strokeWidth = 1.7.dp)
        Text(
            if (state.advancedSearching) "${state.advancedScanned} scanned · ${state.advancedMatches} matches · 20 workers" else "${state.advancedMatches} posts found",
            color = Color.White.copy(alpha = .4f), fontSize = 10.5.sp,
            modifier = Modifier.weight(1f).padding(start = if (state.advancedSearching) 7.dp else 0.dp),
        )
        Box {
            Row(
                Modifier.clickable { open = true }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.5.dp),
            ) {
                Text(sort.label, color = Color.White.copy(alpha = .45f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
                androidx.compose.material3.Icon(Icons.Rounded.KeyboardArrowDown, "Sort results", tint = Color.White.copy(alpha = .45f), modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = if (open) 180f else 0f })
            }
            AppDropdown(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.width(178.dp),
                shape = RoundedCornerShape(14.dp),
                containerColor = Color(0xFF141410),
                tonalElevation = 0.dp,
                shadowElevation = 24.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .1f)),
            ) {
                SearchResultSort.entries.forEach { option ->
                    val selected = option == sort
                    DropdownMenuItem(
                        text = { Text(option.label, color = if (selected) Color(0xFFC8A44D) else Color.White.copy(alpha = .7f), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false) },
                        onClick = { open = false; onSort(option) },
                        trailingIcon = { if (selected) androidx.compose.material3.Icon(Icons.Rounded.Check, null, tint = Color(0xFFC8A44D), modifier = Modifier.size(14.dp)) },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.padding(horizontal = 4.dp).height(35.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) Color(0xFFC8A44D).copy(alpha = .14f) else Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPaginationSpinner() {
    val transition = rememberInfiniteTransition(label = "feed-pagination")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(760, easing = LinearEasing)),
        label = "feed-pagination-rotation",
    )
    Canvas(
        modifier = Modifier
            .size(22.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        drawArc(
            color = Color(0xFFC8A44D),
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun FeedMediaPrewarmer(posts: List<FeedPost>, listState: LazyListState) {
    if (posts.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = remember(context) { context.imageLoader }
    val density = LocalDensity.current
    val targetWidthPx = with(density) { 380.dp.roundToPx() }
    val targetHeightPx = with(density) { 380.dp.roundToPx() }

    // Independently warm only the visible/next few rows, never the entire feed.
    // Post keys avoid offsets introduced by advanced-search header items.
    LaunchedEffect(posts, listState) {
        val indices = posts.withIndex().associate { it.value.uuid to it.index }
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { indices[it.key] } }
            .map { visible ->
                val first = visible.minOrNull() ?: 0
                first..minOf(posts.lastIndex, (visible.maxOrNull() ?: 0) + 3)
            }
            .distinctUntilChanged()
            .collectLatest { range ->
                if (!InteractionPreferences.automaticMediaAllowed(context)) return@collectLatest
                range.flatMap { index ->
                    listOfNotNull(posts[index].meta.videoUrl, posts[index].meta.quotePost?.meta?.videoUrl)
                }.distinct().take(3).forEach { url ->
                    VideoPreviewRepository.prepare(context.applicationContext, url)
                }
            }
    }

    LaunchedEffect(posts, listState, imageLoader, targetWidthPx) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .map { indexes ->
                if (indexes.isEmpty()) 0..minOf(2, posts.lastIndex) else {
                    val first = indexes.minOrNull() ?: 0
                    val last = indexes.maxOrNull() ?: first
                    maxOf(0, first - 2)..minOf(posts.lastIndex, last + 3)
                }
            }
            .distinctUntilChanged()
            .collectLatest { range ->
                if (!InteractionPreferences.automaticMediaAllowed(context)) return@collectLatest
                // Image prefetch stays responsive even if an MP4 metadata probe is slow.
                // Video prefetch runs separately so it never blocks images.
                val urls = range
                    .flatMap { index -> posts[index].warmableMediaUrls() }
                    .distinct()
                    .take(10)
                for (url in urls) {
                    // Await so collectLatest cancels obsolete preloads when the viewport moves.
                    imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(targetWidthPx, targetHeightPx)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .build(),
                    )
                    delay(55)
                }
            }
    }
}

private fun FeedPost.warmableMediaUrls(): List<String> = buildList {
    addAll(meta.images.take(2))
    meta.giphyUrl?.let(::add)
    meta.categoryIconUrl?.let(::add)
    meta.receiptImageUrl?.let(::add)
    meta.quotePost?.meta?.images?.firstOrNull()?.let(::add)
}

@Composable
private fun EmptyFeed(
    error: String?,
    emptyTitle: String,
    emptyMessage: String,
    modifier: Modifier,
) {
    AppLoadState(if (error == null) emptyTitle else "Couldn't load the feed", error ?: emptyMessage, modifier)
}
