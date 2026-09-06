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
import androidx.compose.material.icons.rounded.ChevronRight
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
import com.twocents.mobile.core.text.normalizeBioWhitespace
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
internal fun ProfileUnavailable(blocked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.offset(y = (-22).dp).padding(horizontal = 28.dp).fillMaxWidth().clip(RoundedCornerShape(19.dp))
                .background(Color.White.copy(alpha = .025f)).border(1.dp, Color.White.copy(alpha = .075f), RoundedCornerShape(19.dp))
                .padding(horizontal = 24.dp, vertical = 25.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(ProfileGold.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PersonOff, null, tint = ProfileGold.copy(alpha = .72f), modifier = Modifier.size(20.dp))
            }
            Text(if (blocked) "User blocked" else "Profile unavailable", color = Color.White.copy(alpha = .9f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(if (blocked) "You blocked this user. Unblock them from the profile menu to view their profile." else "This account may no longer exist or is no longer available.", color = Color.White.copy(alpha = .38f), fontSize = 12.5.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun ProfileIdentityCard(state: ProfileState, isOwn: Boolean, isFollowing: Boolean, alias: String?, onToggleFollow: (String?) -> Unit, onGraphGestureActive: (Boolean) -> Unit, onOpenFollowers: (() -> Unit)?, onOpenFollowing: (() -> Unit)?) {
    val user = state.user ?: return
    val view = LocalView.current
    var aliasEntry by remember(user.uuid) { mutableStateOf(false) }
    var aliasText by remember(user.uuid) { mutableStateOf("") }
    var confirmUnfollow by remember(user.uuid) { mutableStateOf(false) }
    LaunchedEffect(user.uuid, ProfileNavigationBus.followPromptUuid, isFollowing) {
        if (!isFollowing && ProfileNavigationBus.followPromptUuid == user.uuid) {
            aliasEntry = true
            ProfileNavigationBus.consumeFollowPrompt(user.uuid)
        }
    }
    Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ProfileSurface).border(1.dp, ProfileBorder, RoundedCornerShape(20.dp)).padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileBalanceChart(state.history, onGraphGestureActive)
        user.bio?.takeIf(String::isNotBlank)?.let { bio ->
            Text(
                normalizeBioWhitespace(bio),
                color = Color.White.copy(alpha = .85f),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                style = NoProfilePadding,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            ProfileStat(ProfileStatKind.Followers, state.followers, "Followers", onOpenFollowers)
            ProfileStatsDivider(afterChevron = onOpenFollowers != null)
            ProfileStat(ProfileStatKind.Following, state.following, "Following", onOpenFollowing)
            ProfileStatsDivider(afterChevron = onOpenFollowing != null)
            ProfileUpvoteStat(state.totalUpvotes)
        }
        ProfileMetaRow(user, alias)
        if (!isOwn) {
            if (aliasEntry && !isFollowing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = aliasText,
                        onValueChange = { aliasText = it.take(30) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White.copy(alpha = .9f), fontSize = 12.5.sp),
                        cursorBrush = SolidColor(ProfileGold),
                        modifier = Modifier.width(190.dp).height(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = .18f))
                            .border(1.dp, ProfileGold.copy(alpha = .3f), CircleShape).padding(horizontal = 13.dp, vertical = 8.dp),
                        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (aliasText.isBlank()) Text("Alias for this person", color = Color.White.copy(alpha = .24f), fontSize = 11.5.sp); inner() } },
                    )
                    Box(Modifier.padding(start = 7.dp).height(38.dp).clip(CircleShape).background(ProfileGold).clickable(enabled = aliasText.isNotBlank()) { AppHaptics.confirm(view); aliasEntry = false; onToggleFollow(aliasText.trim()) }.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                        Text("Follow", color = Color(0xFF17130A), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(Modifier.size(38.dp).clickable { aliasEntry = false; aliasText = "" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Cancel alias", tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(17.dp))
                    }
                }
            } else
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (isFollowing) Color.White.copy(alpha = .06f) else ProfileGold)
                        .border(1.dp, if (isFollowing) Color.White.copy(alpha = .12f) else ProfileGold, CircleShape)
                        .clickable {
                            when {
                                !isFollowing -> { AppHaptics.open(view); aliasEntry = true }
                                !confirmUnfollow -> { AppHaptics.open(view); confirmUnfollow = true }
                                else -> { AppHaptics.confirm(view); confirmUnfollow = false; onToggleFollow(null) }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.PersonAddAlt, null, tint = if (isFollowing) Color.White else Color(0xFF0F0E0A), modifier = Modifier.size(15.dp))
                    Text(if (confirmUnfollow) "Are you sure?" else if (isFollowing) "Following" else "Follow", color = if (isFollowing) Color.White else Color(0xFF0F0E0A), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, style = NoProfilePadding)
                }
                if (state.followsMe) {
                    Text(
                        "Follows you",
                        color = Color.White.copy(alpha = .62f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = .045f))
                            .border(1.dp, Color.White.copy(alpha = .09f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = NoProfilePadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileBalanceChart(history: List<BalancePoint>, onGestureActive: (Boolean) -> Unit) {
    val ordered = remember(history) {
        history.asSequence()
            .filter { it.balance.isFinite() }
            .sortedBy { it.date }
            .toList()
    }
    if (ordered.size < 2) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("Not enough balance history to chart yet.", color = Color.White.copy(alpha = .4f), fontSize = 13.sp) }
        return
    }
    val density = LocalDensity.current
    val balances = ordered.map { it.balance }
    val rawMin = balances.minOrNull() ?: 0.0
    val rawMax = balances.maxOrNull() ?: 0.0
    val span = (rawMax - rawMin).takeIf { it > 0 } ?: maxOf(1.0, abs(rawMax) * .1)
    val min = rawMin - span * .1
    val max = rawMax + span * .1
    var widthPx by remember { mutableIntStateOf(1) }
    var hoverIndex by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Text("NETWORTH", color = Color.White.copy(alpha = .4f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = NoProfilePadding)
        Box(
            Modifier.fillMaxWidth().height(150.dp).onSizeChanged { widthPx = it.width }
                .pointerInput(ordered, widthPx) {
                    fun update(x: Float) {
                        val usable = (widthPx - 28.dp.toPx()).coerceAtLeast(1f)
                        hoverIndex = ((((x - 14.dp.toPx()).coerceIn(0f, usable) / usable) * (ordered.size - 1)).roundToInt()).coerceIn(ordered.indices)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onGestureActive(true)
                        try {
                            down.consume(); update(down.position.x)
                            do {
                                val event = awaitPointerEvent()
                                val active = event.changes.firstOrNull { it.pressed }
                                event.changes.forEach { it.consume() }
                                if (active != null) update(active.position.x)
                            } while (event.changes.any { it.pressed })
                        } finally {
                            hoverIndex = null
                            onGestureActive(false)
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val px = 14.dp.toPx(); val py = 16.dp.toPx(); val chartW = size.width - px * 2; val chartH = size.height - py * 2
                repeat(5) { index ->
                    val y = py + chartH * index / 4f
                    drawLine(
                        Color.White.copy(alpha = if (index == 0 || index == 4) .06f else .035f),
                        Offset(px, y), Offset(size.width - px, y), 1.dp.toPx(),
                        pathEffect = if (index == 0 || index == 4) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx())),
                    )
                }
                val points = ordered.mapIndexed { index, point -> Offset(px + chartW * index / (ordered.size - 1), py + ((max - point.balance) / (max - min)).toFloat() * chartH) }
                val line = buildMonotonePath(points)
                val area = Path().apply { addPath(line); lineTo(points.last().x, size.height); lineTo(points.first().x, size.height); close() }
                drawPath(area, Brush.verticalGradient(listOf(ProfileGold.copy(alpha = .38f), ProfileGold.copy(alpha = .08f), Color.Transparent)))
                drawPath(line, ProfileGold.copy(alpha = .25f), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, Brush.horizontalGradient(listOf(Color(0xFFA3823A), Color(0xFFE8C879), ProfileGold)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val selected = hoverIndex?.let(points::get) ?: points.last()
                if (hoverIndex != null) drawLine(ProfileGold.copy(alpha = .4f), Offset(selected.x, py), Offset(selected.x, size.height - py), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                drawCircle(ProfileGold.copy(alpha = .25f), 7.dp.toPx(), selected)
                drawCircle(Color(0xFFE8C879), 3.5.dp.toPx(), selected)
                drawCircle(Background, 4.25.dp.toPx(), selected, style = Stroke(1.5.dp.toPx()))
            }
            Text("$${formatCompact(rawMax)}", color = Color.White.copy(alpha = .3f), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 2.dp), style = NoProfilePadding)
            Text("$${formatCompact(rawMin)}", color = Color.White.copy(alpha = .3f), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 2.dp), style = NoProfilePadding)
            hoverIndex?.let { index ->
                val point = ordered[index]
                val chartWidth = widthPx.toFloat()
                val px = with(density) { 14.dp.toPx() }
                val chartHeight = with(density) { 150.dp.toPx() }
                val selectedX = px + (chartWidth - px * 2) * index / (ordered.size - 1)
                val py = with(density) { 16.dp.toPx() }
                val selectedY = py + ((max - point.balance) / (max - min)).toFloat() * (chartHeight - py * 2)
                val tooltipWidth = with(density) { 96.dp.toPx() }
                val tooltipX = (selectedX - tooltipWidth / 2).coerceIn(with(density) { 8.dp.toPx() }, chartWidth - tooltipWidth - with(density) { 8.dp.toPx() })
                val tooltipY = if (selectedY < with(density) { 54.dp.toPx() }) selectedY + with(density) { 14.dp.toPx() } else selectedY - with(density) { 54.dp.toPx() }
                Column(Modifier.offset { IntOffset(tooltipX.roundToInt(), tooltipY.roundToInt()) }.width(96.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF141410)).border(1.dp, ProfileGold.copy(alpha = .35f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$${formatNumber(point.balance)}", color = Color(0xFFDAB857), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, style = NoProfilePadding)
                    Text(formatChartDate(point.date), color = Color.White.copy(alpha = .5f), fontSize = 9.5.sp, style = NoProfilePadding)
                }
            }
        }
    }
}

private enum class ProfileStatKind { Followers, Following }
@Composable private fun ProfileStat(kind: ProfileStatKind, value: Int, label: String, onClick: (() -> Unit)? = null) = Row(
    modifier = Modifier.clip(CircleShape).then(if (onClick != null) Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 4.dp) else Modifier),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
) {
    Icon(if (kind == ProfileStatKind.Followers) NotificationIcons.Users else NotificationIcons.UserPlus, null, tint = ProfileGold.copy(alpha = .75f), modifier = Modifier.size(14.dp))
    Text(formatCompact(value.toDouble()), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, style = NoProfilePadding)
    Text(label, color = Color.White.copy(alpha = .45f), fontSize = 10.5.sp, maxLines = 1, softWrap = false, style = NoProfilePadding)
    if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .22f), modifier = Modifier.size(11.dp))
}
@Composable private fun ProfileUpvoteStat(value: Int) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    FeedVoteIcon(true, Modifier.size(12.dp), ProfileGold.copy(alpha = .75f)); Text(value.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, style = NoProfilePadding); Text("Upvotes", color = Color.White.copy(alpha = .45f), fontSize = 10.5.sp, maxLines = 1, softWrap = false, style = NoProfilePadding)
}
@Composable private fun ProfileStatsDivider(afterChevron: Boolean) = Box(
    Modifier
        .padding(
            start = if (afterChevron) 1.dp else 5.dp,
            end = if (afterChevron) 5.dp else 5.dp,
        )
        .width(.5.dp).height(13.dp)
        .background(Color.White.copy(alpha = .11f)),
)
@Composable
private fun ProfileMetaRow(user: ProfileUser, alias: String?) {
    val displayUser = remember(user, alias) { user.toUserDisplay(alias) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        UserMetaPill(
            user = displayUser,
            alias = alias,
            modifier = Modifier,
            compact = true,
            fillWidth = false,
            elo = user.elo,
            joined = joinedAgo(user.createdAt),
            joinedExact = user.createdAt,
        )
    }
}

@Composable
internal fun ProfileTabs(active: ProfileTab, isOwn: Boolean, onChange: (ProfileTab) -> Unit) {
    val view = LocalView.current
    val tabs = if (isOwn) ProfileTab.entries else listOf(ProfileTab.Posts, ProfileTab.Comments)
    val widths = tabs.associateWith {
        when (it) {
            ProfileTab.Posts -> 67.dp
            ProfileTab.Comments -> 98.dp
            ProfileTab.Votes -> 66.dp
        }
    }
    val targetX = 3.dp + tabs.takeWhile { it != active }.sumOf { widths.getValue(it).value.toDouble() }.dp + (3.dp * tabs.indexOf(active).coerceAtLeast(0))
    val indicatorX by animateDpAsState(targetX, animationSpec = androidx.compose.animation.core.spring(dampingRatio = .72f, stiffness = 520f), label = "profile-tab-x")
    val indicatorWidth by animateDpAsState(widths[active] ?: 70.dp, animationSpec = androidx.compose.animation.core.spring(dampingRatio = .72f, stiffness = 520f), label = "profile-tab-width")
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
        Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .035f)).border(1.dp, Color.White.copy(alpha = .08f), CircleShape)) {
            Box(
                Modifier.offset(x = indicatorX, y = 3.dp).width(indicatorWidth).height(30.dp).clip(CircleShape)
                    .background(ProfileGold.copy(alpha = .18f)).border(1.dp, ProfileGold.copy(alpha = .42f), CircleShape),
            )
            Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                tabs.forEach { tab ->
                    val selected = tab == active
                    Box(Modifier.width(widths.getValue(tab)).height(30.dp).clip(CircleShape).clickable { AppHaptics.navigate(view); onChange(tab) }, contentAlignment = Alignment.Center) {
                        Text(tab.label, color = if (selected) ProfileGold else Color.White.copy(alpha = .65f), fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, style = NoProfilePadding)
                    }
                }
            }
        }
    }
}

