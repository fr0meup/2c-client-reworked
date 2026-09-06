package com.twocents.mobile.ui.feed

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.twocents.mobile.ui.compose.GifLibrary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal data class ImageOriginRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

internal fun Rect.toImageOriginRect() = ImageOriginRect(left, top, width, height)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommentImageAttachments(images: List<String>) {
    if (images.isEmpty()) return
    var lightboxIndex by remember(images) { mutableStateOf<Int?>(null) }
    var originRect by remember(images) { mutableStateOf<ImageOriginRect?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        val availableWidth = maxWidth
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            images.forEachIndexed { index, uri ->
                CommentImageAttachment(
                    uri = uri,
                    availableWidth = availableWidth,
                    hidden = lightboxIndex == index,
                    onClick = { rect ->
                        originRect = rect
                        lightboxIndex = index
                    },
                )
            }
        }
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
private fun CommentImageAttachment(
    uri: String,
    availableWidth: androidx.compose.ui.unit.Dp,
    hidden: Boolean,
    onClick: (ImageOriginRect) -> Unit,
) {
    val context = LocalContext.current
    var ratio by remember(uri) { mutableStateOf(cachedMediaRatio(uri)) }
    var bounds by remember(uri) { mutableStateOf<Rect?>(null) }
    var saved by remember(uri) { mutableStateOf(uri in GifLibrary.saved(context)) }
    val maxWidth = 340.dp
    val maxHeight = 280.dp
    val rawWidth: androidx.compose.ui.unit.Dp
    val rawHeight: androidx.compose.ui.unit.Dp
    if (ratio != null && ratio!! > 0f) {
        if (ratio!! >= 340f / 280f) {
            rawWidth = maxWidth
            rawHeight = maxOf(80.dp, 340.dp / ratio!!)
        } else {
            rawHeight = maxHeight
            rawWidth = maxOf(80.dp, 280.dp * ratio!!)
        }
    } else {
        rawWidth = 240.dp
        rawHeight = 160.dp
    }
    val width = minOf(rawWidth, availableWidth)
    val height = if (width < rawWidth) rawHeight * (width / rawWidth) else rawHeight

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.025f))
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clickable { bounds?.let { onClick(it.toImageOriginRect()) } },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .memoryCacheKey(uri)
                .diskCacheKey(uri)
                .build(),
            contentDescription = "Comment attachment",
            contentScale = ContentScale.Fit,
            onSuccess = { success ->
                val image = success.result.image
                if (image.width > 0 && image.height > 0 && ratio == null) {
                    val measuredRatio = image.width.toFloat() / image.height.toFloat()
                    cacheMediaRatio(uri, measuredRatio)
                    ratio = measuredRatio
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .7f))
                .clickable {
                    saved = GifLibrary.toggleSaved(context, uri)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (saved) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                contentDescription = if (saved) "Remove from saved media" else "Save media",
                tint = if (saved) Color(0xFFC8A44D) else Color.White.copy(alpha = .85f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}


