package com.twocents.mobile.ui.feed

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.twocents.mobile.kotlin.R
import com.twocents.mobile.notifications.NotificationIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private val MediaBackground = Color(0xFF0A0907)
@Composable
internal fun FeedVideoSurface(
    videoUri: String,
    player: ExoPlayer,
    thumbnailModel: Any,
    isPlaying: Boolean,
    isBuffering: Boolean,
    thumbnailVisible: Boolean,
    controlsVisible: Boolean,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    fullscreen: Boolean,
    landscape: Boolean,
    playbackSpeed: Float,
    onToggleControls: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleLandscape: () -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPosition by rememberUpdatedState(positionMs)
    val currentDuration by rememberUpdatedState(durationMs)
    val currentSeek by rememberUpdatedState(onSeek)
    var seekDirection by remember { mutableStateOf<Boolean?>(null) }
    var seekAmount by remember { mutableStateOf(0) }
    var seekFeedbackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun downloadVideo() {
        val request = DownloadManager.Request(Uri.parse(videoUri))
            .setTitle("2c video")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "2c-${System.currentTimeMillis()}.mp4")
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    fun doubleTapSeek(forward: Boolean) {
        seekAmount = if (seekDirection == forward && seekFeedbackJob?.isActive == true) seekAmount + 10 else 10
        seekDirection = forward
        val delta = if (forward) 10_000L else -10_000L
        currentSeek((currentPosition + delta).coerceIn(0L, currentDuration.coerceAtLeast(0L)))
        seekFeedbackJob?.cancel()
        seekFeedbackJob = scope.launch {
            delay(700)
            seekDirection = null
            seekAmount = 0
        }
    }

    Box(
        modifier = modifier
            .background(MediaBackground),
    ) {
        if (thumbnailVisible) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailModel)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(MediaBackground),
            )
        }
        AndroidView(
            factory = { viewContext ->
                (LayoutInflater.from(viewContext).inflate(R.layout.feed_video_player, null, false) as PlayerView).apply {
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { playerView ->
                if (playerView.player !== player) playerView.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (isPlaying) onToggleControls() else onTogglePlayback() },
                        onDoubleTap = { offset -> doubleTapSeek(offset.x >= size.width / 2f) },
                    )
                },
        )

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).offset(y = (-22).dp).size(28.dp),
                color = Color(0xFFC8A44D),
                trackColor = Color.White.copy(alpha = .10f),
                strokeWidth = 2.5.dp,
            )
        } else if (!isPlaying || controlsVisible) {
            val playInteraction = remember { MutableInteractionSource() }
            val playPressed by playInteraction.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    // The transport center is the area above the progress strip,
                    // not the geometric center of the complete player chrome.
                    .offset(y = (-22).dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF11100D).copy(alpha = if (playPressed) 0.90f else 0.76f))
                    .border(1.dp, Color.White.copy(alpha = if (playPressed) .22f else .14f), CircleShape)
                    .clickable(
                        interactionSource = playInteraction,
                        indication = null,
                        onClick = onTogglePlayback,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(27.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 46.dp, bottom = 8.dp),
            ) {
                FeedVideoProgress(
                    positionMs = positionMs,
                    bufferedMs = bufferedMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoControlButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        description = if (isPlaying) "Pause video" else "Play video",
                        onClick = onTogglePlayback,
                    )
                    VideoControlButton(
                        icon = Icons.Rounded.FastRewind,
                        description = "Rewind 10 seconds",
                        onClick = { onSeek((positionMs - 10_000L).coerceAtLeast(0L)) },
                    )
                    Text(
                        text = "${formatVideoTime(positionMs)} / ${formatVideoTime(durationMs)}",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (playbackSpeed == 1f) "1×" else "${playbackSpeed}×",
                        color = Color.White.copy(alpha = .72f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onCycleSpeed).padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                    VideoControlButton(
                        icon = Icons.Rounded.FastForward,
                        description = "Forward 10 seconds",
                        onClick = { onSeek((positionMs + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L))) },
                    )
                    VideoControlButton(
                        icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        description = if (fullscreen) "Exit fullscreen" else "Enter fullscreen",
                        onClick = onToggleFullscreen,
                    )
                }
            }
        }

        seekDirection?.let { forward ->
            Column(
                modifier = Modifier
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.42f)
                    .background(
                        Brush.horizontalGradient(
                            if (forward) {
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f))
                            } else {
                                listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent)
                            },
                        ),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.size(27.dp),
                )
                Text(
                    text = "${if (forward) "+" else "−"}${seekAmount}s",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = fullscreen && controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    Triple(NotificationIcons.RotateCw, if (landscape) "Return to portrait" else "Rotate to landscape", onToggleLandscape),
                    Triple(Icons.Rounded.Download, "Download video", ::downloadVideo),
                    Triple(Icons.Rounded.Close, "Close fullscreen", onToggleFullscreen),
                ).forEach { (icon, description, action) ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MediaBackground.copy(alpha = 0.78f)),
                    ) {
                        VideoControlButton(icon = icon, description = description, onClick = action)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullscreenSystemUi() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.setBackgroundDrawable(ColorDrawable(MediaBackground.toArgb()))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun FeedVideoProgress(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(durationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun seekAt(x: Float) {
                        if (durationMs > 0L && size.width > 0) {
                            onSeek(((x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                        }
                    }
                    seekAt(down.position.x)
                    down.consume()
                    var dragging = true
                    while (dragging) {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            seekAt(change.position.x)
                            change.consume()
                        }
                        dragging = event.changes.any { it.pressed }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
        )
        if (buffered > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(buffered)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
        }
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFC8A44D)),
            )
        }
        Box(
            Modifier
                .offset(x = (maxWidth * progress - 4.dp).coerceAtLeast(0.dp))
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFC8A44D)),
        )
    }
}

@Composable
private fun VideoControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (pressed) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatVideoTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

