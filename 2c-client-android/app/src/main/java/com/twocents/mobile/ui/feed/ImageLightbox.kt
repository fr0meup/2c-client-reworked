package com.twocents.mobile.ui.feed

import com.twocents.mobile.core.platform.ShareActions

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
/** Full-screen pager shell; each page owns zoom/pan arbitration in ImageLightboxComponents. */
@Composable
internal fun ImageLightbox(
    images: List<String>,
    initialIndex: Int,
    originRect: ImageOriginRect?,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    val safeInitialIndex = initialIndex.coerceIn(images.indices)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val pagerState = rememberPagerState(initialPage = safeInitialIndex, pageCount = { images.size })
    var isZoomed by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(120, easing = EaseIn))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(120, easing = EaseOut))
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalContext.current as? android.app.Activity)?.window
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: dialogWindow
            window?.let {
                it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                it.setWindowAnimations(0)
                it.statusBarColor = AndroidColor.TRANSPARENT
                it.navigationBarColor = AndroidColor.TRANSPARENT
                WindowCompat.setDecorFitsSystemWindows(it, false)
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        BackHandler(onBack = ::close)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080705).copy(alpha = 0.94f * progress.value)),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isZoomed,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                LightboxSlide(
                    uri = images[page],
                    active = page == pagerState.currentPage,
                    initial = page == safeInitialIndex,
                    progress = progress.value,
                    originRect = originRect,
                    onZoomChange = { if (page == pagerState.currentPage) isZoomed = it },
                    onDismiss = ::close,
                )
            }

            val controlsAlpha = ((progress.value - 0.75f) / 0.25f).coerceIn(0f, 1f)
            if (images.size > 1 && pagerState.currentPage > 0) {
                LightboxArrow(
                    left = true,
                    alpha = controlsAlpha,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                ) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        isZoomed = false
                    }
                }
            }
            if (images.size > 1 && pagerState.currentPage < images.lastIndex) {
                LightboxArrow(
                    left = false,
                    alpha = controlsAlpha,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                ) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        isZoomed = false
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    .graphicsLayer { alpha = controlsAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (images.size > 1) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF0F0E0A).copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${pagerState.currentPage + 1} / ${images.size}",
                            color = Color(0xFFC8A44D),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF0F0E0A).copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    LightboxAction(Icons.Outlined.Share, "Share") {
                        shareImageUrl(context, images[pagerState.currentPage])
                    }
                    LightboxDivider()
                    LightboxAction(Icons.Outlined.Download, "Download") {
                        downloadImage(context, images[pagerState.currentPage])
                    }
                    LightboxDivider()
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = ::close),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private fun shareImageUrl(context: Context, uri: String) {
    ShareActions.shareText(context, uri)
}

private fun downloadImage(context: Context, uri: String) {
    ShareActions.downloadImage(context, uri)
}


