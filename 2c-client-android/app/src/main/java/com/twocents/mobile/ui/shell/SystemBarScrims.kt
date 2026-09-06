package com.twocents.mobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.twocents.mobile.ui.theme.Background

@Composable
internal fun StatusBarScrim(
    alpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset)
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.verticalGradient(
                    listOf(
                        Background,
                        Color(0xD90A0907),
                        Color(0x8C0A0907),
                        Color(0x2E0A0907),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
