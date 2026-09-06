package com.twocents.mobile.ui.shell

import com.twocents.mobile.ui.common.drawSkeletonRect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SkeletonFill = Color.White.copy(alpha = 0.08f)
private val SkeletonMuted = Color.White.copy(alpha = 0.08f)
private val RoomSurface = Color.White.copy(alpha = 0.035f)
private val RoomBorder = Color.White.copy(alpha = 0.08f)
private val DmSurface = Color.White.copy(alpha = 0.025f)
private val DmBorder = Color.White.copy(alpha = 0.06f)
private val NotificationSurface = Color.White.copy(alpha = 0.02f)
private val NotificationBorder = Color.White.copy(alpha = 0.06f)

/** One static draw node for the initial rooms and direct-message list. */
@Composable
internal fun MessagesListSkeleton(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(570.dp)) {
        val padding = 13.dp.toPx()
        val gap = 10.dp.toPx()
        val cardWidth = ((size.width - padding * 2f - gap) / 2f).coerceAtLeast(0f)
        val cardHeight = 118.dp.toPx()

        repeat(4) { index ->
            val column = index % 2
            val row = index / 2
            val x = padding + column * (cardWidth + gap)
            val y = 12.dp.toPx() + row * (cardHeight + gap)
            drawRoomSkeletonCard(x, y, cardWidth, cardHeight)
        }

        val sectionY = 268.dp.toPx()
        val sectionLabelWidth = 110.dp.toPx()
        val sectionLabelX = (size.width - sectionLabelWidth) / 2f
        val lineGap = 8.dp.toPx()
        val lineY = sectionY + 5.dp.toPx()
        drawRect(
            color = DmBorder,
            topLeft = Offset(padding, lineY),
            size = Size((sectionLabelX - lineGap - padding).coerceAtLeast(0f), 1.dp.toPx()),
        )
        skeletonRect(sectionLabelX, sectionY, sectionLabelWidth, 11.dp.toPx(), 5.5.dp.toPx())
        drawRect(
            color = DmBorder,
            topLeft = Offset(sectionLabelX + sectionLabelWidth + lineGap, lineY),
            size = Size((size.width - padding - sectionLabelX - sectionLabelWidth - lineGap).coerceAtLeast(0f), 1.dp.toPx()),
        )

        repeat(4) { index ->
            drawDmSkeletonRow(
                x = padding,
                y = 291.dp.toPx() + index * 66.dp.toPx(),
                width = size.width - padding * 2f,
            )
        }
    }
}

