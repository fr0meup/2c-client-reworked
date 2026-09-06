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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LightboxAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(Modifier.size(32.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(17.dp))
    }
}

@Composable
internal fun LightboxDivider() {
    Box(Modifier.width(1.dp).height(14.dp).background(Color.White.copy(alpha = 0.12f)))
}

@Composable
internal fun LightboxArrow(
    left: Boolean,
    alpha: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (left) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
            if (left) "Previous image" else "Next image",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun LightboxSlide(
    uri: String,
    active: Boolean,
    initial: Boolean,
    progress: Float,
    originRect: ImageOriginRect?,
    onZoomChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val initialScale = if (originRect != null && initial) {
            max(originRect.width / screenWidthPx, originRect.height / screenHeightPx).coerceAtLeast(0.01f)
        } else {
            0.85f
        }
        val initialX = if (originRect != null && initial) {
            originRect.x + originRect.width / 2f - screenWidthPx / 2f
        } else 0f
        val initialY = if (originRect != null && initial) {
            originRect.y + originRect.height / 2f - screenHeightPx / 2f
        } else 0f
        val animatedScale = initialScale + (1f - initialScale) * progress

        ZoomableLightboxImage(
            uri = uri,
            active = active,
            onZoomChange = onZoomChange,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp)
                .graphicsLayer {
                    translationX = initialX * (1f - progress)
                    translationY = initialY * (1f - progress)
                    scaleX = animatedScale
                    scaleY = animatedScale
                    alpha = if (originRect == null || !initial) progress else 1f
                    shape = RoundedCornerShape(6.dp)
                    clip = progress < 0.999f
                },
        )
    }
}

