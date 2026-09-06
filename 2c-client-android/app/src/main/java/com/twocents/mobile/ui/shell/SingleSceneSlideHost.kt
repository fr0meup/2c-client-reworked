package com.twocents.mobile.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext

/**
 * Keeps one live scene during tab transitions. The outgoing frame is retained in
 * a graphics layer so two list-heavy destinations are never composed together.
 */
@Composable
internal fun SingleSceneSlideHost(
    targetState: ShellScene,
    modifier: Modifier = Modifier,
    content: @Composable (ShellScene) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    var displayedState by remember { mutableStateOf(targetState) }
    val horizontalOffset = remember { Animatable(0f) }
    val graphicsContext = LocalGraphicsContext.current
    val transitionBackdrop = remember(graphicsContext) { graphicsContext.createGraphicsLayer() }
    var transitioning by remember { mutableStateOf(false) }
    DisposableEffect(graphicsContext, transitionBackdrop) {
        onDispose { graphicsContext.releaseGraphicsLayer(transitionBackdrop) }
    }

    BoxWithConstraints(
        modifier = modifier.clipToBounds().drawWithContent {
            if (transitioning) {
                drawLayer(transitionBackdrop)
                this@drawWithContent.drawContent()
            } else {
                transitionBackdrop.record { this@drawWithContent.drawContent() }
                drawLayer(transitionBackdrop)
            }
        },
        contentAlignment = Alignment.TopStart,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }

        LaunchedEffect(targetState, widthPx) {
            if (widthPx <= 0f) return@LaunchedEffect
            if (targetState == displayedState) {
                if (horizontalOffset.value != 0f) {
                    horizontalOffset.animateTo(0f, tween(120, easing = ShellOutCubic))
                }
                return@LaunchedEffect
            }

            val movingForward = when {
                targetState.profileRequested && !displayedState.profileRequested -> true
                !targetState.profileRequested && displayedState.profileRequested -> false
                else -> targetState.tab.ordinal > displayedState.tab.ordinal
            }
            val entryOffset = if (movingForward) widthPx else -widthPx
            transitioning = true
            displayedState = targetState
            horizontalOffset.snapTo(entryOffset)
            // Destination composition must complete over the retained frame before motion starts.
            withFrameNanos { }
            horizontalOffset.animateTo(0f, tween(165, easing = ShellOutCubic))
            transitioning = false
        }

        key(displayedState) {
            stateHolder.SaveableStateProvider(
                key = if (displayedState.profileRequested) "profile" else displayedState.tab.name,
            ) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = horizontalOffset.value }) {
                    content(displayedState)
                }
            }
        }
    }
}
