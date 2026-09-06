package com.twocents.mobile.ui.messages

import com.twocents.mobile.ui.common.EdgeToEdgeDialogWindow

import android.content.Intent
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
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.theme.Gold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat

private const val MemberPageSize = 20

@Composable
internal fun RoomInfoSheet(
    room: RoomSummary,
    auth: AuthState,
    api: RpcApi,
    aliases: Map<String, String>,
    onDismiss: () -> Unit,
    onLeave: () -> Unit,
    onOpenProfile: (ComposeAuthorProfile) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var members by remember(room.uuid) { mutableStateOf(room.members.filter { it.leftAt == null }.distinctBy { it.uuid }) }
    var loading by remember(room.uuid) { mutableStateOf(true) }
    var resolvedRoomCode by remember(room.uuid) { mutableStateOf(room.roomCode) }
    var visibleCount by remember(room.uuid) { mutableIntStateOf(MemberPageSize) }
    var paging by remember(room.uuid) { mutableStateOf(false) }
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 72.dp.toPx() }
    val dismissDistance = with(density) { 1200.dp.toPx() }
    var dragOffset by remember(room.uuid) { mutableFloatStateOf(dismissDistance) }
    var closing by remember(room.uuid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            Animatable(dragOffset).animateTo(dismissDistance, tween(210)) { dragOffset = value }
            onDismiss()
        }
    }
    LaunchedEffect(room.uuid) { Animatable(dragOffset).animateTo(0f, tween(210)) { dragOffset = value } }

    LaunchedEffect(room.uuid) {
        // Recover the server-persisted invite code instead of relying solely on
        // device preferences, which may have been cleared or restored elsewhere.
        if (resolvedRoomCode.isNullOrBlank()) runCatching {
            val roomRoot = api.call("/v1/rooms/getRoom", JSONObject().put("roomUuid", room.uuid), auth) as? JSONObject
            val item = roomRoot?.optJSONObject("room")
            item?.optString("room_code").takeUnless { it.isNullOrBlank() || it == "null" }
                ?: item?.optString("roomCode").takeUnless { it.isNullOrBlank() || it == "null" }
        }.getOrNull()?.let { resolvedRoomCode = it }
        runCatching {
            val root = api.call("/v1/rooms/getMembers", JSONObject().put("roomUuid", room.uuid), auth) as? JSONObject
            val array = root?.optJSONArray("members") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val uuid = item.optString("user_uuid").ifBlank { item.optString("userUuid") }.ifBlank { item.optString("uuid") }
                    if (uuid.isBlank() || (!item.isNull("left_at") && item.optString("left_at").isNotBlank())) continue
                    add(
                        RoomMember(
                            uuid = uuid,
                            balance = item.optDouble("balance", item.optDouble("networth"))
                                .takeIf(Double::isFinite) ?: 0.0,
                            subscriptionType = when {
                                item.has("subscription_type") && !item.isNull("subscription_type") -> item.optInt("subscription_type")
                                item.has("subscriptionType") && !item.isNull("subscriptionType") -> item.optInt("subscriptionType")
                                else -> 0
                            }.coerceAtLeast(0),
                            role = item.infoString("role"),
                            alias = item.infoString("alias", "systemAlias", "display_name"),
                            username = item.infoString("username"),
                            online = item.optBoolean("is_online", item.optBoolean("isOnline")),
                            gender = item.infoString("gender"),
                            age = item.optInt("age"),
                            arena = item.infoString("arena"),
                        ),
                    )
                }
            }.distinctBy { it.uuid }
        }.getOrNull()?.let { if (it.isNotEmpty()) members = it }
        loading = false
    }

    val ordered = remember(members) { members.sortedWith(compareByDescending<RoomMember> { it.online }.thenBy { aliases[it.uuid] ?: it.alias ?: it.username ?: "" }) }
    val visible = ordered.take(visibleCount)
    LaunchedEffect(listState, ordered.size, visibleCount) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { it >= visible.size - 3 && visible.size < ordered.size }
            .distinctUntilChanged()
            .collect { shouldPage ->
                if (shouldPage && !paging) {
                    paging = true
                    delay(90)
                    visibleCount = (visibleCount + MemberPageSize).coerceAtMost(ordered.size)
                    paging = false
                }
            }
    }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        EdgeToEdgeDialogWindow(
            navigationBarColor = android.graphics.Color.rgb(20, 20, 16),
            decorFitsSystemWindows = null,
        )
        val openFraction = (1f - dragOffset / dismissDistance).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f * openFraction)).clickable(onClick = ::close)) {
          Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 720.dp)
                .graphicsLayer { translationY = dragOffset }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFF141410))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(onClick = {}).navigationBarsPadding(),
          ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(room.uuid) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        },
                        onDragCancel = {
                            scope.launch { Animatable(dragOffset).animateTo(0f, tween(170)) { dragOffset = value } }
                        },
                        onDragEnd = {
                            if (dragOffset >= dismissThreshold) close()
                            else scope.launch { Animatable(dragOffset).animateTo(0f, tween(170)) { dragOffset = value } }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(room.gradients.ifEmpty { listOf(Color(0xFF32251A), Color(0xFF14120F)) })).border(1.dp, Color.White.copy(alpha = .15f), CircleShape))
            Column(Modifier.weight(1f)) {
                Text(room.name.ifBlank { "Direct message" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                room.description.takeIf(String::isNotBlank)?.let { Text(it, color = Color.White.copy(alpha = .5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .05f)).clickable(onClick = ::close), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).border(0.dp, Color.Transparent), horizontalArrangement = Arrangement.SpaceEvenly) {
            InfoStat(formatInfoCount(ordered.size.ifEmpty(room.memberCount)), "MEMBERS")
            InfoStat(formatInfoCount(ordered.count { it.online }), "ONLINE", Color(0xFF34D399))
            InfoStat(room.totalMessages.toString(), "MESSAGES")
        }
        resolvedRoomCode?.let { code ->
            val inviteLink = "https://www.twocents.money/join/${room.uuid}/$code"
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
                    .background(Gold.copy(alpha = .08f)).border(1.dp, Gold.copy(alpha = .22f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Column {
                    Text("INVITE LINK", color = Color.White.copy(alpha = .4f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Text(inviteLink.removePrefix("https://www."), color = Gold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoomInviteAction("Copy link", Modifier.weight(1f)) { clipboard.setText(AnnotatedString(inviteLink)) }
                    RoomInviteAction("Share invite", Modifier.weight(1f)) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, inviteLink)
                        }, "Share room invite"))
                    }
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 380.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            if (loading && members.isEmpty()) items(6) { RoomMemberPlaceholder() }
            else {
                val online = visible.filter { it.online }
                val offline = visible.filterNot { it.online }
                if (online.isNotEmpty()) { item("online-head") { MemberSection("ONLINE", ordered.count { it.online }, true) }; items(online, key = { it.uuid }) { RoomInfoMember(it, aliases[it.uuid], onOpenProfile) } }
                if (offline.isNotEmpty()) { item("offline-head") { MemberSection("OFFLINE", ordered.count { !it.online }, false) }; items(offline, key = { it.uuid }) { RoomInfoMember(it, aliases[it.uuid], onOpenProfile) } }
                if (paging) item("paging") { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), color = Gold, strokeWidth = 2.dp) } }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEF4444).copy(alpha = .08f)).border(1.dp, Color(0xFFEF4444).copy(alpha = .25f), RoundedCornerShape(14.dp)).clickable(onClick = onLeave).padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Logout, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Leave Room", color = Color(0xFFEF4444), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
          }
        }
    }
}

