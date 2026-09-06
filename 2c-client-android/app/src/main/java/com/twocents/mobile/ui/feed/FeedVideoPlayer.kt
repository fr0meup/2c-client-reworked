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
import com.twocents.mobile.ui.settings.InteractionPreferences
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
internal fun FeedVideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    handoffOnMount: Boolean = false,
) {
    val safeUri = remember(uri) {
        Uri.parse(uri).takeIf { it.scheme?.lowercase() in setOf("https", "http", "content", "file") }?.toString()
    } ?: return
    val context = LocalContext.current
    val rootView = LocalView.current
    val autoplayEnabled = InteractionPreferences.autoPlayVideos(context)
    val wifiOnlyMedia = InteractionPreferences.wifiOnlyMedia(context)
    val handoff = remember(safeUri) { VideoPlaybackHandoff.state(safeUri) }
    var preview by remember(safeUri) { mutableStateOf<CachedVideoPreview?>(null) }
    var activated by remember(safeUri) {
        mutableStateOf(handoffOnMount || handoff.positionMs > 0L || handoff.resumePlaying)
    }
    var autoPlay by remember(safeUri) { mutableStateOf(false) }
    LaunchedEffect(safeUri, wifiOnlyMedia) {
        if (InteractionPreferences.automaticMediaAllowed(context)) {
            preview = VideoPreviewRepository.prepare(context.applicationContext, safeUri)
        }
    }

    if (activated) {
        ActiveFeedVideoPlayer(
            uri = safeUri,
            modifier = modifier,
            compact = compact,
            handoffOnMount = handoffOnMount,
            autoPlayOnMount = autoPlay,
            onLeaveViewport = {
                autoPlay = false
                activated = false
            },
        )
    } else {
        BoxWithConstraints(modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 10.dp)) {
            val ratio = (preview?.ratio ?: cachedMediaRatio(safeUri) ?: (16f / 9f)).coerceIn(.2f, 4f)
            Box(
                Modifier.fillMaxWidth().height(maxWidth / ratio)
                    .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
                    .background(MediaBackground)
                    .onGloballyPositioned { coordinates ->
                        if (!autoplayEnabled || activated || !InteractionPreferences.automaticMediaAllowed(context)) return@onGloballyPositioned
                        val bounds = coordinates.boundsInWindow()
                        val visibleHeight = minOf(bounds.bottom, rootView.height.toFloat()) - maxOf(bounds.top, 0f)
                        if (visibleHeight >= bounds.height * .6f) {
                            autoPlay = true
                            activated = true
                        }
                    }
                    .clickable {
                        autoPlay = true
                        activated = true
                    },
            ) {
                preview?.file?.let { file ->
                    AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } ?: CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).align(Alignment.Center),
                    color = Color.White.copy(alpha = .42f),
                    strokeWidth = 2.dp,
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape)
                        .background(Color(0xFF11100D).copy(alpha = .76f))
                        .border(1.dp, Color.White.copy(alpha = .14f), CircleShape)
                        .clickable(interactionSource = interaction, indication = null) {
                            autoPlay = true
                            activated = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play video", tint = Color.White.copy(alpha = .92f), modifier = Modifier.size(27.dp))
                }
            }
        }
    }
}
