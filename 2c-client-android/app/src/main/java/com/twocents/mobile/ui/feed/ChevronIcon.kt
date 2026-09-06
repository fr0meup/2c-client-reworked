package com.twocents.mobile.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
internal fun ChevronIcon(
    expanded: Boolean,
    tint: Color,
) {
    Canvas(
        modifier = Modifier
            .size(13.5.dp)
            .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
    ) {
        val stroke = 2.5.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.38f)
            lineTo(size.width * 0.5f, size.height * 0.63f)
            lineTo(size.width * 0.75f, size.height * 0.38f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
