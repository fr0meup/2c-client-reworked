package com.twocents.mobile.ui.messages

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import coil3.compose.AsyncImage
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.core.format.compactTimeAgo
import com.twocents.mobile.core.format.exactLocalDate
import com.twocents.mobile.core.format.formatCompactCount
import com.twocents.mobile.core.format.parseApiInstant
import com.twocents.mobile.notifications.NotificationIcons
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.compose.GifPickerSheet
import com.twocents.mobile.ui.feed.ImageLightbox
import com.twocents.mobile.ui.common.PullToRefreshContainer
import com.twocents.mobile.ui.common.RefreshProgressBar
import com.twocents.mobile.ui.common.rememberPullToRefreshState
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.common.LinkifiedText
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val ChatNoPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))



@Composable
internal fun ChatUnreadDivider(count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Gold.copy(alpha = .34f)))
        Text(
            if (count == 1) "1 unread message" else "$count unread messages",
            color = Gold.copy(alpha = .85f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            style = ChatNoPadding,
        )
        Box(Modifier.weight(1f).height(1.dp).background(Gold.copy(alpha = .34f)))
    }
}

@Composable
internal fun RoomChatTopBar(room: RoomSummary, authUuid: String, aliases: Map<String, String>, connected: Boolean, onBack: () -> Unit, onInfo: () -> Unit, onLeave: () -> Unit) {
    val other = room.otherMember(authUuid)
    val dm = room.roomType == "dm" || room.members.size == 2 && room.name.isBlank()
    val nickname = if (dm) aliases[other?.uuid] ?: other?.alias?.takeUnless { it.startsWith("$") } ?: other?.username?.takeUnless { it.startsWith("$") || it == "You" } else null
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).background(Background)
            .border(width = 0.dp, color = Color.Transparent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatCircleButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(17.dp)) }
        Row(
            Modifier.weight(1f).padding(horizontal = 8.dp).height(38.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = .04f)).border(1.dp, Color.White.copy(alpha = .07f), CircleShape)
                .clickable(onClick = onInfo)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (dm && other != null) {
                ComposeNetworthPill(ComposeAuthorProfile(other.uuid, other.balance, other.subscriptionType, other.role), other.uuid, compact = true)
            } else {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Brush.linearGradient(room.gradients.ifEmpty { listOf(Color(0xFF32251A), Color(0xFF14120F)) })))
                Spacer(Modifier.width(6.dp))
                if (room.isPrivate) Icon(Icons.Outlined.Lock, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(12.dp))
                else Text("#", color = Color.White.copy(alpha = .55f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (dm) {
                nickname?.let {
                    Spacer(Modifier.width(7.dp))
                    Text(it, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 145.dp), style = ChatNoPadding)
                }
            } else {
                Spacer(Modifier.width(6.dp))
                Text(room.name.ifBlank { "Room" }, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp), style = ChatNoPadding)
            }
            Spacer(Modifier.width(7.dp))
            Icon(NotificationIcons.Users, null, tint = Color.White.copy(alpha = .4f), modifier = Modifier.size(12.dp))
            Text(room.memberCount.toString(), color = Color.White.copy(alpha = .4f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 3.dp), style = ChatNoPadding)
            Box(Modifier.padding(start = 6.dp).size(6.dp).clip(CircleShape).background(if (connected) Color(0xFF34D399) else Color.White.copy(alpha = .22f)))
        }
        ChatCircleButton(onLeave, danger = true) { Icon(Icons.Outlined.Logout, "Leave room", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp)) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
}

@Composable private fun ChatCircleButton(onClick: () -> Unit, danger: Boolean = false, content: @Composable () -> Unit) = Box(
    Modifier.size(36.dp).clip(CircleShape).background(if (danger) Color(0xFFEF4444).copy(alpha = .08f) else Color.White.copy(alpha = .06f))
        .border(1.dp, if (danger) Color(0xFFEF4444).copy(alpha = .2f) else Color.White.copy(alpha = .08f), CircleShape).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { content() }

