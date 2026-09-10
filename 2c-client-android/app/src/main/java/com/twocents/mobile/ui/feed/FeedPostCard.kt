package com.twocents.mobile.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import com.twocents.mobile.ui.common.AppHaptics
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin

private val PostGold = Color(0xFFC8A44D)
private val PostEmerald = Color(0xFF34D399)
private val PostRose = Color(0xFFF43F5E)
private val ActionGray = Color(0xFF8E8B85)

private const val IOS_ICON_URL = "https://www.twocents.money/_next/image?url=%2F_next%2Fstatic%2Fmedia%2Fapple.0xxwgeqy4kw1g.png&w=32&q=75&dpl=dpl_5ovAARAu8zMP9MtrCL9RTcRsDq7b"
private const val ANDROID_ICON_URL = "https://www.twocents.money/_next/image?url=%2F_next%2Fstatic%2Fmedia%2Fandroid.0ujtbb1oilk8l.png&w=32&q=75&dpl=dpl_5ovAARAu8zMP9MtrCL9RTcRsDq7b"

@Composable
internal fun FeedPostCard(
    post: FeedPost,
    authUuid: String,
    currentVote: Int,
    alias: String?,
    pollVote: Int?,
    likertVote: Int?,
    pickVote: String?,
    pollResults: Map<Int, FeedOptionResult>?,
    likertResults: Map<Int, FeedOptionResult>?,
    picksResult: FeedPicksResult?,
    controller: FeedController,
    onOpenPost: ((FeedPost) -> Unit)? = null,
    onQuotePost: ((FeedPost) -> Unit)? = null,
    onOpenMessages: (() -> Unit)? = null,
    onVoteOverride: ((Int) -> Unit)? = null,
    onDeleted: (() -> Unit)? = null,
    showMenu: Boolean = true,
    detailMode: Boolean = false,
    parentScrolling: () -> Boolean = { false },
    authorNavigationEnabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val cardBringIntoView = remember { BringIntoViewRequester() }
    var cardTop by remember(post.uuid) { mutableFloatStateOf(0f) }
    var cardBottom by remember(post.uuid) { mutableFloatStateOf(0f) }
    val content = remember(post.uuid, post.text, post.meta.giphyUrl) { prepareFeedPostContent(post) }
    val tweetUrl = remember(post.uuid, post.text, post.meta.tweetUrl, post.meta.link) {
        val pattern = Regex("https?://(?:www\\.)?(?:x|twitter)\\.com/[^\\s/]+/status/\\d+", RegexOption.IGNORE_CASE)
        post.meta.tweetUrl ?: post.meta.link?.takeIf { pattern.containsMatchIn(it) } ?: pattern.find(post.text)?.value
    }
    // LazyColumn disposes off-screen cards. Saveable state keeps an explicitly
    // expanded post open when it is recycled and brought back into view.
    var expanded by rememberSaveable(post.uuid) { mutableStateOf(false) }
    val textWithoutTweet = remember(content.visibleText, tweetUrl) {
        if (tweetUrl != null) stripEmbeddedTweetLink(content.visibleText, tweetUrl) else content.visibleText
    }
    val shownText = if (!expanded && textWithoutTweet.length > 400) textWithoutTweet.take(400).trimEnd() + "…" else textWithoutTweet
    val isOwn = authUuid == post.authorUuid

    LaunchedEffect(post.uuid, pollVote, likertVote, isOwn, controller.state.resultsRevision) {
        if (post.postType == 2 && (pollVote != null || isOwn)) controller.ensurePollResults(post.uuid)
        if (post.postType == 5 && (likertVote != null || isOwn)) controller.ensureLikertResults(post.uuid)
        if (post.postType == 7) controller.ensurePicksResults(post.uuid)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(cardBringIntoView)
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                cardTop = bounds.top
                cardBottom = bounds.bottom
            }
            .then(
                if (!detailMode && onOpenPost != null) Modifier.postOpenTap(parentScrolling) {
                    AppHaptics.navigate(view)
                    onOpenPost(post)
                } else Modifier,
            ),
    ) {
        Column(modifier = Modifier.padding(start = 13.dp, top = 13.dp, end = 13.dp, bottom = 12.dp)) {
            FeedPostHeader(
                post = post,
                showMenu = showMenu,
                authUuid = authUuid,
                controller = controller,
                onQuotePost = onQuotePost,
                onOpenMessages = onOpenMessages,
                onDeleted = onDeleted,
                authorNavigationEnabled = authorNavigationEnabled,
                expandableTimestamp = detailMode,
            )

            if (post.title.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = post.title.trim(),
                        color = Color.White,
                        fontSize = 16.5.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        // In feed cards, titles use the same author-row gap as an
                        // untitled post body. Detail keeps its established spacing.
                        modifier = if (detailMode) Modifier.padding(top = 8.dp) else Modifier,
                    )
                }
            }
            if (post.postType != 7 && shownText.isNotBlank()) {
                SelectionContainer {
                    FeedPostText(
                        shownText,
                        // The title owns title-to-body spacing. Header-only posts keep the
                        // original tight transition from the author row into their body.
                        modifier = when {
                            post.title.isNotBlank() -> Modifier.padding(top = 6.dp)
                            detailMode -> Modifier.padding(top = 5.dp)
                            else -> Modifier
                        },
                    )
                }
            }
            if (post.postType != 7 && textWithoutTweet.length > 400) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    color = PostGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            if (!expanded) expanded = true else {
                                expanded = false
                                scope.launch {
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    if (cardBottom <= 0f || cardTop < 0f || cardTop >= view.height.toFloat()) {
                                        cardBringIntoView.bringIntoView()
                                    }
                                }
                            }
                        },
                )
            }
            tweetUrl?.let { TweetEmbedCard(it) }

            when (post.postType) {
                2 -> if (post.meta.poll.isNotEmpty()) FeedPollCard(
                    post = post,
                    userVote = pollVote,
                    results = pollResults,
                    isOwner = isOwn,
                    onVote = { option -> com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { controller.votePoll(post.uuid, option) } },
                    ensureResults = { scope.launch { controller.ensurePollResults(post.uuid) } },
                )
                5 -> FeedLikertCard(
                    post = post,
                    userVote = likertVote,
                    results = likertResults,
                    isOwner = isOwn,
                    onVote = { option -> com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { controller.voteLikert(post.uuid, option) } },
                    ensureResults = { scope.launch { controller.ensureLikertResults(post.uuid) } },
                )
                7 -> FeedPicksCard(
                    post = post,
                    userVote = pickVote,
                    result = picksResult,
                    onVote = { vote -> com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { controller.votePick(post.uuid, vote) } },
                    ensureResults = { scope.launch { controller.ensurePicksResults(post.uuid) } },
                )
            }

            val videoUrl = post.meta.videoUrl
            // GIFs share the still-image gallery path so size, clipping, corner
            // treatment and lightbox behavior cannot drift between media types.
            val visualMedia = (post.meta.images + content.gifUrls).distinct()
            if (!videoUrl.isNullOrBlank()) {
                FeedVideoPlayer(videoUrl, handoffOnMount = detailMode)
            } else if (visualMedia.isNotEmpty() && (content.gifUrls.isNotEmpty() || post.postType !in setOf(8, 9))) {
                FeedPostMedia(visualMedia)
            }

            when (post.postType) {
                8 -> FeedTransactionCard(post)
                9 -> FeedBudgetCard(post)
            }
            com.twocents.mobile.ui.common.LinkPreviewCards(post.text, post.meta.link)
            post.meta.quotePost?.let { quote ->
                FeedQuoteCard(quote, onClick = onOpenPost?.let { open -> { open(quote) } })
            }

            FeedPostActions(
                post = post,
                currentVote = currentVote,
                alias = alias,
                onVote = { direction ->
                    onVoteOverride?.invoke(direction)
                        ?: com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { controller.togglePostVote(post.uuid, direction) }
                },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
    }
}


