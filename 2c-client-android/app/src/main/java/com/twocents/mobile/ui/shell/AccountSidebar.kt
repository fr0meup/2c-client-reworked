package com.twocents.mobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.notifications.NotificationIcons
import com.twocents.mobile.notifications.LocalActivitySummary
import com.twocents.mobile.notifications.NotificationHistoryStore
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.feed.*
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.messages.RoomSummary
import com.twocents.mobile.ui.messages.RoomMember
import com.twocents.mobile.ui.messages.otherMember
import com.twocents.mobile.ui.messages.OfflineModeStore
import com.twocents.mobile.ui.profile.FollowingSheet
import com.twocents.mobile.ui.profile.FollowersSheet
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.toUserDisplay
import com.twocents.mobile.kotlin.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val AccountSidebarRevealWidth = 48.dp
private val SidebarGold = Color(0xFFC8A44D)
private val SidebarShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
private val NoFontPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
/** Account rail composition; loading, persistence, and card layouts are separate collaborators. */
@Composable
internal fun AccountSidebar(
    modifier: Modifier = Modifier,
    progress: Float,
    navigationActive: Boolean,
    profile: ComposeAuthorProfile?,
    auth: AuthState,
    api: RpcApi,
    alias: String?,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit,
    onDragFractionChange: (Float) -> Unit,
    notificationRevision: Int,
    recentRooms: List<RoomSummary>,
    onOpenActivity: (String, String?) -> Unit,
    onOpenRoom: (RoomSummary) -> Unit,
    onOpenRoomsOverview: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenSettings: () -> Unit,
    onOfflineModeChanged: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    var followingOpen by remember(auth.userUuid) { mutableStateOf(false) }
    var followersOpen by remember(auth.userUuid) { mutableStateOf(false) }
    // Keep suspended people sheets alive after closing their source sidebar.
    if (progress <= .001f && !followingOpen && !followersOpen) return
    val context = LocalContext.current
    val view = LocalView.current
    val cached = remember(auth.userUuid) { SidebarDisplayCache.read(context, auth.userUuid) }
    var followers by remember(auth.userUuid) { mutableIntStateOf(cached.followers) }
    var following by remember(auth.userUuid) { mutableIntStateOf(cached.following) }
    var elo by remember(auth.userUuid) { mutableIntStateOf(cached.elo) }
    var joined by remember(auth.userUuid) { mutableStateOf(cached.joined) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var activity by remember(auth.userUuid) { mutableStateOf(cached.activity) }
    val historyStore = remember(auth.userUuid) { NotificationHistoryStore(context, auth.userUuid) }
    var offlineMode by remember(auth.userUuid) { mutableStateOf(OfflineModeStore.isEnabled(context, auth.userUuid)) }
    var verifiedOnly by remember(auth.userUuid) { mutableStateOf(VerifiedContentFilterStore.isEnabled(context, auth.userUuid)) }
    var peopleListIndex by remember(auth.userUuid) { mutableIntStateOf(0) }
    var peopleListOffset by remember(auth.userUuid) { mutableIntStateOf(0) }
    var recentActivity by remember(auth.userUuid) { mutableStateOf(cached.recentActivity) }
    var leaderboardPosition by remember(auth.userUuid) { mutableIntStateOf(cached.leaderboardPosition) }
    var leaderboardTotal by remember(auth.userUuid) { mutableIntStateOf(cached.leaderboardTotal) }
    var confirmLogout by remember(auth.userUuid) { mutableStateOf(false) }
    LaunchedEffect(profile, alias) {
        if (profile != null || !alias.isNullOrBlank()) {
            SidebarDisplayCache.update(context, auth.userUuid) {
                it.copy(profile = profile ?: it.profile, alias = alias ?: it.alias)
            }
        }
    }
    LaunchedEffect(recentRooms) {
        if (recentRooms.isNotEmpty()) {
            SidebarDisplayCache.update(context, auth.userUuid) { it.copy(recentRooms = recentRooms.take(3)) }
        }
    }
    LaunchedEffect(auth.userUuid, notificationRevision) {
        activity = withContext(Dispatchers.IO) { historyStore.summary() }
        SidebarDisplayCache.update(context, auth.userUuid) { it.copy(activity = activity) }
    }
    LaunchedEffect(auth.userUuid, notificationRevision) {
        runCatching {
            api.call(
                "/v2/users/get",
                JSONObject().put("user_uuid", auth.userUuid).put("posts_limit", 4)
                    .put("comments_limit", 4).put("voted_posts_limit", 0), auth,
            ) as? JSONObject
        }.getOrNull()?.let { root ->
            historyStore.reconcileTotalUpvotes(root.optInt("totalUpvotes"))
            historyStore.reconcileFollowerCount(root.optInt("aliasesReceived"))
            activity = historyStore.summary()
            followers = root.optInt("aliasesReceived")
            following = root.optInt("aliasesGiven")
            root.optJSONObject("user")?.let {
                elo = it.optInt("elo_rating", 1500)
                joined = it.optString("created_at").takeIf(String::isNotBlank)
            }
            val activityRows = buildList {
                val posts = root.optJSONObject("recentPosts")?.optJSONArray("posts")
                if (posts != null) for (index in 0 until posts.length()) posts.optJSONObject(index)?.let { post ->
                    val uuid = post.optString("uuid").takeIf(String::isNotBlank) ?: return@let
                    val raw = post.optString("title").ifBlank { post.optString("text") }
                    val preview = sidebarActivityPreview(raw, post.optJSONObject("post_meta"), post.optInt("post_type"))
                    add(SidebarActivityItem("Post", preview, uuid, null, post.optString("created_at")))
                }
                val commentsRoot = root.optJSONObject("recentComments")
                val comments = commentsRoot?.optJSONArray("comments")
                if (comments != null) for (index in 0 until comments.length()) comments.optJSONObject(index)?.let { comment ->
                    val postUuid = comment.optString("post_uuid").takeIf(String::isNotBlank) ?: return@let
                    add(SidebarActivityItem("Comment", sidebarActivityPreview(comment.optString("text"), comment.optJSONObject("comment_meta")), postUuid, comment.optString("uuid").takeIf(String::isNotBlank), comment.optString("created_at")))
                }
            }
            recentActivity = activityRows.sortedByDescending { it.createdAt }.take(3)
            SidebarDisplayCache.update(context, auth.userUuid) {
                it.copy(
                    followers = followers,
                    following = following,
                    elo = elo,
                    joined = joined,
                    activity = activity,
                    recentActivity = recentActivity,
                )
            }
        }
    }
    LaunchedEffect(auth.userUuid, notificationRevision) {
        runCatching {
            api.call(
                "/v2/auth/login",
                JSONObject().put("version", "web-v0.1.3").put("secret_key", auth.secretKey),
                auth,
            ) as? JSONObject
        }.getOrNull()?.optJSONObject("leaderboard")?.let { leaderboard ->
            leaderboardPosition = leaderboard.optInt("myPosition")
            leaderboardTotal = leaderboard.optInt("totalPositions")
            SidebarDisplayCache.update(context, auth.userUuid) {
                it.copy(leaderboardPosition = leaderboardPosition, leaderboardTotal = leaderboardTotal)
            }
        }
    }
    val resolved = profile ?: cached.profile ?: ComposeAuthorProfile(auth.userUuid)
    val resolvedAlias = (alias ?: cached.alias)?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
    val displayUser = remember(resolved, resolvedAlias, elo) {
        resolved.toUserDisplay(nickname = resolvedAlias, elo = elo)
    }

    // A suspended sheet must not leave the sidebar's invisible tap catcher on screen.
    if (progress > .001f) BoxWithConstraints(modifier.fillMaxSize()) {
        val width = (maxWidth - AccountSidebarRevealWidth).coerceAtLeast(260.dp)
        val widthPx = with(LocalDensity.current) { width.toPx() }
        val closeDragModifier = Modifier.pointerInput(widthPx) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (dragFraction > .16f) onDismiss()
                    else { dragFraction = 0f; onDragFractionChange(0f) }
                },
                onDragCancel = { dragFraction = 0f; onDragFractionChange(0f) },
            ) { change, amount ->
                if (amount > 0f || dragFraction > 0f) {
                    change.consume()
                    dragFraction = (dragFraction + amount / widthPx).coerceIn(0f, 1f)
                    onDragFractionChange(dragFraction)
                }
            }
        }
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .52f * progress * progress))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .then(closeDragModifier),
        )
        Column(
            Modifier.align(Alignment.TopEnd).width(width).fillMaxHeight()
                .graphicsLayer { translationX = widthPx * (1f - progress) }
                .clip(SidebarShape).background(Background)
                .drawWithContent {
                    drawContent()
                    val radius = 20.dp.toPx()
                    val line = Path().apply {
                        moveTo(radius, 0f)
                        cubicTo(radius * .42f, 0f, 0f, radius * .42f, 0f, radius)
                        lineTo(0f, size.height - radius)
                        cubicTo(0f, size.height - radius * .42f, radius * .42f, size.height, radius, size.height)
                    }
                    drawPath(line, Color.White.copy(alpha = .11f), style = Stroke(.5.dp.toPx()))
                }
                .then(closeDragModifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(statusBarPadding + 24.dp))
            Box(Modifier.clickable { AppHaptics.navigate(view); onOpenProfile() }) { ComposeNetworthPill(resolved, auth.userUuid, compact = false) }
            Row(Modifier.fillMaxWidth().padding(top = 15.dp), Arrangement.Center, Alignment.CenterVertically) {
                ProfileStat(NotificationIcons.Users, followers, "Followers") { AppHaptics.open(view); followersOpen = true }
                StatDivider()
                ProfileStat(NotificationIcons.UserPlus, following, "Following") { AppHaptics.open(view); followingOpen = true }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                UserMetaPill(displayUser, resolvedAlias, Modifier.widthIn(max = 270.dp), compact = true, fillWidth = false, elo = elo, joined = joined?.let(::joinedAgo), joinedExact = joined)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 18.dp).height(1.dp).background(Color.White.copy(alpha = .07f)))
            ActivityStatsCard(activity, recentActivity, { post, comment -> AppHaptics.navigate(view); onOpenActivity(post, comment) }, { AppHaptics.navigate(view); onOpenProfile() })
            Spacer(Modifier.height(10.dp))
            LeaderboardCard(leaderboardPosition, leaderboardTotal) { AppHaptics.navigate(view); onOpenLeaderboard() }
            Spacer(Modifier.height(10.dp))
            RecentRoomsCard((if (recentRooms.isNotEmpty()) recentRooms else cached.recentRooms).take(3), auth.userUuid, { room -> AppHaptics.navigate(view); onOpenRoom(room) }, { AppHaptics.navigate(view); onOpenRoomsOverview() })
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .035f))
                    .clickable { AppHaptics.navigate(view); onOpenSettings() }.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Settings, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(18.dp))
                Text("Settings", color = Color.White.copy(alpha = .86f), fontSize = 13.sp, fontWeight = FontWeight.Bold, style = NoFontPadding)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .035f))
                    .clickable {
                        AppHaptics.toggle(view)
                        offlineMode = !offlineMode
                        OfflineModeStore.setEnabled(context, auth.userUuid, offlineMode)
                        onOfflineModeChanged(offlineMode)
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.VisibilityOff, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(18.dp))
                    Text("Appear offline", color = Color.White.copy(alpha = .86f), fontSize = 13.sp, fontWeight = FontWeight.Bold, style = NoFontPadding)
                }
                Box(Modifier.width(38.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(if (offlineMode) SidebarGold.copy(alpha = .9f) else Color.White.copy(alpha = .1f))) {
                    Box(Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = if (offlineMode) 17.dp.toPx() else 3.dp.toPx() }.size(18.dp).clip(CircleShape).background(if (offlineMode) Color(0xFF15120B) else Color.White.copy(alpha = .65f)))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .035f))
                    .clickable {
                        AppHaptics.toggle(view)
                        verifiedOnly = !verifiedOnly
                        VerifiedContentFilterStore.setEnabled(context, auth.userUuid, verifiedOnly)
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.VerifiedUser, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(18.dp))
                    Text("Show verified accounts only", color = Color.White.copy(alpha = .86f), fontSize = 13.sp, fontWeight = FontWeight.Bold, style = NoFontPadding)
                }
                Box(Modifier.width(38.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(if (verifiedOnly) SidebarGold.copy(alpha = .9f) else Color.White.copy(alpha = .1f))) {
                    Box(Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = if (verifiedOnly) 17.dp.toPx() else 3.dp.toPx() }.size(18.dp).clip(CircleShape).background(if (verifiedOnly) Color(0xFF15120B) else Color.White.copy(alpha = .65f)))
                }
            }
            AnimatedVisibility(visible = offlineMode, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 7.dp).clip(RoundedCornerShape(12.dp)).background(SidebarGold.copy(alpha = .075f))
                        .border(.5.dp, SidebarGold.copy(alpha = .18f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.VisibilityOff, null, tint = SidebarGold.copy(alpha = .8f), modifier = Modifier.size(15.dp))
                    Text("Live messages cannot be received while you appear offline. You can still send messages and refresh chats manually.", color = Color.White.copy(alpha = .58f), fontSize = 10.5.sp, lineHeight = 14.sp, style = NoFontPadding)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEF4444).copy(alpha = .055f))
                    .border(.6.dp, Color(0xFFEF4444).copy(alpha = .14f), RoundedCornerShape(14.dp))
                    .clickable {
                        if (confirmLogout) {
                            AppHaptics.confirm(view)
                            onLogout()
                        } else {
                            AppHaptics.open(view)
                            confirmLogout = true
                        }
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Logout, null, tint = Color(0xFFFB7185).copy(alpha = .8f), modifier = Modifier.size(18.dp))
                Text(if (confirmLogout) "Are you sure?" else "Log out", color = Color(0xFFFB7185).copy(alpha = .9f), fontSize = 13.sp, fontWeight = FontWeight.Bold, style = NoFontPadding)
            }
            Spacer(Modifier.height(navigationBarPadding + 22.dp))
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
            onOpenProfile = { _, selectedProfile, index, offset ->
                peopleListIndex = index
                peopleListOffset = offset
                followingOpen = true
                onDismiss()
                ProfileNavigationBus.open(selectedProfile)
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
            onOpenProfile = { _, selectedProfile, index, offset ->
                peopleListIndex = index
                peopleListOffset = offset
                onDismiss()
                ProfileNavigationBus.open(selectedProfile)
            },
        )
    }
}
