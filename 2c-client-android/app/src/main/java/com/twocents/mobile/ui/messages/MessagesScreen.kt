package com.twocents.mobile.ui.messages

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.core.format.compactTimeAgo
import com.twocents.mobile.core.format.formatCompactCount
import com.twocents.mobile.core.format.parseApiInstant
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.shell.MessagesListSkeleton
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.notifications.NotificationIcons
import com.twocents.mobile.notifications.LauncherBadge
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.AppLoadState
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

private val NoPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Immutable
data class RoomMember(
    val uuid: String,
    val balance: Double,
    val subscriptionType: Int,
    val role: String?,
    val alias: String?,
    val username: String?,
    val online: Boolean,
    val gender: String? = null,
    val age: Int = 0,
    val arena: String? = null,
    val leftAt: String? = null,
)

@Immutable
data class RoomRequirement(val met: Boolean, val label: String)

@Immutable
data class RoomSummary(
    val uuid: String,
    val name: String,
    val description: String,
    val roomType: String,
    val roomCode: String?,
    val isPrivate: Boolean,
    val gradients: List<Color>,
    val unread: Int,
    val memberCount: Int,
    val lastMessage: String,
    val lastMessageAt: String,
    val members: List<RoomMember>,
    val totalMessages: Int = 0,
    val requirements: List<RoomRequirement> = emptyList(),
)

@Immutable
data class MessagesState(
    val rooms: List<RoomSummary> = emptyList(),
    val dms: List<RoomSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val unreadCount: Int get() = (rooms.sumOf { it.unread } + dms.sumOf { it.unread }).coerceAtMost(999)
}

@Stable
class MessagesController(private val api: RpcApi, private val auth: AuthState, context: Context) {
    var state by mutableStateOf(MessagesState())
        private set
    private var loadedAt = 0L
    private val appContext = context.applicationContext
    private val inviteCodes = context.applicationContext.getSharedPreferences("twocents-room-invites", Context.MODE_PRIVATE)

    private fun restoreInviteCodes(rooms: List<RoomSummary>) = rooms.map { room ->
        room.copy(roomCode = room.roomCode ?: inviteCodes.getString(room.uuid, null))
    }

    private fun rememberInviteCode(roomUuid: String, roomCode: String?) {
        if (!roomCode.isNullOrBlank()) inviteCodes.edit().putString(roomUuid, roomCode).apply()
    }

    private fun restoreMediaPreviews(rooms: List<RoomSummary>) = rooms.map { room ->
        if (room.lastMessage.isNotBlank()) room else {
            val cached = RoomMediaPreviewStore.get(appContext, room.uuid)
            val serverTime = parseApiInstant(room.lastMessageAt)?.toEpochMilli() ?: 0L
            val cachedTime = cached?.let { parseApiInstant(it.second)?.toEpochMilli() } ?: 0L
            if (cached == null || (serverTime > 0L && cachedTime < serverTime)) room else room.copy(
                lastMessage = cached.first,
                lastMessageAt = room.lastMessageAt.ifBlank { cached.second },
            )
        }
    }

    suspend fun load(force: Boolean = false): Boolean {
        if (!force && loadedAt > 0 && System.currentTimeMillis() - loadedAt < 60_000) return true
        state = state.copy(loading = state.rooms.isEmpty() && state.dms.isEmpty(), error = null)
        return runCatching {
            val (roomsRoot, dmsRoot) = coroutineScope {
                val rooms = async { api.call("/v2/rooms/listUserRooms", JSONObject(), auth) as? JSONObject }
                val dms = async { api.call("/v2/rooms/listUserDMs", JSONObject(), auth) as? JSONObject }
                rooms.await() to dms.await()
            }
            val comparator = compareByDescending<RoomSummary> { parseApiInstant(it.lastMessageAt)?.toEpochMilli() ?: 0L }
            state = MessagesState(
                rooms = restoreInviteCodes(parseRooms(roomsRoot)).sortedWith(comparator),
                dms = restoreMediaPreviews(restoreInviteCodes(parseRooms(dmsRoot))).sortedWith(comparator),
                loading = false,
            )
            loadedAt = System.currentTimeMillis()
            publishLauncherBadge()
            true
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            state = state.copy(loading = false, error = error.message ?: "Couldn't load rooms")
            false
        }
    }

    fun markOpened(roomUuid: String) {
        state = state.copy(
            rooms = state.rooms.map { if (it.uuid == roomUuid) it.copy(unread = 0) else it },
            dms = state.dms.map { if (it.uuid == roomUuid) it.copy(unread = 0) else it },
        )
        publishLauncherBadge()
    }

    suspend fun resolveRoom(roomUuid: String): RoomSummary? {
        (state.rooms + state.dms).firstOrNull { it.uuid == roomUuid }?.let { return it }
        val fetched = runCatching {
            val root = api.call("/v1/rooms/getRoom", JSONObject().put("roomUuid", roomUuid), auth) as? JSONObject
            val roomObject = root?.optJSONObject("room") ?: return@runCatching null
            parseRooms(JSONObject().put("rooms", JSONArray().put(roomObject))).firstOrNull()
        }.getOrNull()
        if (fetched != null) {
            state = if (fetched.roomType == "room") state.copy(rooms = listOf(fetched) + state.rooms.filterNot { it.uuid == roomUuid })
            else state.copy(dms = listOf(fetched) + state.dms.filterNot { it.uuid == roomUuid })
        }
        return fetched
    }

