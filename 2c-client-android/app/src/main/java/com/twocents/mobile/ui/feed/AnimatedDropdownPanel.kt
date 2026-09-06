package com.twocents.mobile.ui.feed

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

/** Reveals the dropdown from its top edge on every animation frame. */
@Composable
internal fun AnimatedDropdownPanel(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                clipRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height * progress,
                ) {
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        content()
    }
}
