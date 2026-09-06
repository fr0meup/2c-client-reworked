package com.twocents.mobile.ui.compose

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.theme.Gold

private val FormatSurface = Color(0xFF141410)
private val FormatIcon = Color.White.copy(alpha = 0.78f)

/**
 * The formatter deliberately owns its own coordinate space. The RN editor lets this
 * control float over the editor, change orientation, and stay above the keyboard;
 * putting it in the text column makes all three behaviours fragile.
 */
@Composable
fun ComposePostFormatBar(
    bold: Boolean,
    italic: Boolean,
    bullets: Boolean,
    quote: Boolean,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onBullets: () -> Unit,
    onQuote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var horizontal by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val modalWidth = with(density) { maxWidth.toPx() }
        val modalHeight = with(density) { maxHeight.toPx() }
        val barWidth = with(density) { if (horizontal) 180.dp.toPx() else 38.dp.toPx() }
        val barHeight = with(density) { if (horizontal) 38.dp.toPx() else 190.dp.toPx() }
        val initialTop = modalHeight * 0.28f
        val initialRight = with(density) { 12.dp.toPx() }
        val minX = -(modalWidth - barWidth - initialRight - with(density) { 10.dp.toPx() }).coerceAtLeast(0f)
        val maxX = initialRight - with(density) { 2.dp.toPx() }
        val minY = -(initialTop - with(density) { 20.dp.toPx() }).coerceAtLeast(0f)
        val maxY = (modalHeight - initialTop - barHeight - with(density) { 30.dp.toPx() }).coerceAtLeast(0f)
        val toolbarClearance = with(density) { 64.dp.toPx() }
        val currentBottom = initialTop + dragY + barHeight

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp)
                .graphicsLayer {
                    val visibleHeight = modalHeight - imeInsets.getBottom(this) - toolbarClearance
                    val keyboardOverlap = (currentBottom - visibleHeight).coerceAtLeast(0f)
                    translationX = dragX
                    translationY = initialTop + dragY - keyboardOverlap
                }
                .clip(RoundedCornerShape(24.dp))
                .background(FormatSurface)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .pointerInput(horizontal, modalWidth, modalHeight) {
                    detectDragGestures(
                        onDragStart = {
                            startX = dragX
                            startY = dragY
                        },
                        onDragCancel = {
                            dragX = startX
                            dragY = startY
                        },
                        onDragEnd = {},
                        onDrag = { change, amount ->
                            change.consumePositionChange()
                            dragX = (dragX + amount.x).coerceIn(minX, maxX)
                            dragY = (dragY + amount.y).coerceIn(minY, maxY)
                        },
                    )
                }
                .padding(horizontal = if (horizontal) 6.dp else 4.dp, vertical = if (horizontal) 4.dp else 6.dp),
        ) {
            if (horizontal) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OrientationButton(horizontal = true, onClick = { horizontal = false })
                    FormatDivider(horizontal = true)
                    FormatActionText("B", bold, FontWeight.Black, FontStyle.Normal, onBold)
                    FormatActionText("I", italic, FontWeight.Bold, FontStyle.Italic, onItalic)
                    FormatIconAction(FormatGlyph.Bullets, bullets, onBullets)
                    FormatIconAction(FormatGlyph.Quote, quote, onQuote)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OrientationButton(horizontal = false, onClick = { horizontal = true })
                    FormatDivider(horizontal = false)
                    FormatActionText("B", bold, FontWeight.Black, FontStyle.Normal, onBold)
                    FormatActionText("I", italic, FontWeight.Bold, FontStyle.Italic, onItalic)
                    FormatIconAction(FormatGlyph.Bullets, bullets, onBullets)
                    FormatIconAction(FormatGlyph.Quote, quote, onQuote)
                }
            }
        }
    }
}

private enum class FormatGlyph { Bullets, Quote }

