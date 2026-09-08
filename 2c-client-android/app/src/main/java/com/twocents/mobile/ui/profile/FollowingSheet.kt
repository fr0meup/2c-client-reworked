package com.twocents.mobile.ui.profile

import com.twocents.mobile.ui.common.EdgeToEdgeDialogWindow

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
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
import org.json.JSONObject

private val FollowingGold = Color(0xFFC8A44D)

private data class FollowingEntry(val alias: String?, val profile: ComposeAuthorProfile)

@Composable
internal fun FollowingSheet(
    auth: AuthState,
    api: RpcApi,
    onDismiss: () -> Unit,
    initialListIndex: Int = 0,
    initialListOffset: Int = 0,
    visible: Boolean = true,
    onOpenProfile: (String, ComposeAuthorProfile, Int, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 90.dp.toPx() }
    val dismissDistancePx = with(density) { 1200.dp.toPx() }
    val sheetOffset = remember { Animatable(dismissDistancePx) }
    val listState = rememberLazyListState(initialListIndex, initialListOffset)
    var closing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf(emptyList<FollowingEntry>()) }
    var error by remember { mutableStateOf<String?>(null) }
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
    val listDismissConnection = remember(listState, onDismiss, dismissThresholdPx, dismissDistancePx) {
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
                if (sheetOffset.value > dismissThresholdPx) {
                    close()
                } else sheetOffset.animateTo(0f, tween(180))
                return available
            }
        }
    }
    LaunchedEffect(auth.userUuid) {
        runCatching {
            val root = api.call("/v1/aliases/get", JSONObject(), auth) as? JSONObject
                ?: error("Following response was invalid")
            val rows = root.optJSONArray("aliases")
            buildList {
                if (rows != null) for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
                    val user = row.optJSONObject("user") ?: return@let
                    val uuid = row.optString("for_uuid").ifBlank { user.optString("uuid") }
                    if (uuid.isBlank()) return@let
                    add(FollowingEntry(
                        alias = row.optString("alias").takeUnless { it.isBlank() || it.equals("null", true) },
                        profile = ComposeAuthorProfile(
                            uuid = uuid, balance = user.optDouble("balance"),
                            subscriptionType = user.optInt("subscription_type", 1),
                            role = user.optString("role").takeIf(String::isNotBlank),
                            gender = user.optString("gender").takeIf(String::isNotBlank),
                            age = user.optInt("age").takeIf { user.has("age") && !user.isNull("age") },
                            arena = user.optString("arena").takeIf(String::isNotBlank),
                        ),
                    ))
                }
            }
        }.onSuccess { entries = it }.onFailure { error = it.message ?: "Couldn't load following" }
        loading = false
    }

    // Retain the loaded rows and exact LazyListState while a profile is on top.
    if (!visible) return
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
                            if (sheetOffset.value > 90.dp.toPx()) {
                                close()
                            } else sheetOffset.animateTo(0f, tween(180))
                        }
                    },
                    onDragCancel = { scope.launch { sheetOffset.animateTo(0f, tween(180)) } },
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.86f)
                    .graphicsLayer { translationY = sheetOffset.value }
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF0F0E0A))
                    .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(onClick = {}),
            ) {
                Box(Modifier.fillMaxWidth().then(dragToDismiss).padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)))
                }
                Row(Modifier.fillMaxWidth().then(dragToDismiss).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(FollowingGold.copy(alpha = .12f)).border(1.dp, FollowingGold.copy(alpha = .25f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PeopleAlt, null, tint = FollowingGold, modifier = Modifier.size(19.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text("Following", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(if (loading) "Loading…" else "${entries.size} ${if (entries.size == 1) "person" else "people"}", color = Color.White.copy(alpha = .45f), fontSize = 11.5.sp)
                    }
                    Box(Modifier.size(40.dp).clickable { close() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(17.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
                when {
                    loading -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(6) { Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .035f)) ) }
                    }
                    error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = Color.White.copy(alpha = .42f), fontSize = 13.sp) }
                    entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Not following anyone yet", color = Color.White.copy(alpha = .4f), fontSize = 13.sp) }
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(listDismissConnection), contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(entries, key = { it.profile.uuid }) { entry ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable {
                                    onOpenProfile(entry.profile.uuid, entry.profile, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                }.padding(horizontal = 6.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                ComposeNetworthPill(entry.profile, entry.profile.uuid, compact = true)
                                UserMetaPill(entry.profile.toUserDisplay(entry.alias), entry.alias, Modifier.weight(1f, fill = false), compact = true, fillWidth = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