private val EmbeddedTweetMarkdownLink = Regex(
    "\\[https?://[^]]*(?:x|twitter)\\.com[^]]*]\\(https?://[^)]*(?:x|twitter)\\.com[^)]*\\)",
    RegexOption.IGNORE_CASE,
)
private val EmbeddedTweetRawLink = Regex(
    "https?://(?:www\\.)?(?:x|twitter)\\.com/[^\\s/)]+/status/\\d+(?:\\?[^\\s)\\]]*)?",
    RegexOption.IGNORE_CASE,
)

internal fun stripEmbeddedTweetLink(text: String, detectedUrl: String): String = text
    .replace(EmbeddedTweetMarkdownLink, "")
    .replace(EmbeddedTweetRawLink, "")
    .replace(detectedUrl, "")
    .replace(Regex("[ \\t]+\\n"), "\n")
    .trim()

private fun Modifier.postOpenTap(parentScrolling: () -> Boolean, onTap: () -> Unit): Modifier = pointerInput(parentScrolling, onTap) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val scrollingAtDown = parentScrolling()
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
            if (!change.pressed) {
                if (!scrollingAtDown && !moved && !change.isConsumed && change.changedToUpIgnoreConsumed()) onTap()
                break
            }
            if (change.isConsumed) moved = true
        }
    }
}
