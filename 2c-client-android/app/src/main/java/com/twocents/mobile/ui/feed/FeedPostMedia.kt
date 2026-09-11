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
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Shared failure state for still images and GIFs. LinkPreviewCards remains in
 * the parent content flow, so a URL-backed image naturally falls back to its
 * tappable link card instead of leaving a silent black media canvas.
 */
@Composable
internal fun MediaUnavailableSurface(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MediaBackground)
            .clickable(onClick = {})
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Couldn't load this image. Check your connection or try again. It may no longer be available.",
            color = Color.White.copy(alpha = .78f), fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        onRetry?.let { retry ->
            Text("Retry", color = Color(0xFFC7A653), fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = retry).padding(12.dp))
        }
    }
}

/** Routes a post's media to a single-image surface or equal-height gallery. */
@Composable
internal fun FeedPostMedia(
    images: List<String>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    preserveFullImage: Boolean = false,
) {
    if (images.isEmpty()) return
    var lightboxIndex by remember(images) { mutableStateOf<Int?>(null) }
    var originRect by remember(images) { mutableStateOf<ImageOriginRect?>(null) }
    if (images.size == 1) {
        FeedSingleImage(
            uri = images.first(),
            compact = compact,
            modifier = modifier,
            hidden = lightboxIndex == 0,
            preserveFullImage = preserveFullImage,
            onClick = { rect ->
                originRect = rect
                lightboxIndex = 0
            },
        )
    } else {
        FeedImageGallery(
            images = images,
            compact = compact,
            modifier = modifier,
            hiddenIndex = lightboxIndex,
            preserveFullImage = preserveFullImage,
            onClick = { index, rect ->
                originRect = rect
                lightboxIndex = index
            },
        )
    }
    lightboxIndex?.let { index ->
        ImageLightbox(
            images = images,
            initialIndex = index,
            originRect = originRect,
            onDismiss = {
                lightboxIndex = null
                originRect = null
            },
        )
    }
}

@Composable
private fun FeedSingleImage(
    uri: String,
    compact: Boolean,
    modifier: Modifier,
    hidden: Boolean,
    preserveFullImage: Boolean,
    onClick: (ImageOriginRect) -> Unit,
) {
    val context = LocalContext.current
    var ratio by remember(uri) { mutableFloatStateOf(synchronized(MediaRatios) { MediaRatios[uri] } ?: 4f / 3f) }
    var bounds by remember(uri) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (compact) 8.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val renderHeight: Dp
        val renderWidth: Dp
        if (compact && !preserveFullImage) {
            renderWidth = maxWidth
            renderHeight = 180.dp
        } else {
            val naturalHeight = maxWidth / ratio.coerceAtLeast(0.2f)
            renderHeight = if (preserveFullImage) naturalHeight else minOf(380.dp, if (ratio >= 1.8f) naturalHeight else maxOf(160.dp, naturalHeight))
            renderWidth = if (preserveFullImage) maxWidth else minOf(maxWidth, renderHeight * ratio)
        }
        if (failed) {
            MediaUnavailableSurface(Modifier.width(renderWidth).height(renderHeight)) { failed = false }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .memoryCacheKey(uri)
                    .diskCacheKey(uri)
                    .build(),
                contentDescription = null,
                contentScale = if (compact && !preserveFullImage) ContentScale.Crop else ContentScale.Fit,
                onSuccess = { success ->
                    val image = success.result.image
                    if (image.width > 0 && image.height > 0) {
                        val nextRatio = image.width.toFloat() / image.height.toFloat()
                        synchronized(MediaRatios) { MediaRatios[uri] = nextRatio }
                        ratio = nextRatio
                    }
                },
                onError = { failed = true },
                modifier = Modifier
                    .width(renderWidth)
                    .height(renderHeight)
                    .graphicsLayer { alpha = if (hidden) 0f else 1f }
                    .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
                    .background(MediaBackground)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .clickable { bounds?.let { onClick(it.toImageOriginRect()) } },
            )
        }
    }
}