    private fun publishLauncherBadge() {
        LauncherBadge.setKnownDirectMessages(appContext, state.dms.mapTo(linkedSetOf()) { it.uuid })
        LauncherBadge.setDirectMessages(appContext, state.dms.sumOf { it.unread })
    }

    suspend fun exploreRooms(): List<RoomSummary> = runCatching {
        parseRooms(api.call("/v1/rooms/listRooms", JSONObject(), auth) as? JSONObject)
            .filter { it.roomType == "room" || it.roomType.isBlank() }
    }.getOrDefault(emptyList())

    suspend fun joinRoom(room: RoomSummary): RoomSummary? = runCatching {
        api.call("/v1/rooms/joinRoom", JSONObject().put("roomUuid", room.uuid), auth)
        load(force = true)
        (state.rooms + state.dms).firstOrNull { it.uuid == room.uuid } ?: room
    }.getOrNull()

    suspend fun joinByCode(code: String): RoomSummary? = runCatching {
        val input = code.trim()
        val linkMatch = Regex("https?://(?:www\\.)?twocents\\.(?:money|com)/join/([0-9a-fA-F-]{32,36})/([^/?#\\s]+)", RegexOption.IGNORE_CASE).find(input)
            ?: error("Paste a complete twocents room invite link.")
        val roomUuid = linkMatch.groupValues[1]
        val roomCode = linkMatch.groupValues[2]
        if (!roomCode.isNullOrBlank()) {
            api.call(
                "/v1/rooms/joinRoomWithCode",
                JSONObject().put("roomUuid", roomUuid).put("roomCode", roomCode),
                auth,
            )
            rememberInviteCode(roomUuid, roomCode)
        } else {
            api.call("/v1/rooms/joinRoom", JSONObject().put("roomUuid", roomUuid), auth)
        }
        load(force = true)
        (state.rooms + state.dms).firstOrNull { it.uuid == roomUuid }
            ?: (state.rooms + state.dms).maxByOrNull { parseApiInstant(it.lastMessageAt)?.toEpochMilli() ?: 0L }
    }.getOrNull()

    suspend fun createGroup(): RoomSummary? = runCatching {
        val fakeUuid = UUID.randomUUID().toString()
        val suffix = fakeUuid.take(8)
        val roomCode = "gc-$suffix"
        val created = api.call(
            "/v1/rooms/startDM",
            JSONObject().put("recipientUuid", fakeUuid).put("targetUserUuid", fakeUuid),
            auth,
        ) as? JSONObject ?: error("Room creation failed")
        val roomUuid = created.optJSONObject("room")?.string("uuid")
            ?: created.string("roomUuid", "uuid") ?: error("Room creation failed")
        api.call(
            "/v1/rooms/updateRoom",
            JSONObject().put("roomUuid", roomUuid).put("name", "Group $suffix")
                .put("description", "Group chat $suffix").put("roomCode", roomCode).put("room_code", roomCode),
            auth,
        )
        api.bootstrapGroupRoom(roomUuid, auth)
        rememberInviteCode(roomUuid, roomCode)
        load(force = true)
        val result = state.rooms.firstOrNull { it.uuid == roomUuid }?.copy(roomCode = roomCode) ?: RoomSummary(
            uuid = roomUuid, name = "Group $suffix", description = "Group chat $suffix", roomType = "room",
            roomCode = roomCode, isPrivate = true, gradients = emptyList(), unread = 0, memberCount = 1,
            lastMessage = "", lastMessageAt = "", members = emptyList(),
        )
        state = state.copy(rooms = listOf(result) + state.rooms.filterNot { it.uuid == roomUuid })
        result
    }.getOrNull()
}

/** Rooms overview renderer; controller and JSON parsing are intentionally separate. */
@Composable
fun MessagesContent(
    controller: MessagesController,
    authUuid: String,
    aliases: Map<String, String>,
    bottomContentPadding: Dp,
    listState: LazyListState,
    onOpenRoom: (RoomSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    when {
        state.loading && state.rooms.isEmpty() && state.dms.isEmpty() -> MessagesListSkeleton(modifier)
        state.rooms.isEmpty() && state.dms.isEmpty() && state.error != null -> AppLoadState("Couldn't load rooms", state.error, modifier.background(Background))
        state.rooms.isEmpty() && state.dms.isEmpty() -> AppLoadState("No rooms yet", "Rooms and direct messages will appear here.", modifier.background(Background))
        else -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().background(Background),
            contentPadding = PaddingValues(start = 13.dp, top = 8.dp, end = 13.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.rooms.chunked(2), key = { row -> row.joinToString("|") { it.uuid } }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (row.size == 1) RoomCard(row.first(), Modifier.fillMaxWidth(), onOpenRoom)
                    else row.forEach { room -> RoomCard(room, Modifier.weight(1f), onOpenRoom) }
                }
            }
            if (state.dms.isNotEmpty()) {
                item("dm-divider") { DirectMessagesDivider(state.dms.size) }
                items(state.dms, key = { it.uuid }) { dm ->
                    DMRow(dm = dm, authUuid = authUuid, alias = dm.otherMember(authUuid)?.uuid?.let(aliases::get), onOpen = onOpenRoom)
                }
            }
        }
    }
}

