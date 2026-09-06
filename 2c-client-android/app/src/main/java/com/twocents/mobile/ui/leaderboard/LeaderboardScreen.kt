package com.twocents.mobile.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.core.text.normalizeBioWhitespace
import androidx.compose.ui.zIndex
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.feed.FeedAuthor
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.FeedPostMeta
import com.twocents.mobile.ui.feed.FeedUserMetaPill
import com.twocents.mobile.ui.feed.UserMetaPill
import com.twocents.mobile.ui.common.toUserDisplay
import com.twocents.mobile.ui.feed.AnimatedDropdownPanel
import com.twocents.mobile.ui.feed.HeaderActionButton
import com.twocents.mobile.ui.feed.PROFILE_ICON_RES
import com.twocents.mobile.ui.feed.PROFILE_ICON_SELECTED_RES
import com.twocents.mobile.ui.feed.ChevronIcon
import com.twocents.mobile.ui.common.PullToRefreshContainer
import com.twocents.mobile.ui.common.RefreshProgressBar
import com.twocents.mobile.ui.common.rememberPullToRefreshState
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.common.HapticIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val Gold = Color(0xFFC8A44D)
private val NoPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

/** Leaderboard coordinator; parsing and board-specific value formatting live outside this screen. */
@Composable
internal fun LeaderboardScreen(
    auth: AuthState,
    api: RpcApi,
    aliases: Map<String, String>,
    onBack: () -> Unit,
    onOpenMe: () -> Unit,
    onOpenProfile: (String, ComposeAuthorProfile) -> Unit,
    profileSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val controller = remember(api, auth) { LeaderboardController(api, auth) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val refresh = rememberPullToRefreshState()
    var dropdownOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller) { controller.load() }
    LaunchedEffect(controller.selected?.apiName) { listState.scrollToItem(0) }

    Box(modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            LeaderboardHeader(
                label = controller.selected?.label ?: "Leaderboards",
                expanded = dropdownOpen,
                onBack = onBack,
                onToggle = { dropdownOpen = !dropdownOpen },
                onOpenMe = onOpenMe,
                profileSelected = profileSelected,
            )

            PullToRefreshContainer(
            state = refresh,
            enabled = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
            onRefresh = { controller.refresh() },
            modifier = Modifier.fillMaxSize(),
            indicatorTopOffset = 3.dp,
            ) {
                when {
                controller.loading && controller.entries.isEmpty() -> LeaderboardSkeleton(Modifier.fillMaxSize())
                controller.error != null && controller.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(controller.error.orEmpty(), color = Color.White.copy(alpha = .4f), fontSize = 13.sp, style = NoPadding)
                        Text("Tap to retry", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { scope.launch { controller.load(true) } }, style = NoPadding)
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 7.dp, top = 12.dp, end = 7.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val board = controller.selected
                    if (board?.apiName == "top100" && controller.myPosition > 0) {
                        item("my-rank") { MyRankCard(controller.myPosition, controller.totalPositions) }
                    }
                    if (controller.entries.isNotEmpty()) {
                        if (board?.apiName != "league") item("podium-${board?.apiName}") {
                            Podium(controller.entries.take(3), board, aliases, onOpenProfile)
                        }
                        val visibleEntries = if (board?.apiName == "league") controller.entries else controller.entries.drop(3)
                        itemsIndexed(
                            visibleEntries,
                            key = { index, entry -> "${board?.apiName}-${entry.uuid}-$index" },
                            contentType = { _, _ -> "leaderboard-row" },
                        ) { index, entry ->
                            val rank = if (board?.apiName == "league") null else entry.apiRank ?: index + 4
                            LeaderboardRow(entry, rank, board, aliases[entry.uuid], auth.userUuid, onOpenProfile)
                        }
                    } else if (!controller.loading) {
                        item("empty") { Box(Modifier.fillParentMaxHeight(.7f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No entries yet", color = Color.White.copy(alpha = .38f), fontSize = 13.sp) } }
                    }
                    if (controller.loading && controller.entries.isNotEmpty()) {
                        item("loading") { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp) } }
                    }
                }
                }
            }
        }
        RefreshProgressBar(
            active = refresh.isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 56.dp).zIndex(8f),
        )
        LeaderboardDropdownOverlay(
            open = dropdownOpen,
            boards = controller.boards,
            selected = controller.selected,
            onDismiss = { dropdownOpen = false },
            onSelect = { board ->
                dropdownOpen = false
                scope.launch { controller.select(board) }
            },
        )
    }
}

