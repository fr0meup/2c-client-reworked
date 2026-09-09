package com.twocents.mobile.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.max

const val REFRESH_RESTING_OFFSET_DP = 56
private const val REFRESH_TRIGGER_OFFSET_DP = 31
private const val PULL_RESISTANCE = 0.72f
private const val REFRESH_TIMEOUT_MS = 12_000L

private val RefreshGold = Color(0xFFC8A44D)
private val RefreshText = Color.White.copy(alpha = 0.52f)

/**
 * State for the feed pull gesture. Finger tracking uses one scalar state value;
 * spring animations are layered on top only when the gesture settles.
 */
@Stable
class PullToRefreshState internal constructor() {
    internal var pullOffsetPx by mutableFloatStateOf(0f)
    internal var refreshRequestVersion by mutableIntStateOf(0)
    internal var cancelVersion by mutableIntStateOf(0)
    var isRefreshing by mutableStateOf(false)
        internal set

    fun requestRefresh() {
        if (!isRefreshing) refreshRequestVersion += 1
    }

    fun cancelRefresh() {
        cancelVersion += 1
        isRefreshing = false
        pullOffsetPx = 0f
    }
}

@Composable
fun rememberPullToRefreshState(): PullToRefreshState = remember { PullToRefreshState() }

/**
 * Lightweight pull-to-refresh container for the feed viewport.
 *
 * The gesture only consumes downward movement while the content is at its
 * resting position. That leaves future scrollable feed content free to handle
 * normal upward scrolling, while the current shell can use this component
 * without a special list implementation.
 */