@Composable
private fun RoomCard(room: RoomSummary, modifier: Modifier = Modifier, onOpen: (RoomSummary) -> Unit) {
    val view = LocalView.current
    val colors = room.gradients.ifEmpty { listOf(Color(0xFF32251A), Color(0xFF14120F)) }
    Box(
        modifier
            .height(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors))
            .background(Color.Black.copy(alpha = .3f))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
            .clickable { AppHaptics.navigate(view); onOpen(room) }
            .padding(13.dp),
    ) {
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (room.isPrivate) Icon(Icons.Outlined.Lock, null, tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(13.dp))
                else Text("#", color = Color.White.copy(alpha = .65f), fontSize = 15.sp, fontWeight = FontWeight.Bold, style = NoPadding)
                Text(room.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = NoPadding)
            }
            if (room.unread > 0) CountBadge(room.unread)
        }
        room.lastMessage.takeIf(String::isNotBlank)?.let {
            Text(cleanPreview(it), color = Color.White.copy(alpha = .55f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(bottom = 22.dp), style = NoPadding)
        }
        Row(Modifier.fillMaxWidth().align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(NotificationIcons.Users, null, tint = Color.White.copy(alpha = .45f), modifier = Modifier.size(12.dp))
                Text(formatCompactCount(room.memberCount), color = Color.White.copy(alpha = .45f), fontSize = 10.5.sp, style = NoPadding)
            }
            Text(compactTimeAgo(room.lastMessageAt), color = Color.White.copy(alpha = .4f), fontSize = 10.5.sp, style = NoPadding)
        }
    }
}

@Composable
private fun DirectMessagesDivider(count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = .08f)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("DIRECT MESSAGES", color = Color.White.copy(alpha = .35f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.15.sp, style = NoPadding)
            Text(count.toString(), color = Color.White.copy(alpha = .35f), fontSize = 10.sp, fontWeight = FontWeight.Bold, style = NoPadding)
        }
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = .08f)))
    }
}

@Composable
private fun DMRow(dm: RoomSummary, authUuid: String, alias: String?, onOpen: (RoomSummary) -> Unit) {
    val view = LocalView.current
    val other = dm.otherMember(authUuid)
    val display = alias ?: other?.alias?.takeUnless { it.startsWith("$") }
        ?: other?.username?.takeUnless { it.startsWith("$") || it == "You" }
        ?: dm.name.takeIf { it.isNotBlank() && !it.startsWith("$") }
        ?: "Direct message"
    val unread = dm.unread > 0
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (unread) Color.White.copy(alpha = .035f) else Color.White.copy(alpha = .025f))
            .border(1.dp, if (unread) Gold.copy(alpha = .18f) else Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp))
            .drawBehind {
                if (unread) {
                    drawLine(
                        color = Gold.copy(alpha = .92f),
                        start = androidx.compose.ui.geometry.Offset(.5.dp.toPx(), 15.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(.5.dp.toPx(), size.height - 15.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .clickable { AppHaptics.navigate(view); onOpen(dm) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            Box(Modifier.clip(CircleShape)) {
                ComposeNetworthPill(
                    profile = ComposeAuthorProfile(uuid = other?.uuid.orEmpty(), balance = other?.balance ?: 0.0, subscriptionType = other?.subscriptionType ?: 1, role = other?.role),
                    authUuid = other?.uuid.orEmpty(), compact = true,
                )
            }
            if (other?.online == true) Box(Modifier.align(Alignment.BottomEnd).size(11.dp).clip(CircleShape).background(Background), contentAlignment = Alignment.Center) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF34D399)))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(display, color = if (unread) Color.White else Color.White.copy(alpha = .85f), fontSize = 13.5.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = NoPadding)
                if (unread) CountBadge(dm.unread)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(cleanPreview(dm.lastMessage.ifBlank { dm.description.ifBlank { "No messages yet" } }), color = Color.White.copy(alpha = if (unread) .7f else .4f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = NoPadding)
                Text(compactTimeAgo(dm.lastMessageAt), color = Color.White.copy(alpha = .4f), fontSize = 11.sp, style = NoPadding)
            }
        }
    }
}

@Composable private fun CountBadge(value: Int) = Box(Modifier.widthIn(min = 18.dp).height(18.dp).clip(CircleShape).background(Gold).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
    Text(if (value > 99) "99+" else value.toString(), color = Color(0xFF0F0E0A), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, style = NoPadding)
}