@Composable
private fun LeaderboardHeader(
    label: String,
    expanded: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onOpenMe: () -> Unit,
    profileSelected: Boolean,
) {
    Box(
        Modifier.fillMaxWidth().height(57.dp).background(Background)
            .drawBehind {
                drawLine(
                    color = if (expanded) Background else Color.White.copy(alpha = .06f),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 1f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 12.dp),
    ) {
        val backInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier.align(Alignment.CenterStart).size(48.dp)
                .clickable(interactionSource = backInteraction, indication = null, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f))
                    .border(1.dp, Color.White.copy(alpha = .12f), CircleShape)
                    .pressScale(rememberPressScale(backInteraction)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(19.dp))
            }
        }

        Box(Modifier.align(Alignment.Center)) {
            val selectorInteraction = remember { MutableInteractionSource() }
            Row(
                Modifier.widthIn(min = 112.dp).height(48.dp).clip(RoundedCornerShape(12.dp))
                    .pressScale(rememberPressScale(selectorInteraction))
                    .clickable(interactionSource = selectorInteraction, indication = null, onClick = onToggle)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                Text(label, color = Color.White, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = NoPadding)
                ChevronIcon(expanded = expanded, tint = if (expanded) Gold else Color.White.copy(alpha = .65f))
            }
        }

        Box(Modifier.align(Alignment.CenterEnd).offset(x = 9.dp)) {
            HeaderActionButton(
                imageRes = PROFILE_ICON_RES,
                pressedImageRes = PROFILE_ICON_SELECTED_RES,
                iconSize = 29.dp,
                fallback = Icons.Default.Person,
                contentDescription = "Profile",
                selected = profileSelected,
                hapticIntent = HapticIntent.Open,
                onClick = onOpenMe,
            )
        }
    }
}