@Composable
private fun ZoomableLightboxImage(
    uri: String,
    active: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var ratio by remember(uri) { mutableStateOf(cachedMediaRatio(uri)) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }

    fun fittedSize(): Pair<Float, Float> {
        val maxW = containerSize.width.toFloat()
        val maxH = (containerSize.height - with(density) { 144.dp.roundToPx() }).coerceAtLeast(1).toFloat()
        val imageRatio = ratio ?: (maxW / maxH)
        return if (imageRatio > maxW / maxH) {
            maxW to maxW / imageRatio
        } else {
            maxH * imageRatio to maxH
        }
    }

    fun clampedOffset(candidate: Offset, atScale: Float = scale): Offset {
        val (fitW, fitH) = fittedSize()
        val boundX = max(0f, fitW * atScale - containerSize.width) / 2f
        val boundY = max(0f, fitH * atScale - containerSize.height) / 2f
        return Offset(candidate.x.coerceIn(-boundX, boundX), candidate.y.coerceIn(-boundY, boundY))
    }

    fun animateZoom(targetScale: Float, focal: Offset) {
        val startScale = scale
        val startOffset = offset
        val factor = targetScale / startScale
        val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
        val relativeFocal = focal - center
        val targetOffset = clampedOffset(
            startOffset * factor + relativeFocal * (1f - factor),
            targetScale,
        )
        scope.launch {
            animate(0f, 1f, animationSpec = tween(250)) { fraction, _ ->
                scale = startScale + (targetScale - startScale) * fraction
                offset = clampedOffset(startOffset + (targetOffset - startOffset) * fraction)
                onZoomChange(scale > 1.05f)
            }
        }
    }

    LaunchedEffect(active) {
        if (!active) {
            tapJob?.cancel()
            scale = 1f
            offset = Offset.Zero
            onZoomChange(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose { tapJob?.cancel() }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(uri, active, containerSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedAt = down.uptimeMillis
                    val startPosition = down.position
                    var lastPosition = down.position
                    var totalMovement = Offset.Zero
                    var usedMultiTouch = false
                    var endedAt = startedAt
                    var velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var hadMultiplePointersLastFrame = false

                    var gestureActive = true
                    while (gestureActive) {
                        // Read before the pager so a zoomed image owns the drag
                        // immediately and follows the finger without a dead zone.
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            usedMultiTouch = true
                            hadMultiplePointersLastFrame = true
                            val oldScale = scale
                            val newScale = (oldScale * event.calculateZoom()).coerceIn(1f, 6f)
                            val factor = newScale / oldScale
                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                            val focal = event.calculateCentroid(useCurrent = true) - center
                            val pan = event.calculatePan()
                            scale = newScale
                            offset = clampedOffset(offset * factor + focal * (1f - factor) + pan, newScale)
                            onZoomChange(newScale > 1.05f)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        } else {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                if (hadMultiplePointersLastFrame && change.pressed) {
                                    // Continue from the remaining finger's current position.
                                    // Reusing the pre-pinch coordinate causes the visible snap
                                    // when either finger is lifted.
                                    lastPosition = change.position
                                    velocityTracker = VelocityTracker().also {
                                        it.addPosition(change.uptimeMillis, change.position)
                                    }
                                    hadMultiplePointersLastFrame = false
                                    change.consume()
                                    continue
                                }
                                val movement = change.position - lastPosition
                                totalMovement += movement
                                lastPosition = change.position
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (scale > 1.0001f && change.pressed) {
                                    offset = clampedOffset(offset + movement)
                                    change.consume()
                                }
                            }
                        }
                        endedAt = event.changes.maxOfOrNull { it.uptimeMillis } ?: endedAt
                        gestureActive = event.changes.any { it.pressed }
                    }

                    val duration = endedAt - startedAt
                    val velocity = velocityTracker.calculateVelocity()
                    val movedDistance = (lastPosition - startPosition).getDistance()
                    val (fitW, fitH) = fittedSize()
                    val boundY = max(0f, fitH * scale - containerSize.height) / 2f
                    val atVerticalEdge = abs(abs(offset.y) - boundY) < 1.5f
                    val verticalSwipe = duration <= 175L &&
                        abs(lastPosition.y - startPosition.y) >= with(density) { 20.dp.toPx() } &&
                        abs(velocity.y) >= with(density) { 500.dp.toPx() } &&
                        abs(velocity.y) > abs(velocity.x) &&
                        atVerticalEdge

                    if (!usedMultiTouch && verticalSwipe) {
                        onDismiss()
                    } else if (!usedMultiTouch && movedDistance < with(density) { 8.dp.toPx() }) {
                        val now = endedAt
                        if (now - lastTapTime <= 300L) {
                            tapJob?.cancel()
                            lastTapTime = 0L
                            val target = if (scale >= 6f * 0.8f) 1f else 6f
                            animateZoom(target, lastPosition)
                        } else {
                            lastTapTime = now
                            tapJob?.cancel()
                            tapJob = scope.launch {
                                delay(260)
                                lastTapTime = 0L
                                onDismiss()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            val maxImageHeight = maxHeight - 144.dp
            val imageRatio = ratio
            val imageWidth: androidx.compose.ui.unit.Dp
            val imageHeight: androidx.compose.ui.unit.Dp
            if (imageRatio == null || imageRatio <= 0f) {
                imageWidth = maxWidth
                imageHeight = maxImageHeight
            } else if (imageRatio > maxWidth / maxImageHeight) {
                imageWidth = maxWidth
                imageHeight = maxWidth / imageRatio
            } else {
                imageHeight = maxImageHeight
                imageWidth = maxImageHeight * imageRatio
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .memoryCacheKey(uri)
                    .diskCacheKey(uri)
                    .size(Size.ORIGINAL)
                    .build(),
                contentDescription = "Full screen image",
                contentScale = ContentScale.Fit,
                onSuccess = { success ->
                    val image = success.result.image
                    if (image.width > 0 && image.height > 0) {
                        val measuredRatio = image.width.toFloat() / image.height.toFloat()
                        cacheMediaRatio(uri, measuredRatio)
                        ratio = measuredRatio
                    }
                },
                modifier = Modifier.width(imageWidth).height(imageHeight),
            )
        }
    }
}

