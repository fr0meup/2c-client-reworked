package com.twocents.mobile.ui.feed

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

// A balanced curve prevents the first 70–80% of the panel from appearing in
// one jump while leaving only the final pixels to animate.
private val DropdownOpenEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val DropdownCloseEasing = CubicBezierEasing(0.64f, 0f, 0.78f, 1f)

/**
 * The feed menu lives in the activity's Compose hierarchy instead of a Popup
 * window. This keeps it below the header on every Android compositor/device.
 */
@Composable
internal fun TopicDropdownOverlay(
    open: Boolean,
    activeTopic: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(
            durationMillis = 380,
            easing = if (open) {
                DropdownOpenEasing
            } else {
                DropdownCloseEasing
            },
        ),
        label = "topic-dropdown-progress",
    )
    val backdropAlpha by animateFloatAsState(
        targetValue = if (open) 0.65f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "topic-dropdown-backdrop",
    )
    val visible = open || progress > 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .padding(top = topInset + 57.dp),
    ) {
        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backdropAlpha))
                    .clickable { onDismiss() },
            )
            AnimatedDropdownPanel(
                progress = progress,
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopicDropdown(
                    activeTopic = activeTopic,
                    maxHeight = configuration.screenHeightDp.dp * 0.72f,
                    onSelect = onSelect,
                )
            }
        }
    }
}