private fun DrawScope.drawRoomSkeletonCard(x: Float, y: Float, width: Float, height: Float) {
    drawRoundRect(
        color = RoomSurface,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(16.dp.toPx()),
    )
    drawRoundRect(
        color = RoomBorder,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(16.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
    val innerX = x + 13.dp.toPx()
    val innerWidth = width - 26.dp.toPx()
    skeletonRect(innerX, y + 13.dp.toPx(), 13.dp.toPx(), 13.dp.toPx(), 4.dp.toPx())
    skeletonRect(innerX + 20.dp.toPx(), y + 12.5.dp.toPx(), innerWidth * 0.55f, 14.dp.toPx(), 7.dp.toPx())
    skeletonRect(x + width - 33.dp.toPx(), y + 11.dp.toPx(), 20.dp.toPx(), 18.dp.toPx(), 9.dp.toPx(), SkeletonMuted)
    skeletonRect(innerX, y + 68.dp.toPx(), innerWidth * 0.8f, 11.dp.toPx(), 5.5.dp.toPx(), SkeletonMuted)
    skeletonRect(innerX, y + 91.dp.toPx(), 35.dp.toPx(), 11.dp.toPx(), 5.5.dp.toPx(), SkeletonMuted)
    skeletonRect(innerX + 43.dp.toPx(), y + 91.dp.toPx(), 30.dp.toPx(), 11.dp.toPx(), 5.5.dp.toPx(), SkeletonMuted)
}

private fun DrawScope.drawDmSkeletonRow(x: Float, y: Float, width: Float) {
    val height = 58.dp.toPx()
    drawRoundRect(
        color = DmSurface,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(18.dp.toPx()),
    )
    drawRoundRect(
        color = DmBorder,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(18.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
    val contentX = x + 14.dp.toPx()
    skeletonRect(contentX, y + 16.dp.toPx(), 68.dp.toPx(), 26.dp.toPx(), 13.dp.toPx())
    val textX = contentX + 80.dp.toPx()
    skeletonRect(textX, y + 12.dp.toPx(), width * 0.34f, 13.dp.toPx(), 6.5.dp.toPx())
    skeletonRect(textX, y + 34.dp.toPx(), width * 0.52f, 11.dp.toPx(), 5.5.dp.toPx(), SkeletonMuted)
    skeletonRect(x + width - 39.dp.toPx(), y + 12.dp.toPx(), 25.dp.toPx(), 10.dp.toPx(), 5.dp.toPx(), SkeletonMuted)
}

/** One static draw node for six notification placeholders. */
@Composable
internal fun NotificationsListSkeleton(modifier: Modifier = Modifier) {
    val actorWidths = floatArrayOf(78f, 96f, 68f, 88f, 74f, 102f)
    val actionWidths = floatArrayOf(112f, 86f, 132f, 104f, 118f, 76f)
    val previewWidths = floatArrayOf(0.88f, 0.68f, 0.78f, 0.94f, 0.62f, 0.82f)
    Canvas(modifier.fillMaxWidth().height(476.dp)) {
        val x = 12.dp.toPx()
        val width = size.width - x * 2f
        var y = 12.dp.toPx()
        repeat(6) { index ->
            val extraLine = index == 1 || index == 4
            val height = (if (extraLine) 78 else 64).dp.toPx()
            drawNotificationSkeletonCard(
                x = x,
                y = y,
                width = width,
                height = height,
                actorWidth = actorWidths[index].dp.toPx(),
                actionWidth = actionWidths[index].dp.toPx(),
                previewFraction = previewWidths[index],
                extraLine = extraLine,
            )
            y += height + 8.dp.toPx()
        }
    }
}

private fun DrawScope.drawNotificationSkeletonCard(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    actorWidth: Float,
    actionWidth: Float,
    previewFraction: Float,
    extraLine: Boolean,
) {
    drawRoundRect(
        color = NotificationSurface,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(16.dp.toPx()),
    )
    drawRoundRect(
        color = NotificationBorder,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(16.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
    drawCircle(SkeletonFill, radius = 18.dp.toPx(), center = Offset(x + 34.dp.toPx(), y + 32.dp.toPx()))
    val textX = x + 64.dp.toPx()
    val timestampWidth = 36.dp.toPx()
    val timestampX = x + width - 16.dp.toPx() - timestampWidth
    val usableHeader = (timestampX - textX - 8.dp.toPx()).coerceAtLeast(0f)
    val firstWidth = minOf(actorWidth, usableHeader * 0.44f)
    skeletonRect(textX, y + 16.dp.toPx(), firstWidth, 12.dp.toPx(), 6.dp.toPx())
    skeletonRect(
        textX + firstWidth + 6.dp.toPx(),
        y + 16.dp.toPx(),
        minOf(actionWidth, (usableHeader - firstWidth - 6.dp.toPx()).coerceAtLeast(0f)),
        12.dp.toPx(),
        6.dp.toPx(),
        SkeletonMuted,
    )
    skeletonRect(timestampX, y + 17.dp.toPx(), timestampWidth, 10.dp.toPx(), 5.dp.toPx(), SkeletonMuted)
    val textAreaWidth = width - 80.dp.toPx()
    skeletonRect(textX, y + 38.dp.toPx(), textAreaWidth * previewFraction, 11.dp.toPx(), 5.5.dp.toPx(), SkeletonMuted)
    if (extraLine) {
        skeletonRect(textX, y + 57.dp.toPx(), textAreaWidth * 0.58f, 11.dp.toPx(), 5.5.dp.toPx(), SkeletonMuted)
    }
}

@Composable
internal fun TransactionsPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Transactions aren't available here yet. I don't currently have access to the feature in the EU, so I can't reliably reverse-engineer and implement it.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun DrawScope.skeletonRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
    color: Color = SkeletonFill,
) {
    drawSkeletonRect(x, y, width, height, radius, color)
}