@Composable
private fun RoomInviteAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(9.dp)).background(Gold.copy(alpha = .15f)).clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Link, null, tint = Gold, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Gold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable private fun InfoStat(value: String, label: String, color: Color = Color.White) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text(label, color = Color.White.copy(alpha = .4f), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
@Composable private fun MemberSection(label: String, count: Int, online: Boolean) = Row(
    Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = if (online) Color(0xFF34D399).copy(alpha = .85f) else Color.White.copy(alpha = .4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(count.toString(), color = Color.White.copy(alpha = .3f), fontSize = 10.sp)
    }
    Box(Modifier.weight(1f).offset(y = 3.dp).height(1.dp).background(Color.White.copy(alpha = .06f)))
}
@Composable private fun RoomInfoMember(member: RoomMember, followedAlias: String?, onOpenProfile: (ComposeAuthorProfile) -> Unit) {
    val nickname = followedAlias ?: member.alias?.takeUnless { it.startsWith("$") } ?: member.username?.takeUnless { it.startsWith("$") || it == "You" }
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenProfile(ComposeAuthorProfile(member.uuid, member.balance, member.subscriptionType, member.role, member.gender, member.age, member.arena)) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            ComposeNetworthPill(ComposeAuthorProfile(member.uuid, member.balance, member.subscriptionType, member.role), member.uuid, compact = true)
            if (member.online) Box(Modifier.align(Alignment.BottomEnd).size(11.dp).clip(CircleShape).background(Color(0xFF141410)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF34D399)))
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            UserMetaPill(
                member.toUserDisplay(nickname),
                nickname,
                Modifier.widthIn(max = 250.dp),
                compact = true,
                fillWidth = false,
            )
        }
    }
}
@Composable private fun RoomMemberPlaceholder() = Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(70.dp).height(26.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f))); Spacer(Modifier.width(9.dp)); Box(Modifier.width(112.dp).height(12.dp).clip(CircleShape).background(Color.White.copy(alpha = .07f))) }
private fun Int.ifEmpty(fallback: Int) = if (this > 0) this else fallback
private fun formatInfoCount(value: Int) = when { value >= 1_000_000 -> "${value / 1_000_000}M"; value >= 1_000 -> "${value / 1_000}K"; else -> value.toString() }
private fun JSONObject.infoString(vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() && it != "null" } }
