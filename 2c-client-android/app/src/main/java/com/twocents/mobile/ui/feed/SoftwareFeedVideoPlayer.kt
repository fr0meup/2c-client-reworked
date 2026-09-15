package com.twocents.mobile.ui.feed

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Only allocated after platform decoding fails. VLC bundles a software HEVC decoder,
 * unlike Media3's decoder fallback, which can only choose installed system codecs. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun SoftwareFeedVideoPlayer(
    uri: String,
    platformPlayer: ExoPlayer?,
    modifier: Modifier,
    compact: Boolean,
    ratio: Float,
    autoPlay: Boolean,
    handoffOnMount: Boolean,
    onLeaveViewport: () -> Unit,
) {
    val context = LocalContext.current
    val root = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handoff = remember(uri) { VideoPlaybackHandoff.state(uri) }
    val owner = remember(uri) { Any() }
    val engine = remember(uri) { LibVLC(context.applicationContext, arrayListOf("--no-video-title-show")) }
    val player = remember(engine) { MediaPlayer(engine) }
    val scope = rememberCoroutineScope()
    var videoRatio by remember(uri) { mutableFloatStateOf(cachedMediaRatio(uri) ?: ratio) }
    var pauseAfterFrame by remember(uri) { mutableStateOf(!autoPlay) }
    var captured by remember(uri) { mutableStateOf(VideoPreviewRepository.peek(uri) != null) }
    var playing by remember(uri) { mutableStateOf(false) }
    var buffering by remember(uri) { mutableStateOf(false) }
    var controls by remember(uri) { mutableStateOf(true) }
    var fullscreen by remember(uri) { mutableStateOf(false) }
    var landscape by remember(uri) { mutableStateOf(false) }
    val activity = context.findActivity()
    DisposableEffect(fullscreen, landscape, activity) {
        if (!fullscreen || !landscape || activity == null) return@DisposableEffect onDispose { }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = previous }
    }
    var position by remember(uri) { mutableLongStateOf(handoff.positionMs) }
    var duration by remember(uri) { mutableLongStateOf(0L) }
    var speed by remember(uri) { mutableFloatStateOf(1f) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var attachedView by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var started by remember(uri) { mutableStateOf(false) }
    val leaveViewport by rememberUpdatedState(onLeaveViewport)

    DisposableEffect(player, lifecycle) {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true; buffering = false
                    player.currentVideoTrack?.let { track ->
                        if (track.width > 0 && track.height > 0) {
                            val sar = if (track.sarDen > 0) track.sarNum.toFloat() / track.sarDen else 1f
                            val raw = track.width * sar / track.height
                            val rotated = track.orientation == org.videolan.libvlc.interfaces.IMedia.VideoTrack.Orientation.LeftBottom ||
                                track.orientation == org.videolan.libvlc.interfaces.IMedia.VideoTrack.Orientation.RightTop
                            videoRatio = if (rotated) 1f / raw else raw
                            cacheMediaRatio(uri, videoRatio)
                        }
                    }
                }
                MediaPlayer.Event.Paused -> { playing = false; controls = true }
                MediaPlayer.Event.EndReached -> { playing = false; controls = true; position = 0L; started = false; handoff.resumePlaying = false }
                MediaPlayer.Event.Buffering -> buffering = event.buffering < 100f
                MediaPlayer.Event.EncounteredError -> { error = "Couldn't play this video."; playing = false; buffering = false }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                player.pause()
                handoff.resumePlaying = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            if (handoff.owner === owner) {
                handoff.positionMs = position
                handoff.owner = null
            }
            lifecycle.removeObserver(observer)
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            engine.release()
        }
    }
    LaunchedEffect(player) {
        fun texture(view: android.view.View?): android.view.TextureView? {
            if (view is android.view.TextureView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) texture(view.getChildAt(i))?.let { return it }
            return null
        }
        while (true) {
            if (playing && (!captured || pauseAfterFrame) && player.time > 0) {
                val bitmap = texture(attachedView)?.bitmap
                if (bitmap != null) {
                    if (!captured) { captured = true; scope.launch { runCatching { VideoPreviewRepository.saveFrame(context.applicationContext, uri, bitmap, videoRatio) } } }
                    else bitmap.recycle()
                    if (pauseAfterFrame) { player.pause(); playing = false; handoff.resumePlaying = false; pauseAfterFrame = false; player.volume = 100 }
                }
            }
            if (started) position = player.time.coerceAtLeast(0L)
            duration = player.length.coerceAtLeast(0L)
            if (playing && handoff.owner === owner) { handoff.positionMs = position; handoff.resumePlaying = true }
            delay(250)
        }
    }
    LaunchedEffect(handoff.owner, VideoPlaybackHandoff.detailOverlayActive) {
        if ((handoff.owner != null && handoff.owner !== owner) ||
            (VideoPlaybackHandoff.detailOverlayActive && !handoffOnMount)) player.pause()
    }
    LaunchedEffect(playing, controls) {
        if (playing && controls) { delay(2_500); controls = false }
    }
    fun start() {
        error = null
        buffering = true
        val media = Media(engine, Uri.parse(uri))
        media.setHWDecoderEnabled(false, false)
        media.addOption(":avcodec-threads=0")
        media.addOption(":network-caching=1000")
        media.addOption(":start-time=${position / 1000.0}")
        player.media = media
        media.release()
        handoff.owner = owner
        player.volume = if (pauseAfterFrame) 0 else 100
        player.play()
        started = true
    }
    @Composable
    fun surface(surfaceModifier: Modifier) {
        FeedVideoSurface(
            videoUri = uri, player = platformPlayer, thumbnailModel = null,
            isPlaying = playing, isBuffering = buffering, thumbnailVisible = false,
            controlsVisible = controls, positionMs = position, bufferedMs = position,
            durationMs = duration, fullscreen = fullscreen, landscape = landscape,
            playbackSpeed = speed, onToggleControls = { if (!fullscreen) fullscreen = true else controls = !controls },
            onTogglePlayback = {
                controls = true
                pauseAfterFrame = false
                player.volume = 100
                if (playing) { player.pause(); handoff.resumePlaying = false }
                else if (!started || error != null) start() else { handoff.owner = owner; player.play() }
            },
            onSeek = { player.setTime(it); position = it },
            onToggleFullscreen = { landscape = false; fullscreen = !fullscreen },
            onToggleLandscape = { landscape = !landscape },
            onCycleSpeed = { speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }; player.setRate(speed) },
            modifier = surfaceModifier, errorMessage = error, onRetry = { start() },
            videoContent = {
                AndroidView(
                    factory = { VLCVideoLayout(it).apply {
                        // Recreate video output after the old window detaches. VLC cannot
                        // safely migrate a running output between inline and dialog surfaces.
                        if (started) {
                            position = player.time.coerceAtLeast(0L)
                            pauseAfterFrame = !playing
                            player.stop()
                        }
                        player.detachViews()
                        player.attachViews(this, null, false, true)
                        attachedView = this
                        start()
                    } },
                    onRelease = { view ->
                        if (attachedView === view) {
                            runCatching { player.detachViews() }
                            attachedView = null
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
    // Keep a stable inline placeholder while the same player is attached to the dialog.
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 10.dp)
        .onGloballyPositioned {
            if (!fullscreen) {
                val bounds = it.boundsInWindow()
                if (bounds.bottom <= 0f || bounds.top >= root.height.toFloat()) {
                    player.pause()
                    handoff.resumePlaying = false
                    leaveViewport()
                }
            }
        }) {
        val size = Modifier.fillMaxWidth().height(maxWidth / videoRatio.coerceIn(.2f, 4f))
            .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
        if (!fullscreen) surface(size) else Spacer(size)
    }
    if (fullscreen) Dialog(onDismissRequest = { landscape = false; fullscreen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullscreenSystemUi()
        surface(Modifier.fillMaxSize())
    }
}