@Composable
private fun FeedImageGallery(
    images: List<String>,
    compact: Boolean,
    modifier: Modifier,
    hiddenIndex: Int?,
    preserveFullImage: Boolean,
    onClick: (Int, ImageOriginRect) -> Unit,
) {
    val listState = rememberLazyListState()
    val ratios = remember(images) {
        androidx.compose.runtime.mutableStateMapOf<String, Float>().apply {
            synchronized(MediaRatios) { images.forEach { uri -> MediaRatios[uri]?.let { put(uri, it) } } }
        }
    }
    val visibleIndices by remember(listState, images) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val viewportStart = layout.viewportStartOffset
            val viewportEnd = layout.viewportEndOffset
            val visible = layout.visibleItemsInfo
            val fullyVisible = visible.filter { item ->
                val overlap = (minOf(item.offset + item.size, viewportEnd) - maxOf(item.offset, viewportStart))
                    .coerceAtLeast(0)
                overlap >= item.size - 3 || overlap.toFloat() / item.size.coerceAtLeast(1) >= 0.96f
            }.map { it.index.coerceIn(images.indices) }
            if (fullyVisible.isNotEmpty()) {
                fullyVisible
            } else {
                listOfNotNull(
                    visible.maxByOrNull { item ->
                        (minOf(item.offset + item.size, viewportEnd) - maxOf(item.offset, viewportStart))
                            .coerceAtLeast(0)
                    }?.index?.coerceIn(images.indices),
                )
            }
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (compact) 8.dp else 10.dp),
    ) {
        // Only enlarge an underfilled gallery once actual ratios are known.
        // One common factor preserves all relative image sizes; gaps stay unchanged.
        val gap = 8.dp
        val baseHeight = if (compact) 160.dp else 280.dp
        val imageWidth = images.sumOf { (baseHeight.value * (ratios[it] ?: 1f).coerceIn(.2f, 5f)).toDouble() }.toFloat()
        val gapsWidth = gap.value * (images.size - 1)
        val scale = if (!preserveFullImage && images.all { it in ratios } && imageWidth + gapsWidth < maxWidth.value) {
            (maxWidth.value + gap.value - gapsWidth) / imageWidth
        } else 1f
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(images, key = { index, uri -> "$index:$uri" }) { index, uri ->
                GalleryImage(
                    uri = uri,
                    compact = compact,
                    preserveFullImage = preserveFullImage,
                    fullWidth = maxWidth,
                    scale = scale,
                    onRatio = { next -> if (ratios[uri] != next) ratios[uri] = next },
                    hidden = hiddenIndex == index,
                    onClick = { rect -> onClick(index, rect) },
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(CircleShape)
                .background(MediaBackground.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            images.indices.forEach { index ->
                val active = index in visibleIndices
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 15.dp)
                        .height(15.dp)
                        .clip(CircleShape)
                        .background(if (active) Color(0xFFC8A44D) else Color.Transparent)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = if (active) Color(0xFF0F0E0A) else Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryImage(
    uri: String,
    compact: Boolean,
    preserveFullImage: Boolean,
    fullWidth: Dp,
    scale: Float,
    onRatio: (Float) -> Unit,
    hidden: Boolean,
    onClick: (ImageOriginRect) -> Unit,
) {
    val context = LocalContext.current
    var ratio by remember(uri) { mutableFloatStateOf(synchronized(MediaRatios) { MediaRatios[uri] } ?: 1f) }
    var bounds by remember(uri) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    val width = if (preserveFullImage) fullWidth else (if (compact) 160.dp else 280.dp) * ratio.coerceIn(0.2f, 5f) * scale
    val height = if (preserveFullImage) fullWidth / ratio.coerceAtLeast(.2f) else (if (compact) 160.dp else 280.dp) * scale
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .clip(shape)
            .background(MediaBackground)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clickable { bounds?.let { onClick(it.toImageOriginRect()) } },
    ) {
        if (failed) {
            MediaUnavailableSurface(Modifier.fillMaxSize()) { failed = false }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context).data(uri).memoryCacheKey(uri).diskCacheKey(uri).build(),
                contentDescription = null,
                contentScale = if (preserveFullImage) ContentScale.Fit else ContentScale.FillBounds,
                onSuccess = { success ->
                    val image = success.result.image
                    if (image.width > 0 && image.height > 0) {
                        val next = image.width.toFloat() / image.height.toFloat()
                        synchronized(MediaRatios) { MediaRatios[uri] = next }
                        ratio = next
                        onRatio(next)
                    }
                },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun FeedGif(uri: String) {
    val context = LocalContext.current
    var failed by remember(uri) { mutableStateOf(false) }
    val modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(220.dp)
    if (failed) {
        MediaUnavailableSurface(modifier) { failed = false }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context).data(uri).memoryCacheKey(uri).diskCacheKey(uri).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MediaBackground),
        )
    }
}