@Composable
private fun OrientationButton(horizontal: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(13.dp)) {
            val stroke = 2.dp.toPx()
            val color = FormatIcon
            val scale = size.minDimension / 24f
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                if (horizontal) {
                    drawLine(color, Offset(12f, 3f), Offset(12f, 21f), stroke / scale, StrokeCap.Round)
                    drawLine(color, Offset(8f, 7f), Offset(12f, 3f), stroke / scale, StrokeCap.Round)
                    drawLine(color, Offset(16f, 7f), Offset(12f, 3f), stroke / scale, StrokeCap.Round)
                    drawLine(color, Offset(8f, 17f), Offset(12f, 21f), stroke / scale, StrokeCap.Round)
                    drawLine(color, Offset(16f, 17f), Offset(12f, 21f), stroke / scale, StrokeCap.Round)
                } else {
                    drawLine(color, Offset(3f, 12f), Offset(21f, 12f), stroke / scale, StrokeCap.Round)
                    drawLine(color, Offset(7f, 8f), Offset(3f, 12f), stroke / scale, StrokeCap.Round)
                    drawLine(color, Offset(7f, 16f), Offset(3f, 12f), stroke / scale, StrokeCap.Round)
                    drawLine(color, Offset(17f, 8f), Offset(21f, 12f), stroke / scale, StrokeCap.Round)
                    drawLine(color, Offset(17f, 16f), Offset(21f, 12f), stroke / scale, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun FormatActionText(
    label: String,
    active: Boolean,
    weight: FontWeight,
    style: FontStyle,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (active) Gold else Color.White.copy(alpha = 0.04f))
            .pressScale(rememberPressScale(interaction))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color(0xFF0F0E0A) else Color.White,
            fontSize = 14.sp,
            fontWeight = weight,
            fontStyle = style,
        )
    }
}

@Composable
private fun FormatIconAction(glyph: FormatGlyph, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (active) Gold else Color.White.copy(alpha = 0.04f))
            .pressScale(rememberPressScale(interaction))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(if (glyph == FormatGlyph.Bullets) 15.dp else 14.dp)) {
            val color = if (active) Color(0xFF0F0E0A) else FormatIcon
            val scale = size.minDimension / 24f
            val stroke = (if (glyph == FormatGlyph.Bullets) 2.5.dp else 2.dp).toPx() / scale
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                if (glyph == FormatGlyph.Bullets) {
                    listOf(6f, 12f, 18f).forEach { y ->
                        drawLine(color, Offset(3f, y), Offset(3.01f, y), stroke, StrokeCap.Round)
                        drawLine(color, Offset(8f, y), Offset(21f, y), stroke, StrokeCap.Round)
                    }
                } else {
                    val quotes = Path()
                    fun addQuote(x: Float) {
                        quotes.moveTo(x + 3f, 21f)
                        quotes.cubicTo(x + 6f, 21f, x + 10f, 20f, x + 10f, 13f)
                        quotes.lineTo(x + 10f, 5f)
                        quotes.cubicTo(x + 10f, 3.75f, x + 9.244f, 2.983f, x + 8f, 3f)
                        quotes.lineTo(x + 4f, 3f)
                        quotes.cubicTo(x + 2.75f, 3f, x + 2f, 3.75f, x + 2f, 4.972f)
                        quotes.lineTo(x + 2f, 11f)
                        quotes.cubicTo(x + 2f, 12.25f, x + 2.75f, 13f, x + 4f, 13f)
                        quotes.cubicTo(x + 5f, 13f, x + 5f, 13f, x + 5f, 14f)
                        quotes.lineTo(x + 5f, 15f)
                        quotes.cubicTo(x + 5f, 16f, x + 4f, 16f, x + 3f, 17f)
                        quotes.cubicTo(x + 2f, 17.008f, x + 2f, 17.031f, x + 2f, 18.031f)
                        quotes.lineTo(x + 2f, 20f)
                        quotes.cubicTo(x + 2f, 21f, x + 2f, 21f, x + 3f, 21f)
                    }
                    addQuote(0f)
                    addQuote(12f)
                    // Both quote shapes are drawn in one pass so shared
                    // edges cannot stack alpha and become brighter.
                    drawPath(
                        quotes,
                        color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatDivider(horizontal: Boolean) {
    Box(
        modifier = if (horizontal) {
            Modifier.size(width = 1.dp, height = 18.dp)
        } else {
            Modifier.size(width = 18.dp, height = 1.dp)
        }.background(Color.White.copy(alpha = 0.1f)),
    )
}
