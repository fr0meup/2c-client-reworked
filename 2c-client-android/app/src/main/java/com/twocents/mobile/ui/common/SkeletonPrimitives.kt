package com.twocents.mobile.ui.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Shared low-level skeleton geometry; callers retain their exact feature colors. */
internal fun DrawScope.drawSkeletonRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
    color: Color,
) {
    // Guarding invalid dimensions here keeps partially measured skeletons harmless.
    if (width <= 0f || height <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
    )
}
