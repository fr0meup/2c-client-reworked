package com.twocents.mobile.ui.profile

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.common.EdgeToEdgeDialogWindow
import com.twocents.mobile.ui.common.toUserDisplay
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.feed.UserMetaPill
import kotlinx.coroutines.launch

private val FollowersGold = Color(0xFFC8A44D)
private val FollowersShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * Mirrors FollowingSheet's edge-to-edge dialog frame. Discovery is a list item
 * so the scan controls scroll away with the follower rows instead of sticking.
 */
@Composable
internal fun FollowersSheet(
    auth: AuthState,
    api: RpcApi,
    onDismiss: () -> Unit,
    initialListIndex: Int = 0,
    initialListOffset: Int = 0,
    onOpenProfile: (String, ComposeAuthorProfile, Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 90.dp.toPx() }
    val dismissDistancePx = with(density) { 1200.dp.toPx() }
    val sheetOffset = remember { Animatable(dismissDistancePx) }
    val listState = rememberLazyListState(initialListIndex, initialListOffset)
    val state = FollowersScanner.state(context, auth.userUuid)
    var closing by remember { mutableStateOf(false) }
    var confirmScan by remember { mutableStateOf(false) }

    fun close(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            sheetOffset.animateTo(dismissDistancePx, tween(210))
            onDismiss()
            after()
        }
    }

    LaunchedEffect(Unit) { sheetOffset.animateTo(0f, tween(210)) }
    LaunchedEffect(auth.userUuid) { FollowersScanner.refreshAliases(context, api, auth) }
    DisposableEffect(auth.userUuid) {
        FollowersScanner.setSheetVisible(auth.userUuid, true)
        onDispose { FollowersScanner.setSheetVisible(auth.userUuid, false) }
    }

    val listDismissConnection = remember(listState, dismissThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !listState.canScrollBackward) {
                    scope.launch { sheetOffset.snapTo((sheetOffset.value + available.y).coerceAtLeast(0f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetOffset.value <= 0f) return Velocity.Zero
                if (sheetOffset.value > dismissThresholdPx) close() else sheetOffset.animateTo(0f, tween(180))
                return available
            }
        }
    }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        EdgeToEdgeDialogWindow(navigationBarColor = AndroidColor.TRANSPARENT)
        val openFraction = (1f - sheetOffset.value / dismissDistancePx).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f * openFraction)).clickable { close() }) {
            val dragToDismiss = Modifier.pointerInput(onDismiss) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        if (amount > 0f || sheetOffset.value > 0f) {
                            change.consume()
                            scope.launch { sheetOffset.snapTo((sheetOffset.value + amount).coerceAtLeast(0f)) }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (sheetOffset.value > dismissThresholdPx) close()
                            else sheetOffset.animateTo(0f, tween(180))
                        }
                    },
                    onDragCancel = { scope.launch { sheetOffset.animateTo(0f, tween(180)) } },
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.86f)
                    .graphicsLayer { translationY = sheetOffset.value }
                    .clip(FollowersShape).background(Color(0xFF0F0E0A))
                    .border(1.dp, Color.White.copy(alpha = .1f), FollowersShape)
                    .clickable(onClick = {}),
            ) {
                Box(Modifier.fillMaxWidth().then(dragToDismiss).padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)))
                }
                Row(Modifier.fillMaxWidth().then(dragToDismiss).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(FollowersGold.copy(alpha = .12f)).border(1.dp, FollowersGold.copy(alpha = .25f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PeopleAlt, null, tint = FollowersGold, modifier = Modifier.size(19.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Followers", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("  (BETA)", color = Color.White.copy(alpha = .25f), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (state.running) state.phase else if (state.snapshot.completedAt > 0 || state.snapshot.followers.isNotEmpty()) "${state.snapshot.followers.size} known followers" else "Scan available users",
                            color = Color.White.copy(alpha = .45f), fontSize = 11.5.sp,
                        )
                    }
                    Box(Modifier.size(40.dp).clickable { close() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(17.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(listDismissConnection),
                    contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item("discovery") {
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(15.dp))
                                .background(Color.White.copy(alpha = .03f)).border(.7.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(15.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Text("Follower discovery", color = Color.White.copy(alpha = .9f), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text("twocents does not provide a follower list. 2c scans users visible in your rooms, DMs, leaderboards, following list and local search index, then checks which of them follow you.", color = Color.White.copy(alpha = .52f), fontSize = 11.5.sp, lineHeight = 16.sp)
                            Text("This can take several minutes and the service may temporarily rate-limit requests. Using other network-heavy features during the scan can increase that risk.", color = FollowersGold.copy(alpha = .72f), fontSize = 10.5.sp, lineHeight = 14.sp)
                            state.error?.let { Text(it, color = Color(0xFFFB7185), fontSize = 11.sp) }
                            if (state.running) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    CircularProgressIndicator(Modifier.size(17.dp), color = FollowersGold, strokeWidth = 1.8.dp)
                                    Text(
                                        state.phase + if (state.total > 0) " ${state.completed} of ${state.total}" else "",
                                        color = Color.White.copy(alpha = .65f), fontSize = 11.5.sp,
                                    )
                                }
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().height(38.dp).clip(CircleShape).background(FollowersGold.copy(alpha = .14f))
                                        .border(1.dp, FollowersGold.copy(alpha = .28f), CircleShape).clickable {
                                            if (confirmScan) {
                                                confirmScan = false
                                                FollowersScanner.start(context, api, auth)
                                            } else confirmScan = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (confirmScan) "Are you sure?" else if (state.snapshot.completedAt > 0) "Rescan" else "Start scan", color = FollowersGold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    items(state.snapshot.followers, key = { it.profile.uuid }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable {
                                val index = listState.firstVisibleItemIndex
                                val offset = listState.firstVisibleItemScrollOffset
                                close { onOpenProfile(entry.profile.uuid, entry.profile, index, offset) }
                            }.padding(horizontal = 6.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            ComposeNetworthPill(entry.profile, entry.profile.uuid, compact = true)
                            UserMetaPill(entry.profile.toUserDisplay(entry.alias), entry.alias, Modifier.weight(1f, fill = false), compact = true, fillWidth = false)
                        }
                    }
                    if (!state.running && state.snapshot.completedAt > 0 && state.snapshot.followers.isEmpty()) {
                        item("empty") {
                            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                                Text("No followers found in the scanned users", color = Color.White.copy(alpha = .4f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