@Composable
private fun LeaderboardDropdownOverlay(
    open: Boolean,
    boards: List<LeaderboardBoard>,
    selected: LeaderboardBoard?,
    onDismiss: () -> Unit,
    onSelect: (LeaderboardBoard) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(
            durationMillis = 380,
            easing = if (open) CubicBezierEasing(.42f, 0f, .58f, 1f) else CubicBezierEasing(.64f, 0f, .78f, 1f),
        ),
        label = "leaderboard-dropdown-progress",
    )
    val backdropAlpha by animateFloatAsState(
        targetValue = if (open) .65f else 0f,
        animationSpec = tween(240),
        label = "leaderboard-dropdown-backdrop",
    )
    if (!open && progress <= 0f) return

    Box(Modifier.fillMaxSize().zIndex(10f).padding(top = 57.dp)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = backdropAlpha))
                .clickable(onClick = onDismiss),
        )
        AnimatedDropdownPanel(progress = progress, modifier = Modifier.align(Alignment.TopCenter)) {
            val shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            Column(
                Modifier.fillMaxWidth().heightIn(max = configuration.screenHeightDp.dp * .72f)
                    .shadow(20.dp, shape, clip = false).clip(shape).background(Background)
                    .drawBehind {
                        val line = Color.White.copy(alpha = .08f)
                        drawLine(line, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(0f, size.height - 20.dp.toPx()), 1f)
                        drawLine(line, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height - 20.dp.toPx()), 1f)
                        drawLine(line, androidx.compose.ui.geometry.Offset(20.dp.toPx(), size.height - 1f), androidx.compose.ui.geometry.Offset(size.width - 20.dp.toPx(), size.height - 1f), 1f)
                    }
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    "LEADERBOARDS",
                    color = Color.White.copy(alpha = .38f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .8.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
                boards.forEach { board ->
                    val active = board == selected
                    Text(
                        board.label,
                        color = if (active) Gold else Color.White.copy(alpha = .8f),
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (active) Gold.copy(alpha = .12f) else Color.Transparent)
                            .clickable { onSelect(board) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun MyRankCard(position: Int, total: Int) {
    val percentile = if (total > 0) (position.toDouble() / total * 100.0) else null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(Brush.verticalGradient(listOf(Gold.copy(alpha = .075f), Gold.copy(alpha = .018f))))
            .border(1.dp, Gold.copy(alpha = .2f), RoundedCornerShape(17.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Gold.copy(alpha = .12f)).border(1.dp, Gold.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) {
            Text("#", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Black, style = NoPadding)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("YOUR RANK", color = Gold.copy(alpha = .72f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp, style = NoPadding)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("#${formatInt(position)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = NoPadding)
                if (total > 0) Text("of ${formatInt(total)}", color = Color.White.copy(alpha = .4f), fontSize = 11.5.sp, style = NoPadding)
            }
        }
        percentile?.let {
            val label = when { it < .1 -> "Top 0.1%"; it < 10 -> "Top ${String.format(Locale.US, "%.1f", it)}%"; else -> "Top ${it.roundToLong()}%" }
            Text(label, color = if (position <= 100) Gold else Color.White.copy(alpha = .68f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(if (position <= 100) Gold.copy(alpha = .12f) else Color.White.copy(alpha = .045f)).border(1.dp, if (position <= 100) Gold.copy(alpha = .28f) else Color.White.copy(alpha = .08f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), style = NoPadding)
        }
    }
}

@Composable
private fun Podium(entries: List<LeaderboardEntry>, board: LeaderboardBoard?, aliases: Map<String, String>, onOpenProfile: (String, ComposeAuthorProfile) -> Unit) {
    if (entries.isEmpty()) return
    val ordered = listOfNotNull(entries.getOrNull(1)?.let { 2 to it }, entries.getOrNull(0)?.let { 1 to it }, entries.getOrNull(2)?.let { 3 to it })
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF141410), Color(0xFF0A0907))))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp)).padding(start = 6.dp, top = 17.dp, end = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ordered.forEach { (rank, entry) ->
            PodiumSlot(entry, rank, board, aliases[entry.uuid], Modifier.weight(1f), onOpenProfile)
        }
    }
}

@Composable
private fun PodiumSlot(entry: LeaderboardEntry, rank: Int, board: LeaderboardBoard?, alias: String?, modifier: Modifier, onOpenProfile: (String, ComposeAuthorProfile) -> Unit) {
    val color = when (rank) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFC0C0C0); else -> Color(0xFFCD7F32) }
    val pedestal = when (rank) { 1 -> 94.dp; 2 -> 70.dp; else -> 52.dp }
    Column(modifier.clickable { onOpenProfile(entry.uuid, entry.profile()) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(25.dp).clip(CircleShape).background(color).padding(1.dp), contentAlignment = Alignment.Center) {
            Text(rank.toString(), color = Color(0xFF0F0E0A), fontSize = 10.5.sp, fontWeight = FontWeight.Black, style = NoPadding)
        }
        Spacer(Modifier.height(6.dp))
        ComposeNetworthPill(
            entry.profile(), entry.uuid,
            modifier = Modifier.graphicsLayer { scaleX = .9f; scaleY = .9f },
            compact = true,
        )
        if (board?.hasExtra == true && entry.points != null) {
            Text(
                "${formatExtra(entry.pointsFor(board), board)} ${board.extraLabel.lowercase()}",
                color = Color.White.copy(alpha = .78f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, style = NoPadding, modifier = Modifier.padding(top = 4.dp),
            )
        }
        UserMetaPill(
            entry.toUserDisplay(alias), alias,
            Modifier.padding(top = 4.dp).widthIn(max = 104.dp).graphicsLayer { scaleX = .88f; scaleY = .88f },
            compact = true, fillWidth = false, elo = null,
        )
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier.fillMaxWidth().height(pedestal).clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp))
                .background(Brush.verticalGradient(listOf(color.copy(alpha = .13f), color.copy(alpha = .035f))))
                .drawWithContent {
                    drawContent()
                    val radius = 11.dp.toPx()
                    val border = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(0f, radius)
                        quadraticBezierTo(0f, 0f, radius, 0f)
                        lineTo(size.width - radius, 0f)
                        quadraticBezierTo(size.width, 0f, size.width, radius)
                        lineTo(size.width, size.height)
                    }
                    drawPath(border, color.copy(alpha = .22f), style = Stroke(1.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(rank.toString(), color = color.copy(alpha = .08f), fontSize = 36.sp, fontWeight = FontWeight.Black, style = NoPadding)
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, rank: Int?, board: LeaderboardBoard?, alias: String?, authUuid: String, onOpenProfile: (String, ComposeAuthorProfile) -> Unit) {
    val own = entry.uuid.equals(authUuid, ignoreCase = true)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (own) .05f else .032f), Color.White.copy(alpha = .012f))))
            .border(1.dp, if (own) Gold.copy(alpha = .38f) else Color.White.copy(alpha = .065f), RoundedCornerShape(17.dp))
            .clickable { onOpenProfile(entry.uuid, entry.profile()) }.padding(horizontal = 9.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        rank?.let {
            Text(it.toString(), color = Color.White.copy(alpha = .4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp), style = NoPadding)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                ComposeNetworthPill(
                    entry.profile(), entry.uuid,
                    modifier = Modifier.graphicsLayer { scaleX = .9f; scaleY = .9f },
                    compact = true,
                )
                UserMetaPill(
                    entry.toUserDisplay(alias), alias,
                    Modifier.weight(1f, fill = false).widthIn(max = 210.dp).graphicsLayer { scaleX = .9f; scaleY = .9f },
                    compact = true, fillWidth = false, elo = null,
                )
            }
            entry.bio?.takeIf(String::isNotBlank)?.let {
                Text(normalizeBioWhitespace(it).replace('\n', ' '), color = Color.White.copy(alpha = .43f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp), style = NoPadding)
            }
        }
        if (board?.hasExtra == true && entry.points != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(board.extraLabel.uppercase(), color = Color.White.copy(alpha = .3f), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, style = NoPadding)
                Text(formatExtra(entry.pointsFor(board), board), color = Color.White.copy(alpha = .88f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, style = NoPadding)
            }
        }
    }
}

@Composable
private fun LeaderboardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .04f)))
        Box(Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .035f)))
        repeat(5) { Box(Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .03f))) }
    }
}
