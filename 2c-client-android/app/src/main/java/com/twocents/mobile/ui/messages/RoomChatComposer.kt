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
internal fun RoomComposer(
    input: String,
    onInput: (String) -> Unit,
    reply: ChatMessage?,
    onClearReply: () -> Unit,
    selectedImage: Uri?,
    onClearImage: () -> Unit,
    onPickImage: () -> Unit,
    selectedGif: String?,
    onClearGif: () -> Unit,
    onPickGif: () -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    val view = LocalView.current
    Column(
        Modifier.fillMaxWidth().background(Background).drawBehind {
            drawLine(Color.White.copy(alpha = .08f), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        }
            .imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        reply?.let {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Gold.copy(alpha = .08f)).border(1.dp, Gold.copy(alpha = .2f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Replying to", color = Gold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, style = ChatNoPadding)
                val author = it.author
                ComposeNetworthPill(
                    ComposeAuthorProfile(it.authorUuid, author.balance, author.subscriptionType, author.role),
                    it.authorUuid,
                    compact = true,
                )
                Text(
                    it.text.ifBlank { if (it.mediaUrl != null) "Image" else "Message" },
                    color = Color.White.copy(alpha = .45f), fontSize = 11.5.sp, fontStyle = FontStyle.Italic,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = ChatNoPadding,
                )
                Icon(Icons.Outlined.Close, "Cancel reply", tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClearReply).padding(3.dp))
            }
        }
        selectedImage?.let { uri ->
            Box(Modifier.size(62.dp)) {
                AsyncImage(uri, null, Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Color(0xFFEF4444)).clickable(onClick = onClearImage), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
        }
        selectedGif?.let { url ->
            Box(Modifier.size(82.dp)) {
                AsyncImage(url, null, Modifier.size(80.dp).clip(RoundedCornerShape(9.dp)))
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Color(0xFFEF4444)).clickable(onClick = onClearGif), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF161410)).border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(22.dp)).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f).heightIn(max = 100.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (input.isEmpty()) Text("Message…", color = Color.White.copy(alpha = .3f), fontSize = 14.sp, style = ChatNoPadding); inner() } },
            )
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { AppHaptics.open(view); onPickImage() }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, "Add image", tint = Color.White.copy(alpha = .45f), modifier = Modifier.size(18.dp)) }
            Box(Modifier.width(36.dp).height(32.dp).clip(CircleShape).clickable { AppHaptics.open(view); onPickGif() }, contentAlignment = Alignment.Center) { Text("GIF", color = Color.White.copy(alpha = .45f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, style = ChatNoPadding) }
            val canSend = (input.isNotBlank() || selectedImage != null || selectedGif != null) && !sending
            Box(Modifier.padding(start = 2.dp).size(32.dp).clip(CircleShape).background(if (canSend) Gold else Color.White.copy(alpha = .1f)).clickable(enabled = canSend) { AppHaptics.confirm(view); onSend() }, contentAlignment = Alignment.Center) {
                if (sending) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White.copy(alpha = .5f), strokeWidth = 1.5.dp)
                else Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = if (canSend) Color(0xFF0F0E0A) else Color.White.copy(alpha = .25f), modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
internal fun RoomChatSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(7) { index ->
            val mine = index % 3 == 1
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                if (!mine) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.width(64.dp).height(23.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)))
                        Box(Modifier.width((58 + index * 5).dp).height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)))
                    }
                    Spacer(Modifier.height(5.dp))
                }
                Box(
                    Modifier.width((145 + (index % 4) * 28).dp).height(if (index % 3 == 0) 62.dp else 42.dp)
                        .clip(if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                        .background(if (mine) Gold.copy(alpha = .12f) else Color.White.copy(alpha = .055f)),
                )
            }
        }
    }
}
