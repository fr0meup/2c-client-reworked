package com.twocents.mobile.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.theme.Gold

@Composable
fun ComposePostFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .padding(end = 18.dp, bottom = 51.dp + bottomInset + 16.dp)
            .size(55.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Gold)
            .pressScale(rememberPressScale(interaction))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val framePath = remember {
            PathParser()
                .parsePathString("M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7")
                .toPath()
        }
        val pencilPath = remember {
            PathParser()
                .parsePathString("M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z")
                .toPath()
        }
        val iconColor = Color(0xFF0F0E0A)
        Canvas(
            modifier = Modifier
                .size(22.dp)
                .semantics { contentDescription = "Create post" },
        ) {
            scale(size.width / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                val stroke = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(framePath, color = iconColor, style = stroke)
                drawPath(pencilPath, color = iconColor, style = stroke)
            }
        }
    }
}
