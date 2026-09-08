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
internal fun ActiveFeedVideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    handoffOnMount: Boolean = false,
    autoPlayOnMount: Boolean = false,
    onLeaveViewport: () -> Unit,
) {
    val safeUri = remember(uri) {
        Uri.parse(uri).takeIf { parsed ->
            parsed.scheme?.lowercase() in setOf("https", "http", "content", "file")
        }?.toString()
    } ?: return
    val context = LocalContext.current
    val rootView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handoff = remember(safeUri) { VideoPlaybackHandoff.state(safeUri) }
    val owner = remember(safeUri) { Any() }
    var waitingForReturn by remember(safeUri) { mutableStateOf(false) }
    var pausedForDetailOverlay by remember(safeUri) { mutableStateOf(false) }
    var isPlaying by remember(safeUri) { mutableStateOf(false) }
    var isBuffering by remember(safeUri) { mutableStateOf(false) }
    var playbackError by remember(safeUri) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(safeUri) { mutableStateOf(true) }
    var thumbnailVisible by remember(safeUri) { mutableStateOf(true) }
    var fullscreen by remember(safeUri) { mutableStateOf(false) }
    var landscape by remember(safeUri) { mutableStateOf(false) }
    var positionMs by remember(safeUri) { mutableLongStateOf(0L) }
    var bufferedMs by remember(safeUri) { mutableLongStateOf(0L) }
    var durationMs by remember(safeUri) { mutableLongStateOf(0L) }
    var playbackSpeed by remember(safeUri) { mutableFloatStateOf(1f) }
    var videoRatio by remember(safeUri) { mutableFloatStateOf(cachedMediaRatio(safeUri) ?: (16f / 9f)) }
    var inViewport by remember(safeUri) { mutableStateOf(true) }
    // Never give Coil a remote video: it can download the entire file to decode a frame.
    var thumbnailModel by remember(safeUri) { mutableStateOf<Any?>(null) }
    LaunchedEffect(safeUri) {
        VideoPreviewRepository.prepare(context.applicationContext, safeUri)?.let { preview ->
            thumbnailModel = preview.file
            preview.ratio?.let { videoRatio = it }
        }
    }
    val player = remember(safeUri, lifecycleOwner) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(safeUri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = "Couldn't load this video. Check your connection or try again."
                isBuffering = false
                controlsVisible = true
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (handoff.owner === owner) {
                    handoff.positionMs = player.currentPosition.coerceAtLeast(0L)
                    handoff.resumePlaying = playing
                }
                if (!playing) controlsVisible = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING && player.playWhenReady
                val duration = player.duration
                durationMs = if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0L)
                    player.playWhenReady = false
                    positionMs = 0L
                    if (handoff.owner === owner) {
                        handoff.positionMs = 0L
                        handoff.resumePlaying = false
                    }
                    controlsVisible = true
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoRatio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) /
                        videoSize.height.toFloat()
                }
            }

            override fun onRenderedFirstFrame() {
                thumbnailVisible = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        player.playerError?.let(listener::onPlayerError)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (handoff.owner === owner) {
                handoff.positionMs = player.currentPosition.coerceAtLeast(0L)
                handoff.resumePlaying = player.isPlaying
                handoff.owner = null
            }
            player.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(player, handoffOnMount, autoPlayOnMount) {
        if (autoPlayOnMount) {
            handoff.owner = owner
            player.seekTo(handoff.positionMs)
            positionMs = handoff.positionMs
            handoff.resumePlaying = true
            player.play()
        } else if ((handoffOnMount || handoff.positionMs > 0L) && handoff.owner !== owner) {
            handoff.owner = owner
            delay(48)
            player.seekTo(handoff.positionMs)
            positionMs = handoff.positionMs
            if (handoff.resumePlaying) player.play()
        }
    }

    LaunchedEffect(inViewport) {
        if (!inViewport) {
            handoff.positionMs = player.currentPosition.coerceAtLeast(0L)
            handoff.resumePlaying = false
            player.pause()
            onLeaveViewport()
        }
    }

    LaunchedEffect(VideoPlaybackHandoff.detailOverlayActive, handoffOnMount) {
        if (handoffOnMount) return@LaunchedEffect
        if (VideoPlaybackHandoff.detailOverlayActive) {
            if (player.isPlaying || player.playWhenReady) {
                handoff.positionMs = player.currentPosition.coerceAtLeast(0L)
                handoff.resumePlaying = player.isPlaying
                pausedForDetailOverlay = true
                player.pause()
            }
        } else if (pausedForDetailOverlay && handoff.owner === owner && handoff.resumePlaying) {
            pausedForDetailOverlay = false
            player.seekTo(handoff.positionMs)
            player.play()
        }
    }

    LaunchedEffect(handoff.owner) {
        when {
            handoff.owner != null && handoff.owner !== owner -> {
                if (player.isPlaying || player.playWhenReady) {
                    handoff.positionMs = player.currentPosition.coerceAtLeast(0L)
                    handoff.resumePlaying = player.isPlaying
                    waitingForReturn = true
                }
                player.pause()
            }
            handoff.owner == null && waitingForReturn && handoff.resumePlaying -> {
                waitingForReturn = false
                handoff.owner = owner
                player.seekTo(handoff.positionMs)
                positionMs = handoff.positionMs
                player.play()
            }
        }
    }

    LaunchedEffect(player, isPlaying) {
        fun syncProgress() {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (handoff.owner === owner) handoff.positionMs = positionMs
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
            val duration = player.duration
            durationMs = if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
        }
        syncProgress()
        while (isPlaying) {
            delay(250)
            syncProgress()
        }
    }

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(2_500)
            controlsVisible = false
        }
    }

    LaunchedEffect(fullscreen) {
        controlsVisible = true
    }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(fullscreen, landscape, activity) {
        if (!fullscreen || !landscape || activity == null) return@DisposableEffect onDispose { }
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = previousOrientation }
    }

    fun togglePlayback() {
        if (playbackError != null) {
            playbackError = null
            player.prepare()
        }
        controlsVisible = true
        if (player.isPlaying) {
            handoff.positionMs = player.currentPosition.coerceAtLeast(0L)
            handoff.resumePlaying = false
            player.pause()
        } else {
            if (handoff.owner !== owner) {
                handoff.owner = owner
                player.seekTo(handoff.positionMs)
                positionMs = handoff.positionMs
            }
            handoff.resumePlaying = true
            player.play()
        }
    }

    if (!fullscreen) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = if (compact) 8.dp else 10.dp)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    val next = bounds.bottom > 0f && bounds.top < rootView.height.toFloat()
                    if (next != inViewport) inViewport = next
                },
        ) {
            val safeRatio = videoRatio.coerceIn(0.2f, 4f)
            val playerHeight = maxWidth / safeRatio
            FeedVideoSurface(
                videoUri = safeUri,
                errorMessage = playbackError,
                onRetry = ::togglePlayback,
                player = player,
                thumbnailModel = thumbnailModel,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                thumbnailVisible = thumbnailVisible,
                controlsVisible = controlsVisible,
                positionMs = positionMs,
                bufferedMs = bufferedMs,
                durationMs = durationMs,
                fullscreen = false,
                landscape = false,
                playbackSpeed = playbackSpeed,
                onToggleControls = { controlsVisible = !controlsVisible },
                onTogglePlayback = ::togglePlayback,
                onSeek = { target ->
                    player.seekTo(target)
                    positionMs = target
                    if (handoff.owner === owner) handoff.positionMs = target
                    controlsVisible = true
                },
                onToggleFullscreen = { fullscreen = true },
                onToggleLandscape = {},
                onCycleSpeed = {
                    playbackSpeed = when (playbackSpeed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                    player.setPlaybackSpeed(playbackSpeed)
                    controlsVisible = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playerHeight)
                    .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp)),
            )
        }
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = {
                landscape = false
                fullscreen = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            FullscreenSystemUi()
            FeedVideoSurface(
                videoUri = safeUri,
                errorMessage = playbackError,
                onRetry = ::togglePlayback,
                player = player,
                thumbnailModel = thumbnailModel,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                thumbnailVisible = thumbnailVisible,
                controlsVisible = controlsVisible,
                positionMs = positionMs,
                bufferedMs = bufferedMs,
                durationMs = durationMs,
                fullscreen = true,
                landscape = landscape,
                playbackSpeed = playbackSpeed,
                onToggleControls = { controlsVisible = !controlsVisible },
                onTogglePlayback = ::togglePlayback,
                onSeek = { target ->
                    player.seekTo(target)
                    positionMs = target
                    if (handoff.owner === owner) handoff.positionMs = target
                    controlsVisible = true
                },
                onToggleFullscreen = {
                    landscape = false
                    fullscreen = false
                },
                onToggleLandscape = { landscape = !landscape },
                onCycleSpeed = {
                    playbackSpeed = when (playbackSpeed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                    player.setPlaybackSpeed(playbackSpeed)
                    controlsVisible = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