private fun buildMonotonePath(points: List<Offset>): Path {
    if (points.size < 2) return Path().apply { points.firstOrNull()?.let { moveTo(it.x, it.y) } }
    if (points.size == 2) return Path().apply { moveTo(points[0].x, points[0].y); lineTo(points[1].x, points[1].y) }
    val dx = FloatArray(points.size - 1)
    val slopes = FloatArray(points.size - 1)
    for (i in dx.indices) {
        dx[i] = points[i + 1].x - points[i].x
        slopes[i] = if (dx[i] == 0f) 0f else (points[i + 1].y - points[i].y) / dx[i]
    }
    val tangents = FloatArray(points.size)
    tangents[0] = slopes[0]
    for (i in 1 until points.lastIndex) {
        tangents[i] = if (slopes[i - 1] * slopes[i] <= 0f) 0f else {
            val common = dx[i - 1] + dx[i]
            (3f * common) / ((common + dx[i]) / slopes[i - 1] + (common + dx[i - 1]) / slopes[i])
        }
    }
    tangents[points.lastIndex] = slopes.last()
    return Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.lastIndex) {
            val p0 = points[i]; val p1 = points[i + 1]; val segment = dx[i]
            cubicTo(
                p0.x + segment / 3f, p0.y + tangents[i] * segment / 3f,
                p1.x - segment / 3f, p1.y - tangents[i + 1] * segment / 3f,
                p1.x, p1.y,
            )
        }
    }
}

private fun formatChartDate(raw: String): String = runCatching {
    val instant = runCatching { Instant.parse(raw) }.getOrElse { OffsetDateTime.parse(raw).toInstant() }
    DateTimeFormatter.ofPattern("MMM yyyy", Locale.US).format(instant.atZone(ZoneId.systemDefault()))
}.getOrDefault(raw.take(7))