@Composable
fun PullToRefreshContainer(
    state: PullToRefreshState,
    enabled: Boolean,
    onRefresh: suspend () -> Boolean,
    modifier: Modifier = Modifier,
    indicatorTopOffset: Dp = 0.dp,
    fromBottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motion = remember { Animatable(0f) }
    val triggerOffset = with(density) { REFRESH_TRIGGER_OFFSET_DP.dp.toPx() }
    val restingOffset = with(density) { REFRESH_RESTING_OFFSET_DP.dp.toPx() }
    val updatedOnRefresh = androidx.compose.runtime.rememberUpdatedState(onRefresh)
    val updatedEnabled = androidx.compose.runtime.rememberUpdatedState(enabled)
    var isPulling by remember { mutableStateOf(false) }
    var dots by remember { mutableIntStateOf(0) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(state.isRefreshing) {
        if (state.isRefreshing) {
            dots = 0
            while (true) {
                delay(220)
                dots = (dots + 1) % 4
            }
        } else {
            dots = 0
        }
    }

    fun startRefresh() {
        if (state.isRefreshing) return
        resultMessage = null
        state.isRefreshing = true
        val runVersion = state.cancelVersion
        scope.launch {
            motion.snapTo(state.pullOffsetPx)
            motion.animateTo(
                targetValue = restingOffset,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
            ) { state.pullOffsetPx = value }
        }
        refreshJob?.cancel()
        refreshJob = scope.launch {
            var holdResultMs = 350L
            try {
                val succeeded = withTimeout(REFRESH_TIMEOUT_MS) { updatedOnRefresh.value() }
                if (runVersion != state.cancelVersion) return@launch
                resultMessage = if (succeeded) "Refreshed" else if (com.twocents.mobile.ApiRateLimitNotice.active.value) "Rate limited — try again later" else "Refresh failed"
                if (!succeeded) holdResultMs = 1_350L
            } catch (_: TimeoutCancellationException) {
                resultMessage = "Refresh timed out"
                holdResultMs = 1_500L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                resultMessage = if (com.twocents.mobile.ApiRateLimitNotice.active.value) "Rate limited — try again later" else "Refresh failed"
                holdResultMs = 1_350L
            } finally {
                if (runVersion == state.cancelVersion) {
                    state.isRefreshing = false
                    delay(holdResultMs)
                    resultMessage = null
                    motion.snapTo(state.pullOffsetPx)
                    motion.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                    ) { state.pullOffsetPx = value }
                }
            }
        }
    }

    LaunchedEffect(state.cancelVersion) {
        refreshJob?.cancel()
        refreshJob = null
        resultMessage = null
        isPulling = false
        motion.stop()
        motion.snapTo(0f)
        state.pullOffsetPx = 0f
    }

    fun finishPull() {
        if (state.isRefreshing) return

        if (state.pullOffsetPx >= triggerOffset) {
            startRefresh()
        } else {
            scope.launch {
                motion.snapTo(state.pullOffsetPx)
                motion.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                ) { state.pullOffsetPx = value }
            }
        }
    }

    LaunchedEffect(state.refreshRequestVersion) {
        if (state.refreshRequestVersion > 0 && enabled) startRefresh()
    }

    val gestureModifier = Modifier.pointerInput(state, triggerOffset) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var active = true
                var ownsGesture = false
                var totalX = 0f
                var totalY = 0f
                val directionSlop = with(density) { 5.dp.toPx() }

                while (active) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        active = false
                        continue
                    }

                    val dragAmount = change.position.y - change.previousPosition.y
                    totalX += change.position.x - change.previousPosition.x
                    totalY += dragAmount
                    val gestureEnabled = updatedEnabled.value
                    val primaryDistance = if (fromBottom) -totalY else totalY
                    if (!ownsGesture && gestureEnabled && primaryDistance > directionSlop &&
                        primaryDistance > kotlin.math.abs(totalX) * 1.2f
                    ) {
                        ownsGesture = true
                    }
                    if (gestureEnabled && ownsGesture) {
                        val current = state.pullOffsetPx
                        val towardRefresh = if (fromBottom) dragAmount < 0f else dragAmount > 0f
                        val awayFromRefresh = if (fromBottom) dragAmount > 0f else dragAmount < 0f
                        when {
                            towardRefresh && !state.isRefreshing -> {
                                isPulling = true
                                change.consume()
                                // There is intentionally no maximum pull
                                // distance. The release distance below still
                                // decides whether a refresh should start, but
                                // the user can keep pulling past it naturally.
                                state.pullOffsetPx = current + kotlin.math.abs(dragAmount) * PULL_RESISTANCE
                            }

                            current > 0f && awayFromRefresh -> {
                                change.consume()
                                val next = max(0f, current - kotlin.math.abs(dragAmount) * 0.8f)
                                state.pullOffsetPx = next
                                if (state.isRefreshing && next <= with(density) { 8.dp.toPx() }) {
                                    state.cancelRefresh()
                                }
                            }
                        }
                    }

                    if (!change.pressed) active = false
                }

                isPulling = false
                if (updatedEnabled.value && ownsGesture) finishPull()
                else if (state.pullOffsetPx > 0f && !state.isRefreshing) {
                    state.pullOffsetPx = 0f
                }
            }
        }

    Box(
        modifier = modifier
            .then(gestureModifier)
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // graphicsLayer reads the animation on the render path; the
                // feed body is not recomposed for every drag frame.
                .graphicsLayer { translationY = if (fromBottom) -state.pullOffsetPx else state.pullOffsetPx },
        ) {
            content()
        }

        val pullProgress = (state.pullOffsetPx / triggerOffset).coerceIn(0f, 1f)
        val indicatorAlpha = if (state.isRefreshing || resultMessage != null) {
            1f
        } else if (isPulling) {
            ((pullProgress - 0.08f) / 0.55f).coerceIn(0f, 1f)
        } else {
            0f
        }
        // Keep both labels anchored to one stable visual position. The feed
        // body can travel with the finger without making the label drift.
        val indicatorTranslation = with(density) {
            (indicatorTopOffset + 13.dp).toPx() * if (fromBottom) -1f else 1f
        }

        RefreshPullIndicator(
            text = when {
                state.isRefreshing -> "Refreshing${".".repeat(dots)}"
                resultMessage != null -> resultMessage.orEmpty()
                else -> "Refresh?"
            },
            alpha = indicatorAlpha,
            translationY = indicatorTranslation,
            modifier = Modifier.align(if (fromBottom) Alignment.BottomCenter else Alignment.TopCenter),
        )
    }
}

@Composable
private fun RefreshPullIndicator(
    text: String,
    alpha: Float,
    translationY: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translationY
            }
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = text,
            color = RefreshText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
fun RefreshProgressBar(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!active) return

    val transition = rememberInfiniteTransition(label = "refresh-progress")
    val beamProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
        ),
        label = "refresh-beam",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        val beamWidth = size.width * 0.35f
        val beamStart = -beamWidth + beamProgress * (size.width + beamWidth * 2f)
        drawRect(
            color = RefreshGold.copy(alpha = 0.12f),
            size = size,
        )
        drawRect(
            color = RefreshGold.copy(alpha = 0.95f),
            topLeft = androidx.compose.ui.geometry.Offset(beamStart, 0f),
            size = androidx.compose.ui.geometry.Size(beamWidth, size.height),
        )
    }
}
